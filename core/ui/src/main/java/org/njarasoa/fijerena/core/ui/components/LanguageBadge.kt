package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.player.domain.ParsedTitle
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.history.LocalShowSourceBadges
import org.njarasoa.fijerena.core.ui.history.LocalSourceShortNames
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary

/**
 * Shared badge primitive used for status indicators, codecs, ratings, and tags.
 */
@Composable
fun CinemaBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = CinemaSurfaceVariant,
    textColor: Color = CinemaTextSecondary,
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    val radius = CinemaCornerRadius.small
    val shape = remember(radius) { RoundedCornerShape(radius) }
    Text(
        text = text,
        style = style,
        color = textColor,
        modifier =
            modifier
                .clip(shape)
                .background(backgroundColor)
                .padding(horizontal = CinemaSpacing.xs, vertical = CinemaSpacing.xxs),
    )
}

/**
 * Small pill for the language/region code
 * [parseDisplayTitle][org.njarasoa.fijerena.core.player.domain.parseDisplayTitle] strips off a
 * title — rendered next to the title, never baked back into it.
 */
@Composable
fun LanguageBadge(
    code: String,
    modifier: Modifier = Modifier,
) {
    CinemaBadge(
        text = code,
        modifier = modifier,
        backgroundColor = CinemaSurfaceVariant,
        textColor = CinemaTextSecondary,
    )
}

/**
 * What [parseDisplayTitle] took out of a title, in one [LanguageBadge] pill: the code, the formats
 * and the provider's audio, subtitle or timeshift words, then "Events" for an events-only channel,
 * joined by dots ("DE · 4K · Events"). Nothing when there is none.
 */
@Composable
fun TitleBadges(parsed: ParsedTitle) {
    val events = stringResource(R.string.title_badge_events)
    if (!parsed.hasBadges) return
    val text =
        remember(parsed, events) {
            (listOfNotNull(parsed.badge) + parsed.tags + listOfNotNull(events.takeIf { parsed.eventsOnly })).joinToString(BADGE_SEPARATOR)
        }
    LanguageBadge(text)
}

private const val BADGE_SEPARATOR = " · "

/** [TitleBadges] read aloud, for a row that sets one description for all its content. */
@Composable
fun titleBadgesDescription(parsed: ParsedTitle): String? =
    (
        listOfNotNull(parsed.badge) + parsed.tags +
            listOfNotNull(stringResource(R.string.title_badge_events).takeIf { parsed.eventsOnly })
    ).joinToString(", ").ifEmpty { null }

/**
 * A provider's raw title shown clean: its "EN - " / "4K-NF - " / "4K:" tag and the rest as
 * [TitleBadges], then the title without them ([parseDisplayTitle]). One line; [textModifier] goes on
 * the title text.
 */
@Composable
fun BadgedTitle(
    raw: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textModifier: Modifier = Modifier,
) {
    val parsed = remember(raw) { parseDisplayTitle(raw) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TitleBadges(parsed)
        Text(
            text = parsed.title.ifBlank { raw },
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).then(textModifier),
        )
    }
}

/**
 * The source an item comes from, where a list mixes several (search, Recent, favourites), on the
 * same pill as [LanguageBadge]: its short name ("X4P", [LocalSourceShortNames]), by [providerId]
 * when the caller has it, else by [name]. Read as "Source: <name>", the whole name. Nothing while
 * the profile's Expert mode is off ([LocalShowSourceBadges]).
 */
@Composable
fun SourceBadge(
    name: String,
    modifier: Modifier = Modifier,
    providerId: Long? = null,
) {
    if (!LocalShowSourceBadges.current) return
    val shortNames = LocalSourceShortNames.current
    val description = stringResource(R.string.source_badge_description, name)
    CinemaBadge(
        text = providerId?.let { shortNames.byId[it] } ?: shortNames.byName[name] ?: name,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    )
}

/**
 * Star rating badge standardizing the `"★ ${formatRating(rating)}"` pattern across mobile and TV.
 */
@Composable
fun RatingBadge(
    rating: String,
    modifier: Modifier = Modifier,
    textColor: Color = CinemaAccent,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    Text(
        text = "★ ${formatRating(rating)}",
        style = style,
        color = textColor,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * A channel whose source keeps an archive (catch-up): the guide's replay mark and the days it
 * keeps ("↻ 7d"), on the same pill as [LanguageBadge]. [iconSize] is the platform's small icon.
 */
@Composable
fun CatchupBadge(
    days: Int,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    val radius = CinemaCornerRadius.small
    val shape = remember(radius) { RoundedCornerShape(radius) }
    val description = pluralStringResource(R.plurals.catchup_badge_description, days, days)
    Row(
        modifier =
            modifier
                .clearAndSetSemantics { contentDescription = description }
                .clip(shape)
                .background(CinemaSurfaceVariant)
                .padding(horizontal = CinemaSpacing.xs, vertical = CinemaSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CinemaIcons.Replay,
            contentDescription = null,
            tint = CinemaTextSecondary,
            modifier = Modifier.size(iconSize),
        )
        Text(
            text = stringResource(R.string.catchup_badge_days, days),
            style = style,
            color = CinemaTextSecondary,
            maxLines = 1,
        )
    }
}
