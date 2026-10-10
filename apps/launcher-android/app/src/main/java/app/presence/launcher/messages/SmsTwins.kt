package app.presence.launcher.messages

import app.presence.launcher.notifications.Channel
import kotlin.math.abs

/**
 * The same SMS can reach our history twice: the phone itself records a message sent by a non-default app in the system database (we
 * then copy that row), and a message announced by a notification can also be copied from the system database. These rules tell a
 * copy from a genuinely new message: same direction, same text, same number (or none known), a few minutes apart.
 */
object SmsTwins {
    const val SENT_WINDOW_MS = 5 * 60_000L
    const val RECEIVED_WINDOW_MS = 3 * 60_000L

    /** Messages we sent ourselves get a random "out-" id; the system's row, once copied, is "sms-<row>". */
    fun isLocalSent(m: StoredMessage) = m.outgoing && m.id.startsWith("out-")

    fun isCopyOfSent(copy: StoredMessage, local: StoredMessage) =
        copy.channel == Channel.SMS && copy.outgoing && local.outgoing && copy.text.trim() == local.text.trim() &&
            sameNumber(copy.address, local.address) && abs(copy.receivedAtMs - local.receivedAtMs) <= SENT_WINDOW_MS

    /** A notification-made message (random id, no number) and the system's row for the same SMS. */
    fun isNotificationTwin(row: StoredMessage, notification: StoredMessage) =
        row.channel == Channel.SMS && notification.channel == Channel.SMS && !row.outgoing && !notification.outgoing &&
            row.id.startsWith("sms-") && !notification.id.startsWith("sms-") && !notification.id.startsWith("out-") &&
            row.text.trim() == notification.text.trim() && abs(row.receivedAtMs - notification.receivedAtMs) <= RECEIVED_WINDOW_MS

    private fun tail(address: String?) = address.orEmpty().filter { it.isDigit() }.takeLast(9)
    private fun sameNumber(a: String?, b: String?) = tail(a).isNotEmpty() && tail(a) == tail(b)
}
