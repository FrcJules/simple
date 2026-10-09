package app.presence.launcher.sms

import android.app.PendingIntent
import android.content.Context
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import app.presence.launcher.messages.MessageStatus
import app.presence.launcher.messages.StoredMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsSenderTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class FakeTransport(private val splitAt: Int = 160, private val failWith: RuntimeException? = null) : SmsTransport {
        var address: String? = null
        var parts: List<String> = emptyList()
        var sent: List<PendingIntent> = emptyList()
        override fun divide(text: String) = text.chunked(splitAt)
        override fun send(address: String, parts: List<String>, sent: List<PendingIntent>, delivered: List<PendingIntent>, subId: Int) {
            failWith?.let { throw it }
            this.address = address; this.parts = parts; this.sent = sent
        }
    }

    private val gateway = FakeGateway()
    private val stored = mutableMapOf<String, StoredMessage>()
    private val statuses = mutableMapOf<String, MessageStatus>()

    private fun sender(transport: SmsTransport, default: Boolean = true) = SmsSender(
        context, gateway, { stored[it.id] = it }, { id, s -> statuses[id] = s }, { default }, transport, clock = { 9_000L },
    )

    @Test
    fun sendsAndKeepsTheMessageInHistoryAndInTheSystemOutbox() {
        val t = FakeTransport()
        val r = sender(t).send("+33611223344", "  Bonjour Marie  ", "Marie") as SendResult.Started
        val m = stored.getValue(r.messageId)
        assertEquals("Bonjour Marie", m.text)
        assertEquals("Marie", m.from)
        assertEquals("+33611223344", m.address)
        assertTrue(m.outgoing)
        assertTrue(m.read)
        assertEquals(MessageStatus.SENDING, m.status)
        assertEquals(listOf(FakeGateway.Inserted("+33611223344", "Bonjour Marie", true)), gateway.inserted)
        assertEquals("+33611223344", t.address)
        assertEquals(listOf("Bonjour Marie"), t.parts)
    }

    @Test
    fun theIdIsTheSystemRowsWhenThereIsOneAndFreshWhenNot() {
        val r = sender(FakeTransport()).send("0611223344", "Salut", "Marie") as SendResult.Started
        assertEquals("sms-100", r.messageId)
        val direct = SmsSender(context, FakeGateway(writable = false), { stored[it.id] = it }, { id, s -> statuses[id] = s }, { true }, FakeTransport(), clock = { 9_000L })
        assertTrue((direct.send("0611223344", "Salut", "Marie") as SendResult.Started).messageId.startsWith("out-"))
    }

    @Test
    fun sendsDirectlyWithoutBeingTheDefaultAppWhenAllowed() {
        val t = FakeTransport()
        val allowed = SmsSender(context, FakeGateway(writable = false), { stored[it.id] = it }, { id, s -> statuses[id] = s }, { true }, t, clock = { 9_000L })
        assertTrue(allowed.send("0611223344", "Salut", "Marie") is SendResult.Started)
        assertEquals(listOf("Salut"), t.parts)
    }

    @Test
    fun aLongMessageIsSplitIntoPartsEachWithItsOwnCallback() {
        val t = FakeTransport(splitAt = 5)
        sender(t).send("0611223344", "abcdefghijkl", "Marie")
        assertEquals(3, t.parts.size)
        assertEquals(3, t.sent.size)
        assertEquals(3, t.sent.toSet().size) // distinct PendingIntents: parts must not overwrite each other's extras
    }

    @Test
    fun refusesWhenWeAreNotTheDefaultSmsApp() {
        val t = FakeTransport()
        assertEquals(SendResult.NotDefaultApp, sender(t, default = false).send("0611223344", "Salut", "Marie"))
        assertTrue(stored.isEmpty())
        assertTrue(gateway.inserted.isEmpty())
        assertEquals(null, t.address)
    }

    @Test
    fun refusesBadInputBeforeDoingAnything() {
        val s = sender(FakeTransport())
        assertEquals(SendResult.InvalidNumber, s.send("12", "Salut", "x"))
        assertEquals(SendResult.EmptyText, s.send("0611223344", "   ", "x"))
        assertEquals(SendResult.TooLong, s.send("0611223344", "a".repeat(SmsSender.MAX_CHARS + 1), "x"))
        assertTrue(stored.isEmpty())
    }

    @Test
    fun aPlatformRefusalMarksTheMessageFailedInsteadOfCrashing() {
        val r = sender(FakeTransport(failWith = SecurityException("no permission"))).send("0611223344", "Salut", "Marie") as SendResult.Started
        assertEquals(MessageStatus.FAILED, statuses[r.messageId])
        assertEquals(Telephony.Sms.MESSAGE_TYPE_FAILED, gateway.types.values.single())
    }

    @Test
    fun eachMessageGetsItsOwnId() {
        val s = sender(FakeTransport())
        val a = s.send("0611223344", "un", "M") as SendResult.Started
        val b = s.send("0611223344", "deux", "M") as SendResult.Started
        assertNotEquals(a.messageId, b.messageId)
    }
}
