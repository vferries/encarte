package io.github.vferries.encarte.lock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LockState { LOADING, LOCKED, UNLOCKED }

/**
 * UI lock state. Not thread-safe: call it from the main thread, with a main-thread [scope].
 * The lock only gates the UI; data at rest relies on Android's file-based encryption.
 */
class LockManager(
    scope: CoroutineScope,
    lockEnabled: Flow<Boolean>,
    private val elapsedRealtime: () -> Long,
    private val relockAfterMillis: Long = 60_000,
) {
    private val _state = MutableStateFlow(LockState.LOADING)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var enabled = false
    private var backgroundedAt: Long? = null

    init {
        scope.launch { lockEnabled.collect(::onSettingChanged) }
    }

    private fun onSettingChanged(isEnabled: Boolean) {
        val firstValue = _state.value == LockState.LOADING
        enabled = isEnabled
        _state.value = when {
            firstValue -> if (isEnabled) LockState.LOCKED else LockState.UNLOCKED
            !isEnabled -> LockState.UNLOCKED
            // Enabling the lock (after a successful prompt) must not lock the user out of the session.
            else -> _state.value
        }
    }

    fun onBackground() {
        backgroundedAt = elapsedRealtime()
    }

    fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (enabled && elapsedRealtime() - since > relockAfterMillis) _state.value = LockState.LOCKED
    }

    fun unlock() {
        if (_state.value == LockState.LOCKED) _state.value = LockState.UNLOCKED
    }
}
