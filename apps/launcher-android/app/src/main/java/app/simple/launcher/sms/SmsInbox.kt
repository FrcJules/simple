package app.simple.launcher.sms

import app.simple.launcher.messages.StoredMessage
import app.simple.launcher.messages.TextSafety
import app.simple.launcher.notifications.Channel
import app.simple.launcher.notifications.SensitiveText
import java.util.UUID

/**
 * What happens when an SMS arrives and we are the default SMS app: it is recorded in the system inbox (other apps and the
 * phone rely on it), kept in our own history and announced. Nothing leaves the phone.
 *
 * A one-time code or banking alert is kept (it is the person's own SMS) but flagged: masked in lists, never read aloud.
 */
class SmsInbox(
    private val gateway: SmsProviderGateway,
    private val add: (StoredMessage) -> Unit,
    private val nameFor: (String) -> String?,
    private val notify: (StoredMessage) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { "sms-${UUID.randomUUID()}" },
) {
    fun onReceived(address: String, rawBody: String, dateSentMs: Long, subId: Int) {
        val now = clock()
        // Control and bidirectional-override characters could make a text read differently from what it says.
        val body = TextSafety.clean(rawBody)
        val sensitive = SensitiveText.looksSensitive(body)
        val contactName = nameFor(address)?.takeIf { it.isNotBlank() }
        // A received SMS must never be lost: each step is isolated so that one failing does not stop the others.
        // The id is the system row's when there is one ("sms-<row>", as in the history copy): leaving or entering the default-app mode never doubles a message.
        val providerId = runCatching { gateway.insertIncoming(address, body, now, dateSentMs, subId) }.getOrNull()
        val message = StoredMessage(providerId?.let { "sms-$it" } ?: newId(), contactName ?: address, Channel.SMS, body, now, address = address, sensitive = sensitive)
        runCatching { add(message) }
        runCatching { notify(message) }
    }
}
