package app.presence.launcher

import android.content.ContentResolver
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts

/** Reads starred contacts: the family stars the people Jean-Pierre should see, no setup screen needed. */
class ContactsRepository(private val resolver: ContentResolver) {
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
