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
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.colorOf
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.core.ui.viewmodels.DiagnosticsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
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

/** How much of one stack trace a TV row shows; the whole log is on the device (see CrashLog). */
private const val DETAIL_MAX_LINES = 14

/**
 * Settings → Diagnostics on TV (developer mode). Each entry is focusable so the D-pad scrolls
 * through them. See the rock solid stability resilience plan → F-30.
 */
@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val viewModel: DiagnosticsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val scale = LocalUiScale.current

    // Entry focus on the first entry, or Refresh when there is none (never Clear), or the screen
    // opened with nothing focused and the remote did nothing. Once, when the entries first load.
    val firstEntryFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    val loadedOnce = entries != null
    LaunchedEffect(loadedOnce) {
        if (!loadedOnce) return@LaunchedEffect
        if (entries.isNullOrEmpty()) {
            refreshFocus.requestFocusWithRetry()
        } else {
            firstEntryFocus.requestFocusWithRetry(
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
            TvScreenHeader(title = stringResource(R.string.settings_diagnostics_title)) {
                TvIconAction(
                    onClick = viewModel::reload,
                    icon = CinemaIcons.Refresh,
                    label = stringResource(R.string.common_refresh),
                    modifier = Modifier.focusRequester(refreshFocus),
                )
                TvIconAction(
                    onClick = viewModel::clear,
                    icon = CinemaIcons.Delete,
                    label = stringResource(R.string.settings_diagnostics_clear),
                    danger = true,
                )
            }
        }
        item {
            CinemaText(
                stringResource(R.string.settings_diagnostics_desc),
                role = TextRole.Body,
                tone = TextTone.Secondary,
            )
        }
        val loaded = entries
        if (loaded != null && loaded.isEmpty()) {
            item {
                CinemaText(
                    stringResource(R.string.settings_diagnostics_empty),
                    role = TextRole.Body,
                    tone = TextTone.Secondary,
                )
            }
        }
        itemsIndexed(loaded.orEmpty()) { index, entry ->
            // A no-op clickable Surface only so each entry takes D-pad focus (and the list scrolls
            // to it) with the standard focus border.
            Surface(
                onClick = {},
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(firstEntryFocus) else Modifier)
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
                    CinemaText(entry.title, role = TextRole.Heading, tone = TextTone.Accent)
                    // look-ok: the detail is raw log text (stack traces), set in monospace; size and colour are the Body role's
                    Text(
                        text = entry.detail,
                        style = textStyleOf(TextRole.Body).copy(fontFamily = FontFamily.Monospace),
                        color = colorOf(TextTone.Primary),
                        maxLines = DETAIL_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
