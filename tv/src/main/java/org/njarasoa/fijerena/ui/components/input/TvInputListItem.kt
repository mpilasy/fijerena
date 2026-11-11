@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.input

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.tv.material3.ListItem
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * `androidx.tv.material3.ListItem` wired to [TvInputDefaults]. Every control in this package goes
 * through here, so focus and selection look the same everywhere.
 *
 * A selected row has the P5 "current" bar ([currentIndicator]). `ListItem` pads its content, so the
 * bar is drawn from outside the item and follows the item's focus / press scale itself.
 * [focusBorder] false: the row around the item draws the focus outline ([restingRowGround]).
 */
@Composable
internal fun TvInputListItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: (@Composable BoxScope.() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    focusBorder: Boolean = true,
    headlineContent: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier =
            modifier.currentIndicator(
                active = selected,
                shape = cornerShape(CornerRadius.small),
                containerScale = listItemScale(interactionSource, enabled),
            ),
        enabled = enabled,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        supportingContent = supportingContent,
        shape = TvInputDefaults.shape(),
        colors = TvInputDefaults.colors(),
        scale = TvInputDefaults.scale(),
        border = if (focusBorder) TvInputDefaults.border() else TvInputDefaults.noBorder(),
        glow = TvInputDefaults.glow(),
        interactionSource = interactionSource,
        headlineContent = headlineContent,
    )
}

/** The scale [TvInputDefaults.scale] gives the item right now, animated alongside it and read at draw time. */
@Composable
private fun listItemScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
): () -> Float {
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val target =
        when {
            !enabled -> TvFocusTokens.defaultScale
            pressed -> TvFocusTokens.pressedScaleSubtle
            focused -> TvFocusTokens.focusedScaleRow
            else -> TvFocusTokens.defaultScale
        }
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = CinemaAnimation.focusDurationMs, easing = CinemaAnimation.EmphasizedEasing),
        label = "current_bar_scale",
    )
    return { scale }
}
