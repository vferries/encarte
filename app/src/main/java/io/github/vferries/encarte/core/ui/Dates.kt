package io.github.vferries.encarte.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The user's medium date format ("12 mars 2027", "Mar 12, 2027"); follows locale changes. */
@Composable
fun rememberMediumDateFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
}
