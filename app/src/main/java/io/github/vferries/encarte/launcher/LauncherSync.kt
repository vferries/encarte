package io.github.vferries.encarte.launcher

import android.util.Log
import io.github.vferries.encarte.cards.list.cardCollator
import io.github.vferries.encarte.core.data.Card
import io.github.vferries.encarte.core.data.CardGroup
import io.github.vferries.encarte.core.data.CardRepository
import io.github.vferries.encarte.core.data.GroupRepository
import io.github.vferries.encarte.core.prefs.SettingsRepository
import io.github.vferries.encarte.core.prefs.SortOrder
import io.github.vferries.encarte.widget.WidgetSourceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Collator

private const val TAG = "LauncherSync"

/**
 * Keeps the home screen in step with the cards: card shortcuts, pinned shortcuts and every widget.
 * Each output is pushed only when it changed, which keeps clear of Android's shortcut rate limit.
 */
class LauncherSync(
    private val cards: CardRepository,
    private val groups: GroupRepository,
    private val settings: SettingsRepository,
    private val widgetSources: WidgetSourceStore,
    private val shortcuts: ShortcutPublisher,
    private val widgets: WidgetRenderer,
    private val collator: Collator = cardCollator(),
    private val firstRetryDelayMs: Long = 1_000,
    private val maxRetryDelayMs: Long = 60_000,
    // Test seam: lets the tests record the backoff schedule instead of waiting through it.
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var lastDynamic: List<LauncherCard>? = null
    private var lastPinned: PinnedState? = null
    private val lastWidgets = mutableMapOf<Int, WidgetContent>()

    private val inputs: Flow<LauncherInputs> = combine(
        combine(cards.observeCards(), groups.observeGroups(collator), groups.observeMemberships()) { all, groupList, memberships ->
            CardData(all, groupList, memberships)
        },
        settings.lockEnabled,
        settings.sortOrder,
        widgetSources.sources,
    ) { data, locked, order, sources -> LauncherInputs(data, locked, order, sources) }

    /**
     * An upstream failure (database, settings store) must not end the sync for the rest of the process: with the lock
     * on, the home screen would stop hiding store names. The inputs are subscribed again after a growing delay.
     */
    fun start(scope: CoroutineScope): Job = scope.launch {
        var retryDelayMs = firstRetryDelayMs
        inputs
            .retryWhen { e, _ ->
                // The collector's own cancellation never gets here; a CancellationException from upstream does (a
                // cancelled inner job, say). Retrying it could resubscribe in a scope that is going away.
                if (e is CancellationException) {
                    Log.w(TAG, "Home screen sync stopped by an upstream ${e.javaClass.simpleName}")
                    return@retryWhen false
                }
                Log.e(TAG, "Home screen sync failed, retrying in $retryDelayMs ms", e)
                retryDelay(retryDelayMs)
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(maxRetryDelayMs)
                true
            }
            .collect {
                push(it, forcedWidgets = emptySet())
                retryDelayMs = firstRetryDelayMs
            }
    }

    /** Placement, reboot or a new source: these widgets need drawing even if no data changed. */
    suspend fun renderWidgets(appWidgetIds: IntArray) {
        // The widget configuration screen calls this from the main thread: icon bitmaps, the font and the binder
        // calls of a push must not block it.
        withContext(Dispatchers.Default) {
            // Read under the lock: a read made before it could be older than what the sync pushes meanwhile, and would
            // then be drawn over it. The read only queries the database and the stores, never waits on the collector.
            mutex.withLock {
                val current = attempt("home screen data") { inputs.first() } ?: return@withLock
                pushUnlocked(current, forcedWidgets = appWidgetIds.toSet())
            }
        }
    }

    private suspend fun push(inputs: LauncherInputs, forcedWidgets: Set<Int>) = mutex.withLock {
        pushUnlocked(inputs, forcedWidgets)
    }

    private fun pushUnlocked(inputs: LauncherInputs, forcedWidgets: Set<Int>) {
        pushDynamicShortcuts(inputs)
        pushPinnedShortcuts(inputs)
        pushWidgets(inputs, forcedWidgets)
    }

    private fun pushDynamicShortcuts(inputs: LauncherInputs) {
        // cardLimit asks the shortcut service too, so it belongs inside the attempt.
        attempt("card shortcuts") {
            val wanted = if (inputs.locked) emptyList() else shortcutCards(inputs.data.cards, collator, shortcuts.cardLimit)
            if (wanted == lastDynamic) return
            if (shortcuts.publish(wanted)) {
                lastDynamic = wanted
            } else {
                Log.w(TAG, "Android refused the card shortcuts (rate limit): retrying at the next change")
            }
        }
    }

    private fun pushPinnedShortcuts(inputs: LauncherInputs) {
        val wanted = PinnedState(inputs.data.cards.map { it.toLauncherCard() }, inputs.locked)
        if (wanted == lastPinned) return
        attempt("pinned shortcuts") {
            shortcuts.syncPinned(wanted.cards, wanted.locked)
            lastPinned = wanted
        }
    }

    private fun pushWidgets(inputs: LauncherInputs, forced: Set<Int>) {
        val ids = attempt("widget ids") { widgets.widgetIds().toSet() }.orEmpty() + forced
        lastWidgets.keys.retainAll(ids)
        for (id in ids) {
            val content = widgetContent(
                inputs.sources[id], inputs.data.cards, inputs.data.groups, inputs.data.memberships,
                inputs.order, collator, inputs.locked,
            )
            if (id !in forced && lastWidgets[id] == content) continue
            attempt("widget $id") {
                widgets.render(id, content)
                lastWidgets[id] = content
            }
        }
    }

    /** A failed push is logged and retried at the next change: it never stops the sync. */
    private inline fun <T> attempt(what: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Cannot update the $what", e)
        null
    }

    private data class CardData(val cards: List<Card>, val groups: List<CardGroup>, val memberships: Map<Long, Set<Long>>)

    private data class LauncherInputs(
        val data: CardData,
        val locked: Boolean,
        val order: SortOrder,
        val sources: Map<Int, WidgetSource>,
    )

    private data class PinnedState(val cards: List<LauncherCard>, val locked: Boolean)
}
