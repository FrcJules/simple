package app.presence.launcher.notifications

import android.content.Context

/** Stores the pairing in app-private preferences (excluded from backup, see manifest). */
class PairingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pairing", Context.MODE_PRIVATE)

    fun load(): PairingConfig? {
        val url = prefs.getString("url", null) ?: return null
        val token = prefs.getString("token", null) ?: return null
        return PairingConfig.parse(url, token)
    }

    fun save(config: PairingConfig) {
        prefs.edit().putString("url", config.baseUrl).putString("token", config.token).apply()
    }
}
