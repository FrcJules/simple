package app.simple.launcher.messages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.simple.launcher.Contact
import app.simple.launcher.ContactsRepository
import app.simple.launcher.notifications.ReplyRegistry
import app.simple.launcher.photos.ContactPhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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

    private val _loaded = MutableStateFlow(false)

    /** False until the conversations were built once: an empty list before that does not mean "the conversation vanished". */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Number of messages not opened yet, shown as a badge on the home screen. */
    val unread: StateFlow<Int> = store.messages.map { store.unreadCount() }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        // Rebuilt when messages change and when a WhatsApp conversation becomes (or stops being) answerable.
        viewModelScope.launch { combine(store.messages, ReplyRegistry.available) { list, replyable -> list to replyable }.collect { (l, r) -> rebuild(l, r) } }
    }

    /** Call after contacts or photos changed (permission granted, photo chosen). */
    fun refresh() {
        faces.clear()
        rebuild(store.messages.value, ReplyRegistry.available.value)
    }

    private fun rebuild(list: List<StoredMessage>, replyable: Set<String>) {
        viewModelScope.launch {
            _items.value = withContext(Dispatchers.IO) { groupConversations(list, replyable, ::face) }
            _loaded.value = true
        }
    }

    /** The conversation currently open, oldest message first; empty when none. */
    fun select(threadKey: String?) {
        selected.value = threadKey
    }

    fun markThreadRead(key: String) {
        viewModelScope.launch(Dispatchers.IO) { store.markThreadRead(key) }
    }

    private val selected = MutableStateFlow<String?>(null)

    val thread: StateFlow<List<StoredMessage>> = combine(selected, store.messages) { key, _ -> key }
        .map { key -> if (key == null) emptyList() else store.thread(key) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The face of whoever wrote [m]: by number when the SMS carries one (an identity), by name otherwise (not an identity). */
    private fun face(m: StoredMessage): Contact? =
        faces.getOrPut(m.address ?: "name:${m.from}") {
            try {
                val repo = ContactsRepository(getApplication<Application>().contentResolver)
                val c = if (m.address != null) repo.byNumber(m.address) else repo.byName(m.from)
                c?.let { it.copy(photoUri = photos.uriFor(it.id) ?: it.photoUri) }
            } catch (e: SecurityException) {
                null // contacts permission not granted: show the initial instead
            }
        }
}
