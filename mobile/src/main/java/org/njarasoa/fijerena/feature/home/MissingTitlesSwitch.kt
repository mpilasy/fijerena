package org.njarasoa.fijerena.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.ui.components.buttons.IconAction

/**
 * The switch for the titles no source has (UX-D7), at the end of a Trending row's title and of each
 * row's title on a person's page: one eye, plain while off, filled in the accent while on. Its
 * tooltip names the setting with its state ("Titles Not in Your Sources: Off"); a tap flips it.
 */
@Composable
internal fun MissingTitlesSwitch(
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    IconAction(
        onClick = { onChange(!on) },
        icon = if (on) CinemaIcons.VisibilityFilled else CinemaIcons.VisibilityOutline,
        label =
            stringResource(
                R.string.chart_missing_switch,
                stringResource(if (on) R.string.chart_missing_on else R.string.chart_missing_off),
            ),
        on = on,
    )
}
