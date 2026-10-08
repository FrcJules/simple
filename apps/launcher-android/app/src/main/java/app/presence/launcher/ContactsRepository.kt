package app.presence.launcher

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.provider.ContactsContract.Contacts

/** Reads starred contacts: the family stars the people Jean-Pierre should see, no setup screen needed. */
class ContactsRepository(private val resolver: ContentResolver) {
    /**
     * The contact whose display name is [name] (ignoring case), or null. Used to put a face on a sender.
     * A name is not an identity: when several contacts share it we show none, so a stranger calling themselves
     * "Marie" never borrows the face (or number) of a relative.
     */
    fun byName(name: String): Contact? {
        if (name.isBlank()) return null
        val found = LinkedHashMap<Long, Contact>()
        resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.PHOTO_URI),
            "${Phone.DISPLAY_NAME} = ? COLLATE NOCASE",
            arrayOf(name.trim()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (id !in found) {
                    found[id] = Contact(id, c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3)?.takeIf { it.startsWith("content://") })
                }
            }
        }
        return found.values.singleOrNull()
    }

    /** The contact owning [number] (the system matches the different ways of writing it), or null. */
    fun byNumber(number: String): Contact? {
        if (number.isBlank() || number.any { it.isLetter() }) return null
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return try {
            resolver.query(uri, arrayOf(PhoneLookup.CONTACT_ID, PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_URI), null, null, null)?.use { c ->
                if (c.moveToFirst()) Contact(c.getLong(0), c.getString(1).orEmpty(), number, c.getString(2)?.takeIf { it.startsWith("content://") }) else null
            }
        } catch (e: SecurityException) {
            null // contacts permission not granted: the number will be shown instead of a name
        }
    }

    fun starred(): List<Contact> = query("${Contacts.STARRED} = 1")

    /** Every contact with a phone number, once each (first number), sorted by name: the pool the family picks from. */
    fun all(): List<Contact> = query(null)

    /** The contacts with these ids, sorted by name. */
    fun byIds(ids: Set<Long>): List<Contact> {
        if (ids.isEmpty()) return emptyList()
        return query("${Phone.CONTACT_ID} IN (${ids.joinToString(",")})") // ids are numbers from our own store, never free text
    }

    private fun query(selection: String?): List<Contact> {
        val projection = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.PHOTO_URI)
        val seen = LinkedHashMap<Long, Contact>()
        resolver.query(Phone.CONTENT_URI, projection, selection, null, "${Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (id !in seen) {
                    seen[id] = Contact(id, c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3)?.takeIf { it.startsWith("content://") })
                }
            }
        }
        return seen.values.toList()
    }
}
