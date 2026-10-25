package org.njarasoa.fijerena.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * A scrolling name stays between the badges on its line: the marquee draws nothing outside its
 * own width. It ran on under the source badge at the end of a channel row's first line.
 *
 * The pixels are drawn from the view directly: the marquee animates without end, so the test
 * clock does not advance by itself and `captureToImage` would wait for a frame forever.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BounceMarqueeClipTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var view: View

    /** A 300 px line: a 50 px badge, the name, a 50 px badge. */
    private fun setLine() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Row(Modifier.width(300.dp).background(Color.White)) {
                    Box(Modifier.size(50.dp, 20.dp))
                    Text(
                        text = "W".repeat(60),
                        color = Color.Black,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f).bounceMarquee(),
                    )
                    Box(Modifier.size(50.dp, 20.dp))
                }
            }
        }
    }

    private fun draw(): Bitmap {
        val bitmap = Bitmap.createBitmap(300, 30, Bitmap.Config.ARGB_8888)
        composeTestRule.runOnUiThread { view.draw(Canvas(bitmap)) }
        return bitmap
    }

    /** The first and last x of the line that hold text pixels: dark and opaque (under the line is transparent). */
    private fun Bitmap.inkedSpan(): IntRange {
        fun isInk(pixel: Int) = android.graphics.Color.alpha(pixel) >= 128 && android.graphics.Color.red(pixel) < 128
        val inked = (0 until width).filter { x -> (0 until height).any { y -> isInk(getPixel(x, y)) } }
        assertTrue("no text drawn", inked.isNotEmpty())
        return inked.first()..inked.last()
    }

    @Test
    fun `at rest the name stops at the end badge`() {
        setLine()
        composeTestRule.mainClock.advanceTimeByFrame()
        val span = draw().inkedSpan()
        assertTrue("$span", span.last < 250)
    }

    @Test
    fun `scrolled, the name stays between the two badges`() {
        setLine()
        val atRest = draw()
        // Past the 1.2 s pause at the start, the text has moved.
        repeat(200) { composeTestRule.mainClock.advanceTimeByFrame() }
        val scrolled = draw()
        assertFalse("the text did not move", scrolled.sameAs(atRest))
        val span = scrolled.inkedSpan()
        assertTrue("$span", span.first >= 50)
        assertTrue("$span", span.last < 250)
    }
}
