package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import org.njarasoa.fijerena.ui.components.cards.CinemaCard
import org.njarasoa.fijerena.ui.components.chips.CinemaAssistChip
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * The Cast tab from TMDB's credits (cast tab plan, Design 2): the crew lines (a chip for each
 * person), then a grid of cast cards with the photo, the name and the role. A tap on a person calls
 * [onPersonSelected] (crew is true for a crew line). [section] gives the colour of the letter that
 * shows when TMDB has no photo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MobileCreditsTabContent(
    credits: TitleCredits,
    section: ThumbnailContentType,
    onPersonSelected: (personId: String, name: String, crew: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        credits.crew.forEach { line ->
            MobileDetailRow(label = crewLabel(line)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    line.people.forEach { person ->
                        CinemaAssistChip(
                            onClick = { onPersonSelected(person.personId, person.name, true) },
                            label = { CinemaText(text = person.name, tone = TextTone.Inherit) },
                        )
                    }
                }
            }
        }
        if (credits.cast.isNotEmpty()) {
            CastCardGrid(cast = credits.cast, section = section, onPersonSelected = onPersonSelected)
        }
    }
}

/** As many columns as fit cards of at least [CARD_MIN_WIDTH]; at least two. */
@Composable
private fun CastCardGrid(
    cast: List<CastMember>,
    section: ThumbnailContentType,
    onPersonSelected: (personId: String, name: String, crew: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = (maxWidth / CARD_MIN_WIDTH).toInt().coerceAtLeast(MIN_COLUMNS)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            cast.chunked(columns).forEach { rowMembers ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    rowMembers.forEach { member ->
                        CastCard(
                            member = member,
                            section = section,
                            onClick = { onPersonSelected(member.personId, member.name, false) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Empty room keeps the last row's cards as wide as the others.
                    repeat(columns - rowMembers.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CastCard(
    member: CastMember,
    section: ThumbnailContentType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CinemaCard(onClick = onClick, modifier = modifier) {
        CinemaThumbnail(
            url = member.photoUrl,
            fallbackLetter = member.name.firstOrNull(),
            contentType = section,
            contentDescription = member.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(PHOTO_ASPECT_RATIO),
        )
        Column(modifier = Modifier.padding(Spacing.xs)) {
            CinemaText(text = member.name, role = TextRole.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Two lines kept for every card, so the cards in a row stay the same height.
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

@Composable
private fun crewLabel(line: CrewLine): String =
    when (line.role) {
        CrewRole.DIRECTOR -> pluralStringResource(R.plurals.details_label_directors, line.people.size)
        CrewRole.CREATOR -> stringResource(R.string.details_label_creators)
        CrewRole.WRITER -> pluralStringResource(R.plurals.details_label_writers, line.people.size)
    }

// The related-title cards' scale (RelatedTitlesRow): a 2:3 photo, a little narrower.
private val CARD_MIN_WIDTH = MobileDimensions.posterHeightLarge * 0.5f
private const val PHOTO_ASPECT_RATIO = 2f / 3f
private const val MIN_COLUMNS = 2
