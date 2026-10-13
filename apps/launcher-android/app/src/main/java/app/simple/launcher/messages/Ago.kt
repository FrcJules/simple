package app.simple.launcher.messages

enum class AgoUnit { NOW, MINUTES, HOURS, DAYS }

data class Ago(val unit: AgoUnit, val count: Int)

/** "How long ago" as a unit and a count; the wording lives in strings.xml. */
fun ago(thenMs: Long, nowMs: Long): Ago {
    val minutes = ((nowMs - thenMs).coerceAtLeast(0) / 60_000).toInt()
    return when {
        minutes < 1 -> Ago(AgoUnit.NOW, 0)
        minutes < 60 -> Ago(AgoUnit.MINUTES, minutes)
        minutes < 24 * 60 -> Ago(AgoUnit.HOURS, minutes / 60)
        else -> Ago(AgoUnit.DAYS, minutes / (24 * 60))
    }
}
