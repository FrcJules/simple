package app.presence.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.presence.launcher.apps.AppEntry
import app.presence.launcher.apps.AppInfo
import app.presence.launcher.config.HomeConfig
import app.presence.launcher.config.TileAction
import app.presence.launcher.config.TileConfig
import app.presence.launcher.config.TileIcon
import app.presence.launcher.config.UiConfig
import app.presence.launcher.ui.HomeScreen
import app.presence.launcher.ui.HomeTilesEditor
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
        var tiles = UiConfig.Default.home.tiles
        rule.setContent {
            androidx.compose.runtime.key(tiles) {
                HomeTilesEditor(
                    tiles = tiles, appInfo = { null },
                    loadApps = { listOf(AppEntry("com.whatsapp", "WhatsApp"), AppEntry("org.videolan.vlc", "VLC")) },
                    onChange = { tiles = it }, onReset = { tiles = UiConfig.Default.home.tiles },
                )
            }
        }
        rule.onNodeWithContentDescription("Descendre Appeler").performClick()
        assertEquals(listOf("messages", "call", "camera", "gallery"), tiles.map { it.id })
        rule.onNodeWithContentDescription("Monter Messages").assertIsNotEnabled() // already first
        rule.onNodeWithText("Ajouter une application").performClick()
        rule.onNodeWithText("WhatsApp").performClick()
        assertEquals("com.whatsapp", tiles.last().app)
    }
}
