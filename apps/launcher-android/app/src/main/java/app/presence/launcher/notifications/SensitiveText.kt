package app.presence.launcher.notifications

import app.presence.launcher.messages.normalizeName

/**
 * One-time codes and banking alerts must not be shown in a list, read aloud in a living room or sent to the home display.
 * Deliberately blunt: a false positive only means one message sits behind a tap, a miss would leak a secret.
 */
object SensitiveText {
    // 4-10 digits in one block, or a code split in two blocks ("123 456", "123-456").
    private val digits = Regex("""\b\d{4,10}\b|\b\d{3}[ -]\d{3}\b""")
    private val keywords = listOf(
        "code", "otp", "mot de passe", "password", "verification", "confirmation", "authentif", "securite", "valider", "validation",
        "cvv", "paiement", "achat", "connexion", "identifiant", "login", "one-time", "usage unique", "expire",
        "ne communiquez", "ne partagez", "do not share", "3d secure", "carte bancaire", "virement", "iban", "pin",
    )

    fun looksSensitive(text: String): Boolean {
        val t = normalizeName(text) // lower-cased, accents removed
        return digits.containsMatchIn(t) && keywords.any { t.contains(it) }
    }
}
