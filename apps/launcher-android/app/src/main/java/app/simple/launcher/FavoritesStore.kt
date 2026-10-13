package app.simple.launcher

import android.content.Context

/**
 * The people the family chose to show (as photos) in "Appeler" and "Écrire". Kept inside the app, so it does not depend on the
 * phone's own "favourites" star, which many people cannot find. Only contact ids are stored, never names or numbers.
 */
class FavoritesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun ids(): Set<Long> = prefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun isChosen(id: Long) = id in ids()

    /** Adds the contact if absent, removes it if present. Returns whether it is now chosen. */
    @Synchronized
    fun toggle(id: Long): Boolean {
        val next = ids().toMutableSet()
        val chosen = if (id in next) { next.remove(id); false } else { next.add(id); true }
        prefs.edit().putStringSet("ids", next.map { it.toString() }.toSet()).commit()
        return chosen
    }

    fun clear() {
        prefs.edit().remove("ids").commit()
    }
}
