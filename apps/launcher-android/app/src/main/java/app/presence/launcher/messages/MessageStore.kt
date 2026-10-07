package app.presence.launcher.messages

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.presence.launcher.notifications.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.io.IOException

/**
 * Every message, kept on the phone only, with no age or count limit (ADR 0005), in a private SQLite database that is
 * excluded from cloud backup and device transfer (data_extraction_rules.xml). "Réglages famille" can erase it.
 *
 * [messages] holds only the most recent [RECENT] messages (oldest first) so the screens stay fast however long the
 * history grows; older ones stay in the database for per-conversation queries.
 *
 * Pass `dbName = null` for an in-memory database (tests).
 */
class MessageStore(
    context: Context,
    dbName: String? = DB_NAME,
    private val legacyFile: File? = File(context.applicationContext.filesDir, "messages.json"),
) {
    private class Helper(context: Context, name: String?) : SQLiteOpenHelper(context, name, null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE messages (
                    id TEXT PRIMARY KEY,
                    who TEXT NOT NULL,
                    address TEXT,
                    thread TEXT NOT NULL,
                    channel TEXT NOT NULL,
                    outgoing INTEGER NOT NULL DEFAULT 0,
                    body TEXT NOT NULL,
                    at INTEGER NOT NULL,
                    read INTEGER NOT NULL DEFAULT 0,
                    status INTEGER NOT NULL DEFAULT 0
                )""",
            )
            db.execSQL("CREATE INDEX messages_at ON messages(at)")
            db.execSQL("CREATE INDEX messages_thread ON messages(thread, at)")
            // MMS parts (images, audio, video) are stored as private files; the row only points to them.
            db.execSQL(
                """CREATE TABLE attachments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
                    mime TEXT NOT NULL,
                    path TEXT NOT NULL
                )""",
            )
            db.execSQL("CREATE INDEX attachments_message ON attachments(message_id)")
        }

        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true) // ON DELETE CASCADE for attachments
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private val helper = Helper(context.applicationContext, dbName)
    private val _messages = MutableStateFlow<List<StoredMessage>>(emptyList())
    val messages: StateFlow<List<StoredMessage>> = _messages.asStateFlow()

    init {
        migrateLegacyFile()
        publish()
    }

    @Synchronized
    fun add(message: StoredMessage) {
        helper.writableDatabase.insertWithOnConflict("messages", null, values(message), SQLiteDatabase.CONFLICT_REPLACE)
        publish()
    }

    @Synchronized
    fun markRead(id: String) {
        helper.writableDatabase.update("messages", ContentValues().apply { put("read", 1) }, "id = ?", arrayOf(id))
        publish()
    }

    @Synchronized
    fun setStatus(id: String, status: MessageStatus) {
        helper.writableDatabase.update("messages", ContentValues().apply { put("status", status.code) }, "id = ?", arrayOf(id))
        publish()
    }

    /** Erases the whole history. */
    @Synchronized
    fun clear() {
        helper.writableDatabase.delete("messages", null, null)
        publish()
    }

    @Synchronized
    fun count(): Int =
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM messages", null).use { it.moveToFirst(); it.getInt(0) }

    /** Number of received messages not opened yet (all history, not only the recent ones). */
    @Synchronized
    fun unreadCount(): Int =
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM messages WHERE read = 0 AND outgoing = 0", null)
            .use { it.moveToFirst(); it.getInt(0) }

    /** The whole SMS conversation with one phone number (any way of writing it), oldest first. */
    @Synchronized
    fun conversation(address: String): List<StoredMessage> =
        query("thread = ?", arrayOf(ThreadKey.of(Channel.SMS, address, address)), "at ASC", null)

    private fun publish() {
        _messages.value = query(null, null, "at DESC", RECENT.toString()).reversed()
    }

    private fun query(where: String?, args: Array<String>?, order: String, limit: String?): List<StoredMessage> =
        helper.readableDatabase.query("messages", COLUMNS, where, args, null, null, order, limit).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val channel = Channel.entries.firstOrNull { it.wire == c.getString(3) } ?: continue
                    add(
                        StoredMessage(
                            id = c.getString(0), from = c.getString(1), address = c.getString(2), channel = channel,
                            outgoing = c.getInt(4) != 0, text = c.getString(5), receivedAtMs = c.getLong(6),
                            read = c.getInt(7) != 0, status = MessageStatus.fromCode(c.getInt(8)),
                        ),
                    )
                }
            }
        }

    private fun values(m: StoredMessage) = ContentValues().apply {
        put("id", m.id); put("who", m.from); put("address", m.address); put("channel", m.channel.wire)
        put("thread", ThreadKey.of(m.channel, m.address, m.from))
        put("outgoing", if (m.outgoing) 1 else 0); put("body", m.text); put("at", m.receivedAtMs)
        put("read", if (m.read) 1 else 0); put("status", m.status.code)
    }

    /** One-time import of the former bounded JSON file, then the file is deleted. */
    private fun migrateLegacyFile() {
        val f = legacyFile ?: return
        if (!f.exists()) return
        val old = try {
            parseLegacy(f.readText())
        } catch (e: IOException) {
            return // try again next start
        } catch (e: JSONException) {
            emptyList() // corrupt: nothing to keep
        }
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            old.forEach { db.insertWithOnConflict("messages", null, values(it), SQLiteDatabase.CONFLICT_IGNORE) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        f.delete()
    }

    companion object {
        const val RECENT = 500
        private const val DB_NAME = "messages.db"
        private const val VERSION = 1
        private val COLUMNS = arrayOf("id", "who", "address", "channel", "outgoing", "body", "at", "read", "status")

        @Volatile private var instance: MessageStore? = null

        fun get(context: Context): MessageStore =
            instance ?: synchronized(this) { instance ?: MessageStore(context.applicationContext).also { instance = it } }

        internal fun parseLegacy(json: String): List<StoredMessage> {
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
