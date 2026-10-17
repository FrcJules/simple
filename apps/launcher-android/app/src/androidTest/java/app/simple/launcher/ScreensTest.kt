package app.simple.launcher

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.ExperimentalTestApi
import app.simple.launcher.config.UiConfigParser
import app.simple.launcher.config.ParseResult
import app.simple.launcher.ui.CallScreen
import app.simple.launcher.ui.CallingScreen
import app.simple.launcher.ui.FirstStepsScreen
import app.simple.launcher.ui.SettingsCard
import app.simple.launcher.ui.SettingsMenuItem
import app.simple.launcher.ui.SettingsTopBar
import app.simple.launcher.ui.SetupChecklist
import app.simple.launcher.ui.SimpleTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Sms
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.simple.launcher.ui.HomeScreen
import app.simple.launcher.ui.MessageScreen
import app.simple.launcher.ui.PermissionRationaleScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Renders each screen with fake data, checks the essentials and saves a screenshot. */
@OptIn(ExperimentalTestApi::class)
class ScreensTest {
    @get:Rule val rule = createComposeRule()

    private val marie = Contact(1, "Marie", "0600000001", null)
    private val jules = Contact(2, "Jules", "0600000002", null)

    private fun shot(name: String) = Screenshots.save(name, rule.onRoot().captureToImage().asAndroidBitmap())

    @Test
    fun home() {
        rule.setContent { HomeScreen(seniorName = "Jean-Pierre") }
        rule.onNodeWithText("Bonjour Jean-Pierre").assertIsDisplayed()
        listOf("Appeler", "Messages", "Photo", "Mes photos").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        shot("01_home")
    }

    @Test
    fun firstStepsShowWhatIsDoneAndWhatIsLeft() {
        var homeAsked = false
        rule.setContent {
            FirstStepsScreen(homeDone = false, contactsDone = true, messagesDone = false, onHome = { homeAsked = true }, onContacts = {}, onMessages = {}, onAppSettings = {}, onFinish = {})
        }
        rule.onNodeWithText("Bienvenue dans Simple").assertIsDisplayed()
        rule.onNodeWithText("Choisir Simple comme écran d'accueil").assertIsDisplayed()
        rule.onNodeWithText("Activer la lecture des notifications").assertIsDisplayed()
        rule.onNodeWithText("Fait").assertIsDisplayed() // contacts: written, not only an icon
        shot("12_first_steps")
        rule.onNodeWithText("Choisir Simple comme écran d'accueil").performClick()
        assertTrue(homeAsked)
    }

    @Test
    fun settingsMenuLooksLikeAMenu() {
        rule.setContent {
            SimpleTheme {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SettingsTopBar("Réglages famille", onBack = null)
                    SettingsCard { SetupChecklist(homeDone = true, contactsDone = true, messagesDone = false, onHome = {}, onContacts = {}, onMessages = {}, onAppSettings = {}) }
                    SettingsMenuItem(Icons.Filled.Sms, "Messages", "À activer") {}
                    SettingsMenuItem(Icons.Filled.People, "Proches et photos", "Qui apparaît pour appeler, et leurs photos") {}
                }
            }
        }
        rule.onNodeWithText("Réglages famille").assertIsDisplayed()
        rule.onNodeWithText("Proches et photos").assertIsDisplayed()
        shot("13_settings_menu")
    }

    @Test
    fun homeTileTapIsReported() {
        var tapped: String? = null
        rule.setContent { HomeScreen(seniorName = "", onTile = { tapped = it.action.name }) }
        rule.onNodeWithText("Bonjour").assertIsDisplayed()
        rule.onNodeWithText("Appeler").performClick()
        assertEquals("CALL", tapped)
    }

    @Test
    fun callScreenShowsContactsAndAsksConfirmation() {
        var called: Contact? = null
        rule.setContent { CallScreen(favorites = listOf(marie, jules), all = listOf(marie, jules), onBack = {}, onCall = { called = it }) }
        rule.onNodeWithText("Marie").assertIsDisplayed()
        rule.onNodeWithText("Jules").assertIsDisplayed()
        shot("02_call_list")
        rule.onNodeWithText("Marie").performClick()
        rule.onNodeWithText("Appeler Marie ?").assertIsDisplayed()
        assertEquals(null, called) // nothing is called before the explicit confirmation
        rule.onNodeWithText("Oui, appeler").performClick()
        assertEquals(marie, called)
    }

    @Test
    fun cancelInConfirmationDoesNotCall() {
        var called = false
        rule.setContent { CallScreen(favorites = listOf(marie), all = listOf(marie), onBack = {}, onCall = { called = true }) }
        rule.onNodeWithText("Marie").performClick()
        rule.onNodeWithText("Non, annuler").performClick()
        assertFalse(called)
    }

    @Test
    fun callScreenWithoutContactsExplainsWhatToDo() {
        rule.setContent { CallScreen(favorites = emptyList(), all = emptyList(), onBack = {}, onCall = {}) }
        rule.onNodeWithText("Demandez à un proche d'ajouter vos contacts.").assertIsDisplayed()
        shot("03_call_empty")
    }

    @Test
    fun callingScreenCanBeCancelledBeforeTheCallStarts() {
        var cancelled = false
        var launched = false
        rule.mainClock.autoAdvance = false
        rule.setContent { CallingScreen(marie, onCancel = { cancelled = true }, onLaunch = { launched = true }) }
        rule.onNodeWithText("J'appelle Marie…").assertIsDisplayed()
        shot("04_calling")
        rule.onNodeWithText("Non, annuler").performClick()
        assertTrue(cancelled)
        assertFalse(launched)
    }

    @Test
    fun callingScreenLaunchesTheCallAfterTheDelay() {
        var launched = false
        rule.mainClock.autoAdvance = false
        rule.setContent { CallingScreen(marie, onCancel = {}, onLaunch = { launched = true }) }
        assertFalse(launched)
        rule.mainClock.advanceTimeBy(2_000)
        assertTrue(launched)
    }

    @Test
    fun permissionRationaleAndDeniedScreens() {
        rule.setContent { PermissionRationaleScreen(onContinue = {}, onLater = {}) }
        rule.onNodeWithText("Continuer").assertIsDisplayed()
        rule.onNodeWithText("Plus tard").assertIsDisplayed()
        shot("05_rationale")
    }

    @Test
    fun deniedMessageScreen() {
        rule.setContent { MessageScreen(R.string.permission_denied, onBack = {}) }
        rule.onNodeWithText("Retour").assertIsDisplayed()
        shot("06_denied")
    }

    @Test
    fun homeFollowsACustomConfig() {
        val json = """{"schemaVersion":1,"home":{"columns":1,"tiles":[
            {"id":"a","action":"call","label":"Téléphoner","icon":"person"},
            {"id":"b","action":"camera"}]}}"""
        val config = (UiConfigParser.parse(json) as ParseResult.Ok).config
        rule.setContent { HomeScreen(seniorName = "Jean-Pierre", config = config) }
        rule.onNodeWithText("Téléphoner").assertIsDisplayed()
        rule.onNodeWithText("Photo").assertIsDisplayed()
        rule.onNodeWithText("Messages").assertDoesNotExist()
        shot("07_home_custom_config")
    }
}
