package app.simple.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingLinkTest {
    private val fp = "88A2899B9DF00490B76854FB11FF4F1019470BA0D5FB7CD68BC700E4B4B7B68F"
    private val claim = "claim-0123456789abcdef"
    private fun link(u: String = "https%3A%2F%2F192.168.1.66%3A8788", s: String = claim, f: String = fp, extra: String = "") =
        "simple://pair?u=$u&s=$s&f=$f$extra"

    @Test
    fun readsTheLinkTheHubBuilds() {
        val o = PairingLink.parse(link())!!
        assertEquals("https://192.168.1.66:8788", o.baseUrl)
        assertEquals(claim, o.claim)
        assertEquals(fp, o.certSha256)
    }

    @Test
    fun theFingerprintIsShownInGroupsOfFour() = assertTrue(PairingLink.parse(link())!!.groupedFingerprint().startsWith("88A2 899B 9DF0 0490 "))

    @Test
    fun theClaimNeverAppearsInLogs() = assertFalse(PairingLink.parse(link())!!.toString().contains(claim))

    @Test
    fun acceptsHomeNetworkNames() = assertEquals("https://simple.local:8788", PairingLink.parse(link(u = "https%3A%2F%2Fsimple.local%3A8788"))!!.baseUrl)

    @Test
    fun refusesAddressesThatAreNotAPlainHomeHostAndPort() {
        assertNull(PairingLink.parse(link(u = "https%3A%2F%2Fexample.com%3A8788"))) // not on the home network
        assertNull(PairingLink.parse(link(u = "http%3A%2F%2F192.168.1.66%3A8788"))) // not https
        assertNull(PairingLink.parse(link(u = "https%3A%2F%2F192.168.1.66%3A8788%40evil.com"))) // credentials hiding the real host
        assertNull(PairingLink.parse(link(u = "https%3A%2F%2F192.168.1.66%3A8788%2Fpath")))
        assertNull(PairingLink.parse(link(u = "https%3A%2F%2F192.168.1.66%3A8788%3Fx%3D1")))
        assertNull(PairingLink.parse(link(u = "https%3A%2F%2F192.168.1.66%3A8788%23frag")))
        assertNull(PairingLink.parse(link(u = "https%3A%2F%2F192.168.1.66%3A99999")))
    }

    @Test
    fun refusesBadClaimsAndFingerprints() {
        assertNull(PairingLink.parse(link(s = "short")))
        assertNull(PairingLink.parse(link(s = "claim with spaces 0123456789")))
        assertNull(PairingLink.parse(link(f = "abc")))
    }

    @Test
    fun refusesOtherSchemesHostsAndMissingFields() {
        assertNull(PairingLink.parse("https://pair?u=x"))
        assertNull(PairingLink.parse("simple://settings?u=https%3A%2F%2F192.168.1.66&s=$claim&f=$fp"))
        assertNull(PairingLink.parse("simple://pair"))
        assertNull(PairingLink.parse("simple://pair?u=https%3A%2F%2F192.168.1.66%3A8788&s=$claim"))
        assertNull(PairingLink.parse(null))
        assertNull(PairingLink.parse("not a link at all"))
    }

    @Test
    fun theOldFormatWithALongLivedTokenIsNoLongerAccepted() =
        assertNull(PairingLink.parse("simple://pair?u=https%3A%2F%2F192.168.1.66%3A8788&t=0123456789abcdef0123&f=$fp"))

    @Test
    fun exactlyTheThreeFieldsAreAccepted() {
        assertNull(PairingLink.parse(link(extra = "&v=2"))) // extra fields could carry something we did not review
        assertNull(PairingLink.parse(link(extra = "&s=another-claim-0123456789"))) // the same field twice is ambiguous
    }

    @Test
    fun anOversizedLinkIsRefused() = assertNull(PairingLink.parse(link() + "&x=" + "a".repeat(PairingLink.MAX_LENGTH)))

    @Test
    fun badPercentEncodingIsRefusedNotACrash() = assertNull(PairingLink.parse(link(u = "https%3A%2F%2F192.168.1.66%ZZ")))
}
