package app.presence.launcher.mms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoCompressorTest {
    /** A fake encoder: the size grows with the picture and with the quality. */
    private fun encoder(log: MutableList<Pair<Int, Int>>) = { side: Int, quality: Int ->
        log += side to quality
        ByteArray(side * quality / 2)
    }

    @Test
    fun keepsTheBestQualityWhenItAlreadyFits() {
        val log = mutableListOf<Pair<Int, Int>>()
        val r = PhotoCompressor.fit(1280, 1_000_000, encoder(log))!!
        assertEquals(1280 * 85 / 2, r.size)
        assertEquals(listOf(1280 to 85), log)
    }

    @Test
    fun lowersTheQualityBeforeShrinkingThePicture() {
        val log = mutableListOf<Pair<Int, Int>>()
        PhotoCompressor.fit(1280, 1280 * 60 / 2, encoder(log))!! // fits at quality 55
        assertEquals(listOf(1280 to 85, 1280 to 75, 1280 to 65, 1280 to 55), log)
    }

    @Test
    fun shrinksByAQuarterWhenQualityAloneIsNotEnough() {
        val log = mutableListOf<Pair<Int, Int>>()
        PhotoCompressor.fit(1280, 960 * 45 / 2, encoder(log))!! // only fits at 960 px, quality 45
        assertEquals(960 to 45, log.last())
        assertEquals(5, log.count { it.first == 1280 })
    }

    @Test
    fun givesUpRatherThanSendingSomethingTooLarge() {
        assertNull(PhotoCompressor.fit(1280, 10, encoder(mutableListOf())))
    }

    @Test
    fun anEncoderFailureStopsEverything() = assertNull(PhotoCompressor.fit(1280, 1_000_000) { _, _ -> null })
}
