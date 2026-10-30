package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.components.TitleBadges
import org.njarasoa.fijerena.core.ui.wanted.NotAvailableMark
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * A film or show in a row of titles (Home's favourite, Wishlist and chart rows, an actor's page,
 * More Like This): its poster as 2:3 art, [MobileDimensions.posterCardWidth] wide (about three a
 * screen), the letter when there is no poster, then its name and [subtitle] (CardLines).
 * [subtitleLines]: the lines of [subtitle], all kept, so a row's cards are one height (two on an
 * actor's page, where the year and the role do not fit on one line of a narrow card).
 */
@Composable
fun PosterCard(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    thumbnailType: ThumbnailContentType,
    sourceName: String?,
    providerId: Long,
    // Null: no long-press menu.
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    // A TMDB title no source has: the Not available mark on its art.
    notAvailable: Boolean = false,
    // False: [title] shows as it is, without tags (a TMDB list's title, MediaItem.listTitle).
    tags: Boolean = true,
    subtitleLines: Int = 1,
    onClick: () -> Unit,
) {
    val width = MobileDimensions.posterCardWidth
    CinemaCard(modifier = modifier.width(width).homeCardClicks(onClick, onLongClick)) {
        Box {
            CinemaThumbnail(
                url = thumbnailUrl,
                fallbackLetter = title.firstOrNull(),
                contentType = thumbnailType,
                overlayGradient = true,
                modifier = Modifier.width(width).aspectRatio(POSTER_ASPECT),
            )
            if (notAvailable) {
                NotAvailableMark(
                    iconSize = MobileDimensions.iconSmall,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xxs),
                )
            }
        }
        // Two lines for the title, as on a store's poster rows: a narrow card cuts long names early.
        CardLines(title, subtitle, sourceName, providerId, width, tags, titleLines = 2, subtitleLines = subtitleLines)
    }
}

/**
 * A card's clean name ("From (2022)", not "ES - From (2022)"), then its source (Expert mode), the
 * name's tags as badges and [subtitle] on [subtitleLines] lines — as TV Home's cards and the lists show it.
 * Without [tags], [title] shows as it is and has no badges: a TMDB list's title.
 */
@Composable
internal fun CardLines(
    title: String,
    subtitle: String?,
    sourceName: String?,
    providerId: Long,
    width: Dp,
    tags: Boolean = true,
    titleLines: Int = 1,
    subtitleLines: Int = 1,
) {
    val parsed = remember(title) { parseDisplayTitle(title) }
    Column(modifier = Modifier.width(width).padding(Spacing.xs)) {
        CinemaText(
            text = if (tags) parsed.title.ifBlank { title } else title,
            role = TextRole.Heading,
            maxLines = titleLines,
            // Room for every line, so the cards of a row are one height.
            minLines = titleLines,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            sourceName?.let { SourceBadge(it, providerId = providerId) }
            if (tags) TitleBadges(parsed)
            CinemaText(
                text = subtitle.orEmpty(),
                role = TextRole.Caption,
                tone = TextTone.Secondary,
                maxLines = subtitleLines,
                minLines = subtitleLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A card's tap and long-press (its menu, none when null), with the long-press named for accessibility. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.homeCardClicks(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier =
    combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClick?.let { stringResource(R.string.row_more_actions) },
    )

private const val POSTER_ASPECT = 2f / 3f
