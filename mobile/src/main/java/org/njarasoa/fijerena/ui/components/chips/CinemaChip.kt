package org.njarasoa.fijerena.ui.components.chips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.ChipElevation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius

/**
 * Themed replacement for [FilterChip] — M3's default chip shape ignores the app's
 * [org.njarasoa.fijerena.core.ui.theme.UiStyle] shape tokens. Thin passthrough otherwise.
 */
@Composable
fun CinemaFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(CinemaCornerRadius.small),
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
    )
}

/** Themed replacement for [AssistChip], see [CinemaFilterChip]. */
@Composable
fun CinemaAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    colors: ChipColors = AssistChipDefaults.assistChipColors(),
    elevation: ChipElevation? = AssistChipDefaults.assistChipElevation(),
    border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled),
    interactionSource: MutableInteractionSource? = null,
) {
    AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(CinemaCornerRadius.small),
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
    )
}
