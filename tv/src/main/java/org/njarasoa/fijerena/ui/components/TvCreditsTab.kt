package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.network.tmdb.CastMember
import org.njarasoa.fijerena.core.network.tmdb.CrewLine
import org.njarasoa.fijerena.core.network.tmdb.CrewRole
import org.njarasoa.fijerena.core.network.tmdb.TitleCredits
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
 * person), then a row of cast cards with the photo, the name and the role. OK on a person calls
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
    // Back from a person page: the card that opened it is scrolled in, so its requester attaches.
    val returnId = returnFocus.key?.takeIf { it.startsWith(RETURN_CAST_PREFIX) }?.removePrefix(RETURN_CAST_PREFIX)
    LaunchedEffect(returnId) {
        val index = cast.indexOfFirst { it.personId == returnId }
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
        itemsIndexed(cast, key = { _, member -> member.personId }) { index, member ->
            val key = RETURN_CAST_PREFIX + member.personId
            TvCard(
                onClick = {
                    returnFocus.leaveFrom(key)
                    onPersonSelected(member.personId, member.name, false)
                },
                look = look,
                modifier =
                    Modifier
                        .width(TvDimensions.posterWidth)
                        .navReturnFocusTarget(returnFocus, key)
                        .then(if (index == 0) Modifier.leftToRail() else Modifier)
                        .then(topFocusModifier),
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
        }
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
