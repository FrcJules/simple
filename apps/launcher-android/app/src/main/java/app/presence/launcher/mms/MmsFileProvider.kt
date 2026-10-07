package app.presence.launcher.mms

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Lets the platform's MMS service read and write our transfer files (it runs in another process and needs a `content://`
 * address). It serves nothing else: only files with the random names created by [MmsFiles], from one cache folder.
 * Exported because the platform must reach it; the 128-bit file names are the access control, and the files live minutes.
 */
class MmsFileProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException()
        val file: File = MmsFiles(ctx).transferFile(uri.lastPathSegment.orEmpty()) ?: throw FileNotFoundException("not a transfer file")
        if (!file.exists()) throw FileNotFoundException("gone")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
