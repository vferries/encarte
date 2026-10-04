package io.github.vferries.encarte.core.text

import java.text.Normalizer
import java.util.Locale

private val combiningMarks = Regex("\\p{Mn}+")
private val nonAlphanumeric = Regex("[^\\p{L}\\p{Nd}]+")

/**
 * Lowercases, strips accents, expands ligatures and drops punctuation and spaces,
 * so that "E.Leclerc", "e leclerc" and "É-Leclerc" all compare equal.
 */
fun String.normalizedForMatching(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .lowercase(Locale.ROOT)
        .replace("œ", "oe")
        .replace("æ", "ae")
        .replace(nonAlphanumeric, "")
