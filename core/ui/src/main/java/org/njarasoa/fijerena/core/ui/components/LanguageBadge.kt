package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.player.domain.ParsedTitle
import org.njarasoa.fijerena.core.player.domain.isResolutionTag
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.history.LocalNerd
import org.njarasoa.fijerena.core.ui.history.LocalSourceShortNames
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.cornerShape

/**
 * Shared badge primitive used for status indicators, codecs, ratings, and tags.
 */

/** What a badge marks, which decides its colours. */
enum class BadgeKind {
    /** A tag: codes, formats, cast names — raised surface or the theme's badge fill. */
    Neutral,

    /** [Neutral] on a card (already the raised surface): one step lighter, so the pill still shows. */
    OnCard,

    /** Over video or artwork: a dark tint, light text. */
    OnMedia,

    /** Something live now ("On air"): the success colour. */
    Success,

    /** Something about to happen ("Soon"): the warning colour. */
    Warning,
}

@Composable
fun CinemaBadge(
    text: String,
    modifier: Modifier = Modifier,
    kind: BadgeKind = BadgeKind.Neutral,
    role: TextRole = TextRole.Caption,
    maxLines: Int = Int.MAX_VALUE,
) {
    val c = CinemaTheme.colors
    val (container, tone) =
        when (kind) {
            BadgeKind.Neutral -> (c.badgeFill ?: c.surfaceRaised) to TextTone.OnBadge
            BadgeKind.OnCard -> (c.badgeFill ?: c.surfaceHigh) to TextTone.OnBadge
            BadgeKind.OnMedia -> c.mediaScrim.copy(alpha = CinemaAlpha.tint) to TextTone.OnMedia
            BadgeKind.Success -> c.success to TextTone.OnAccent
            BadgeKind.Warning -> c.warning to TextTone.OnAccent
        }
    CinemaText(
        text = text,
        role = role,
        tone = tone,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier =
            modifier
                .clip(CinemaTheme.shapes.chip)
                .background(container)
                .padding(horizontal = CinemaSpacing.xs, vertical = BADGE_PADDING_VERTICAL),
    )
}

private val BADGE_PADDING_VERTICAL = CinemaSpacing.Base.xxs

/** The height of a one-line [CinemaBadge] in [role] text, in the current Look and Feel and size. */
@Composable
fun rememberBadgeHeight(role: TextRole = TextRole.Caption): Dp = rememberTextHeight(role) + BADGE_PADDING_VERTICAL * 2

/**
 * Small pill for the language/region code
 * [parseDisplayTitle][org.njarasoa.fijerena.core.player.domain.parseDisplayTitle] strips off a
 * title — rendered next to the title, never baked back into it.
 */
@Composable
fun LanguageBadge(
    code: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    CinemaBadge(text = code, modifier = modifier, maxLines = maxLines)
}

/**
 * What [parseDisplayTitle] took out of a title, in one [LanguageBadge] pill: the code, the formats
 * and the provider's audio, subtitle or timeshift words, then "Events" for an events-only channel,
 * joined by dots ("DE · 4K · Events"). Nothing when there is none.
 */
@Composable
fun TitleBadges(
    parsed: ParsedTitle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val events = stringResource(R.string.title_badge_events)
    if (!parsed.hasBadges) return
    val text =
        remember(parsed, events) {
            (listOfNotNull(parsed.badge) + parsed.tags + listOfNotNull(events.takeIf { parsed.eventsOnly })).joinToString(BADGE_SEPARATOR)
        }
    LanguageBadge(text, modifier, maxLines)
}

private const val BADGE_SEPARATOR = " · "

/**
 * The badges of [parsed] in the order a card shows them: the resolution first ("4K"), then the
 * code (the first language), then the other tags in their order, then [events] for an events-only
 * channel.
 */
internal fun cardBadgeTexts(
    parsed: ParsedTitle,
    events: String,
): List<String> {
    val (quality, rest) = parsed.tags.partition(::isResolutionTag)
    return quality + listOfNotNull(parsed.badge) + rest + listOfNotNull(events.takeIf { parsed.eventsOnly })
}

/**
 * A card's line under its name, always one line: the source ([SourceBadge]), the [rating]
 * ([RatingBadge], none when missing or 0), then the badges of [parsed] one pill each
 * ([cardBadgeTexts] order), then [trailing] in the room that is left. The badges that do not fit
 * give way to one "+N" pill ("EN +4"); the source and the rating always stay; a pill never wraps or
 * is cut. Without room, [trailing] is left out. Detail pages keep [TitleBadges], which shows every tag.
 */
