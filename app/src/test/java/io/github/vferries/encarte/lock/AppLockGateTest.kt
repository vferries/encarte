package io.github.vferries.encarte.lock

import android.app.KeyguardManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppLockGateTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lockScope = MainScope()
    private val settings by lazy {
        SettingsRepository(PreferenceDataStoreFactory.create(scope = dataStoreScope) { File(tmp.root, "s.preferences_pb") })
    }
    private val prompts = mutableListOf<String>()

    @After
    fun tearDown() {
        lockScope.cancel()
        dataStoreScope.cancel()
    }

    private fun setGate(deviceSecure: Boolean, unlockOnPrompt: Boolean = false) {
        runBlocking { settings.setLockEnabled(true) }
        shadowOf(composeRule.activity.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(deviceSecure)
        val lockManager = LockManager(lockScope, settings.lockEnabled, elapsedRealtime = { 0L })
        composeRule.setContent {
            AppLockGate(
                lockManager,
                settings,
                authenticate = { title, onSuccess ->
                    prompts += title
                    if (unlockOnPrompt) onSuccess()
                },
            ) { Text("Secret content") }
        }
    }

    @Test
    fun lockedContentStaysHiddenAndTheUserIsPrompted() {
        setGate(deviceSecure = true)

        composeRule.onNodeWithText("Encarté is locked").assertIsDisplayed()
        composeRule.onNodeWithText("Secret content").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(listOf("Unlock Encarté"), prompts) }
    }

    @Test
    fun unlockingShowsTheContent() {
        setGate(deviceSecure = true, unlockOnPrompt = true)

        composeRule.onNodeWithText("Secret content").assertIsDisplayed()
    }

    @Test
    fun backWhileLockedSendsTheAppToTheBackground() {
        setGate(deviceSecure = true)
        composeRule.onNodeWithText("Encarté is locked").assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.runOnIdle {
            assertTrue(shadowOf(composeRule.activity).isTaskMovedToBack)
            assertFalse(composeRule.activity.isFinishing)
        }
    }

    @Test
    fun aLockWithoutScreenLockTurnsItselfOff() {
        setGate(deviceSecure = false)

        composeRule.waitUntil(5_000) { runBlocking { !settings.lockEnabled.first() } }
        assertEquals("App lock turned off: this device no longer has a screen lock.", ShadowToast.getTextOfLatestToast())
    }
}
