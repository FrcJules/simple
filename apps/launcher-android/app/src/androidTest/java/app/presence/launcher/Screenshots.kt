package app.presence.launcher

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Saved where AGP collects "additional test output" after `connectedAndroidTest`; the CI uploads it as an artifact. */
object Screenshots {
    private fun dir(): File {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        return File(ctx.externalMediaDirs.first(), "additional_test_output").apply { mkdirs() }
    }

    fun file(name: String) = File(dir(), "$name.png")

    fun save(name: String, bitmap: Bitmap) {
        file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
