package io.github.vferries.encarte.navigation

import android.os.Bundle
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.EncarteApp
import io.github.vferries.encarte.MainActivity
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.importing.TestFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/** A file sent by another app, as ImportActivity hands it over: copied, then named in the launch request. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceivedFileTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<EncarteApp>()

    private val ticket = TestFiles.pass(
        """{"formatVersion": 1, "organizationName": "Cinéma Lumière",
            "barcodes": [{"format": "PKBarcodeFormatQR", "message": "TICKET-42", "messageEncoding": "iso-8859-1"}]}"""
    )

    private fun waitFor(matcher: SemanticsMatcher) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }

    private fun received(bytes: ByteArray): File = app.container.importFiles.copy { bytes.inputStream() }

    @Test
    fun aPassOpensThePrefilledEditorAndSavesItsBarcode() {
        val file = received(ticket)
        ActivityScenario.launch<MainActivity>(LaunchRequests.importFile(app, file.name)).use {
            waitFor(hasText("New card"))
            waitFor(hasText("Cinéma Lumière"))
            assertFalse("the copy is deleted once read", file.exists())

            composeRule.onNodeWithText("Save").performClick()

            // The display replaces the editor ("Edit" only exists there).
            waitFor(hasContentDescription("Edit"))
            val card = runBlocking { app.container.cardRepository.observeCards().first() }.single()
            assertEquals("Cinéma Lumière", card.storeName)
            assertEquals("TICKET-42", card.cardNumber)
            assertEquals(BarcodeFormat.QR_CODE, card.barcodeFormat)
        }
    }

    @Test
    fun theEditorSurvivesARecreationWithoutReadingTheFileAgain() {
        val file = received(ticket)
        ActivityScenario.launch<MainActivity>(LaunchRequests.importFile(app, file.name)).use { scenario ->
            waitFor(hasText("Cinéma Lumière"))

            scenario.recreate()

            waitFor(hasText("New card"))
            waitFor(hasText("TICKET-42"))

            // ImportKey was replaced, not kept under the editor: Back lands on the list, with no second read.
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            waitFor(hasText("No cards yet"))
            composeRule.onAllNodes(hasText("This file is no longer available.")).assertCountEquals(0)
        }
    }

    @Test
    fun theEditorSurvivesTheLossOfItsViewModelsWithoutReadingTheFileAgain() {
        val file = received(ticket)
        val first = Robolectric.buildActivity(MainActivity::class.java, LaunchRequests.importFile(app, file.name)).setup()
        waitFor(hasText("Cinéma Lumière"))
        val state = Bundle()
        first.saveInstanceState(state)
        first.pause().stop().destroy()

        // A new controller has no retained ViewModel, as after process death.
        val second = Robolectric.buildActivity(MainActivity::class.java, LaunchRequests.importFile(app, file.name)).setup(state)
        waitFor(hasText("New card"))
        waitFor(hasText("TICKET-42"))

        second.get().onBackPressedDispatcher.onBackPressed()
        waitFor(hasText("No cards yet"))
        composeRule.onAllNodes(hasText("This file is no longer available.")).assertCountEquals(0)
        second.pause().stop().destroy()
    }

    @Test
    fun aFileGoneSinceTheRequestReturnsToTheListAndSaysSo() {
        ActivityScenario.launch<MainActivity>(LaunchRequests.importFile(app, UUID.randomUUID().toString())).use {
            waitFor(hasText("This file is no longer available."))
            waitFor(hasText("No cards yet"))
        }
    }

    @Test
    fun anUnrecognizedFileReturnsToTheListAndSaysSo() {
        val file = received("just some text".toByteArray())
        ActivityScenario.launch<MainActivity>(LaunchRequests.importFile(app, file.name)).use {
            waitFor(hasText("Unrecognized file."))
            assertFalse(file.exists())
        }
    }
}
