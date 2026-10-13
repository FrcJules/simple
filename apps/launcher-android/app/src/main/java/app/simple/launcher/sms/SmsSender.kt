package app.simple.launcher.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import app.simple.launcher.isDialable
import app.simple.launcher.messages.MessageStatus
import app.simple.launcher.messages.StoredMessage
import app.simple.launcher.notifications.Channel
import java.util.UUID

sealed interface SendResult {
    data class Started(val messageId: String) : SendResult
    data object NotDefaultApp : SendResult
    data object InvalidNumber : SendResult
    data object EmptyText : SendResult
    data object TooLong : SendResult
}

/** The part of SmsManager we use, so sending can be tested. */
interface SmsTransport {
    fun divide(text: String): List<String>

    /** Throws [SecurityException] or [IllegalArgumentException] when the platform refuses. */
    fun send(address: String, parts: List<String>, sent: List<PendingIntent>, delivered: List<PendingIntent>, subId: Int)
}

/** The SmsManager of one SIM (or the default one when [subId] is -1), shared by SMS and MMS. */
internal fun smsManagerFor(context: Context, subId: Int): SmsManager {
    val default = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
    return if (subId >= 0 && Build.VERSION.SDK_INT >= 31) default.createForSubscriptionId(subId) else default
}

class SmsManagerTransport(private val context: Context) : SmsTransport {
    override fun divide(text: String): List<String> = smsManagerFor(context, -1).divideMessage(text)

    override fun send(address: String, parts: List<String>, sent: List<PendingIntent>, delivered: List<PendingIntent>, subId: Int) {
        smsManagerFor(context, subId).sendMultipartTextMessage(address, null, ArrayList(parts), ArrayList(sent), ArrayList(delivered))
    }
}

/**
 * Sends an SMS (as the default SMS app, or directly with the SEND_SMS permission), keeps it in our history, in the system outbox when
 * we are the default app, and follows its progress through [SmsStatusReceiver] so a failure is shown instead of being silent.
 */
class SmsSender(
    private val context: Context,
    private val gateway: SmsProviderGateway,
    private val addMessage: (StoredMessage) -> Unit,
    private val setStatus: (String, MessageStatus) -> Unit,
    /** True when we may send: we are the default SMS app, or the phone gave us the SEND_SMS permission (direct mode). */
    private val maySend: () -> Boolean,
    private val transport: SmsTransport,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun send(address: String, text: String, name: String, subId: Int = -1): SendResult {
        if (!maySend()) return SendResult.NotDefaultApp // "not allowed": neither the default SMS app nor holder of SEND_SMS
        if (!isDialable(address)) return SendResult.InvalidNumber
        val body = text.trim()
        if (body.isEmpty()) return SendResult.EmptyText
        if (body.length > MAX_CHARS) return SendResult.TooLong

        val now = clock()
        val providerId = gateway.insertOutgoing(address, body, now, subId)
        val id = providerId?.let { "sms-$it" } ?: "out-${UUID.randomUUID()}" // same id as the history copy when the system has a row
        addMessage(StoredMessage(id, name, Channel.SMS, body, now, read = true, outgoing = true, address = address, status = MessageStatus.SENDING))

        val parts = try {
            transport.divide(body)
        } catch (e: RuntimeException) {
            fail(id, providerId)
            return SendResult.Started(id)
        }
        try {
            transport.send(address, parts, parts.indices.map { pending(ACTION_SENT, id, providerId, it, parts.size) }, parts.indices.map { pending(ACTION_DELIVERED, id, providerId, it, parts.size) }, subId)
        } catch (e: SecurityException) {
            fail(id, providerId)
        } catch (e: IllegalArgumentException) {
            fail(id, providerId)
        }
        return SendResult.Started(id)
    }

    private fun fail(id: String, providerId: Long?) {
        setStatus(id, MessageStatus.FAILED)
        providerId?.let { gateway.setType(it, Telephony.Sms.MESSAGE_TYPE_FAILED) }
    }

    private fun pending(action: String, id: String, providerId: Long?, part: Int, parts: Int): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("simple-sms://$id/$part")) // makes each PendingIntent distinct, with its own extras
            .putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, id)
            .putExtra(SmsStatusReceiver.EXTRA_PROVIDER_ID, providerId ?: -1L)
            .putExtra(SmsStatusReceiver.EXTRA_PART, part)
            .putExtra(SmsStatusReceiver.EXTRA_PARTS, parts)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val MAX_CHARS = 1_600
        const val ACTION_SENT = "app.simple.launcher.SMS_SENT"
        const val ACTION_DELIVERED = "app.simple.launcher.SMS_DELIVERED"
    }
}
