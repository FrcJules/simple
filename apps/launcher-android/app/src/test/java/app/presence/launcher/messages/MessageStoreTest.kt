package app.presence.launcher.messages

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.presence.launcher.notifications.Channel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dbName = "test-messages.db"

    @Before fun clean() { context.deleteDatabase(dbName) }
    @After fun cleanUp() { context.deleteDatabase(dbName) }

    private fun store(legacy: File? = null) = MessageStore(context, dbName, legacy)
    private fun msg(id: String, at: Long, read: Boolean = false, address: String? = null, outgoing: Boolean = false) =
        StoredMessage(id, "Marie", Channel.SMS, "Bonjour $id", at, read, outgoing, address)

    @Test
    fun addedMessagesSurviveARestart() {
        store().add(msg("a", 9_000, address = "+33600000001"))
        val again = store().messages.value.single()
        assertEquals("a", again.id)
        assertEquals("Bonjour a", again.text)
        assertEquals(Channel.SMS, again.channel)
        assertEquals("+33600000001", again.address)
    }

    @Test
    fun historyIsNeverTrimmedByAgeOrCount() {
        val s = store()
        val tenYears = 10L * 365 * 24 * 3600 * 1000
        s.add(msg("ancient", 1_000, address = "+9"))
        (1..700).forEach { s.add(msg("m$it", tenYears + it)) }
        assertEquals(701, s.count()) // all kept in the database
        assertEquals(MessageStore.RECENT, s.messages.value.size) // the screens only load the recent ones
        assertEquals("m700", s.messages.value.last().id)
        assertEquals("ancient", store().conversation("+9").single().id) // still there, just not loaded in the recent list
    }

    @Test
    fun sameIdReplacesInsteadOfDuplicating() {
        val s = store()
        s.add(msg("a", 1_000))
        s.add(msg("a", 1_000).copy(text = "changed"))
        assertEquals(1, s.count())
        assertEquals("changed", s.messages.value.single().text)
    }

    @Test
    fun markReadChangesOnlyThatMessageAndPersists() {
        val s = store()
        s.add(msg("a", 1_000))
        s.add(msg("b", 2_000))
        s.markRead("a")
        val reloaded = store().messages.value
        assertTrue(reloaded.first { it.id == "a" }.read)
        assertFalse(reloaded.first { it.id == "b" }.read)
        assertEquals(1, store().unreadCount())
    }

    @Test
    fun outgoingMessagesDoNotCountAsUnread() {
        val s = store()
        s.add(msg("in", 1_000))
        s.add(msg("out", 2_000, outgoing = true))
        assertEquals(1, s.unreadCount())
    }

    @Test
    fun conversationReturnsOneNumberOldestFirst() {
        val s = store()
        s.add(msg("c", 3_000, address = "+1"))
        s.add(msg("a", 1_000, address = "+1"))
        s.add(msg("x", 2_000, address = "+2"))
        s.add(msg("b", 2_500, address = "+1", outgoing = true))
        assertEquals(listOf("a", "b", "c"), s.conversation("+1").map { it.id })
    }

    @Test
    fun conversationGroupsDifferentWritingsOfTheSameNumber() {
        val s = store()
        s.add(msg("a", 1_000, address = "0612345678"))
        s.add(msg("b", 2_000, address = "+33612345678", outgoing = true))
        s.add(msg("c", 3_000, address = "0699999999"))
        assertEquals(listOf("a", "b"), s.conversation("+33 6 12 34 56 78").map { it.id })
    }

    @Test
    fun statusIsStoredAndUpdated() {
        val s = store()
        s.add(msg("o", 1_000, outgoing = true).copy(status = MessageStatus.SENDING))
        s.setStatus("o", MessageStatus.SENT)
        assertEquals(MessageStatus.SENT, store().messages.value.single().status)
    }

    @Test
    fun clearErasesEverything() {
        val s = store()
        s.add(msg("a", 1_000))
        s.clear()
        assertEquals(0, store().count())
        assertTrue(store().messages.value.isEmpty())
    }

    @Test
    fun theOldJsonFileIsImportedOnceThenRemoved() {
        val f = File(tmp.root, "messages.json")
        f.writeText("""[{"id":"old1","from":"Jules","channel":"whatsapp","text":"Salut","at":5000,"read":true}]""")
        val s = store(f)
        assertEquals("old1", s.messages.value.single().id)
        assertEquals(Channel.WHATSAPP, s.messages.value.single().channel)
        assertTrue(s.messages.value.single().read)
        assertFalse(f.exists())
    }

    @Test
    fun aCorruptOldFileIsDroppedWithoutCrashing() {
        val f = File(tmp.root, "messages.json").apply { writeText("{ not json") }
        assertEquals(0, store(f).count())
        assertFalse(f.exists())
    }

    @Test
    fun toStringHidesTheText() = assertEquals("StoredMessage(id=a)", msg("a", 1).toString())

    @Test
    fun removeSmsTwinsKeepsOurOwnSendAndTheSystemRowOfAReceivedSms() {
        val s = store()
        s.add(StoredMessage("out-1", "Marie", Channel.SMS, "Salut", 9_000, read = true, outgoing = true, address = "0611223344", status = MessageStatus.SENT))
        s.add(StoredMessage("sms-5", "Marie", Channel.SMS, "Salut", 9_020, read = true, outgoing = true, address = "+33611223344")) // the system's record of it
        s.add(StoredMessage("uuid-n", "Marie", Channel.SMS, "Bonjour", 20_000)) // announced by a notification
        s.add(StoredMessage("sms-6", "Marie", Channel.SMS, "Bonjour", 20_500, address = "+33611223344")) // and copied from the system database
        s.add(StoredMessage("sms-7", "Marie", Channel.SMS, "Bonjour", 400_000, address = "+33611223344")) // a later, genuinely new one
        assertEquals(2, s.removeSmsTwins())
        assertEquals(listOf("out-1", "sms-6", "sms-7"), s.messages.value.map { it.id }.sorted())
        assertEquals(MessageStatus.SENT, s.status("out-1")) // the copy with the real send status stays
        assertEquals(0, s.removeSmsTwins()) // nothing more the second time
    }

    @Test
    fun sentSinceListsOnlyOurOwnSendsAfterThatTime() {
        val s = store()
        s.add(StoredMessage("out-1", "M", Channel.SMS, "a", 1_000, outgoing = true, address = "0611223344"))
        s.add(StoredMessage("out-2", "M", Channel.SMS, "b", 5_000, outgoing = true, address = "0611223344"))
        s.add(StoredMessage("sms-3", "M", Channel.SMS, "c", 6_000, outgoing = true, address = "0611223344"))
        s.add(StoredMessage("x", "M", Channel.SMS, "d", 7_000))
        assertEquals(listOf("out-2"), s.sentSince(2_000).map { it.id })
    }
}

