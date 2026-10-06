package io.github.vferries.encarte

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import io.github.vferries.encarte.core.ui.theme.EncarteTheme
import io.github.vferries.encarte.lock.AppLockGate
import io.github.vferries.encarte.lock.DeviceAuthenticator
import io.github.vferries.encarte.navigation.EncarteNavHost

// FragmentActivity (not ComponentActivity): androidx.biometric 1.1.0's BiometricPrompt requires it.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as EncarteApp).container
        val authenticator = DeviceAuthenticator(this)
        setContent {
            EncarteTheme {
                AppLockGate(container.lockManager, container.settingsRepository, authenticator::authenticate) {
                    EncarteNavHost(container)
                }
            }
        }
    }
}
