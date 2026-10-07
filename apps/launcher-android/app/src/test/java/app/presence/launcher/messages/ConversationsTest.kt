package app.presence.launcher.messages

import app.presence.launcher.Contact
import app.presence.launcher.notifications.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationsTest {
    private fun sms(id: String, at: Long, address: String, name: String = address, read: Boolean = false, out: Boolean = false) =
        StoredMessage(id, name, Channel.SMS, "texte $id", at, read, out, address)

    @Test
    fun oneRowPerPersonWithTheLatestMessageAndTheUnreadCount() {
        val items = groupConversations(
            listOf(
                sms("1", 1_000, "0611223344"),
                sms("2", 2_000, "+33611223344", read = true, out = true),
                sms("3", 3_000, "+33 6 11 22 33 44"),
                sms("4", 1_500, "0699999999"),
            ),
        ) { null }
        assertEquals(listOf("3", "4"), items.map { it.message.id })
        assertEquals(listOf(2, 1), items.map { it.unread }) // the outgoing message is never "unread"
    }

    @Test
    fun mostRecentConversationFirst() {
        val items = groupConversations(listOf(sms("a", 5_000, "1111"), sms("b", 9_000, "2222"), sms("c", 7_000, "3333"))) { null }
        assertEquals(listOf("b", "c", "a"), items.map { it.message.id })
    }

    @Test
    fun whatsappAndSmsFromTheSamePersonStayApart() {
        val wa = StoredMessage("w", "Marie", Channel.WHATSAPP, "salut", 2_000)
        val items = groupConversations(listOf(sms("s", 1_000, "0611223344", "Marie"), wa)) { null }
        assertEquals(2, items.size)
    }

    @Test
    fun theFaceIsLookedUpForTheLatestMessageOfEachConversation() {
        val marie = Contact(1, "Marie", "0611223344", null)
        val items = groupConversations(listOf(sms("1", 1_000, "0611223344"))) { marie }
        assertEquals(marie, items.single().contact)
    }

    @Test
    fun replyNeedsARealNumberAndTheSmsChannel() {
        val withAddress = MessageItem(sms("1", 1, "0611223344"), null)
        assertEquals("0611223344", withAddress.replyNumber)
        assertTrue(withAddress.canReply)

        val nameOnly = MessageItem(StoredMessage("2", "Marie", Channel.SMS, "x", 1), null)
        assertFalse(nameOnly.canReply) // a name from a notification is not an identity
        val matched = MessageItem(StoredMessage("2", "Marie", Channel.SMS, "x", 1), Contact(1, "Marie", "0600000000", null))
        assertTrue(matched.canReply)
        assertEquals("0600000000", matched.replyNumber)

        val whatsapp = MessageItem(StoredMessage("3", "Marie", Channel.WHATSAPP, "x", 1), Contact(1, "Marie", "0600000000", null))
        assertFalse(whatsapp.canReply)
        assertNull(MessageItem(StoredMessage("4", "X", Channel.SMS, "x", 1, address = " "), null).replyNumber)
    }
}
