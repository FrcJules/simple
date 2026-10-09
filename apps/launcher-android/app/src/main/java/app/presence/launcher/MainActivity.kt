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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
import app.presence.launcher.messages.MessageStatus
import app.presence.launcher.messages.MessageStore
import app.presence.launcher.mms.MmsComponents
import app.presence.launcher.mms.MmsFiles
import app.presence.launcher.mms.MmsSendResult
import app.presence.launcher.messages.MessagesViewModel
import app.presence.launcher.feedback.Feedback
import app.presence.launcher.security.FamilyLock
import app.presence.launcher.messages.Speaker
import app.presence.launcher.messages.StoredMessage
import app.presence.launcher.notifications.Channel
import app.presence.launcher.notifications.ReplyRegistry
import app.presence.launcher.sms.MessageNotifier
import app.presence.launcher.sms.SendResult
import app.presence.launcher.sms.SmsAccess
import app.presence.launcher.sms.SmsComponents
import app.presence.launcher.sms.SmsRole
import app.presence.launcher.ui.CallScreen
import app.presence.launcher.ui.CallingScreen
import app.presence.launcher.ui.ConversationScreen
import app.presence.launcher.ui.HomeScreen
import app.presence.launcher.ui.rememberHomeStatus
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
    private enum class Screen { HOME, RATIONALE, DENIED, CALL, SMS_ROLE, MESSAGES, PICK_RECIPIENT, MESSAGE, REPLY, PHOTO_CHOICE, PHOTO_CONFIRM, WRITE_NOT_READY }

    private val contactsVm: ContactsViewModel by viewModels()
    private val messagesVm: MessagesViewModel by viewModels()
    private val profile by lazy { ProfileStore(this) }
    private val uiConfigStore by lazy { UiConfigStore(this) }
    private var uiConfig by mutableStateOf(UiConfig.Default)
    private val feedback by lazy { Feedback(this) { profile.feedbackEnabled() } }
    private var speakerInstance: Speaker? = null
    private fun speaker() = speakerInstance ?: Speaker(this).also { speakerInstance = it }
    private var screen by mutableStateOf(Screen.HOME)

    /** No family code yet: the home screen shows a plain way in to the settings for whoever installs the app. */
    private var needsSetup by mutableStateOf(false)

    /** Set while "J'appelle X…" is shown; null otherwise. Kept separate so the call screen can never exist without a contact. */
    private var calling by mutableStateOf<Contact?>(null)
    private var seniorName by mutableStateOf("")

    /** The conversation being read, by its thread key: the live item is looked up so a late photo or message still shows. */
    private var openThreadKey by mutableStateOf<String?>(null)

    /** A conversation that does not exist yet (writing to someone for the first time, or a `sms:` link). */
    private var composeItem by mutableStateOf<MessageItem?>(null)
    private var replyText by mutableStateOf("")

    /** The WhatsApp notification an answer will go through, noted when the confirmation opens and checked again at "Envoyer". */
    private var replyTargetKey: String? = null
    private var sendingReply = false

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
            if (target == null) failure(R.string.photo_send_failed)
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

    /** Direct mode (Android did not make us the SMS app): the SMS permissions alone read old and new SMS and send. */
    private val smsPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onSmsPermissionsAnswered() }

    /** "Écrire un message" was touched before the SMS question: go on to choosing who to write to once it is answered. */
    private var pendingWrite = false
    private val notificationsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val dictation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if (spoken.isNotEmpty()) {
            replyText = spoken
            notice = null
            replyTargetKey = openThreadKey?.let { ReplyRegistry.currentKey(it) }
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
        // Android may kill the process while the camera or the microphone is in front: come back to the same conversation and draft.
        // Only plain text is kept (never a photo, never a call in progress); a screen that needs more falls back to its parent.
        val restorable = when (screen) {
            Screen.CALL, Screen.MESSAGES, Screen.PICK_RECIPIENT, Screen.MESSAGE, Screen.REPLY -> screen
            Screen.PHOTO_CHOICE, Screen.PHOTO_CONFIRM -> Screen.MESSAGE
            else -> Screen.HOME
        }
        outState.putString(STATE_SCREEN, restorable.name)
        openThreadKey?.let { outState.putString(STATE_THREAD, it) }
        composeItem?.let {
            outState.putString(STATE_COMPOSE_NUMBER, it.message.address)
        }
        if (replyText.isNotEmpty()) outState.putString(STATE_REPLY_TEXT, replyText)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(STATE_CAMERA_FILE)?.let { name ->
            val files = MmsFiles(this)
            files.transferFile(name)?.let { cameraTarget = files.uriFor(it) }
        }
        MmsFiles(this).cleanOldTransfers(System.currentTimeMillis() - STALE_TRANSFER_MS)
        if (savedInstanceState == null) handleIntent(intent) else restore(savedInstanceState)
        setContent {
            val contacts by contactsVm.contacts.collectAsStateWithLifecycle()
            val conversations by messagesVm.items.collectAsStateWithLifecycle()
            val thread by messagesVm.thread.collectAsStateWithLifecycle()
            val unread by messagesVm.unread.collectAsStateWithLifecycle()
            val open: MessageItem? = conversations.firstOrNull { it.threadKey == openThreadKey }
                ?: composeItem?.takeIf { it.threadKey == openThreadKey }
            val loaded by messagesVm.loaded.collectAsStateWithLifecycle()
            // "J'appelle Marie" is also said aloud, once, when the call screen opens.
            LaunchedEffect(calling) {
                // Quiet if the voice is missing: this one is not asked for, and the screen already says it.
                calling?.let { if (profile.feedbackEnabled()) speaker().speak(getString(R.string.calling, it.name)) {} }
            }
            val c = calling
            val image = viewingImage
            CompositionLocalProvider(LocalThemeConfig provides uiConfig.theme) {
                if (image != null) {
                    BackHandler { viewingImage = null }
                    ImageViewerScreen(image, onBack = { viewingImage = null })
                } else if (c != null) {
                    BackHandler { cancelCall() }
                    CallingScreen(
                        contact = c,
                        onCancel = { cancelCall() },
                        onLaunch = {
                            calling = null
                            screen = Screen.HOME
                            LauncherActions.call(this, c.number, direct = granted(Manifest.permission.CALL_PHONE))
                        },
                    )
                } else if (!loaded && screen in NEEDS_CONVERSATIONS) {
                    Box(Modifier.fillMaxSize()) // the conversations are being read: an empty list is not "the conversation is gone"
                } else when (screen) {
                    Screen.HOME -> HomeScreen(
                        seniorName = seniorName,
                        unreadMessages = unread,
                        config = uiConfig,
                        status = rememberHomeStatus(),
                        onSecretTap = { startActivity(Intent(this, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_FROM_HOME, true)) },
                        onTile = ::onTile,
                        showSetupHint = needsSetup,
                        onSetup = { startActivity(Intent(this, SetupActivity::class.java)) }, // no "from home" mark: the family chooses the code
                    )
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
                        val everybody by contactsVm.allContacts.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) { contactsVm.loadAll() } // the "Général" tab and the search need every contact
                        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { contactsVm.clearAll() } } // and they are forgotten when the screen is left
                        CallScreen(favorites = contacts, all = everybody, onBack = { screen = Screen.HOME }, onCall = { calling = it })
                    }
                    Screen.WRITE_NOT_READY -> {
                        BackHandler { screen = Screen.MESSAGES }
                        MessageScreen(R.string.write_not_ready, onBack = { screen = Screen.MESSAGES })
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
                                onSendPhoto = if (open.message.channel == Channel.SMS && open.canReply && SmsRole.isHeld(this)) ({ notice = null; screen = Screen.PHOTO_CHOICE }) else null,
                                onOpenWhatsApp = if (open.message.channel == Channel.WHATSAPP && !open.canReply) ({ openWhatsApp() }) else null,
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
                                directSend = SmsAccess.canSend(this) || open.message.channel == Channel.WHATSAPP,
                                whatsapp = open.message.channel == Channel.WHATSAPP,
                                onOpenWhatsApp = if (open.message.channel == Channel.WHATSAPP && notice == R.string.whatsapp_reply_failed) ({ openWhatsApp() }) else null,
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

    /** Shows [message] as the notice and tells the person with a vibration and a tone, once per failure (even if the same one repeats). */
    private fun failure(message: Int) {
        notice = message
        feedback.failed()
    }

    /** Leaving the call screen also silences "J'appelle X": nothing is said after "Annuler". */
    private fun cancelCall() {
        calling = null
        speakerInstance?.stop()
    }

    /** Back to where the person was after the process was killed. Nothing here starts a call, a photo or a message. */
    private fun restore(state: Bundle) {
        val saved = state.getString(STATE_SCREEN)?.let { name -> Screen.entries.firstOrNull { it.name == name } } ?: return
        val key = state.getString(STATE_THREAD)
        val number = state.getString(STATE_COMPOSE_NUMBER)
        if (number != null && NUMBER_FORMAT.matches(number)) {
            val contact = try { ContactsRepository(contentResolver).byNumber(number) } catch (e: SecurityException) { null }
            val first = StoredMessage("new", contact?.name ?: number, Channel.SMS, "", System.currentTimeMillis(), address = number)
            composeItem = MessageItem(first, contact)
        }
        replyText = state.getString(STATE_REPLY_TEXT).orEmpty()
        if (key != null) {
            openThreadKey = key
            messagesVm.select(key)
        }
        // A WhatsApp answer was confirmed against one particular notification, which did not survive: it is dictated again, never sent from here.
        val whatsapp = key?.startsWith("${Channel.WHATSAPP.wire}:") == true
        screen = when {
            saved in NEEDS_CONVERSATIONS && key == null -> Screen.MESSAGES
            saved == Screen.REPLY && (replyText.isBlank() || whatsapp) -> Screen.MESSAGE
            else -> saved
        }
    }

    override fun onResume() {
        super.onResume()
        seniorName = profile.name() // may have been changed in "Réglages famille"
        // Direct mode: new SMS of the phone's own messaging app are copied in (the default SMS app receives them itself).
        if (!SmsRole.isHeld(this) && SmsAccess.canRead(this)) lifecycleScope.launch(Dispatchers.IO) { runCatching { SmsComponents.syncNew(this@MainActivity) } }
        needsSetup = profile.setupHintVisible(System.currentTimeMillis(), FamilyLock(this).isSet())
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
        feedback.release()
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

    // The offer comes first only while neither the role nor the direct permissions are there (debug builds: they carry the SMS permissions).
    private fun shouldOfferSmsRole() =
        BuildConfig.SMS_ROLE_OFFERED && !SmsAccess.direct(this) && !profile.smsRoleDeclined() && !smsRoleLater

    private fun openMessages() {
        screen = if (shouldOfferSmsRole()) Screen.SMS_ROLE else Screen.MESSAGES
    }

    private fun requestSmsRole() {
        val intent = SmsRole.requestIntent(this)
        if (intent == null) {
            requestSmsPermissions() // this phone offers no SMS role: the permissions alone still give the direct mode
            return
        }
        try {
            smsRole.launch(intent)
        } catch (e: ActivityNotFoundException) {
            requestSmsPermissions()
        }
    }

    /** Asks for the two SMS permissions; the answer arrives in [onSmsPermissionsAnswered]. Nothing to ask when they are already given. */
    private fun requestSmsPermissions() {
        if (SmsAccess.direct(this)) {
            onSmsPermissionsAnswered()
            return
        }
        smsPermissions.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS))
    }

    private fun onSmsPermissionsAnswered() {
        val write = pendingWrite
        pendingWrite = false
        if (SmsAccess.canRead(this)) {
            importing = !profile.smsImported() // the first copy of the old messages can take a moment
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { runCatching { SmsComponents.syncNew(this@MainActivity) } }
                profile.markSmsImported()
                importing = false
            }
        }
        screen = if (write && SmsAccess.direct(this)) Screen.PICK_RECIPIENT else Screen.MESSAGES
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
            // Android did not make us the SMS app (refused, or no dialog on this phone): the SMS permissions give the direct mode.
            requestSmsPermissions()
            return
        }
        screen = if (pendingWrite && SmsAccess.direct(this)) Screen.PICK_RECIPIENT else Screen.MESSAGES
        pendingWrite = false
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
        replyText = "" // a draft does not outlive its conversation (nor the state Android keeps for us)
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
        when {
            SmsAccess.direct(this) -> screen = Screen.PICK_RECIPIENT
            // Neither the SMS role nor the SMS permissions yet: ask here (it may have been refused once) instead of sending the person to another app.
            BuildConfig.SMS_ROLE_OFFERED -> {
                profile.setSmsRoleDeclined(false)
                smsRoleLater = false
                pendingWrite = true
                screen = Screen.SMS_ROLE
            }
            // Never another app: say plainly that this is still being set up by the family.
            else -> screen = Screen.WRITE_NOT_READY
        }
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

    /** The answer goes through WhatsApp's own "Répondre" notification action: the message stays a WhatsApp message. */
    private fun sendWhatsAppReply(item: MessageItem, text: String) {
        if (sendingReply) return // a second touch must not send it twice
        sendingReply = true
        try {
            if (!ReplyRegistry.reply(this, item.threadKey, text, expectedKey = replyTargetKey)) {
                // Either the conversation changed since it was confirmed, or WhatsApp's notification is gone/refused: say which.
                notice = if (ReplyRegistry.currentKey(item.threadKey) != replyTargetKey && ReplyRegistry.canReply(item.threadKey)) R.string.whatsapp_changed
                else R.string.whatsapp_reply_failed
                return
            }
        } finally {
            sendingReply = false
        }
        notice = R.string.whatsapp_sent
        feedback.sent()
        screen = Screen.MESSAGE
        lifecycleScope.launch(Dispatchers.IO) {
            MessageStore.get(this@MainActivity).add(
                StoredMessage(
                    "wa-out-${java.util.UUID.randomUUID()}", item.message.from, Channel.WHATSAPP, text.trim(), System.currentTimeMillis(),
                    read = true, outgoing = true, status = MessageStatus.HANDED_OFF, // given to WhatsApp, which does not report back
                ),
            )
        }
    }

    private fun openWhatsApp() {
        val intent = listOf("com.whatsapp", "com.whatsapp.w4b").firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (intent == null) {
            notice = R.string.whatsapp_missing
            return
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            notice = R.string.whatsapp_missing
        }
    }

    private fun sendReply(item: MessageItem, text: String) {
        if (item.message.channel == Channel.WHATSAPP) {
            sendWhatsAppReply(item, text)
            return
        }
        val number = item.replyNumber
        if (number == null) {
            failure(R.string.number_invalid) // stay on the confirmation, with the text still there
            return
        }
        if (!SmsAccess.canSend(this)) {
            LauncherActions.smsDraft(this, number, text)
            closeConversation()
            return
        }
        // Leave the confirmation at once so a second touch cannot send twice; the message shows up with "Envoi en cours…".
        notice = null
        screen = Screen.MESSAGE
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { SmsComponents.sender(this@MainActivity).send(number, text, item.message.from) }
            if (result is SendResult.Started) feedback.sent()
            if (result !is SendResult.Started) {
                // Nothing was stored: go back to the confirmation with the text, instead of losing what was dictated.
                replyText = text
                failure(R.string.send_failed)
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
            failure(R.string.photo_send_failed)
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
                feedback.sent()
                screen = Screen.MESSAGE // the picture appears in the conversation, "Envoi en cours…"
            } else {
                failure(R.string.photo_send_failed) // stay on the confirmation: send again, or choose another picture
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
        const val STATE_SCREEN = "screen"
        const val STATE_THREAD = "thread_key"
        const val STATE_COMPOSE_NUMBER = "compose_number"
        private val NUMBER_FORMAT = Regex("[0-9+ ()./-]{1,30}")
        const val STATE_REPLY_TEXT = "reply_text"
        private val NEEDS_CONVERSATIONS = setOf(Screen.MESSAGE, Screen.REPLY, Screen.PHOTO_CHOICE, Screen.PHOTO_CONFIRM)
        const val STALE_TRANSFER_MS = 20 * 60 * 1000L
        const val STALE_DOWNLOAD_MS = 10 * 60 * 1000L
        val SMS_SCHEMES = setOf("sms", "smsto") // mms:/mmsto: once MMS are handled
    }
}
