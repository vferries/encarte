package io.github.vferries.encarte.lock

import android.app.KeyguardManager
import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val TAG = "DeviceAuthenticator"

/** True when a PIN, pattern or password exists, so the prompt can always fall back to it. */
fun Context.isDeviceSecure(): Boolean =
    getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

class DeviceAuthenticator(private val activity: FragmentActivity) {
    fun authenticate(title: String, onSuccess: () -> Unit, onFailure: () -> Unit = {}) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Includes the user cancelling: the lock screen stays and offers to retry.
                Log.i(TAG, "Authentication ended with code $errorCode")
                onFailure()
            }
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            // WEAK|CREDENTIAL works from API 26; STRONG|CREDENTIAL is unsupported on API 28-29.
            // No negative button: it is not allowed together with DEVICE_CREDENTIAL.
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(promptInfo)
    }
}
