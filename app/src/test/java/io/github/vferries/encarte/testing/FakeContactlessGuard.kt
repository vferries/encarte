package io.github.vferries.encarte.testing

import android.app.Activity
import io.github.vferries.encarte.core.nfc.ContactlessGuard

/** [available] = false behaves like a phone without NFC, or with NFC turned off. */
class FakeContactlessGuard(private val available: Boolean = true) : ContactlessGuard {
    var blocked = false
        private set
    var blockCalls = 0
        private set
    var releaseCalls = 0
        private set

    override fun block(activity: Activity): Boolean {
        blockCalls++
        blocked = available
        return available
    }

    override fun release(activity: Activity) {
        releaseCalls++
        blocked = false
    }
}
