package app.presence.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.presence.launcher.messages.MessageItem
import app.presence.launcher.messages.MessagesViewModel
import app.presence.launcher.messages.Speaker
import app.presence.launcher.ui.CallScreen
import app.presence.launcher.ui.CallingScreen
import app.presence.launcher.ui.HomeScreen
import app.presence.launcher.ui.HomeTile
import app.presence.launcher.ui.MessageDetailScreen
import app.presence.launcher.ui.MessageScreen
import app.presence.launcher.ui.MessagesScreen
import app.presence.launcher.ui.PermissionRationaleScreen
import app.presence.launcher.ui.ReplyConfirmScreen

class MainActivity : ComponentActivity() {
    private enum class Screen { HOME, RATIONALE, DENIED, CALL, MESSAGES, MESSAGE, REPLY }

    private val contactsVm: ContactsViewModel by viewModels()
    private val messagesVm: MessagesViewModel by viewModels()
    private val profile by lazy { ProfileStore(this) }
    private var speakerInstance: Speaker? = null
    private fun speaker() = speakerInstance ?: Speaker(this).also { speakerInstance = it }
    private var screen by mutableStateOf(Screen.HOME)

    /** Set while "J'appelle X…" is shown; null otherwise. Kept separate so the call screen can never exist without a contact. */
    private var calling by mutableStateOf<Contact?>(null)
    private var seniorName by mutableStateOf("")

    /** The message being read, by id: the live item is looked up so a late photo or contact update still shows. */
    private var openMessageId by mutableStateOf<String?>(null)
    private var replyText by mutableStateOf("")

    /** A short explanation shown on the message screen (e.g. nothing was heard), as text and not a fleeting toast. */
    private var notice by mutableStateOf<Int?>(null)

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        profile.markContactsAsked()
        contactsVm.refresh()
        messagesVm.refresh()
        // Contacts refused: explain simply. Only the call permission refused: still fine, the dialer is used instead.
        screen = if (granted(Manifest.permission.READ_CONTACTS)) Screen.CALL else Screen.DENIED
    }

    private val dictation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if (spoken.isNotEmpty()) {
            replyText = spoken
            notice = null
            screen = Screen.REPLY
        } else {
            notice = R.string.dictate_empty
            screen = Screen.MESSAGE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val contacts by contactsVm.contacts.collectAsStateWithLifecycle()
            val messages by messagesVm.items.collectAsStateWithLifecycle()
            val unread by messagesVm.unread.collectAsStateWithLifecycle()
            val open: MessageItem? = messages.firstOrNull { it.message.id == openMessageId }
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
                Screen.HOME -> HomeScreen(seniorName = seniorName, unreadMessages = unread, onTile = ::onTile)
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
                Screen.MESSAGES -> {
                    BackHandler { screen = Screen.HOME }
                    MessagesScreen(
                        items = messages,
                        nowMs = System.currentTimeMillis(),
                        onOpen = { openMessage(it) },
                        onWrite = { LauncherActions.messages(this) },
                        onBack = { screen = Screen.HOME },
                    )
                }
                Screen.MESSAGE -> {
                    BackHandler { closeMessage() }
                    if (open == null) {
                        LaunchedEffect(Unit) { closeMessage() } // the message vanished (expired): back to the list
                    } else {
                        MessageDetailScreen(
                            item = open,
                            nowMs = System.currentTimeMillis(),
                            canDictate = canDictate(),
                            notice = notice?.let { stringResource(it) },
                            onListen = { listen(open) },
                            onReply = { dictate() },
                            onBack = { closeMessage() },
                        )
                    }
                }
                Screen.REPLY -> {
                    BackHandler { screen = Screen.MESSAGE }
                    if (open == null) {
                        LaunchedEffect(Unit) { closeMessage() }
                    } else {
                        ReplyConfirmScreen(
                            item = open,
                            text = replyText,
                            onListen = { speakText(replyText) },
                            onSend = {
                                val number = open.contact?.number
                                if (number != null) {
                                    LauncherActions.smsDraft(this, number, replyText)
                                    closeMessage()
                                } else {
                                    Toast.makeText(this, R.string.number_invalid, Toast.LENGTH_LONG).show() // stay here: nothing was prepared
                                }
                            },
                            onRedo = { dictate() },
                            onCancel = { screen = Screen.MESSAGE },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        seniorName = profile.name() // may have been changed in "Réglages famille"
        if (granted(Manifest.permission.READ_CONTACTS)) {
            contactsVm.refresh()
            messagesVm.refresh() // a photo may have been chosen in "Réglages famille"
        }
    }

    override fun onPause() {
        super.onPause()
        calling = null // never place a call after the screen went off or another app came to the front
        speakerInstance?.stop()
    }

    override fun onDestroy() {
        speakerInstance?.shutdown()
        super.onDestroy()
    }

    /** The Home button (launchMode singleTask) always returns to the home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        calling = null
        openMessageId = null
        screen = Screen.HOME
    }

    private fun onTile(tile: HomeTile) = when (tile) {
        HomeTile.CALL -> openCallScreen()
        HomeTile.MESSAGES -> screen = Screen.MESSAGES
        HomeTile.CAMERA -> LauncherActions.camera(this)
        HomeTile.PHOTOS -> LauncherActions.gallery(this)
    }

    private fun openMessage(item: MessageItem) {
        notice = null
        messagesVm.markRead(item.message.id)
        openMessageId = item.message.id
        screen = Screen.MESSAGE
    }

    private fun closeMessage() {
        openMessageId = null
        screen = Screen.MESSAGES
    }

    private fun listen(item: MessageItem) = speakText("${item.message.from}. ${item.message.text}")

    /** Only ever called from a button press: nothing is read aloud on its own. */
    private fun speakText(text: String) {
        speaker().speak(text) { Toast.makeText(this, R.string.listen_unavailable, Toast.LENGTH_LONG).show() }
    }

    private fun dictationIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // a request: the system service may still use its servers (ADR 0004)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.dictate_prompt))

    private fun canDictate() = dictationIntent().resolveActivity(packageManager) != null

    private fun dictate() {
        try {
            dictation.launch(dictationIntent())
        } catch (e: ActivityNotFoundException) {
            notice = R.string.dictate_unavailable
        }
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
