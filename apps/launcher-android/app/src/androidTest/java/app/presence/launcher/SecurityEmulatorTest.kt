package app.presence.launcher

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import app.presence.launcher.notifications.PairingConfig
import app.presence.launcher.notifications.PairingStore
import app.presence.launcher.security.SecretBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

/** The real Android Keystore, and the real "Réglages famille" activity behind its code. */
class SecurityEmulatorTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Runs before the activity is launched (a @Before would run after): every test starts with no family code. */
    @get:Rule(order = 0)
    val noCodeYet = object : ExternalResource() {
        override fun before() {
            context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @get:Rule(order = 1) val rule = createAndroidComposeRule<SetupActivity>()

    @Test
    fun theRealKeystoreSealsAndOpensASecret() {
        val box = SecretBox.forKeystore()
        assertNotNull("this emulator must have a usable Keystore", box)
        val sealed = box!!.seal("0123456789abcdef0123")!!
        assertFalse(sealed.contains("0123456789"))
        assertEquals("0123456789abcdef0123", box.open(sealed))
        assertNull(box.open(sealed.dropLast(4) + "AAAA")) // altered
    }

    @Test
    fun theTokenOfThePairingIsEncryptedWithTheKeystore() {
        val config = PairingConfig.parse("https://192.168.1.20:8788", "0123456789abcdef0123", "B".repeat(64))!!
        PairingStore(context).save(config)
        val prefs = context.getSharedPreferences("pairing", Context.MODE_PRIVATE)
        assertTrue(prefs.contains("token_sealed") && !prefs.contains("token"))
        assertEquals(config, PairingStore(context).load())
    }

    @Test
    fun theFamilySettingsAskForANewCodeThenForThatCode() {
        // first use: choose a code
        rule.onNodeWithText("Choisissez un code famille").assertIsDisplayed()
        rule.onNodeWithText("Ces réglages sont réservés à la famille.").assertIsDisplayed()
        rule.onNodeWithText("Code famille").performTextInput("2580")
        rule.onNodeWithText("Répétez le code").performTextInput("2581")
        rule.onNodeWithText("Valider").performClick()
        rule.onNodeWithText("Les deux codes ne sont pas pareils.").assertIsDisplayed()
        rule.onNodeWithText("Répétez le code").performTextInput("2580")
        rule.onNodeWithText("Valider").performClick()
        rule.waitForIdle()
        // now the settings themselves are shown
        rule.onNodeWithText("Réglages famille").assertIsDisplayed()
        // with the real Keystore the hash is stored sealed, never as is
        val prefs = context.getSharedPreferences("family_lock", Context.MODE_PRIVATE)
        assertTrue(prefs.contains("sealed") && !prefs.contains("hash"))
        assertEquals(app.presence.launcher.security.FamilyLock.Result.Ok, app.presence.launcher.security.FamilyLock(context).verify("2580"))
    }

    @Test
    fun anObviousCodeIsRefusedAtFirstUse() {
        rule.onNodeWithText("Code famille").performTextInput("1234")
        rule.onNodeWithText("Répétez le code").performTextInput("1234")
        rule.onNodeWithText("Valider").performClick()
        rule.onNodeWithText("Ce code est trop simple (1111, 1234…). Choisissez-en un autre.").assertIsDisplayed()
        assertFalse(context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).let { it.contains("hash") || it.contains("sealed") })
    }
}
