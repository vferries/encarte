package io.github.vferries.encarte.cards.edit

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.brands.BrandCatalog
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.ImageStore
import io.github.vferries.encarte.testing.inMemoryDatabase
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardEditScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val db = inMemoryDatabase()
    private val brands = BrandCatalog { """[{"name": "Carrefour", "aliases": [], "color": "#254F9B"}]""" }

    @After
    fun tearDown() = db.close()

    private fun viewModel(value: String? = null, format: BarcodeFormat? = null, unsupported: Boolean = false) =
        CardEditViewModel(
            cardId = null,
            prefillValue = value,
            prefillFormat = format,
            showUnsupportedFormatNotice = unsupported,
            cards = CardRepository(db.cardDao(), ImageStore(File(tmp.root, "i"), File(tmp.root, "s")), Clock.systemUTC()),
            brands = brands,
        )

    @Test
    fun saveEnablesOnceStoreNameIsTypedAndPreviewShows() {
        val vm = viewModel("4006381333931", BarcodeFormat.EAN_13)
        composeRule.setContent { CardEditScreen(vm, onClose = {}, onPickImage = {}, onTakePhoto = {}) }

        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithText("Store").performTextInput("Fnac")

        composeRule.onNodeWithText("Save").assertIsEnabled()
        composeRule.onNodeWithTag("barcode").assertIsDisplayed()
    }

    @Test
    fun brandSuggestionFillsStoreName() {
        val vm = viewModel("123")
        composeRule.setContent { CardEditScreen(vm, onClose = {}, onPickImage = {}, onTakePhoto = {}) }

        composeRule.onNodeWithText("Store").performTextInput("carre")
        composeRule.onNodeWithText("Carrefour").performClick()

        composeRule.runOnIdle { assertTrue(vm.storeName.text.toString() == "Carrefour") }
    }

    @Test
    fun invalidValueShowsError() {
        val vm = viewModel("4006381333932", BarcodeFormat.EAN_13)
        composeRule.setContent { CardEditScreen(vm, onClose = {}, onPickImage = {}, onTakePhoto = {}) }

        composeRule.onNodeWithText("Invalid check digit").assertIsDisplayed()
    }

    @Test
    fun unsupportedFormatNoticeIsShown() {
        val vm = viewModel("0101234567890128", null, unsupported = true)
        composeRule.setContent { CardEditScreen(vm, onClose = {}, onPickImage = {}, onTakePhoto = {}) }

        composeRule.onNodeWithText("Encarté can't display this type of barcode. Only the number will be shown.")
            .assertIsDisplayed()
    }

    @Test
    fun systemBackWithChangesAsksBeforeLeavingNavDisplay() {
        val vm = viewModel("123")
        composeRule.setContent {
            val backStack = remember { mutableStateListOf("home", "edit") }
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = { key ->
                    NavEntry(key) {
                        if (key == "home") Text("Home")
                        else CardEditScreen(vm, onClose = { backStack.removeLastOrNull() }, onPickImage = {}, onTakePhoto = {})
                    }
                },
            )
        }
        composeRule.onNodeWithText("Store").performTextInput("Fnac")

        Espresso.pressBack()

        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
    }
}
