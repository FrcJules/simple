package app.simple.launcher.security

import android.content.Context
import java.io.File

/**
 * Earlier versions could be paired with a home display (Raspberry Pi) and kept its address, certificate fingerprint and token.
 * That feature is gone: what it stored is erased the first time this version starts, and again at each start until it is really gone.
 * Nothing else is touched.
 */
class LegacyHubCleanup(
    context: Context,
    private val deleteKey: (String) -> Unit = SecretBox::deleteKey,
    private val erasePairing: (Context) -> Unit = { app ->
        // Emptied first, so nothing is left even if the file itself cannot be removed.
        app.getSharedPreferences(PAIRING_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        app.deleteSharedPreferences(PAIRING_PREFS)
    },
) {
    private val app = context.applicationContext
    private val profile = app.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val pairingFile get() = File(app.dataDir, "shared_prefs/$PAIRING_PREFS.xml")

    /**
     * Erases the old pairing if it has not been done yet. True if it ran now. Never throws: the app calls it at start-up.
     * Marked done only once the file is really gone: the token may be in it in clear (phones without a usable Keystore).
     */
    fun runOnce(): Boolean = synchronized(LOCK) { // the home screen and the settings may both start it
        if (profile.getBoolean(DONE, false)) return false
        runCatching { erasePairing(app) }
        // A key left behind (Keystore unavailable right now) protects nothing any more: it is not worth trying at every start.
        runCatching { deleteKey(PAIRING_KEY_ALIAS) }
        if (!pairingFile.exists()) profile.edit().putBoolean(DONE, true).commit()
        true
    }

    companion object {
        const val PAIRING_PREFS = "pairing"
        const val PAIRING_KEY_ALIAS = "simple-pairing"
        private const val DONE = "legacy_hub_data_removed"
        private val LOCK = Any()
    }
}
