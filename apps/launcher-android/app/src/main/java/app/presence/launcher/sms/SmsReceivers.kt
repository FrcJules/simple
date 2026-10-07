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

/**
 * Required of every SMS app: MMS notifications arrive here. Not handled yet: MMS support is the next step and the role
 * must not be offered to a real user before it exists (see SMS_ROLE_OFFERED in build.gradle.kts).
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
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
