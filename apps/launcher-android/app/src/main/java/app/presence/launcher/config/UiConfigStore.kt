package app.presence.launcher.config

import android.content.Context
import java.io.File

/** Loads the UI config: family override file if valid, else the bundled default, else built-in values. Never throws. */
class UiConfigStore(private val context: Context) {
    private val overrideFile get() = File(context.filesDir, FILE_NAME)

    fun load(): UiConfig {
        val override = readOverride()
        if (override is ParseResult.Ok) return override.config
        return (bundledDefault() as? ParseResult.Ok)?.config ?: UiConfig.Default
    }

    /** Validates [json] and, if valid, uses it from now on. Returns the result so the caller can explain a refusal. */
    fun import(json: String): ParseResult {
        val result = UiConfigParser.parse(json)
        if (result is ParseResult.Ok) overrideFile.writeText(json)
        return result
    }

    fun reset() {
        overrideFile.delete()
    }

    fun hasOverride(): Boolean = overrideFile.exists()

    private fun readOverride(): ParseResult? =
        if (overrideFile.exists()) runCatching { UiConfigParser.parse(overrideFile.readText()) }.getOrNull() else null

    private fun bundledDefault(): ParseResult? =
        runCatching { UiConfigParser.parse(context.assets.open(ASSET).bufferedReader().use { it.readText() }) }.getOrNull()

    private companion object {
        const val FILE_NAME = "ui-config.json"
        const val ASSET = "ui-config.default.json"
    }
}
