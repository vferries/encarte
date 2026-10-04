package io.github.vferries.encarte.lock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

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
        Box(if (covered) Modifier.clearAndSetSemantics {} else Modifier) { content() }
        when {
            state == LockState.LOCKED && deviceSecure -> LockScreen(onUnlock = onUnlockRequest)
            // Loading the setting, or about to turn the lock off: cover without prompting.
            covered -> Surface(Modifier.fillMaxSize()) {}
        }
    }
    if (state == LockState.LOCKED && !deviceSecure) {
        // The screen lock was removed after enabling ours: no credential could ever unlock the app.
        LaunchedEffect(Unit) { onLockUnavailable() }
    }
}
