package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/** Diagnostics, shown while the profile in use has developer mode on (switched on its profile page). */
@Composable
fun DeveloperSettingsCard(
    onDiagnostics: () -> Unit,
    scale: Float,
    diagnosticsButtonFocusRequester: FocusRequester? = null,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_diagnostics_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
            CinemaSecondaryButton(
                onClick = onDiagnostics,
                text = stringResource(R.string.settings_diagnostics_open),
                modifier = diagnosticsButtonFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
            )
        }
    }
}
