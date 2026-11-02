package org.njarasoa.fijerena.core.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.MenuAction
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

/**
 * One section of a "Browse <source>" menu (Live TV, Films, TV Shows), with the same icon and label
 * on both apps.
 */
@Composable
fun browseSectionAction(
    contentType: String,
    onClick: () -> Unit,
): MenuAction =
    when (contentType) {
        ContentType.LIVE_TV -> MenuAction(CinemaIcons.LiveTv, stringResource(R.string.provider_live_tv_label), onClick = onClick)
        ContentType.MOVIES -> MenuAction(CinemaIcons.Movie, stringResource(R.string.provider_movies_label), onClick = onClick)
        else -> MenuAction(CinemaIcons.Tv, stringResource(R.string.provider_tv_shows_label), onClick = onClick)
    }
