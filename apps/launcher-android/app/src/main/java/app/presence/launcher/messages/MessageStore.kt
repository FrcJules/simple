package app.presence.launcher.messages

import app.presence.launcher.notifications.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * The few most recent messages, in one private JSON file. Bounded by count and age so the phone never
 * accumulates a conversation history. Excluded from cloud backup and device transfer (data_extraction_rules.xml).
 */
class MessageStore(
    private val file: File,
    private val maxMessages: Int = 30,
    private val maxAgeMs: Long = 7L * 24 * 3600 * 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _messages = MutableStateFlow(prune(load()))
    val messages: StateFlow<List<StoredMessage>> = _messages.asStateFlow()

    @Synchronized
    fun add(message: StoredMessage) {
        update { list -> list.filterNot { it.id == message.id } + message }
    }

    @Synchronized
    fun markRead(id: String) {
        update { list -> list.map { if (it.id == id) it.copy(read = true) else it } }
    }

    @Synchronized
    fun clear() = update { emptyList() }

    private fun update(change: (List<StoredMessage>) -> List<StoredMessage>) {
        val next = prune(change(_messages.value))
        _messages.value = next
        save(next)
    }

    private fun prune(list: List<StoredMessage>): List<StoredMessage> {
        val now = clock()
        return list.filter { now - it.receivedAtMs <= maxAgeMs }.sortedBy { it.receivedAtMs }.takeLast(maxMessages)
    }

    private fun load(): List<StoredMessage> =
        try {
            if (!file.exists()) emptyList() else parse(file.readText())
        } catch (e: IOException) {
            emptyList()
        } catch (e: JSONException) {
            emptyList() // a corrupt file must never crash the launcher: start again from nothing
        }

    private fun save(list: List<StoredMessage>) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(serialize(list))
            if (!tmp.renameTo(file)) file.writeText(serialize(list))
        } catch (e: IOException) {
            // Keep working in memory; the next successful save catches up.
        }
    }

    companion object {
        @Volatile private var instance: MessageStore? = null

        fun get(context: android.content.Context): MessageStore =
            instance ?: synchronized(this) {
                instance ?: MessageStore(File(context.applicationContext.filesDir, "messages.json")).also { instance = it }
            }

        internal fun serialize(list: List<StoredMessage>): String =
            JSONArray().also { arr ->
                list.forEach {
                    arr.put(
                        JSONObject().put("id", it.id).put("from", it.from).put("channel", it.channel.wire)
                            .put("text", it.text).put("at", it.receivedAtMs).put("read", it.read),
                    )
                }
            }.toString()

        internal fun parse(json: String): List<StoredMessage> {
            val arr = JSONArray(json)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val channel = Channel.entries.firstOrNull { it.wire == o.optString("channel") } ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isEmpty()) return@mapNotNull null
                StoredMessage(id, o.optString("from"), channel, o.optString("text"), o.optLong("at"), o.optBoolean("read"))
            }
        }
    }
}
