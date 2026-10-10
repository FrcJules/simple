package app.presence.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactSearchTest {
    private fun c(id: Long, name: String) = Contact(id, name, "0600000000", null)
    private val people = listOf(c(1, "Marie Dupont"), c(2, "Éloïse Martin"), c(3, "Jean-Pierre Marc"), c(4, "Paul"), c(5, "Anne-Marie"), c(6, "Zoé"))

    private fun names(query: String) = ContactSearch.filter(people, query).map { it.name }

    @Test fun noLettersMeansEveryone() = assertEquals(people.map { it.name }, names(""))

    @Test fun matchesTheBeginningOfAnyWordOfTheName() = assertEquals(listOf("Marie Dupont", "Éloïse Martin", "Jean-Pierre Marc", "Anne-Marie"), names("mar"))

    @Test fun firstNamesComeBeforeOtherWords() = assertEquals(listOf("Marie Dupont", "Anne-Marie").first(), names("mari").first())

    @Test fun ignoresAccentsAndCapitals() {
        assertEquals(listOf("Éloïse Martin"), names("ELO"))
        assertEquals(listOf("Zoé"), names("zoe"))
    }

    @Test fun findsTheSecondPartOfACompoundFirstName() = assertEquals(listOf("Jean-Pierre Marc"), names("pie"))

    @Test fun nothingMatchesNothing() = assertEquals(emptyList<String>(), names("xyz"))

    @Test fun lettersWithoutHyphenOrSpaceFindACompoundName() {
        assertEquals(listOf("Jean-Pierre Marc"), names("jeanp")) // no hyphen on the keyboard
        assertEquals(listOf("Marie Dupont"), names("mariedu"))
        assertEquals(listOf("Anne-Marie"), names("annem"))
    }

    @Test fun anyPartOfTheNameIsFoundFromThreeLetters() {
        assertEquals(listOf("Jean-Pierre Marc"), names("rremar").take(1)) // across the hyphen and the space
        assertEquals(listOf("Éloïse Martin"), names("oisema")) // inside the name, from three letters
        assertEquals(emptyList<String>(), names("ie")) // two letters: only the start of a word counts
    }

    @Test fun startOfNameComesBeforeLaterWordsBeforeInsideMatches() {
        val list = listOf(c(1, "Paul Maman"), c(2, "Maman"), c(3, "Grand-maman"), c(4, "Oncle Amand"))
        assertEquals(listOf("Maman", "Paul Maman", "Grand-maman"), ContactSearch.filter(list, "maman").map { it.name })
    }
}

