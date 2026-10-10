package app.presence.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.presence.launcher.ui.CallScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** "Qui voulez-vous appeler ?": favourites, everybody, and the search by first letters on the big keyboard. */
class CallScreenTabsTest {
    @get:Rule val rule = createComposeRule()

    private val marie = Contact(1, "Marie", "0600000001", null)
    private val jules = Contact(2, "Jules", "0600000002", null)
    private val paul = Contact(3, "Paul Martin", "0600000003", null)

    @Test
    fun favouritesFirstAndGeneralShowsEverybody() {
        rule.setContent { CallScreen(favorites = listOf(marie), all = listOf(jules, marie, paul), onBack = {}, onCall = {}) }
        rule.onNodeWithText("Marie").assertIsDisplayed()
        rule.onNodeWithText("Jules").assertDoesNotExist()
        rule.onNodeWithText("Général").performClick()
        rule.onNodeWithText("Jules").assertIsDisplayed()
        rule.onNodeWithText("Paul Martin").assertIsDisplayed()
    }

    @Test
    fun withoutFavouritesEverybodyIsShownAtOnce() {
        rule.setContent { CallScreen(favorites = emptyList(), all = listOf(jules, marie), onBack = {}, onCall = {}) }
        rule.onNodeWithText("Jules").assertIsDisplayed()
        rule.onNodeWithText("Marie").assertIsDisplayed()
    }

    @Test
    fun typingLettersFindsAPersonAndStillAsksConfirmation() {
        var called: Contact? = null
        rule.setContent { CallScreen(favorites = listOf(marie), all = listOf(jules, marie, paul), onBack = {}, onCall = { called = it }) }
        rule.onNodeWithText("Chercher").performClick()
        rule.onNodeWithText("Touchez les premières lettres du prénom").assertIsDisplayed()
        rule.onNodeWithText("P").performClick()
        rule.onNodeWithText("Paul Martin").assertIsDisplayed()
        rule.onNodeWithText("Marie").assertDoesNotExist()
        rule.onNodeWithText("Paul Martin").performClick()
        rule.onNodeWithText("Appeler Paul Martin ?").assertIsDisplayed()
        assertEquals(null, called) // nothing is dialled before "Oui, appeler"
    }

    @Test
    fun aSearchWithNoMatchSaysSo() {
        rule.setContent { CallScreen(favorites = listOf(marie), all = listOf(marie), onBack = {}, onCall = {}) }
        rule.onNodeWithText("Chercher").performClick()
        rule.onNodeWithText("Z").performClick()
        rule.onNodeWithText("Personne ne correspond.", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Recommencer").performClick()
        rule.onNodeWithText("Personne ne correspond.", substring = true).assertDoesNotExist()
    }

    @Test
    fun theCorrectionKeysStayInViewAndWorkWithoutScrolling() {
        rule.setContent { CallScreen(favorites = listOf(marie), all = listOf(jules, marie, paul), onBack = {}, onCall = {}) }
        rule.onNodeWithText("Chercher").performClick()
        rule.onNodeWithText("M").performClick()
        rule.onNodeWithText("A").performClick()
        rule.onNodeWithContentDescription("Reculer").assertIsDisplayed() // no scrolling needed to reach it
        rule.onNodeWithContentDescription("Recommencer").assertIsDisplayed()
        rule.onNodeWithContentDescription("Reculer").performClick()
        rule.onNodeWithContentDescription("Vous avez écrit : M").assertIsDisplayed()
        rule.onNodeWithContentDescription("Recommencer").performClick()
        rule.onNodeWithContentDescription("Vous avez écrit : -").assertIsDisplayed()
    }

    @Test
    fun writingToSomeoneOffersTheSameFavouritesGeneralAndSearch() {
        var picked: Contact? = null
        rule.setContent { app.presence.launcher.ui.PickContactScreen(R.string.write_to_title, listOf(marie), listOf(jules, marie, paul), onPick = { picked = it }, onBack = {}) }
        rule.onNodeWithText("Favoris").assertIsDisplayed()
        rule.onNodeWithText("Général").performClick()
        rule.onNodeWithText("Paul Martin").assertIsDisplayed()
        rule.onNodeWithText("Chercher").performClick()
        rule.onNodeWithText("J").performClick()
        rule.onNodeWithText("Jules").performClick()
        assertEquals(jules, picked)
    }

    @Test
    fun aCompoundNameIsFoundWithoutAHyphenAndAllMatchesCanBeScrolled() {
        val many = (1..30).map { Contact(100L + it, "Maman $it", "06000000$it", null) } + Contact(1, "Jean-Baptiste", "0611111111", null)
        rule.setContent { CallScreen(favorites = emptyList(), all = many, onBack = {}, onCall = {}) }
        rule.onNodeWithText("Chercher").performClick()
        for (letter in "JEANB") rule.onNodeWithText(letter.toString()).performClick()
        rule.onNodeWithText("Jean-Baptiste").assertIsDisplayed()
        rule.onNodeWithContentDescription("Recommencer").performClick()
        for (letter in "MAMAN") rule.onNodeWithText(letter.toString()).performClick()
        rule.onNodeWithText("30 personnes").assertIsDisplayed() // thirty people match: the count says so, the list scrolls
        rule.onNodeWithText("Maman 1").assertIsDisplayed()
    }
}
