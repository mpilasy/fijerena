@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ContinueWatchingItem
import org.njarasoa.fijerena.core.player.domain.episodeTitleWithoutSeries
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.player.model.formatDuration
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.LanguageBadge
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBorder
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.feature.category.components.RowActionsHint
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius

/**
 * Home-screen "Jump Back In" row — see docs/plans/archive/20260923_ui-ux-transitions-flow-uplift-plan.md,
 * Phase 3.
 */
@Composable
fun TvContinueWatchingShelf(
    items: List<ContinueWatchingItem>,
    onItemSelected: (ContinueWatchingItem) -> Unit,
    /** Long-press OK or the Menu key on a card: its actions menu. */
    onOpenActions: (ContinueWatchingItem) -> Unit,
    firstItemFocus: FocusRequester,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    itemModifier: (ContinueWatchingItem) -> Modifier = { Modifier },
) {
    HomeRow(
        title = stringResource(R.string.series_continue_watching_badge),
        items = items,
        key = { it.id },
        listState = listState,
        firstItemFocus = firstItemFocus,
        modifier = modifier,
    ) { item, rowModifier ->
        TvContinueWatchingCard(
            item = item,
            onClick = { onItemSelected(item) },
            onOpenActions = { onOpenActions(item) },
            modifier = itemModifier(item).then(rowModifier),
        )
    }
}

@Composable
private fun TvContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        onLongClick = onOpenActions,
        modifier =
            modifier
                .width(TvDimensions.continueWatchingCardWidth)
                .onFocusChanged { isFocused = it.isFocused }
                .openActionsOnMenuKey(onOpenActions),
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaSurface,
                focusedContentColor = CinemaTextPrimary,
            ),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleSubtle,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.medium)),
        border =
            CardDefaults.border(
                border = Border(BorderStroke(TvFocusTokens.borderThin, CinemaGlassBorder)),
                focusedBorder =
                    Border(
                        border = BorderStroke(TvFocusTokens.focusBorderWidth, CinemaTextPrimary),
                        shape = RoundedCornerShape(CinemaCornerRadius.medium),
                    ),
            ),
        glow = CardDefaults.glow(glow = TvFocusTokens.restingGlow, focusedGlow = TvFocusTokens.focusedGlow),
    ) {
        Column {
            CinemaThumbnail(
                url = item.thumbnailUrl,
                fallbackLetter = item.name.firstOrNull(),
                contentType = if (item.contentType == ContentType.TV_SHOWS) ThumbnailContentType.TV_SHOW else ThumbnailContentType.MOVIE,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
            )
            if (item.upNext) {
                // Nothing watched of it yet: no bar, but its height, so cards in the row line up.
                Spacer(modifier = Modifier.height(TvDimensions.resumeBarHeight))
            } else {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().height(TvDimensions.resumeBarHeight),
                    color = CinemaAccent,
                    trackColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.focusedTint),
                )
            }
            Column(modifier = Modifier.padding(Spacing.sm)) {
                // The provider's "EN - " / "4K-A+ - " tag as a badge, as in the lists.
                val parsedTitle = remember(item.name) { parseDisplayTitle(item.name) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    parsedTitle.badge?.let { LanguageBadge(it) }
                    Text(
                        text = parsedTitle.title.ifBlank { item.name },
                        style = MaterialTheme.typography.titleMedium,
                        color = CinemaTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // The row-actions hint, as on the focused list row.
                    if (isFocused) RowActionsHint()
                }
                // An episode's title repeats the series' raw name, which the line above already shows;
                // on a card's width only its number fits beside the time left ("S18E01 • 35m left",
                // TV UI audit X4), so the number stands for it when there is one.
                val episode =
                    item.subtitle?.let { subtitle ->
                        val own = episodeTitleWithoutSeries(subtitle, item.name)
                        EPISODE_NUMBER.find(own)?.value ?: own
                    }
                val subtitleLine =
                    if (item.upNext) {
                        val upNext = stringResource(R.string.continue_watching_up_next)
                        episode?.let { "$upNext • $it" } ?: upNext
                    } else {
                        val timeLeft = formatDuration((item.remainingMs / 1000).toString())
                        if (episode != null) {
                            "$episode • " + stringResource(R.string.tv_home_time_left_format, timeLeft)
                        } else {
                            stringResource(R.string.series_remaining_format, timeLeft)
                        }
                    }
                Text(
                    text = subtitleLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** An episode's "S18E01" number inside its title. */
private val EPISODE_NUMBER = Regex("""S\d{1,3}\s?E\d{1,4}""", RegexOption.IGNORE_CASE)
