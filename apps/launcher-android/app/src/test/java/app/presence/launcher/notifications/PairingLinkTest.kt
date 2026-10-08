package app.presence.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingLinkTest {
    private val fp = "88A2899B9DF00490B76854FB11FF4F1019470BA0D5FB7CD68BC700E4B4B7B68F"
    private val token = "0123456789abcdef0123"
    private fun link(u: String = "https%3A%2F%2F192.168.1.66%3A8788", t: String = token, f: String = fp, extra: String = "") =
        "presence://pair?u=$u&t=$t&f=$f$extra"

    @Test
    fun readsTheLinkTheHubBuilds() {
        val c = PairingLink.parse(link())!!
        assertEquals("https://192.168.1.66:8788", c.baseUrl)
        assertEquals(token, c.token)
        assertEquals(fp, c.certSha256)
    }

    @Test
    fun acceptsHomeNetworkNames() = assertEquals("https://presence.local:8788", PairingLink.parse(link(u = "https%3A%2F%2Fpresence.local%3A8788"))!!.baseUrl)

    @Test
    fun refusesWhatTypedValuesWouldRefuse() {
        assertNull(PairingLink.parse(link(u = "https%3A%2F%2Fexample.com%3A8788"))) // not on the home network
        assertNull(PairingLink.parse(link(u = "http%3A%2F%2F192.168.1.66%3A8788"))) // not https
        assertNull(PairingLink.parse(link(t = "short")))
        assertNull(PairingLink.parse(link(f = "abc")))
        assertNull(PairingLink.parse(link(u = "https%3A%2F%2F192.168.1.5%40evil.com")))
    }

    @Test
    fun refusesOtherSchemesHostsAndMissingFields() {
        assertNull(PairingLink.parse("https://pair?u=x"))
        assertNull(PairingLink.parse("presence://settings?u=https%3A%2F%2F192.168.1.66&t=$token&f=$fp"))
        assertNull(PairingLink.parse("presence://pair"))
        assertNull(PairingLink.parse("presence://pair?u=https%3A%2F%2F192.168.1.66%3A8788&t=$token"))
        assertNull(PairingLink.parse(null))
        assertNull(PairingLink.parse("not a link at all"))
    }

    @Test
    fun aFieldGivenTwiceIsRefusedNotGuessed() = assertNull(PairingLink.parse(link(extra = "&t=another-token-0123456789")))

    @Test
    fun anOversizedLinkIsRefused() = assertNull(PairingLink.parse(link(extra = "&x=" + "a".repeat(PairingLink.MAX_LENGTH))))

    @Test
    fun unknownExtraFieldsAreIgnored() = assertEquals(token, PairingLink.parse(link(extra = "&v=2"))!!.token)

    @Test
    fun badPercentEncodingIsRefusedNotACrash() = assertNull(PairingLink.parse(link(u = "https%3A%2F%2F192.168.1.66%ZZ")))
}
