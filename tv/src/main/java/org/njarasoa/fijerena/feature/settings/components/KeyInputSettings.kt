package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow

/** How long Show Key Input lasts, in minutes. */
private val KEY_INPUT_MINUTES = listOf(5, 10, 30, 60)
private const val DEFAULT_KEY_INPUT_MINUTES = 10

/**
 * Show Key Input (Admin → This Device): on until [until] (ms since the epoch; 0: off). Turning it
 * on asks how long first ([KeyInputDurationPane]); off, at once.
 */
@Composable
fun KeyInputSettingsRow(
    until: Long,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    rowFocusRequester: FocusRequester? = null,
) {
    val on = until > System.currentTimeMillis()
    TvSwitchRow(
        checked = on,
        onCheckedChange = { if (it) onTurnOn() else onTurnOff() },
        label = stringResource(R.string.settings_key_input_title),
        // Focus comes back here from the picker.
        modifier = rowFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
        description =
            if (on) {
                stringResource(R.string.settings_key_input_desc) + " " +
                    stringResource(R.string.settings_key_input_until, TimeFormat.formatTime(until / 1000))
            } else {
                stringResource(R.string.settings_key_input_desc)
            },
    )
}

/** How long Show Key Input lasts: OK on a choice turns it on for that long. */
@Composable
fun KeyInputDurationPane(
    onPick: (minutes: Int) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPickerPane(
        title = stringResource(R.string.settings_key_input_duration_title),
        options = KEY_INPUT_MINUTES.map { PickerOption(pluralStringResource(R.plurals.settings_key_input_minutes, it, it), it) },
        selectedValue = DEFAULT_KEY_INPUT_MINUTES,
        onPick = onPick,
        onBack = onBack,
    )
}
