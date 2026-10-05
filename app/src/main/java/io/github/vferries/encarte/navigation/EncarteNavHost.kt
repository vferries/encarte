package io.github.vferries.encarte.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.vferries.encarte.AppContainer
import io.github.vferries.encarte.cards.display.CardDisplayRoute
import io.github.vferries.encarte.cards.display.CardDisplayViewModel
import io.github.vferries.encarte.cards.edit.CardEditRoute
import io.github.vferries.encarte.cards.edit.CardEditViewModel
import io.github.vferries.encarte.cards.list.CardListRoute
import io.github.vferries.encarte.cards.list.CardListViewModel
import io.github.vferries.encarte.cards.list.cardCollator
import io.github.vferries.encarte.scan.ScannerRoute
import io.github.vferries.encarte.scan.ScannerViewModel
import io.github.vferries.encarte.settings.SettingsRoute
import io.github.vferries.encarte.settings.SettingsViewModel
import kotlin.reflect.KClass

@Composable
fun EncarteNavHost(container: AppContainer) {
    val backStack = rememberNavBackStack(CardListKey)
    // NavDisplay requires a non-empty back stack: never pop the root entry.
    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }
    // Set when the card display archives a card; the list then offers to undo it.
    var archivedNotice by rememberSaveable { mutableStateOf<Long?>(null) }
    // Idempotent: a repeated callback (double tap, late result) must not replace another screen.
    fun replaceTopIf(expected: KClass<out NavKey>, key: NavKey) {
        if (expected.isInstance(backStack.lastOrNull())) backStack[backStack.lastIndex] = key
    }

    NavDisplay(
        backStack = backStack,
        onBack = pop,
        // Passing a list replaces NavDisplay's defaults: the saveable decorator must come first.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<CardListKey> {
                CardListRoute(
                    viewModel = viewModel {
                        CardListViewModel(container.cardRepository, container.settingsRepository, cardCollator(), container.clock)
                    },
                    archivedNotice = archivedNotice,
                    onArchivedNoticeShown = { archivedNotice = null },
                    onOpenCard = { id -> backStack.add(CardDisplayKey(id)) },
                    onAddCard = { backStack.add(ScannerKey) },
                    onOpenSettings = { backStack.add(SettingsKey) },
                    onImport = { backStack.add(SettingsKey) },
                )
            }
            entry<CardDisplayKey> { key ->
                CardDisplayRoute(
                    viewModel = viewModel { CardDisplayViewModel(key.cardId, container.cardRepository, container.clock) },
                    onBack = pop,
                    onEdit = { id -> backStack.add(CardEditKey(cardId = id)) },
                    onArchived = { id ->
                        // Idempotent: only the display that archived the card closes.
                        if (backStack.lastOrNull() == key) {
                            archivedNotice = id
                            pop()
                        }
                    },
                )
            }
            entry<ScannerKey> {
                ScannerRoute(
                    viewModel = viewModel { ScannerViewModel() },
                    onBack = pop,
                    onScanned = { code ->
                        replaceTopIf(ScannerKey::class, CardEditKey(barcodeValue = code.value, barcodeFormat = code.format, unsupportedFormat = code.format == null))
                    },
                    onManualEntry = { replaceTopIf(ScannerKey::class, CardEditKey()) },
                )
            }
            entry<CardEditKey> { key ->
                CardEditRoute(
                    viewModel = viewModel {
                        CardEditViewModel(
                            cardId = key.cardId,
                            prefillValue = key.barcodeValue,
                            prefillFormat = key.barcodeFormat,
                            showUnsupportedFormatNotice = key.unsupportedFormat,
                            cards = container.cardRepository,
                            brands = container.brandCatalog,
                            savedStateHandle = createSavedStateHandle(),
                        )
                    },
                    onSaved = { id, isNew ->
                        if (backStack.lastOrNull() is CardEditKey) {
                            if (isNew) replaceTopIf(CardEditKey::class, CardDisplayKey(id)) else pop()
                        }
                    },
                    onClose = pop,
                )
            }
            entry<SettingsKey> {
                SettingsRoute(
                    viewModel = viewModel { SettingsViewModel(container.settingsRepository, container.backupService) },
                    onBack = pop,
                )
            }
        },
    )
}
