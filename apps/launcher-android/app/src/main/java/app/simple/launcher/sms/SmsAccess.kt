package app.simple.launcher.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import app.simple.launcher.BuildConfig

/**
 * What we may do with SMS right now. Being the default SMS app is the full mode (it also carries MMS); when Android does not grant that
 * role, the SMS permissions alone give a direct mode: old and new SMS are copied from the system database and messages are sent
 * directly. Without either, SMS only appear through notifications and writing goes to the other messaging app.
 * The direct mode exists in test builds only: Google Play allows SMS permissions to the default SMS app alone (ADR 0005, 0007).
 */
object SmsAccess {
    /** False in the Play build: there, SMS permissions count only together with the role, even if Android still lists them as granted. */
    @VisibleForTesting
    internal var directModeAllowed: Boolean = BuildConfig.SMS_DIRECT_MODE_OFFERED

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun canRead(context: Context) = SmsRole.isHeld(context) || (directModeAllowed && granted(context, Manifest.permission.READ_SMS))

    fun canSend(context: Context) = SmsRole.isHeld(context) || (directModeAllowed && granted(context, Manifest.permission.SEND_SMS))

    /** Told of each received SMS (the broadcast receiver of the direct mode needs it). */

    /** Both: write a message inside Simple and see the conversation. */
    fun direct(context: Context) = canRead(context) && canSend(context)
}
