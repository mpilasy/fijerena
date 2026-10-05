package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Version and build, as one focusable row: the group's entry row, so Right from the rail always
 * lands somewhere and Up/Down never skip it (focus contract rule 3). OK does nothing. Below it,
 * Device info opens its own screen.
 */
@Composable
fun AboutSettingsCard(
    scale: Float,
    onDeviceInfo: () -> Unit,
    rowModifier: Modifier = Modifier,
    deviceInfoFocusRequester: FocusRequester? = null,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
            Text(
                text = stringResource(R.string.settings_about_section_title),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize =
                            MaterialTheme.typography.titleMedium.fontSize
                                .scaled(scale),
                    ),
                color = CinemaAccent,
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            TvInputListItem(
                selected = false,
                onClick = {},
                modifier = rowModifier.fillMaxWidth(),
                supportingContent = {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_about_build_format, org.njarasoa.fijerena.BuildConfig.GIT_HASH),
                            style = MaterialTheme.typography.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                        Text(
                            text = stringResource(R.string.settings_about_built_format, org.njarasoa.fijerena.BuildConfig.BUILD_TIME),
                            style = MaterialTheme.typography.bodySmall,
                            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }
                },
                headlineContent = {
                    Text(stringResource(R.string.settings_about_version_format, org.njarasoa.fijerena.BuildConfig.VERSION_NAME))
                },
            )
            TvInputListItem(
                selected = false,
                onClick = onDeviceInfo,
                modifier = Modifier.fillMaxWidth().then(deviceInfoFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
                trailingContent = { Text("›", style = MaterialTheme.typography.bodyMedium) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.device_info_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    )
                },
                headlineContent = { Text(stringResource(R.string.device_info_open)) },
            )
        }
    }
}
