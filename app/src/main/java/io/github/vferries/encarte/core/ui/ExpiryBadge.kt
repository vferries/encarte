package io.github.vferries.encarte.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.data.ExpiryStatus

private val SoonBackground = Color(0xFFF2A93B)
private val SoonText = Color(0xFF1D2440)
private val ExpiredBackground = Color(0xFFD32F2F)
private val ExpiredText = Color.White

/** Opaque, so it stays readable on any tile color or photo. Nothing unless the card expires soon. */
@Composable
fun ExpiryBadge(status: ExpiryStatus, modifier: Modifier = Modifier) {
    val (text, background, content) = when (status) {
        is ExpiryStatus.Soon -> Triple(
            if (status.daysLeft == 0) {
                stringResource(R.string.expiry_badge_today)
            } else {
                stringResource(R.string.expiry_badge_days, status.daysLeft)
            },
            SoonBackground,
            SoonText,
        )
        ExpiryStatus.Expired -> Triple(stringResource(R.string.expiry_badge_expired), ExpiredBackground, ExpiredText)
        ExpiryStatus.None, ExpiryStatus.Later -> return
    }
    Text(
        text = text,
        color = content,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = modifier.background(background, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
