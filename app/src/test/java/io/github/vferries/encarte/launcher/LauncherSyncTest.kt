package io.github.vferries.encarte.launcher

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
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

private class FakeShortcuts : ShortcutPublisher {
    override val cardLimit = 3
    val published = CopyOnWriteArrayList<List<LauncherCard>>()
    val pinnedSyncs = CopyOnWriteArrayList<Pair<List<LauncherCard>, Boolean>>()

    @Volatile
    var refuseNext = false

    override fun publish(cards: List<LauncherCard>): Boolean {
        published += cards
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

    override fun widgetIds() = ids

    override fun render(appWidgetId: Int, content: WidgetContent) {
        if (failNext) {
            failNext = false
            throw IllegalStateException("launcher gone")
        }
        renders += appWidgetId to content
    }
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
    private val sources by lazy {
        WidgetSourceStore(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "w.preferences_pb") })
    }
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
}
