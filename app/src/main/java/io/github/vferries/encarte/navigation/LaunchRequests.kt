package io.github.vferries.encarte.navigation

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.navigation3.runtime.NavKey
import io.github.vferries.encarte.MainActivity

private const val TAG = "LaunchRequests"

/** What the widget and the shortcuts ask MainActivity for. */
object LaunchRequests {
    const val ACTION_VIEW_CARD = "io.github.vferries.encarte.action.VIEW_CARD"
    const val ACTION_ADD_CARD = "io.github.vferries.encarte.action.ADD_CARD"
    const val EXTRA_CARD_ID = "card_id"

    /**
     * Like a fresh launch: launchers already start shortcuts this way, so the widget does the same.
     * An unsaved edit in progress is lost (spec §8).
     */
    fun launch(context: Context, action: String): Intent = Intent(context, MainActivity::class.java)
        .setAction(action)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    fun viewCard(context: Context, cardId: Long): Intent = launch(context, ACTION_VIEW_CARD).putExtra(EXTRA_CARD_ID, cardId)

    fun addCard(context: Context): Intent = launch(context, ACTION_ADD_CARD)

    /** The list is always at the bottom, so Back from a requested screen lands on it. */
    fun backStackFor(intent: Intent?): List<NavKey> {
        // Recents relaunch a finished task with the intent that first started it: that request was already served.
        if (intent != null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return listOf(CardListKey)
        return when (intent?.action) {
            ACTION_VIEW_CARD -> {
                val cardId = intent.getLongExtra(EXTRA_CARD_ID, 0)
                if (cardId > 0) {
                    listOf(CardListKey, CardDisplayKey(cardId))
                } else {
                    Log.w(TAG, "Card request without a valid card id: opening the list")
                    listOf(CardListKey)
                }
            }
            ACTION_ADD_CARD -> listOf(CardListKey, ScannerKey())
            null, Intent.ACTION_MAIN -> listOf(CardListKey)
            else -> {
                // MainActivity is exported: an unknown action can only come from another app.
                Log.w(TAG, "Ignoring unknown launch action ${intent.action}: opening the list")
                listOf(CardListKey)
            }
        }
    }
}
