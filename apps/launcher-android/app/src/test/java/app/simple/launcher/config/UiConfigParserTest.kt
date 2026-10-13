package app.simple.launcher.config

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
        val nine = (1..9).joinToString(",") { """{"id":"t$it","action":"call"}""" }
        assertTrue(error("""{"schemaVersion":1,"home":{"tiles":[$nine]}}""").contains("tuiles"))
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

    @Test
    fun anAppTileNeedsAPackageAndOnlyAnAppTileMayHaveOne() {
        val c = ok("""{"schemaVersion":1,"home":{"tiles":[{"id":"wa","action":"app","package":"com.whatsapp"},{"id":"c","action":"call"}]}}""")
        assertEquals("com.whatsapp", c.home.tiles[0].app)
        assertEquals(TileIcon.APPS, c.home.tiles[0].icon)
        assertEquals(null, c.home.tiles[1].app)
        assertTrue(error("""{"schemaVersion":1,"home":{"tiles":[{"id":"a","action":"app"}]}}""").contains("package"))
        assertTrue(error("""{"schemaVersion":1,"home":{"tiles":[{"id":"a","action":"call","package":"com.whatsapp"}]}}""").contains("n'est permis"))
    }

    @Test
    fun aPackageNameIsStrictlyAnAndroidPackageName() {
        for (bad in listOf("whatsapp", "com.whatsapp;rm", "com..x", "1com.x", "com.whatsapp/.Main", "com.x y", "")) {
            assertTrue(bad, error("""{"schemaVersion":1,"home":{"tiles":[{"id":"a","action":"app","package":"$bad"}]}}""").contains("package"))
        }
        for (good in listOf("com.whatsapp", "com.google.android.youtube", "org.mozilla.firefox", "fr.free_tv.app2")) {
            assertEquals(good, ok("""{"schemaVersion":1,"home":{"tiles":[{"id":"a","action":"app","package":"$good"}]}}""").home.tiles[0].app)
        }
    }

    @Test
    fun upToEightTilesAreAllowed() {
        fun tiles(n: Int) = (1..n).joinToString(",") { """{"id":"t$it","action":"app","package":"com.example.app$it"}""" }
        assertEquals(8, ok("""{"schemaVersion":1,"home":{"tiles":[${tiles(8)}]}}""").home.tiles.size)
        assertTrue(error("""{"schemaVersion":1,"home":{"tiles":[${tiles(9)}]}}""").contains("8"))
    }

    @Test
    fun theBuiltInDefaultNeverContainsAnAppTile() {
        assertEquals(listOf(TileAction.CALL, TileAction.MESSAGES, TileAction.CAMERA, TileAction.GALLERY), UiConfig.Default.home.tiles.map { it.action })
    }

    @Test
    fun writingAConfigAndReadingItBackGivesTheSameConfig() {
        val config = UiConfig(
            ThemeConfig.Default.copy(primary = 0xFF006400.toInt(), fontScale = 1.2f),
            HomeConfig(1, listOf(
                TileConfig("call", TileAction.CALL, null, TileIcon.CALL),
                TileConfig("m", TileAction.MESSAGES, "Courrier", TileIcon.HOME),
                TileConfig("app-1", TileAction.APP, null, TileIcon.APPS, app = "com.whatsapp"),
            )),
        )
        assertEquals(config, ok(UiConfigJson.write(config)))
        assertEquals(UiConfig.Default, ok(UiConfigJson.write(UiConfig.Default)))
    }

    @Test
    fun appsThatChangeThePhoneOrInstallThingsCannotBeTiles() {
        for (bad in listOf("com.android.settings", "com.android.settings.intelligence", "com.android.vending", "org.fdroid.fdroid", "com.samsung.android.settings", "com.anydesk.anydeskandroid", "app.simple.launcher")) {
            assertTrue(bad, error("""{"schemaVersion":1,"home":{"tiles":[{"id":"a","action":"app","package":"$bad"}]}}""").contains("ne peut pas être sur l'accueil"))
        }
        assertEquals("com.google.android.youtube", ok("""{"schemaVersion":1,"home":{"tiles":[{"id":"a","action":"app","package":"com.google.android.youtube"}]}}""").home.tiles[0].app)
    }

    @Test
    fun anAppTileKeepsTheNameOfTheAppSoItCannotPassForSomethingElse() {
        assertTrue(error("""{"schemaVersion":1,"home":{"tiles":[{"id":"a","action":"app","package":"com.whatsapp","label":"Photos"}]}}""").contains("garde toujours le nom"))
        // a label stays possible on the built-in rubriques
        assertEquals("Courrier", ok("""{"schemaVersion":1,"home":{"tiles":[{"id":"a","action":"messages","label":"Courrier"}]}}""").home.tiles[0].label)
    }
}
