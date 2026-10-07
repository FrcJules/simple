package app.presence.launcher.notifications

/**
 * Messaging apps post a notification and then update it a few hundred ms later: the listener sees both.
 * The same notification key with the same text inside [windowMs] is the same message.
 */
class DuplicateFilter(private val windowMs: Long = 5_000, private val maxEntries: Int = 50) {
    private val seen = LinkedHashMap<String, Long>()

    @Synchronized
    fun isDuplicate(key: String, text: String, nowMs: Long): Boolean {
        seen.entries.removeIf { nowMs - it.value > windowMs }
        val id = "$key|$text"
        val duplicate = id in seen
        seen[id] = nowMs
        while (seen.size > maxEntries) seen.remove(seen.keys.first())
        return duplicate
    }
}
