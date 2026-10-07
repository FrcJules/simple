package app.presence.launcher

import androidx.test.platform.app.InstrumentationRegistry
import app.presence.launcher.messages.MessageStatus
import app.presence.launcher.messages.MessageStore
import app.presence.launcher.sms.SendResult
import app.presence.launcher.sms.SmsComponents
import app.presence.launcher.sms.SmsRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The real telephony stack on the emulator: we become the default SMS app (this also proves the manifest meets the role's
 * requirements), send an SMS to the emulator's own number, and must receive it back through [SmsDeliverReceiver].
 */
class SmsEndToEndTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    /**
     * The role is granted by scripts/ci-emulator-tests.sh *before* the tests start, and taken back after: granting runtime
     * permissions to a running app makes Android kill its process, which here is also the test runner.
     */
    @Before
    fun theAppMustAlreadyBeTheSmsApp() {
        assertTrue(
            "the app must be the default SMS app (and so be eligible for the role): see scripts/ci-emulator-tests.sh",
            SmsRole.isHeld(context),
        )
        MessageStore.get(context).clear()
    }

    @Test
    fun anSmsSentToTheEmulatorItselfIsSentThenReceivedAndKept() {
        val text = "ping ${System.nanoTime()}"
        val result = SmsComponents.sender(context).send("5554", text, "Moi")
        val id = (result as SendResult.Started).messageId

        val store = MessageStore.get(context)
        val deadline = System.currentTimeMillis() + 90_000
        fun sentOk() = store.status(id).let { it == MessageStatus.SENT || it == MessageStatus.DELIVERED }
        fun received() = store.messages.value.any { !it.outgoing && it.text == text }
        while (!(sentOk() && received()) && System.currentTimeMillis() < deadline) Thread.sleep(500)

        assertTrue("the outgoing message must reach SENT, was ${store.status(id)}", sentOk())
        val incoming = store.messages.value.single { !it.outgoing && it.text == text }
        assertEquals(Channel_SMS, incoming.channel)
        assertTrue(incoming.address.orEmpty().endsWith("5554"))
        assertEquals(false, incoming.read)
        assertEquals(1, store.unreadCount())
    }

    private companion object {
        val Channel_SMS = app.presence.launcher.notifications.Channel.SMS
    }
}
