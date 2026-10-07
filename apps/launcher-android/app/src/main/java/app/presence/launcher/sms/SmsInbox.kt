package app.presence.launcher.sms

import app.presence.launcher.messages.StoredMessage
import app.presence.launcher.notifications.Channel
import app.presence.launcher.notifications.Incoming
import app.presence.launcher.notifications.SensitiveText
import java.util.UUID

/**
 * What happens when an SMS arrives and we are the default SMS app: it is recorded in the system inbox (other apps and the
 * phone rely on it), kept in our own history, forwarded to the home display and announced.
 *
 * A one-time code or banking alert is kept (it is the person's own SMS) but flagged: masked in lists, never read aloud,
 * never forwarded to the home display.
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
    fun onReceived(address: String, body: String, dateSentMs: Long, subId: Int) {
        val now = clock()
        gateway.insertIncoming(address, body, now, dateSentMs, subId)
        val sensitive = SensitiveText.looksSensitive(body)
        val name = nameFor(address)?.takeIf { it.isNotBlank() } ?: address
        val message = StoredMessage(newId(), name, Channel.SMS, body, now, address = address, sensitive = sensitive)
        add(message)
        if (!sensitive) forward(Incoming.Message(name.take(60), Channel.SMS, body.take(500)))
        notify(message)
    }
}
