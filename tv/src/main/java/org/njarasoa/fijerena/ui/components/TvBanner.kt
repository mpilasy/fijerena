package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * The keys of a TV banner that never takes focus: the control under it keeps focus, so it is still
 * focused when the banner closes. While an item is [shown], the remote's keys go to the banner:
 * Left and Right move through [choices], OK acts on the choice on its key up only ([onOk]; a key
 * pressed before the banner showed never acts), Back closes ([onBack]), and no other key reaches
 * the screen. Volume keys still work. One state for a kind of banner: the game reminder banner and
 * the send banner share this behaviour.
 */
@Stable
class TvBannerKeys<T : Any, C : Any>(
    private val first: C,
    private val fallback: C,
    private val choices: (T) -> List<C>,
    private val onBack: (T) -> Unit,
    private val onOk: (T, C) -> Unit,
) {
    /** The item on screen, which has the keys; null while none shows or a Dialog window is on top. */
    internal var shown: T? = null

    private var choice by mutableStateOf(first)

    /** The key that went down while the banner showed: its key up acts, never one from before. */
    private var down: Key? = null

    internal fun chosen(item: T): C = choice.takeIf { it in choices(item) } ?: fallback

    internal fun reset() {
        shown = null
        choice = first
        down = null
    }

    private fun move(
        item: T,
        step: Int,
    ) {
        val all = choices(item)
        choice = all[(all.indexOf(chosen(item)) + step).coerceIn(0, all.lastIndex)]
    }

    /** Takes [event] while an item shows; volume keys go on to the system. */
    internal fun onKey(event: KeyEvent): Boolean {
        val item = shown ?: return false
        if (event.key == Key.VolumeUp || event.key == Key.VolumeDown || event.key == Key.VolumeMute) return false
        if (event.type == KeyEventType.KeyDown) {
            down = event.key
            when (event.key) {
                Key.DirectionLeft -> move(item, -1)
                Key.DirectionRight -> move(item, 1)
            }
        } else if (event.type == KeyEventType.KeyUp && event.key == down) {
            down = null
            when (event.key) {
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    val acted = chosen(item)
                    reset()
                    onOk(item, acted)
                }

                Key.Back -> {
                    reset()
                    onBack(item)
                }
            }
        }
        return true
    }
}

/**
 * The panel of a TV banner: [title] over [detail] over the buttons, at the top centre, after the
 * content so it draws over it. The buttons never take focus ([bannerChoice]).
 */
@Composable
internal fun TvBannerPanel(
    tag: String,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    buttons: @Composable RowScope.() -> Unit,
) {
    val shape = cornerShape(CornerRadius.medium)
    // The text over the buttons, so a long title keeps its end: with the buttons beside it, the
    // title had little room left.
    Column(
        modifier =
            modifier
                // The TV Surface's Box passes its minimum size on to its content: without this, the
                // bar would fill the whole screen. The bar keeps its own size, at the top centre.
                .wrapContentSize(Alignment.TopCenter)
                .testTag(tag)
                .padding(top = Spacing.tvSafeMarginVertical)
                .widthIn(max = TvDimensions.dialogWidthLarge)
                .background(CinemaTheme.colors.surface, shape)
                .border(TvDimensions.borderDefault, CinemaTheme.colors.glassBorder, shape)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            CinemaText(
                text = title,
                role = TextRole.Heading,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            CinemaText(
                text = detail,
                tone = TextTone.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            content = buttons,
        )
    }
}

/** A banner button: never focused (the keys choose it), and selected while chosen. */
internal fun Modifier.bannerChoice(chosen: Boolean): Modifier = focusProperties { canFocus = false }.semantics { selected = chosen }
