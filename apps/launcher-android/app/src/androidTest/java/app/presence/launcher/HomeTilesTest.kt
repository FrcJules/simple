package app.presence.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.Modifier
import app.presence.launcher.apps.AppEntry
import app.presence.launcher.apps.AppInfo
import app.presence.launcher.config.HomeConfig
import app.presence.launcher.config.TileAction
import app.presence.launcher.config.TileConfig
import app.presence.launcher.config.TileIcon
import app.presence.launcher.config.UiConfig
import app.presence.launcher.ui.HomeScreen
import app.presence.launcher.ui.HomeTilesEditor
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The home rubriques the family can change: an app tile on the home screen, and the editor in the settings. */
class HomeTilesTest {
    @get:Rule val rule = createComposeRule()

    private val whatsapp = TileConfig("app-1", TileAction.APP, null, TileIcon.APPS, app = "com.whatsapp")

    @Test
    fun anAppTileShowsTheNameOfTheAppAndOpensIt() {
        var tapped: TileConfig? = null
        val config = UiConfig.Default.copy(home = HomeConfig(2, UiConfig.Default.home.tiles + whatsapp))
        rule.setContent {
            HomeScreen(seniorName = "Jean-Pierre", config = config, onTile = { tapped = it }, appInfo = { AppInfo("WhatsApp", null) })
        }
        rule.onNodeWithText("WhatsApp").assertIsDisplayed()
        rule.onNodeWithText("WhatsApp").performClick()
        assertEquals("com.whatsapp", tapped?.app)
    }

    @Test
    fun theEditorMovesRemovesAndAddsAndNeverEmptiesTheHomeScreen() {
        var tiles by mutableStateOf(UiConfig.Default.home.tiles) // a Compose state: the editor redraws when the list changes
        rule.setContent {
            // As in the settings: the editor sits in a scrolling page, taller than the screen.
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                HomeTilesEditor(
                    tiles = tiles, appInfo = { null },
                    loadApps = { listOf(AppEntry("com.whatsapp", "WhatsApp"), AppEntry("org.videolan.vlc", "VLC")) },
                    onChange = { tiles = it }, onReset = { tiles = UiConfig.Default.home.tiles },
                )
            }
        }
        rule.onNodeWithContentDescription("Descendre Appeler").performScrollTo().performClick()
        assertEquals(listOf("messages", "call", "camera", "gallery"), tiles.map { it.id })
        rule.onNodeWithContentDescription("Monter Messages").assertIsNotEnabled() // already first, and the word "Avant" is on the key
        rule.onNodeWithText("Ajouter une application").performScrollTo().performClick()
        rule.onNodeWithText("WhatsApp").performScrollTo().performClick()
        assertEquals("com.whatsapp", tiles.last().app)
    }
}
