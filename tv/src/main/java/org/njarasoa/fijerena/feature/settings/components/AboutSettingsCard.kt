package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R

/**
 * About: About Fijerena — the group's entry row, so Right from the rail always lands somewhere
 * and Up/Down never skip it (focus contract rule 3) — then Device info and Logs (Diagnostics,
 * open to every profile that has Settings), each opening its own screen. The version, the
 * notices, the links, the terms, the licences and the credits are on About Fijerena.
 */
@Composable
fun AboutSettingsCard(
    onAbout: () -> Unit,
    onDeviceInfo: () -> Unit,
    onDiagnostics: () -> Unit = {},
    rowModifier: Modifier = Modifier,
    aboutFocusRequester: FocusRequester? = null,
    deviceInfoFocusRequester: FocusRequester? = null,
    diagnosticsFocusRequester: FocusRequester? = null,
) {
    SettingsSection(title = stringResource(R.string.settings_about_section_title)) {
        SettingsRow(
            title = stringResource(R.string.about_title),
            description = stringResource(R.string.about_desc),
            onClick = onAbout,
            modifier = rowModifier,
            focusRequester = aboutFocusRequester,
        )
        SettingsRow(
            title = stringResource(R.string.device_info_open),
            description = stringResource(R.string.device_info_desc),
            onClick = onDeviceInfo,
            focusRequester = deviceInfoFocusRequester,
        )
        SettingsRow(
            title = stringResource(R.string.settings_diagnostics_title),
            description = stringResource(R.string.settings_diagnostics_desc),
            onClick = onDiagnostics,
            focusRequester = diagnosticsFocusRequester,
        )
    }
}
