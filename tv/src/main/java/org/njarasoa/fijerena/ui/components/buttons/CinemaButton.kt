@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaOrange
import org.njarasoa.fijerena.core.ui.theme.CinemaOrangeLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextDisabled
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius

/**
 * Primary Button - Main CTAs
 * Bright cyan background with dark text for maximum visibility.
 * Use for primary actions like "Play", "Start", "Continue", etc.
 *
 * @param onClick Callback when button is clicked
 * @param text Button label text
 * @param modifier Optional modifier
 * @param enabled Whether button is enabled (default true)
 */
@Composable
fun CinemaPrimaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scale = LocalUiScale.current
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors =
            ButtonDefaults.colors(
                containerColor = CinemaAccent,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaAccentLight,
                focusedContentColor = CinemaTextPrimary,
                pressedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.textMedium),
                disabledContainerColor = CinemaSurfaceVariant,
                disabledContentColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textFaint),
            ),
        scale =
            ButtonDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                pressedScale = TvFocusTokens.pressedScale,
                disabledScale = TvFocusTokens.defaultScale,
            ),
        border =
            ButtonDefaults.border(
                focusedBorder =
                    Border(
                        border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaTextPrimary),
                    ),
            ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.small.scaled(scale))),
        contentPadding =
            PaddingValues(
                horizontal = Spacing.md.scaled(scale),
                vertical = Spacing.sm.scaled(scale),
            ),
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Secondary Button - Less emphasis
 * Dark gray background with white text.
 * Use for secondary actions like "Cancel", "Back", "Settings", etc.
 *
 * @param onClick Callback when button is clicked
 * @param text Button label text
 * @param modifier Optional modifier
 * @param enabled Whether button is enabled (default true)
 */
@Composable
fun CinemaSecondaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scale = LocalUiScale.current
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors =
            ButtonDefaults.colors(
                containerColor = TvFocusTokens.restingContainer,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = TvFocusTokens.focusedContainer,
                focusedContentColor = CinemaAccentLight,
                pressedContainerColor = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.textMedium),
                disabledContainerColor = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.scrim),
                disabledContentColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textFaint),
            ),
        scale =
            ButtonDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                pressedScale = TvFocusTokens.pressedScale,
                disabledScale = TvFocusTokens.defaultScale,
            ),
        border =
            ButtonDefaults.border(
                focusedBorder =
                    Border(
                        border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaAccentLight),
                    ),
            ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.small.scaled(scale))),
        contentPadding =
            PaddingValues(
                horizontal = Spacing.md.scaled(scale),
                vertical = Spacing.sm.scaled(scale),
            ),
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Tertiary Button - Minimal emphasis
 * Transparent background with cyan outline.
 * Use for tertiary actions like "Skip", "Don't show again", etc.
 *
 * @param onClick Callback when button is clicked
 * @param text Button label text
 * @param modifier Optional modifier
 * @param enabled Whether button is enabled (default true)
 */
@Composable
fun CinemaTertiaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scale = LocalUiScale.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors =
            ButtonDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = CinemaAccent,
                focusedContainerColor = CinemaSurface.copy(alpha = CinemaAlpha.scrim),
                focusedContentColor = CinemaAccentLight,
                pressedContainerColor = CinemaSurface.copy(alpha = CinemaAlpha.tint),
                disabledContainerColor = Color.Transparent,
                disabledContentColor = CinemaAccent.copy(alpha = CinemaAlpha.textFaint),
            ),
        scale =
            ButtonDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                pressedScale = TvFocusTokens.pressedScale,
                disabledScale = TvFocusTokens.defaultScale,
            ),
        border =
            ButtonDefaults.border(
                border =
                    Border(
                        border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaAccent),
                    ),
                focusedBorder =
                    Border(
                        border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaAccentLight),
                    ),
                disabledBorder =
                    Border(
                        border =
                            BorderStroke(
                                width = TvFocusTokens.focusBorderWidth.scaled(scale),
                                color = CinemaAccent.copy(alpha = CinemaAlpha.focusedGlow),
                            ),
                    ),
            ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.small.scaled(scale))),
        contentPadding =
            PaddingValues(
                horizontal = Spacing.md.scaled(scale),
                vertical = Spacing.sm.scaled(scale),
            ),
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Icon Button - For icon-only actions
 * Circular button with icon.
 *
 * Focus is a lifted container plus an accent ring, matching every other control, rather than a
 * solid accent fill. The fill collided with the content: most call sites tint their glyph
 * [CinemaAccent] — decoratively (Add, Edit, LiveTv, the select check) or to mark an "on" state
 * (the favourite star) — so focusing the button painted an accent glyph onto an accent circle and
 * the icon disappeared at exactly the moment the viewer was pointing at it.
 *
 * @param onClick Callback when button is clicked
 * @param icon Composable icon content
 * @param modifier Optional modifier
 * @param enabled Whether button is enabled (default true)
 * @param size Button size (default 48.dp)
 */
