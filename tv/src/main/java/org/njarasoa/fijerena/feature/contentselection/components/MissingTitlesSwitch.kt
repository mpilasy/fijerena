package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction

/**
 * The switch for the titles no source has (UX-D7), on a Trending row and on each row of a person's
 * page: one eye, plain while off, filled in the accent while on. Its label names the setting with
 * its state ("Titles Not in Your Sources: Off"); OK flips it. The row's new cards can push the
 * switch off screen (an empty row on a person's page): it brings itself back into view while it
 * has focus.
 */
@Composable
fun TvMissingTitlesSwitch(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(on) {
        // The cards come a moment after the flip: follow them for a short while.
        repeat(SETTLE_STEPS) {
            delay(SETTLE_STEP_MS)
            if (focused) requester.bringIntoView()
        }
    }
    TvIconAction(
        onClick = { onChange(!on) },
        icon = if (on) CinemaIcons.VisibilityFilled else CinemaIcons.VisibilityOutline,
        label =
            stringResource(
                R.string.chart_missing_switch,
                stringResource(if (on) R.string.chart_missing_on else R.string.chart_missing_off),
            ),
        modifier =
            modifier
                .bringIntoViewRequester(requester)
                .onFocusChanged { focused = it.hasFocus },
        on = on,
    )
}

private const val SETTLE_STEPS = 5
private const val SETTLE_STEP_MS = 120L
