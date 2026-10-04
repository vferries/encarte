package io.github.vferries.encarte.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vferries.encarte.core.color.CardPalette
import io.github.vferries.encarte.core.data.Card
import java.io.File

/** ISO/IEC 7810 ID-1, the size of a bank or loyalty card. */
const val CARD_ASPECT_RATIO = 1.586f

private const val TILE_IMAGE_MAX_SIDE = 480

@Composable
fun CardTile(card: Card, imageFile: File?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val image = rememberImageBitmap(imageFile, TILE_IMAGE_MAX_SIDE)
    Surface(
        onClick = onClick,
        modifier = modifier.aspectRatio(CARD_ASPECT_RATIO),
        shape = RoundedCornerShape(12.dp),
        color = Color(card.color),
        contentColor = Color(CardPalette.contentColorFor(card.color)),
        shadowElevation = 2.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = card.storeName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = card.storeName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                )
            }
        }
    }
}
