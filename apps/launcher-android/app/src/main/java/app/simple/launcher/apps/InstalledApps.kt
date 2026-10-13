package app.simple.launcher.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.simple.launcher.config.AppPolicy
import java.util.Locale

/** An app that can be opened from a home tile: its package and the name the person knows it by. */
data class AppEntry(val packageName: String, val label: String)

/** What a tile shows for an app: its own name and its own icon (a familiar picture beats any word). */
data class AppInfo(val label: String, val icon: ImageBitmap?)

/** The apps installed on the phone, as the launcher sees them. Only launchable apps (those with a launcher icon) are ever listed. */
object InstalledApps {
    private val launcherIntent get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    /** Every launchable app except [self], by name (accents and capitals ignored). */
    fun launchable(pm: PackageManager, self: String): List<AppEntry> {
        val found = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }
        return found
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != self && AppPolicy.isAllowed(it.packageName) && it.label.isNotBlank() }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    /** Name and icon of an installed app, or null if it is not (or no longer) installed. */
    fun info(pm: PackageManager, packageName: String): AppInfo? =
        try {
            val label = pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
            AppInfo(label, icon(pm, packageName))
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

    /** The intent that opens the app, or null if it has no launcher activity (or is gone). */
    fun launchIntent(pm: PackageManager, packageName: String): Intent? =
        if (!AppPolicy.isAllowed(packageName)) null else pm.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun icon(pm: PackageManager, packageName: String): ImageBitmap? =
        try {
            val d = pm.getApplicationIcon(packageName)
            val size = 192
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, size, size)
            d.draw(Canvas(bitmap))
            bitmap.asImageBitmap()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: RuntimeException) {
            null // an icon that cannot be drawn: the tile shows the generic picture
        }
}
