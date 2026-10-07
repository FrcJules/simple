package app.presence.launcher.messages

import app.presence.launcher.Contact

/** A message and, when the sender is in the phone's contacts, their face and number. */
data class MessageItem(val message: StoredMessage, val contact: Contact?) {
    /** The contact to display: the real one, or a placeholder showing the sender's initial. */
    val displayContact: Contact get() = contact ?: Contact(-1, message.from, "", null)

    /**
     * Replying needs a phone number (known only for a matched contact) and goes through SMS, so it is not offered
     * for WhatsApp messages: the answer would silently change channel.
     */
    val canReply: Boolean get() = contact != null && contact.number.isNotBlank() && message.channel == app.presence.launcher.notifications.Channel.SMS
}
