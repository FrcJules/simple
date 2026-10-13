package app.simple.launcher.notifications

import app.simple.launcher.messages.normalizeName

/**
 * One-time codes and banking alerts must not be shown in a list, read aloud in a living room or sent to the home display.
 * Still blunt (a false positive only means one message sits behind a tap, a miss would leak a secret), but not silly: words are matched
 * as words ("lapin" is not "pin"), and a phone number, a year or a time is not mistaken for a code unless the text really talks about one.
 */
object SensitiveText {
    // 4-10 digits in one block, or a code split in two blocks ("123 456", "123-456").
    private val digits = Regex("""\b\d{4,10}\b|\b\d{3}[ -]\d{3}\b""")
    private val year = Regex("""(19|20)\d{2}""")

    /** Words that name a secret: with any number of 4+ digits the message is sensitive. */
    private val strong = words(
        "code", "codes", "otp", "cvv", "login", "password", "mot de passe", "3d secure", "usage unique", "one-time",
        "ne communiquez", "ne partagez", "do not share",
    ) + stems("verification", "authentif")

    /** Words around money and accounts: they need a longer number (5+ digits) next to them, since 4 digits are as often a time or a year. */
    private val weak = words(
        "confirmation", "validation", "valider", "securite", "paiement", "achat", "connexion", "identifiant", "expire",
        "carte bancaire", "virement", "iban",
    )

    private fun words(vararg w: String) = w.map { Regex("""\b${Regex.escape(it)}\b""") }
    private fun stems(vararg w: String) = w.map { Regex("""\b${Regex.escape(it)}""") }

    fun looksSensitive(text: String): Boolean {
        val t = normalizeName(text) // lower-cased, accents removed
        val numbers = digits.findAll(t).map { it.value }.filterNot(::isPhoneNumber).toList()
        if (numbers.isEmpty()) return false
        if (strong.any { it.containsMatchIn(t) }) return true // any number: even a year or a time can be the code
        val long = numbers.filter { it.length >= 5 || it.contains(' ') || it.contains('-') }
        return long.isNotEmpty() && weak.any { it.containsMatchIn(t) } && numbers.any { !year.matches(it) }
    }

    /** A French mobile or landline number written in one block (0612345678): somebody's number, not a code. */
    private fun isPhoneNumber(block: String) = block.length == 10 && block.startsWith("0")
}
