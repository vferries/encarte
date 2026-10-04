package io.github.vferries.encarte.lock

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

private const val TAG = "LockGate"

/**
 * True while the lock gate covers the content. Dialogs and popups are separate windows drawn above
 * the gate, so every one of them must stay hidden while this is true (their open state is kept).
 */
val LocalContentCovered = compositionLocalOf { false }

/**
 * Keeps [content] composed (pending activity results and the back stack survive a re-lock) but
 * covers it with an opaque, touch-blocking surface and hides it from accessibility services.
 */
@Composable
fun LockGate(
    state: LockState,
    deviceSecure: Boolean,
    onUnlockRequest: () -> Unit,
    onLockUnavailable: () -> Unit,
    content: @Composable () -> Unit,
) {
    val covered = state != LockState.UNLOCKED
    Box(Modifier.fillMaxSize()) {
        Box(if (covered) Modifier.clearAndSetSemantics {} else Modifier) {
            CompositionLocalProvider(LocalContentCovered provides covered) { content() }
        }
        when {
            state == LockState.LOCKED && deviceSecure -> LockScreen(onUnlock = onUnlockRequest)
            // Loading the setting, or about to turn the lock off: cover without prompting.
            covered -> Surface(Modifier.fillMaxSize()) {}
        }
    }
    if (covered) {
        // Composed after the content's handlers, so it wins: Back must not act on the hidden screens.
        val activity = LocalActivity.current
        BackHandler {
            if (activity != null) activity.moveTaskToBack(true) else Log.w(TAG, "No activity to send to back")
        }
    }
    if (state == LockState.LOCKED && !deviceSecure) {
        // The screen lock was removed after enabling ours: no credential could ever unlock the app.
        LaunchedEffect(Unit) { onLockUnavailable() }
    }
}
