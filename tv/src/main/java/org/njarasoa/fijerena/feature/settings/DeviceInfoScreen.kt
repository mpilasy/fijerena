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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.BuildConfig
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.DeviceInfoViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Settings → About → Device info on TV. Each section is one focus stop so the D-pad scrolls
 * through them, as on Diagnostics. See docs/plans/archive/20261004_device-info-screen-plan.md.
 */
@Composable
fun DeviceInfoScreen() {
    val context = LocalContext.current
    val viewModel: DeviceInfoViewModel =
        viewModel { DeviceInfoViewModel(context.applicationContext, BuildConfig.GIT_HASH, BuildConfig.BUILD_TIME) }
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
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
                text = stringResource(R.string.device_info_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        item {
            Text(
                stringResource(R.string.device_info_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaTextSecondary,
            )
        }
        if (failed) {
            item {
                Text(stringResource(R.string.device_info_load_failed), style = MaterialTheme.typography.bodyMedium, color = CinemaError)
            }
        }
        item {
            CinemaSecondaryButton(onClick = viewModel::reload, text = stringResource(R.string.common_refresh))
        }
        items(sections.orEmpty()) { section ->
            // A no-op clickable Surface only so each section takes D-pad focus (and the list
            // scrolls to it) with the standard focus border.
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
                    Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium, color = CinemaAccent)
                    section.rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))) {
                            Text(
                                text = row.label.asString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CinemaTextSecondary,
                                modifier = Modifier.weight(LABEL_WEIGHT),
                            )
                            Text(
                                text = row.value.asString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CinemaTextPrimary,
                                modifier = Modifier.weight(VALUE_WEIGHT),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val LABEL_WEIGHT = 0.4f
private const val VALUE_WEIGHT = 0.6f
