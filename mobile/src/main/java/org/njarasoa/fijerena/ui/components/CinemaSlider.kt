package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * The phone's slider (the player's scrubber): [value] from 0 to 1, an accent track and a round
 * thumb big enough to drag. [onValueChange] fires through the drag, [onValueChangeFinished] at its
 * end. Feature code never calls Material's slider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinemaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors =
        SliderDefaults.colors(
            thumbColor = CinemaTheme.colors.accent,
            activeTrackColor = CinemaTheme.colors.accent,
            inactiveTrackColor = CinemaTheme.colors.textPrimary.copy(alpha = CinemaAlpha.tint),
        )
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        colors = colors,
        interactionSource = interactionSource,
        // The default thumb is a 4dp-wide bar: a round, bigger thumb reads as a knob to drag.
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = colors,
                thumbSize = DpSize(MobileDimensions.playerScrubberThumbSize, MobileDimensions.playerScrubberThumbSize),
            )
        },
    )
}
