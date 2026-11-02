package org.njarasoa.fijerena.core.network.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A child profile's rules filter categories also with no rule or script of the profile's own, on
 * every source (the early return of `MediaRepository.getFilteredCategories` kept every category of
 * an M3U or Jellyfin source for a child).
 */
class CategoryFiltersChildTest {
    private val names =
        listOf(
            "FR - ENFANTS",
            "BG - БЪЛГАРИЯ ФИЛМ",
            "أفلام أجنبية 2019",
            "EN - ANIMATION",
            "EN - ACTION",
        )

    @Test
    fun `filters with no rule filter nothing for an adult`() {
        assertTrue(CategoryFilters().filtersNothing)
    }

    @Test
    fun `a child's rules alone are a filter`() {
        assertFalse(CategoryFilters(child = ParentalRules()).filtersNothing)
    }

    @Test
    fun `a child sees only the kids' film categories`() {
        val filters = CategoryFilters(child = ParentalRules())
        assertEquals(
            listOf("FR - ENFANTS", "EN - ANIMATION"),
            names.filter { filters.shouldShowCategory(it, films = true) },
        )
        assertEquals(listOf("FR - ENFANTS"), names.filter { filters.shouldShowCategory(it, films = false) })
    }
}
