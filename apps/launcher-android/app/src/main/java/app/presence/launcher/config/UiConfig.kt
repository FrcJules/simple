package app.presence.launcher.config

import androidx.annotation.StringRes
import app.presence.launcher.R

/** What a home tile does. A fixed whitelist: the JSON can reorder and relabel tiles but never run arbitrary code. */
enum class TileAction(val wire: String, @StringRes val defaultLabel: Int, val defaultIcon: TileIcon) {
    CALL("call", R.string.tile_call, TileIcon.CALL),
    MESSAGES("messages", R.string.tile_messages, TileIcon.SMS),
    CAMERA("camera", R.string.tile_camera, TileIcon.CAMERA),
    GALLERY("gallery", R.string.tile_photos, TileIcon.PHOTOS);

    companion object {
        fun fromWire(s: String): TileAction? = entries.firstOrNull { it.wire == s }
    }
}

enum class TileIcon(val wire: String) {
    CALL("call"), SMS("sms"), CAMERA("camera"), PHOTOS("photos"),
    FAVORITE("favorite"), HOME("home"), PERSON("person"), MUSIC("music"), TV("tv"), SUN("sun");

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

data class TileConfig(val id: String, val action: TileAction, val label: String?, val icon: TileIcon)

data class HomeConfig(val columns: Int, val tiles: List<TileConfig>) {
    companion object {
        val Default = HomeConfig(
            columns = 2,
            tiles = TileAction.entries.map { TileConfig(it.wire, it, label = null, icon = it.defaultIcon) },
        )
    }
}

data class UiConfig(val theme: ThemeConfig, val home: HomeConfig) {
    companion object {
        val Default = UiConfig(ThemeConfig.Default, HomeConfig.Default)
    }
}
