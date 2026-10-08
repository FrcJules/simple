package app.presence.launcher.mms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Operators accept only small MMS (often ~300 KB). A photo from a modern phone is several MB, so it is reduced here: scaled
 * down and re-encoded as JPEG until it fits. Only the pixels are kept: the original file, its location and its metadata
 * (GPS, camera, date) never leave the phone.
 */
object PhotoCompressor {
    const val MAX_BYTES = 280_000
    const val MAX_SIDE = 1280

    /**
     * The smallest reduction that fits: tries from the best quality at [startSide] pixels down to a floor, then shrinks the
     * picture by a quarter and starts again. [encode] gives the JPEG for a (longest side, quality) pair. Null if nothing fits.
     */
    internal fun fit(startSide: Int, maxBytes: Int, encode: (side: Int, quality: Int) -> ByteArray?): ByteArray? {
        var side = startSide
        while (side >= MIN_SIDE) {
            for (quality in QUALITIES) {
                val bytes = encode(side, quality) ?: return null
                if (bytes.size <= maxBytes) return bytes
            }
            side = side * 3 / 4
        }
        return null
    }

    fun compress(context: Context, source: Uri): ByteArray? {
        val resolver = context.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // With inJustDecodeBounds, decodeStream returns null *by design* and only fills in the dimensions: the result must not be tested.
            (resolver.openInputStream(source) ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > MAX_SOURCE_PIXELS) return null
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            // Decode already reduced (powers of two) so a 48 Mpx photo never sits whole in memory.
            var sample = 1
            while (longest / (sample * 2) >= MAX_SIDE) sample *= 2
            val decoded = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val upright = rotate(flattenOnWhite(decoded), orientation(context, source))
            try {
                fit(MAX_SIDE, MAX_BYTES) { side, quality -> encode(upright, side, quality) }
            } finally {
                upright.recycle()
            }
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null // a third-party picture provider may fail in any way: the picture is "unreadable", the app lives on
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun encode(bitmap: Bitmap, side: Int, quality: Int): ByteArray? {
        val scale = minOf(1f, side.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        return try {
            ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    /** JPEG has no transparency: a transparent PNG would turn black, so it is laid on white first. */
    private fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(flat).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
        bitmap.recycle()
        return flat
    }

    private fun orientation(context: Context, source: Uri): Int =
        try {
            context.contentResolver.openInputStream(source)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private val QUALITIES = intArrayOf(85, 75, 65, 55, 45)
    private const val MIN_SIDE = 320
    private const val MAX_SOURCE_PIXELS = 120_000_000L
}
