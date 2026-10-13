package app.presence.launcher.config

/**
 * Apps that must never become a home tile: they change the phone, install or remove apps, or give someone else control. Jean-Pierre
 * must not be one touch away from them, whatever a file says or a helper picks by mistake. Applied when a tile file is read, when the
 * list of apps is built and when a tile is opened.
 */
object AppPolicy {
    /** Présence itself (opening it from its own home screen would open the family's settings). */
    private const val SELF = "app.simple.launcher"

    private val exact = setOf(
        SELF,
        "com.android.vending", "com.sec.android.app.samsungapps", "com.huawei.appmarket", "com.xiaomi.market", "org.fdroid.fdroid",
        "com.aurora.store", "com.amazon.venezia", "com.heytap.market", "com.oppo.market",
        "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller", "com.android.shell", "com.android.managedprovisioning",
        "com.rustdesk.rustdesk", "com.anydesk.anydeskandroid", "com.teamviewer.quicksupport.market", "com.teamviewer.teamviewer.market.mobile",
    )

    /** Any package starting with these (Settings and its sub-apps of every maker). */
    private val prefixes = listOf("com.android.settings", "com.samsung.android.settings", "com.miui.securitycenter", "com.coloros.safecenter")

    fun isAllowed(packageName: String): Boolean = packageName !in exact && prefixes.none { packageName.startsWith(it) }
}
