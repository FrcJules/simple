package app.presence.launcher.messages

import app.presence.launcher.notifications.Channel
import kotlin.math.abs

/**
 * The phone itself records an SMS sent by a non-default app in the system database, and the sync then copies that row next to our own
 * record of the same send. These rules tell that copy from a genuinely different message: same text, same number, a few minutes
 * apart, and the copy must be a plain system row (`sms-<number>`), never a picture message or a message made some other way.
 * Only our own sends are ever matched: a received message has no reliable sender here, so received messages are never merged.
 */
object SmsTwins {
    const val SENT_WINDOW_MS = 5 * 60_000L
    private val systemRow = Regex("sms-\\d+")

    /** A message we sent ourselves (random "out-" id). */
    fun isLocalSent(m: StoredMessage) = m.channel == Channel.SMS && m.outgoing && !m.mms && m.id.startsWith("out-")

    /** A sent SMS as the system database holds it, once copied ("sms-<row id>"). */
    fun isSystemSent(m: StoredMessage) = m.channel == Channel.SMS && m.outgoing && !m.mms && systemRow.matches(m.id)

    /** Same text, same number: the key under which a send and its system record meet (no number, no match: never guessed). */
    fun key(m: StoredMessage): Pair<String, String>? {
        val tail = m.address.orEmpty().filter { it.isDigit() }.takeLast(9)
        return if (tail.isEmpty()) null else tail to m.text.trim()
    }

    /** The local send that [copy] records, the closest in time among [locals] (not yet paired), or null. */
    fun twinOf(copy: StoredMessage, locals: List<StoredMessage>): StoredMessage? {
        val k = key(copy) ?: return null
        if (!isSystemSent(copy)) return null
        return locals.filter { key(it) == k && abs(it.receivedAtMs - copy.receivedAtMs) <= SENT_WINDOW_MS }
            .minByOrNull { abs(it.receivedAtMs - copy.receivedAtMs) }
    }
}
