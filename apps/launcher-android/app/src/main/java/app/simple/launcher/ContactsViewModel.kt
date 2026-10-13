package app.simple.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.simple.launcher.photos.ContactPhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsViewModel(app: Application) : AndroidViewModel(app) {
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())

    /** The people shown to the senior: the ones the family chose, or (if none chosen yet) the phone's starred contacts. */
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _all = MutableStateFlow<List<Contact>>(emptyList())

    /** Every contact: the family's picker in the settings, and the "Général" tab of the call screen. Loaded on demand. */
    val allContacts: StateFlow<List<Contact>> = _all.asStateFlow()

    private val _allLoaded = MutableStateFlow(false)

    /** False until the full list was read once: "nobody matches" must not be said while it is still loading. */
    val allLoaded: StateFlow<Boolean> = _allLoaded.asStateFlow()

    private val _chosen = MutableStateFlow<Set<Long>>(emptySet())

    /** Ids chosen by the family. */
    val chosen: StateFlow<Set<Long>> = _chosen.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val (shown, ids) = withContext(Dispatchers.IO) {
                try {
                    val photos = ContactPhotoStore(getApplication())
                    val repo = ContactsRepository(getApplication<Application>().contentResolver)
                    val ids = FavoritesStore(getApplication()).ids()
                    val list = if (ids.isNotEmpty()) repo.byIds(ids) else repo.starred()
                    list.map { c -> c.copy(photoUri = photos.uriFor(c.id) ?: c.photoUri) } to ids
                } catch (e: SecurityException) {
                    emptyList<Contact>() to emptySet() // permission refused: show the empty state, never a technical error
                }
            }
            _contacts.value = shown
            _chosen.value = ids
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                try {
                    val photos = ContactPhotoStore(getApplication())
                    ContactsRepository(getApplication<Application>().contentResolver).all().map { c -> c.copy(photoUri = photos.uriFor(c.id) ?: c.photoUri) }
                } catch (e: SecurityException) {
                    emptyList()
                }
            }
            _all.value = list
            _allLoaded.value = true
        }
    }

    /** Forgets the full list (kept only while the call screen needs it). */
    fun clearAll() {
        _all.value = emptyList()
        _allLoaded.value = false
    }

    /** The family shows or hides one person. */
    fun toggle(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            FavoritesStore(getApplication()).toggle(id)
            refresh()
        }
    }
}
