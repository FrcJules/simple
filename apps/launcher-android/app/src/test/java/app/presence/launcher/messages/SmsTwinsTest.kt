package app.presence.launcher.messages

import app.presence.launcher.notifications.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SmsTwinsTest {
    private fun sent(id: String, text: String = "Salut", address: String? = "+33611223344", at: Long = 1_000_000, mms: Boolean = false) =
        StoredMessage(id, "Jean-Baptiste", Channel.SMS, text, at, read = true, outgoing = true, address = address, mms = mms)

    @Test fun theSystemRecordOfOurOwnSendIsItsTwin() {
        val local = sent("out-a")
        assertEquals(local, SmsTwins.twinOf(sent("sms-12", address = "0611223344", at = 1_030_000), listOf(local)))
    }

    @Test fun anotherTextNumberTimeOrNoNumberIsNeverATwin() {
        val local = listOf(sent("out-a"))
        assertNull(SmsTwins.twinOf(sent("sms-1", text = "Autre"), local))
        assertNull(SmsTwins.twinOf(sent("sms-1", address = "0699999999"), local))
        assertNull(SmsTwins.twinOf(sent("sms-1", at = 1_000_000 + 6 * 60_000), local))
        assertNull(SmsTwins.twinOf(sent("sms-1", address = null), listOf(sent("out-b", address = null)))) // no number: never guessed
    }

    @Test fun onlyPlainSystemRowsAreCopiesNeverPicturesOrOtherIds() {
        val local = listOf(sent("out-a"))
        assertNull(SmsTwins.twinOf(sent("sms-1", mms = true), local))
        assertNull(SmsTwins.twinOf(sent("mms-ab12"), local))
        assertNull(SmsTwins.twinOf(sent("sms-3f2a9c1e-uuid"), local))
        assertFalse(SmsTwins.isLocalSent(sent("out-a", mms = true)))
        assertTrue(SmsTwins.isLocalSent(sent("out-a")))
    }

    @Test fun theClosestInTimeIsChosen() {
        val early = sent("out-1", at = 1_000_000)
        val late = sent("out-2", at = 1_200_000)
        assertEquals(late, SmsTwins.twinOf(sent("sms-9", at = 1_190_000), listOf(early, late)))
    }
}
