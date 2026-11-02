package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.input.TvRadioRow

/**
 * Picks one of [options] (`label to value`) in a dialog: one [TvRadioRow] for each, the
 * [selected] one marked, then Cancel. Focus opens on the selected option (else the first, rule 5);
 * OK on an option calls [onSelect] and closes the picker. The TV twin of the phone's
 * `SettingsPickerDialog`. [subtitle] gives an option a second line (a copy's source);
 * [labelIsName] when the options are names (Heading).
 */
@Composable
fun <T> TvPickerDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    subtitle: (T) -> String? = { null },
    labelIsName: Boolean = false,
) {
    val entry = remember { FocusRequester() }
    val entryIndex = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        initialFocus = entry,
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.dialogBody),
            ) {
                options.forEachIndexed { index, (label, value) ->
                    TvRadioRow(
                        selected = value == selected,
                        onClick = {
                            onDismiss()
                            onSelect(value)
                        },
                        label = label,
                        modifier = if (index == entryIndex) Modifier.focusRequester(entry) else Modifier,
                        description = subtitle(value),
                        labelIsName = labelIsName,
                    )
                }
            }
        },
        cancel = DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
    )
}
