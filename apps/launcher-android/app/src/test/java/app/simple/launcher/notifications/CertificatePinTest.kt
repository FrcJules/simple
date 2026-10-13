package app.simple.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificatePinTest {
    private val der = "certificate-bytes".toByteArray()

    @Test
    fun sha256IsUppercaseHexOf64Chars() {
        val h = CertificatePin.sha256Hex(der)
        assertEquals(64, h.length)
        assertEquals(h.uppercase(), h)
    }

    @Test
    fun knownSha256Vector() =
        // SHA-256("abc")
        assertEquals("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD", CertificatePin.sha256Hex("abc".toByteArray()))

    @Test
    fun matchesOnlyTheExactCertificate() {
        val pin = CertificatePin.sha256Hex(der)
        assertTrue(CertificatePin.matches(der, pin))
        assertFalse(CertificatePin.matches("other".toByteArray(), pin))
    }

    @Test
    fun normalizeAcceptsSeparatorsAndRejectsGarbage() {
        val pin = CertificatePin.sha256Hex(der)
        assertEquals(pin, CertificatePin.normalize(pin.chunked(2).joinToString(":")))
        assertNull(CertificatePin.normalize(pin.dropLast(2)))
        assertNull(CertificatePin.normalize("not a fingerprint"))
    }
}
