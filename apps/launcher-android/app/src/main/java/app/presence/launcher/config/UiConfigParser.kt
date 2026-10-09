package app.presence.launcher.config

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

sealed interface ParseResult {
    data class Ok(val config: UiConfig) : ParseResult
    data class Error(val message: String) : ParseResult
}

/**
 * Strict validation of the UI configuration (see docs/customization.md). Anything invalid is rejected with a
 * plain-language reason: a bad file must never make the senior's phone unreadable.
 */
object UiConfigParser {
    const val MAX_CHARS = 20_000
    const val MAX_TILES = 8
    const val MAX_LABEL = 24
    const val MAX_PACKAGE = 150
    const val MIN_CONTRAST = 4.5
    const val MAX_FONT_SCALE = 1.6

    private val colorRegex = Regex("^#[0-9A-Fa-f]{6}$")

    /** An Android package name: dotted segments of letters, digits and underscores (so nothing else can ride along in a launch request). */
    private val packageRegex = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private class Invalid(message: String) : Exception(message)

    fun parse(json: String): ParseResult =
        try {
            ParseResult.Ok(parseOrThrow(json))
        } catch (e: Invalid) {
            ParseResult.Error(e.message.orEmpty())
        } catch (e: JSONException) {
            ParseResult.Error("Ce fichier n'est pas un JSON valide.")
        }

    private fun parseOrThrow(json: String): UiConfig {
        if (json.length > MAX_CHARS) throw Invalid("Le fichier est trop gros ($MAX_CHARS caractères maximum).")
        val root = JSONObject(json)
        if (root.optInt("schemaVersion", -1) != 1) throw Invalid("\"schemaVersion\" doit valoir 1.")
        val theme = if (root.has("theme")) theme(obj(root, "theme")) else ThemeConfig.Default
        val home = if (root.has("home")) home(obj(root, "home")) else HomeConfig.Default
        checkContrast(theme)
        return UiConfig(theme, home)
    }

    private fun obj(parent: JSONObject, key: String): JSONObject =
        parent.opt(key) as? JSONObject ?: throw Invalid("\"$key\" doit être un objet.")

    private fun theme(o: JSONObject): ThemeConfig {
        val d = ThemeConfig.Default
        val scale = if (o.has("fontScale")) {
            val v = (o.opt("fontScale") as? Number)?.toDouble() ?: throw Invalid("\"fontScale\" doit être un nombre.")
            if (v < 1.0 || v > MAX_FONT_SCALE) throw Invalid("\"fontScale\" doit être entre 1 et $MAX_FONT_SCALE (le texte ne peut pas être plus petit que par défaut).")
            v.toFloat()
        } else d.fontScale
        return ThemeConfig(
            background = color(o, "background", d.background),
            text = color(o, "text", d.text),
            primary = color(o, "primary", d.primary),
            onPrimary = color(o, "onPrimary", d.onPrimary),
            tile = color(o, "tile", d.tile),
            accent = color(o, "accent", d.accent),
            fontScale = scale,
        )
    }

    private fun color(o: JSONObject, key: String, default: Int): Int {
        if (!o.has(key)) return default
        val s = o.opt(key) as? String
        if (s == null || !colorRegex.matches(s)) throw Invalid("\"$key\" doit être une couleur au format #RRGGBB.")
        return (s.substring(1).toInt(16)) or 0xFF000000.toInt()
    }

    private fun checkContrast(t: ThemeConfig) {
        fun need(name: String, a: Int, b: Int) {
            if (Contrast.ratio(a, b) < MIN_CONTRAST) throw Invalid("Contraste insuffisant entre les couleurs $name (minimum $MIN_CONTRAST:1).")
        }
        need("\"text\" et \"background\"", t.text, t.background)
        need("\"text\" et \"tile\"", t.text, t.tile)
        need("\"text\" et \"accent\"", t.text, t.accent)
        need("\"onPrimary\" et \"primary\"", t.onPrimary, t.primary)
    }

    private fun home(o: JSONObject): HomeConfig {
        val columns = if (o.has("columns")) (o.opt("columns") as? Int ?: throw Invalid("\"columns\" doit valoir 1 ou 2.")) else HomeConfig.Default.columns
        if (columns !in 1..2) throw Invalid("\"columns\" doit valoir 1 ou 2.")
        val tiles = if (o.has("tiles")) tiles(o.opt("tiles") as? JSONArray ?: throw Invalid("\"tiles\" doit être une liste.")) else HomeConfig.Default.tiles
        return HomeConfig(columns, tiles)
    }

    private fun tiles(a: JSONArray): List<TileConfig> {
        if (a.length() !in 1..MAX_TILES) throw Invalid("\"tiles\" doit contenir de 1 à $MAX_TILES tuiles.")
        val seen = mutableSetOf<String>()
        return (0 until a.length()).map { i ->
            val t = a.opt(i) as? JSONObject ?: throw Invalid("La tuile ${i + 1} doit être un objet.")
            val id = (t.opt("id") as? String)?.trim().orEmpty()
            if (id.isEmpty() || id.length > 32) throw Invalid("La tuile ${i + 1} a besoin d'un \"id\" de 1 à 32 caractères.")
            if (!seen.add(id)) throw Invalid("L'\"id\" \"$id\" est utilisé deux fois.")
            val action = TileAction.fromWire(t.opt("action") as? String ?: "")
                ?: throw Invalid("Tuile \"$id\" : \"action\" doit être l'une de ${TileAction.entries.joinToString { it.wire }}.")
            val label = if (t.has("label")) {
                val l = (t.opt("label") as? String)?.trim().orEmpty()
                if (l.isEmpty() || l.length > MAX_LABEL) throw Invalid("Tuile \"$id\" : \"label\" doit faire de 1 à $MAX_LABEL caractères.")
                l
            } else null
            val icon = if (t.has("icon")) {
                TileIcon.fromWire(t.opt("icon") as? String ?: "")
                    ?: throw Invalid("Tuile \"$id\" : \"icon\" doit être l'une de ${TileIcon.entries.joinToString { it.wire }}.")
            } else action.defaultIcon
            val app = if (t.has("package")) {
                val p = (t.opt("package") as? String)?.trim().orEmpty()
                if (p.length > MAX_PACKAGE || !packageRegex.matches(p)) throw Invalid("Tuile \"$id\" : \"package\" doit être un nom d'application Android (par exemple com.whatsapp).")
                p
            } else null
            if (action == TileAction.APP && app == null) throw Invalid("Tuile \"$id\" : l'action \"app\" demande un \"package\".")
            if (action != TileAction.APP && app != null) throw Invalid("Tuile \"$id\" : \"package\" n'est permis que pour l'action \"app\".")
            TileConfig(id, action, label, icon, app)
        }
    }
}
