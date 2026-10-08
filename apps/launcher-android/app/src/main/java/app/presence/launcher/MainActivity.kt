package app.presence.launcher

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.presence.launcher.config.TileAction
import app.presence.launcher.config.TileConfig
import app.presence.launcher.config.UiConfig
import app.presence.launcher.config.UiConfigStore
import app.presence.launcher.messages.MessageItem
import app.presence.launcher.messages.MessageStore
import app.presence.launcher.mms.MmsComponents
import app.presence.launcher.mms.MmsFiles
import app.presence.launcher.mms.MmsSendResult
import app.presence.launcher.messages.MessagesViewModel
import app.presence.launcher.messages.Speaker
import app.presence.launcher.messages.StoredMessage
import app.presence.launcher.notifications.Channel
import app.presence.launcher.sms.MessageNotifier
import app.presence.launcher.sms.SendResult
import app.presence.launcher.sms.SmsComponents
import app.presence.launcher.sms.SmsRole
import app.presence.launcher.ui.CallScreen
import app.presence.launcher.ui.CallingScreen
import app.presence.launcher.ui.ConversationScreen
import app.presence.launcher.ui.HomeScreen
import app.presence.launcher.ui.ImageViewerScreen
import app.presence.launcher.ui.LocalThemeConfig
import app.presence.launcher.ui.MessageScreen
import app.presence.launcher.ui.MessagesScreen
import app.presence.launcher.ui.PermissionRationaleScreen
import app.presence.launcher.ui.PhotoChoiceScreen
import app.presence.launcher.ui.PhotoConfirmScreen
import app.presence.launcher.ui.PickContactScreen
import app.presence.launcher.ui.ReplyConfirmScreen
import app.presence.launcher.ui.SmsRoleScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private enum class Screen { HOME, RATIONALE, DENIED, CALL, SMS_ROLE, MESSAGES, PICK_RECIPIENT, MESSAGE, REPLY, PHOTO_CHOICE, PHOTO_CONFIRM }

    private val contactsVm: ContactsViewModel by viewModels()
    private val messagesVm: MessagesViewModel by viewModels()
    private val profile by lazy { ProfileStore(this) }
    private val uiConfigStore by lazy { UiConfigStore(this) }
    private var uiConfig by mutableStateOf(UiConfig.Default)
    private var speakerInstance: Speaker? = null
    private fun speaker() = speakerInstance ?: Speaker(this).also { speakerInstance = it }
    private var screen by mutableStateOf(Screen.HOME)

    /** Set while "J'appelle X…" is shown; null otherwise. Kept separate so the call screen can never exist without a contact. */
    private var calling by mutableStateOf<Contact?>(null)
    private var seniorName by mutableStateOf("")

    /** The conversation being read, by its thread key: the live item is looked up so a late photo or message still shows. */
    private var openThreadKey by mutableStateOf<String?>(null)

    /** A conversation that does not exist yet (writing to someone for the first time, or a `sms:` link). */
    private var composeItem by mutableStateOf<MessageItem?>(null)
    private var replyText by mutableStateOf("")

    /** "Plus tard" on the SMS-role screen lasts until the app is restarted; only a "no" in Android's own dialog is remembered. */
    private var smsRoleLater = false

    /** The picture about to be sent (camera or picker), shown for confirmation. */
    private var photoToSend by mutableStateOf<Uri?>(null)
    private var sendingPhoto by mutableStateOf(false)
    private var cameraTarget: Uri? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val target = cameraTarget
        cameraTarget = null
        if (taken && target != null) {
            photoToSend = target
            notice = null
            screen = Screen.PHOTO_CONFIRM
        } else {
            discardTransferPhoto(target) // cancelled (or the process was restarted): never leave the camera's file behind
            if (target == null) notice = R.string.photo_send_failed
        }
    }

    private val pickPicture = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            photoToSend = uri
            notice = null
            screen = Screen.PHOTO_CONFIRM
        }
    }

    /** The picture shown full screen, if any (path of a private file). */
    private var viewingImage by mutableStateOf<String?>(null)
    private var importing by mutableStateOf(false)

    /** A short explanation shown on the conversation screen (e.g. nothing was heard), as text and not a fleeting toast. */
    private var notice by mutableStateOf<Int?>(null)

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        profile.markContactsAsked()
        contactsVm.refresh()
        messagesVm.refresh()
        // Contacts refused: explain simply. Only the call permission refused: still fine, the dialer is used instead.
        screen = if (granted(Manifest.permission.READ_CONTACTS)) Screen.CALL else Screen.DENIED
    }

    private val smsRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { onSmsRoleAnswered() }
    private val notificationsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val dictation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if (spoken.isNotEmpty()) {
            replyText = spoken
            notice = null
            screen = Screen.REPLY
        } else {
            // Closing the microphone window is not "nothing heard": only say so when it really listened.
            notice = if (result.resultCode == Activity.RESULT_CANCELED) null else R.string.dictate_empty
            screen = Screen.MESSAGE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // The camera app may outlive us: keep the name of the file it is writing so we can find (and delete) it afterwards.
        cameraTarget?.lastPathSegment?.let { outState.putString(STATE_CAMERA_FILE, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(STATE_CAMERA_FILE)?.let { name ->
            val files = MmsFiles(this)
            files.transferFile(name)?.let { cameraTarget = files.uriFor(it) }
        }
        MmsFiles(this).cleanOldTransfers(System.currentTimeMillis() - STALE_TRANSFER_MS)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val contacts by contactsVm.contacts.collectAsStateWithLifecycle()
            val conversations by messagesVm.items.collectAsStateWithLifecycle()
            val thread by messagesVm.thread.collectAsStateWithLifecycle()
            val unread by messagesVm.unread.collectAsStateWithLifecycle()
            val open: MessageItem? = conversations.firstOrNull { it.threadKey == openThreadKey }
                ?: composeItem?.takeIf { it.threadKey == openThreadKey }
            val c = calling
            val image = viewingImage
            CompositionLocalProvider(LocalThemeConfig provides uiConfig.theme) {
                if (image != null) {
                    BackHandler { viewingImage = null }
                    ImageViewerScreen(image, onBack = { viewingImage = null })
                } else if (c != null) {
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
                    Screen.HOME -> HomeScreen(seniorName = seniorName, unreadMessages = unread, config = uiConfig, onTile = ::onTile)
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
                    Screen.SMS_ROLE -> {
                        BackHandler { screen = Screen.HOME }
                        SmsRoleScreen(onContinue = { requestSmsRole() }, onLater = { declineSmsRole() })
                    }
                    Screen.MESSAGES -> {
                        BackHandler { screen = Screen.HOME }
                        if (importing) MessageScreen(R.string.importing, onBack = { screen = Screen.HOME }) else MessagesScreen(
                            items = conversations,
                            nowMs = System.currentTimeMillis(),
                            onOpen = { openConversation(it) },
                            onWrite = { write() },
                            onBack = { screen = Screen.HOME },
                        )
                    }
                    Screen.PICK_RECIPIENT -> {
                        BackHandler { screen = Screen.MESSAGES }
                        PickContactScreen(R.string.write_to_title, contacts, onPick = { startNewConversation(it) }, onBack = { screen = Screen.MESSAGES })
                    }
                    Screen.MESSAGE -> {
                        BackHandler { closeConversation() }
                        if (open == null) {
                            LaunchedEffect(Unit) { closeConversation() } // the conversation vanished (history erased): back to the list
                        } else {
                            ConversationScreen(
                                item = open,
                                messages = thread,
                                nowMs = System.currentTimeMillis(),
                                canDictate = canDictate(),
                                notice = notice?.let { stringResource(it) },
                                onListen = { listen(thread) },
                                onReply = { dictate() },
                                onBack = { closeConversation() },
                                onRetry = { failed -> if (failed.mms) retryMms(failed) else sendReply(open, failed.text) },
                                onImage = { viewingImage = it },
                                onSendPhoto = if (open.canReply && SmsRole.isHeld(this)) ({ notice = null; screen = Screen.PHOTO_CHOICE }) else null,
                            )
                        }
                    }
                    Screen.PHOTO_CHOICE -> {
                        BackHandler { screen = Screen.MESSAGE }
                        if (open == null) {
                            LaunchedEffect(Unit) { closeConversation() }
                        } else {
                            PhotoChoiceScreen(
                                contact = open.displayContact,
                                known = open.contact != null,
                                canTakePhoto = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
                                notice = notice?.let { stringResource(it) },
                                onTake = { takePhoto() },
                                onPick = { pickPhoto() },
                                onBack = { notice = null; screen = Screen.MESSAGE },
                            )
                        }
                    }
                    Screen.PHOTO_CONFIRM -> {
                        BackHandler { if (!sendingPhoto) notThisPhoto() }
                        val photo = photoToSend
                        if (open == null || photo == null) {
                            LaunchedEffect(Unit) { closeConversation() }
                        } else {
                            PhotoConfirmScreen(
                                contact = open.displayContact,
                                known = open.contact != null,
                                photo = photo,
                                notice = notice?.let { stringResource(it) },
                                sending = sendingPhoto,
                                onSend = { sendPhoto(open, photo) },
                                onNotThisOne = { notThisPhoto() },
                            )
                        }
                    }
                    Screen.REPLY -> {
                        BackHandler { screen = Screen.MESSAGE }
                        if (open == null) {
                            LaunchedEffect(Unit) { closeConversation() }
                        } else {
                            ReplyConfirmScreen(
                                item = open,
                                text = replyText,
                                directSend = SmsRole.isHeld(this),
                                isNew = thread.isEmpty(),
                                notice = notice?.let { stringResource(it) } ?: if (open.contact == null) stringResource(R.string.reply_unknown_number) else null,
                                onListen = { speakText(replyText) },
                                onSend = { sendReply(open, replyText) },
                                onRedo = { dictate() },
                                onCancel = { screen = Screen.MESSAGE },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        seniorName = profile.name() // may have been changed in "Réglages famille"
        uiConfig = uiConfigStore.load()
        if (granted(Manifest.permission.READ_CONTACTS)) {
            contactsVm.refresh()
            messagesVm.refresh() // a photo may have been chosen in "Réglages famille"
        }
        // A picture message whose download never finished becomes "not received", with a retry, instead of waiting forever.
        lifecycleScope.launch(Dispatchers.IO) {
            MessageStore.get(this@MainActivity).apply {
                expireStaleDownloads(System.currentTimeMillis() - STALE_DOWNLOAD_MS)
                expireStaleSends(System.currentTimeMillis() - STALE_DOWNLOAD_MS)
            }
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

    /** The Home button (launchMode singleTask) returns to the home screen; a notification or an `sms:` link goes where it says. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        calling = null
        if (!handleIntent(intent)) {
            leaveConversation()
            screen = Screen.HOME
        }
    }

    /** Returns true when the intent asked for something specific (and we moved there). */
    private fun handleIntent(intent: Intent?): Boolean {
        intent ?: return false
        if (intent.action == MessageNotifier.ACTION_OPEN_MESSAGES) {
            screen = Screen.MESSAGES
            return true
        }
        val uri = intent.data
        if ((intent.action == Intent.ACTION_SENDTO || intent.action == Intent.ACTION_SEND) && uri != null && uri.scheme in SMS_SCHEMES) {
            val number = Uri.decode(uri.schemeSpecificPart).substringBefore('?').trim()
            if (number.isBlank()) return false
            val body = (intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT))?.trim().orEmpty()
            startConversationWithNumber(number, body)
            return true
        }
        return false
    }

    private fun onTile(tile: TileConfig) = when (tile.action) {
        TileAction.CALL -> openCallScreen()
        TileAction.MESSAGES -> openMessages()
        TileAction.CAMERA -> LauncherActions.camera(this)
        TileAction.GALLERY -> LauncherActions.gallery(this)
    }

    // ---- SMS role ----

    private fun shouldOfferSmsRole() =
        BuildConfig.SMS_ROLE_OFFERED && !SmsRole.isHeld(this) && !profile.smsRoleDeclined() && !smsRoleLater && SmsRole.requestIntent(this) != null

    private fun openMessages() {
        screen = if (shouldOfferSmsRole()) Screen.SMS_ROLE else Screen.MESSAGES
    }

    private fun requestSmsRole() {
        val intent = SmsRole.requestIntent(this)
        if (intent == null) {
            screen = Screen.MESSAGES
            return
        }
        try {
            smsRole.launch(intent)
        } catch (e: ActivityNotFoundException) {
            screen = Screen.MESSAGES
        }
    }

    private fun declineSmsRole() {
        smsRoleLater = true // not remembered: the family can still choose it later, and it is offered again next time the app starts
        screen = Screen.MESSAGES
    }

    private fun onSmsRoleAnswered() {
        if (SmsRole.isHeld(this)) {
            profile.setSmsRoleDeclined(false)
            if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS) // so new messages can be announced
            }
            if (!profile.smsImported()) {
                importing = true
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        try {
                            SmsComponents.importer(this@MainActivity).importAll()
                            true
                        } catch (e: RuntimeException) {
                            false // try again next time: the history is only copied, never moved
                        }
                    }
                    if (ok) profile.markSmsImported()
                    importing = false
                    messagesVm.refresh()
                }
            }
        } else {
            profile.setSmsRoleDeclined(true)
        }
        screen = Screen.MESSAGES
    }

    // ---- Messages ----

    private fun openConversation(item: MessageItem) {
        notice = null
        composeItem = null
        openThreadKey = item.threadKey
        messagesVm.select(item.threadKey)
        screen = Screen.MESSAGE
    }

    /** Leaves the open conversation, reading what was received in it. */
    private fun leaveConversation() {
        openThreadKey?.let { messagesVm.markThreadRead(it) }
        openThreadKey = null
        composeItem = null
        discardTransferPhoto(photoToSend)
        photoToSend = null
        sendingPhoto = false
        messagesVm.select(null)
    }

    private fun closeConversation() {
        leaveConversation()
        screen = Screen.MESSAGES
    }

    private fun write() {
        if (SmsRole.isHeld(this)) screen = Screen.PICK_RECIPIENT else LauncherActions.messages(this)
    }

    private fun startNewConversation(contact: Contact) = startConversationWithNumber(contact.number, "", contact)

    private fun startConversationWithNumber(number: String, body: String, known: Contact? = null) {
        val contact = known ?: try {
            ContactsRepository(contentResolver).byNumber(number)
        } catch (e: SecurityException) {
            null
        }
        val name = contact?.name ?: number
        val first = StoredMessage("new", name, Channel.SMS, "", System.currentTimeMillis(), address = number)
        val item = MessageItem(first, contact)
        notice = null
        composeItem = item
        openThreadKey = item.threadKey
        messagesVm.select(item.threadKey)
        if (body.isNotEmpty()) {
            replyText = body
            screen = Screen.REPLY
        } else {
            screen = Screen.MESSAGE // the big "Dire mon message" button starts the microphone, never a surprise
        }
    }

    private fun sendReply(item: MessageItem, text: String) {
        val number = item.replyNumber
        if (number == null) {
            notice = R.string.number_invalid // stay on the confirmation, with the text still there
            return
        }
        if (!SmsRole.isHeld(this)) {
            LauncherActions.smsDraft(this, number, text)
            closeConversation()
            return
        }
        // Leave the confirmation at once so a second touch cannot send twice; the message shows up with "Envoi en cours…".
        notice = null
        screen = Screen.MESSAGE
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { SmsComponents.sender(this@MainActivity).send(number, text, item.message.from) }
            if (result !is SendResult.Started) {
                // Nothing was stored: go back to the confirmation with the text, instead of losing what was dictated.
                replyText = text
                notice = R.string.send_failed
                screen = Screen.REPLY
            }
        }
    }

    /** Reads the newest message received in the open conversation. Only ever from a button press: nothing is read aloud on its own. */
    private fun listen(thread: List<StoredMessage>) {
        val last = thread.lastOrNull { !it.outgoing && !it.sensitive && it.text.isNotBlank() } ?: return
        speakText("${last.from}. ${last.text}")
    }

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

    /** A picture we could not receive is downloaded again; one we could not send is sent again from the copy we kept. */
    private fun retryMms(message: StoredMessage) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (message.outgoing) MmsComponents.sender(this@MainActivity).retry(message.id) else MmsComponents.inbox(this@MainActivity).retry(message.id)
        }
    }

    private fun takePhoto() {
        val files = MmsFiles(this)
        val file = try {
            files.newTransferFile() // a short-lived private file: the picture never lands in the gallery
        } catch (e: java.io.IOException) {
            notice = R.string.photo_send_failed
            return
        }
        val target = files.uriFor(file)
        cameraTarget = target
        notice = null
        try {
            takePicture.launch(target)
        } catch (e: ActivityNotFoundException) {
            cameraTarget = null
            files.deleteQuietly(file)
            notice = R.string.app_missing // shown on the choice screen
        }
    }

    private fun pickPhoto() {
        notice = null
        try {
            pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: ActivityNotFoundException) {
            notice = R.string.app_missing
        }
    }

    /** "Pas cette photo": the picture is dropped (and the camera's original file deleted) and the person chooses again. */
    private fun notThisPhoto() {
        discardTransferPhoto(photoToSend)
        photoToSend = null
        notice = null
        screen = Screen.PHOTO_CHOICE
    }

    /** The camera writes the full-resolution original (with its GPS position) into one of our temporary files: delete it as soon as it is of no use. */
    private fun discardTransferPhoto(uri: Uri?) {
        if (uri == null || uri.authority != MmsFiles.authority(this)) return
        val files = MmsFiles(this)
        uri.lastPathSegment?.let { files.deleteQuietly(files.transferFile(it)) }
    }

    private fun sendPhoto(item: MessageItem, photo: Uri) {
        val number = item.replyNumber ?: return
        if (sendingPhoto) return // a second touch while it goes must not send it twice
        sendingPhoto = true
        notice = null
        lifecycleScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { MmsComponents.sender(this@MainActivity).send(number, item.message.from, photo) }
            } catch (e: RuntimeException) {
                MmsSendResult.Unreadable
            }
            sendingPhoto = false
            if (result is MmsSendResult.Started) {
                discardTransferPhoto(photo)
                photoToSend = null
                notice = R.string.photo_on_the_way
                screen = Screen.MESSAGE // the picture appears in the conversation, "Envoi en cours…"
            } else {
                notice = R.string.photo_send_failed // stay on the confirmation: send again, or choose another picture
            }
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

    private companion object {
        const val STATE_CAMERA_FILE = "camera_file"
        const val STALE_TRANSFER_MS = 20 * 60 * 1000L
        const val STALE_DOWNLOAD_MS = 10 * 60 * 1000L
        val SMS_SCHEMES = setOf("sms", "smsto") // mms:/mmsto: once MMS are handled
    }
}
