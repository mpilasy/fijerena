@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.player.components.dialogs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import org.njarasoa.fijerena.ui.components.input.TvOptionRow
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

/**
 * The in-player picker shared by the audio, subtitle, quality and chapter dialogs.
 *
 * Selection comes from the caller, never from `onFocusChanged` (which would re-point the "Active"
 * marker at whichever row the viewer is looking at); focus is the row's own business, which is
 * also what lets both read at once — see
 * [org.njarasoa.fijerena.ui.components.input.TvInputDefaults].
 */
@Composable
fun TvSelectorDialog(
    title: String,
    options: List<TvSelectorOption>,
    onDismiss: () -> Unit,
    emptyText: String? = null,
) {
    val initialFocusRequester = remember { FocusRequester() }
    val selectedIndex = options.indexOfFirst { it.selected }

    LaunchedEffect(Unit) {
        // Open on the active choice, so a viewer who only wants to confirm what is playing does
        // not have to hunt for it. Falls back to the Cancel button when nothing is selected.
        initialFocusRequester.requestFocusWithRetry()
    }

    // In the player's own tree, not a Dialog window, which can break the video surface under it.
    TvOverlayPanel(onDismiss = onDismiss) {
        CinemaText(
            text = title,
            role = TextRole.Title,
            tone = TextTone.Primary,
            emphasis = true,
        )

        if (options.isEmpty() && emptyText != null) {
            CinemaText(
                text = emptyText,
                role = TextRole.Body,
                tone = TextTone.Secondary,
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

        CinemaText(
            text = stringResource(R.string.player_nav_hint),
            role = TextRole.BodySmall,
            tone = TextTone.Disabled,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
