package app.presence.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingConfigTest {
    private val token = "0123456789abcdef0123"

    @Test
    fun acceptsHomeNetworkAddresses() {
        assertEquals("http://192.168.1.20:8787", PairingConfig.parse(" http://192.168.1.20:8787/ ", token)?.baseUrl)
        assertEquals("http://presence.local:8787", PairingConfig.parse("http://presence.local:8787", token)?.baseUrl)
        assertEquals("http://172.16.0.5", PairingConfig.parse("http://172.16.0.5", token)?.baseUrl)
    }

    @Test
    fun rejectsPublicOrMalformedAddresses() {
        assertNull(PairingConfig.parse("http://8.8.8.8", token))
        assertNull(PairingConfig.parse("http://172.32.0.1", token))
        assertNull(PairingConfig.parse("http://example.com", token))
        assertNull(PairingConfig.parse("ftp://192.168.1.2", token))
        assertNull(PairingConfig.parse("not a url", token))
    }

    @Test
    fun rejectsShortToken() = assertNull(PairingConfig.parse("http://192.168.1.2", "short"))

    @Test
    fun toStringHidesToken() = assertEquals("PairingConfig(baseUrl=http://10.0.0.1)", PairingConfig("http://10.0.0.1", token).toString())
}
