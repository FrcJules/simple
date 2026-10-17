package app.simple.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Whether we are the phone's home screen, and how to ask the family to make us one: Android never offers it by itself. */
object HomeRole {
    fun isHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
        return home?.activityInfo?.packageName == context.packageName
    }

    /** The system screen "Set Simple as your home app?"; before Android 10 the list of home apps in the settings. */
    fun requestIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }
}
