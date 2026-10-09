package app.presence.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        rule.onNodeWithText("P").performScrollTo().performClick()
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
        rule.onNodeWithText("Z").performScrollTo().performClick()
        rule.onNodeWithText("Personne ne correspond.").assertIsDisplayed()
        rule.onNodeWithText("Recommencer").performScrollTo().performClick()
        rule.onNodeWithText("Personne ne correspond.").assertDoesNotExist()
    }
}
