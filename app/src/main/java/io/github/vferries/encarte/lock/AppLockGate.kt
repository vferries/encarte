package io.github.vferries.encarte.lock

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.prefs.SettingsRepository
import io.github.vferries.encarte.core.ui.SecureWindowEffect
import kotlinx.coroutines.launch
import java.io.IOException

private const val TAG = "AppLockGate"

/** The app lock around [content]: every activity that shows cards or group names goes through it. */
@Composable
fun AppLockGate(
    lockManager: LockManager,
    settings: SettingsRepository,
    authenticate: (title: String, onSuccess: () -> Unit) -> Unit,
    onBackWhileCovered: () -> Unit = moveTaskToBackAction(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lockState by lockManager.state.collectAsStateWithLifecycle()
    val lockEnabled by settings.lockEnabled.collectAsStateWithLifecycle(initialValue = false)
    var deviceSecure by remember { mutableStateOf(context.isDeviceSecure()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { deviceSecure = context.isDeviceSecure() }
    val scope = rememberCoroutineScope()
    val promptTitle = stringResource(R.string.lock_prompt_title)

    SecureWindowEffect(enabled = lockEnabled)
    LockGate(
        state = lockState,
        deviceSecure = deviceSecure,
        onUnlockRequest = { authenticate(promptTitle, lockManager::unlock) },
        onLockUnavailable = {
            Log.w(TAG, "Lock enabled without a device credential: turning it off")
            scope.launch {
                try {
                    settings.setLockEnabled(false)
                } catch (e: IOException) {
                    // Must not crash at every launch: the next launch detects it and tries again.
                    Log.e(TAG, "Cannot turn the lock off", e)
                }
            }
            Toast.makeText(context, R.string.lock_disabled_no_credential, Toast.LENGTH_LONG).show()
        },
        onBackWhileCovered = onBackWhileCovered,
        content = content,
    )
}
