package app.presence.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presence.launcher.ui.PresenceTheme
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.presence.launcher.config.ParseResult
import app.presence.launcher.config.UiConfigParser
import app.presence.launcher.config.UiConfigStore
import app.presence.launcher.messages.MessageStore
import app.presence.launcher.photos.ContactPhotoStore
import app.presence.launcher.ui.Avatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.presence.launcher.notifications.PairingConfig
import app.presence.launcher.notifications.PairingStore

/** Family-only settings (hub address, token, notification access). Not reachable from Jean-Pierre's home screen. */
class SetupActivity : ComponentActivity() {
    private val contactsVm: ContactsViewModel by viewModels()
    private val photoStore by lazy { ContactPhotoStore(this) }
    private var pickFor by mutableStateOf<Long?>(null)
    private var photoFailed by mutableStateOf(false)

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = pickFor
        pickFor = null
        if (uri != null && id != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val ok = photoStore.save(id, contentResolver, uri)
                withContext(Dispatchers.Main) {
                    photoFailed = !ok
                    contactsVm.refresh()
                }
            }
        }
    }

    private val uiStore by lazy { UiConfigStore(this) }
    private var appearanceMessage by mutableStateOf<String?>(null)

    /** Reads at most MAX_CHARS + 1 characters: a larger file is refused by the parser without being loaded whole. */
    private val configPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { r ->
                    val buf = CharArray(UiConfigParser.MAX_CHARS + 1)
                    String(buf, 0, maxOf(r.read(buf), 0))
                }.orEmpty()
            } catch (e: java.io.IOException) {
                ""
            }
            appearanceMessage = when (val r = uiStore.import(text)) {
                is ParseResult.Ok -> getString(R.string.setup_appearance_ok)
                is ParseResult.Error -> getString(R.string.setup_appearance_error, r.message)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            contactsVm.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PairingStore(this)
        val profile = ProfileStore(this)
        setContent {
            PresenceTheme {
                var url by remember { mutableStateOf(store.load()?.baseUrl ?: "https://") }
                var token by remember { mutableStateOf("") } // never re-display the stored token
                var pin by remember { mutableStateOf(store.load()?.certSha256.orEmpty()) }
                var name by remember { mutableStateOf(profile.name()) }
                var saved by remember { mutableStateOf<Boolean?>(null) }
                var access by remember { mutableStateOf(hasNotificationAccess()) }
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.setup_title), fontSize = 28.sp)
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.setup_name)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.setup_hub_url)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        token, { token = it }, label = { Text(stringResource(R.string.setup_token)) },
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(pin, { pin = it }, label = { Text(stringResource(R.string.setup_fingerprint)) }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        profile.saveName(name)
                        val config = PairingConfig.parse(url, token, pin)
                        if (config != null) store.save(config)
                        saved = config != null
                    }) { Text(stringResource(R.string.setup_save)) }
                    when (saved) {
                        true -> Text(stringResource(R.string.setup_saved))
                        false -> Text(stringResource(R.string.setup_invalid))
                        null -> Unit
                    }
                    Text(stringResource(if (access) R.string.setup_access_on else R.string.setup_access_off))
                    OutlinedButton(onClick = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        access = hasNotificationAccess()
                    }) { Text(stringResource(R.string.setup_open_access)) }
                    OutlinedButton(onClick = { access = hasNotificationAccess() }) { Text(stringResource(R.string.setup_refresh)) }

                    Text(stringResource(R.string.setup_appearance_title), fontSize = 28.sp)
                    OutlinedButton(onClick = { configPicker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.setup_appearance_import)) }
                    OutlinedButton(onClick = {
                        uiStore.reset()
                        appearanceMessage = getString(R.string.setup_appearance_reset)
                    }) { Text(stringResource(R.string.setup_appearance_reset_button)) }
                    appearanceMessage?.let { Text(it) }

                    var erased by remember { mutableStateOf(false) }
                    ConfirmedButton(stringResource(R.string.setup_erase_messages), stringResource(R.string.setup_erase_confirm)) {
                        MessageStore.get(this@SetupActivity).clear()
                        erased = true
                    }
                    ConfirmedButton(stringResource(R.string.setup_erase_photos), stringResource(R.string.setup_erase_confirm)) {
                        photoStore.clearAll()
                        contactsVm.refresh()
                        erased = true
                    }
                    if (BuildConfig.SMS_ROLE_OFFERED) {
                        var offered by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = {
                            profile.setSmsRoleDeclined(false)
                            offered = true
                        }) { Text(stringResource(R.string.setup_sms_offer)) }
                        if (offered) Text(stringResource(R.string.setup_sms_offered))
                    }
                    if (erased) Text(stringResource(R.string.setup_erased))

                    val contacts by contactsVm.contacts.collectAsStateWithLifecycle()
                    Text(stringResource(R.string.setup_people), fontSize = 28.sp)
                    if (contacts.isEmpty()) Text(stringResource(R.string.setup_people_empty))
                    if (photoFailed) Text(stringResource(R.string.setup_photo_failed))
                    contacts.forEach { c ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(c, Modifier.size(72.dp), initialSize = 36.sp)
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontSize = 24.sp)
                                OutlinedButton(onClick = {
                                    pickFor = c.id
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }) { Text(stringResource(R.string.setup_photo_choose)) }
                                if (c.photoUri?.startsWith("file:") == true) {
                                    OutlinedButton(onClick = {
                                        photoStore.remove(c.id)
                                        contactsVm.refresh()
                                    }) { Text(stringResource(R.string.setup_photo_remove)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasNotificationAccess() = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
}

/** A destructive action needs two touches: the first one only asks. */
@androidx.compose.runtime.Composable
private fun ConfirmedButton(label: String, confirmLabel: String, onConfirmed: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { if (armed) { armed = false; onConfirmed() } else armed = true }) {
        Text(if (armed) confirmLabel else label)
    }
}
