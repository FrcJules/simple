package app.presence.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsViewModel(app: Application) : AndroidViewModel(app) {
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _contacts.value = withContext(Dispatchers.IO) {
                try {
                    ContactsRepository(getApplication<Application>().contentResolver).starred()
                } catch (e: SecurityException) {
                    emptyList() // permission refused: show the empty state, never a technical error
                }
            }
        }
    }
}
