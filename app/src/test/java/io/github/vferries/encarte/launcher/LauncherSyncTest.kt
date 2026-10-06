package io.github.vferries.encarte.launcher

import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.cards.list.cardCollator
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.GroupNameResult
import io.github.vferries.encarte.core.data.GroupRepository
import io.github.vferries.encarte.core.data.ImageStore
import io.github.vferries.encarte.core.prefs.SettingsRepository
import io.github.vferries.encarte.testing.eventually
import io.github.vferries.encarte.testing.inMemoryDatabase
import io.github.vferries.encarte.testing.testCard
import io.github.vferries.encarte.widget.WidgetSourceStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private fun onMainThread() = Looper.myLooper() == Looper.getMainLooper()

private class FakeShortcuts : ShortcutPublisher {
    override val cardLimit = 3
    val published = CopyOnWriteArrayList<List<LauncherCard>>()
    val publishedOnMain = CopyOnWriteArrayList<Boolean>()
    val pinnedSyncs = CopyOnWriteArrayList<Pair<List<LauncherCard>, Boolean>>()

    @Volatile
    var refuseNext = false

    override fun publish(cards: List<LauncherCard>): Boolean {
        published += cards
        publishedOnMain += onMainThread()
        return !refuseNext.also { refuseNext = false }
    }

    override fun syncPinned(cards: List<LauncherCard>, locked: Boolean) {
        pinnedSyncs += cards to locked
    }
}

private class FakeWidgets : WidgetRenderer {
    @Volatile
    var ids = intArrayOf()

    @Volatile
    var failNext = false
    val renders = CopyOnWriteArrayList<Pair<Int, WidgetContent>>()
    val renderedOnMain = CopyOnWriteArrayList<Boolean>()

    override fun widgetIds() = ids

    override fun render(appWidgetId: Int, content: WidgetContent) {
        if (failNext) {
            failNext = false
            throw IllegalStateException("launcher gone")
        }
        renders += appWidgetId to content
        renderedOnMain += onMainThread()
    }
}

/** Can hold one read of the widget sources open, as a slow read would, while the stored sources move on. */
private class GatedDataStore(private val real: DataStore<Preferences>) : DataStore<Preferences> {
    private val holdNext = AtomicBoolean(false)
    val readHeld = CompletableDeferred<Unit>()
    val releaseRead = CompletableDeferred<Unit>()

    fun holdNextRead() = holdNext.set(true)

    override val data: Flow<Preferences> = flow {
        if (holdNext.compareAndSet(true, false)) {
            val snapshot = real.data.first()
            readHeld.complete(Unit)
            releaseRead.await()
            emit(snapshot)
        } else {
            emitAll(real.data)
        }
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences) = real.updateData(transform)
}

/** Fails the first read with an error the store does not absorb itself (it turns IOException into defaults), then behaves normally. */
private class FailOnceDataStore(private val real: DataStore<Preferences>) : DataStore<Preferences> {
    private val failNext = AtomicBoolean(true)
    val failures = AtomicInteger(0)

    override val data: Flow<Preferences> = flow {
        if (failNext.compareAndSet(true, false)) {
            failures.incrementAndGet()
            throw IllegalStateException("store unavailable")
        }
        emitAll(real.data)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences) = real.updateData(transform)
}

/** Fails [failuresBefore] subscriptions, then emits and fails once more after [failAfterEmit], then behaves normally. */
private class ScriptedDataStore(
    private val real: DataStore<Preferences>,
    private val failuresBefore: Int,
    private val failAfterEmit: CompletableDeferred<Unit>,
) : DataStore<Preferences> {
    private val subscriptions = AtomicInteger(0)

    override val data: Flow<Preferences> = flow {
        when (val n = subscriptions.getAndIncrement()) {
            in 0 until failuresBefore -> throw IllegalStateException("store unavailable ($n)")
            failuresBefore -> {
                emit(real.data.first())
                failAfterEmit.await()
                throw IllegalStateException("store unavailable again")
            }
            else -> emitAll(real.data)
        }
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences) = real.updateData(transform)
}

