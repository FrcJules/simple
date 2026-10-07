package app.presence.launcher.sms

import android.provider.Telephony
import app.presence.launcher.messages.MessageStatus
import app.presence.launcher.messages.StoredMessage
import app.presence.launcher.notifications.Channel
import app.presence.launcher.notifications.SensitiveText

/**
 * Copies the phone's existing SMS into our history when we become the default SMS app, so the person keeps their old
 * conversations (all marked read: an old inbox must not raise a huge "new messages" badge). Idempotent: each row has a stable id derived from the system row, so running it twice adds nothing.
 */
class SmsHistoryImporter(
    private val gateway: SmsProviderGateway,
    private val addAll: (List<StoredMessage>) -> Unit,
    private val nameFor: (String) -> String?,
) {
    fun importAll(): Int {
        val names = HashMap<String, String>()
        val messages = gateway.readAll().mapNotNull { sms ->
            if (sms.address.isBlank() || sms.body.isBlank() || sms.type == Telephony.Sms.MESSAGE_TYPE_DRAFT) return@mapNotNull null
            val outgoing = sms.type != Telephony.Sms.MESSAGE_TYPE_INBOX
            val name = names.getOrPut(sms.address) { nameFor(sms.address)?.takeIf { it.isNotBlank() } ?: sms.address }
            StoredMessage(
                id = "sms-${sms.id}", from = name, channel = Channel.SMS, text = sms.body, receivedAtMs = sms.dateMs,
                read = true, outgoing = outgoing, address = sms.address,
                status = when (sms.type) {
                    Telephony.Sms.MESSAGE_TYPE_SENT -> MessageStatus.SENT
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageStatus.NONE
                    Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageStatus.FAILED
                    else -> MessageStatus.SENDING // outbox / queued
                },
                sensitive = !outgoing && SensitiveText.looksSensitive(sms.body),
            )
        }
        addAll(messages)
        return messages.size
    }
}
