package io.github.vferries.encarte

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vferries.encarte.core.ui.SecureWindowEffect
import io.github.vferries.encarte.core.ui.theme.EncarteTheme
import io.github.vferries.encarte.lock.DeviceAuthenticator
import io.github.vferries.encarte.lock.LockGate
import io.github.vferries.encarte.lock.isDeviceSecure
import io.github.vferries.encarte.navigation.EncarteNavHost
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

// FragmentActivity (not ComponentActivity): androidx.biometric 1.1.0's BiometricPrompt requires it.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EncarteApp).container
        val authenticator = DeviceAuthenticator(this)
        setContent {
            EncarteTheme {
                val lockState by container.lockManager.state.collectAsStateWithLifecycle()
                val lockEnabled by container.settingsRepository.lockEnabled.collectAsStateWithLifecycle(initialValue = false)
                var deviceSecure by remember { mutableStateOf(isDeviceSecure()) }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { deviceSecure = isDeviceSecure() }
                val scope = rememberCoroutineScope()
                val promptTitle = stringResource(R.string.lock_prompt_title)

                SecureWindowEffect(enabled = lockEnabled)
                LockGate(
                    state = lockState,
                    deviceSecure = deviceSecure,
                    onUnlockRequest = { authenticator.authenticate(promptTitle, onSuccess = container.lockManager::unlock) },
                    onLockUnavailable = {
                        Log.w(TAG, "Lock enabled without a device credential: turning it off")
                        scope.launch { container.settingsRepository.setLockEnabled(false) }
                        Toast.makeText(this, R.string.lock_disabled_no_credential, Toast.LENGTH_LONG).show()
                    },
                ) {
                    EncarteNavHost(container)
                }
            }
        }
    }
}
