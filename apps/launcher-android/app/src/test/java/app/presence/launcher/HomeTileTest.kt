package app.presence.launcher

import app.presence.launcher.ui.HomeTile
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTileTest {
    @Test
    fun homeShowsOnlyTheFourEssentials() {
        assertEquals(4, HomeTile.entries.size)
    }
}
