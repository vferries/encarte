package io.github.vferries.encarte.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.backup.BackupService
import io.github.vferries.encarte.backup.CatimaArchive
import io.github.vferries.encarte.backup.ImportLabels
import io.github.vferries.encarte.brands.BrandCatalog
import io.github.vferries.encarte.core.data.ImageStore
import io.github.vferries.encarte.core.prefs.SettingsRepository
import io.github.vferries.encarte.testing.MainDispatcherRule
import io.github.vferries.encarte.testing.inMemoryDatabase
import io.github.vferries.encarte.testing.testCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val db = inMemoryDatabase()
    private val backup by lazy {
        BackupService(
            db, ImageStore(File(tmp.root, "images"), File(tmp.root, "staging")), CatimaArchive(),
            File(tmp.root, "work"), { ImportLabels("%1\$s", "%1\$s", "%1\$s", Locale.US) }, Clock.systemUTC(),
            BrandCatalog { "[]" },
        )
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.viewModel(): Pair<SettingsViewModel, SettingsRepository> {
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "s.preferences_pb") }
        )
        return SettingsViewModel(settings, backup) to settings
    }

    @Test
    fun lockToggleIsPersisted() = runTest {
        val (vm, settings) = viewModel()

        vm.setLockEnabled(true)

        assertTrue(settings.lockEnabled.first { it })
        assertTrue(vm.uiState.first { it.lockEnabled }.lockEnabled)
    }

    @Test
    fun preparedEncryptedExportThenImportWithRetry() = runTest {
        db.cardDao().insert(testCard("Fnac"))
        val (vm, _) = viewModel()
        val exported = ByteArrayOutputStream()

        vm.prepareExport("secret".toCharArray())
        vm.exportTo { exported }
        assertEquals(BackupMessage.Exported(1), vm.uiState.first { it.message != null }.message)
        vm.messageShown()

        vm.startImport { exported.toByteArray().inputStream() }
        assertEquals(PasswordPrompt.FIRST_TRY, vm.uiState.first { it.passwordPrompt != null }.passwordPrompt)

        vm.submitImportPassword("wrong".toCharArray())
        assertEquals(PasswordPrompt.RETRY, vm.uiState.first { it.passwordPrompt == PasswordPrompt.RETRY }.passwordPrompt)

        vm.submitImportPassword("secret".toCharArray())
        val done = vm.uiState.first { it.message != null }
        assertEquals(BackupMessage.Imported(imported = 0, skipped = 1), done.message)
        assertNull(done.passwordPrompt)
        assertTrue(File(tmp.root, "work").listFiles().isNullOrEmpty())
    }

    @Test
    fun exportWithoutPreparationFails() = runTest {
        val (vm, _) = viewModel()

        vm.exportTo { ByteArrayOutputStream() }

        assertEquals(BackupMessage.ExportFailed, vm.uiState.first { it.message != null }.message)
    }

    @Test
    fun cancellingPasswordPromptDiscardsWorkFile() = runTest {
        db.cardDao().insert(testCard("Fnac"))
        val (vm, _) = viewModel()
        val exported = ByteArrayOutputStream()
        vm.prepareExport("pw".toCharArray())
        vm.exportTo { exported }
        vm.uiState.first { it.message != null }

        vm.startImport { exported.toByteArray().inputStream() }
        vm.uiState.first { it.passwordPrompt != null }
        vm.cancelImport()

        assertNull(vm.uiState.first { it.passwordPrompt == null }.passwordPrompt)
        assertTrue(File(tmp.root, "work").listFiles().isNullOrEmpty())
    }

    @Test
    fun revokedAccessToThePickedBackupIsReported() = runTest {
        val (vm, _) = viewModel()

        vm.startImport { throw SecurityException("permission revoked") }

        val state = vm.uiState.first { it.message != null }
        assertEquals(BackupMessage.ImportFailed, state.message)
        assertFalse(state.busy)
    }

    @Test
    fun unavailablePickerDropsPreparedPassword() = runTest {
        val (vm, _) = viewModel()
        vm.prepareExport("pw".toCharArray())

        vm.exportUnavailable()
        assertEquals(BackupMessage.ExportFailed, vm.uiState.first { it.message != null }.message)
        vm.messageShown()

        vm.exportTo { ByteArrayOutputStream() }
        assertEquals(BackupMessage.ExportFailed, vm.uiState.first { it.message != null }.message)
    }

    @Test
    fun contactlessBlockingToggleIsPersisted() = runTest {
        val (vm, settings) = viewModel()
        assertTrue(vm.uiState.first { it.nfcBlockEnabled }.nfcBlockEnabled)

        vm.setNfcBlockEnabled(false)

        assertFalse(settings.nfcBlockEnabled.first { !it })
        assertFalse(vm.uiState.first { !it.nfcBlockEnabled }.nfcBlockEnabled)
    }
}
