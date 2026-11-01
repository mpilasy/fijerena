package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaCodeText
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.DiagnosticsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
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
                TvEmptyState(message = stringResource(R.string.settings_diagnostics_empty))
            }
        }
        itemsIndexed(loaded.orEmpty()) { index, entry ->
            val causes = entry.causes
            // An entry with a stack trace opens and closes on OK; one with a plain message does nothing.
            var expanded by rememberSaveable(entry.timestampMs, entry.title) { mutableStateOf(false) }
            // A clickable card so each entry takes D-pad focus (and the list scrolls to it) with
            // the standard focus border.
            TvCard(
                onClick = { if (causes != null) expanded = !expanded },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(firstEntryFocus) else Modifier)
                        .leftToRail(),
                look = TvCardLooks.infoBlock(),
            ) {
                Row(Modifier.padding(Spacing.md.scaled(scale)), horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                        CinemaText(entry.title, role = TextRole.Heading, tone = TextTone.Primary)
                        // The detail is raw log text (stack traces).
                        CinemaCodeText(
                            text =
                                when {
                                    causes == null -> entry.detail
                                    expanded -> causes.chain.joinToString("\n")
                                    else -> causes.rootCause
                                },
                            role = TextRole.Body,
                            maxLines = if (causes != null && !expanded) 1 else DETAIL_MAX_LINES,
                        )
                    }
                    if (causes != null) {
                        Icon(
                            imageVector = if (expanded) CinemaIcons.ExpandLess else CinemaIcons.ExpandMore,
                            contentDescription =
                                if (expanded) {
                                    stringResource(R.string.settings_diagnostics_hide_details)
                                } else {
                                    stringResource(R.string.settings_diagnostics_show_details)
                                },
                            modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                        )
                    }
                }
            }
        }
    }
}
