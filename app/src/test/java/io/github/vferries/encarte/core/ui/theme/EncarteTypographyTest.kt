package io.github.vferries.encarte.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import io.github.vferries.encarte.R
import org.junit.Assert.assertEquals
import org.junit.Test

class EncarteTypographyTest {
    private fun Typography.styles(): Map<String, TextStyle> = mapOf(
        "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
        "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
        "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
        "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
        "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
    )

    private val styles = EncarteTypography.styles()

    @Test
    fun everyStyleUsesNunito() {
        assertEquals(15, styles.size)
        styles.forEach { (name, style) -> assertEquals(name, NunitoFamily, style.fontFamily) }
    }

    @Test
    fun weightsFollowTheTextRole() {
        styles.forEach { (name, style) ->
            val expected = when {
                name.startsWith("body") -> FontWeight.Medium
                name.startsWith("label") -> FontWeight.Bold
                else -> FontWeight.ExtraBold
            }
            assertEquals(name, expected, style.fontWeight)
        }
    }

    @Test
    fun sizesAndLineHeightsStayMaterialDefaults() {
        val defaults = Typography().styles()
        styles.forEach { (name, style) ->
            assertEquals(name, defaults.getValue(name).fontSize, style.fontSize)
            assertEquals(name, defaults.getValue(name).lineHeight, style.lineHeight)
        }
    }

    @Test
    fun eachWeightPinsTheVariableAxis() {
        val fonts = (NunitoFamily as FontListFontFamily).fonts.map { it as ResourceFont }
        assertEquals(listOf(400, 500, 700, 800), fonts.map { it.weight.weight })
        fonts.forEach { font ->
            assertEquals(R.font.nunito, font.resId)
            // One variable file serves every weight: without this setting, all of them render at the file's default.
            assertEquals(FontVariation.Settings(font.weight, font.style), font.variationSettings)
        }
    }
}
