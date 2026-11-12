package org.njarasoa.fijerena.core.player.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayModeChoiceTest {
    /** Modes at [width]×[height], one for each rate, with ids from [firstId]. */
    private fun modes(
        width: Int,
        height: Int,
        firstId: Int,
        vararg rates: Float,
    ): List<ModeSpec> = rates.mapIndexed { i, rate -> ModeSpec(firstId + i, width, height, rate) }

    private val rate23976 = FrameRateEstimator.RATE_23_976
    private val rate2997 = FrameRateEstimator.RATE_29_97
    private val rate5994 = FrameRateEstimator.RATE_59_94

    // A 4K TV as a Shield lists it: each rate at 2160p and at 1080p.
    private val uhd = modes(3840, 2160, 1, rate23976, 24f, 25f, rate2997, 30f, 50f, rate5994, 60f)
    private val hd = modes(1920, 1080, 101, rate23976, 24f, 25f, rate2997, 30f, 50f, rate5994, 60f)
    private val shield = uhd + hd
    private val uhd60 = uhd.single { it.refreshRate == 60f }

    private fun pick(
        fps: Float,
        modes: List<ModeSpec> = shield,
        current: ModeSpec = uhd60,
    ): Float? = pickDisplayMode(modes, current, fps)?.refreshRate

    @Test
    fun `each rate picks its own mode on a 4K list`() {
        assertEquals(rate23976, pick(rate23976))
        assertEquals(24f, pick(24f))
        assertEquals(50f, pick(50f))
        assertEquals(rate5994, pick(rate5994))
        assertEquals(60f, pick(60f))
    }

    @Test
    fun `the resolution does not change`() {
        val hd60 = hd.single { it.refreshRate == 60f }
        val picked = pickDisplayMode(shield, hd60, 24f)
        assertEquals(ModeSpec(102, 1920, 1080, 24f), picked)
    }

    @Test
    fun `25 and 30 fps prefer twice their rate`() {
        assertEquals(50f, pick(25f))
        assertEquals(rate5994, pick(rate2997))
        assertEquals(60f, pick(30f))
    }

    @Test
    fun `25 fps takes a 25 Hz mode when no 50 Hz mode exists`() {
        assertEquals(25f, pick(25f, modes(3840, 2160, 1, 24f, 25f, 60f)))
    }

    @Test
    fun `other rates prefer 1x, then 2x, then a higher multiple`() {
        assertEquals(24f, pick(24f, modes(3840, 2160, 1, 24f, 48f, 60f)))
        assertEquals(48f, pick(24f, modes(3840, 2160, 1, 48f, 60f, 120f)))
        assertEquals(120f, pick(24f, modes(3840, 2160, 1, 60f, 120f)))
    }

    @Test
    fun `23_976 takes its own mode, and a 24 Hz mode when there is none`() {
        assertEquals(rate23976, pick(rate23976))
        assertEquals(24f, pick(rate23976, modes(3840, 2160, 1, 24f, 50f, 60f)))
        // 24 fps does not take the 23.976 Hz mode while a 24 Hz mode exists.
        assertEquals(24f, pick(24f, modes(3840, 2160, 1, rate23976, 24f, 60f)))
    }

    @Test
    fun `59_94 and 60 stay apart`() {
        assertEquals(rate5994, pick(rate5994, modes(3840, 2160, 1, rate5994, 60f)))
        assertEquals(60f, pick(60f, modes(3840, 2160, 1, rate5994, 60f)))
    }

    @Test
    fun `a TV with only 60 Hz has no mode for 24 or 25 fps`() {
        val only60 = listOf(uhd60)
        assertNull(pick(24f, only60))
        assertNull(pick(rate23976, only60))
        assertNull(pick(25f, only60))
        assertEquals(60f, pick(30f, only60))
    }

    @Test
    fun `no rate, no mode`() {
        assertNull(pick(0f))
        assertNull(pick(-1f))
    }

    @Test
    fun `the request is the picked mode, when it is not the mode in place`() {
        val uhd24 = uhd.single { it.refreshRate == 24f }
        assertEquals(uhd24, displayModeToRequest(enabled = true, isLive = false, fps = 24f, modes = shield, current = uhd60))
        assertNull(displayModeToRequest(enabled = true, isLive = false, fps = 24f, modes = shield, current = uhd24))
        assertNull(displayModeToRequest(enabled = true, isLive = false, fps = 30f, modes = shield, current = uhd60))
    }

    @Test
    fun `no request with the switch off, without a rate, or with no mode`() {
        assertNull(displayModeToRequest(enabled = false, isLive = false, fps = 24f, modes = shield, current = uhd60))
        assertNull(displayModeToRequest(enabled = true, isLive = false, fps = null, modes = shield, current = uhd60))
        assertNull(displayModeToRequest(enabled = true, isLive = false, fps = 24f, modes = listOf(uhd60), current = uhd60))
    }

    private fun live(
        fps: Float?,
        current: ModeSpec,
        enabled: Boolean = true,
    ): ModeSpec? = displayModeToRequest(enabled = enabled, isLive = true, fps = fps, modes = shield, current = current)

    private fun uhdAt(rate: Float) = uhd.single { it.refreshRate == rate }

    @Test
    fun `live in the same family keeps the mode in place`() {
        // A zap between two 50 Hz channels, 25i (50 frames) included.
        assertNull(live(50f, uhdAt(50f)))
        assertNull(live(25f, uhdAt(50f)))
        // 59.94 and 60 are one family; so are 23.976 and 24.
        assertNull(live(rate5994, uhd60))
        assertNull(live(60f, uhdAt(rate5994)))
        assertNull(live(rate2997, uhd60))
        assertNull(live(24f, uhdAt(rate23976)))
    }

    @Test
    fun `live in another family changes the mode`() {
        assertEquals(uhdAt(50f), live(50f, uhd60))
        assertEquals(uhdAt(50f), live(25f, uhd60))
        assertEquals(uhdAt(rate5994), live(rate5994, uhdAt(50f)))
        assertEquals(uhdAt(24f), live(24f, uhdAt(50f)))
    }

    @Test
    fun `live with an unknown rate or the switch off keeps the mode`() {
        assertNull(live(null, uhd60))
        assertNull(live(50f, uhd60, enabled = false))
    }

    @Test
    fun `a film changes the mode in the same family too`() {
        assertEquals(
            uhdAt(rate5994),
            displayModeToRequest(enabled = true, isLive = false, fps = rate5994, modes = shield, current = uhd60),
        )
    }

    @Test
    fun `rate families`() {
        assertEquals(RateFamily.HZ_24, rateFamily(rate23976))
        assertEquals(RateFamily.HZ_24, rateFamily(48f))
        assertEquals(RateFamily.HZ_50, rateFamily(25f))
        assertEquals(RateFamily.HZ_50, rateFamily(100f))
        assertEquals(RateFamily.HZ_60, rateFamily(rate2997))
        assertEquals(RateFamily.HZ_60, rateFamily(120f))
        assertNull(rateFamily(56f))
        assertNull(rateFamily(12f))
        assertFalse(sameRateFamily(56f, 56f))
        assertTrue(sameRateFamily(23.976f, 24f))
        assertFalse(sameRateFamily(24f, 25f))
    }
}
