package app.presence.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactTest {
    @Test
    fun telUriKeepsDigitsOnly() = assertEquals("tel:0612345678", telUri("06 12-34 56.78"))

    @Test
    fun telUriKeepsLeadingPlus() = assertEquals("tel:+33612345678", telUri(" +33 6 12 34 56 78"))

    @Test
    fun telUriDropsInnerPlus() = assertEquals("tel:0612", telUri("06+12"))

    @Test
    fun emptyOrPlusOnlyNumbersAreNotDialable() {
        assertEquals(false, isDialable(""))
        assertEquals(false, isDialable("+"))
        assertEquals(true, isDialable("0612345678"))
    }

    @Test
    fun toStringHidesPersonalData() = assertEquals("Contact(id=1)", Contact(1, "Marie", "0600000000", null).toString())

    @Test
    fun initialIsUppercasedFirstLetter() = assertEquals("M", Contact(1, " marie", "0600000000", null).initial)
}
