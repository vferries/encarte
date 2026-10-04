package io.github.vferries.encarte.scan

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScannerViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val png = ByteArrayOutputStream().also {
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    @Test
    fun decodedImageBecomesTheResult() = runTest {
        val code = ScannedCode("4006381333931", BarcodeFormat.EAN_13)
        val vm = ScannerViewModel(decodeImage = { code })

        vm.scanImage { png.inputStream() }

        assertEquals(code, vm.uiState.first { it.result != null }.result)
    }

    @Test
    fun imageWithoutBarcodeIsReported() = runTest {
        val vm = ScannerViewModel(decodeImage = { null })

        vm.scanImage { png.inputStream() }

        assertTrue(vm.uiState.first { it.imageNotDecoded }.imageNotDecoded)
    }

    @Test
    fun unreadableFileIsReported() = runTest {
        val vm = ScannerViewModel(decodeImage = { error("must not be called") })

        vm.scanImage { "not an image".byteInputStream() }

        assertTrue(vm.uiState.first { it.imageNotDecoded }.imageNotDecoded)
    }
}
