package app.simple.launcher.mms

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MmsFileProviderTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun register() {
        Robolectric.setupContentProvider(MmsFileProvider::class.java, MmsFiles.authority(context))
    }

    @Test
    fun servesOnlyTransferFilesWithTheirRandomNames() {
        val files = MmsFiles(context)
        val f = files.newTransferFile().also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val bytes = context.contentResolver.openInputStream(files.uriFor(f))!!.use { it.readBytes() }
        assertEquals(listOf<Byte>(1, 2, 3), bytes.toList())
    }

    @Test
    fun refusesAnythingElse() {
        val base = "content://${MmsFiles.authority(context)}/"
        listOf("messages.db", "..%2Ffiles%2Fmessages.db", "t-abc.dat", "t-${"a".repeat(32)}.dat").forEach { name ->
            try {
                context.contentResolver.openInputStream(Uri.parse(base + name))?.close()
                fail("$name must not be served")
            } catch (e: FileNotFoundException) {
                // expected
            }
        }
    }

    @Test
    fun hasNoDatabaseView() {
        assertNull(context.contentResolver.query(Uri.parse("content://${MmsFiles.authority(context)}/x"), null, null, null, null))
    }
}
