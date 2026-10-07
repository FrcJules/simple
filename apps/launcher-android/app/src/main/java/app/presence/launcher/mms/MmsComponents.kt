package app.presence.launcher.mms

import android.content.Context
import app.presence.launcher.ContactsRepository
import app.presence.launcher.R
import app.presence.launcher.messages.MessageStore
import app.presence.launcher.notifications.HubForwarder
import app.presence.launcher.sms.MessageNotifier

/** Wires the MMS pieces to the real system (receivers have no dependency injection). */
object MmsComponents {
    fun inbox(context: Context): MmsInbox {
        val app = context.applicationContext
        val contacts = ContactsRepository(app.contentResolver)
        val notifier = MessageNotifier(app)
        return MmsInbox(
            context = app,
            store = MessageStore.get(app),
            files = MmsFiles(app),
            transport = SmsManagerMmsTransport(app),
            nameFor = { number -> contacts.byNumber(number)?.name },
            forward = { HubForwarder.forward(app, it, System.currentTimeMillis()) },
            notify = notifier::notifyIncoming,
            unknownSender = app.getString(R.string.mms_unknown_sender),
            photoLabel = app.getString(R.string.message_photo),
        )
    }
}
