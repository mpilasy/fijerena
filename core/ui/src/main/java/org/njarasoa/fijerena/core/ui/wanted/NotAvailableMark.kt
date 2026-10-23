package org.njarasoa.fijerena.core.ui.wanted

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme

/**
 * The "Not available" mark over a poster: a TMDB title no source has, in a related row (wishlist
 * UX plan, Phase 4). A cloud on a dark chip, so it reads on any art. [iconSize] is the app's own.
 */
@Composable
fun NotAvailableMark(
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(CinemaTheme.shapes.chip)
                .background(CinemaTheme.colors.background.copy(alpha = CinemaAlpha.scrim))
                .padding(CinemaSpacing.xxs),
    ) {
        Icon(
            imageVector = CinemaIcons.CloudOff,
            contentDescription = stringResource(R.string.wanted_not_available),
            tint = CinemaTheme.colors.textPrimary,
            modifier = Modifier.size(iconSize),
        )
    }
}
