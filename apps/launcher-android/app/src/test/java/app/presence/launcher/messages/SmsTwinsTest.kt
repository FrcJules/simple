package app.presence.launcher.messages

import app.presence.launcher.notifications.Channel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTwinsTest {
    private fun sent(id: String, text: String = "Salut", address: String? = "+33611223344", at: Long = 1_000_000) =
        StoredMessage(id, "Jean-Baptiste", Channel.SMS, text, at, read = true, outgoing = true, address = address)

    private fun received(id: String, text: String = "Salut", address: String? = null, at: Long = 1_000_000) =
        StoredMessage(id, "Jean-Baptiste", Channel.SMS, text, at, address = address)

    @Test fun theSystemRecordOfOurOwnSendIsACopy() {
        assertTrue(SmsTwins.isCopyOfSent(sent("sms-12", address = "0611223344", at = 1_030_000), sent("out-a")))
    }

    @Test fun anotherTextAnotherNumberOrAnotherTimeIsNotACopy() {
        val local = sent("out-a")
        assertFalse(SmsTwins.isCopyOfSent(sent("sms-1", text = "Autre"), local))
        assertFalse(SmsTwins.isCopyOfSent(sent("sms-1", address = "0699999999"), local))
        assertFalse(SmsTwins.isCopyOfSent(sent("sms-1", at = 1_000_000 + 6 * 60_000), local))
        assertFalse(SmsTwins.isCopyOfSent(sent("sms-1", address = null), local)) // no number: never guessed
    }

    @Test fun onlyOurOwnRandomIdsAreLocalSends() {
        assertTrue(SmsTwins.isLocalSent(sent("out-1")))
        assertFalse(SmsTwins.isLocalSent(sent("sms-1")))
        assertFalse(SmsTwins.isLocalSent(received("x")))
    }

    @Test fun aNotificationMessageAndTheSystemRowOfTheSameSmsAreTwins() {
        assertTrue(SmsTwins.isNotificationTwin(received("sms-7", address = "+33611223344", at = 1_060_000), received("uuid-1")))
        assertFalse(SmsTwins.isNotificationTwin(received("sms-7", at = 1_000_000 + 4 * 60_000), received("uuid-1")))
        assertFalse(SmsTwins.isNotificationTwin(received("sms-7", text = "Autre"), received("uuid-1")))
        assertFalse(SmsTwins.isNotificationTwin(received("sms-7"), received("sms-8"))) // two system rows are never twins
    }
}
