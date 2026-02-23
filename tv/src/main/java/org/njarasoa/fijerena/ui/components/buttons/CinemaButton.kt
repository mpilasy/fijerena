@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaOrange
import org.njarasoa.fijerena.ui.theme.CinemaOrangeLight
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

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
    enabled: Boolean = true
) {
    val scale = LocalUiScale.current
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = CinemaAccent,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaAccentLight,
            focusedContentColor = CinemaTextPrimary,
            pressedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.textMedium),
            disabledContainerColor = CinemaSurfaceVariant,
            disabledContentColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textFaint)
        ),
        scale = ButtonDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScale,
            pressedScale = TvFocusTokens.pressedScale,
            disabledScale = TvFocusTokens.defaultScale
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaTextPrimary)
            )
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.small.scaled(scale))),
        contentPadding = PaddingValues(
            horizontal = Spacing.md.scaled(scale),
            vertical = Spacing.sm.scaled(scale)
        )
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center
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
    enabled: Boolean = true
) {
    val scale = LocalUiScale.current
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = CinemaSurfaceVariant,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaSurface,
            focusedContentColor = CinemaAccentLight,
            pressedContainerColor = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.textMedium),
            disabledContainerColor = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.scrim),
            disabledContentColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textFaint)
        ),
        scale = ButtonDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScale,
            pressedScale = TvFocusTokens.pressedScale,
            disabledScale = TvFocusTokens.defaultScale
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaAccentLight)
            )
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.small.scaled(scale))),
        contentPadding = PaddingValues(
            horizontal = Spacing.md.scaled(scale),
            vertical = Spacing.sm.scaled(scale)
        )
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center
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
    enabled: Boolean = true
) {
    val scale = LocalUiScale.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = CinemaAccent,
            focusedContainerColor = CinemaSurface.copy(alpha = CinemaAlpha.scrim),
            focusedContentColor = CinemaAccentLight,
            pressedContainerColor = CinemaSurface.copy(alpha = CinemaAlpha.tint),
            disabledContainerColor = Color.Transparent,
            disabledContentColor = CinemaAccent.copy(alpha = CinemaAlpha.textFaint)
        ),
        scale = ButtonDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScale,
            pressedScale = TvFocusTokens.pressedScale,
            disabledScale = TvFocusTokens.defaultScale
        ),
        border = ButtonDefaults.border(
            border = Border(
                border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaAccent)
            ),
            focusedBorder = Border(
                border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaAccentLight)
            ),
            disabledBorder = Border(
                border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaAccent.copy(alpha = CinemaAlpha.focusedGlow))
            )
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.small.scaled(scale))),
        contentPadding = PaddingValues(
            horizontal = Spacing.md.scaled(scale),
            vertical = Spacing.sm.scaled(scale)
        )
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Icon Button - For icon-only actions
 * Square button with icon, uses primary color scheme.
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
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    val scale = LocalUiScale.current
    Button(
        onClick = onClick,
        modifier = modifier.size(size.scaled(scale)),
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = CinemaSurfaceVariant,
            contentColor = CinemaAccent,
            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
            focusedContentColor = CinemaAccentLight,
            pressedContainerColor = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.textMedium),
            disabledContainerColor = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.scrim),
            disabledContentColor = CinemaAccent.copy(alpha = CinemaAlpha.textFaint)
        ),
        scale = ButtonDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = 1.15f,
            pressedScale = 0.9f,
            disabledScale = TvFocusTokens.defaultScale
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaAccentLight)
            )
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.small.scaled(scale))),
        contentPadding = PaddingValues(Spacing.xs.scaled(scale))
    ) {
        icon()
    }
}

/**
 * Danger Icon Button - For destructive icon-only actions
 * Square button with icon, uses orange/danger color scheme.
 *
 * @param onClick Callback when button is clicked
 * @param icon Composable icon content
 * @param modifier Optional modifier
 * @param enabled Whether button is enabled (default true)
 * @param size Button size (default 48.dp)
 */
@Composable
fun CinemaDangerIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    val scale = LocalUiScale.current
    Button(
        onClick = onClick,
        modifier = modifier.size(size.scaled(scale)),
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = CinemaOrange.copy(alpha = CinemaAlpha.tint),
            contentColor = CinemaOrange,
            focusedContainerColor = CinemaOrange,
            focusedContentColor = CinemaTextPrimary,
            pressedContainerColor = CinemaOrange.copy(alpha = CinemaAlpha.textMedium),
            disabledContainerColor = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.scrim),
            disabledContentColor = CinemaOrange.copy(alpha = CinemaAlpha.textFaint)
        ),
        scale = ButtonDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = 1.15f,
            pressedScale = 0.9f,
            disabledScale = TvFocusTokens.defaultScale
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaTextPrimary)
            )
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.small.scaled(scale))),
        contentPadding = PaddingValues(Spacing.xs.scaled(scale))
    ) {
        icon()
    }
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
    enabled: Boolean = true
) {
    val scale = LocalUiScale.current
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = CinemaOrange,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaOrangeLight,
            focusedContentColor = CinemaBackground,
            pressedContainerColor = CinemaOrange.copy(alpha = CinemaAlpha.textMedium),
            disabledContainerColor = CinemaSurfaceVariant,
            disabledContentColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textFaint)
        ),
        scale = ButtonDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScale,
            pressedScale = TvFocusTokens.pressedScale,
            disabledScale = TvFocusTokens.defaultScale
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = TvFocusTokens.focusBorderWidth.scaled(scale), color = CinemaTextPrimary)
            )
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.small.scaled(scale))),
        contentPadding = PaddingValues(
            horizontal = Spacing.md.scaled(scale),
            vertical = Spacing.sm.scaled(scale)
        )
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center
        )
    }
}
