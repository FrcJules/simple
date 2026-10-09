package app.presence.launcher.config

import org.json.JSONArray
import org.json.JSONObject

/** Writes a [UiConfig] back as the JSON the parser reads (the family's editor changes the rubriques, then stores the result). */
object UiConfigJson {
    fun write(config: UiConfig): String {
        val t = config.theme
        val theme = JSONObject()
            .put("background", hex(t.background)).put("text", hex(t.text)).put("primary", hex(t.primary))
            .put("onPrimary", hex(t.onPrimary)).put("tile", hex(t.tile)).put("accent", hex(t.accent))
            .put("fontScale", t.fontScale.toDouble())
        val tiles = JSONArray()
        config.home.tiles.forEach { tile ->
            val o = JSONObject().put("id", tile.id).put("action", tile.action.wire).put("icon", tile.icon.wire)
            tile.label?.let { o.put("label", it) }
            tile.app?.let { o.put("package", it) }
            tiles.put(o)
        }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("theme", theme)
            .put("home", JSONObject().put("columns", config.home.columns).put("tiles", tiles))
            .toString(2)
    }

    private fun hex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)
}
