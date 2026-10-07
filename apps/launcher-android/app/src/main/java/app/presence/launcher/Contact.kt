package app.presence.launcher

/** A starred phone contact shown as a photo card. */
data class Contact(val id: Long, val name: String, val number: String, val photoUri: String?) {
    /** Keeps name and number out of logs. */
    override fun toString() = "Contact(id=$id)"

    /** First letter, shown when the contact has no photo. */
    val initial: String get() = name.trim().take(1).uppercase()
}

/** Builds a `tel:` URI string keeping only digits and a leading `+`. */
fun telUri(number: String): String {
    val cleaned = number.filterIndexed { i, c -> c.isDigit() || (c == '+' && i == number.indexOfFirst { !it.isWhitespace() }) }
    return "tel:$cleaned"
}

/** A number worth dialing directly: at least 3 digits. */
fun isDialable(number: String): Boolean = number.count { it.isDigit() } >= 3
