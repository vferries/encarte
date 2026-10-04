package io.github.vferries.encarte.navigation

import androidx.compose.runtime.Composable
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

@Composable
fun EncarteNavHost(container: AppContainer) {
    val backStack = rememberNavBackStack(CardListKey)
    // Never called on the root entry: NavDisplay requires a non-empty back stack.
    val pop: () -> Unit = { backStack.removeLastOrNull() }
    val replaceTop: (NavKey) -> Unit = { key -> backStack[backStack.lastIndex] = key }

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
                        CardListViewModel(container.cardRepository, container.settingsRepository, cardCollator())
                    },
                    onOpenCard = { id -> backStack.add(CardDisplayKey(id)) },
                    onAddCard = { backStack.add(ScannerKey) },
                    onOpenSettings = { backStack.add(SettingsKey) },
                    onImport = { backStack.add(SettingsKey) },
                )
            }
            entry<CardDisplayKey> { key ->
                CardDisplayRoute(
                    viewModel = viewModel { CardDisplayViewModel(key.cardId, container.cardRepository) },
                    onBack = pop,
                    onEdit = { id -> backStack.add(CardEditKey(cardId = id)) },
                )
            }
            entry<ScannerKey> {
                ScannerRoute(
                    viewModel = viewModel { ScannerViewModel() },
                    onBack = pop,
                    onScanned = { code ->
                        replaceTop(CardEditKey(barcodeValue = code.value, barcodeFormat = code.format, unsupportedFormat = code.format == null))
                    },
                    onManualEntry = { replaceTop(CardEditKey()) },
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
                        )
                    },
                    onSaved = { id, isNew -> if (isNew) replaceTop(CardDisplayKey(id)) else pop() },
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
