package io.github.vferries.encarte.core.nfc

import android.app.Activity
import android.nfc.NfcAdapter

/** Keeps a contactless terminal from reaching the phone's payment app while a card is shown. */
interface ContactlessGuard {
    /** Returns false when there is nothing to block: no NFC, or NFC turned off. */
    fun block(activity: Activity): Boolean

    /** Only called after [block] returned true. */
    fun release(activity: Activity)
}

/**
 * Reader mode turns card emulation off while the activity is in the foreground, so a terminal
 * can't reach the payment app. The tags it reads meanwhile are ignored.
 */
class NfcContactlessGuard(private val adapter: NfcAdapter?) : ContactlessGuard {
    override fun block(activity: Activity): Boolean {
        val nfc = adapter?.takeIf { it.isEnabled } ?: return false
        nfc.enableReaderMode(activity, IgnoreTags, READER_FLAGS, null)
        return true
    }

    override fun release(activity: Activity) {
        adapter?.disableReaderMode(activity)
    }

    private companion object {
        val IgnoreTags = NfcAdapter.ReaderCallback { }

        const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NFC_BARCODE or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
    }
}
