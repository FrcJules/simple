package app.presence.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test

/** Runs the real activity (real window, system bars, edge-to-edge) on the emulator. */
class MainActivityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun deviceShot(name: String) {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(Screenshots.file(name))
    }

    @Test
    fun launchesOnTheHomeScreen() {
        rule.onNodeWithText("Appeler").assertIsDisplayed()
        rule.onNodeWithText("Mes photos").assertIsDisplayed()
        deviceShot("10_activity_home")
    }

    @Test
    fun callTileExplainsPermissionsBeforeAskingThem() {
        rule.onNodeWithText("Appeler").performClick()
        rule.onNodeWithText("Continuer").assertIsDisplayed()
        deviceShot("11_activity_rationale")
    }
}
