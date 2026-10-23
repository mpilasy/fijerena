package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaRadioButton
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Radio-list picker behind a value row: one option per `label to value` pair, [selected] checked.
 * [footer] is drawn under the list (the watch delay's custom field); [preview] is a small static
 * sample at an option's end (a colour theme's colours); [inLook] wraps an option's row (a Look's
 * row drawn in that Look).
 */
@Composable
fun <T> SettingsPickerDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    preview: (@Composable (T) -> Unit)? = null,
    inLook: (@Composable (T, @Composable () -> Unit) -> Unit)? = null,
) {
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { CinemaText(title, role = TextRole.Title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (label, value) ->
                    val row: @Composable () -> Unit = {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .then(if (inLook != null) Modifier.lookOutline(value == selected) else Modifier)
                                    .clickable { onSelect(value) }
                                    .padding(vertical = Spacing.sm)
                                    .then(if (inLook != null) Modifier.padding(horizontal = Spacing.sm) else Modifier),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CinemaRadioButton(
                                selected = value == selected,
                                onClick = null, // handled by Row clickable
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            // A Look's name as a heading, where its title font shows (Cinema's serif).
                            CinemaText(
                                text = label,
                                modifier = Modifier.weight(1f),
                                role = if (inLook != null) TextRole.Heading else TextRole.Body,
                            )
                            preview?.invoke(value)
                        }
                    }
                    inLook?.invoke(value, row) ?: row()
                }
                footer?.invoke(this)
            }
        },
        confirmButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
    )
}

/**
 * A Look picker row's outline, drawn by the Look the row is in: its card corners (rounded, square,
 * or pill), the accent at its selected width on the chosen row, a hairline on the others.
 */
@Composable
private fun Modifier.lookOutline(selected: Boolean): Modifier {
    val shape = CinemaTheme.shapes.card
    val width = if (selected) CinemaTheme.borders.focus else CinemaTheme.borders.outline
    val color = if (selected) CinemaTheme.colors.accent else CinemaTheme.colors.surfaceHigh
    return padding(vertical = Spacing.xxs).clip(shape).border(width, color, shape)
}
