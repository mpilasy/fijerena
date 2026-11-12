package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.SettingsRowKind
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.theme.AllUiStyles
import org.njarasoa.fijerena.core.ui.theme.CupertinoStyle
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.theme.ModernStyle
import org.njarasoa.fijerena.ui.components.input.TvInputDefaults
import org.robolectric.RobolectricTestRunner

/**
 * A tall row (Home's "Your Sources": a source's name, its state and a long reason) keeps its
 * text inside its corner in every Look and Feel. A pill Look (Dial, Rounded) gave the row the chips'
 * 100 dp corner, so the row became a stadium and clipped the start of its first and last lines.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRowCornerTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a tall row keeps its first and last lines inside its corner in every Look`() {
        var style by mutableStateOf(ModernStyle)
        lateinit var shape: Shape
        lateinit var density: Density
        rule.setContent {
            CompositionLocalProvider(LocalUiStyle provides style) {
                shape = TvInputDefaults.shape().shape
                density = LocalDensity.current
                SettingsRow(
                    title = TITLE,
                    description = null,
                    onClick = {},
                    kind = SettingsRowKind.Action,
                    modifier = Modifier.width(ROW_WIDTH).testTag(ROW),
                    supporting = { CinemaText(MESSAGE, role = TextRole.Body) },
                )
            }
        }
        AllUiStyles.forEach { look ->
            style = look
            rule.waitForIdle()
            val row = rule.onNodeWithTag(ROW).getBoundsInRoot()
            val title = rule.onNodeWithText(TITLE, useUnmergedTree = true).getBoundsInRoot()
            val message = rule.onNodeWithText(MESSAGE, useUnmergedTree = true).getBoundsInRoot()
            with(density) {
                val size = Size((row.right - row.left).toPx(), (row.bottom - row.top).toPx())
                // A Look with square corners (Sharp) gives a plain rectangle.
                val outline =
                    when (val it = shape.createOutline(size, LayoutDirection.Ltr, this)) {
                        is Outline.Rounded -> it.roundRect
                        else -> RoundRect(it.bounds)
                    }
                val corners =
                    listOf(
                        "first line" to Offset((title.left - row.left).toPx(), (title.top - row.top).toPx()),
                        "last line" to Offset((message.left - row.left).toPx(), (message.bottom - row.top).toPx() - 1f),
                    )
                corners.forEach { (line, point) ->
                    assertTrue(
                        "${look.id}: the start of the $line ($point) is outside the row's corner $outline",
                        outline.contains(point),
                    )
                }
            }
        }
    }

    @Test
    fun `a one-line row stays a pill in a pill Look`() {
        lateinit var shape: Shape
        lateinit var density: Density
        rule.setContent {
            CompositionLocalProvider(LocalUiStyle provides CupertinoStyle) {
                shape = TvInputDefaults.shape().shape
                density = LocalDensity.current
                SettingsRow(title = TITLE, description = null, onClick = {}, modifier = Modifier.width(ROW_WIDTH).testTag(ROW))
            }
        }
        val row = rule.onNodeWithTag(ROW).getBoundsInRoot()
        with(density) {
            val size = Size((row.right - row.left).toPx(), (row.bottom - row.top).toPx())
            val outline = shape.createOutline(size, LayoutDirection.Ltr, this) as Outline.Rounded
            val radius = outline.roundRect.topLeftCornerRadius.x
            assertTrue("corner $radius on a row ${size.height} high", radius >= size.height / 2 - 1f)
        }
    }

    private companion object {
        const val ROW = "row"
        const val TITLE = "ac5test (Xtream IPTV)"
        const val MESSAGE =
            "Update failed\nLast update failed: Catalogue sync failed. This device's storage is almost full. " +
                "Free up space: remove apps that you do not use, or clear the image cache (Settings → About → " +
                "Device Info) or the guide data (Settings → Sources & Guide → Guide Data Maintenance). Then try " +
                "again. Automatic updates wait until then.\n\n[dev] Storage low: 100 MB free"
        val ROW_WIDTH = 700.dp
    }
}
