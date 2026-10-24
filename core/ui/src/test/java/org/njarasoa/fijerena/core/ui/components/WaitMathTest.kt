package org.njarasoa.fijerena.core.ui.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.BufferingInfo
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.ui.theme.WaitStyle

class WaitMathTest {
    private fun info(
        buffered: Long = 0,
        needed: Long = 0,
        receiving: Long = 0,
        stream: Long? = null,
    ) = BufferingInfo(
        bufferedMs = buffered,
        neededMs = needed,
        waitingMs = 0,
        receivingBps = receiving,
        streamBps = stream,
        host = null,
        errors = 0,
        lastError = null,
        bytesReceived = 0,
    )

    @Test
    fun progressIsHeldOverNeededAndUnknownWithoutANeed() {
        assertNull(WaitMath.progress(null))
        assertNull(WaitMath.progress(info(buffered = 500, needed = 0)))
        assertEquals(0.25f, WaitMath.progress(info(buffered = 500, needed = 2000))!!, 0.001f)
        assertEquals(1f, WaitMath.progress(info(buffered = 5000, needed = 2000))!!, 0.001f)
    }

    @Test
    fun irisIsALittleOpenAtTheStartAndFullyOpenAtTheEnd() {
        assertTrue(WaitMath.irisOpening(0f) > 0f)
        assertEquals(1f, WaitMath.irisOpening(1f), 0.001f)
        assertTrue(WaitMath.irisOpening(0.5f) > WaitMath.irisOpening(0.2f))
    }

    @Test
    fun reelFollowsTheDownloadSpeed() {
        val base = WaitStyle.REEL_TURNS_PER_SECOND
        assertTrue(WaitMath.reelTurnsPerSecond(null) < base)
        assertTrue(WaitMath.reelTurnsPerSecond(info(receiving = 0)) < base)
        assertEquals(base, WaitMath.reelTurnsPerSecond(info(receiving = 3_000_000)), 0.001f)
        assertEquals(base, WaitMath.reelTurnsPerSecond(info(receiving = 3_000_000, stream = 3_000_000)), 0.001f)
        assertEquals(base / 2, WaitMath.reelTurnsPerSecond(info(receiving = 1_500_000, stream = 3_000_000)), 0.001f)
        // Within limits: never stopped while data comes, never a blur.
        assertEquals(base * 0.25f, WaitMath.reelTurnsPerSecond(info(receiving = 1, stream = 3_000_000)), 0.001f)
        assertEquals(base * 2f, WaitMath.reelTurnsPerSecond(info(receiving = 30_000_000, stream = 3_000_000)), 0.001f)
    }

    @Test
    fun aLineMeetsTheCircleAtItsRadius() {
        val hit = WaitMath.toCircle(from = Offset(10f, 0f), direction = Offset(0f, 1f), radius = 20f)
        assertEquals(20f, hit.getDistance(), 0.01f)
        assertEquals(10f, hit.x, 0.01f)
        assertTrue(hit.y > 0f)
    }

    @Test
    fun aGreyFrameHasNoTint() {
        assertNull(WaitMath.sceneTint(IntArray(100) { 0xFF808080.toInt() }, 10))
        assertNull(WaitMath.sceneTint(IntArray(0), 10))
    }

    @Test
    fun aDarkBlueFrameGivesALightBlue() {
        val tint = WaitMath.sceneTint(IntArray(100) { 0xFF102060.toInt() }, 10)!!
        val r = tint shr 16 and 0xFF
        val g = tint shr 8 and 0xFF
        val b = tint and 0xFF
        assertTrue("blue leads", b > r && b > g)
        assertTrue("light on a dark frame", (r + g + b) / 3 > 128)
    }

    @Test
    fun aLightRedFrameGivesADarkRed() {
        val tint = WaitMath.sceneTint(IntArray(100) { 0xFFF0A0A0.toInt() }, 10)!!
        val r = tint shr 16 and 0xFF
        val g = tint shr 8 and 0xFF
        val b = tint and 0xFF
        assertTrue("red leads", r > g && r > b)
        assertTrue("dark on a light frame", (r + g + b) / 3 < 128)
    }

    @Test
    fun theMiddleOfTheFrameDecidesLightOrDark() {
        // A light face in the middle, black bars at the sides: the tint is dark, to stand out on the face.
        val width = 9
        val frame = IntArray(81) { i -> if (i % width in 3..5) 0xFFE0B090.toInt() else 0xFF000000.toInt() }
        val tint = WaitMath.sceneTint(frame, width)!!
        val r = tint shr 16 and 0xFF
        val g = tint shr 8 and 0xFF
        val b = tint and 0xFF
        assertTrue("dark on a light middle", (r + g + b) / 3 < 128)
    }

    @Test
    fun hslGivesThePrimaries() {
        assertEquals(0xFFFF0000.toInt(), WaitMath.hslToArgb(0f, 1f, 0.5f))
        assertEquals(0xFF00FF00.toInt(), WaitMath.hslToArgb(120f, 1f, 0.5f))
        assertEquals(0xFF0000FF.toInt(), WaitMath.hslToArgb(240f, 1f, 0.5f))
    }

    @Test
    fun theKindFollowsTheStream() {
        assertEquals(WaitKind.Live, waitKindOf(PlayerMetadata(isLive = true)))
        assertEquals(WaitKind.Live, waitKindOf(PlayerMetadata(catchupLabel = "Mon 20:00")))
        assertEquals(WaitKind.Episode, waitKindOf(PlayerMetadata(showTitle = "Silo")))
        assertEquals(WaitKind.Film, waitKindOf(PlayerMetadata(title = "Arrival")))
        assertEquals(WaitKind.Live, waitKindOf("LIVE_TV"))
        assertEquals(WaitKind.Live, waitKindOf("MOVIES", isCatchup = true))
        assertEquals(WaitKind.Episode, waitKindOf("TV_SHOWS"))
        assertEquals(WaitKind.Film, waitKindOf("MOVIES"))
    }

    @Test
    fun theNameIsTheChannelTheShowOrTheFilm() {
        assertEquals("Silo", waitName(PlayerMetadata(title = "Silo - S01E02", showTitle = "Silo")))
        assertEquals("Arrival", waitName(PlayerMetadata(title = "Arrival")))
        assertEquals("CP24", waitName(PlayerMetadata(isLive = true, channelName = "CP24")))
    }
}
