package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun DeveloperSettingsCard(
    isDevMode: Boolean,
    onDevModeChanged: (Boolean) -> Unit,
    scale: Float,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
    ) {
        TvSwitchRow(
            checked = isDevMode,
            onCheckedChange = onDevModeChanged,
            label = stringResource(R.string.settings_developer_mode_title),
            description = stringResource(R.string.settings_developer_mode_desc),
            modifier = Modifier.padding(Spacing.md.scaled(scale)),
        )
    }
}
