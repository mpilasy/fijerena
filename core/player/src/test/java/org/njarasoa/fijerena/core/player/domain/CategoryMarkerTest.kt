package org.njarasoa.fijerena.core.player.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryMarkerTest {
    private fun channel(name: String) =
        MediaItem(
            id = name,
            name = name,
            mediaType = MediaType.LIVE_CHANNEL,
            categoryId = "cat1",
        )

    @Test
    fun `hash runs at both ends are a marker`() {
        assertTrue(channel("##### 4K #####").isCategoryMarker)
        assertTrue(channel("#### GÉNÉRAL HD/4K ####").isCategoryMarker)
        assertTrue(channel("## NOW TV SPORT ##").isCategoryMarker)
        assertTrue(channel("  ## padded ##  ").isCategoryMarker)
    }

    @Test
    fun `a hash on one side only is a channel name`() {
        assertFalse(channel("#1 Music").isCategoryMarker)
        assertFalse(channel("C#").isCategoryMarker)
        assertFalse(channel("#").isCategoryMarker)
    }

    @Test
    fun `plain names are channels`() {
        assertFalse(channel("TF1 HD").isCategoryMarker)
        assertFalse(channel("").isCategoryMarker)
    }
}
