package app.presence.launcher.messages

import app.presence.launcher.notifications.Channel

/** A message seen in a notification, kept on the phone only (see ADR 0004). */
data class StoredMessage(
    val id: String,
    val from: String,
    val channel: Channel,
    val text: String,
    val receivedAtMs: Long,
    val read: Boolean = false,
) {
    /** Keeps the message text out of logs. */
    override fun toString() = "StoredMessage(id=$id)"
}