@Composable
fun CardBadgeLine(
    parsed: ParsedTitle?,
    modifier: Modifier = Modifier,
    sourceName: String? = null,
    providerId: Long? = null,
    rating: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    // A number above 0 only: "0" is a source's way to say it has none.
    val stars = rating?.takeIf { (it.trim().toDoubleOrNull() ?: 0.0) > 0.0 }
    val events = stringResource(R.string.title_badge_events)
    val texts = remember(parsed, events) { parsed?.let { cardBadgeTexts(it, events) }.orEmpty() }
    val gapStep = CinemaSpacing.xs
    SubcomposeLayout(modifier) { constraints ->
        val gap = gapStep.roundToPx()
        val bounded = constraints.hasBoundedWidth
        val maxWidth = constraints.maxWidth
        // A pill at its own width, so it stays on one line.
        val free = Constraints(maxHeight = constraints.maxHeight)
        // The source and the rating: never left out.
        val lead =
            subcompose(SLOT_SOURCE) { sourceName?.let { SourceBadge(it, providerId = providerId) } }
                .map { it.measure(Constraints(maxWidth = maxWidth, maxHeight = constraints.maxHeight)) } +
                subcompose(SLOT_RATING) { stars?.let { RatingBadge(it, role = TextRole.Caption) } }.map { it.measure(free) }
        val badges = texts.mapIndexed { i, text -> subcompose(i) { LanguageBadge(text, maxLines = 1) }.single().measure(free) }

        fun widthOf(pills: List<Placeable>): Int = pills.sumOf { it.width } + gap * (pills.size - 1).coerceAtLeast(0)

        var pills = lead + badges
        if (bounded && widthOf(pills) > maxWidth) {
            // The most badges that still leave room for a "+N" of the rest; none when even that does not fit.
            pills = lead
            for (kept in badges.size - 1 downTo 0) {
                val more = badges.size - kept
                val plus = subcompose(SLOT_MORE to more) { CinemaBadge("+$more", maxLines = 1) }.single().measure(free)
                val line = lead + badges.take(kept) + plus
                if (widthOf(line) <= maxWidth) {
                    pills = line
                    break
                }
            }
        }
        val used = if (pills.isEmpty()) 0 else widthOf(pills) + gap
        val room = if (bounded) maxWidth - used else Constraints.Infinity
        val after =
            if (room > 0) {
                subcompose(SLOT_TRAILING, trailing).map { it.measure(Constraints(maxWidth = room, maxHeight = constraints.maxHeight)) }
            } else {
                emptyList()
            }
        val placed = pills + after
        val height = (placed.maxOfOrNull { it.height } ?: 0).coerceAtLeast(constraints.minHeight)
        val width = if (bounded) maxWidth else used + after.sumOf { it.width }
        layout(width, height) {
            var x = 0
            placed.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width + gap
            }
        }
    }
}

private const val SLOT_SOURCE = "source"
private const val SLOT_RATING = "rating"
private const val SLOT_MORE = "more"
private const val SLOT_TRAILING = "trailing"

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
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.Heading,
    tone: TextTone = TextTone.Primary,
    textModifier: Modifier = Modifier,
) {
    val parsed = remember(raw) { parseDisplayTitle(raw) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TitleBadges(parsed)
        CinemaText(
            text = parsed.title.ifBlank { raw },
            role = role,
            tone = tone,
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
 * the profile is not a Nerd ([LocalNerd]).
 */
@Composable
fun SourceBadge(
    name: String,
    modifier: Modifier = Modifier,
    providerId: Long? = null,
    kind: BadgeKind = BadgeKind.Neutral,
) {
    if (!LocalNerd.current) return
    val shortNames = LocalSourceShortNames.current
    val description = stringResource(R.string.source_badge_description, name)
    CinemaBadge(
        text = providerId?.let { shortNames.byId[it] } ?: shortNames.byName[name] ?: name,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        kind = kind,
    )
}

/**
 * Star rating badge standardizing the `"★ ${formatRating(rating)}"` pattern across mobile and TV.
 */
@Composable
fun RatingBadge(
    rating: String,
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.BodySmall,
    tone: TextTone = TextTone.Accent,
) {
    CinemaText(
        text = "★ ${formatRating(rating)}",
        role = role,
        tone = tone,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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
) {
    val shape = CinemaTheme.shapes.chip
    val description = pluralStringResource(R.plurals.catchup_badge_description, days, days)
    Row(
        modifier =
            modifier
                .clearAndSetSemantics { contentDescription = description }
                .clip(shape)
                .background(CinemaTheme.colors.badgeFill ?: CinemaTheme.colors.surfaceRaised)
                .padding(horizontal = CinemaSpacing.xs, vertical = CinemaSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CinemaIcon(
            imageVector = CinemaIcons.Replay,
            contentDescription = null,
            tone = TextTone.OnBadge,
            modifier = Modifier.size(iconSize),
        )
        CinemaText(
            text = stringResource(R.string.catchup_badge_days, days),
            role = TextRole.Caption,
            tone = TextTone.OnBadge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
