package io.github.vferries.encarte.scan

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vferries.encarte.R
import java.io.IOException

private const val TAG = "ScannerScreen"

enum class CameraPermission { GRANTED, NOT_GRANTED, PERMANENTLY_DENIED }

@Composable
fun ScannerRoute(
    viewModel: ScannerViewModel,
    onBack: () -> Unit,
    onScanned: (ScannedCode) -> Unit,
    onManualEntry: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var permission by remember { mutableStateOf(if (context.hasCameraPermission()) CameraPermission.GRANTED else CameraPermission.NOT_GRANTED) }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAskAgain = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } ?: false
        permission = when {
            granted -> CameraPermission.GRANTED
            canAskAgain -> CameraPermission.NOT_GRANTED
            else -> CameraPermission.PERMANENTLY_DENIED
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            viewModel.scanImage { context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open picked image") }
        }
    }

    LaunchedEffect(Unit) {
        if (permission != CameraPermission.GRANTED) requestPermission.launch(Manifest.permission.CAMERA)
    }
    // The user may grant the permission from the system settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (context.hasCameraPermission()) permission = CameraPermission.GRANTED
    }
    if (permission == CameraPermission.GRANTED) {
        LaunchedEffect(lifecycleOwner) { viewModel.bindToCamera(context.applicationContext, lifecycleOwner) }
    }
    LaunchedEffect(state.result) {
        state.result?.let(onScanned)
    }

    ScannerScreen(
        state = state,
        permission = permission,
        onBack = onBack,
        onRequestPermission = { requestPermission.launch(Manifest.permission.CAMERA) },
        onOpenSettings = { context.openAppSettings() },
        onToggleTorch = { viewModel.setTorch(!state.torchOn) },
        onManualEntry = onManualEntry,
        onPickImage = {
            try {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No photo picker available", e)
            }
        },
    )
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app settings screen", e)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    state: ScannerUiState,
    permission: CameraPermission,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTorch: () -> Unit,
    onManualEntry: () -> Unit,
    onPickImage: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (permission == CameraPermission.GRANTED && state.hasTorch) {
                        IconButton(onClick = onToggleTorch) {
                            Icon(
                                painterResource(if (state.torchOn) R.drawable.ic_flashlight_off else R.drawable.ic_flashlight_on),
                                stringResource(if (state.torchOn) R.string.action_torch_off else R.string.action_torch_on),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                val request = state.surfaceRequest
                when {
                    permission == CameraPermission.NOT_GRANTED -> PermissionMessage(
                        R.string.action_grant_permission, onRequestPermission,
                    )
                    permission == CameraPermission.PERMANENTLY_DENIED -> PermissionMessage(
                        R.string.action_open_settings, onOpenSettings,
                    )
                    state.cameraUnavailable -> Text(
                        stringResource(R.string.camera_unavailable),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                    request != null -> {
                        CameraXViewfinder(surfaceRequest = request, modifier = Modifier.fillMaxSize())
                        Text(
                            stringResource(R.string.scan_hint),
                            color = Color.White,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        )
                    }
                }
            }
            if (state.imageNotDecoded) {
                Text(
                    stringResource(R.string.no_barcode_in_image),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onManualEntry, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_keyboard), contentDescription = null)
                    Text(stringResource(R.string.action_enter_manually), Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onPickImage, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_image), contentDescription = null)
                    Text(stringResource(R.string.action_from_image), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun PermissionMessage(action: Int, onAction: () -> Unit) {
    Column(
        Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.camera_permission_rationale), color = Color.White, textAlign = TextAlign.Center)
        Button(onClick = onAction) { Text(stringResource(action)) }
    }
}
