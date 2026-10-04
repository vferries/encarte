package io.github.vferries.encarte.core.ui

import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

private const val TAG = "WindowEffects"

/** Full screen brightness while in composition, restored afterwards (checkout scanners read better). */
@Composable
fun MaxBrightnessEffect() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        if (window == null) {
            Log.w(TAG, "No activity window: brightness unchanged")
            return@DisposableEffect onDispose {}
        }
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
        }
    }
}

/** Hides the app from recents previews and blocks screenshots while [enabled]. */
@Composable
fun SecureWindowEffect(enabled: Boolean) {
    val window = LocalActivity.current?.window
    DisposableEffect(window, enabled) {
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
