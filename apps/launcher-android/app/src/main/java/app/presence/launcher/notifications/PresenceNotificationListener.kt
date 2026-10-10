package app.presence.launcher.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.presence.launcher.ContactsRepository
import app.presence.launcher.messages.MessageDiagnostics
import app.presence.launcher.messages.MessageStore
import app.presence.launcher.messages.StoredMessage
import app.presence.launcher.messages.ThreadKey
import app.presence.launcher.sms.SmsAccess
import app.presence.launcher.sms.SmsComponents
import app.presence.launcher.sms.SmsRole
import java.util.UUID

/** Watches whitelisted apps only; everything else is dropped by [NotificationParser] without being read. */
class PresenceNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!NotificationParser.isWatched(sbn.packageName)) return // before reading any content
        if (sbn.notification.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT) != 0) return
        val extras = sbn.notification.extras
        val raw = RawNotification(
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            category = sbn.notification.category,
        )
        val source = shortName(sbn.packageName)
        val incoming = NotificationParser.parse(raw)
        if (incoming == null) {
            MessageDiagnostics.record(applicationContext, "Notification $source : ignorée (${NotificationParser.rejectionReason(raw)})")
            return
        }
        // When we are the SMS app we receive SMS directly (SmsDeliverReceiver); a copy via another app's notification would double it.
        if (incoming is Incoming.Message && incoming.channel == Channel.SMS && SmsRole.isHeld(applicationContext)) return
        // Registered before the duplicate filter: WhatsApp often re-posts a notification with a fresh PendingIntent.
        if (incoming is Incoming.Message && incoming.channel == Channel.WHATSAPP && !isGroupConversation(extras)) {
            ReplyRegistry.findReplyAction(sbn.notification)?.let { (action, inputs) ->
                ReplyRegistry.put(sbn.key, ThreadKey.of(Channel.WHATSAPP, null, incoming.from), sbn.packageName, sbn.notification.shortcutId, action, inputs)
            }
        }
        if (duplicates.isDuplicate(sbn.key, "${raw.title}|${raw.text}", System.currentTimeMillis())) {
            MessageDiagnostics.record(applicationContext, "Notification $source : déjà vue")
            return
        }
        MessageDiagnostics.record(applicationContext, "Notification $source : ${if (incoming is Incoming.Message) "message gardé" else "appel manqué"}")
        if (incoming is Incoming.Message && incoming.channel == Channel.SMS) {
            // The home display is visible from the whole room: only SMS from people we know appear there, never a stranger's or a company's.
            val known = runCatching { ContactsRepository(applicationContext.contentResolver).byName(incoming.from) != null }.getOrDefault(false)
            // Direct mode: the SMS themselves are copied from the system database (a copy made from this notification would double them).
            if (SmsAccess.canRead(applicationContext)) {
                worker.execute { runCatching { SmsComponents.syncNew(applicationContext) } } // never on the main thread: the first copy reads the whole history
                if (known) HubForwarder.forward(applicationContext, incoming, sbn.postTime)
                return
            }
            if (!known) {
                MessageStore.get(applicationContext).add(StoredMessage(UUID.randomUUID().toString(), incoming.from, incoming.channel, incoming.text, sbn.postTime))
                return
            }
        }
        if (incoming is Incoming.Message) {
            // Kept on the phone so the launcher can show it; independent of the home display being paired.
            MessageStore.get(applicationContext).add(
                StoredMessage(UUID.randomUUID().toString(), incoming.from, incoming.channel, incoming.text, sbn.postTime),
            )
        }
        HubForwarder.forward(applicationContext, incoming, sbn.postTime)
    }

    private fun shortName(packageName: String) = when (packageName) {
        "com.whatsapp" -> "WhatsApp"
        "com.whatsapp.w4b" -> "WhatsApp Business"
        "com.google.android.apps.messaging" -> "Messages (Google)"
        "com.samsung.android.messaging" -> "Messages (Samsung)"
        "com.android.mms" -> "Messages (Android)"
        else -> "Téléphone"
    }

    /** An answer from here could go to a whole group: only one-to-one chats are ever answered. */
    private fun isGroupConversation(extras: android.os.Bundle) =
        extras.getBoolean("android.isGroupConversation", false) || extras.getCharSequence("android.conversationTitle") != null

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (NotificationParser.isWatched(sbn.packageName)) ReplyRegistry.removeNotification(sbn.key)
    }

    override fun onListenerDisconnected() {
        ReplyRegistry.clear() // the actions are only valid while we see the notifications
    }

    private companion object {
        val duplicates = DuplicateFilter()
        val worker: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor()
    }
}
