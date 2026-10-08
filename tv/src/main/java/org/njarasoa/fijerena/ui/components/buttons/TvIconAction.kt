package org.njarasoa.fijerena.ui.components.buttons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextDisabled
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * An action as an icon: a round button at rest, widening to show [label] beside the icon while it
 * is focused, so a row of actions stays compact and the one being pointed at says what it does.
 * The icon carries the state (filled or outlined star); [label] names the action and is the
 * button's content description. [danger] (Remove, Delete) is outlined in the error colour.
 * See docs/plans/archive/20261005_icon-buttons-plan.md.
 */
@Composable
fun TvIconAction(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    iconTint: Color? = null,
    iconModifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    var focused by remember { mutableStateOf(false) }
    val accent = if (danger) CinemaError else CinemaTextPrimary
    val size = Spacing.xxl.scaled(scale)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .height(size)
                .defaultMinSize(minWidth = size)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (danger) Color.Transparent else TvFocusTokens.restingContainer,
                contentColor = accent,
                focusedContainerColor = if (danger) CinemaError else TvFocusTokens.focusedContainer,
                focusedContentColor = if (danger) CinemaTextPrimary else CinemaAccentLight,
                pressedContainerColor = TvFocusTokens.focusedContainer,
                pressedContentColor = CinemaTextPrimary,
            ),
        scale =
            ClickableSurfaceDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                pressedScale = TvFocusTokens.pressedScale,
            ),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        border =
            ClickableSurfaceDefaults.border(
                border =
                    Border(
                        BorderStroke(
                            TvFocusTokens.borderDefault.scaled(scale),
                            if (danger) CinemaError else CinemaTextPrimary.copy(alpha = CinemaAlpha.textLow),
                        ),
                    ),
                focusedBorder = Border(BorderStroke(TvFocusTokens.focusBorderWidth.scaled(scale), CinemaAccentLight)),
            ),
    ) {
        // Full height, or the row sits at the top of the circle and CenterVertically only centres
        // the icon within the row: every icon rode high in its button.
        Row(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = (size - TvDimensions.iconMedium.scaled(scale)) / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (!enabled) CinemaTextDisabled else iconTint ?: androidx.tv.material3.LocalContentColor.current,
                modifier = Modifier.size(TvDimensions.iconMedium.scaled(scale)).then(iconModifier),
            )
            AnimatedVisibility(
                visible = focused,
                enter = expandHorizontally(tween(CinemaAnimation.focusDurationMs)) + fadeIn(tween(CinemaAnimation.focusDurationMs)),
                exit = shrinkHorizontally(tween(CinemaAnimation.focusDurationMs)) + fadeOut(tween(CinemaAnimation.focusDurationMs)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                }
            }
        }
    }
}
