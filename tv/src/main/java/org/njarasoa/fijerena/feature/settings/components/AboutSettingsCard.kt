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
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary

/**
 * About: version and build as one focusable row — the group's entry row, so Right from the rail
 * always lands somewhere and Up/Down never skip it (focus contract rule 3); OK counts toward
 * developer mode ([onVersionClick], 7 taps). Then
 * Device info, Terms of use, Open-source licences, and Diagnostics while the profile in use has developer mode on ([onDiagnostics]
 * not null; switched on each profile's page). Both open their own screens. Last, the copyright and licence notice.
 */
@Composable
fun AboutSettingsCard(
    onDeviceInfo: () -> Unit,
    onDiagnostics: (() -> Unit)?,
    onLicenses: () -> Unit = {},
    onTerms: () -> Unit = {},
    onVersionClick: () -> Unit = {},
    rowModifier: Modifier = Modifier,
    deviceInfoFocusRequester: FocusRequester? = null,
    licensesFocusRequester: FocusRequester? = null,
    termsFocusRequester: FocusRequester? = null,
    diagnosticsFocusRequester: FocusRequester? = null,
) {
    SettingsSection(title = stringResource(R.string.settings_about_section_title)) {
        SettingsRow(
            title = stringResource(R.string.settings_about_version_format, BuildConfig.VERSION_NAME),
            description = null,
            onClick = onVersionClick,
            modifier = rowModifier,
            chevron = false,
            supporting = {
                Column {
                    CinemaText(
                        stringResource(R.string.settings_about_build_format, BuildConfig.GIT_HASH),
                        role = TextRole.Body,
                        tone = TextTone.Secondary,
                    )
                    CinemaText(
                        stringResource(R.string.settings_about_built_format, BuildConfig.BUILD_TIME),
                        role = TextRole.Body,
                        tone = TextTone.Secondary,
                    )
                    CinemaText(
                        stringResource(R.string.app_disclaimer_no_content),
                        role = TextRole.Body,
                        tone = TextTone.Secondary,
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
        SettingsRow(
            title = stringResource(R.string.terms_title),
            description = stringResource(R.string.terms_desc),
            onClick = onTerms,
            focusRequester = termsFocusRequester,
        )
        SettingsRow(
            title = stringResource(R.string.licenses_title),
            description = stringResource(R.string.licenses_desc),
            onClick = onLicenses,
            focusRequester = licensesFocusRequester,
        )
        if (onDiagnostics != null) {
            SettingsRow(
                title = stringResource(R.string.settings_diagnostics_title),
                description = stringResource(R.string.settings_diagnostics_desc),
                onClick = onDiagnostics,
                focusRequester = diagnosticsFocusRequester,
            )
        }
        // GPL-3.0 "Appropriate Legal Notices": copyright, licence, no warranty, source. A note, not
        // in the version row: two more lines there made the Console Look's pill clip its corners.
        SettingsNote(stringResource(R.string.app_copyright) + "\n" + stringResource(R.string.app_license_notice))
    }
}
