package app.presence.launcher.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileEditingTest {
    private val defaults = UiConfig.Default.home.tiles

    @Test
    fun movesUpAndDownAndStopsAtTheEnds() {
        assertEquals(listOf("messages", "call", "camera", "gallery"), TileEditing.move(defaults, 0, +1).map { it.id })
        assertEquals(listOf("call", "messages", "gallery", "camera"), TileEditing.move(defaults, 3, -1).map { it.id })
        assertEquals(defaults, TileEditing.move(defaults, 0, -1))
        assertEquals(defaults, TileEditing.move(defaults, 3, +1))
        assertEquals(defaults, TileEditing.move(defaults, 9, +1))
    }

    @Test
    fun theLastRubriqueIsNeverRemoved() {
        var tiles = defaults
        repeat(3) { tiles = TileEditing.remove(tiles, 0) }
        assertEquals(listOf("gallery"), tiles.map { it.id })
        assertEquals(tiles, TileEditing.remove(tiles, 0))
    }

    @Test
    fun aRemovedBuiltInCanComeBackOnlyOnce() {
        val without = TileEditing.remove(defaults, 1)
        assertEquals(listOf(TileAction.MESSAGES), TileEditing.missingBuiltIns(without))
        val back = TileEditing.addBuiltIn(without, TileAction.MESSAGES)
        assertEquals(TileAction.MESSAGES, back.last().action)
        assertEquals(back, TileEditing.addBuiltIn(back, TileAction.MESSAGES)) // not twice
        assertTrue(TileEditing.missingBuiltIns(back).isEmpty())
    }

    @Test
    fun appsAreAddedOncePerPackageWithValidUniqueIds() {
        val one = TileEditing.addApp(defaults, "com.whatsapp")
        assertEquals("com.whatsapp", one.last().app)
        assertEquals(TileAction.APP, one.last().action)
        assertEquals(one, TileEditing.addApp(one, "com.whatsapp"))
        val two = TileEditing.addApp(one, "com.google.android.youtube")
        assertEquals(two.map { it.id }.toSet().size, two.size)
        assertTrue(two.all { it.id.length in 1..32 })
        // whatever the editor builds is accepted by the parser
        assertTrue(UiConfigParser.parse(UiConfigJson.write(UiConfig.Default.copy(home = UiConfig.Default.home.copy(tiles = two)))) is ParseResult.Ok)
    }

    @Test
    fun nothingIsAddedBeyondEightRubriques() {
        var tiles = defaults
        for (i in 1..10) tiles = TileEditing.addApp(tiles, "com.example.app$i")
        assertEquals(TileEditing.MAX, tiles.size)
        assertEquals(tiles, TileEditing.addApp(tiles, "com.example.other"))
    }
}
