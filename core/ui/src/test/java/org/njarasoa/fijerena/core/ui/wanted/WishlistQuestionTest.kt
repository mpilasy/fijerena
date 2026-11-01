package org.njarasoa.fijerena.core.ui.wanted

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the Wishlist question shows (consistency plan, C-D4): once, after the title is watched to the end. */
class WishlistQuestionTest {
    @Test
    fun `a watched Wishlist title never asked is asked`() {
        assertTrue(shouldAskRemove(onWishlist = true, watched = true, askedAt = null, answered = false))
    }

    @Test
    fun `a title not watched to the end is not asked`() {
        assertFalse(shouldAskRemove(onWishlist = true, watched = false, askedAt = null, answered = false))
    }

    @Test
    fun `a title not on the Wishlist is not asked`() {
        assertFalse(shouldAskRemove(onWishlist = false, watched = true, askedAt = null, answered = false))
    }

    @Test
    fun `a title asked before, here or on a synced device, is not asked again`() {
        assertFalse(shouldAskRemove(onWishlist = true, watched = true, askedAt = 1L, answered = false))
    }

    @Test
    fun `the prompt closes on the answer before the stored answer comes back`() {
        assertFalse(shouldAskRemove(onWishlist = true, watched = true, askedAt = null, answered = true))
    }
}
