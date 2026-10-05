package io.github.vferries.encarte.core.nfc

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.testing.FakeContactlessGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactlessBlockEffectTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun blocksWhileResumedAndReleasesOnPause() {
        val guard = FakeContactlessGuard()
        composeRule.setContent { ContactlessBlockEffect(enabled = true, guard = guard) }
        composeRule.waitForIdle()
        assertTrue(guard.blocked)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        assertFalse(guard.blocked)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        assertTrue(guard.blocked)
    }

    @Test
    fun settingOffNeverBlocks() {
        val guard = FakeContactlessGuard()
        composeRule.setContent { ContactlessBlockEffect(enabled = false, guard = guard) }
        composeRule.waitForIdle()

        assertEquals(0, guard.blockCalls)
    }

    @Test
    fun turningTheSettingOffReleases() {
        val guard = FakeContactlessGuard()
        var enabled by mutableStateOf(true)
        composeRule.setContent { ContactlessBlockEffect(enabled = enabled, guard = guard) }
        composeRule.waitForIdle()
        assertTrue(guard.blocked)

        enabled = false
        composeRule.waitForIdle()

        assertFalse(guard.blocked)
    }

    @Test
    fun withoutNfcNothingIsReleased() {
        val guard = FakeContactlessGuard(available = false)
        composeRule.setContent { ContactlessBlockEffect(enabled = true, guard = guard) }
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)

        assertEquals(1, guard.blockCalls)
        assertEquals(0, guard.releaseCalls)
    }
}
