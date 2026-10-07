package app.presence.launcher.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test
    fun blackOnWhiteIs21To1() = assertEquals(21.0, Contrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)

    @Test
    fun ratioIsSymmetric() {
        val a = 0xFF0B3D91.toInt()
        val b = 0xFFFFFFFF.toInt()
        assertEquals(Contrast.ratio(a, b), Contrast.ratio(b, a), 1e-9)
    }

    @Test
    fun defaultPaletteMeetsAaa() {
        val t = ThemeConfig.Default
        assertTrue(Contrast.ratio(t.text, t.background) >= 7.0)
        assertTrue(Contrast.ratio(t.text, t.tile) >= 7.0)
        assertTrue(Contrast.ratio(t.text, t.accent) >= 7.0)
        assertTrue(Contrast.ratio(t.onPrimary, t.primary) >= 7.0)
    }
}
