package io.github.vferries.encarte.cards.display

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.data.Card
import io.github.vferries.encarte.core.data.ExpiryStatus
import io.github.vferries.encarte.core.data.encodedValue
import io.github.vferries.encarte.core.nfc.ContactlessBlockEffect
import io.github.vferries.encarte.core.nfc.ContactlessGuard
import io.github.vferries.encarte.core.ui.BarcodeImage
import io.github.vferries.encarte.core.ui.CARD_ASPECT_RATIO
import io.github.vferries.encarte.core.ui.EncarteAlertDialog
import io.github.vferries.encarte.core.ui.EncarteDialog
import io.github.vferries.encarte.core.ui.EncarteDropdownMenu
import io.github.vferries.encarte.core.ui.MaxBrightnessEffect
import io.github.vferries.encarte.core.ui.displayAspectRatio
import io.github.vferries.encarte.core.ui.rememberImageBitmap
import io.github.vferries.encarte.core.ui.rememberMediumDateFormatter
import java.io.File
import java.time.LocalDate

private const val TAG = "CardDisplayScreen"
private const val THUMBNAIL_MAX_SIDE = 480
private const val FULL_SCREEN_MAX_SIDE = 2048

/** Vertical padding (32), spacing (16) and the card number line (about 32) kept on screen below the code. */
private val BarcodeReservedHeight = 80.dp
private val BarcodeMinHeight = 120.dp

@Composable
fun CardDisplayRoute(
    viewModel: CardDisplayViewModel,
    contactlessGuard: ContactlessGuard,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onArchived: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ContactlessBlockEffect(enabled = state.blockContactless && state.card != null, guard = contactlessGuard)
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onBack()
    }
    LaunchedEffect(state.justArchived) {
        if (state.justArchived) {
            state.card?.let { onArchived(it.id) } ?: Log.w(TAG, "Archived card vanished before leaving its screen")
        }
    }
    CardDisplayScreen(
        state = state,
        onBack = onBack,
        onEdit = { state.card?.let { onEdit(it.id) } },
        onToggleFavorite = viewModel::toggleFavorite,
        onDelete = viewModel::delete,
        onToggleArchive = viewModel::toggleArchived,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDisplayScreen(
    state: CardDisplayUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onToggleArchive: () -> Unit,
) {
    val card = state.card
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card?.storeName.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (card != null) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                painterResource(if (card.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star),
                                stringResource(if (card.isFavorite) R.string.action_unfavorite else R.string.action_favorite),
                            )
                        }
                        IconButton(onClick = onEdit) {
                            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.action_edit))
                        }
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more))
                            }
                            EncarteDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(if (card.isArchived) R.string.action_unarchive else R.string.action_archive))
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painterResource(if (card.isArchived) R.drawable.ic_unarchive else R.drawable.ic_archive),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onToggleArchive()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.padding(padding))
            card == null -> Text(
                stringResource(R.string.card_not_found),
                modifier = Modifier.padding(padding).padding(24.dp),
            )
            else -> CardContent(state, card, Modifier.padding(padding))
        }
    }
    if (confirmDelete && card != null) {
        EncarteAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body, card.storeName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun CardContent(state: CardDisplayUiState, card: Card, modifier: Modifier = Modifier) {
    MaxBrightnessEffect()
    var fullScreenImage by remember { mutableStateOf<File?>(null) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // In landscape a square code as wide as the content would be taller than the screen.
        val barcodeMaxHeight = (maxHeight - BarcodeReservedHeight).coerceAtLeast(BarcodeMinHeight)
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Large screens: cap the content width instead of stretching the layout.
                .wrapContentWidth()
                .widthIn(max = 640.dp)
                .keepScreenOn()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val format = card.barcodeFormat
            if (format != null) {
                BarcodeImage(
                    card.encodedValue,
                    format,
                    Modifier.widthIn(max = barcodeMaxHeight * format.displayAspectRatio).fillMaxWidth(),
                )
            }
            SelectionContainer {
                Text(
                    text = card.cardNumber,
                    style = if (format == null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
            card.expiresOn?.let { date -> ExpiryLine(date, expired = state.expiry == ExpiryStatus.Expired) }
            if (card.note.isNotBlank()) {
                Text(card.note, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                state.frontImage?.let { file ->
                    Thumbnail(file, stringResource(R.string.photo_front), Modifier.weight(1f)) { fullScreenImage = file }
                }
                state.backImage?.let { file ->
                    Thumbnail(file, stringResource(R.string.photo_back), Modifier.weight(1f)) { fullScreenImage = file }
                }
            }
        }
    }
    val shownImage = fullScreenImage
    if (shownImage != null) FullScreenImage(shownImage) { fullScreenImage = null }
}

@Composable
private fun Thumbnail(file: File, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val image = rememberImageBitmap(file, THUMBNAIL_MAX_SIDE) ?: return
    Image(
        bitmap = image,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    )
}

@Composable
private fun FullScreenImage(file: File, onDismiss: () -> Unit) {
    EncarteDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val image = rememberImageBitmap(file, FULL_SCREEN_MAX_SIDE)
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            if (image != null) {
                Image(image, contentDescription = stringResource(R.string.close), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ExpiryLine(date: LocalDate, expired: Boolean) {
    val formatted = rememberMediumDateFormatter().format(date)
    Text(
        stringResource(if (expired) R.string.expired_on else R.string.expires_on, formatted),
        color = if (expired) MaterialTheme.colorScheme.error else Color.Unspecified,
        style = MaterialTheme.typography.bodyLarge,
    )
}
