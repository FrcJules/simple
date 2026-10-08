package app.presence.launcher.notifications

import android.content.Context
import app.presence.launcher.security.SecretBox

/**
 * Stores the pairing in app-private preferences (excluded from backup, see manifest). The token is encrypted with a key kept in
 * the Android Keystore; a token saved by an earlier version in clear is encrypted the first time it is read. If the phone has no
 * usable Keystore the token is kept as before rather than losing the pairing.
 */
class PairingStore(context: Context, private val box: SecretBox? = SecretBox.shared) {
    private val prefs = context.applicationContext.getSharedPreferences("pairing", Context.MODE_PRIVATE)

    fun load(): PairingConfig? {
        val url = prefs.getString("url", null) ?: return null
        val pin = prefs.getString("pin", null) ?: return null
        val token = readToken() ?: return null
        return PairingConfig.parse(url, token, pin)
    }

    fun save(config: PairingConfig) {
        val sealed = box?.seal(config.token)
        prefs.edit()
            .putString("url", config.baseUrl)
            .putString("pin", config.certSha256)
            .apply { if (sealed != null) putString("token_sealed", sealed).remove("token") else putString("token", config.token).remove("token_sealed") }
            .apply()
    }

    private fun readToken(): String? {
        prefs.getString("token_sealed", null)?.let { return box?.open(it) } // null if the key is gone: the family pairs again
        val plain = prefs.getString("token", null) ?: return null
        // From an earlier version: encrypt it now.
        box?.seal(plain)?.let { prefs.edit().putString("token_sealed", it).remove("token").apply() }
        return plain
    }
}
