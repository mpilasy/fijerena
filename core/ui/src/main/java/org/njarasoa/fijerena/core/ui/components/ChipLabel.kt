package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.LocalCinemaType
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.theme.UiChipLabelTokens
import org.njarasoa.fijerena.core.ui.theme.UiStyle

/**
 * Gives [content], a chip's label, the Look's chip label style ([UiChipLabelTokens]): the one place
 * that reads it, for the TV's `TvSelectableButton` and the phone's chips. It changes only
 * [TextRole.Label] text, so a badge in the chip stays as it is; `keepCase` text keeps its case.
 */
@Composable
fun ProvideChipLabel(content: @Composable () -> Unit) {
    val style = LocalUiStyle.current
    val chip = style.type.chipLabel ?: return content()
    val type = CinemaTheme.type
    val chipType = remember(type, chip) { type.copy(label = type.label.trackedForChip(chip)) }
    val chipStyle = remember(style) { style.forChipLabel() }
    CompositionLocalProvider(LocalCinemaType provides chipType, LocalUiStyle provides chipStyle, content = content)
}

/** The Look as a chip's label reads it: its capitals decided by [UiChipLabelTokens], unchanged when it has none. */
internal fun UiStyle.forChipLabel(): UiStyle = type.chipLabel?.let { copy(type = type.copy(capsLabels = it.caps)) } ?: this

internal fun TextStyle.trackedForChip(chip: UiChipLabelTokens): TextStyle = copy(letterSpacing = (letterSpacing.value + chip.trackingSp).sp)
