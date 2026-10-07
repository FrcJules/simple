package app.presence.launcher

import android.content.ContentResolver
import android.provider.ContactsContract.CommonDataKinds.Phone
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

    fun starred(): List<Contact> {
        val projection = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.PHOTO_URI)
        val seen = LinkedHashMap<Long, Contact>()
        resolver.query(
            Phone.CONTENT_URI,
            projection,
            "${Contacts.STARRED} = 1",
            null,
            "${Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (id !in seen) {
                    seen[id] = Contact(id, c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3)?.takeIf { it.startsWith("content://") }) // never a network URL
                }
            }
        }
        return seen.values.toList()
    }
}
