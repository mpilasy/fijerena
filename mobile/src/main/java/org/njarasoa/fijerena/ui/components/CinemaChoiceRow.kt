package org.njarasoa.fijerena.ui.components

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.contentOn

/**
 * One option in a list of choices (an audio track, a source, a section), in the chip corner. At
 * rest it has the raised fill; [selected], the strong accent with text that reads on it (Material's
 * own pick was invisible on light themes). Its text takes `TextTone.Inherit`.
 *
 * [overMedia]: a list over video (the player's channel list): both fills see-through, the selected
 * one in the accent, the text in the text colour.
 */
@Composable
fun CinemaChoiceRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    overMedia: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = CinemaTheme.colors
    val fill =
        when {
            overMedia && selected -> colors.accent.copy(alpha = CinemaAlpha.glass)
            overMedia -> colors.surfaceRaised.copy(alpha = CinemaAlpha.glass)
            selected -> colors.accentStrong
            else -> colors.surfaceRaised
        }
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = fill,
        contentColor = if (selected && !overMedia) contentOn(colors.accentStrong) else colors.textPrimary,
        shape = CinemaTheme.shapes.chip,
        content = content,
    )
}
