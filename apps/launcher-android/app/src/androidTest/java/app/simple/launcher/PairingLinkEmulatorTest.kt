package app.simple.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import app.simple.launcher.notifications.PairingOffer
import app.simple.launcher.notifications.PairingStore
import app.simple.launcher.security.FamilyLock
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** A `simple://pair` link (the QR code of the home display) opened in the real settings activity. */
class PairingLinkEmulatorTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule val rule = createEmptyComposeRule()
    private var activity: Activity? = null

    private val fp = "88A2899B9DF00490B76854FB11FF4F1019470BA0D5FB7CD68BC700E4B4B7B68F"

    @Before
    fun codeAlreadyChosen() {
        context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("pairing", Context.MODE_PRIVATE).edit().clear().commit()
        FamilyLock(context).set("2580")
    }

    @After
    fun close() {
        activity?.let { a -> InstrumentationRegistry.getInstrumentation().runOnMainSync { a.finish() } }
        context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun open(link: String) {
        // startActivitySync waits for the first resume of the activity itself; ActivityScenario never saw this one reach onCreate on the CI emulator.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setClass(context, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent)
    }

    private fun unlock() {
        rule.onNodeWithText("Code famille").performTextInput("2580")
        rule.onNodeWithText("Valider").performClick()
        rule.waitForIdle()
    }

    @Test
    fun aValidLinkAsksForTheCodeThenFillsInTheFieldsWithoutSavingAnything() {
        open("simple://pair?u=https%3A%2F%2F192.168.1.66%3A8788&s=claim-0123456789abcdef&f=$fp")
        rule.onNodeWithText("Réglages famille : entrez le code").assertIsDisplayed() // nothing before the code
        unlock()
        rule.onNodeWithText("Appairer avec cet écran maison ?").assertIsDisplayed()
        rule.onNodeWithText("https://192.168.1.66:8788").assertIsDisplayed()
        PairingOffer.fingerprintLines(fp).forEach { rule.onNodeWithText(it).assertIsDisplayed() } // all 16 groups, never cut
        assertNull("nothing is saved until the family confirms", PairingStore(context).load())
        rule.onNodeWithText("Annuler").performScrollTo().performClick() // the card is taller than the screen at large text sizes
        rule.onNodeWithText("Appairer avec cet écran maison ?").assertDoesNotExist()
    }

    @Test
    fun anInvalidLinkIsRefusedWithAnExplanationAndFillsNothing() {
        open("simple://pair?u=https%3A%2F%2Fexample.com%3A8788&s=claim-0123456789abcdef&f=$fp")
        unlock()
        rule.onNodeWithText("Ce lien d'appairage n'est pas valable (adresse hors de la maison, secret absent ou incorrect, empreinte incorrecte).").assertIsDisplayed()
        assertNull(PairingStore(context).load())
    }
}
