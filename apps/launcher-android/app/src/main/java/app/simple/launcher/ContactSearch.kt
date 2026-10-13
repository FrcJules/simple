package app.simple.launcher

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
     * The contacts matching the typed letters (no spaces, no hyphens: the keyboard has none), best matches first, each group in the
     * order given (the phone's alphabetical order):
     * 1. the name starts with them, hyphens and spaces ignored ("JEANB" finds "Jean-Baptiste", "MARIED" finds "Marie Dupont");
     * 2. another word of the name starts with them ("BAPTISTE" finds "Jean-Baptiste");
     * 3. from three letters on, they appear anywhere in the name ("APTIS" finds "Jean-Baptiste").
     */
    fun filter(contacts: List<Contact>, query: String): List<Contact> {
        val q = normalize(query).replace(" ", "")
        if (q.isEmpty()) return contacts
        val ranked = contacts.mapNotNull { c ->
            val w = words(c.name)
            val compact = w.joinToString("")
            val rank = when {
                compact.startsWith(q) -> 0
                w.any { it.startsWith(q) } -> 1
                q.length >= 3 && compact.contains(q) -> 2
                else -> return@mapNotNull null
            }
            rank to c
        }
        return ranked.sortedBy { it.first }.map { it.second } // sortedBy is stable: the phone's order is kept inside each group
    }
}
