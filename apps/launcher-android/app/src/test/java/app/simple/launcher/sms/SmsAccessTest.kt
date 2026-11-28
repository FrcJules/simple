package app.simple.launcher.sms

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsAccessTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @After fun reset() { SmsAccess.directModeAllowed = true }

    private fun grantSmsPermissions() = shadowOf(app).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)

    @Test
    fun thePlayBuildNeverUsesSmsPermissionsWithoutTheRole() {
        grantSmsPermissions() // e.g. still granted after the person chose another SMS app again
        SmsAccess.directModeAllowed = false
        assertFalse(SmsRole.isHeld(app))
        assertFalse(SmsAccess.canRead(app))
        assertFalse(SmsAccess.canSend(app))
        assertFalse(SmsAccess.direct(app))
    }

    @Test
    fun testBuildsKeepTheDirectMode() {
        grantSmsPermissions()
        SmsAccess.directModeAllowed = true
        assertTrue(SmsAccess.direct(app))
    }
}
