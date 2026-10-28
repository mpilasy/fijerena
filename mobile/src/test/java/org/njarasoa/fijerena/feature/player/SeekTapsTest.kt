package org.njarasoa.fijerena.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekTapsTest {
    private val width = 2000f

    @Test
    fun `left 40 percent rewinds, right 40 percent seeks forward, centre does neither`() {
        assertEquals(-1, seekTapSide(0f, width))
        assertEquals(-1, seekTapSide(799f, width))
        assertEquals(0, seekTapSide(800f, width))
        assertEquals(0, seekTapSide(1000f, width))
        assertEquals(0, seekTapSide(1200f, width))
        assertEquals(1, seekTapSide(1201f, width))
        assertEquals(1, seekTapSide(2000f, width))
    }

    @Test
    fun `each step on the same side adds 10 s to the pill`() {
        var side = 0
        var seconds = 0
        repeat(3) {
            seconds = seekBurstSeconds(side, seconds, 1)
            side = 1
        }
        assertEquals(30, seconds)
    }

    @Test
    fun `a step on the other side starts a new burst`() {
        assertEquals(10, seekBurstSeconds(1, 30, -1))
        assertEquals(10, seekBurstSeconds(0, 0, -1))
    }
}
