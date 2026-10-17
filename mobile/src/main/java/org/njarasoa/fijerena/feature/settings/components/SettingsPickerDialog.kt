package org.njarasoa.fijerena.feature.settings.components

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
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaRadioButton
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Radio-list picker behind a value row: one option per `label to value` pair, [selected] checked.
 * [footer] is drawn under the list (the watch delay's custom field).
 */
@Composable
fun <T> SettingsPickerDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { CinemaText(title, role = TextRole.Title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (label, value) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(value) }
                                .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CinemaRadioButton(
                            selected = value == selected,
                            onClick = null, // handled by Row clickable
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        CinemaText(text = label)
                    }
                }
                footer?.invoke(this)
            }
        },
        confirmButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(stringResource(R.string.player_back), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
    )
}
