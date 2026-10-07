package app.presence.launcher.messages

import app.presence.launcher.Contact

/** A message and, when the sender is in the phone's contacts, their face and number. */
data class MessageItem(val message: StoredMessage, val contact: Contact?) {
    /** The contact to display: the real one, or a placeholder showing the sender's initial. */
    val displayContact: Contact get() = contact ?: Contact(-1, message.from, "", null)

    /** Replying needs a phone number, and we only know it for matched contacts. */
    val canReply: Boolean get() = contact != null && contact.number.isNotBlank()
}
