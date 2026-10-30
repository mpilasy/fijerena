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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextDisabled
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
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
 * [on]: the button is a switch (UX-D7) — its glyph takes `TvButtonStyle.iconOnContent` at rest while
 * on, and it reads as a switch with that state; [label] then names the setting and its state.
 * See the icon buttons plan.
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
    on: Boolean? = null,
) {
    val scale = LocalUiScale.current
    var focused by remember { mutableStateOf(false) }
    val accent = if (danger) TvButtonStyle.dangerIconOutline else TvButtonStyle.iconContent
    val size = TvButtonStyle.iconButtonSize.scaled(scale)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .height(size)
                .defaultMinSize(minWidth = size)
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    contentDescription = label
                    if (on != null) {
                        role = Role.Switch
                        toggleableState = ToggleableState(on)
                    }
                },
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (danger) CinemaTheme.colors.clear else TvButtonStyle.iconContainer,
                contentColor = accent,
                focusedContainerColor = if (danger) TvButtonStyle.dangerIconFocusedContainer else TvButtonStyle.iconFocusedContainer,
                focusedContentColor = if (danger) TvButtonStyle.dangerIconFocusedContent else TvButtonStyle.iconFocusedContent,
                pressedContainerColor = TvButtonStyle.iconFocusedContainer,
                pressedContentColor = if (danger) TvButtonStyle.dangerIconFocusedContent else TvButtonStyle.iconFocusedContent,
            ),
        scale =
            ClickableSurfaceDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                pressedScale = TvFocusTokens.pressedScale,
            ),
        shape = ClickableSurfaceDefaults.shape(shape = CinemaTheme.shapes.control),
        border =
            ClickableSurfaceDefaults.border(
                border =
                    Border(
                        BorderStroke(
                            TvFocusTokens.borderDefault.scaled(scale),
                            if (danger) TvButtonStyle.dangerIconOutline else TvButtonStyle.iconOutline,
                        ),
                    ),
                focusedBorder = Border.None,
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
                // Focused, the glyph takes the white focus's dark content colour, whatever its tint at rest.
                tint =
                    when {
                        !enabled -> TvButtonStyle.disabledContent
                        focused -> androidx.tv.material3.LocalContentColor.current
                        on == true -> iconTint ?: TvButtonStyle.iconOnContent
                        else -> iconTint ?: androidx.tv.material3.LocalContentColor.current
                    },
                modifier = Modifier.size(TvDimensions.iconMedium.scaled(scale)).then(iconModifier),
            )
            AnimatedVisibility(
                visible = focused,
                enter = expandHorizontally(tween(CinemaAnimation.focusDurationMs)) + fadeIn(tween(CinemaAnimation.focusDurationMs)),
                exit = shrinkHorizontally(tween(CinemaAnimation.focusDurationMs)) + fadeOut(tween(CinemaAnimation.focusDurationMs)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
                    // CinemaText's Inherit tone reads Material 3's content colour: hand it the focus colour.
                    CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides androidx.tv.material3.LocalContentColor.current,
                    ) {
                        CinemaText(
                            text = label,
                            role = TextRole.Label,
                            tone = TextTone.Inherit,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                }
            }
        }
    }
}
