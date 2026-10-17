package app.simple.launcher.ui

import java.time.LocalDateTime
import java.util.Locale
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class HomeStatusTest {
    private val now = LocalDateTime.of(2026, 10, 8, 9, 5)

    @Test fun formatsTimeAndDateInFrench() {
        val s = HomeStatus.of(now, 80, false, Locale.FRANCE)
        assertEquals("09:05", s.time)
        assertEquals("Jeudi 8 octobre", s.date)
    }

    @Test fun lowBatteryAtTwentyOrLess() {
        assertTrue(HomeStatus.of(now, 20, false, Locale.FRANCE).batteryLow)
        assertFalse(HomeStatus.of(now, 21, false, Locale.FRANCE).batteryLow)
        assertFalse(HomeStatus.of(now, null, false, Locale.FRANCE).batteryLow)
    }

    @Test fun percentFromLevelAndScale() {
        assertEquals(50, HomeStatus.percentOf(50, 100))
        assertEquals(100, HomeStatus.percentOf(5, 5))
        assertNull(HomeStatus.percentOf(-1, 100))
        assertNull(HomeStatus.percentOf(10, 0))
    }

    @Test fun sevenQuickTapsOpenTheSettings() {
        val c = SecretTapCounter()
        repeat(6) { assertFalse(c.tap(1_000L + it * 300)) }
        assertTrue(c.tap(3_000))
        assertFalse(c.tap(3_100)) // starts over
    }

    @Test fun slowTapsNeverOpenThem() {
        val c = SecretTapCounter()
        repeat(50) { assertFalse(c.tap(it * 1_000L)) }
    }

    @Test fun clockWakesOnTheMinute() {
        assertEquals(60_000L, millisUntilNextMinute(java.time.LocalDateTime.of(2026, 10, 17, 9, 5, 0)))
        assertEquals(15_000L, millisUntilNextMinute(java.time.LocalDateTime.of(2026, 10, 17, 9, 5, 45)))
        assertEquals(1_000L, millisUntilNextMinute(java.time.LocalDateTime.of(2026, 10, 17, 9, 5, 59, 500_000_000))) // never spins
    }
}
