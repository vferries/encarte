package io.github.vferries.encarte.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.vferries.encarte.R

// The short Font(resId, weight) overload leaves variationSettings empty, so every weight would render at
// the variable file's default instance: pin the wght axis explicitly.
private fun nunitoFont(weight: FontWeight) =
    Font(R.font.nunito, weight, variationSettings = FontVariation.Settings(weight, FontStyle.Normal))

/** Bundled variable font: each declared weight pins the file's wght axis, so no font is ever downloaded. */
val NunitoFamily = FontFamily(
    nunitoFont(FontWeight.Normal),
    nunitoFont(FontWeight.Medium),
    nunitoFont(FontWeight.Bold),
    nunitoFont(FontWeight.ExtraBold),
)

private fun TextStyle.nunito(weight: FontWeight) = copy(fontFamily = NunitoFamily, fontWeight = weight)

// Heavy rounded headings carry the identity; Medium body text keeps Nunito's thin strokes legible at small sizes.
val EncarteTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.nunito(FontWeight.ExtraBold),
        displayMedium = displayMedium.nunito(FontWeight.ExtraBold),
        displaySmall = displaySmall.nunito(FontWeight.ExtraBold),
        headlineLarge = headlineLarge.nunito(FontWeight.ExtraBold),
        headlineMedium = headlineMedium.nunito(FontWeight.ExtraBold),
        headlineSmall = headlineSmall.nunito(FontWeight.ExtraBold),
        titleLarge = titleLarge.nunito(FontWeight.ExtraBold),
        titleMedium = titleMedium.nunito(FontWeight.ExtraBold),
        titleSmall = titleSmall.nunito(FontWeight.ExtraBold),
        bodyLarge = bodyLarge.nunito(FontWeight.Medium),
        bodyMedium = bodyMedium.nunito(FontWeight.Medium),
        bodySmall = bodySmall.nunito(FontWeight.Medium),
        labelLarge = labelLarge.nunito(FontWeight.Bold),
        labelMedium = labelMedium.nunito(FontWeight.Bold),
        labelSmall = labelSmall.nunito(FontWeight.Bold),
    )
}
