package io.github.vferries.encarte

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import io.github.vferries.encarte.core.ui.theme.EncarteTheme

// FragmentActivity (not ComponentActivity): androidx.biometric 1.1.0's BiometricPrompt requires it.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EncarteTheme {
                Surface(Modifier.fillMaxSize()) {
                    Text(stringResource(R.string.app_name), Modifier.safeDrawingPadding())
                }
            }
        }
    }
}
