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
            isDefaultApp = { SmsRole.isHeld(app) },
            transport = SmsManagerTransport(app),
        )
    }

    fun progress(context: Context): SendProgress {
        val app = context.applicationContext
        val store = MessageStore.get(app)
        return SendProgress(store::status, store::setStatus, TelephonyGateway(app.contentResolver))
    }

    fun importer(context: Context): SmsHistoryImporter {
        val app = context.applicationContext
        return SmsHistoryImporter(TelephonyGateway(app.contentResolver), MessageStore.get(app)::addAll, nameFor(app))
    }
}