@RunWith(AndroidJUnit4::class)
class LauncherSyncTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = inMemoryDatabase()
    private val cards by lazy { CardRepository(db, ImageStore(File(tmp.root, "images"), File(tmp.root, "staging")), Clock.systemUTC()) }
    private val groups = GroupRepository(db)
    private val settings by lazy {
        SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "s.preferences_pb") })
    }
    private val sourcesData by lazy {
        GatedDataStore(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "w.preferences_pb") })
    }
    private val sources by lazy { WidgetSourceStore(sourcesData) }
    private val shortcuts = FakeShortcuts()
    private val widgets = FakeWidgets()

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun started() = LauncherSync(cards, groups, settings, sources, shortcuts, widgets, cardCollator(Locale.FRANCE))
        .also { it.start(scope) }

    private fun shownNames(content: WidgetContent) = (content as WidgetContent.Shown).cards.map { it.storeName }

    private fun lastTitle() = (widgets.renders.lastOrNull()?.second as? WidgetContent.Shown)?.title

    @Test
    fun theTopCardsArePublishedAndInvisibleChangesPushNothing() = runTest {
        val fnac = cards.save(testCard("Fnac", isFavorite = true))
        cards.save(testCard("Zara"))
        started()
        eventually { shortcuts.published.isNotEmpty() }
        assertEquals(listOf("Fnac", "Zara"), shortcuts.published.last().map { it.storeName })

        cards.save(cards.get(fnac)!!.copy(note = "not on the home screen"))
        cards.save(cards.get(fnac)!!.copy(storeName = "Fnac Darty"))

        eventually { shortcuts.published.last().first().storeName == "Fnac Darty" }
        assertEquals(2, shortcuts.published.size)
    }

    @Test
    fun theLockHidesTheCardsFromTheHomeScreen() = runTest {
        cards.save(testCard("Fnac", isFavorite = true))
        widgets.ids = intArrayOf(7)
        started()
        eventually { widgets.renders.isNotEmpty() && shortcuts.published.isNotEmpty() }

        settings.setLockEnabled(true)

        eventually { shortcuts.published.last().isEmpty() }
        eventually { widgets.renders.last() == (7 to WidgetContent.Locked) }
        assertTrue(shortcuts.pinnedSyncs.last().second)
    }

    @Test
    fun aDeletedCardLeavesThePinnedSync() = runTest {
        val fnac = cards.save(testCard("Fnac"))
        val zara = cards.save(testCard("Zara"))
        started()
        eventually { shortcuts.pinnedSyncs.lastOrNull()?.first?.size == 2 }

        cards.delete(fnac)

        eventually { shortcuts.pinnedSyncs.last().first.map { it.id } == listOf(zara) }
    }

    @Test
    fun aRefusedPublishIsRetriedAtTheNextChange() = runTest {
        val fnac = cards.save(testCard("Fnac"))
        shortcuts.refuseNext = true
        started()
        eventually { shortcuts.published.size == 1 }

        cards.save(cards.get(fnac)!!.copy(note = "any change"))

        eventually { shortcuts.published.size == 2 }
        assertEquals(shortcuts.published[0], shortcuts.published[1])
    }

    @Test
    fun aFailingWidgetDoesNotStopTheSync() = runTest {
        val fnac = cards.save(testCard("Fnac", isFavorite = true))
        widgets.ids = intArrayOf(7)
        widgets.failNext = true
        started()
        eventually { shortcuts.published.isNotEmpty() }

        cards.save(cards.get(fnac)!!.copy(storeName = "Fnac Darty"))

        eventually { widgets.renders.lastOrNull()?.let { shownNames(it.second) } == listOf("Fnac Darty") }
    }

    @Test
    fun renderWidgetsDrawsEvenWithoutAnyChange() = runTest {
        widgets.ids = intArrayOf(7)
        val sync = started()
        eventually { widgets.renders.size == 1 }

        sync.renderWidgets(intArrayOf(7))

        assertEquals(2, widgets.renders.size)
    }

    @Test
    fun renderWidgetsPushesOffTheMainThread() = runTest {
        widgets.ids = intArrayOf(7)
        val sync = LauncherSync(cards, groups, settings, sources, shortcuts, widgets, cardCollator(Locale.FRANCE))
        // Robolectric runs the test on the main thread, where the widget configuration screen calls renderWidgets.
        assertTrue(onMainThread())

        sync.renderWidgets(intArrayOf(7))

        // Icon bitmaps, the font and the binder calls must not hold the main thread.
        assertEquals(listOf(false), shortcuts.publishedOnMain)
        assertEquals(listOf(false), widgets.renderedOnMain)
    }

    @Test
    fun aWidgetFollowsItsGroupAndItsRename() = runTest {
        val fnac = cards.save(testCard("Fnac"))
        val courses = (groups.create("Courses") as GroupNameResult.Saved).id
        groups.setMembership(fnac, courses, member = true)
        widgets.ids = intArrayOf(7)
        started()
        sources.set(7, WidgetSource.Group(courses))
        eventually { (widgets.renders.lastOrNull()?.second as? WidgetContent.Shown)?.title == WidgetTitle.Group("Courses") }
        assertEquals(listOf("Fnac"), shownNames(widgets.renders.last().second))

        groups.rename(courses, "Marché")

        eventually { (widgets.renders.last().second as WidgetContent.Shown).title == WidgetTitle.Group("Marché") }
    }

    @Test
    fun renderWidgetsNeverDrawsAnOlderReadOverTheSync() = runTest {
        val fnac = cards.save(testCard("Fnac"))
        val courses = (groups.create("Courses") as GroupNameResult.Saved).id
        groups.setMembership(fnac, courses, member = true)
        widgets.ids = intArrayOf(7)
        val sync = started()
        eventually { widgets.renders.isNotEmpty() }

        // As on RESTORED: onUpdate's render reads the sources while onRestored moves them.
        sourcesData.holdNextRead()
        val rendering = launch(Dispatchers.Default) { sync.renderWidgets(intArrayOf(7)) }
        sourcesData.readHeld.await()
        sources.set(7, WidgetSource.Group(courses))
        // A sync free to draw the group now would then see renderWidgets draw its older read over it.
        // A sync that waits for renderWidgets never draws it here: the wait runs out.
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(500) { while (lastTitle() != WidgetTitle.Group("Courses")) delay(10) }
        }
        sourcesData.releaseRead.complete(Unit)
        rendering.join()

        eventually { lastTitle() == WidgetTitle.Group("Courses") }
    }

    @Test
    fun theSyncResubscribesAfterAnUpstreamError() = runTest {
        cards.save(testCard("Fnac", isFavorite = true))
        widgets.ids = intArrayOf(7)
        val flaky = FailOnceDataStore(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "f.preferences_pb") })
        val sync = LauncherSync(
            cards, groups, settings, WidgetSourceStore(flaky), shortcuts, widgets, cardCollator(Locale.FRANCE),
            firstRetryDelayMs = 10,
        )

        sync.start(scope)

        eventually { shortcuts.published.isNotEmpty() && widgets.renders.isNotEmpty() }
        assertEquals(1, flaky.failures.get())
        assertEquals(listOf("Fnac"), shortcuts.published.last().map { it.storeName })
    }

    @Test
    fun cancellingTheSyncJobDuringTheBackoffStopsTheRetry() = runTest {
        val flaky = FailOnceDataStore(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "f.preferences_pb") })
        val sync = LauncherSync(
            cards, groups, settings, WidgetSourceStore(flaky), shortcuts, widgets, cardCollator(Locale.FRANCE),
            firstRetryDelayMs = 1_000,
        )
        val job = sync.start(scope)
        eventually { flaky.failures.get() == 1 }

        job.cancel()
        // Real time: the backoff runs on Dispatchers.Default, not on the test scheduler.
        withContext(Dispatchers.Default) { delay(1_500) }

        assertEquals(emptyList<List<LauncherCard>>(), shortcuts.published.toList())
    }

    @Test
    fun theBackoffDoublesUpToItsCapAndRestartsAfterASuccessfulPush() = runTest {
        cards.save(testCard("Fnac", isFavorite = true))
        val failAgain = CompletableDeferred<Unit>()
        val store = ScriptedDataStore(
            PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "f.preferences_pb") }, 4, failAgain,
        )
        val delays = CopyOnWriteArrayList<Long>()
        val sync = LauncherSync(
            cards, groups, settings, WidgetSourceStore(store), shortcuts, widgets, cardCollator(Locale.FRANCE),
            firstRetryDelayMs = 1, maxRetryDelayMs = 4, retryDelay = { delays += it },
        )

        sync.start(scope)
        eventually { shortcuts.published.isNotEmpty() }
        // Let the push finish, and the backoff reset with it, before the source fails again.
        withContext(Dispatchers.Default) { delay(200) }
        failAgain.complete(Unit)

        eventually { delays.size == 5 }
        assertEquals(listOf(1L, 2L, 4L, 4L, 1L), delays.toList())
    }
}
