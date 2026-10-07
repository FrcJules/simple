package app.presence.launcher

import android.content.Intent
import android.provider.Telephony
import androidx.test.platform.app.InstrumentationRegistry
import app.presence.launcher.messages.MessageStatus
import app.presence.launcher.messages.MessageStore
import app.presence.launcher.notifications.Channel
import app.presence.launcher.sms.SendResult
import app.presence.launcher.sms.SmsComponents
import app.presence.launcher.sms.MmsDeliverReceiver
import app.presence.launcher.sms.SmsDeliverReceiver
import app.presence.launcher.sms.SmsRole
import app.presence.launcher.sms.TelephonyGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The real telephony stack on the emulator, with the app as the default SMS app (so it is eligible for the role, and the
 * system lets it write to `content://sms`). Run on its own by scripts/ci-emulator-tests.sh, which grants the role first:
 * granting runtime permissions to a running app makes Android kill its process, which here is also the test runner.
 */
class SmsEndToEndTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val gateway get() = TelephonyGateway(context.contentResolver)

    @Before
    fun theAppMustAlreadyBeTheSmsApp() {
        assertTrue(
            "the app must be the default SMS app (and so be eligible for the role): see scripts/ci-emulator-tests.sh",
            SmsRole.isHeld(context),
        )
        MessageStore.get(context).clear()
    }

    private fun waitFor(timeoutMs: Long = 60_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(250)
        return condition()
    }

    @Test
    fun anSmsWeSendIsAcceptedByTheRadioAndRecordedInTheSystem() {
        val text = "ping ${System.nanoTime()}"
        val result = SmsComponents.sender(context).send("5554", text, "Moi")
        val id = (result as SendResult.Started).messageId
        val store = MessageStore.get(context)

        assertTrue("must reach SENT, was ${store.status(id)}", waitFor { store.status(id).let { it == MessageStatus.SENT || it == MessageStatus.DELIVERED } })
        // The system SMS database now holds it as a sent message (type SENT), updated by our status receiver.
        assertTrue(
            "the system SMS box must hold the sent message",
            waitFor { gateway.readAll().any { it.body == text && it.type == Telephony.Sms.MESSAGE_TYPE_SENT } },
        )
    }

    @Test
    fun anIncomingSmsIsKeptShownAndRecordedInTheSystemInbox() {
        val text = "Bonjour Jean-Pierre, a demain ${System.nanoTime()}"
        val intent = Intent(Telephony.Sms.Intents.SMS_DELIVER_ACTION)
            .putExtra("pdus", arrayOf(SmsPdu.deliver("+33611223344", text)))
            .putExtra("format", "3gpp")
        SmsDeliverReceiver().onReceive(context, intent)

        val store = MessageStore.get(context)
        assertTrue("the message must reach our history", waitFor { store.messages.value.any { !it.outgoing && it.text == text } })
        val m = store.messages.value.single { !it.outgoing && it.text == text }
        assertEquals(Channel.SMS, m.channel)
        assertEquals("+33611223344", m.address)
        assertFalse(m.read)
        assertEquals(1, store.unreadCount())
        assertTrue(
            "the system SMS box must hold the received message",
            waitFor { gateway.readAll().any { it.body == text && it.type == Telephony.Sms.MESSAGE_TYPE_INBOX } },
        )
    }

    /**
     * There is no operator on the emulator, so the download cannot succeed; what must hold is that an announcement is shown at
     * once, handed to the real platform, never crashes the app, and never stays silently "being received".
     */
    @Test
    fun anMmsAnnouncementIsShownAtOnceAndNeverSilentlyLost() {
        val tx = "tx-${System.nanoTime()}"
        val intent = Intent(Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION)
            .putExtra("data", MmsTestPdus.notification(tx, "http://mmsc.invalid/$tx", "+33611223344"))
        MmsDeliverReceiver().onReceive(context, intent)

        val store = MessageStore.get(context)
        assertTrue("a bubble must appear", waitFor { store.messages.value.any { it.mms && !it.outgoing } })
        val m = store.messages.value.single { it.mms && !it.outgoing }
        assertEquals("+33611223344", m.address)
        assertTrue(m.status == MessageStatus.SENDING || m.status == MessageStatus.FAILED)
        assertTrue("the operator's announcement must be remembered for a retry", store.pending(m.id) != null)

        // Whatever the platform answers, after a long wait the message is shown as not received (never stuck as "being received").
        store.expireStaleDownloads(olderThanMs = System.currentTimeMillis() + 1)
        assertEquals(MessageStatus.FAILED, store.status(m.id))
    }
}
