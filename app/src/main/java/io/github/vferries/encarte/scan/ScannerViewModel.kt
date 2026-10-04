package io.github.vferries.encarte.scan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import io.github.vferries.encarte.core.ui.decodeSampledBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "ScannerViewModel"
private const val GALLERY_MAX_SIDE = 2048

data class ScannerUiState(
    val surfaceRequest: SurfaceRequest? = null,
    val hasTorch: Boolean = false,
    val torchOn: Boolean = false,
    val cameraUnavailable: Boolean = false,
    val result: ScannedCode? = null,
    val imageNotDecoded: Boolean = false,
)

class ScannerViewModel(
    private val decodeImage: (Bitmap) -> ScannedCode? = { BarcodeScanner().scan(it) },
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var cameraControl: CameraControl? = null
    private var analysisExecutor: ExecutorService? = null

    /** Binds preview and analysis until the calling coroutine is cancelled (the screen leaves). */
    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        analysisExecutor?.shutdown()
        val executor = Executors.newSingleThreadExecutor().also { analysisExecutor = it }
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request -> _uiState.update { it.copy(surfaceRequest = request) } }
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
            .build()
        // Created lazily on the analysis thread: the reader is not thread-safe and loads native code.
        val scanner by lazy { BarcodeScanner() }
        analysis.setAnalyzer(executor) { image ->
            image.use { frame ->
                if (_uiState.value.result == null) {
                    scanner.scan(frame)?.let { found ->
                        _uiState.update { if (it.result == null) it.copy(result = found) else it }
                    }
                }
            }
        }

        val provider = ProcessCameraProvider.awaitInstance(appContext)
        val camera = try {
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "No usable back camera", e)
            _uiState.update { it.copy(cameraUnavailable = true) }
            analysis.clearAnalyzer()
            return
        }
        cameraControl = camera.cameraControl
        _uiState.update { it.copy(hasTorch = camera.cameraInfo.hasFlashUnit()) }
        try {
            camera.cameraInfo.torchState.asFlow().collect { torch ->
                _uiState.update { it.copy(torchOn = torch == TorchState.ON) }
            }
        } finally {
            provider.unbind(preview, analysis)
            analysis.clearAnalyzer()
            cameraControl = null
            _uiState.update { it.copy(surfaceRequest = null) }
        }
    }

    fun setTorch(on: Boolean) {
        val future = cameraControl?.enableTorch(on) ?: return
        future.addListener({
            try {
                future.get()
            } catch (e: ExecutionException) {
                // Fails when the camera closes mid-call; the torch state flow stays the source of truth.
                Log.w(TAG, "enableTorch($on) failed", e)
            }
        }, Runnable::run)
    }

    fun scanImage(open: () -> InputStream) {
        _uiState.update { it.copy(imageNotDecoded = false) }
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                try {
                    decodeSampledBitmap(GALLERY_MAX_SIDE, open)?.let(decodeImage)
                } catch (e: IOException) {
                    Log.w(TAG, "Cannot read picked image", e)
                    null
                }
            }
            _uiState.update { if (found != null) it.copy(result = found) else it.copy(imageNotDecoded = true) }
        }
    }

    override fun onCleared() {
        analysisExecutor?.shutdown()
    }
}
