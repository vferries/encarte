package io.github.vferries.encarte

import android.content.Context
import android.nfc.NfcAdapter
import android.os.SystemClock
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.vferries.encarte.backup.BackupService
import io.github.vferries.encarte.backup.CatimaArchive
import io.github.vferries.encarte.backup.importLabels
import io.github.vferries.encarte.brands.BrandCatalog
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.EncarteDatabase
import io.github.vferries.encarte.core.data.ImageStore
import io.github.vferries.encarte.core.nfc.ContactlessGuard
import io.github.vferries.encarte.core.nfc.NfcContactlessGuard
import io.github.vferries.encarte.core.prefs.SettingsRepository
import io.github.vferries.encarte.core.time.DeviceClock
import io.github.vferries.encarte.lock.LockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock

/** Manual dependency injection: every long-lived object, built once per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val clock: Clock = DeviceClock()
    private val database = EncarteDatabase.create(appContext)
    private val imageStore = ImageStore(File(appContext.filesDir, "images"), File(appContext.cacheDir, "staging"))

    val cardRepository = CardRepository(database.cardDao(), imageStore, clock)

    val settingsRepository = SettingsRepository(
        PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("settings") }
    )

    val brandCatalog = BrandCatalog {
        appContext.assets.open("brands.json").bufferedReader().use { it.readText() }
    }

    /** Null adapter on devices without NFC: the guard then blocks nothing. */
    val contactlessGuard: ContactlessGuard = NfcContactlessGuard(NfcAdapter.getDefaultAdapter(appContext))

    val backupService = BackupService(
        database = database,
        images = imageStore,
        archive = CatimaArchive(),
        workDir = File(appContext.cacheDir, "backup"),
        labels = { importLabels(appContext) },
        clock = clock,
    )

    val lockManager = LockManager(MainScope(), settingsRepository.lockEnabled, SystemClock::elapsedRealtime)

    /** Leftovers of editors killed with the process, interrupted imports and exports. */
    suspend fun cleanUpLeftovers() {
        cardRepository.deleteOrphanImages()
        withContext(Dispatchers.IO) {
            imageStore.clearStaging()
            backupService.clearWorkDir()
        }
    }
}
