package app.presence.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.presence.launcher.notifications.PairingStore
import app.presence.launcher.security.FamilyLock
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** A `presence://pair` link (the QR code of the home display) opened in the real settings activity. */
class PairingLinkEmulatorTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<SetupActivity>? = null

    private val fp = "88A2899B9DF00490B76854FB11FF4F1019470BA0D5FB7CD68BC700E4B4B7B68F"

    @Before
    fun codeAlreadyChosen() {
        context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("pairing", Context.MODE_PRIVATE).edit().clear().commit()
        FamilyLock(context).set("2580")
    }

    @After
    fun close() {
        scenario?.close()
        context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun open(link: String) {
        scenario = ActivityScenario.launch(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setClass(context, SetupActivity::class.java))
    }

    private fun unlock() {
        rule.onNodeWithText("Code famille").performTextInput("2580")
        rule.onNodeWithText("Valider").performClick()
        rule.waitForIdle()
    }

    @Test
    fun aValidLinkAsksForTheCodeThenFillsInTheFieldsWithoutSavingAnything() {
        open("presence://pair?u=https%3A%2F%2F192.168.1.66%3A8788&s=claim-0123456789abcdef&f=$fp")
        rule.onNodeWithText("Réglages famille : entrez le code").assertIsDisplayed() // nothing before the code
        unlock()
        rule.onNodeWithText("Appairer avec cet écran maison ?").assertIsDisplayed()
        rule.onNodeWithText("https://192.168.1.66:8788").assertIsDisplayed()
        rule.onNodeWithText("88A2 899B 9DF0 0490 B76854FB11FF4F1019470BA0D5FB7CD68BC700E4B4B7B68F".chunked(4).joinToString(" ").let { "88A2 899B 9DF0 0490 B768 54FB 11FF 4F10 1947 0BA0 D5FB 7CD6 8BC7 00E4 B4B7 B68F" }).assertIsDisplayed()
        assertNull("nothing is saved until the family confirms", PairingStore(context).load())
        rule.onNodeWithText("Annuler").performClick()
        rule.onNodeWithText("Appairer avec cet écran maison ?").assertDoesNotExist()
    }

    @Test
    fun anInvalidLinkIsRefusedWithAnExplanationAndFillsNothing() {
        open("presence://pair?u=https%3A%2F%2Fexample.com%3A8788&s=claim-0123456789abcdef&f=$fp")
        unlock()
        rule.onNodeWithText("Ce lien d'appairage n'est pas valable (adresse hors de la maison, secret absent ou incorrect, empreinte incorrecte).").assertIsDisplayed()
        assertNull(PairingStore(context).load())
    }
}
