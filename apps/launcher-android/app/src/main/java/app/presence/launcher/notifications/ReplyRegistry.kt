package app.presence.launcher.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Answering a WhatsApp message without WhatsApp's own screen: the notification carries a "Répondre" action, which is the very
 * mechanism Android's quick reply uses. We keep the latest one per conversation, **in memory only** (a PendingIntent cannot
 * be saved), for as long as WhatsApp's notification exists. If it was dismissed, there is nothing to answer through and the
 * launcher offers to open WhatsApp instead.
 */
object ReplyRegistry {
    /** What is needed to answer one conversation. */
    class Entry(val notificationKey: String, val threadKey: String, val action: PendingIntent, val inputs: Array<RemoteInput>)

    private val entries = LinkedHashMap<String, Entry>()
    private val _available = MutableStateFlow<Set<String>>(emptySet())

    /** The conversations (thread keys) that can currently be answered. */
    val available: StateFlow<Set<String>> = _available.asStateFlow()

    /** The reply action of [notification]: the first one that takes free text. Null if it has none. */
    fun findReplyAction(notification: Notification): Pair<PendingIntent, Array<RemoteInput>>? {
        val actions = notification.actions ?: return null
        for (a in actions) {
            val inputs = a.remoteInputs?.filter { it.allowFreeFormInput }?.toTypedArray()
            val intent = a.actionIntent
            if (!inputs.isNullOrEmpty() && intent != null) return intent to inputs
        }
        return null
    }

    @Synchronized
    fun put(notificationKey: String, threadKey: String, action: PendingIntent, inputs: Array<RemoteInput>) {
        entries[threadKey] = Entry(notificationKey, threadKey, action, inputs)
        publish()
    }

    /** WhatsApp's notification was dismissed or replaced: its action may no longer work. */
    @Synchronized
    fun removeNotification(notificationKey: String) {
        if (entries.values.removeIf { it.notificationKey == notificationKey }) publish()
    }

    @Synchronized
    fun clear() {
        entries.clear()
        publish()
    }

    @Synchronized
    fun canReply(threadKey: String) = threadKey in entries

    /** Sends [text] through the conversation's reply action. False if there is none (any more) or WhatsApp refused it. */
    fun reply(context: Context, threadKey: String, text: String): Boolean {
        val entry = synchronized(this) { entries[threadKey] } ?: return false
        if (text.isBlank()) return false
        val results = Bundle().also { b -> entry.inputs.forEach { b.putCharSequence(it.resultKey, text.trim()) } }
        val intent = Intent()
        RemoteInput.addResultsToIntent(entry.inputs, intent, results)
        return try {
            entry.action.send(context.applicationContext, 0, intent)
            true
        } catch (e: PendingIntent.CanceledException) {
            synchronized(this) { entries.remove(threadKey); publish() } // the notification is gone: stop offering to answer
            false
        }
    }

    private fun publish() {
        _available.value = entries.keys.toSet()
    }
}
