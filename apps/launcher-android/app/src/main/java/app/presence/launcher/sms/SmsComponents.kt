package app.presence.launcher.sms

import android.content.Context
import app.presence.launcher.ContactsRepository
import app.presence.launcher.messages.MessageDiagnostics
import app.presence.launcher.messages.MessageStore
import app.presence.launcher.messages.TextSafety
import app.presence.launcher.notifications.Channel
import app.presence.launcher.notifications.Incoming
import app.presence.launcher.notifications.SensitiveText
import app.presence.launcher.notifications.HubForwarder

/** Wires the SMS pieces to the real system (receivers and services have no dependency injection). */
object SmsComponents {
    private fun nameFor(context: Context): (String) -> String? {
        val contacts = ContactsRepository(context.contentResolver)
        return { number -> contacts.byNumber(number)?.name }
    }

    fun inbox(context: Context): SmsInbox {
        val app = context.applicationContext
        val store = MessageStore.get(app)
        val notifier = MessageNotifier(app)
        return SmsInbox(
            gateway = TelephonyGateway(app.contentResolver),
            add = store::add,
            nameFor = nameFor(app),
            forward = { HubForwarder.forward(app, it, System.currentTimeMillis()) },
            notify = notifier::notifyIncoming,
        )
    }

    fun sender(context: Context): SmsSender {
        val app = context.applicationContext
        val store = MessageStore.get(app)
        return SmsSender(
            context = app,
            gateway = TelephonyGateway(app.contentResolver),
            addMessage = store::add,
            setStatus = store::setStatus,
            maySend = { SmsAccess.canSend(app) },
            transport = SmsManagerTransport(app),
        )
    }

    fun progress(context: Context): SendProgress {
        val app = context.applicationContext
        val store = MessageStore.get(app)
        return SendProgress(store::status, store::setStatus, TelephonyGateway(app.contentResolver))
    }

    /**
     * Copies new system SMS into our history when we are NOT the default SMS app but may read them (direct mode). Starts from the last row
     * already copied; the very first run copies the whole history, all marked read. Returns how many messages were added.
     */
    fun syncNew(context: Context, allowFirstCopy: Boolean = true): Int {
        val app = context.applicationContext
        if (SmsRole.isHeld(app)) return 0 // as the default app, receivers keep the history up to date
        if (!SmsAccess.canRead(app)) {
            MessageDiagnostics.record(app, "Copie des SMS : impossible, la permission de lecture des SMS n'est pas accordée")
            return 0
        }
        return syncLocked(app, allowFirstCopy = allowFirstCopy)
    }

    /** After the full history was copied in default-app mode: the marker moves, so leaving that mode later does not read everything again. */
    fun markHistoryCopied(context: Context) {
        syncLocked(context.applicationContext, onlyMarker = true)
    }

    // One sync at a time (opening the app, a notification, the settings): the marker only ever moves forward.
    private val syncLock = Any()

    private fun syncLocked(app: Context, onlyMarker: Boolean = false, allowFirstCopy: Boolean = true): Int = synchronized(syncLock) {
        val profile = app.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val last = profile.getLong("sms_synced_up_to", 0L)
        // "First copy" is its own flag: an empty inbox leaves the marker at 0, and the first SMS after that must still count as new.
        if (!profile.getBoolean("sms_twins_cleaned", false)) {
            // Once: SMS that an earlier version copied twice (its own send, then the system's record of it; a notification, then the row).
            val removed = MessageStore.get(app).removeSmsTwins()
            profile.edit().putBoolean("sms_twins_cleaned", true).apply()
            if (removed > 0) MessageDiagnostics.record(app, "Doublons de SMS supprimés : $removed")
        }
        val firstDone = profile.getBoolean("sms_first_copy_done", false)
        if (!firstDone && !allowFirstCopy) {
            // A broadcast receiver has about ten seconds: the whole history is copied when the app is opened or from the settings.
            MessageDiagnostics.record(app, "Copie des SMS : première copie à faire en ouvrant Présence")
            return@synchronized 0
        }
        val result = importer(app).syncSince(last, keepReadFlag = firstDone)
        MessageDiagnostics.record(app, "Copie des SMS : ${result.count} nouveau(x)" + if (!firstDone) " (première copie)" else "")
        val edit = profile.edit().putBoolean("sms_first_copy_done", true)
        if (result.lastId > last) edit.putLong("sms_synced_up_to", result.lastId)
        edit.apply()
        if (onlyMarker) 0 else result.count
    }

    /** The home display shows an SMS only from a known contact and never a code (same rule as when we are the SMS app). */
    fun forwardToDisplay(context: Context, address: String, body: String) {
        val app = context.applicationContext
        val clean = TextSafety.clean(body)
        if (SensitiveText.looksSensitive(clean)) return
        val name = runCatching { ContactsRepository(app.contentResolver).byNumber(address)?.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        HubForwarder.forward(app, Incoming.Message(name.take(60), Channel.SMS, clean.take(500)), System.currentTimeMillis())
    }

    fun importer(context: Context): SmsHistoryImporter {
        val app = context.applicationContext
        val store = MessageStore.get(app)
        return SmsHistoryImporter(TelephonyGateway(app.contentResolver), store::addAll, nameFor(app), sentSince = store::sentSince)
    }
}
