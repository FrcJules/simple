package app.simple.launcher.config

import androidx.annotation.StringRes
import app.simple.launcher.R

/** What a home tile does. A fixed whitelist: the JSON can reorder and relabel tiles but never run arbitrary code. */
enum class TileAction(val wire: String, @StringRes val defaultLabel: Int, val defaultIcon: TileIcon) {
    CALL("call", R.string.tile_call, TileIcon.CALL),
    MESSAGES("messages", R.string.tile_messages, TileIcon.SMS),
    CAMERA("camera", R.string.tile_camera, TileIcon.CAMERA),
    GALLERY("gallery", R.string.tile_photos, TileIcon.PHOTOS),

    /** Opens one installed app chosen by the family (its package name is in [TileConfig.app]), with that app's own name and icon. */
    APP("app", R.string.tile_app, TileIcon.APPS);

    companion object {
        fun fromWire(s: String): TileAction? = entries.firstOrNull { it.wire == s }
    }
}

enum class TileIcon(val wire: String) {
    CALL("call"), SMS("sms"), CAMERA("camera"), PHOTOS("photos"),
    FAVORITE("favorite"), HOME("home"), PERSON("person"), MUSIC("music"), TV("tv"), SUN("sun"), APPS("apps");

    companion object {
        fun fromWire(s: String): TileIcon? = entries.firstOrNull { it.wire == s }
    }
}

/** Colors are ARGB ints. [fontScale] multiplies every text size (never below 1, so text stays at least 24 sp). */
data class ThemeConfig(
    val background: Int,
    val text: Int,
    val primary: Int,
    val onPrimary: Int,
    val tile: Int,
    val accent: Int,
    val fontScale: Float,
) {
    companion object {
        val Default = ThemeConfig(
            background = 0xFFFFFFFF.toInt(),
            text = 0xFF000000.toInt(),
            primary = 0xFF0B3D91.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            tile = 0xFFE3E8F2.toInt(),
            accent = 0xFFDCE6FF.toInt(),
            fontScale = 1f,
        )
    }
}

/** [app] is the package name of the app to open, present exactly for [TileAction.APP]. */
data class TileConfig(val id: String, val action: TileAction, val label: String?, val icon: TileIcon, val app: String? = null)

data class HomeConfig(val columns: Int, val tiles: List<TileConfig>) {
    companion object {
        val Default = HomeConfig(
            columns = 2,
            // The four built-in rubriques; app tiles are added by the family.
            tiles = TileAction.entries.filter { it != TileAction.APP }.map { TileConfig(it.wire, it, label = null, icon = it.defaultIcon) },
        )
    }
}

data class UiConfig(val theme: ThemeConfig, val home: HomeConfig) {
    companion object {
        val Default = UiConfig(ThemeConfig.Default, HomeConfig.Default)
    }
}
