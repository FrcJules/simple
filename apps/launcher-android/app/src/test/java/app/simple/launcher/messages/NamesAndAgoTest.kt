package app.simple.launcher.messages

import org.junit.Assert.assertEquals
import org.junit.Test

class NamesAndAgoTest {
    @Test
    fun namesMatchIgnoringCaseAccentsAndSpaces() {
        assertEquals(normalizeName("Hélène "), normalizeName("helene"))
        assertEquals(normalizeName("JEAN-PIERRE"), normalizeName("jean-pierre"))
    }

    @Test
    fun differentNamesDoNotMatch() = assert(normalizeName("Marie") != normalizeName("Maria"))

    @Test
    fun agoUnits() {
        val now = 100L * 24 * 3600 * 1000
        assertEquals(Ago(AgoUnit.NOW, 0), ago(now - 20_000, now))
        assertEquals(Ago(AgoUnit.MINUTES, 20), ago(now - 20 * 60_000, now))
        assertEquals(Ago(AgoUnit.HOURS, 3), ago(now - 3 * 3600_000, now))
        assertEquals(Ago(AgoUnit.DAYS, 2), ago(now - 49 * 3600_000, now))
    }

    @Test
    fun aMessageFromTheFutureIsJustNow() = assertEquals(Ago(AgoUnit.NOW, 0), ago(10_000, 5_000))
}
