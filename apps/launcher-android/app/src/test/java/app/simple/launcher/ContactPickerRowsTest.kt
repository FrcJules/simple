package app.simple.launcher

import org.junit.Test
import org.junit.Assert.assertEquals

class ContactPickerRowsTest {
    private fun c(id: Long, name: String) = Contact(id, name, "0600000000", null)
    private val all = listOf(c(1, "Zoé"), c(2, "Émilie"), c(3, "Alain"), c(4, "Hélène"))

    @Test fun chosenFirstThenByName() {
        assertEquals(listOf(1L, 3L, 2L, 4L), ContactPickerRows.rows(all, setOf(1), "").map { it.id })
    }

    @Test fun searchIgnoresAccentsAndCase() {
        assertEquals(listOf(2L), ContactPickerRows.rows(all, emptySet(), "emil").map { it.id })
        assertEquals(listOf(4L), ContactPickerRows.rows(all, emptySet(), "HELENE").map { it.id })
    }

    @Test fun limited() {
        val many = (1L..100L).map { c(it, "N$it") }
        assertEquals(30, ContactPickerRows.rows(many, emptySet(), "").size)
    }
}
