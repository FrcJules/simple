package app.presence.launcher.mms

import android.content.Context
import app.presence.launcher.ContactsRepository
import app.presence.launcher.R
import app.presence.launcher.messages.MessageStore
import app.presence.launcher.notifications.HubForwarder
import app.presence.launcher.sms.MessageNotifier

/** Wires the MMS pieces to the real system (receivers have no dependency injection). */
object MmsComponents {
    fun sender(context: Context): MmsSender {
        val app = context.applicationContext
        return MmsSender(
            context = app,
            store = MessageStore.get(app),
            files = MmsFiles(app),
            transport = SmsManagerMmsTransport(app),
            compress = { uri -> PhotoCompressor.compress(app, uri) },
            isDefaultApp = { app.packageName == android.provider.Telephony.Sms.getDefaultSmsPackage(app) },
        )
    }

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
            imageOk = ::decodesSafely,
        )
    }

    /** The picture must decode, and not be a tiny file that expands to hundreds of millions of pixels. */
    private fun decodesSafely(image: ReceivedImage): Boolean {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, o)
        return o.outWidth > 0 && o.outHeight > 0 && o.outWidth.toLong() * o.outHeight <= MAX_PIXELS
    }

    private const val MAX_PIXELS = 50_000_000L
}
