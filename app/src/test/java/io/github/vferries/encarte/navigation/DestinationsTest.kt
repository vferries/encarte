package io.github.vferries.encarte.navigation

import android.os.Parcel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.importing.CardDraft
import io.github.vferries.encarte.importing.DraftNotice
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The back stack survives process death only if every key goes through Navigation 3's serializer and a Parcel. */
@RunWith(AndroidJUnit4::class)
class DestinationsTest {
    private fun afterProcessDeath(key: NavKey): NavKey {
        val state = encodeToSavedState(NavKeySerializer(), key)
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(state)
            parcel.setDataPosition(0)
            return decodeFromSavedState(NavKeySerializer(), parcel.readBundle(javaClass.classLoader)!!)
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun anEditorKeyKeepsItsDraft() {
        val key = CardEditKey(
            groupId = 3,
            draft = CardDraft(
                storeName = "Cinéma", cardNumber = "A-42", barcodeValue = "X-42", barcodeFormat = BarcodeFormat.QR_CODE,
                color = -16777216, expiresOnEpochDay = 20_524, note = "Séance", notice = DraftNotice.PASS_WITHOUT_BARCODE,
            ),
        )

        assertEquals(key, afterProcessDeath(key))
    }
}
