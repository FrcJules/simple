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

    fun saveName(name: String) {
        prefs.edit().putString("name", name.trim().take(40)).apply()
    }
}
