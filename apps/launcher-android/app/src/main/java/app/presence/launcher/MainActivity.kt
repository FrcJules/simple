package app.presence.launcher

import android.Manifest
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
import app.presence.launcher.ui.HomeScreen
import app.presence.launcher.ui.CallingScreen
import app.presence.launcher.ui.HomeTile
import app.presence.launcher.ui.PermissionRationaleScreen

class MainActivity : ComponentActivity() {
    private enum class Screen { HOME, RATIONALE, CALL, CALLING }

    private val contactsVm: ContactsViewModel by viewModels()
    private var screen by mutableStateOf(Screen.HOME)
    private var calling by mutableStateOf<Contact?>(null)

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Whatever the answer, show the call screen: it degrades gracefully (empty state, dialer instead of direct call).
        contactsVm.refresh()
        screen = Screen.CALL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val contacts by contactsVm.contacts.collectAsStateWithLifecycle()
            when (screen) {
                Screen.HOME -> HomeScreen(seniorName = ProfileStore(this).name(), onTile = ::onTile)
                Screen.RATIONALE -> {
                    BackHandler { screen = Screen.HOME }
                    PermissionRationaleScreen(
                        onContinue = { permissions.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)) },
                        onLater = { screen = Screen.HOME },
                    )
                }
                Screen.CALL -> {
                    BackHandler { screen = Screen.HOME }
                    CallScreen(
                        contacts = contacts,
                        onBack = { screen = Screen.HOME },
                        onCall = { calling = it; screen = Screen.CALLING },
                    )
                }
                Screen.CALLING -> {
                    BackHandler { screen = Screen.CALL }
                    calling?.let { c ->
                        CallingScreen(
                            contact = c,
                            onCancel = { screen = Screen.CALL },
                            onLaunch = {
                                LauncherActions.call(this, c.number, direct = granted(Manifest.permission.CALL_PHONE))
                                screen = Screen.HOME
                            },
                        )
                    } ?: run { screen = Screen.HOME }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (granted(Manifest.permission.READ_CONTACTS)) contactsVm.refresh()
    }

    private fun onTile(tile: HomeTile) = when (tile) {
        HomeTile.CALL -> openCallScreen()
        HomeTile.MESSAGES -> LauncherActions.messages(this)
        HomeTile.CAMERA -> LauncherActions.camera(this)
        HomeTile.PHOTOS -> LauncherActions.gallery(this)
    }

    private fun openCallScreen() {
        if (granted(Manifest.permission.READ_CONTACTS) && granted(Manifest.permission.CALL_PHONE)) {
            screen = Screen.CALL
        } else {
            screen = Screen.RATIONALE
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
