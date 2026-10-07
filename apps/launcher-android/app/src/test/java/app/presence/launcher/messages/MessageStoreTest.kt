package app.presence.launcher.messages

import app.presence.launcher.notifications.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MessageStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val day = 24L * 3600 * 1000
    private fun msg(id: String, at: Long, read: Boolean = false) = StoredMessage(id, "Marie", Channel.SMS, "Bonjour $id", at, read)
    private fun store(file: File, now: Long, max: Int = 30) = MessageStore(file, maxMessages = max, clock = { now })

    @Test
    fun addedMessagesSurviveARestart() {
        val f = File(tmp.root, "m.json")
        store(f, 10_000).add(msg("a", 9_000))
        val again = store(f, 10_000)
        assertEquals(listOf("a"), again.messages.value.map { it.id })
        assertEquals("Bonjour a", again.messages.value.single().text)
        assertEquals(Channel.SMS, again.messages.value.single().channel)
    }

    @Test
    fun keepsOnlyTheMostRecentMessages() {
        val s = store(File(tmp.root, "m.json"), 100_000, max = 3)
        (1..5).forEach { s.add(msg("m$it", it * 1_000L)) }
        assertEquals(listOf("m3", "m4", "m5"), s.messages.value.map { it.id })
    }

    @Test
    fun dropsMessagesOlderThanAWeek() {
        val now = 20 * day
        val s = store(File(tmp.root, "m.json"), now)
        s.add(msg("old", now - 8 * day))
        s.add(msg("fresh", now - 1 * day))
        assertEquals(listOf("fresh"), s.messages.value.map { it.id })
    }

    @Test
    fun markReadChangesOnlyThatMessageAndPersists() {
        val f = File(tmp.root, "m.json")
        val s = store(f, 10_000)
        s.add(msg("a", 1_000))
        s.add(msg("b", 2_000))
        s.markRead("a")
        val reloaded = store(f, 10_000).messages.value
        assertTrue(reloaded.first { it.id == "a" }.read)
        assertFalse(reloaded.first { it.id == "b" }.read)
    }

    @Test
    fun aCorruptFileStartsEmptyInsteadOfCrashing() {
        val f = File(tmp.root, "m.json").apply { writeText("{ not json") }
        assertEquals(emptyList<StoredMessage>(), store(f, 10_000).messages.value)
    }

    @Test
    fun clearEmptiesTheStoreAndTheFile() {
        val f = File(tmp.root, "m.json")
        val s = store(f, 10_000)
        s.add(msg("a", 1_000))
        s.clear()
        assertEquals(emptyList<StoredMessage>(), store(f, 10_000).messages.value)
    }

    @Test
    fun toStringHidesTheText() = assertEquals("StoredMessage(id=a)", msg("a", 1).toString())
}
