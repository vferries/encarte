package io.github.vferries.encarte.navigation

import android.util.Log
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
import io.github.vferries.encarte.groups.GroupCardsRoute
import io.github.vferries.encarte.groups.GroupCardsViewModel
import io.github.vferries.encarte.importing.ImportChoiceScreen
import io.github.vferries.encarte.importing.ImportFailure
import io.github.vferries.encarte.importing.ImportRoute
import io.github.vferries.encarte.importing.ImportViewModel
import io.github.vferries.encarte.importing.failure
import io.github.vferries.encarte.importing.toDraft
import io.github.vferries.encarte.scan.ScannerRoute
import io.github.vferries.encarte.scan.ScannerViewModel
import io.github.vferries.encarte.settings.SettingsRoute
import io.github.vferries.encarte.settings.SettingsViewModel
import kotlin.reflect.KClass

private const val TAG = "EncarteNavHost"

@Composable
fun EncarteNavHost(container: AppContainer, initialBackStack: List<NavKey> = listOf(CardListKey)) {
    val backStack = rememberNavBackStack(*initialBackStack.toTypedArray())
    // NavDisplay requires a non-empty back stack: never pop the root entry.
    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }
    // Set when the card display archives a card; the list then offers to undo it.
    var archivedNotice by rememberSaveable { mutableStateOf<Long?>(null) }
    // Set when a file sent by another app gives no card; the list then says why.
    var importFailure by rememberSaveable { mutableStateOf<ImportFailure?>(null) }
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
                        CardListViewModel(
                            container.cardRepository, container.groupRepository, container.settingsRepository,
                            cardCollator(), container.clock, createSavedStateHandle(),
                        )
                    },
                    archivedNotice = archivedNotice,
                    onArchivedNoticeShown = { archivedNotice = null },
                    importFailure = importFailure,
                    onImportFailureShown = { importFailure = null },
                    onOpenCard = { id -> backStack.add(CardDisplayKey(id)) },
                    onAddCard = { groupId -> backStack.add(ScannerKey(groupId)) },
                    onOpenSettings = { backStack.add(SettingsKey) },
                    onImport = { backStack.add(SettingsKey) },
                    onChooseCards = { id -> backStack.add(GroupCardsKey(id)) },
                )
            }
            entry<CardDisplayKey> { key ->
                CardDisplayRoute(
                    viewModel = viewModel {
                        CardDisplayViewModel(key.cardId, container.cardRepository, container.clock, container.settingsRepository)
                    },
                    contactlessGuard = container.contactlessGuard,
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
            entry<ScannerKey> { key ->
                ScannerRoute(
                    viewModel = viewModel { ScannerViewModel(container.fileImport::importPicked) },
                    onBack = pop,
                    onScanned = { code -> replaceTopIf(ScannerKey::class, code.editKey(key.groupId)) },
                    onFileRead = { outcome ->
                        val next = outcome.destination(key.groupId)
                        if (next != null) {
                            replaceTopIf(ScannerKey::class, next)
                        } else {
                            Log.w(TAG, "A read file without a screen to open: ${outcome::class.simpleName}")
                        }
                    },
                    onManualEntry = { replaceTopIf(ScannerKey::class, CardEditKey(groupId = key.groupId)) },
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
                            groups = container.groupRepository,
                            initialGroupId = key.groupId,
                            draft = key.draft,
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
            entry<ImportKey> { key ->
                ImportRoute(
                    viewModel = viewModel { ImportViewModel(key.fileName, container.fileImport::importReceived) },
                    onRead = { outcome ->
                        // Idempotent: only the import screen still on top moves on.
                        if (backStack.lastOrNull() == key) {
                            val next = outcome.destination(groupId = null)
                            if (next != null) {
                                replaceTopIf(ImportKey::class, next)
                            } else {
                                importFailure = outcome.failure
                                pop()
                            }
                        }
                    },
                )
            }
            entry<ImportChoiceKey> { key ->
                ImportChoiceScreen(
                    codes = key.codes,
                    onBack = pop,
                    onChoose = { code -> replaceTopIf(ImportChoiceKey::class, code.toDraft().editKey(key.groupId)) },
                )
            }
            entry<SettingsKey> {
                SettingsRoute(
                    viewModel = viewModel { SettingsViewModel(container.settingsRepository, container.backupService) },
                    onBack = pop,
                )
            }
            entry<GroupCardsKey> { key ->
                GroupCardsRoute(
                    viewModel = viewModel {
                        GroupCardsViewModel(key.groupId, container.cardRepository, container.groupRepository, cardCollator())
                    },
                    // Idempotent: a deletion noticed twice must not pop another screen.
                    onBack = { if (backStack.lastOrNull() == key) pop() },
                )
            }
        },
    )
}
