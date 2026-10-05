package io.github.vferries.encarte.core.nfc

import android.app.Activity
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNfcAdapter

@RunWith(AndroidJUnit4::class)
class NfcContactlessGuardTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun adapter(enabled: Boolean): NfcAdapter {
        ShadowNfcAdapter.setNfcHardwareExists(true)
        shadowOf(activity.packageManager).setSystemFeature(PackageManager.FEATURE_NFC, true)
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        Shadow.extract<ShadowNfcAdapter>(adapter).setEnabled(enabled)
        return adapter
    }

    @Test
    fun entersReaderModeWhenNfcIsOnAndLeavesItOnRelease() {
        val adapter = adapter(enabled = true)
        val guard = NfcContactlessGuard(adapter)

        assertTrue(guard.block(activity))
        assertTrue(Shadow.extract<ShadowNfcAdapter>(adapter).isInReaderMode)

        guard.release(activity)
        assertFalse(Shadow.extract<ShadowNfcAdapter>(adapter).isInReaderMode)
    }

    @Test
    fun nfcTurnedOffBlocksNothing() {
        val adapter = adapter(enabled = false)

        assertFalse(NfcContactlessGuard(adapter).block(activity))
        assertFalse(Shadow.extract<ShadowNfcAdapter>(adapter).isInReaderMode)
    }

    @Test
    fun deviceWithoutNfcBlocksNothing() {
        assertFalse(NfcContactlessGuard(adapter = null).block(activity))
    }
}
