package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lists whose rows carry their source's badge: those that mix the profile's sources. */
class MixesSourcesTest {
    @Test
    fun `Recent, Favourites and the category lists mix sources`() {
        assertTrue(CategoryViewModel.mixesSources(CategoryViewModel.RECENT_CATEGORY_ID))
        assertTrue(CategoryViewModel.mixesSources(CategoryViewModel.FAVORITES_CATEGORY_ID))
        assertTrue(CategoryViewModel.mixesSources(CategoryViewModel.FAVORITE_CATEGORIES_ID))
        assertTrue(CategoryViewModel.mixesSources(CategoryViewModel.RECENTLY_VIEWED_CATEGORIES_ID))
    }

    @Test
    fun `an ordinary category is one source's`() {
        assertFalse(CategoryViewModel.mixesSources("42"))
        assertFalse(CategoryViewModel.mixesSources(null))
    }
}
