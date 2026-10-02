package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.DiagnosticsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/** How much of one stack trace a TV row shows; the whole log is on the device (see CrashLog). */
private const val DETAIL_MAX_LINES = 14

/**
 * Settings → Diagnostics on TV (developer mode). Each entry is focusable so the D-pad scrolls
 * through them. See docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-30.
 */
@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val viewModel: DiagnosticsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val scale = LocalUiScale.current

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_diagnostics_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        item {
            Text(
                stringResource(R.string.settings_diagnostics_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaTextSecondary,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                CinemaSecondaryButton(onClick = viewModel::reload, text = stringResource(R.string.common_refresh))
                CinemaSecondaryButton(onClick = viewModel::clear, text = stringResource(R.string.settings_diagnostics_clear))
            }
        }
        val loaded = entries
        if (loaded != null && loaded.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.settings_diagnostics_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = CinemaTextSecondary,
                )
            }
        }
        items(loaded.orEmpty()) { entry ->
            // A no-op clickable Surface only so each entry takes D-pad focus (and the list scrolls
            // to it) with the standard focus border.
            Surface(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(CornerRadius.large)),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = TvFocusTokens.restingContainer,
                        focusedContainerColor = TvFocusTokens.focusedContainer,
                        pressedContainerColor = TvFocusTokens.focusedContainer,
                    ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocusTokens.defaultScale),
                border =
                    ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(TvFocusTokens.focusBorderWidth.scaled(scale), CinemaAccentLight)),
                    ),
            ) {
                Column(Modifier.padding(Spacing.md.scaled(scale)), verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, color = CinemaAccent)
                    Text(
                        text = entry.detail,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = CinemaTextPrimary,
                        maxLines = DETAIL_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
