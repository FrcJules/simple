package app.simple.launcher.messages

import app.simple.launcher.notifications.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThreadKeyTest {
    private fun sms(address: String?, name: String = "x") = ThreadKey.of(Channel.SMS, address, name)

    @Test
    fun differentWritingsOfTheSameFrenchMobileShareAThread() {
        val k = sms("0612345678")
        assertEquals(k, sms("+33612345678"))
        assertEquals(k, sms("+33 6 12 34 56 78"))
        assertEquals(k, sms("06.12.34.56.78"))
    }

    @Test
    fun differentNumbersDoNotShareAThread() = assertNotEquals(sms("0612345678"), sms("0612345679"))

    @Test
    fun alphanumericSendersAndShortCodesKeepTheirOwnThread() {
        assertNotEquals(sms("AMAZON"), sms("36105"))
        assertEquals(sms("Amazon"), sms("AMAZON"))
    }

    @Test
    fun whatsappNeverMergesWithSms() {
        val wa = ThreadKey.of(Channel.WHATSAPP, "0612345678", "Marie")
        assertNotEquals(wa, ThreadKey.of(Channel.SMS, "0612345678", "Marie"))
        assertEquals(wa, ThreadKey.of(Channel.WHATSAPP, null, " marie "))
    }

    @Test
    fun smsWithoutAddressFallsBackToTheName() = assertEquals("sms:name:marie", sms(null, "Marie"))
}
