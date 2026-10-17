@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentDark
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaOrange
import org.njarasoa.fijerena.core.ui.theme.CinemaOrangeLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextDisabled
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import kotlin.math.roundToInt
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius

/**
 * A text button, looking as [TvButtonStyle] says every TV button does: [kind]'s fill (and outline),
 * the Display style's shape, the label centred at any width. Focus adds the ring and the style's
 * focus scale and keeps the rest of the look, so the focused button stands out from the others
 * on the screen. Built on a clickable [Surface] rather than TV Material's `Button`, whose label
 * stays at the left of a wide button.
 */
@Composable
fun CinemaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Other,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val scale = LocalUiScale.current
    val shape = TvButtonStyle.shape
    val container = TvButtonStyle.container(kind)
    val contentColor = TvButtonStyle.content(kind)
    val focusRing = Border(BorderStroke(TvFocusTokens.focusBorderWidth.scaled(scale), TvButtonStyle.focusRing), shape = shape)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .semantics { role = Role.Button }
                .defaultMinSize(minHeight = TvButtonStyle.minHeight.scaled(scale)),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = container,
                contentColor = contentColor,
                focusedContainerColor = container,
                focusedContentColor = contentColor,
                pressedContainerColor = container,
                pressedContentColor = contentColor,
                disabledContainerColor = TvButtonStyle.disabledContainer,
                disabledContentColor = TvButtonStyle.disabledContent,
            ),
        scale =
            ClickableSurfaceDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                pressedScale = TvFocusTokens.pressedScale,
                disabledScale = TvFocusTokens.defaultScale,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = TvButtonStyle.outline(kind)?.let { Border(it, shape = shape) } ?: Border.None,
                focusedBorder = focusRing,
                pressedBorder = focusRing,
                disabledBorder = Border.None,
                focusedDisabledBorder = focusRing,
            ),
    ) {
        // Dialog content is often phone Material's Text, which reads its own locals: the same
        // colour and label style as TV Material's.
        CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides LocalContentColor.current,
            androidx.compose.material3.LocalTextStyle provides textStyleOf(TextRole.Label),
        ) {
            ProvideTextStyle(textStyleOf(TextRole.Label)) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(
                                horizontal = TvButtonStyle.horizontalPadding.scaled(scale),
                                vertical = TvButtonStyle.verticalPadding.scaled(scale),
                            ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale), Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

/**
 * A stack of buttons, as [TvButtonStyle] lays it out: an invisible area
 * [TvButtonStyle.AREA_WIDTH_FRACTION] of the page wide, the buttons one under the other, centred in
 * it, all as wide as the widest label. Place it where the stack goes ([modifier]); the buttons
 * inside need no width of their own.
 */
@Composable
fun CinemaButtonArea(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val gap = Spacing.sm.scaled(LocalUiScale.current)
    Layout(content = content, modifier = modifier.fillMaxWidth(TvButtonStyle.AREA_WIDTH_FRACTION)) { measurables, constraints ->
        val width = constraints.maxWidth
        val buttonWidth = (measurables.maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) } ?: 0).coerceAtMost(width)
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = buttonWidth, maxWidth = buttonWidth, minHeight = 0)) }
        val gapPx = gap.roundToPx()
        val height = placeables.sumOf { it.height } + gapPx * (placeables.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var y = 0
            placeables.forEach {
                it.place((width - buttonWidth) / 2, y)
                y += it.height + gapPx
            }
        }
    }
}

/** A [CinemaButton] of [kind] with a text label. */
@Composable
fun CinemaTextLabelButton(
    onClick: () -> Unit,
    text: String,
    kind: ButtonKind,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    CinemaButton(onClick = onClick, modifier = modifier, kind = kind, enabled = enabled) {
        CinemaText(text = text, role = TextRole.Label, tone = TextTone.Inherit, textAlign = TextAlign.Center)
    }
}

/** The screen's main action ([ButtonKind.Main]): Save, Add, Play. */
@Composable
fun CinemaPrimaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = CinemaTextLabelButton(onClick, text, ButtonKind.Main, modifier, enabled)

/** Any other action ([ButtonKind.Other]): Cancel, Back, Retry. */
@Composable
fun CinemaSecondaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = CinemaTextLabelButton(onClick, text, ButtonKind.Other, modifier, enabled)

/** Deletes or wipes something ([ButtonKind.Danger]). */
@Composable
fun CinemaDangerButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = CinemaTextLabelButton(onClick, text, ButtonKind.Danger, modifier, enabled)

/**
 * Icon Button - For icon-only actions
 * Circular button with icon.
 *
 * Focus is a lifted container plus an accent ring, matching every other control, rather than a
 * solid accent fill. The fill collided with the content: most call sites tint their glyph
 * [CinemaAccent] — decoratively (Add, Edit, LiveTv, the select check) or to mark an "on" state
 * (the favourite star) — so focusing the button painted an accent glyph onto an accent circle and
 * the icon disappeared at exactly the moment the viewer was pointing at it.
 */
@Composable
fun CinemaIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = TvButtonStyle.iconButtonSize,
) {
    val scale = LocalUiScale.current
    androidx.tv.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size.scaled(scale)),
        colors =
            androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = TvButtonStyle.iconContainer,
                contentColor = TvButtonStyle.iconContent,
                focusedContainerColor = TvButtonStyle.iconFocusedContainer,
                focusedContentColor = TvButtonStyle.iconContent,
                pressedContainerColor = TvButtonStyle.iconFocusedContainer,
                pressedContentColor = TvButtonStyle.iconContent,
            ),
        scale =
            androidx.tv.material3.ClickableSurfaceDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                pressedScale = TvFocusTokens.pressedScale,
            ),
        shape =
            androidx.tv.material3.ClickableSurfaceDefaults
                .shape(shape = CinemaTheme.shapes.control),
        border =
            androidx.tv.material3.ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(TvFocusTokens.borderDefault.scaled(scale), TvButtonStyle.iconOutline)),
                focusedBorder = Border(BorderStroke(TvFocusTokens.focusBorderWidth.scaled(scale), TvButtonStyle.iconFocusRing)),
            ),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.tv.material3.LocalContentColor provides if (enabled) TvButtonStyle.iconContent else TvButtonStyle.disabledContent,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        }
    }
}

/**
 * Danger Icon Button - Circular variant with standard TV interaction
 */
@Composable
fun CinemaDangerIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = TvButtonStyle.iconButtonSize,
) {
    val scale = LocalUiScale.current
    androidx.tv.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size.scaled(scale)),
        colors =
            androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = TvButtonStyle.dangerIconContainer,
                contentColor = TvButtonStyle.iconContent,
                focusedContainerColor = TvButtonStyle.dangerIconFocusedContainer,
                focusedContentColor = TvButtonStyle.iconContent,
            ),
        scale =
            androidx.tv.material3.ClickableSurfaceDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                pressedScale = TvFocusTokens.pressedScale,
            ),
        shape =
            androidx.tv.material3.ClickableSurfaceDefaults
                .shape(shape = CinemaTheme.shapes.control),
        border =
            androidx.tv.material3.ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(TvFocusTokens.borderDefault.scaled(scale), TvButtonStyle.dangerIconOutline)),
                focusedBorder = Border(BorderStroke(TvFocusTokens.focusBorderWidth.scaled(scale), TvButtonStyle.focusRing)),
            ),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.tv.material3.LocalContentColor provides TvButtonStyle.iconContent,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        }
    }
}
