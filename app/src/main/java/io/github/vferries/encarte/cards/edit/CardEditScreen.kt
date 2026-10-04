package io.github.vferries.encarte.cards.edit

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.barcode.BarcodeError
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import io.github.vferries.encarte.core.color.CardPalette
import io.github.vferries.encarte.core.data.CardSide
import io.github.vferries.encarte.core.ui.BarcodeImage
import io.github.vferries.encarte.core.ui.CARD_ASPECT_RATIO
import io.github.vferries.encarte.core.ui.rememberImageBitmap
import io.github.vferries.encarte.lock.LocalContentCovered
import java.io.File
import java.io.IOException

private const val TAG = "CardEditScreen"
private const val PHOTO_MAX_SIDE = 480

@Composable
fun CardEditRoute(viewModel: CardEditViewModel, onSaved: (cardId: Long, isNew: Boolean) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var pendingSide by rememberSaveable { mutableStateOf<CardSide?>(null) }
    var photoNotice by remember { mutableStateOf<Int?>(null) }
    val captureFile = remember { File(context.cacheDir, "camera/capture.jpg") }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val side = pendingSide
        pendingSide = null
        if (uri != null && side != null) {
            viewModel.onImagePicked(side) {
                context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open picked image")
            }
        }
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val side = pendingSide
        pendingSide = null
        if (success && side != null) {
            // The capture is a copy of the photo outside the image store: don't leave it behind.
            viewModel.onImagePicked(side, onFinished = { deleteCapture(captureFile) }) { captureFile.inputStream() }
        }
    }
    val launchCamera = {
        launchSafely({ photoNotice = R.string.photo_error }) { takePicture.launch(captureUri(context, captureFile)) }
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else {
            pendingSide = null
            photoNotice = R.string.camera_permission_needed_for_photo
        }
    }

    LaunchedEffect(viewModel.savedCardId) {
        viewModel.savedCardId?.let { onSaved(it, viewModel.isNew) }
    }

    CardEditScreen(
        viewModel = viewModel,
        onClose = onClose,
        photoNotice = photoNotice,
        onPickImage = { side ->
            pendingSide = side
            photoNotice = null
            viewModel.dismissImageError()
            launchSafely({ photoNotice = R.string.photo_error }) {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        },
        onTakePhoto = { side ->
            pendingSide = side
            photoNotice = null
            viewModel.dismissImageError()
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) launchCamera() else requestCamera.launch(Manifest.permission.CAMERA)
        },
    )
}

private fun captureUri(context: Context, file: File): Uri {
    file.parentFile?.mkdirs()
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun deleteCapture(file: File) {
    if (!file.delete() && file.exists()) Log.w(TAG, "Cannot delete the camera capture")
}

private inline fun launchSafely(onFailure: () -> Unit, launch: () -> Unit) {
    try {
        launch()
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No activity can handle the request", e)
        onFailure()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditScreen(
    viewModel: CardEditViewModel,
    onClose: () -> Unit,
    onPickImage: (CardSide) -> Unit,
    onTakePhoto: (CardSide) -> Unit,
    photoNotice: Int? = null,
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val close = {
        if (viewModel.hasChanges) {
            confirmDiscard = true
        } else {
            viewModel.discard()
            onClose()
        }
    }
    // Always enabled: hasChanges is read when back is pressed, not at the last recomposition,
    // so a back press in the same frame as a keystroke can't skip the confirmation.
    BackHandler { close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (viewModel.isNew) R.string.edit_title_new else R.string.edit_title_existing))
                },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = viewModel.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        if (!viewModel.isLoading) {
            Form(
                viewModel, onPickImage, onTakePhoto, photoNotice,
                Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
            )
        }
    }

    if (confirmDiscard && !LocalContentCovered.current) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.discard()
                    onClose()
                }) { Text(stringResource(R.string.discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }
}

@Composable
private fun Form(
    viewModel: CardEditViewModel,
    onPickImage: (CardSide) -> Unit,
    onTakePhoto: (CardSide) -> Unit,
    photoNotice: Int?,
    modifier: Modifier = Modifier,
) {
    Column(
        // Large screens: cap the content width instead of stretching the form.
        modifier = modifier.fillMaxSize().wrapContentWidth().widthIn(max = 640.dp)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (viewModel.showUnsupportedFormatNotice) {
            Text(
                stringResource(R.string.unsupported_format_notice),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedTextField(
            state = viewModel.storeName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_store_name)) },
            supportingText = supportingText(R.string.field_required_hint.takeIf { viewModel.storeName.text.isBlank() }),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        val suggestions = viewModel.suggestions
        if (suggestions.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { brand ->
                    SuggestionChip(onClick = { viewModel.selectSuggestion(brand) }, label = { Text(brand.name) })
                }
            }
        }
        val numberError = viewModel.barcodeError.takeUnless { viewModel.differentEncodedValue }
        OutlinedTextField(
            state = viewModel.cardNumber,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_card_number)) },
            isError = numberError != null,
            supportingText = supportingText(
                numberError?.message ?: R.string.field_required_hint.takeIf { viewModel.cardNumber.text.isBlank() }
            ),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        FormatField(viewModel.barcodeFormat, viewModel::selectFormat)
        if (viewModel.barcodeFormat != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.field_encoded_value_toggle), Modifier.weight(1f))
                Switch(checked = viewModel.differentEncodedValue, onCheckedChange = viewModel::setDifferentEncodedValue)
            }
        }
        if (viewModel.differentEncodedValue && viewModel.barcodeFormat != null) {
            val error = viewModel.barcodeError
            OutlinedTextField(
                state = viewModel.barcodeValue,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_encoded_value)) },
                isError = error != null,
                supportingText = supportingText(
                    error?.message ?: R.string.field_required_hint.takeIf { viewModel.barcodeValue.text.isBlank() }
                ),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        }
        val preview = viewModel.previewValue
        val format = viewModel.barcodeFormat
        if (preview != null && format != null) {
            BarcodeImage(preview, format, Modifier.fillMaxWidth())
        }
        Text(stringResource(R.string.field_color), style = MaterialTheme.typography.titleSmall)
        ColorSwatches(viewModel.color, viewModel::selectColor)
        OutlinedTextField(
            state = viewModel.note,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_note)) },
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2),
        )
        Text(stringResource(R.string.photos_title), style = MaterialTheme.typography.titleSmall)
        if (viewModel.imageError || photoNotice != null) {
            Text(stringResource(photoNotice ?: R.string.photo_error), color = MaterialTheme.colorScheme.error)
        }
        PhotoSlot(R.string.photo_front, viewModel.frontImage?.let(viewModel::imageFile), CardSide.FRONT, viewModel, onPickImage, onTakePhoto)
        PhotoSlot(R.string.photo_back, viewModel.backImage?.let(viewModel::imageFile), CardSide.BACK, viewModel, onPickImage, onTakePhoto)
    }
}

