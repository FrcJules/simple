package app.simple.launcher.sms

import android.provider.Telephony
import app.simple.launcher.messages.MessageStatus

/**
 * Turns the platform's per-part "sent" and "delivered" callbacks into one status per message. A failure of any part fails the
 * message; it is only "sent" once the last part went out and nothing failed before; only a sent message can become delivered.
 */
class SendProgress(
    private val status: (String) -> MessageStatus?,
    private val setStatus: (String, MessageStatus) -> Unit,
    private val gateway: SmsProviderGateway,
) {
    fun onSent(id: String, providerId: Long?, part: Int, parts: Int, ok: Boolean) {
        if (status(id) != MessageStatus.SENDING) return
        if (!ok) {
            setStatus(id, MessageStatus.FAILED)
            providerId?.let { gateway.setType(it, Telephony.Sms.MESSAGE_TYPE_FAILED) }
        } else if (part == parts - 1) {
            setStatus(id, MessageStatus.SENT)
            providerId?.let { gateway.setType(it, Telephony.Sms.MESSAGE_TYPE_SENT) }
        }
    }

    fun onDelivered(id: String, part: Int, parts: Int, ok: Boolean) {
        if (ok && part == parts - 1 && status(id) == MessageStatus.SENT) setStatus(id, MessageStatus.DELIVERED)
    }
}
