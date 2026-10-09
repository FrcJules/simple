package app.presence.launcher

import android.content.Context

/** The first name shown on the home screen, set by the family in "Réglages famille" (nothing personal lives in the code). */
class ProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("profile", Context.MODE_PRIVATE)

    fun name(): String = prefs.getString("name", "").orEmpty()

    /** Whether the system contacts dialog was already shown (it may never show again after a refusal). */
    fun contactsAsked(): Boolean = prefs.getBoolean("contacts_asked", false)

    fun markContactsAsked() {
        prefs.edit().putBoolean("contacts_asked", true).apply()
    }

    /** The person (or family) said "Plus tard" to making Présence the SMS app: do not ask again by surprise. */
    fun smsRoleDeclined(): Boolean = prefs.getBoolean("sms_role_declined", false)

    fun setSmsRoleDeclined(declined: Boolean) {
        prefs.edit().putBoolean("sms_role_declined", declined).apply()
    }

    /** The phone's old SMS were copied into our history. */
    fun smsImported(): Boolean = prefs.getBoolean("sms_imported", false)

    fun markSmsImported() {
        prefs.edit().putBoolean("sms_imported", true).apply()
    }

    /** Sounds and vibrations after sending, on failure, and "J'appelle …" aloud. On by default; the family can switch them off. */
    fun feedbackEnabled(): Boolean = prefs.getBoolean("feedback", true)

    fun setFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("feedback", enabled).apply()
    }

    /**
     * The "pour la famille" button on the home screen is offered for [SETUP_WINDOW_MS] after it first appears, and only while no family
     * code exists: afterwards the code can still be chosen from the "Réglages" icon, but not from Jean-Pierre's home screen.
     */
    fun setupHintVisible(now: Long, codeSet: Boolean): Boolean {
        if (codeSet) return false
        val first = prefs.getLong("setup_hint_first", 0L).takeIf { it > 0 } ?: now.also { prefs.edit().putLong("setup_hint_first", it).apply() }
        return now - first in 0..SETUP_WINDOW_MS
    }

    fun saveName(name: String) {
        prefs.edit().putString("name", name.trim().take(40)).apply()
    }

    private companion object {
        const val SETUP_WINDOW_MS = 30 * 60 * 1000L
    }
}
