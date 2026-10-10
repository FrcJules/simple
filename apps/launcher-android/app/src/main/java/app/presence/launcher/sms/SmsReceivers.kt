package app.presence.launcher.sms

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.Telephony
import android.telephony.TelephonyManager
import app.presence.launcher.messages.MessageDiagnostics
import app.presence.launcher.mms.MmsComponents
import app.presence.launcher.mms.MmsInbox
import app.presence.launcher.mms.MmsSender
import kotlin.concurrent.thread

/** Delivered by the system only to the default SMS app. */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        val first = parts.firstOrNull() ?: return
        val address = first.originatingAddress ?: return
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        if (body.isEmpty()) return
        val subId = intent.getIntExtra("subscription", -1)
        val pending: PendingResult? = goAsync() // null when called directly (tests)
        thread(name = "sms-deliver") {
            try {
                SmsComponents.inbox(context).onReceived(address, body, first.timestampMillis, subId)
            } catch (e: RuntimeException) {
                // Never crash the process on an incoming SMS; SmsInbox already isolates each step.
            } finally {
                pending?.finish()
            }
        }
    }
}

/**
 * Direct mode: the phone's own messaging app is still the default SMS app and writes the SMS to the system database; every app with
 * the RECEIVE_SMS permission is told as well. We then copy the new SMS into our history at once, whether or not Présence is open and
 * whether or not notification access works. Nothing here when we are the default app ([SmsDeliverReceiver] does the work).
 */
class SmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION || SmsRole.isHeld(context)) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        val address = parts.firstOrNull()?.originatingAddress.orEmpty()
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        MessageDiagnostics.record(context, "SMS reçu (diffusion du système)")
        val pending: PendingResult? = goAsync()
        thread(name = "sms-received") {
            try {
                Thread.sleep(WRITE_DELAY_MS) // the messaging app writes the row to the system database first
                SmsComponents.syncNew(context)
                if (address.isNotEmpty() && body.isNotEmpty()) SmsComponents.forwardToDisplay(context, address, body)
            } catch (e: RuntimeException) {
                MessageDiagnostics.record(context, "SMS reçu : copie en erreur")
            } finally {
                pending?.finish()
            }
        }
    }

    private companion object {
        const val WRITE_DELAY_MS = 2_000L
    }
}

/** Per-part "sent" and "delivered" callbacks of [SmsSender]. Not exported: only our own PendingIntents reach it. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
        val providerId = intent.getLongExtra(EXTRA_PROVIDER_ID, -1L).takeIf { it >= 0 }
        val part = intent.getIntExtra(EXTRA_PART, 0)
        val parts = intent.getIntExtra(EXTRA_PARTS, 1)
        val ok = resultCode == Activity.RESULT_OK
        val progress = SmsComponents.progress(context)
        when (intent.action) {
            SmsSender.ACTION_SENT -> progress.onSent(id, providerId, part, parts, ok)
            SmsSender.ACTION_DELIVERED -> progress.onDelivered(id, part, parts, ok)
        }
    }

    companion object {
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_PROVIDER_ID = "provider_id"
        const val EXTRA_PART = "part"
        const val EXTRA_PARTS = "parts"
    }
}

/** MMS announcements arrive here (delivered only to the default SMS app): the operator says a message is waiting. */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val data = intent.getByteArrayExtra("data") ?: return
        val subId = intent.getIntExtra("subscription", -1)
        val pending: PendingResult? = goAsync() // null when called directly (tests)
        thread(name = "mms-notification") {
            try {
                MmsComponents.inbox(context).onNotification(data, subId)
            } catch (e: RuntimeException) {
                // Never crash the process on an incoming message; the operator will announce it again.
            } finally {
                pending?.finish()
            }
        }
    }
}

/** The platform finished (or failed) downloading an MMS. Not exported: only our own PendingIntent reaches it. */
class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(MmsInbox.EXTRA_ID) ?: return
        val file = intent.getStringExtra(MmsInbox.EXTRA_FILE) ?: return
        val ok = resultCode == Activity.RESULT_OK
        val pending: PendingResult? = goAsync()
        thread(name = "mms-downloaded") {
            try {
                MmsComponents.inbox(context).onDownloaded(id, file, ok)
            } catch (e: RuntimeException) {
                // The message stays "being received" and is marked "not received" after a while, with a retry.
            } finally {
                pending?.finish()
            }
        }
    }
}

/** The platform answered about an outgoing MMS. Not exported: only our own PendingIntent reaches it. */
class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(MmsSender.EXTRA_ID) ?: return
        val file = intent.getStringExtra(MmsSender.EXTRA_FILE) ?: return
        val ok = resultCode == Activity.RESULT_OK
        val pending: PendingResult? = goAsync()
        thread(name = "mms-sent") {
            try {
                MmsComponents.sender(context).onSent(id, file, ok)
            } catch (e: RuntimeException) {
                // The message stays "being sent" and is shown as not sent after a while.
            } finally {
                pending?.finish()
            }
        }
    }
}

/** Required of every SMS app: "reply with a message" from the incoming-call screen. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.data?.schemeSpecificPart?.let { Uri.decode(it).substringBefore('?') }
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE && !number.isNullOrBlank() && !text.isNullOrBlank()) {
            SmsComponents.sender(this).send(number, text, name = number)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