@Composable
fun CinemaIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = Spacing.xxl,
) {
    val scale = LocalUiScale.current
    androidx.tv.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size.scaled(scale)),
        colors =
            androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = TvFocusTokens.restingContainer,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = TvFocusTokens.focusedContainer,
                focusedContentColor = CinemaTextPrimary,
                pressedContainerColor = TvFocusTokens.focusedContainer,
                pressedContentColor = CinemaTextPrimary,
            ),
        scale =
            androidx.tv.material3.ClickableSurfaceDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                pressedScale = TvFocusTokens.pressedScale,
            ),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = androidx.compose.foundation.shape.CircleShape),
        border =
            androidx.tv.material3.ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(TvFocusTokens.borderDefault.scaled(scale), CinemaTextPrimary.copy(alpha = 0.3f))),
                focusedBorder = Border(BorderStroke(TvFocusTokens.focusBorderWidth.scaled(scale), CinemaAccentLight)),
            ),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.tv.material3.LocalContentColor provides if (enabled) CinemaTextPrimary else CinemaTextDisabled
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
    size: androidx.compose.ui.unit.Dp = Spacing.xxl,
) {
    val scale = LocalUiScale.current
    androidx.tv.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size.scaled(scale)),
        colors =
            androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = CinemaOrange.copy(alpha = 0.4f),
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaOrange,
                focusedContentColor = CinemaBackground,
            ),
        scale =
            androidx.tv.material3.ClickableSurfaceDefaults.scale(
                focusedScale = 1.1f,
            ),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = androidx.compose.foundation.shape.CircleShape),
        border =
            androidx.tv.material3.ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(TvFocusTokens.focusBorderWidth.scaled(scale), CinemaOrange.copy(alpha = 0.6f))),
                focusedBorder = Border(BorderStroke(TvFocusTokens.focusBorderWidth.scaled(scale), CinemaTextPrimary)),
            ),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.tv.material3.LocalContentColor provides CinemaTextPrimary
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
 * Generic Button - shape-themed passthrough for raw call sites with custom colors that don't
 * fit the semantic Primary/Secondary/Tertiary/Danger variants above. M3's default TV button
 * shape ignores the app's [org.njarasoa.fijerena.core.ui.theme.UiStyle] shape tokens.
 *
 * Callers own [colors], so the focus outline and scale are supplied here rather than left to
 * M3's defaults — otherwise a call site that passes only a `containerColor` ends up with a button
 * whose focused and resting states are told apart by container colour alone.
 */
@Composable
fun CinemaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.colors(),
    content: @Composable RowScope.() -> Unit,
) {
    val scale = LocalUiScale.current
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        scale =
            ButtonDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                pressedScale = TvFocusTokens.pressedScale,
                disabledScale = TvFocusTokens.defaultScale,
            ),
        border =
            ButtonDefaults.border(
                focusedBorder =
                    Border(
                        border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaAccentLight),
                    ),
            ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.small.scaled(scale))),
        content = content,
    )
}

/**
 * Danger Button - Destructive actions
 * Vivid Orange background with white text for destructive actions.
 * Use for dangerous operations like "Delete", "Logout", "Clear All", etc.
 *
 * @param onClick Callback when button is clicked
 * @param text Button label text
 * @param modifier Optional modifier
 * @param enabled Whether button is enabled (default true)
 */
@Composable
fun CinemaDangerButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scale = LocalUiScale.current
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors =
            ButtonDefaults.colors(
                containerColor = CinemaOrange,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaOrangeLight,
                focusedContentColor = CinemaBackground,
                pressedContainerColor = CinemaOrange.copy(alpha = CinemaAlpha.textMedium),
                disabledContainerColor = CinemaSurfaceVariant,
                disabledContentColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textFaint),
            ),
        scale =
            ButtonDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScale,
                pressedScale = TvFocusTokens.pressedScale,
                disabledScale = TvFocusTokens.defaultScale,
            ),
        border =
            ButtonDefaults.border(
                focusedBorder =
                    Border(
                        border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaTextPrimary),
                    ),
            ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.small.scaled(scale))),
        contentPadding =
            PaddingValues(
                horizontal = Spacing.md.scaled(scale),
                vertical = Spacing.sm.scaled(scale),
            ),
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
        )
    }
}
