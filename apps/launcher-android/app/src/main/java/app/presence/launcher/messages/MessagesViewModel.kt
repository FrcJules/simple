package app.presence.launcher.messages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.presence.launcher.Contact
import app.presence.launcher.ContactsRepository
import app.presence.launcher.photos.ContactPhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagesViewModel(app: Application) : AndroidViewModel(app) {
    private val store = MessageStore.get(app)
    private val photos = ContactPhotoStore(app)
    private val faces = HashMap<String, Contact?>()

    private val _items = MutableStateFlow<List<MessageItem>>(emptyList())
    val items: StateFlow<List<MessageItem>> = _items.asStateFlow()

    /** Number of messages not opened yet, shown as a badge on the home screen. */
    val unread: StateFlow<Int> = store.messages.map { list -> list.count { !it.read } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch { store.messages.collect { rebuild(it) } }
    }

    /** Call after contacts or photos changed (permission granted, photo chosen). */
    fun refresh() {
        faces.clear()
        rebuild(store.messages.value)
    }

    fun markRead(id: String) {
        viewModelScope.launch(Dispatchers.IO) { store.markRead(id) }
    }

    private fun rebuild(list: List<StoredMessage>) {
        viewModelScope.launch {
            _items.value = withContext(Dispatchers.IO) {
                list.sortedByDescending { it.receivedAtMs }.map { MessageItem(it, face(it.from)) }
            }
        }
    }

    private fun face(name: String): Contact? =
        faces.getOrPut(name) {
            try {
                ContactsRepository(getApplication<Application>().contentResolver).byName(name)
                    ?.let { c -> c.copy(photoUri = photos.uriFor(c.id) ?: c.photoUri) }
            } catch (e: SecurityException) {
                null // contacts permission not granted: show the initial instead
            }
        }
}
