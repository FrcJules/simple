package app.presence.launcher

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Saved in the app's own media dir (the test package's dir is not writable); scripts/ci-emulator-tests.sh pulls it with adb. */
object Screenshots {
    private fun dir(): File {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        return File(ctx.externalMediaDirs.first(), "screenshots").apply { mkdirs() }
    }

    fun file(name: String) = File(dir(), "$name.png")

    fun save(name: String, bitmap: Bitmap) {
        file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
