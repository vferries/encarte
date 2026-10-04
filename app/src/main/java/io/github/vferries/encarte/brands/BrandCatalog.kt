package io.github.vferries.encarte.brands

import io.github.vferries.encarte.core.text.normalizedForMatching
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A store brand: name and color only. Logos are deliberately not bundled (non-free assets). */
@Serializable
data class Brand(val name: String, val aliases: List<String> = emptyList(), val color: String) {
    val argb: Int get() = (0xFF000000 or color.removePrefix("#").toLong(16)).toInt()
}

class BrandCatalog(private val loadJson: () -> String) {

    private class Entry(val brand: Brand, val keys: List<String>)

    private val entries: List<Entry> by lazy {
        Json.decodeFromString<List<Brand>>(loadJson()).map { brand ->
            Entry(brand, (listOf(brand.name) + brand.aliases).map { it.normalizedForMatching() })
        }
    }

    /** Touches the lazy list so the asset is parsed off the main thread. */
    fun preload() {
        entries
    }

    fun suggest(query: String, limit: Int = 5): List<Brand> {
        val normalized = query.normalizedForMatching()
        if (normalized.isEmpty()) return emptyList()
        val prefix = entries.filter { entry -> entry.keys.any { it.startsWith(normalized) } }
        val substring = entries.filter { entry -> entry !in prefix && entry.keys.any { it.contains(normalized) } }
        return (prefix + substring).take(limit).map { it.brand }
    }

    fun match(name: String): Brand? {
        val normalized = name.normalizedForMatching()
        if (normalized.isEmpty()) return null
        return entries.firstOrNull { normalized in it.keys }?.brand
    }
}
