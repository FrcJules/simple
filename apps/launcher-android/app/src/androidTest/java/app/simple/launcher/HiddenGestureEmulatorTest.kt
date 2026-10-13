package app.simple.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import app.simple.launcher.security.FamilyLock
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The hidden gesture of the home screen never lets whoever holds the phone choose the family code. */
class HiddenGestureEmulatorTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule val rule = createEmptyComposeRule()
    private var activity: Activity? = null

    @Before
    fun noCodeYet() {
        context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun close() {
        activity?.let { a -> InstrumentationRegistry.getInstrumentation().runOnMainSync { a.finish() } }
        context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun open(fromHome: Boolean) {
        val intent = Intent(context, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (fromHome) intent.putExtra(SetupActivity.EXTRA_FROM_HOME, true)
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent)
    }

    @Test
    fun fromTheHomeScreenWithoutACodeNothingCanBeChosen() {
        open(fromHome = true)
        rule.onNodeWithText("Rien à faire ici. Tout va bien.").assertIsDisplayed()
        rule.onNodeWithText("Choisissez un code famille").assertDoesNotExist()
        assertFalse(FamilyLock(context).isSet())
    }

    @Test
    fun fromTheAppListTheFamilyCanChooseTheCode() {
        open(fromHome = false)
        rule.onNodeWithText("Choisissez un code famille").assertIsDisplayed()
    }
}
