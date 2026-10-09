package app.presence.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.text.font.FontWeight
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
import app.presence.launcher.security.FamilyLock
import app.presence.launcher.sms.SmsRole
import app.presence.launcher.ui.EnterFamilyCodeScreen
import app.presence.launcher.ui.FamilyCodeNotSetScreen
import app.presence.launcher.ui.PairingOfferCard
import app.presence.launcher.ui.SetFamilyCodeScreen
import app.presence.launcher.photos.ContactPhotoStore
import app.presence.launcher.ui.Avatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.presence.launcher.notifications.PairingConfig
import app.presence.launcher.notifications.PairingClient
import app.presence.launcher.notifications.PairingLink
import app.presence.launcher.notifications.PairingOffer
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

    private val lock by lazy { FamilyLock(this) }

    /** Whether Présence is the phone's SMS app; refreshed when this screen comes back from the system dialog. */
    private var smsHeld by mutableStateOf(false)

    /** The last answer of the system dialog: true = accepted, false = refused, null = not asked yet. */
    private var smsRoleAnswer by mutableStateOf<Boolean?>(null)

    /** Installed by hand (not from a store) on a phone that restricts such apps (Android 13+): some switches stay greyed out until allowed. */
    private val sideloaded: Boolean by lazy {
        Build.VERSION.SDK_INT >= 33 && try {
            packageManager.getInstallSourceInfo(packageName).installingPackageName == null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private val smsRoleResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        smsHeld = SmsRole.isHeld(this)
        smsRoleAnswer = smsHeld
        if (smsHeld) ProfileStore(this).setSmsRoleDeclined(false)
    }

    private fun requestSmsRole() {
        val intent = SmsRole.requestIntent(this) ?: return
        openingChild = true // the system dialog is not "leaving" the settings
        try {
            smsRoleResult.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            smsRoleAnswer = false
        }
    }

    private fun openAppInfo() {
        openingChild = true
        smsRoleAnswer = null // the family is about to change what Android allows: the old answer no longer counts
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)))
    }
    private var unlocked by mutableStateOf(false)

    /** Set just before this screen deliberately opens another one (photo or file picker, Android's notification settings). */
    private var openingChild = false

    /** Opened by the hidden gesture of the home screen (not from the app list, nor by a QR code): see [FamilyCodeNotSetScreen]. */
    private var fromHome = false

    /** Leaving the settings locks them again; opening a picker or Android settings from here does not. */
    override fun onStop() {
        super.onStop()
        // An unused offer does not wait behind a locked screen; a request already sent is left to finish (and save its token): only "Annuler" stops it.
        if (openingChild) openingChild = false else { unlocked = false; if (attempt == null) forgetOffer() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        fromHome = savedInstanceState?.getBoolean(STATE_FROM_HOME) ?: intent.getBooleanExtra(EXTRA_FROM_HOME, false)
        // The settings show the pairing address and the names of the family: keep them out of screenshots and the recent-apps view.
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        // Only a fresh launch reads the link: a recreated activity (rotation, process death) must not offer it a second time.
        onCreateContent(readLink = savedInstanceState == null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_FROM_HOME, fromHome)
    }

    override fun onResume() {
        super.onResume()
        smsHeld = SmsRole.isHeld(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            contactsVm.refresh()
        }
    }

    /**
     * A pairing offer opened from a QR code. It is only *shown* (after the family code) for the family to verify; nothing is saved
     * until "Appairer" is touched. It is read once, at the first launch, and kept only in memory: it is forgotten when it is used,
     * when the settings lock again, and a recreated activity does not read the link again. The system may still keep the original
     * link with the task, but its secret is valid for two minutes and once, so it is worthless afterwards.
     */
    private var offer by mutableStateOf<PairingOffer?>(null)
    private var offerRefused by mutableStateOf(false)
    private var pairingStatus by mutableStateOf<Int?>(null)
    private var pairing by mutableStateOf(false)

    /** One request to the hub. Whoever settles it first wins: either the answer is saved, or the family cancelled and nothing is. */
    private class Attempt {
        val settled = java.util.concurrent.atomic.AtomicBoolean(false)
        var job: Job? = null
    }
    private var attempt: Attempt? = null

    private fun forgetOffer() {
        attempt?.let { if (it.settled.compareAndSet(false, true)) it.job?.cancel() } // too late to cancel if it was already saved
        attempt = null
        offer = null
        offerRefused = false
        pairingStatus = null
        pairing = false
    }

    private fun onCreateContent(readLink: Boolean) {
        if (readLink && intent?.data != null) {
            val data = intent.data.toString()
            setIntent(Intent()) // the link (and the claim in it) is not kept in this activity
            offer = PairingLink.parse(data)
            offerRefused = offer == null
        }
        val store = PairingStore(this)
        val profile = ProfileStore(this)
        setContent {
            PresenceTheme {
                if (!unlocked) {
                    var gateError by remember { mutableStateOf<String?>(null) }
                    var lockedSeconds by remember { mutableStateOf(lock.lockedSeconds()) }
                    if (!lock.isSet() && fromHome) {
                        FamilyCodeNotSetScreen(onBack = { finish() }) // never lets the home screen choose the family's code
                    } else if (!lock.isSet()) {
                        SetFamilyCodeScreen(gateError, onEdit = { gateError = null }, onBack = { finish() }, onSubmit = { code, again ->
                            // The two fields are compared first: a typing slip is the likeliest mistake.
                            gateError = when {
                                code != again -> getString(R.string.family_code_mismatch)
                                lock.problemWith(code) == FamilyLock.Problem.LENGTH -> getString(R.string.family_code_length)
                                lock.problemWith(code) == FamilyLock.Problem.OBVIOUS -> getString(R.string.family_code_obvious)
                                else -> { lock.set(code); unlocked = true; null }
                            }
                        })
                    } else {
                        EnterFamilyCodeScreen(gateError, lockedSeconds, onEdit = { gateError = null }, onBack = { finish() }, onSubmit = { code ->
                            gateError = when (val r = lock.verify(code)) {
                                FamilyLock.Result.Ok -> { unlocked = true; null }
                                is FamilyLock.Result.Wrong -> resources.getQuantityString(R.plurals.family_code_wrong, r.triesLeft, r.triesLeft)
                                is FamilyLock.Result.Locked -> { lockedSeconds = r.seconds; null }
                            }
                        })
                    }
                    return@PresenceTheme
                }
                var url by remember { mutableStateOf(store.load()?.baseUrl ?: "https://") }
                var token by remember { mutableStateOf("") } // a stored token is never shown again
                var pin by remember { mutableStateOf(store.load()?.certSha256.orEmpty()) }
                var name by remember { mutableStateOf(profile.name()) }
                var saved by remember { mutableStateOf<Boolean?>(null) }
                var access by remember { mutableStateOf(hasNotificationAccess()) }
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.setup_title), fontSize = 28.sp)
                    offer?.let { o ->
                        PairingOfferCard(
                            offer = o,
                            replacing = store.load(),
                            busy = pairing,
                            status = pairingStatus?.let { stringResource(it) },
                            onPair = {
                                pairing = true
                                pairingStatus = null
                                val mine = Attempt()
                                attempt = mine
                                mine.job = lifecycleScope.launch {
                                    val outcome = withContext(Dispatchers.IO) {
                                        val r = PairingClient().redeem(o)
                                        // Saved in the same step as the answer, even if the screen has gone meanwhile: the hub has
                                        // already burnt the QR code, so a token that was received must not be lost.
                                        if (r is PairingClient.Outcome.Paired && mine.settled.compareAndSet(false, true)) store.save(r.config)
                                        r
                                    }
                                    pairing = false
                                    attempt = null
                                    when (outcome) {
                                        is PairingClient.Outcome.Paired -> {
                                            url = outcome.config.baseUrl
                                            pin = outcome.config.certSha256
                                            offer = null
                                            pairingStatus = R.string.setup_paired
                                        }
                                        PairingClient.Outcome.Refused -> pairingStatus = R.string.setup_pair_refused
                                        PairingClient.Outcome.TooManyAttempts -> pairingStatus = R.string.setup_pair_too_many
                                        PairingClient.Outcome.Unreachable -> pairingStatus = R.string.setup_pair_unreachable
                                        PairingClient.Outcome.Invalid -> pairingStatus = R.string.setup_pair_invalid
                                    }
                                }
                            },
                            onCancel = {
                                val inFlight = pairing
                                forgetOffer()
                                if (inFlight) pairingStatus = R.string.setup_pair_cancelled
                            },
                        )
                    }
                    if (offer == null) pairingStatus?.let { Text(stringResource(it), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    if (offerRefused) Text(stringResource(R.string.setup_link_refused), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.setup_name)) }, modifier = Modifier.fillMaxWidth())
                    if (offer == null) Text(stringResource(R.string.setup_scan_hint), fontSize = 24.sp)
                    OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.setup_hub_url)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        token, { token = it }, label = { Text(stringResource(R.string.setup_token)) },
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(pin, { pin = it }, label = { Text(stringResource(R.string.setup_fingerprint)) }, modifier = Modifier.fillMaxWidth())
                    when (store.protectionState()) {
                        PairingStore.Protection.ENCRYPTED -> Text(stringResource(R.string.setup_token_encrypted))
                        PairingStore.Protection.PLAIN -> Text(stringResource(R.string.setup_token_plain), fontWeight = FontWeight.Bold)
                        PairingStore.Protection.UNREADABLE -> Text(stringResource(R.string.setup_token_lost), fontWeight = FontWeight.Bold)
                        PairingStore.Protection.NONE -> Unit
                    }
                    Button(onClick = {
                        profile.saveName(name)
                        val config = PairingConfig.parse(url, token, pin)
                        if (config != null) store.save(config)
                        saved = config != null
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text(stringResource(R.string.setup_save), fontSize = 24.sp) }
                    when (saved) {
                        true -> Text(stringResource(R.string.setup_saved))
                        false -> Text(stringResource(R.string.setup_invalid))
                        null -> Unit
                    }
                    Text(stringResource(if (access) R.string.setup_access_on else R.string.setup_access_off))
                    OutlinedButton(onClick = {
                        openingChild = true
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        access = hasNotificationAccess()
                    }) { Text(stringResource(R.string.setup_open_access)) }
                    OutlinedButton(onClick = { access = hasNotificationAccess() }) { Text(stringResource(R.string.setup_refresh)) }

                    Text(stringResource(R.string.setup_appearance_title), fontSize = 28.sp)
                    OutlinedButton(onClick = { openingChild = true; configPicker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.setup_appearance_import)) }
                    OutlinedButton(onClick = {
                        uiStore.reset()
                        appearanceMessage = getString(R.string.setup_appearance_reset)
                    }) { Text(stringResource(R.string.setup_appearance_reset_button)) }
                    appearanceMessage?.let { Text(it) }

                    var feedbackOn by remember { mutableStateOf(profile.feedbackEnabled()) }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(value = feedbackOn, role = Role.Switch, onValueChange = {
                            feedbackOn = it
                            profile.setFeedbackEnabled(it)
                            if (it) app.presence.launcher.feedback.Feedback(this@SetupActivity) { true }.sent() // a try, so the family hears what was switched on
                        }),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(checked = feedbackOn, onCheckedChange = null)
                        Text(stringResource(R.string.setup_feedback), fontSize = 24.sp, modifier = Modifier.weight(1f))
                        Text(stringResource(if (feedbackOn) R.string.setup_yes else R.string.setup_no), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }

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
                    // Messages: what is missing, and a button for each fix (the family reads this, Jean-Pierre does not).
                    Text(stringResource(R.string.setup_messages_title), fontSize = 28.sp, modifier = Modifier.semantics { heading() })
                    Text(stringResource(if (smsHeld) R.string.setup_sms_held else R.string.setup_sms_not_held), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(if (access) R.string.setup_access_on else R.string.setup_access_off), fontSize = 24.sp)
                    if (!smsHeld) {
                        if (BuildConfig.SMS_ROLE_OFFERED && SmsRole.requestIntent(this@SetupActivity) != null) {
                            Button(onClick = { requestSmsRole() }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                                Text(stringResource(R.string.setup_sms_offer), fontSize = 24.sp)
                            }
                            if (smsRoleAnswer == false) Text(stringResource(R.string.setup_sms_refused), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text(stringResource(R.string.setup_sms_unavailable), fontSize = 24.sp)
                        }
                    }
                    if (smsRoleAnswer == true) Text(stringResource(R.string.setup_sms_done), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    if (sideloaded && (smsRoleAnswer == false || !access)) {
                        // Android 13+ blocks some access for an app that was not installed from a store until it is allowed by hand.
                        Text(stringResource(R.string.setup_restricted_hint), fontSize = 24.sp)
                        Button(onClick = { openAppInfo() }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                            Text(stringResource(R.string.setup_open_app_info), fontSize = 24.sp)
                        }
                    }
                    if (erased) Text(stringResource(R.string.setup_erased))

                    val contacts by contactsVm.contacts.collectAsStateWithLifecycle()
                    val all by contactsVm.allContacts.collectAsStateWithLifecycle()
                    val chosen by contactsVm.chosen.collectAsStateWithLifecycle()
                    var query by remember { mutableStateOf("") }
                    var granted by remember {
                        mutableStateOf(ContextCompat.checkSelfPermission(this@SetupActivity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
                    }
                    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                        granted = ok
                        if (ok) { contactsVm.refresh(); contactsVm.loadAll() }
                    }
                    androidx.compose.runtime.LaunchedEffect(granted) { if (granted) contactsVm.loadAll() }

                    Text(stringResource(R.string.setup_favorites), fontSize = 28.sp)
                    Text(stringResource(R.string.setup_favorites_help), fontSize = 24.sp)
                    if (!granted) {
                        Button(onClick = { askContacts.launch(Manifest.permission.READ_CONTACTS) }, modifier = Modifier.heightIn(min = 64.dp)) {
                            Text(stringResource(R.string.setup_contacts_allow), fontSize = 24.sp)
                        }
                    } else {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(stringResource(R.string.setup_favorites_search), fontSize = 24.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        )
                        ContactPickerRows.rows(all, chosen, query).forEach { c ->
                            val on = c.id in chosen
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 64.dp)
                                    .toggleable(value = on, role = Role.Switch, onValueChange = { contactsVm.toggle(c.id) }),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Switch(checked = on, onCheckedChange = null)
                                Text(c.name, fontSize = 24.sp, modifier = Modifier.weight(1f))
                                Text(stringResource(if (on) R.string.setup_favorites_on else R.string.setup_favorites_off), fontSize = 24.sp)
                            }
                        }
                        if (all.size > ContactPickerRows.LIMIT && query.isBlank()) Text(stringResource(R.string.setup_favorites_more), fontSize = 24.sp)
                    }

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
                                    openingChild = true
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

    companion object {
        /** Set by the home screen's hidden gesture. */
        const val EXTRA_FROM_HOME = "from_home"
        private const val STATE_FROM_HOME = "from_home"
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
