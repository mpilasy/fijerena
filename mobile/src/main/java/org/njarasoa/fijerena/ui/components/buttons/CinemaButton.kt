package org.njarasoa.fijerena.ui.components.buttons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaOrange
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextDisabled

/**
 * Mobile Icon Button - For icon-only actions
 * Circular button with icon, uses primary color scheme, identical to TV's aesthetic.
 */
@Composable
fun CinemaIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = CinemaTextPrimary.copy(alpha = 0.15f),
            contentColor = CinemaTextPrimary,
            disabledContainerColor = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.scrim),
            disabledContentColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textFaint),
        )
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (enabled) CinemaTextPrimary else CinemaTextDisabled
        ) {
            icon()
        }
    }
}

/**
 * Mobile Danger Icon Button
 */
@Composable
fun CinemaDangerIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = CinemaOrange.copy(alpha = 0.4f),
            contentColor = CinemaTextPrimary,
            disabledContainerColor = CinemaSurfaceVariant.copy(alpha = CinemaAlpha.scrim),
            disabledContentColor = CinemaOrange.copy(alpha = CinemaAlpha.textFaint),
        )
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides CinemaTextPrimary
        ) {
            icon()
        }
    }
}
