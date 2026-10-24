package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.core.ui.viewmodels.DeviceInfoViewModel
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Settings → About → Device info on TV. Each section is one focus stop so the D-pad scrolls
 * through them, as on Diagnostics. See the device info screen plan.
 */
@Composable
fun DeviceInfoScreen() {
    val context = LocalContext.current
    val viewModel: DeviceInfoViewModel =
        viewModel { DeviceInfoViewModel(context.applicationContext, BuildConfig.GIT_HASH, BuildConfig.BUILD_TIME) }
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val scale = LocalUiScale.current

    // Entry focus on the first section, or Refresh when none loaded, or the screen opened with
    // nothing focused and the remote did nothing. Once, when the sections first load (or fail).
    val firstSectionFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    val loadedOnce = sections != null || failed
    LaunchedEffect(loadedOnce) {
        if (!loadedOnce) return@LaunchedEffect
        if (sections.isNullOrEmpty()) {
            refreshFocus.requestFocusWithRetry()
        } else {
            firstSectionFocus.requestFocusWithRetry(
                fallback = refreshFocus,
            )
        }
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
    ) {
        item {
            TvScreenHeader(title = stringResource(R.string.device_info_title)) {
                TvIconAction(
                    onClick = viewModel::reload,
                    icon = CinemaIcons.Refresh,
                    label = stringResource(R.string.common_refresh),
                    modifier = Modifier.focusRequester(refreshFocus),
                )
            }
        }
        item {
            CinemaText(
                stringResource(R.string.device_info_desc),
                role = TextRole.Body,
                tone = TextTone.Secondary,
            )
        }
        if (failed) {
            item {
                CinemaText(
                    stringResource(R.string.device_info_load_failed),
                    role = TextRole.Body,
                    tone = TextTone.Danger,
                )
            }
        }
        itemsIndexed(sections.orEmpty()) { index, section ->
            // A no-op clickable Surface only so each section takes D-pad focus (and the list
            // scrolls to it) with the standard focus border.
            Surface(
                onClick = {},
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(firstSectionFocus) else Modifier)
                        .leftToRail(),
                shape = ClickableSurfaceDefaults.shape(shape = cornerShape(CornerRadius.large)),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = TvFocusTokens.restingContainer,
                        focusedContainerColor = TvFocusTokens.focusedContainer,
                        pressedContainerColor = TvFocusTokens.focusedContainer,
                    ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocusTokens.defaultScale),
                border =
                    ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(TvFocusTokens.focusBorderWidth, TvFocusTokens.focusedRowOutline)),
                    ),
            ) {
                Column(Modifier.padding(Spacing.md.scaled(scale)), verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                    CinemaText(stringResource(section.title), role = TextRole.Heading, tone = TextTone.Primary)
                    section.rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))) {
                            CinemaText(
                                row.label.asString(),
                                modifier = Modifier.weight(LABEL_WEIGHT),
                                role = TextRole.Body,
                                tone = TextTone.Secondary,
                            )
                            CinemaText(
                                row.value.asString(),
                                modifier = Modifier.weight(VALUE_WEIGHT),
                                role = TextRole.Body,
                                tone = TextTone.Primary,
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
