package app.simple.launcher.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/** Whether we are the phone's default SMS app, and how to ask the person to make us one (ADR 0005). */
object SmsRole {
    fun isHeld(context: Context): Boolean = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    /** The system screen that asks "Use Simple as your SMS app?"; null if this phone cannot do SMS. */
    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java) ?: return null
            if (!rm.isRoleAvailable(RoleManager.ROLE_SMS)) return null
            return rm.createRequestRoleIntent(RoleManager.ROLE_SMS)
        }
        return Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
    }
}
