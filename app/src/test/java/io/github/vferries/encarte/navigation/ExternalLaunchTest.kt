package io.github.vferries.encarte.navigation

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.EncarteApp
import io.github.vferries.encarte.MainActivity
import io.github.vferries.encarte.testing.testCard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExternalLaunchTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<EncarteApp>()

    private fun waitFor(matcher: SemanticsMatcher) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }

    private fun isShown(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun savedCard(): Long = runBlocking { app.container.cardRepository.save(testCard("Fnac", cardNumber = "A-42")) }

    @Test
    fun aCardRequestOpensTheCardAndBackReturnsToTheList() {
        val id = savedCard()
        ActivityScenario.launch<MainActivity>(LaunchRequests.viewCard(app, id)).use { scenario ->
            // "Edit" only exists on the card display, "Sort" only on the list.
            waitFor(hasContentDescription("Edit"))

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            waitFor(hasContentDescription("Sort"))
        }
    }

    @Test
    fun anAddCardRequestOpensTheScanner() {
        ActivityScenario.launch<MainActivity>(LaunchRequests.addCard(app)).use {
            waitFor(hasText("Enter manually"))
        }
    }

    @Test
    fun aDeletedCardSaysItNoLongerExists() {
        ActivityScenario.launch<MainActivity>(LaunchRequests.viewCard(app, 9_999)).use {
            waitFor(hasText("This card no longer exists."))
        }
    }

    @Test
    fun aRestoredActivityKeepsItsOwnBackStack() {
        val id = savedCard()
        ActivityScenario.launch<MainActivity>(LaunchRequests.viewCard(app, id)).use { scenario ->
            waitFor(hasContentDescription("Edit"))
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            waitFor(hasContentDescription("Sort"))

            scenario.recreate()

            waitFor(hasContentDescription("Sort"))
            assertTrue("the request is not replayed", !isShown(hasContentDescription("Edit")))
        }
    }
}
