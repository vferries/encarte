package io.github.vferries.encarte.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Waits in real time (runTest's virtual clock would skip the delays) for work that Room
 * runs on its own IO threads, e.g. a ViewModel's snapshot state set after a suspend call.
 */
suspend fun eventually(timeoutMs: Long = 5_000, condition: () -> Boolean) = withContext(Dispatchers.Default) {
    withTimeout(timeoutMs) {
        while (!condition()) delay(10)
    }
}
