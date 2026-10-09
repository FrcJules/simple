package app.presence.launcher.sms

import android.content.Context
import app.presence.launcher.ContactsRepository
import app.presence.launcher.messages.MessageStore
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
    fun syncNew(context: Context): Int {
        val app = context.applicationContext
        if (SmsRole.isHeld(app) || !SmsAccess.canRead(app)) return 0 // as the default app, receivers keep the history up to date
        return syncLocked(app)
    }

    /** After the full history was copied in default-app mode: the marker moves, so leaving that mode later does not read everything again. */
    fun markHistoryCopied(context: Context) {
        syncLocked(context.applicationContext, onlyMarker = true)
    }

    // One sync at a time (opening the app, a notification, the settings): the marker only ever moves forward.
    private val syncLock = Any()

    private fun syncLocked(app: Context, onlyMarker: Boolean = false): Int = synchronized(syncLock) {
        val profile = app.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val last = profile.getLong("sms_synced_up_to", 0L)
        // "First copy" is its own flag: an empty inbox leaves the marker at 0, and the first SMS after that must still count as new.
        val firstDone = profile.getBoolean("sms_first_copy_done", false)
        val result = importer(app).syncSince(last, keepReadFlag = firstDone)
        val edit = profile.edit().putBoolean("sms_first_copy_done", true)
        if (result.lastId > last) edit.putLong("sms_synced_up_to", result.lastId)
        edit.apply()
        if (onlyMarker) 0 else result.count
    }

    fun importer(context: Context): SmsHistoryImporter {
        val app = context.applicationContext
        return SmsHistoryImporter(TelephonyGateway(app.contentResolver), MessageStore.get(app)::addAll, nameFor(app))
    }
}
