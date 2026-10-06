package io.github.vferries.encarte

import android.content.Context
import android.nfc.NfcAdapter
import android.os.SystemClock
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.vferries.encarte.backup.BackupService
import io.github.vferries.encarte.backup.CatimaArchive
import io.github.vferries.encarte.backup.importLabels
import io.github.vferries.encarte.brands.BrandCatalog
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.EncarteDatabase
import io.github.vferries.encarte.core.data.GroupRepository
import io.github.vferries.encarte.core.data.ImageStore
import io.github.vferries.encarte.core.nfc.ContactlessGuard
import io.github.vferries.encarte.core.nfc.NfcContactlessGuard
import io.github.vferries.encarte.core.prefs.SettingsRepository
import io.github.vferries.encarte.core.time.DeviceClock
import io.github.vferries.encarte.launcher.AndroidShortcutPublisher
import io.github.vferries.encarte.launcher.LauncherSync
import io.github.vferries.encarte.lock.LockManager
import io.github.vferries.encarte.widget.AndroidWidgetRenderer
import io.github.vferries.encarte.widget.WidgetSourceStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock

private const val TAG = "AppContainer"

/** Manual dependency injection: every long-lived object, built once per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val clock: Clock = DeviceClock()
    private val database = EncarteDatabase.create(appContext)
    private val imageStore = ImageStore(File(appContext.filesDir, "images"), File(appContext.cacheDir, "staging"))

    val cardRepository = CardRepository(database, imageStore, clock)
    val groupRepository = GroupRepository(database)

    val settingsRepository = SettingsRepository(
        PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("settings") }
    )

    val widgetSources = WidgetSourceStore(
        PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("widgets") }
    )

    val brandCatalog = BrandCatalog {
        appContext.assets.open("brands.json").bufferedReader().use { it.readText() }
    }

    /** Null adapter on devices without NFC: the guard then blocks nothing. */
    val contactlessGuard: ContactlessGuard by lazy { NfcContactlessGuard(NfcAdapter.getDefaultAdapter(appContext)) }

    val backupService = BackupService(
        database = database,
        images = imageStore,
        archive = CatimaArchive(),
        workDir = File(appContext.cacheDir, "backup"),
        labels = { importLabels(appContext) },
        clock = clock,
        brands = brandCatalog,
    )

    val lockManager = LockManager(MainScope(), settingsRepository.lockEnabled, SystemClock::elapsedRealtime)

    /** Work that outlives any screen: the home screen sync and the widget broadcasts. */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e(TAG, "Uncaught in the app scope", e) }
    )

    val launcherSync = LauncherSync(
        cards = cardRepository,
        groups = groupRepository,
        settings = settingsRepository,
        widgetSources = widgetSources,
        shortcuts = AndroidShortcutPublisher(appContext),
        widgets = AndroidWidgetRenderer(appContext),
    )

    /** Leftovers of editors killed with the process, interrupted imports and exports. */
    suspend fun cleanUpLeftovers() {
        cardRepository.deleteOrphanImages()
        withContext(Dispatchers.IO) {
            imageStore.clearStaging()
            backupService.clearWorkDir()
        }
    }
}
