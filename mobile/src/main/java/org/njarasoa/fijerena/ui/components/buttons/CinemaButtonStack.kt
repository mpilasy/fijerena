package org.njarasoa.fijerena.ui.components.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/** One button of a [CinemaButtonStack]: its text, its kind and what it does. */
data class StackButton(
    val label: String,
    val kind: ButtonKind = ButtonKind.Other,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The page actions of a phone screen or panel, the phone side of TV's `CinemaButtonArea`: one
 * button under the other, all the full width and the same size, with one gap between them. Give
 * one [ButtonKind.Main] at most (the page's main action), first.
 */
@Composable
fun CinemaButtonStack(
    buttons: List<StackButton>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
        buttons.forEach { button ->
            CinemaButton(
                onClick = button.onClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = button.enabled,
                kind = button.kind,
            ) {
                CinemaText(button.label, role = TextRole.Label, tone = TextTone.Inherit)
            }
        }
    }
}
