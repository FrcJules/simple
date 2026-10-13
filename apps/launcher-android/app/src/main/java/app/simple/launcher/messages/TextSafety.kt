package app.simple.launcher.messages

/**
 * Text that arrives from the network is shown to someone who lives on trust. Control characters and the "bidirectional
 * override" characters (which can make a text read differently from how it is stored) are removed; line breaks and tabs stay.
 */
object TextSafety {
    fun clean(text: String): String = buildString(text.length) {
        text.forEach { c ->
            val control = c.isISOControl() && c != '\n' && c != '\t'
            val bidi = c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069' || c == '\u200E' || c == '\u200F' || c == '\u061C'
            val invisible = c == '\u200B' || c == '\u2060' || c == '\uFEFF'
            if (!control && !bidi && !invisible) append(c)
        }
    }
}
