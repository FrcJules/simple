package app.presence.launcher.notifications

import app.presence.launcher.messages.normalizeName

/**
 * One-time codes and banking alerts must not be stored, read aloud in a living room or sent to the home display.
 * Deliberately blunt: a false positive only means one message is not shown, a miss would leak a secret.
 */
object SensitiveText {
    private val digits = Regex("""\b\d{4,8}\b""")
    private val keywords = listOf(
        "code", "otp", "mot de passe", "password", "verification", "confirmation", "authentif",
        "ne communiquez", "ne partagez", "do not share", "3d secure", "carte bancaire", "virement", "iban", "pin",
    )

    fun looksSensitive(text: String): Boolean {
        val t = normalizeName(text) // lower-cased, accents removed
        return digits.containsMatchIn(t) && keywords.any { t.contains(it) }
    }
}
