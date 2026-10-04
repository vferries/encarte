package io.github.vferries.encarte.lock

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockManagerTest {
    private var now = 0L
    private val setting = MutableStateFlow(false)

    private fun TestScope.manager(enabled: Boolean): LockManager {
        setting.value = enabled
        return LockManager(backgroundScope, setting, elapsedRealtime = { now }).also { runCurrent() }
    }

    @Test
    fun startsLoadingUntilSettingIsKnown() = runTest {
        val manager = LockManager(backgroundScope, setting, elapsedRealtime = { now })

        assertEquals(LockState.LOADING, manager.state.value)
    }

    @Test
    fun disabledStartsUnlocked() = runTest {
        assertEquals(LockState.UNLOCKED, manager(enabled = false).state.value)
    }

    @Test
    fun enabledStartsLockedAndUnlocks() = runTest {
        val manager = manager(enabled = true)
        assertEquals(LockState.LOCKED, manager.state.value)

        manager.unlock()

        assertEquals(LockState.UNLOCKED, manager.state.value)
    }

    @Test
    fun shortBackgroundKeepsSessionUnlocked() = runTest {
        val manager = manager(enabled = true).apply { unlock() }

        manager.onBackground()
        now += 30_000
        manager.onForeground()

        assertEquals(LockState.UNLOCKED, manager.state.value)
    }

    @Test
    fun longBackgroundRelocks() = runTest {
        val manager = manager(enabled = true).apply { unlock() }

        manager.onBackground()
        now += 61_000
        manager.onForeground()

        assertEquals(LockState.LOCKED, manager.state.value)
    }

    @Test
    fun disabledNeverRelocks() = runTest {
        val manager = manager(enabled = false)

        manager.onBackground()
        now += 3_600_000
        manager.onForeground()

        assertEquals(LockState.UNLOCKED, manager.state.value)
    }

    @Test
    fun enablingWhileInUseKeepsSessionUnlocked() = runTest {
        val manager = manager(enabled = false)

        setting.value = true
        runCurrent()

        assertEquals(LockState.UNLOCKED, manager.state.value)
    }

    @Test
    fun disablingWhileLockedUnlocks() = runTest {
        val manager = manager(enabled = true)

        setting.value = false
        runCurrent()

        assertEquals(LockState.UNLOCKED, manager.state.value)
    }
}
