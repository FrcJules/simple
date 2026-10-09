package app.presence.launcher.sms

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.Telephony

/** A row of the system SMS database, as we need it to import history. */
data class ProviderSms(val id: Long, val address: String, val body: String, val dateMs: Long, val type: Int, val read: Boolean)

/**
 * The system SMS database (`content://sms`). As the default SMS app we must keep it up to date for the rest of the
 * system; behind an interface so the logic can be tested without a real provider.
 */
interface SmsProviderGateway {
    /** Records a received SMS in the system inbox. Returns its row id, or null if we are not allowed to write. */
    fun insertIncoming(address: String, body: String, dateMs: Long, dateSentMs: Long, subId: Int): Long?

    /** Records an SMS we are about to send (outbox). */
    fun insertOutgoing(address: String, body: String, dateMs: Long, subId: Int): Long?

    fun setType(providerId: Long, type: Int)

    /** Everything in the system SMS database, oldest first. */
    fun readAll(): List<ProviderSms>

    /** The rows added after [afterId] (the highest row id already copied), oldest first: used when we are not the default SMS app. */
    fun readSince(afterId: Long): List<ProviderSms>
}

class TelephonyGateway(private val resolver: ContentResolver) : SmsProviderGateway {
    override fun insertIncoming(address: String, body: String, dateMs: Long, dateSentMs: Long, subId: Int): Long? =
        insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, dateMs)
            put(Telephony.Sms.DATE_SENT, dateSentMs)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        })

    override fun insertOutgoing(address: String, body: String, dateMs: Long, subId: Int): Long? =
        insert(Telephony.Sms.Outbox.CONTENT_URI, ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, dateMs)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        })

    override fun setType(providerId: Long, type: Int) {
        try {
            resolver.update(
                Telephony.Sms.CONTENT_URI, ContentValues().apply { put(Telephony.Sms.TYPE, type) },
                "${Telephony.Sms._ID} = ?", arrayOf(providerId.toString()),
            )
        } catch (e: SecurityException) {
            // Not the default SMS app any more: the system record is no longer ours to update.
        }
    }

    override fun readAll(): List<ProviderSms> = readSince(0)

    override fun readSince(afterId: Long): List<ProviderSms> =
        try {
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ),
                "${Telephony.Sms._ID} > ?", arrayOf(afterId.toString()), "${Telephony.Sms.DATE} ASC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(ProviderSms(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getLong(3), c.getInt(4), c.getInt(5) != 0))
                    }
                }
            }.orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }

    private fun insert(uri: android.net.Uri, values: ContentValues): Long? =
        try {
            resolver.insert(uri, values)?.lastPathSegment?.toLongOrNull()
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
}
