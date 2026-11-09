package org.njarasoa.fijerena.core.ui.newepisodes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.showSeriesId
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.BadgeKind
import org.njarasoa.fijerena.core.ui.components.CinemaBadge

/** The **New** badge of a show's card or row while the profile has new episodes of the show. */
@Composable
fun NewEpisodesBadge(
    item: MediaItem,
    modifier: Modifier = Modifier,
) {
    val seriesId = item.takeIf { it.mediaType == MediaType.SERIES }?.showSeriesId(ContentType.TV_SHOWS)
    if (seriesId != null) NewEpisodesBadge(item.providerId, seriesId, modifier)
}

/** As above for a row that has only its source and show id, such as a search result. */
@Composable
fun NewEpisodesBadge(
    providerId: Long,
    seriesId: String,
    modifier: Modifier = Modifier,
) {
    if (LocalNewEpisodes.current.episodeIds(providerId, seriesId).isNotEmpty()) NewEpisodeMark(modifier)
}

/** The **New** mark, an accent pill: the badge of a show and the mark of an episode. */
@Composable
fun NewEpisodeMark(modifier: Modifier = Modifier) {
    CinemaBadge(text = stringResource(R.string.new_episodes_badge), modifier = modifier, kind = BadgeKind.Accent, maxLines = 1)
}
