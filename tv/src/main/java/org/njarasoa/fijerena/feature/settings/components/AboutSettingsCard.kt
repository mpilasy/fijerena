package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.BuildConfig
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary

/**
 * About: version and build as one focusable row — the group's entry row, so Right from the rail
 * always lands somewhere and Up/Down never skip it (focus contract rule 3); OK does nothing. Then
 * Device info, and Diagnostics while the profile in use has developer mode on ([onDiagnostics]
 * not null; switched on each profile's page). Both open their own screens.
 */
@Composable
fun AboutSettingsCard(
    onDeviceInfo: () -> Unit,
    onDiagnostics: (() -> Unit)?,
    rowModifier: Modifier = Modifier,
    deviceInfoFocusRequester: FocusRequester? = null,
    diagnosticsFocusRequester: FocusRequester? = null,
) {
    SettingsSection(title = stringResource(R.string.settings_about_section_title)) {
        SettingsRow(
            title = stringResource(R.string.settings_about_version_format, BuildConfig.VERSION_NAME),
            description = null,
            onClick = {},
            modifier = rowModifier,
            chevron = false,
            supporting = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_about_build_format, BuildConfig.GIT_HASH),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    )
                    Text(
                        text = stringResource(R.string.settings_about_built_format, BuildConfig.BUILD_TIME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    )
                }
            },
        )
        SettingsRow(
            title = stringResource(R.string.device_info_open),
            description = stringResource(R.string.device_info_desc),
            onClick = onDeviceInfo,
            focusRequester = deviceInfoFocusRequester,
        )
        if (onDiagnostics != null) {
            SettingsRow(
                title = stringResource(R.string.settings_diagnostics_title),
                description = stringResource(R.string.settings_diagnostics_desc),
                onClick = onDiagnostics,
                focusRequester = diagnosticsFocusRequester,
            )
        }
    }
}
