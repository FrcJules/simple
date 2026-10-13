package app.simple.launcher.sms

import android.provider.Telephony
import app.simple.launcher.messages.MessageStatus
import app.simple.launcher.messages.SmsTwins
import app.simple.launcher.messages.StoredMessage
import app.simple.launcher.messages.TextSafety
import app.simple.launcher.notifications.Channel
import app.simple.launcher.notifications.SensitiveText

/**
 * Copies the phone's existing SMS into our history when we become the default SMS app, so the person keeps their old
 * conversations (all marked read: an old inbox must not raise a huge "new messages" badge). Idempotent: each row has a stable id derived from the system row, so running it twice adds nothing.
 */
class SmsHistoryImporter(
    private val gateway: SmsProviderGateway,
    private val addAll: (List<StoredMessage>) -> Unit,
    private val nameFor: (String) -> String?,
    /** Messages we sent ourselves since this time (direct mode): the system's own record of the same SMS must not be added again. */
    private val sentSince: (Long) -> List<StoredMessage> = { emptyList() },
) {
    /** Each message we sent ourselves hides one system row for it (one to one, so two identical sends stay two). */
    private fun withoutOurOwnSent(messages: List<StoredMessage>): List<StoredMessage> {
        val sentRows = messages.filter { it.outgoing }
        if (sentRows.isEmpty()) return messages
        val locals = sentSince(sentRows.minOf { it.receivedAtMs } - SmsTwins.SENT_WINDOW_MS).filter(SmsTwins::isLocalSent).toMutableList()
        if (locals.isEmpty()) return messages
        return messages.filterNot { row ->
            val twin = if (row.outgoing) SmsTwins.twinOf(row, locals) else null // the closest one in time; each send hides one row only
            twin?.let { locals.remove(it) } != null
        }
    }

    fun importAll(): Int = syncSince(0, keepReadFlag = false).count

    /** What a sync copied, and the highest system row id seen (to start from there next time). */
    data class Synced(val count: Int, val lastId: Long)

    /**
     * Copies the rows after [afterId]. The first copy marks everything read (an old inbox must not raise a huge badge); later copies, made
     * while we are not the default SMS app, keep the system's own read flag so a new message counts as new.
     */
    fun syncSince(afterId: Long, keepReadFlag: Boolean): Synced {
        val rows = gateway.readSince(afterId)
        val names = HashMap<String, String>()
        val messages = rows.mapNotNull { sms ->
            if (sms.address.isBlank() || sms.body.isBlank() || sms.type == Telephony.Sms.MESSAGE_TYPE_DRAFT) return@mapNotNull null
            val text = TextSafety.clean(sms.body) // control and bidirectional-override characters could make a text read differently from what it says
            val outgoing = sms.type != Telephony.Sms.MESSAGE_TYPE_INBOX
            val name = names.getOrPut(sms.address) { nameFor(sms.address)?.takeIf { it.isNotBlank() } ?: sms.address }
            StoredMessage(
                id = "sms-${sms.id}", from = name, channel = Channel.SMS, text = text, receivedAtMs = sms.dateMs,
                read = if (keepReadFlag && !outgoing) sms.read else true, outgoing = outgoing, address = sms.address,
                status = when (sms.type) {
                    Telephony.Sms.MESSAGE_TYPE_SENT -> MessageStatus.SENT
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageStatus.NONE
                    Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageStatus.FAILED
                    else -> MessageStatus.SENDING // outbox / queued
                },
                sensitive = !outgoing && SensitiveText.looksSensitive(text),
            )
        }
        val fresh = withoutOurOwnSent(messages)
        addAll(fresh)
        return Synced(fresh.size, maxOf(afterId, rows.maxOfOrNull { it.id } ?: afterId))
    }
}
