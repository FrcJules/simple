package app.presence.launcher.sms

import app.presence.launcher.messages.StoredMessage
import app.presence.launcher.messages.TextSafety
import app.presence.launcher.notifications.Channel
import app.presence.launcher.notifications.Incoming
import app.presence.launcher.notifications.SensitiveText
import java.util.UUID

/**
 * What happens when an SMS arrives and we are the default SMS app: it is recorded in the system inbox (other apps and the
 * phone rely on it), kept in our own history, forwarded to the home display and announced.
 *
 * A one-time code or banking alert is kept (it is the person's own SMS) but flagged: masked in lists, never read aloud,
 * never forwarded to the home display. Only messages from known contacts are forwarded to the display at all.
 */
class SmsInbox(
    private val gateway: SmsProviderGateway,
    private val add: (StoredMessage) -> Unit,
    private val nameFor: (String) -> String?,
    private val forward: (Incoming) -> Unit,
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
        // The home display is visible from the whole room: only people we know appear there, never a stranger's or a company's text.
        if (!sensitive && contactName != null) runCatching { forward(Incoming.Message(contactName.take(60), Channel.SMS, body.take(500))) }
        runCatching { notify(message) }
    }
}
