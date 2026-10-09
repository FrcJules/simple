package app.presence.launcher.sms

import app.presence.launcher.messages.StoredMessage
import app.presence.launcher.notifications.Channel
import app.presence.launcher.notifications.Incoming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsInboxTest {
    private val gateway = FakeGateway()
    private val stored = mutableListOf<StoredMessage>()
    private val forwarded = mutableListOf<Incoming>()
    private val notified = mutableListOf<StoredMessage>()

    private fun inbox(names: Map<String, String> = emptyMap()) = SmsInbox(
        gateway, stored::add, names::get, forwarded::add, notified::add, clock = { 5_000L }, newId = { "sms-x" },
    )

    @Test
    fun anOrdinaryMessageIsRecordedKeptForwardedAndAnnounced() {
        inbox(mapOf("+33600000001" to "Marie")).onReceived("+33600000001", "Je passe à 15h", 4_000L, -1)
        assertEquals(listOf(FakeGateway.Inserted("+33600000001", "Je passe à 15h", false)), gateway.inserted)
        val m = stored.single()
        assertEquals("Marie", m.from)
        assertEquals("+33600000001", m.address)
        assertEquals(Channel.SMS, m.channel)
        assertEquals(5_000L, m.receivedAtMs)
        assertFalse(m.sensitive)
        assertEquals(listOf<Incoming>(Incoming.Message("Marie", Channel.SMS, "Je passe à 15h")), forwarded)
        assertEquals(listOf(m), notified)
    }

    @Test
    fun aReceivedMessageTakesTheIdOfTheSystemRowSoTheHistoryCopyNeverDoublesIt() {
        inbox().onReceived("0611223344", "Bonjour", 0L, -1)
        assertEquals("sms-100", stored.single().id) // FakeGateway's first row; the history copy would call it "sms-100" too
    }

    @Test
    fun withoutASystemRowItFallsBackToAFreshId() {
        val inbox = SmsInbox(FakeGateway(writable = false), stored::add, { null }, forwarded::add, notified::add, clock = { 5_000L }, newId = { "sms-x" })
        inbox.onReceived("0611223344", "Bonjour", 0L, -1)
        assertEquals("sms-x", stored.single().id)
    }

    @Test
    fun anUnknownSenderIsShownByNumberAndNeverSentToTheDisplay() {
        inbox().onReceived("0611223344", "Bonjour", 0L, -1)
        assertEquals("0611223344", stored.single().from)
        assertTrue(forwarded.isEmpty()) // the display is visible from the whole room: strangers' texts stay on the phone
        assertEquals(1, notified.size)
    }

    @Test
    fun aSecurityCodeIsKeptAndFlaggedButNeverForwarded() {
        inbox().onReceived("BANQUE", "Votre code de confirmation est 482913. Ne le communiquez à personne.", 0L, -1)
        assertTrue(stored.single().sensitive)
        assertEquals(1, gateway.inserted.size) // still in the system inbox
        assertTrue(forwarded.isEmpty()) // never leaves the phone
        assertEquals(1, notified.size) // the person is still told a message arrived
    }

    @Test
    fun aFailingStepNeverLosesTheMessageOrStopsTheOthers() {
        val boom = object : SmsProviderGateway by FakeGateway() {
            override fun insertIncoming(address: String, body: String, dateMs: Long, dateSentMs: Long, subId: Int): Long? =
                throw IllegalStateException("provider down")
        }
        val kept = mutableListOf<StoredMessage>()
        val told = mutableListOf<StoredMessage>()
        SmsInbox(boom, kept::add, { "Marie" }, { throw IllegalStateException("hub down") }, told::add).onReceived("0611223344", "Bonjour", 0L, -1)
        assertEquals(1, kept.size)
        assertEquals(1, told.size)
    }

    @Test
    fun whenTheSystemInboxRefusesTheMessageIsStillKept() {
        val refusing = FakeGateway(writable = false)
        val kept = mutableListOf<StoredMessage>()
        SmsInbox(refusing, kept::add, { null }, {}, {}).onReceived("0611223344", "Bonjour", 0L, -1)
        assertEquals(1, kept.size)
    }
}
