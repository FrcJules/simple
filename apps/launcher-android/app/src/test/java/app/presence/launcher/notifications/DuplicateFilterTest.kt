package app.presence.launcher.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateFilterTest {
    @Test
    fun sameKeyAndTextWithinWindowIsDuplicate() {
        val f = DuplicateFilter(windowMs = 5_000)
        assertFalse(f.isDuplicate("k", "Marie|Salut", 1_000))
        assertTrue(f.isDuplicate("k", "Marie|Salut", 1_250))
    }

    @Test
    fun sameTextAfterTheWindowIsANewMessage() {
        val f = DuplicateFilter(windowMs = 5_000)
        assertFalse(f.isDuplicate("k", "Marie|Ok", 1_000))
        assertFalse(f.isDuplicate("k", "Marie|Ok", 9_000))
    }

    @Test
    fun differentTextOrKeyIsNotDuplicate() {
        val f = DuplicateFilter()
        assertFalse(f.isDuplicate("k1", "Marie|Salut", 1_000))
        assertFalse(f.isDuplicate("k1", "Marie|Ça va ?", 1_100))
        assertFalse(f.isDuplicate("k2", "Marie|Salut", 1_200))
    }

    @Test
    fun memoryIsBounded() {
        val f = DuplicateFilter(windowMs = 60_000, maxEntries = 3)
        (1..10).forEach { f.isDuplicate("k$it", "t", 1_000L + it) }
        assertFalse(f.isDuplicate("k1", "t", 1_100)) // evicted long ago, so seen as new
    }
}
