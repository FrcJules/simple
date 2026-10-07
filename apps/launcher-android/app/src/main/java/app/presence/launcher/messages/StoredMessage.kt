package app.presence.launcher.messages

import app.presence.launcher.notifications.Channel

enum class MessageStatus(val code: Int) {
    NONE(0), SENDING(1), SENT(2), FAILED(3), DELIVERED(4);

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/** A picture received in an MMS, kept as a private file of the app (never in the gallery). */
data class Attachment(val mime: String, val path: String)

/**
 * A message kept on the phone only, with no age or count limit (ADR 0005).
 *
 * [from] is the *other person's* display name: the sender of an incoming message, the recipient of an outgoing one.
 * [address] is their phone number when known (SMS), used to group a conversation and to reply.
 */
data class StoredMessage(
    val id: String,
    val from: String,
    val channel: Channel,
    val text: String,
    val receivedAtMs: Long,
    val read: Boolean = false,
    val outgoing: Boolean = false,
    val address: String? = null,
    val status: MessageStatus = MessageStatus.NONE,
    /** One-time code or banking alert: kept (it is the person's own SMS) but masked in lists and never read aloud or forwarded. */
    val sensitive: Boolean = false,
    /** An MMS: while [status] is SENDING for a received one it is still being downloaded; FAILED means it did not arrive (retry). */
    val mms: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
) {
    /** Keeps the message text out of logs. */
    override fun toString() = "StoredMessage(id=$id)"
}
