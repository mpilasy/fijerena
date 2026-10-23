package org.njarasoa.fijerena.core.ui.image

import coil3.size.Dimension
import coil3.size.Size
import org.junit.Assert.assertEquals
import org.junit.Test

/** TV images are decoded at 1080p at most, keeping their shape. */
class MaxImageSizeTest {
    @Test
    fun `a 4K backdrop is decoded at 1080p`() {
        assertEquals(Size(Dimension(1920), Dimension(1080)), cappedSize(Size(Dimension(3840), Dimension(2160)), 1920))
    }

    @Test
    fun `a tall poster keeps its shape`() {
        assertEquals(Size(Dimension(1280), Dimension(1920)), cappedSize(Size(Dimension(2560), Dimension(3840)), 1920))
    }

    @Test
    fun `an image already small enough is left alone`() {
        val size = Size(Dimension(500), Dimension(750))
        assertEquals(size, cappedSize(size, 1920))
    }

    @Test
    fun `a known side is capped, an unknown one stays unknown`() {
        assertEquals(Size(Dimension(1920), Dimension.Undefined), cappedSize(Size(Dimension(3840), Dimension.Undefined), 1920))
        assertEquals(Size.ORIGINAL, cappedSize(Size.ORIGINAL, 1920))
    }
}
