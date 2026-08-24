package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSuccess

/**
 * "Watched" marker for content laid over artwork.
 *
 * A bare green check tinted onto a thumbnail disappears against bright or busy frames, so this
 * paints a filled success-coloured disc with a white check and a dark ring: the ring separates it
 * from whatever is behind it, and the white glyph carries the contrast at 10 feet.
 */
@Composable
fun WatchedBadge(
    modifier: Modifier = Modifier,
    size: Dp = CinemaSpacing.lg + CinemaSpacing.xxs,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .background(CinemaSuccess, CircleShape)
                .border(RING_WIDTH, Color.Black.copy(alpha = RING_ALPHA), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = stringResource(R.string.content_watched_badge),
            tint = Color.White,
            modifier = Modifier.size(size * GLYPH_FRACTION),
        )
    }
}

private val RING_WIDTH = CinemaSpacing.xxxs
private const val RING_ALPHA = 0.55f
private const val GLYPH_FRACTION = 0.68f
