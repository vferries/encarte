package io.github.vferries.encarte.brands

import io.github.vferries.encarte.core.text.normalizedForMatching
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrandCatalogTest {
    private val catalog = BrandCatalog {
        """
        [
          {"name": "Carrefour", "aliases": ["Carrefour Market", "Proxi"], "color": "#254F9B"},
          {"name": "E.Leclerc", "aliases": ["Leclerc"], "color": "#0B70B5"},
          {"name": "Leroy Merlin", "aliases": [], "color": "#78BE20"},
          {"name": "Relay", "aliases": [], "color": "#E30613"},
          {"name": "Tape à l'œil", "aliases": ["TAO"], "color": "#FF4040"}
        ]
        """
    }

    @Test
    fun prefixMatchesComeBeforeSubstringMatches() {
        // "Carrefour" only contains "re"; "Relay" starts with it, so it comes first despite catalog order.
        assertEquals(listOf("Relay", "Carrefour"), catalog.suggest("re").map { it.name })
    }

    @Test
    fun suggestionsIgnoreAccentsPunctuationAndAliases() {
        assertEquals(listOf("Carrefour"), catalog.suggest("proxi").map { it.name })
        assertEquals(listOf("Tape à l'œil"), catalog.suggest("tape a l oeil").map { it.name })
        assertEquals(emptyList<Brand>(), catalog.suggest(" "))
    }

    @Test
    fun matchIsExactOnNameOrAlias() {
        assertEquals("E.Leclerc", catalog.match("leclerc")?.name)
        assertEquals("E.Leclerc", catalog.match("E. Leclerc")?.name)
        assertNull(catalog.match("Lecl"))
    }

    @Test
    fun colorParsesToOpaqueArgb() {
        assertEquals(0xFF254F9B.toInt(), catalog.match("Carrefour")!!.argb)
    }

    @Test
    fun bundledCatalogIsValid() {
        // Unit tests run with the module directory as working directory.
        val json = File("src/main/assets/brands.json").readText()
        val brands = Json.decodeFromString<List<Brand>>(json)
        assertEquals(224, brands.size)
        assertTrue(brands.all { it.name.isNotBlank() })

        val keys = brands.flatMap { listOf(it.name) + it.aliases }.map { it.normalizedForMatching() }
        assertEquals("normalized names and aliases must be unique", keys.size, keys.toSet().size)
        val hex = Regex("^#[0-9A-F]{6}$")
        assertTrue(brands.all { hex.matches(it.color) && it.color != "#FFFFFF" })
        assertEquals("Carrefour", BrandCatalog { json }.match("Carrefour Market")?.name)
    }

    @Test
    fun extendedCatalogMatchesNewBrandsAndAliasAdditions() {
        val bundled = BrandCatalog { File("src/main/assets/brands.json").readText() }
        // One new entry per family (supermarket, pharmacy, optician), then an alias merged into an old brand.
        assertEquals(0xFFE61845.toInt(), bundled.match("Cora")?.argb)
        assertEquals(0xFF006536.toInt(), bundled.match("Pharmavie")?.argb)
        assertEquals(0xFF252525.toInt(), bundled.match("Grand Optical")?.argb)
        assertEquals("Casino", bundled.match("Vival")?.name)
        assertEquals("Casino", bundled.match("Sherpa")?.name)
        assertEquals("Spar", bundled.match("Spar")?.name)
        assertEquals("Cora", bundled.suggest("cora").first().name)
        assertEquals("ALL Accor", bundled.match("Sofitel")?.name)
    }
}
