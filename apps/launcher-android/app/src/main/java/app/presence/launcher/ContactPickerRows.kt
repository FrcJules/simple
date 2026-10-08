package app.presence.launcher

import java.text.Normalizer

object ContactPickerRows {
    const val LIMIT = 30

    private fun fold(s: String) =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    /** Chosen people first, then the others by name; filtered by [query] (accent- and case-insensitive); at most [limit] rows. */
    fun rows(all: List<Contact>, chosen: Set<Long>, query: String, limit: Int = LIMIT): List<Contact> {
        val q = fold(query.trim())
        return all.filter { q.isEmpty() || fold(it.name).contains(q) }
            .sortedWith(compareByDescending<Contact> { it.id in chosen }.thenBy { fold(it.name) })
            .take(limit)
    }
}
