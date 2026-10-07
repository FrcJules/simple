package app.presence.launcher.photos

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.IOException

/**
 * Photos chosen by the family for a contact, kept in the app's private storage (no extra permission, nothing
 * written to the phone's contacts). They take precedence over the photo stored in the contact.
 */
class ContactPhotoStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "contact_photos")

    fun uriFor(contactId: Long): String? = File(dir, "$contactId.jpg").takeIf { it.exists() }?.let { Uri.fromFile(it).toString() }

    /** Deletes every photo chosen through the app ("Effacer les photos" in Réglages famille). */
    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun remove(contactId: Long) {
        File(dir, "$contactId.jpg").delete()
    }

    /** Scales the picked image down to [MAX_SIDE] px, fixes its rotation and stores it as JPEG. */
    fun save(contactId: Long, resolver: ContentResolver, source: Uri): Boolean =
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
            val decoded = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (decoded == null) {
                false
            } else {
                val orientation = resolver.openInputStream(source)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                } ?: 1
                val degrees = rotationDegrees(orientation)
                val bitmap = if (degrees == 0) decoded else Bitmap.createBitmap(
                    decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees.toFloat()) }, true,
                )
                dir.mkdirs()
                val tmp = File(dir, "$contactId.tmp")
                tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                tmp.renameTo(File(dir, "$contactId.jpg"))
            }
        } catch (e: IOException) {
            false
        } catch (e: RuntimeException) {
            false
        }

    companion object {
        const val MAX_SIDE = 512

        /** Power-of-two decode factor keeping the longest side at or above [max]. */
        fun sampleSizeFor(width: Int, height: Int, max: Int = MAX_SIDE): Int {
            var side = maxOf(width, height)
            var sample = 1
            while (side / 2 >= max) {
                side /= 2
                sample *= 2
            }
            return sample
        }

        /** EXIF orientation values: 6 = rotated 90°, 3 = 180°, 8 = 270°. */
        fun rotationDegrees(orientation: Int): Int = when (orientation) {
            6 -> 90
            3 -> 180
            8 -> 270
            else -> 0
        }
    }
}
