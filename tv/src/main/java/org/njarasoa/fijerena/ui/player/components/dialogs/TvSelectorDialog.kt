@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaTextDisabled
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.TvGlassPanel
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.TvOptionRow
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * One choice offered by a [TvSelectorDialog].
 *
 * [selected] is what is *actually* active — the current audio track, the current quality — never
 * whatever happens to hold focus.
 */
@Immutable
data class TvSelectorOption(
    val title: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
    val subtitle: String? = null,
)

/**
 * The in-player picker shared by the audio, subtitle, quality and chapter dialogs.
 *
 * Those were four near-identical files of about 250 lines each, every one re-deriving the same
 * button colour and border matrix by hand, and every one making the same mistake: they drove their
 * selected index from `onFocusChanged`, so arrowing through the list re-pointed the "Active"
 * marker and the dialog reported whichever row the viewer was looking at as the current track.
 *
 * Here selection comes from the caller and focus is the row's own business, which is also what
 * lets both read at once — see
 * [org.njarasoa.fijerena.ui.components.input.TvInputDefaults].
 */
@Composable
fun TvSelectorDialog(
    title: String,
    options: List<TvSelectorOption>,
    onDismiss: () -> Unit,
    emptyText: String? = null,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val initialFocusRequester = remember { FocusRequester() }
    val selectedIndex = options.indexOfFirst { it.selected }

    LaunchedEffect(Unit) {
        // Open on the active choice, so a viewer who only wants to confirm what is playing does
        // not have to hunt for it. Falls back to the Cancel button when nothing is selected.
        try {
            initialFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    BackHandler { onDismiss() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CinemaBackground.copy(alpha = CinemaAlpha.overlayHeavy)),
        contentAlignment = Alignment.Center,
    ) {
        TvGlassPanel(
            modifier =
                Modifier
                    .width(TvDimensions.dialogWidth)
                    .heightIn(max = screenHeight * 0.8f)
                    .padding(Spacing.xxl),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(Spacing.xxl)
                        .verticalScroll(rememberScrollState())
                        // Keep focus inside the dialog. `exit = Cancel` (carried over from the
                        // four dialogs this replaced) also blocked movement *between* the options,
                        // so the picker opened on the active track and then would not move at all.
                        .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = TvFocusTokens.emphasisWeight,
                )

                if (options.isEmpty() && emptyText != null) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CinemaTextSecondary,
                        modifier = Modifier.padding(vertical = Spacing.md),
                    )
                }

                options.forEachIndexed { index, option ->
                    TvOptionRow(
                        title = option.title,
                        selected = option.selected,
                        onClick = option.onSelect,
                        subtitle = option.subtitle,
                        activeLabel = stringResource(R.string.player_active),
                        modifier =
                            if (index == selectedIndex) {
                                Modifier.focusRequester(initialFocusRequester)
                            } else {
                                Modifier
                            },
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                CinemaSecondaryButton(
                    onClick = onDismiss,
                    text = stringResource(R.string.common_cancel),
                    modifier =
                        Modifier
                            .align(CenterHorizontally)
                            .width(TvDimensions.selectionListWidth)
                            .then(
                                if (selectedIndex < 0) Modifier.focusRequester(initialFocusRequester) else Modifier,
                            ),
                )

                Text(
                    text = stringResource(R.string.player_nav_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = CinemaTextDisabled,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
