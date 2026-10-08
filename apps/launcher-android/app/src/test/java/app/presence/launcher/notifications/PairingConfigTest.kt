package app.presence.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingConfigTest {
    private val token = "0123456789abcdef0123"
    private val pin = "88A2899B9DF00490B76854FB11FF4F1019470BA0D5FB7CD68BC700E4B4B7B68F"

    @Test
    fun acceptsHomeNetworkAddresses() {
        assertEquals("https://192.168.1.20:8787", PairingConfig.parse(" https://192.168.1.20:8787/ ", token, pin)?.baseUrl)
        assertEquals("https://presence.local:8787", PairingConfig.parse("https://presence.local:8787", token, pin)?.baseUrl)
        assertEquals("https://172.16.0.5", PairingConfig.parse("https://172.16.0.5", token, pin)?.baseUrl)
    }

    @Test
    fun rejectsPublicOrMalformedAddresses() {
        assertNull(PairingConfig.parse("https://8.8.8.8", token, pin))
        assertNull(PairingConfig.parse("https://127.0.0.1", token, pin))
        assertNull(PairingConfig.parse("https://localhost", token, pin))
        assertNull(PairingConfig.parse("https://192.168.1.5@evil.com", token, pin))
        assertNull(PairingConfig.parse("https://172.32.0.1", token, pin))
        assertNull(PairingConfig.parse("https://example.com", token, pin))
        assertNull(PairingConfig.parse("ftp://192.168.1.2", token, pin))
        assertNull(PairingConfig.parse("not a url", token, pin))
    }

    @Test
    fun rejectsShortToken() = assertNull(PairingConfig.parse("https://192.168.1.2", "short", pin))

    @Test
    fun toStringHidesToken() = assertEquals("PairingConfig(baseUrl=https://10.0.0.1)", PairingConfig("https://10.0.0.1", token, pin).toString())

    @Test
    fun rejectsPlainHttp() = assertNull(PairingConfig.parse("http://192.168.1.2:8787", token, pin))

    @Test
    fun rejectsInvalidFingerprint() {
        assertNull(PairingConfig.parse("https://192.168.1.2", token, "abc"))
        assertNull(PairingConfig.parse("https://192.168.1.2", token, "Z".repeat(64)))
    }

    @Test
    fun acceptsColonSeparatedLowercaseFingerprint() {
        val spaced = pin.chunked(2).joinToString(":").lowercase()
        assertEquals(pin, PairingConfig.parse("https://192.168.1.2", token, spaced)?.certSha256)
    }

    @Test
    fun theStoredAddressIsRebuiltFromTheRealHost() {
        assertEquals("https://192.168.1.20:8787", PairingConfig.parse(" https://192.168.1.20:8787/ ", token, pin)?.baseUrl)
        assertEquals("https://presence.local", PairingConfig.parse("HTTPS://Presence.LOCAL", token, pin)?.baseUrl)
    }

    @Test
    fun refusesCredentialsPathsQueriesAndFragmentsThatCanHideTheRealHost() {
        listOf(
            "https://192.168.1.20:8787@10.9.9.9", "https://user:pw@192.168.1.20", "https://192.168.1.20/admin",
            "https://192.168.1.20/?next=evil", "https://192.168.1.20/#x", "https://192.168.1.20:70000",
        ).forEach { assertNull("$it must be refused", PairingConfig.parse(it, token, pin)) }
    }

    @Test
    fun refusesLeadingZerosInAddressesAndOddLocalNames() {
        assertNull(PairingConfig.parse("https://010.0.0.1", token, pin)) // read as octal (8.0.0.1) by some resolvers
        assertNull(PairingConfig.parse("https://172.016.0.1", token, pin))
        assertNull(PairingConfig.parse("https://-bad-.local", token, pin))
        assertNull(PairingConfig.parse("https://a..local", token, pin))
        assertEquals("https://my-hub.local", PairingConfig.parse("https://my-hub.local", token, pin)?.baseUrl)
    }
}
