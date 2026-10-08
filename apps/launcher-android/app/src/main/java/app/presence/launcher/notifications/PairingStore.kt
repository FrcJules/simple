package app.presence.launcher.notifications

import android.content.Context
import app.presence.launcher.security.SecretBox

/**
 * Stores the pairing in app-private preferences (excluded from backup, see manifest). The token is encrypted with a key kept in
 * the Android Keystore; a token saved by an earlier version in clear is encrypted the first time it is read. If the phone has no
 * usable Keystore the token is kept as before rather than losing the pairing; [protectionState] tells the family which it is.
 */
class PairingStore(context: Context, private val boxProvider: () -> SecretBox? = SecretBox::shared) {
    private val prefs = context.applicationContext.getSharedPreferences("pairing", Context.MODE_PRIVATE)

    enum class Protection {
        /** Nothing is paired. */
        NONE,

        /** The token is encrypted with the Keystore. */
        ENCRYPTED,

        /** The token is stored as before (this phone's Keystore was not usable). */
        PLAIN,

        /** An encrypted token exists but cannot be read now (the key is gone): the pairing must be done again. */
        UNREADABLE,
    }

    fun load(): PairingConfig? =
        try {
            val url = prefs.getString("url", null)
            val pin = prefs.getString("pin", null)
            val token = if (url == null || pin == null) null else readToken()
            if (url == null || pin == null || token == null) null else PairingConfig.parse(url, token, pin)
        } catch (e: RuntimeException) {
            null // never let a storage problem crash the notification service that calls this for every message
        }

    fun save(config: PairingConfig) {
        val sealed = boxProvider()?.seal(config.token)
        prefs.edit()
            .putString("url", config.baseUrl)
            .putString("pin", config.certSha256)
            .apply { if (sealed != null) putString("token_sealed", sealed).remove("token") else putString("token", config.token).remove("token_sealed") }
            .commit()
    }

    fun protectionState(): Protection = when {
        prefs.contains("token_sealed") -> if (load() != null) Protection.ENCRYPTED else Protection.UNREADABLE
        prefs.contains("token") -> Protection.PLAIN
        else -> Protection.NONE
    }

    private fun readToken(): String? {
        val box = boxProvider()
        prefs.getString("token_sealed", null)?.let { return box?.open(it) } // null if the key is gone: the family pairs again
        val plain = prefs.getString("token", null) ?: return null
        // From an earlier version: encrypt it now.
        box?.seal(plain)?.let { prefs.edit().putString("token_sealed", it).remove("token").commit() }
        return plain
    }
}
