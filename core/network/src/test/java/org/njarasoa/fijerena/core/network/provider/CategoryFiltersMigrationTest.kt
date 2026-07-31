package org.njarasoa.fijerena.core.network.provider

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryFiltersMigrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `legacy prefixes key decodes into rules with STARTS_WITH default`() {
        val legacy = """{"categoryFilters":{"mode":"EXCLUDE","prefixes":["Adult","XXX"]}}"""
        val settings = json.decodeFromString<ProviderSettings>(legacy)
        assertEquals(
            listOf(CategoryMatcher("Adult"), CategoryMatcher("XXX")),
            settings.categoryFilters.rules,
        )
    }

    @Test
    fun `new rules shape round-trips unchanged`() {
        val settings =
            ProviderSettings(
                categoryFilters =
                    CategoryFilters(
                        rules = listOf(CategoryMatcher("Sport", MatchType.CONTAINS)),
                    ),
            )
        val encoded = json.encodeToString(settings)
        val decoded = json.decodeFromString<ProviderSettings>(encoded)
        assertEquals(settings.categoryFilters.rules, decoded.categoryFilters.rules)
    }
}
