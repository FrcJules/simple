package app.presence.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.presence.launcher.ui.CallScreen
import app.presence.launcher.ui.CallingScreen
import app.presence.launcher.ui.HomeScreen
import app.presence.launcher.ui.HomeTile
import app.presence.launcher.ui.MessageScreen
import app.presence.launcher.ui.PermissionRationaleScreen

class MainActivity : ComponentActivity() {
    private enum class Screen { HOME, RATIONALE, DENIED, CALL }

    private val contactsVm: ContactsViewModel by viewModels()
    private val profile by lazy { ProfileStore(this) }
    private var screen by mutableStateOf(Screen.HOME)

    /** Set while "J'appelle X…" is shown; null otherwise. Kept separate so the call screen can never exist without a contact. */
    private var calling by mutableStateOf<Contact?>(null)
    private var seniorName by mutableStateOf("")

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        profile.markContactsAsked()
        contactsVm.refresh()
        // Contacts refused: explain simply. Only the call permission refused: still fine, the dialer is used instead.
        screen = if (granted(Manifest.permission.READ_CONTACTS)) Screen.CALL else Screen.DENIED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val contacts by contactsVm.contacts.collectAsStateWithLifecycle()
            val c = calling
            if (c != null) {
                BackHandler { calling = null }
                CallingScreen(
                    contact = c,
                    onCancel = { calling = null },
                    onLaunch = {
                        calling = null
                        screen = Screen.HOME
                        LauncherActions.call(this, c.number, direct = granted(Manifest.permission.CALL_PHONE))
                    },
                )
            } else when (screen) {
                Screen.HOME -> HomeScreen(seniorName = seniorName, onTile = ::onTile)
                Screen.RATIONALE -> {
                    BackHandler { screen = Screen.HOME }
                    PermissionRationaleScreen(
                        onContinue = { permissions.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)) },
                        onLater = { screen = Screen.HOME },
                    )
                }
                Screen.DENIED -> {
                    BackHandler { screen = Screen.HOME }
                    MessageScreen(R.string.permission_denied, onBack = { screen = Screen.HOME })
                }
                Screen.CALL -> {
                    BackHandler { screen = Screen.HOME }
                    CallScreen(contacts = contacts, onBack = { screen = Screen.HOME }, onCall = { calling = it })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        seniorName = profile.name() // may have been changed in "Réglages famille"
        if (granted(Manifest.permission.READ_CONTACTS)) contactsVm.refresh()
    }

    override fun onPause() {
        super.onPause()
        calling = null // never place a call after the screen went off or another app came to the front
    }

    /** The Home button (launchMode singleTask) always returns to the home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        calling = null
        screen = Screen.HOME
    }

    private fun onTile(tile: HomeTile) = when (tile) {
        HomeTile.CALL -> openCallScreen()
        HomeTile.MESSAGES -> LauncherActions.messages(this)
        HomeTile.CAMERA -> LauncherActions.camera(this)
        HomeTile.PHOTOS -> LauncherActions.gallery(this)
    }

    private fun openCallScreen() {
        screen = when {
            granted(Manifest.permission.READ_CONTACTS) -> Screen.CALL
            // After a refusal Android may no longer show its dialog: do not loop on a button that does nothing.
            profile.contactsAsked() -> Screen.DENIED
            else -> Screen.RATIONALE
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