/** A declared return type lets the lambda be inferred as @Composable. */
private fun supportingText(message: Int?): (@Composable () -> Unit)? {
    if (message == null) return null
    return { Text(stringResource(message)) }
}

private val BarcodeError.message: Int
    get() = when (this) {
        BarcodeError.EMPTY -> R.string.field_required_hint
        BarcodeError.INVALID_CHARACTERS -> R.string.error_barcode_characters
        BarcodeError.INVALID_LENGTH -> R.string.error_barcode_length
        BarcodeError.INVALID_CHECK_DIGIT -> R.string.error_barcode_check_digit
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatField(selected: BarcodeFormat?, onSelect: (BarcodeFormat?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val noneLabel = stringResource(R.string.barcode_format_none)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.label ?: noneLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.field_barcode_format)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        if (!LocalContentCovered.current) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                (listOf<BarcodeFormat?>(null) + BarcodeFormat.entries).forEach { format ->
                    DropdownMenuItem(
                        text = { Text(format?.label ?: noneLabel) },
                        onClick = {
                            expanded = false
                            onSelect(format)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatches(current: Int, onSelect: (Int) -> Unit) {
    // A brand color is usually not a swatch: show it first so the current choice is always visible.
    val options = if (current in CardPalette.swatches) CardPalette.swatches else listOf(current) + CardPalette.swatches
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, argb ->
            val description = stringResource(R.string.color_swatch_description, index + 1)
            val selected = argb == current
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(argb) })
                    .semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun PhotoSlot(
    label: Int,
    file: File?,
    side: CardSide,
    viewModel: CardEditViewModel,
    onPickImage: (CardSide) -> Unit,
    onTakePhoto: (CardSide) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val image = rememberImageBitmap(file, PHOTO_MAX_SIDE)
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(label),
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(120.dp).aspectRatio(CARD_ASPECT_RATIO).clip(RoundedCornerShape(8.dp)),
            )
            TextButton(onClick = { viewModel.removeImage(side) }) { Text(stringResource(R.string.photo_remove)) }
        } else {
            Text(stringResource(label), Modifier.width(80.dp))
            IconButton(onClick = { onTakePhoto(side) }) {
                Icon(painterResource(R.drawable.ic_photo_camera), stringResource(R.string.photo_take))
            }
            OutlinedButton(onClick = { onPickImage(side) }, shape = MaterialTheme.shapes.small) {
                Icon(painterResource(R.drawable.ic_image), contentDescription = null)
                Text(stringResource(R.string.photo_pick), Modifier.padding(start = 8.dp))
            }
        }
    }
}
