package org.njarasoa.fijerena.feature.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSwipeTest {
    // A landscape phone: 10 % is 200 px across and 108 px down.
    private val width = 2000f
    private val height = 1080f

    private fun edge(
        x: Float,
        y: Float,
    ) = startsInChannelSwipeEdge(x, y, width, height)

    @Test
    fun `centre of the screen is not an edge`() {
        assertFalse(edge(1000f, 540f))
    }

    @Test
    fun `left and right tenths are edges (system back)`() {
        assertTrue(edge(0f, 540f))
        assertTrue(edge(199f, 540f))
        assertTrue(edge(1801f, 540f))
        assertTrue(edge(2000f, 540f))
    }

    @Test
    fun `top and bottom tenths are edges (notification shade, home)`() {
        assertTrue(edge(1000f, 0f))
        assertTrue(edge(1000f, 107f))
        assertTrue(edge(1000f, 973f))
        assertTrue(edge(1000f, 1080f))
    }

    @Test
    fun `just inside the tenth line is not an edge`() {
        assertFalse(edge(200f, 540f))
        assertFalse(edge(1800f, 540f))
        assertFalse(edge(1000f, 108f))
        assertFalse(edge(1000f, 972f))
    }
}
