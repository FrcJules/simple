package app.simple.launcher.security

import android.content.Context

/**
 * Earlier versions could be paired with a home display (Raspberry Pi) and kept its address, certificate fingerprint and token.
 * That feature is gone: what it stored is erased once, the first time this version starts. Nothing else is touched.
 */
class LegacyHubCleanup(
    context: Context,
    private val deleteKey: (String) -> Unit = SecretBox::deleteKey,
) {
    private val app = context.applicationContext
    private val profile = app.getSharedPreferences("profile", Context.MODE_PRIVATE)

    /** Erases the old pairing if it has not been done yet. True if it ran now. Never throws: the home screen calls it at start-up. */
    fun runOnce(): Boolean {
        if (profile.getBoolean(DONE, false)) return false
        runCatching {
            // Emptied first, so nothing is left even if the file itself cannot be removed.
            app.getSharedPreferences(PAIRING_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
            app.deleteSharedPreferences(PAIRING_PREFS)
        }
        // A key left behind (Keystore unavailable right now) protects nothing any more: it is not worth trying at every start.
        runCatching { deleteKey(PAIRING_KEY_ALIAS) }
        profile.edit().putBoolean(DONE, true).apply()
        return true
    }

    companion object {
        const val PAIRING_PREFS = "pairing"
        const val PAIRING_KEY_ALIAS = "simple-pairing"
        private const val DONE = "legacy_hub_data_removed"
    }
}
