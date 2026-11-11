@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.ui.components.TvOverlayPanel
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.TvRadioRow
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

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

/** A switch row at the top of a [TvSelectorDialog], above the choices (the Audio dialog's Night Audio). */
@Immutable
data class TvSelectorSwitch(
    val title: String,
    val description: String?,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
)

/**
 * The in-player picker shared by the audio, subtitle, quality and chapter dialogs. Selection comes
 * from the caller, never from `onFocusChanged` (which would move the "Active" mark to the row the
 * viewer looks at); focus is the row's own, so both read at once (see
 * [org.njarasoa.fijerena.ui.components.input.TvInputDefaults]).
 */
@Composable
fun TvSelectorDialog(
    title: String,
    options: List<TvSelectorOption>,
    onDismiss: () -> Unit,
    emptyText: String? = null,
    switchRow: TvSelectorSwitch? = null,
) {
    val initialFocusRequester = remember { FocusRequester() }
    val selectedIndex = options.indexOfFirst { it.selected }

    LaunchedEffect(Unit) {
        // Open on the active choice, so a viewer who only wants to confirm what plays need not look for it.
        // Falls back to the Cancel button when nothing is selected.
        initialFocusRequester.requestFocusWithRetry()
    }

    // In the player's own tree, not a Dialog window, which can break the video surface under it.
    // The title and the Cancel button stay put; only the rows scroll.
    TvOverlayPanel(
        onDismiss = onDismiss,
        title = title,
        footer = {
            CinemaSecondaryButton(
                onClick = onDismiss,
                text = stringResource(R.string.common_cancel),
                modifier =
                    Modifier
                        .align(CenterHorizontally)
                        .width(TvDimensions.selectionListWidth)
                        .then(
                            if (selectedIndex < 0 && switchRow == null) {
                                Modifier.focusRequester(initialFocusRequester)
                            } else {
                                Modifier
                            },
                        ),
            )

            CinemaText(
                text = stringResource(R.string.player_nav_hint),
                role = TextRole.BodySmall,
                tone = TextTone.Disabled,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        switchRow?.let { row ->
            TvSwitchRow(
                checked = row.checked,
                onCheckedChange = row.onCheckedChange,
                label = row.title,
                description = row.description,
                // Up reaches it from the first choice; with no choice selected, focus opens here.
                modifier = if (selectedIndex < 0) Modifier.focusRequester(initialFocusRequester) else Modifier,
            )
        }

        if (options.isEmpty() && emptyText != null) {
            CinemaText(
                text = emptyText,
                role = TextRole.Body,
                tone = TextTone.Secondary,
                modifier = Modifier.padding(vertical = Spacing.md),
            )
        }

        options.forEachIndexed { index, option ->
            TvRadioRow(
                selected = option.selected,
                onClick = option.onSelect,
                label = option.title,
                description = option.subtitle,
                // A long track name (a file name) takes two lines, then "…".
                labelMaxLines = 2,
                modifier =
                    if (index == selectedIndex) {
                        Modifier.focusRequester(initialFocusRequester)
                    } else {
                        Modifier
                    },
            )
        }
    }
}
