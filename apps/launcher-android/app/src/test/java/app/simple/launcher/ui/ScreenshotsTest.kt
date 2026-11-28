package app.simple.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Sms
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.simple.launcher.Contact
import app.simple.launcher.R
import app.simple.launcher.config.ParseResult
import app.simple.launcher.config.UiConfigParser
import app.simple.launcher.messages.MessageItem
import app.simple.launcher.messages.MessageStatus
import app.simple.launcher.messages.StoredMessage
import app.simple.launcher.notifications.Channel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every senior-facing screen on the JVM, on a regular phone, a small phone and with the system font at 200 %,
 * so a change can be looked at without an emulator.
 * Images: `gradle :app:recordRoborazziDebug` → `app/build/outputs/roborazzi/<screen>_<variant>.png` (see docs/testing.md).
 * Without the record task the screens are still composed, so a crash in any of them fails the build.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE)
class ScreenshotsTest {
    private val now = 1_800_000_000_000L
    private val marie = Contact(1, "Marie", "0600000001", null)
    private val jules = Contact(2, "Jules", "0600000002", null)
    private val longName = Contact(3, "Marie-Christine de La Rochefoucauld", "0600000003", null)

    private fun message(id: String, text: String, ageMin: Long, outgoing: Boolean = false, status: MessageStatus? = null) =
        StoredMessage(id, "Marie", Channel.SMS, text, now - ageMin * 60_000, read = true, outgoing = outgoing, address = "0600000001")
            .let { if (status != null) it.copy(status = status) else it }

    private val items = listOf(
        MessageItem(StoredMessage("1", "Marie", Channel.SMS, "Bonjour papa, je passe demain après-midi avec les enfants.", now - 20 * 60_000, false, address = "0600000001"), marie, unread = 1),
        MessageItem(StoredMessage("2", "Numéro inconnu", Channel.SMS, "Votre colis arrive.", now - 180 * 60_000, true, address = null), null, unread = 0),
    )

    /** Captures [content] once per variant. */
    private fun shoot(name: String, content: @Composable () -> Unit) {
        for ((variant, qualifiers, fontScale) in VARIANTS) {
            RuntimeEnvironment.setQualifiers(qualifiers)
            RuntimeEnvironment.setFontScale(fontScale)
            try {
                captureRoboImage("build/outputs/roborazzi/${name}_$variant.png") { content() }
            } finally {
                RuntimeEnvironment.setFontScale(1f)
            }
        }
    }

    @Test fun home() = shoot("01_home") { HomeScreen(seniorName = "Jean-Pierre") }

    @Test fun homeWithUnreadMessages() = shoot("02_home_badge") { HomeScreen(seniorName = "Jean-Pierre", unreadMessages = 2) }

    @Test fun homeCustomConfig() {
        val json = """{"schemaVersion":1,"home":{"columns":1,"tiles":[
            {"id":"a","action":"call","label":"Téléphoner","icon":"person"},{"id":"b","action":"camera"}]}}"""
        val config = (UiConfigParser.parse(json) as ParseResult.Ok).config
        shoot("03_home_custom_config") { HomeScreen(seniorName = "Jean-Pierre", config = config) }
    }

    @Test fun callList() = shoot("10_call_list") { CallScreen(favorites = listOf(marie, jules, longName), all = listOf(marie, jules, longName), onBack = {}, onCall = {}) }

    @Test fun callEmpty() = shoot("11_call_empty") { CallScreen(favorites = emptyList(), all = emptyList(), onBack = {}, onCall = {}) }

    @Test fun calling() = shoot("12_calling") { CallingScreen(marie, onCancel = {}, onLaunch = {}) }

    @Test fun messagesList() = shoot("20_messages_list") { MessagesScreen(items, now, onOpen = {}, onWrite = {}, onBack = {}) }

    @Test fun messagesEmpty() = shoot("21_messages_empty") { MessagesScreen(emptyList(), now, onOpen = {}, onWrite = {}, onBack = {}) }

    @Test fun conversation() {
        val thread = listOf(
            message("a", "Bonjour papa", 30),
            message("b", "Bonjour ma chérie", 25, outgoing = true, status = MessageStatus.DELIVERED),
            message("c", "À demain", 5, outgoing = true, status = MessageStatus.FAILED),
        )
        shoot("22_conversation") { ConversationScreen(items[0], thread, now, canDictate = true, notice = null, onListen = {}, onReply = {}, onBack = {}) }
    }

    @Test fun conversationWithNotice() = shoot("23_conversation_notice") {
        ConversationScreen(items[0], listOf(items[0].message), now, canDictate = true, notice = "Je n'ai rien entendu. Touchez « Répondre » pour réessayer.", onListen = {}, onReply = {}, onBack = {})
    }

    @Test fun replyConfirm() = shoot("24_reply_confirm") {
        ReplyConfirmScreen(items[0], "Oui, venez à 15 heures, je vous attends avec le goûter.", onListen = {}, onSend = {}, onRedo = {}, onCancel = {})
    }

    @Test fun pickRecipient() = shoot("25_pick_recipient") { PickContactScreen(R.string.write_to_title, listOf(marie, jules), listOf(marie, jules), onPick = {}, onBack = {}) }

    @Test fun photoChoice() = shoot("30_photo_choice") { PhotoChoiceScreen(marie, known = true, canTakePhoto = true, notice = null, onTake = {}, onPick = {}, onBack = {}) }

    @Test fun permissionRationale() = shoot("40_rationale") { PermissionRationaleScreen(onContinue = {}, onLater = {}) }

    @Test fun permissionDenied() = shoot("41_denied") { MessageScreen(R.string.permission_denied, onBack = {}) }

    @Test fun smsRole() = shoot("42_sms_role") { SmsRoleScreen(onContinue = {}, onLater = {}) }

    @Test fun firstSteps() = shoot("50_first_steps") {
        FirstStepsScreen(homeDone = false, contactsDone = true, messagesDone = false, codeDone = false, onCode = {}, onHome = {}, onContacts = {}, onMessages = {}, onAppSettings = {}, onFinish = {})
    }

    @Test fun settingsMenu() = shoot("51_settings_menu") {
        SimpleTheme {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingsTopBar("Réglages famille", onBack = null)
                SettingsCard { SetupChecklist(homeDone = true, contactsDone = true, messagesDone = false, onHome = {}, onContacts = {}, onMessages = {}, onAppSettings = {}) }
                SettingsMenuItem(Icons.Filled.Sms, "Messages", "À activer") {}
                SettingsMenuItem(Icons.Filled.People, "Proches et photos", "Qui apparaît pour appeler, et leurs photos") {}
            }
        }
    }

    private companion object {
        val VARIANTS = listOf(
            Triple("phone", PHONE, 1f),
            Triple("small", "fr-rFR-w320dp-h568dp-xhdpi", 1f),
            Triple("font200", PHONE, 2f),
            // Google Play phone screenshots: 1080 × 1920 px (9:16, within Play's 2:1 limit), copied to fastlane/metadata/android/*/images/phoneScreenshots.
            Triple("store", "fr-rFR-w360dp-h640dp-xxhdpi", 1f),
        )
    }
}

private const val PHONE = "fr-rFR-w411dp-h891dp-xxhdpi"
