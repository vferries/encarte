package io.github.vferries.encarte.cards.edit

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.brands.BrandCatalog
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.GroupRepository
import io.github.vferries.encarte.core.data.ImageStore
import io.github.vferries.encarte.lock.LocalContentCovered
import io.github.vferries.encarte.testing.inMemoryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardEditScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val db = inMemoryDatabase()
    private val brands = BrandCatalog { """[{"name": "Carrefour", "aliases": [], "color": "#254F9B"}]""" }

    /** Clearing it stops the editors' group queries, as leaving the screen does, before the database closes. */
    private val viewModels = ViewModelStore()

    @After
    fun tearDown() {
        viewModels.clear()
        db.close()
    }

    private fun viewModel(value: String? = null, format: BarcodeFormat? = null, unsupported: Boolean = false): CardEditViewModel {
        val factory = viewModelFactory {
            initializer {
                CardEditViewModel(
                    cardId = null,
                    prefillValue = value,
                    prefillFormat = format,
                    showUnsupportedFormatNotice = unsupported,
                    cards = CardRepository(db, ImageStore(File(tmp.root, "i"), File(tmp.root, "s")), Clock.systemUTC()),
                    brands = brands,
                    savedStateHandle = SavedStateHandle(),
                    groups = GroupRepository(db),
                )
            }
        }
        return ViewModelProvider.create(viewModels, factory)[CardEditViewModel::class]
    }

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
    fun emptyEncodedValueSaysItIsRequired() {
        val vm = viewModel("1234", BarcodeFormat.CODE_128)
        composeRule.setContent { CardEditScreen(vm, onClose = {}, onPickImage = {}, onTakePhoto = {}) }
        composeRule.onNodeWithText("Store").performTextInput("Shop")
        composeRule.onNodeWithText("Required").assertDoesNotExist()

        composeRule.onNode(isToggleable()).performClick()

        composeRule.onNodeWithText("Required").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithText("Encoded value").performTextInput("X-1234")
        composeRule.onNodeWithText("Required").assertDoesNotExist()
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

    @Test
    fun expiryDateShowsFormattedAndCanBeCleared() {
        val vm = viewModel("123")
        vm.setExpiresOn(LocalDate.of(2027, 3, 12))
        composeRule.setContent { CardEditScreen(vm, onClose = {}, onPickImage = {}, onTakePhoto = {}) }

        composeRule.onNodeWithText("Mar 12, 2027").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Clear date").performScrollTo().performClick()

        composeRule.onNodeWithText("Mar 12, 2027").assertDoesNotExist()
        assertNull(vm.expiresOn)
    }

    @Test
    fun tappingTheExpiryFieldOpensTheDatePicker() {
        val vm = viewModel("123")
        composeRule.setContent { CardEditScreen(vm, onClose = {}, onPickImage = {}, onTakePhoto = {}) }

        composeRule.onNodeWithText("Expiry date (optional)").performScrollTo().performClick()

        composeRule.onNodeWithText("OK").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("OK").assertDoesNotExist()
        assertNull(vm.expiresOn)
    }

    @Test
    fun datePickerHidesWhileTheLockCoversTheScreen() {
        val vm = viewModel("123")
        var covered by mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalContentCovered provides covered) {
                CardEditScreen(vm, onClose = {}, onPickImage = {}, onTakePhoto = {})
            }
        }
        composeRule.onNodeWithContentDescription("Choose a date").performScrollTo().performClick()
        composeRule.onNodeWithText("OK").assertIsDisplayed()

        covered = true
        composeRule.onNodeWithText("OK").assertDoesNotExist()

        covered = false
        composeRule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun groupChipsToggleAndANewGroupIsChecked() {
        runBlocking { GroupRepository(db).create("Courses") }
        val vm = viewModel("42")
        composeRule.setContent { CardEditScreen(vm, onClose = {}, onPickImage = {}, onTakePhoto = {}) }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Courses").fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithText("Courses").performScrollTo().performClick()
        composeRule.onNodeWithText("Courses").assertIsSelected()

        composeRule.onNodeWithText("New group").performScrollTo().performClick()
        composeRule.onNodeWithText("Group name").performTextInput("Bricolage")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Group name").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText("Bricolage").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Bricolage").performScrollTo().assertIsSelected()
    }
}
