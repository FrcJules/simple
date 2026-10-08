package app.presence.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.presence.launcher.mms.PhotoCompressor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

/** Real bitmaps and the real JPEG encoder: what a modern phone camera produces must fit what an operator accepts. */
class PhotoCompressorEmulatorTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** A noisy 4000x3000 picture (noise compresses badly, so this is a hard case), saved as a big JPEG like a camera would. */
    private fun bigPhoto(): File {
        val bmp = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        val rnd = Random(42)
        repeat(4000) {
            paint.color = Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            canvas.drawRect(rnd.nextInt(4000).toFloat(), rnd.nextInt(3000).toFloat(), rnd.nextInt(4000).toFloat(), rnd.nextInt(3000).toFloat(), paint)
        }
        return File(context.cacheDir, "big-photo.jpg").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } }
    }

    @Test
    fun aBigPhotoIsReducedToWhatAnOperatorAccepts() {
        val photo = bigPhoto()
        assertTrue("the test photo must be big", photo.length() > 1_000_000)
        val out = PhotoCompressor.compress(context, Uri.fromFile(photo))
        assertNotNull(out)
        assertTrue("size ${out!!.size}", out.size <= PhotoCompressor.MAX_BYTES)
        assertEquals(0xFF.toByte(), out[0])
        assertEquals(0xD8.toByte(), out[1])
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(out, 0, out.size, o)
        assertTrue("longest side ${maxOf(o.outWidth, o.outHeight)}", maxOf(o.outWidth, o.outHeight) <= PhotoCompressor.MAX_SIDE)
        assertTrue(o.outWidth > 0 && o.outHeight > 0)
        // the 4:3 proportions are kept
        assertEquals(4.0 / 3.0, o.outWidth.toDouble() / o.outHeight, 0.02)
    }

    @Test
    fun somethingThatIsNotAPictureIsRefusedNotACrash() {
        val junk = File(context.cacheDir, "junk.jpg").also { it.writeBytes(ByteArray(2000) { i -> i.toByte() }) }
        assertNull(PhotoCompressor.compress(context, Uri.fromFile(junk)))
        assertNull(PhotoCompressor.compress(context, Uri.fromFile(File(context.cacheDir, "does-not-exist.jpg"))))
    }
}
