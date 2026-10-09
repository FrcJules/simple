package app.presence.launcher.config

/** The family's changes to the home rubriques, as small pure steps (so each rule can be tested). Every step keeps the list valid. */
object TileEditing {
    const val MAX = UiConfigParser.MAX_TILES

    fun canAdd(tiles: List<TileConfig>) = tiles.size < MAX

    /** Moves the tile at [index] by [delta] places (-1 up, +1 down); at an end it stays. */
    fun move(tiles: List<TileConfig>, index: Int, delta: Int): List<TileConfig> {
        val to = index + delta
        if (index !in tiles.indices || to !in tiles.indices) return tiles
        return tiles.toMutableList().also { val t = it.removeAt(index); it.add(to, t) }
    }

    /** Removes the tile at [index]; the last one is never removed (an empty home screen would be a dead end). */
    fun remove(tiles: List<TileConfig>, index: Int): List<TileConfig> =
        if (tiles.size <= 1 || index !in tiles.indices) tiles else tiles.filterIndexed { i, _ -> i != index }

    /** The built-in rubriques (call, messages, camera, photos) not on the home screen yet. */
    fun missingBuiltIns(tiles: List<TileConfig>): List<TileAction> =
        TileAction.entries.filter { it != TileAction.APP && tiles.none { t -> t.action == it } }

    fun addBuiltIn(tiles: List<TileConfig>, action: TileAction): List<TileConfig> =
        if (!canAdd(tiles) || action == TileAction.APP || tiles.any { it.action == action }) tiles
        else tiles + TileConfig(action.wire, action, label = null, icon = action.defaultIcon)

    /** Adds a tile for the app; one tile per app. The id is derived from the package name (unique, short, stable). */
    fun addApp(tiles: List<TileConfig>, packageName: String): List<TileConfig> =
        if (!canAdd(tiles) || tiles.any { it.app == packageName }) tiles
        else tiles + TileConfig(idFor(packageName, tiles), TileAction.APP, label = null, icon = TileIcon.APPS, app = packageName)

    private fun idFor(packageName: String, tiles: List<TileConfig>): String {
        val base = "app-" + (packageName.hashCode().toLong() and 0xFFFFFFFFL).toString(36)
        var id = base
        var n = 2
        while (tiles.any { it.id == id }) id = "$base-${n++}"
        return id
    }
}
