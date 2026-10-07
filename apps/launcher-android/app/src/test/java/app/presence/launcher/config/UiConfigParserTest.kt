package app.presence.launcher.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiConfigParserTest {
    private fun ok(json: String) = (UiConfigParser.parse(json) as ParseResult.Ok).config
    private fun error(json: String) = (UiConfigParser.parse(json) as ParseResult.Error).message

    @Test
    fun bundledDefaultIsValidAndEqualsTheBuiltInDefault() {
        val json = File("src/main/assets/ui-config.default.json").readText()
        assertEquals(UiConfig.Default, ok(json))
    }

    @Test
    fun minimalFileUsesDefaults() = assertEquals(UiConfig.Default, ok("""{"schemaVersion":1}"""))

    @Test
    fun partialThemeOverridesOnlyWhatIsGiven() {
        val c = ok("""{"schemaVersion":1,"theme":{"primary":"#006400"}}""")
        assertEquals(0xFF006400.toInt(), c.theme.primary)
        assertEquals(ThemeConfig.Default.background, c.theme.background)
    }

    @Test
    fun tilesCanBeReorderedRelabelledAndReiconed() {
        val c = ok(
            """{"schemaVersion":1,"home":{"columns":1,"tiles":[
                {"id":"a","action":"messages","label":"Courrier","icon":"home"},
                {"id":"b","action":"call"}]}}""",
        )
        assertEquals(1, c.home.columns)
        assertEquals(listOf(TileAction.MESSAGES, TileAction.CALL), c.home.tiles.map { it.action })
        assertEquals("Courrier", c.home.tiles[0].label)
        assertEquals(TileIcon.HOME, c.home.tiles[0].icon)
        assertEquals(TileIcon.CALL, c.home.tiles[1].icon) // default icon of the action
    }

    @Test
    fun rejectsInvalidJsonAndWrongVersion() {
        assertEquals("Ce fichier n'est pas un JSON valide.", error("not json"))
        assertTrue(error("""{"schemaVersion":2}""").contains("schemaVersion"))
        assertTrue(error("{}").contains("schemaVersion"))
    }

    @Test
    fun rejectsBadColorsAndLowContrast() {
        assertTrue(error("""{"schemaVersion":1,"theme":{"text":"red"}}""").contains("#RRGGBB"))
        assertTrue(error("""{"schemaVersion":1,"theme":{"text":"#777777","background":"#888888"}}""").contains("Contraste"))
        assertTrue(error("""{"schemaVersion":1,"theme":{"onPrimary":"#FFFF00","primary":"#FFFFFF"}}""").contains("Contraste"))
    }

    @Test
    fun fontScaleCannotShrinkTextOrBeHuge() {
        assertTrue(error("""{"schemaVersion":1,"theme":{"fontScale":0.8}}""").contains("fontScale"))
        assertTrue(error("""{"schemaVersion":1,"theme":{"fontScale":3}}""").contains("fontScale"))
        assertEquals(1.5f, ok("""{"schemaVersion":1,"theme":{"fontScale":1.5}}""").theme.fontScale)
    }

    @Test
    fun rejectsUnknownActionsAndIcons() {
        assertTrue(error("""{"schemaVersion":1,"home":{"tiles":[{"id":"x","action":"rm -rf"}]}}""").contains("action"))
        assertTrue(error("""{"schemaVersion":1,"home":{"tiles":[{"id":"x","action":"call","icon":"nope"}]}}""").contains("icon"))
    }

    @Test
    fun rejectsBadTileLists() {
        assertTrue(error("""{"schemaVersion":1,"home":{"tiles":[]}}""").contains("tuiles"))
        val seven = (1..7).joinToString(",") { """{"id":"t$it","action":"call"}""" }
        assertTrue(error("""{"schemaVersion":1,"home":{"tiles":[$seven]}}""").contains("tuiles"))
        assertTrue(error("""{"schemaVersion":1,"home":{"tiles":[{"id":"a","action":"call"},{"id":"a","action":"camera"}]}}""").contains("deux fois"))
        assertTrue(error("""{"schemaVersion":1,"home":{"tiles":[{"action":"call"}]}}""").contains("id"))
    }

    @Test
    fun rejectsTooManyColumnsAndLongLabels() {
        assertTrue(error("""{"schemaVersion":1,"home":{"columns":3}}""").contains("columns"))
        assertTrue(error("""{"schemaVersion":1,"home":{"tiles":[{"id":"a","action":"call","label":"${"x".repeat(25)}"}]}}""").contains("label"))
    }

    @Test
    fun rejectsOversizedFile() = assertTrue(error("{" + " ".repeat(UiConfigParser.MAX_CHARS) + "}").contains("trop gros"))
}
