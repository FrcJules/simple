package app.simple.launcher.messages

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.simple.launcher.notifications.Channel
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
                    status INTEGER NOT NULL DEFAULT 0,
                    sensitive INTEGER NOT NULL DEFAULT 0,
                    mms INTEGER NOT NULL DEFAULT 0
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
            createPending(db)
        }

        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true) // ON DELETE CASCADE for attachments
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) db.execSQL("ALTER TABLE messages ADD COLUMN sensitive INTEGER NOT NULL DEFAULT 0")
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE messages ADD COLUMN mms INTEGER NOT NULL DEFAULT 0")
                createPending(db)
            }
        }

        /** MMS announced by the operator and not (yet) downloaded: what is needed to try again. */
        private fun createPending(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE mms_pending (
                    message_id TEXT PRIMARY KEY,
                    transaction_id TEXT NOT NULL,
                    location TEXT NOT NULL,
                    subscription INTEGER NOT NULL,
                    tries INTEGER NOT NULL DEFAULT 0,
                    since INTEGER NOT NULL
                )""",
            )
        }
    }

    private val appContext = context.applicationContext
    private val helper = Helper(appContext, dbName)
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

    /** Adds many messages in one transaction (history import); an existing id is kept as it is. */
    @Synchronized
    fun addAll(messages: List<StoredMessage>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            messages.forEach { db.insertWithOnConflict("messages", null, values(it), SQLiteDatabase.CONFLICT_IGNORE) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        publish()
    }

    @Synchronized
    fun markRead(id: String) {
        helper.writableDatabase.update("messages", ContentValues().apply { put("read", 1) }, "id = ?", arrayOf(id))
        publish()
    }

    /** Adds an MMS with its pictures in one step (an outgoing picture message). */
    @Synchronized
    fun addMms(message: StoredMessage, attachments: List<Attachment>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("messages", null, values(message), SQLiteDatabase.CONFLICT_REPLACE)
            db.delete("attachments", "message_id = ?", arrayOf(message.id))
            attachments.forEach {
                db.insert("attachments", null, ContentValues().apply { put("message_id", message.id); put("mime", it.mime); put("path", it.path) })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        publish()
    }

    @Synchronized
    fun get(id: String): StoredMessage? = query("id = ?", arrayOf(id), "at ASC", "1").firstOrNull()

    /** Fills in a received MMS (text and pictures) once downloaded, replacing the "being received" placeholder. */
    @Synchronized
    fun completeMms(
        id: String, text: String, atMs: Long, attachments: List<Attachment>, sensitive: Boolean,
        sender: String? = null, senderAddress: String? = null,
    ) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.update(
                "messages",
                ContentValues().apply {
                    put("body", text); put("at", atMs); put("status", MessageStatus.NONE.code); put("sensitive", if (sensitive) 1 else 0)
                    // The sender written in the downloaded message wins over the one of the (forgeable) announcement.
                    if (senderAddress != null) {
                        put("address", senderAddress)
                        put("who", sender ?: senderAddress)
                        put("thread", ThreadKey.of(Channel.SMS, senderAddress, sender ?: senderAddress))
                    }
                },
                "id = ?", arrayOf(id),
            )
            db.delete("attachments", "message_id = ?", arrayOf(id))
            attachments.forEach {
                db.insert("attachments", null, ContentValues().apply { put("message_id", id); put("mime", it.mime); put("path", it.path) })
            }
            db.delete("mms_pending", "message_id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        publish()
    }

    data class PendingMms(val messageId: String, val transactionId: String, val location: String, val subscription: Int, val tries: Int, val sinceMs: Long)

    @Synchronized
    fun putPending(p: PendingMms) {
        helper.writableDatabase.insertWithOnConflict(
            "mms_pending", null,
            ContentValues().apply {
                put("message_id", p.messageId); put("transaction_id", p.transactionId); put("location", p.location)
                put("subscription", p.subscription); put("tries", p.tries); put("since", p.sinceMs)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun pending(messageId: String): PendingMms? =
        helper.readableDatabase.rawQuery(
            "SELECT message_id, transaction_id, location, subscription, tries, since FROM mms_pending WHERE message_id = ?", arrayOf(messageId),
        ).use { c -> if (c.moveToFirst()) PendingMms(c.getString(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), c.getLong(5)) else null }

    @Synchronized
    fun removePending(messageId: String) {
        helper.writableDatabase.delete("mms_pending", "message_id = ?", arrayOf(messageId))
    }

    /** Downloads announced and not finished: bounded by [MmsInbox] so a flood of announcements cannot fill the phone. */
    @Synchronized
    fun pendingCount(): Int = helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM mms_pending", null).use { it.moveToFirst(); it.getInt(0) }

    @Synchronized
    fun mmsReceivedSince(sinceMs: Long): Int =
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM messages WHERE mms = 1 AND outgoing = 0 AND at >= ?", arrayOf(sinceMs.toString()))
            .use { it.moveToFirst(); it.getInt(0) }

    @Synchronized
    fun exists(id: String): Boolean =
        helper.readableDatabase.rawQuery("SELECT 1 FROM messages WHERE id = ?", arrayOf(id)).use { it.moveToFirst() }

    /** Downloads that never finished (the process died, no network): they become "not received" so the person can retry. */
    @Synchronized
    fun expireStaleDownloads(olderThanMs: Long) {
        helper.writableDatabase.execSQL(
            "UPDATE messages SET status = ? WHERE mms = 1 AND outgoing = 0 AND status = ? AND id IN (SELECT message_id FROM mms_pending WHERE since < ?)",
            arrayOf<Any>(MessageStatus.FAILED.code, MessageStatus.SENDING.code, olderThanMs),
        )
        publish()
    }

    /** A picture message that never got an answer from the operator becomes "not sent", with a retry. */
    @Synchronized
    fun expireStaleSends(olderThanMs: Long) {
        helper.writableDatabase.execSQL(
            "UPDATE messages SET status = ? WHERE mms = 1 AND outgoing = 1 AND status = ? AND at < ?",
            arrayOf<Any>(MessageStatus.FAILED.code, MessageStatus.SENDING.code, olderThanMs),
        )
        publish()
    }

    /** Opening a conversation reads everything received in it. */
    @Synchronized
    fun markThreadRead(key: String) {
        helper.writableDatabase.update("messages", ContentValues().apply { put("read", 1) }, "thread = ? AND outgoing = 0", arrayOf(key))
        publish()
    }

    @Synchronized
    fun setStatus(id: String, status: MessageStatus) {
        helper.writableDatabase.update("messages", ContentValues().apply { put("status", status.code) }, "id = ?", arrayOf(id))
        publish()
    }

    /**
     * Erases the whole history of this app (the system SMS box is not touched), pictures included. Freed pages are overwritten
     * and the file is compacted, so deleted messages cannot be recovered from the database file.
     */
    @Synchronized
    fun clear() {
        val db = helper.writableDatabase
        val files = db.rawQuery("SELECT path FROM attachments", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        db.rawQuery("PRAGMA secure_delete = ON", null).use { it.moveToFirst() }
        db.delete("messages", null, null) // attachments and pending rows follow (ON DELETE CASCADE / cleared below)
        db.delete("attachments", null, null)
        db.delete("mms_pending", null, null)
        files.forEach { runCatching { java.io.File(it).delete() } }
        // Pictures never recorded in the database (a crash half way), empty folders that reveal how many messages there were,
        // and any transfer file still in the cache: all of it goes.
        runCatching { java.io.File(appContext.filesDir, "mms").deleteRecursively() }
        runCatching { java.io.File(appContext.cacheDir, "mms-transfer").deleteRecursively() }
        db.execSQL("VACUUM")
        publish()
    }

    @Synchronized
    fun count(): Int =
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM messages", null).use { it.moveToFirst(); it.getInt(0) }

    @Synchronized
    fun status(id: String): MessageStatus? =
        helper.readableDatabase.rawQuery("SELECT status FROM messages WHERE id = ?", arrayOf(id))
            .use { if (it.moveToFirst()) MessageStatus.fromCode(it.getInt(0)) else null }

    /** Number of received messages not opened yet (all history, not only the recent ones). */
    @Synchronized
    fun unreadCount(): Int =
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM messages WHERE read = 0 AND outgoing = 0", null)
            .use { it.moveToFirst(); it.getInt(0) }

    /** The messages we sent ourselves (random "out-" id) since [sinceMs], oldest first. */
    @Synchronized
    fun sentSince(sinceMs: Long): List<StoredMessage> =
        query("outgoing = 1 AND substr(id, 1, 4) = 'out-' AND at >= ?", arrayOf(sinceMs.toString()), "at ASC", null)

    /**
     * Deletes the system's copy of SMS that we sent ourselves and that an earlier version copied next to our own record (see [SmsTwins]);
     * our own record, which carries the real send status, stays. One to one, the closest in time; only plain SMS rows of ours are ever
     * candidates, never a received message nor a picture message. Returns how many were removed.
     */
    @Synchronized
    fun removeSmsTwins(): Int {
        val sent = query("channel = ? AND outgoing = 1 AND mms = 0", arrayOf(Channel.SMS.wire), "at ASC", null)
        val locals = sent.filter(SmsTwins::isLocalSent).groupBy { SmsTwins.key(it) }.filterKeys { it != null }.mapValues { it.value.toMutableList() }
        if (locals.isEmpty()) return 0
        val doomed = ArrayList<String>()
        for (row in sent.filter(SmsTwins::isSystemSent)) {
            val group = locals[SmsTwins.key(row)] ?: continue
            val twin = SmsTwins.twinOf(row, group) ?: continue
            group.remove(twin)
            doomed += row.id
        }
        if (doomed.isEmpty()) return 0
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            doomed.chunked(500).forEach { chunk -> db.delete("messages", "id IN (${chunk.joinToString(",") { "?" }})", chunk.toTypedArray()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        publish()
        return doomed.size
    }

    /** One conversation by its [ThreadKey], oldest first. */
    @Synchronized
    fun thread(key: String): List<StoredMessage> = query("thread = ?", arrayOf(key), "at ASC", null)

    /** The whole SMS conversation with one phone number (any way of writing it), oldest first. */
    @Synchronized
    fun conversation(address: String): List<StoredMessage> =
        query("thread = ?", arrayOf(ThreadKey.of(Channel.SMS, address, address)), "at ASC", null)

    private fun publish() {
        _messages.value = query(null, null, "at DESC", RECENT.toString()).reversed()
    }

    private fun query(where: String?, args: Array<String>?, order: String, limit: String?): List<StoredMessage> =
        withAttachments(rawQuery(where, args, order, limit))

    /** One extra query for all the pictures of these messages (not one per message). */
    private fun withAttachments(list: List<StoredMessage>): List<StoredMessage> {
        val ids = list.filter { it.mms }.map { it.id }
        if (ids.isEmpty()) return list
        val byMessage = HashMap<String, MutableList<Attachment>>()
        ids.chunked(500).forEach { chunk ->
            helper.readableDatabase.rawQuery(
                "SELECT message_id, mime, path FROM attachments WHERE message_id IN (${chunk.joinToString(",") { "?" }}) ORDER BY id",
                chunk.toTypedArray(),
            ).use { c ->
                while (c.moveToNext()) byMessage.getOrPut(c.getString(0)) { mutableListOf() }.add(Attachment(c.getString(1), c.getString(2)))
            }
        }
        return list.map { if (it.mms) it.copy(attachments = byMessage[it.id].orEmpty()) else it }
    }

    private fun rawQuery(where: String?, args: Array<String>?, order: String, limit: String?): List<StoredMessage> =
        helper.readableDatabase.query("messages", COLUMNS, where, args, null, null, order, limit).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val channel = Channel.entries.firstOrNull { it.wire == c.getString(3) } ?: continue
                    add(
                        StoredMessage(
                            id = c.getString(0), from = c.getString(1), address = c.getString(2), channel = channel,
                            outgoing = c.getInt(4) != 0, text = c.getString(5), receivedAtMs = c.getLong(6),
                            read = c.getInt(7) != 0, status = MessageStatus.fromCode(c.getInt(8)), sensitive = c.getInt(9) != 0, mms = c.getInt(10) != 0,
                        ),
                    )
                }
            }
        }

    private fun values(m: StoredMessage) = ContentValues().apply {
        put("id", m.id); put("who", m.from); put("address", m.address); put("channel", m.channel.wire)
        put("thread", ThreadKey.of(m.channel, m.address, m.from))
        put("outgoing", if (m.outgoing) 1 else 0); put("body", m.text); put("at", m.receivedAtMs)
        put("read", if (m.read) 1 else 0); put("status", m.status.code); put("sensitive", if (m.sensitive) 1 else 0); put("mms", if (m.mms) 1 else 0)
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
        private const val VERSION = 3
        private val COLUMNS = arrayOf("id", "who", "address", "channel", "outgoing", "body", "at", "read", "status", "sensitive", "mms")

        @Volatile private var instance: MessageStore? = null

        /** Lets tests start each case with a fresh store. */
        internal fun replaceInstanceForTests(store: MessageStore?) {
            instance = store
        }

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
