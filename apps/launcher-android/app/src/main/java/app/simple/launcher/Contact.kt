package app.simple.launcher

/** A starred phone contact shown as a photo card. */
data class Contact(val id: Long, val name: String, val number: String, val photoUri: String?) {
    /** Keeps name and number out of logs. */
    override fun toString() = "Contact(id=$id)"

    /** First letter, shown when the contact has no photo. */
    val initial: String get() = name.trim().take(1).uppercase()
}

private fun dialable(number: String) =
    number.filterIndexed { i, c -> c.isDigit() || (c == '+' && i == number.indexOfFirst { !it.isWhitespace() }) }

/** Builds a `tel:` URI string keeping only digits and a leading `+`. */
fun telUri(number: String): String = "tel:${dialable(number)}"

/** Same cleaning for an `smsto:` URI (opens the messaging app with a draft). */
fun smsUri(number: String): String = "smsto:${dialable(number)}"

/**
 * A number worth dialing or texting: only digits and the usual separators (never `,` `;` `*` `#` or letters, which could
 * chain several recipients or dial a code), with at least 3 digits.
 */
fun isDialable(number: String): Boolean =
    number.count { it.isDigit() } >= 3 && number.all { it.isDigit() || it in "+ ()-." }
