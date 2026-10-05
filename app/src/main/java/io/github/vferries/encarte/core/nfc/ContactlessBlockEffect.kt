package io.github.vferries.encarte.core.nfc

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect

private const val TAG = "ContactlessBlockEffect"

/** While the screen is resumed and [enabled], a contactless terminal can't start a payment. */
@Composable
fun ContactlessBlockEffect(enabled: Boolean, guard: ContactlessGuard) {
    val activity = LocalActivity.current
    if (activity == null) {
        if (enabled) LaunchedEffect(Unit) { Log.w(TAG, "No activity: contactless payment is not blocked") }
        return
    }
    LifecycleResumeEffect(enabled, guard, activity) {
        val blocked = enabled && guard.block(activity)
        onPauseOrDispose { if (blocked) guard.release(activity) }
    }
}
