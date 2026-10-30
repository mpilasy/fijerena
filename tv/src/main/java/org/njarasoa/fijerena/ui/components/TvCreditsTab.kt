package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.network.tmdb.CAST_MORE_STEP
import org.njarasoa.fijerena.core.network.tmdb.CastMember
import org.njarasoa.fijerena.core.network.tmdb.CrewLine
import org.njarasoa.fijerena.core.network.tmdb.CrewRole
import org.njarasoa.fijerena.core.network.tmdb.MAX_CAST_CARDS
import org.njarasoa.fijerena.core.network.tmdb.TitleCredits
import org.njarasoa.fijerena.core.network.tmdb.castMoreCount
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.input.NavReturnFocus
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * The Cast tab from TMDB's credits (cast tab plan, Design 2): the crew lines (a button for each
 * person), then a row of cast cards with the photo, the name and the role: the first
 * [MAX_CAST_CARDS], then a More card that shows the rest of the cast. OK on a person calls
 * [onPersonSelected] (crew is true for a crew line). Each control records itself in [returnFocus],
 * so Back from the person page lands on it again. [section] gives the colour of the letter that
 * shows when TMDB has no photo. [topFocusModifier] goes on the topmost controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvCreditsTabContent(
    credits: TitleCredits,
    section: ThumbnailContentType,
    returnFocus: NavReturnFocus,
    onPersonSelected: (personId: String, name: String, crew: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    topFocusModifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        credits.crew.forEachIndexed { lineIndex, line ->
            TvDetailRow(label = crewLabel(line)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    line.people.forEachIndexed { index, person ->
                        val key = RETURN_CREW_PREFIX + line.role.name + ":" + person.personId
                        CinemaSecondaryButton(
                            onClick = {
                                returnFocus.leaveFrom(key)
                                onPersonSelected(person.personId, person.name, true)
                            },
                            text = person.name,
                            modifier =
                                Modifier
                                    .navReturnFocusTarget(returnFocus, key)
                                    .then(if (index == 0) Modifier.leftToRail() else Modifier)
                                    .then(if (lineIndex == 0) topFocusModifier else Modifier),
                        )
                    }
                }
            }
        }
        if (credits.cast.isNotEmpty()) {
            CastCardRow(
                cast = credits.cast,
                section = section,
                returnFocus = returnFocus,
                onPersonSelected = onPersonSelected,
                topFocusModifier = if (credits.crew.isEmpty()) topFocusModifier else Modifier,
            )
        }
    }
}

@Composable
private fun CastCardRow(
    cast: List<CastMember>,
    section: ThumbnailContentType,
    returnFocus: NavReturnFocus,
    onPersonSelected: (personId: String, name: String, crew: Boolean) -> Unit,
    topFocusModifier: Modifier,
) {
    val rowState = rememberLazyListState()
    // The actors on screen: MAX_CAST_CARDS, then CAST_MORE_STEP more per press of the More card,
    // kept while the page is open (also over a person page and Back), not stored.
    var shownCount by rememberSaveable { mutableIntStateOf(MAX_CAST_CARDS) }
    val collapsed = cast.size > shownCount
    // Collapsed, the card after the shown ones is the More card. It is the same card as
    // the actor that takes its place, so OK on it keeps the focus there: on the first actor it adds.
    val shown = if (collapsed) cast.take(shownCount + 1) else cast
    // Back from a person page: the card that opened it is scrolled in, so its requester attaches.
    val returnId = returnFocus.key?.takeIf { it.startsWith(RETURN_CAST_PREFIX) }?.removePrefix(RETURN_CAST_PREFIX)
    LaunchedEffect(returnId) {
        val index = shown.indexOfFirst { it.personId == returnId }
        if (index >= 0) rowState.scrollToItem(index)
    }
    val look = TvCardLooks.shelf()
    LazyRow(
        state = rowState,
        // Coming back to the row lands on the card the user left, not back at the start.
        modifier = Modifier.focusRestorer(),
        // A focused card grows past its bounds: the slack keeps its glow from being clipped.
        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        itemsIndexed(shown, key = { _, member -> member.personId }) { index, member ->
            val key = RETURN_CAST_PREFIX + member.personId
            val more = collapsed && index == shownCount
            TvCard(
                onClick = {
                    if (more) {
                        shownCount += CAST_MORE_STEP
                    } else {
                        returnFocus.leaveFrom(key)
                        onPersonSelected(member.personId, member.name, false)
                    }
                },
                look = look,
                modifier =
                    Modifier
                        .width(TvDimensions.posterWidth)
                        .navReturnFocusTarget(returnFocus, key)
                        .then(if (index == 0) Modifier.leftToRail() else Modifier)
                        .then(topFocusModifier),
            ) {
                if (more) {
                    MoreCardContent(count = castMoreCount(shownCount, cast.size))
                } else {
                    CastCardContent(member, section)
                }
            }
        }
    }
}

/** An actor's card: the photo, the name and the role. */
@Composable
private fun CastCardContent(
    member: CastMember,
    section: ThumbnailContentType,
) {
    CinemaThumbnail(
        url = member.photoUrl,
        fallbackLetter = member.name.firstOrNull(),
        contentType = section,
        contentDescription = member.name,
        modifier = Modifier.size(width = TvDimensions.posterWidth, height = TvDimensions.posterHeightLarge),
    )
    Column(modifier = Modifier.padding(Spacing.xs)) {
        CinemaText(
            text = member.name,
            role = TextRole.Heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Two lines kept for every card, so the cards stay the same height.
        CinemaText(
            text = member.character?.let { stringResource(R.string.cast_as_character, it) }.orEmpty(),
            role = TextRole.BodySmall,
            tone = TextTone.Secondary,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The More card: "+35 more" where the photo is, and the name lines kept empty for the row's height. */
@Composable
private fun MoreCardContent(count: Int) {
    Box(
        modifier = Modifier.size(width = TvDimensions.posterWidth, height = TvDimensions.posterHeightLarge),
        contentAlignment = Alignment.Center,
    ) {
        CinemaText(text = pluralStringResource(R.plurals.cast_more_count, count, count), role = TextRole.Heading)
    }
    Column(modifier = Modifier.padding(Spacing.xs)) {
        CinemaText(text = "", role = TextRole.Heading, maxLines = 1)
        CinemaText(text = "", role = TextRole.BodySmall, maxLines = 2, minLines = 2)
    }
}

@Composable
private fun crewLabel(line: CrewLine): String =
    when (line.role) {
        CrewRole.DIRECTOR -> pluralStringResource(R.plurals.details_label_directors, line.people.size)
        CrewRole.CREATOR -> stringResource(R.string.details_label_creators)
        CrewRole.WRITER -> pluralStringResource(R.plurals.details_label_writers, line.people.size)
    }

// Keys for the controls that open a person page — see rememberNavReturnFocus.
private const val RETURN_CAST_PREFIX = "cast:"
private const val RETURN_CREW_PREFIX = "crew:"
