package app.simple.launcher.mms

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Where MMS bytes live. Transfer files (what the platform downloads into, or sends from) are short-lived and have random
 * names; pictures of received messages are private files of the app, never written to the gallery.
 */
class MmsFiles(context: Context) {
    private val app = context.applicationContext
    private val transferDir = File(app.cacheDir, "mms-transfer")
    private val pictureDir = File(app.filesDir, "mms")

    /** An empty file the platform can write into, named so it cannot be guessed. */
    fun newTransferFile(): File {
        transferDir.mkdirs()
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return File(transferDir, "t-" + bytes.joinToString("") { "%02x".format(it) } + ".dat").also { it.createNewFile() }
    }

    /** The `content://` address the platform's MMS service uses to reach [file] (served by [MmsFileProvider]). */
    fun uriFor(file: File): Uri = Uri.Builder().scheme("content").authority(authority(app)).path(file.name).build()

    /** The transfer file called [name], or null if [name] is not one we create (no path tricks). */
    fun transferFile(name: String): File? = if (NAME.matches(name)) File(transferDir, name) else null

    fun readTransfer(file: File, maxBytes: Int = MmsPdu.MAX_PDU_BYTES): ByteArray? =
        try {
            if (!file.exists() || file.length() > maxBytes) null else file.readBytes()
        } catch (e: IOException) {
            null
        }

    fun writeTransfer(bytes: ByteArray): File? =
        try {
            newTransferFile().also { it.writeBytes(bytes) }
        } catch (e: IOException) {
            null
        }

    /** Saves one picture of a message; the name comes from us, never from the message. */
    fun savePicture(messageId: String, index: Int, image: ReceivedImage): File? =
        try {
            val dir = File(pictureDir, hash(messageId)).apply { mkdirs() }
            val target = File(dir, "$index.${extension(image.mime)}")
            // Written under a temporary name, then renamed: a crash never leaves half a picture that looks complete.
            val tmp = File(dir, "$index.tmp")
            FileOutputStream(tmp).use { it.write(image.bytes) }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                null
            } else target
        } catch (e: IOException) {
            null
        }

    /** Removes every picture of one message (a message that could not be completed). */
    fun deletePictures(messageId: String) {
        File(pictureDir, hash(messageId)).deleteRecursively()
    }

    /** A picture kept with a message (only paths inside our own picture folder are read). */
    fun readPicture(path: String): ByteArray? =
        try {
            val f = File(path)
            if (f.canonicalPath.startsWith(pictureDir.canonicalPath + File.separator) && f.length() <= MmsPdu.MAX_PART_BYTES) f.readBytes() else null
        } catch (e: IOException) {
            null
        }

    fun deleteQuietly(file: File?) {
        runCatching { file?.delete() }
    }

    /** Transfer files left behind (process killed, platform never answered) are removed once they are old. */
    fun cleanOldTransfers(olderThanMs: Long) {
        transferDir.listFiles()?.filter { it.lastModified() < olderThanMs }?.forEach { deleteQuietly(it) }
    }

    private fun extension(mime: String) = when (mime) {
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/bmp" -> "bmp"
        else -> "jpg"
    }

    companion object {
        private val NAME = Regex("t-[0-9a-f]{32}\\.dat")

        fun authority(context: Context) = "${context.packageName}.mms"

        internal fun hash(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(32)
    }
}
