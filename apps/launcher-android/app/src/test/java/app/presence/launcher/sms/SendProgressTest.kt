package app.presence.launcher.sms

import android.provider.Telephony
import app.presence.launcher.messages.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SendProgressTest {
    private val gateway = FakeGateway()
    private val statuses = mutableMapOf("m" to MessageStatus.SENDING)
    private val progress = SendProgress(statuses::get, { id, s -> statuses[id] = s }, gateway)

    @Test
    fun aSingleSuccessfulPartMakesTheMessageSent() {
        progress.onSent("m", 7L, 0, 1, ok = true)
        assertEquals(MessageStatus.SENT, statuses["m"])
        assertEquals(Telephony.Sms.MESSAGE_TYPE_SENT, gateway.types[7L])
    }

    @Test
    fun aMultipartMessageIsSentOnlyAfterItsLastPart() {
        progress.onSent("m", 7L, 0, 3, ok = true)
        progress.onSent("m", 7L, 1, 3, ok = true)
        assertEquals(MessageStatus.SENDING, statuses["m"])
        progress.onSent("m", 7L, 2, 3, ok = true)
        assertEquals(MessageStatus.SENT, statuses["m"])
    }

    @Test
    fun anyFailedPartFailsTheMessageForGood() {
        progress.onSent("m", 7L, 0, 2, ok = false)
        progress.onSent("m", 7L, 1, 2, ok = true) // a later success must not hide the failure
        assertEquals(MessageStatus.FAILED, statuses["m"])
        assertEquals(Telephony.Sms.MESSAGE_TYPE_FAILED, gateway.types[7L])
    }

    @Test
    fun onlyASentMessageCanBecomeDelivered() {
        progress.onDelivered("m", 0, 1, ok = true)
        assertEquals(MessageStatus.SENDING, statuses["m"])
        progress.onSent("m", null, 0, 1, ok = true)
        progress.onDelivered("m", 0, 1, ok = true)
        assertEquals(MessageStatus.DELIVERED, statuses["m"])
    }

    @Test
    fun unknownMessagesAreIgnored() = progress.onSent("zzz", null, 0, 1, ok = false)
}
