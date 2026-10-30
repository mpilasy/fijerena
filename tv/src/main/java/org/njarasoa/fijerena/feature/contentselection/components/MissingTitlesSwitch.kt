package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction

/**
 * The switch for the titles no source has (UX-D7), on a Trending row and on each row of a person's
 * page: one eye, plain while off, filled in the accent while on. Its label names the setting with
 * its state ("Titles Not in Your Sources: Off"); OK flips it.
 */
@Composable
fun TvMissingTitlesSwitch(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvIconAction(
        onClick = { onChange(!on) },
        icon = if (on) CinemaIcons.VisibilityFilled else CinemaIcons.VisibilityOutline,
        label =
            stringResource(
                R.string.chart_missing_switch,
                stringResource(if (on) R.string.chart_missing_on else R.string.chart_missing_off),
            ),
        modifier = modifier,
        on = on,
    )
}
