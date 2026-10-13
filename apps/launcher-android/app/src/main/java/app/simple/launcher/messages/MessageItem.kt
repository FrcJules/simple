package app.simple.launcher.messages

import app.simple.launcher.Contact
import app.simple.launcher.notifications.Channel

/**
 * A conversation as the list shows it: its most recent message, the person's face and number when we know them, and how
 * many received messages have not been opened yet.
 */
data class MessageItem(val message: StoredMessage, val contact: Contact?, val unread: Int = 0, val whatsappReplyAvailable: Boolean = false) {
    val threadKey: String get() = ThreadKey.of(message.channel, message.address, message.from)

    /** The contact to display: the real one, or a placeholder showing the sender's initial. */
    val displayContact: Contact get() = contact ?: Contact(-1, message.from, "", null)

    /** The number to answer: the one the SMS came from, else the matched contact's. Never guessed from a name alone. */
    val replyNumber: String? get() = (message.address ?: contact?.number)?.takeIf { it.isNotBlank() }

    /**
     * A reply stays in the channel of the message (SMS to SMS, WhatsApp to WhatsApp): it never silently changes channel.
     * A number that came with the SMS is the sender's real number; a name alone (from a notification) needs a matching contact.
     */
    val canReply: Boolean
        get() = when (message.channel) {
            Channel.SMS -> replyNumber != null
            // WhatsApp: only through its live notification (ReplyRegistry): otherwise the answer would have nowhere to go.
            Channel.WHATSAPP -> whatsappReplyAvailable
        }
}

/** One row per conversation, most recently active first. [face] finds the contact for a conversation's latest message. */
fun groupConversations(
    messages: List<StoredMessage>,
    replyableWhatsApp: Set<String> = emptySet(),
    face: (StoredMessage) -> Contact?,
): List<MessageItem> =
    messages.groupBy { ThreadKey.of(it.channel, it.address, it.from) }.values
        .map { thread ->
            val last = thread.maxBy { it.receivedAtMs }
            val key = ThreadKey.of(last.channel, last.address, last.from)
            MessageItem(last, face(last), unread = thread.count { !it.read && !it.outgoing }, whatsappReplyAvailable = key in replyableWhatsApp)
        }
        .sortedByDescending { it.message.receivedAtMs }
