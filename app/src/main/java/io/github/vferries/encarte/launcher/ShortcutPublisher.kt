package io.github.vferries.encarte.launcher

import android.content.Context
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.vferries.encarte.R
import io.github.vferries.encarte.navigation.LaunchRequests

private const val TAG = "ShortcutPublisher"
private const val CARD_PREFIX = "card:"

/** Three cards and "Add a card": what most launchers show in the long-press menu. */
private const val MAX_CARD_SHORTCUTS = 3

/** shortcuts.xml declares one shortcut: "Add a card". */
private const val STATIC_SHORTCUTS = 1

interface ShortcutPublisher {
    /** How many card shortcuts the launcher menu can take next to the static ones. */
    val cardLimit: Int

    /** Replaces the dynamic card shortcuts; false when Android refused (rate limit). */
    fun publish(cards: List<LauncherCard>): Boolean

    /** Pinned card shortcuts can't be removed: they read "Encarté" while [locked], and are disabled once their card is gone. */
    fun syncPinned(cards: List<LauncherCard>, locked: Boolean)
}

fun cardShortcutId(cardId: Long): String = CARD_PREFIX + cardId

internal data class PinnedChanges(val disable: List<String>, val update: List<LauncherCard>)

internal fun pinnedChanges(pinnedIds: List<String>, cards: List<LauncherCard>): PinnedChanges {
    val byId = cards.associateBy { cardShortcutId(it.id) }
    val cardIds = pinnedIds.filter { it.startsWith(CARD_PREFIX) }
    return PinnedChanges(disable = cardIds.filterNot { it in byId }, update = cardIds.mapNotNull { byId[it] })
}

class AndroidShortcutPublisher(private val context: Context) : ShortcutPublisher {
    override val cardLimit: Int
        get() = minOf(MAX_CARD_SHORTCUTS, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) - STATIC_SHORTCUTS)

    override fun publish(cards: List<LauncherCard>): Boolean =
        ShortcutManagerCompat.setDynamicShortcuts(context, cards.mapIndexed { rank, card -> cardShortcut(card, rank) })

    override fun syncPinned(cards: List<LauncherCard>, locked: Boolean) {
        val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED).map { it.id }
        val changes = pinnedChanges(pinned, cards)
        if (changes.disable.isNotEmpty()) {
            ShortcutManagerCompat.disableShortcuts(context, changes.disable, context.getString(R.string.shortcut_card_deleted))
        }
        val updates = if (locked) {
            changes.update.map(::lockedShortcut)
        } else {
            // A pinned menu entry is also dynamic: publish already updated it, and rebuilding it here would reset its
            // rank, which orders the menu.
            val dynamic = ShortcutManagerCompat.getDynamicShortcuts(context).mapTo(mutableSetOf()) { it.id }
            changes.update.filterNot { cardShortcutId(it.id) in dynamic }.map { cardShortcut(it, rank = 0) }
        }
        if (updates.isEmpty()) return
        if (!ShortcutManagerCompat.updateShortcuts(context, updates)) {
            Log.w(TAG, "Android refused the pinned shortcuts update (rate limit)")
        }
    }

    private fun cardShortcut(card: LauncherCard, rank: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, cardShortcutId(card.id))
            .setShortLabel(card.storeName)
            .setLongLabel(card.storeName)
            .setIcon(IconCompat.createWithAdaptiveBitmap(cardIconBitmap(context, card)))
            .setIntent(LaunchRequests.viewCard(context, card.id))
            .setRank(rank)
            .build()

    /** Still opens the card, behind the lock, but shows nothing of it. */
    private fun lockedShortcut(card: LauncherCard): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, cardShortcutId(card.id))
            .setShortLabel(context.getString(R.string.app_name))
            .setLongLabel(context.getString(R.string.app_name))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(LaunchRequests.viewCard(context, card.id))
            .build()
}
