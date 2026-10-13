package app.simple.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast

/** System intents behind the home tiles. Failures show a friendly message, never a crash. */
object LauncherActions {
    fun camera(c: Context) = launch(c, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    fun gallery(c: Context) =
        launch(c, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))

    fun messages(c: Context) =
        launch(c, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))

    /** [direct] = true places the call immediately (needs CALL_PHONE); otherwise opens the dialer. */
    fun call(c: Context, number: String, direct: Boolean) {
        if (!isDialable(number)) {
            Toast.makeText(c, R.string.number_invalid, Toast.LENGTH_LONG).show()
            return
        }
        val uri = Uri.parse(telUri(number))
        try {
            c.startActivity(Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: SecurityException) {
            launch(c, Intent(Intent.ACTION_DIAL, uri)) // permission revoked: fall back to the dialer
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(c, R.string.app_missing, Toast.LENGTH_LONG).show()
        }
    }

    /** Opens the messaging app on a draft: Play does not allow us to send SMS ourselves (ADR 0002). */
    fun smsDraft(c: Context, number: String, text: String) {
        if (!isDialable(number)) {
            Toast.makeText(c, R.string.number_invalid, Toast.LENGTH_LONG).show()
            return
        }
        launch(c, Intent(Intent.ACTION_SENDTO, Uri.parse(smsUri(number))).putExtra("sms_body", text))
    }

    private fun launch(c: Context, intent: Intent) {
        try {
            c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(c, R.string.app_missing, Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(c, R.string.app_missing, Toast.LENGTH_LONG).show()
        }
    }
}
