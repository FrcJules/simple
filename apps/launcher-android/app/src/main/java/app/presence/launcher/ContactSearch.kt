package app.presence.launcher

import java.text.Normalizer

/** Finding a person by the first letters of a first name, on a big on-screen keyboard: no accents, no capitals, any word of the name. */
object ContactSearch {
    /** Lower case, accents removed, anything that is not a letter or digit becomes a space. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() } // the accents split off by NFD
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")

    private fun words(name: String) = normalize(name).split(' ').filter { it.isNotEmpty() }

    /**
     * The contacts whose name has a word starting with [query] (typed letters, no spaces). People whose *first* word matches come first,
     * then the others, each group in the order given (the phone's alphabetical order).
     */
    fun filter(contacts: List<Contact>, query: String): List<Contact> {
        val q = normalize(query).replace(" ", "")
        if (q.isEmpty()) return contacts
        val (first, others) = contacts
            .filter { c -> words(c.name).any { it.startsWith(q) } }
            .partition { c -> words(c.name).firstOrNull()?.startsWith(q) == true }
        return first + others
    }
}
