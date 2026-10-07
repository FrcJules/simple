package app.presence.launcher.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.presence.launcher.messages.MessageStore
import app.presence.launcher.messages.StoredMessage
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
        val incoming = NotificationParser.parse(raw) ?: return
        // When we are the SMS app we receive SMS directly (SmsDeliverReceiver); a copy via another app's notification would double it.
        if (incoming is Incoming.Message && incoming.channel == Channel.SMS && SmsRole.isHeld(applicationContext)) return
        if (duplicates.isDuplicate(sbn.key, "${raw.title}|${raw.text}", System.currentTimeMillis())) return
        if (incoming is Incoming.Message) {
            // Kept on the phone so the launcher can show it; independent of the home display being paired.
            MessageStore.get(applicationContext).add(
                StoredMessage(UUID.randomUUID().toString(), incoming.from, incoming.channel, incoming.text, sbn.postTime),
            )
        }
        HubForwarder.forward(applicationContext, incoming, sbn.postTime)
    }

    private companion object {
        val duplicates = DuplicateFilter()
    }
}
