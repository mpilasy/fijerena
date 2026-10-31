package org.njarasoa.fijerena.core.ui.sports

import org.njarasoa.fijerena.core.network.sports.Broadcaster
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.GameChannelHow
import org.njarasoa.fijerena.core.network.sports.SportsGame
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// What the sports screens show of a game, the same on TV and phone (the sports teams plan → Screens).

/** A game's key on a screen: its source and its id in that source. */
val SportsGame.key: String get() = "${source.id}/$id"

/** The games of one day in the device's time zone, soonest first. */
data class GameDay(
    val date: LocalDate,
    val games: List<SportsGame>,
)

/** [games] by day in [zone], days and games soonest first. */
fun gamesByDay(
    games: List<SportsGame>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<GameDay> =
    games
        .sortedBy { it.startEpochSec }
        .groupBy { Instant.ofEpochSecond(it.startEpochSec).atZone(zone).toLocalDate() }
        .map { (date, list) -> GameDay(date, list) }

/**
 * A broadcaster the profile has no channel for, once for all the countries it covers: the season
 * table writes a region (`SUB_SAHARAN_AFRICA`) as one row per country, so "SuperSport" comes
 * back for 48 countries. [countries] are ISO codes, sorted; [worldwide] is a service with no
 * country (the table's `WORLD`).
 */
data class BroadcasterGroup(
    val name: String,
    val countries: List<String>,
    val worldwide: Boolean,
)

/**
 * The broadcasters as groups, one per name (any case): a worldwide service first, then by the
 * first country of each group, so broadcasters of one country each read as a list by country.
 */
fun groupBroadcasters(broadcasters: List<Broadcaster>): List<BroadcasterGroup> =
    broadcasters
        .groupBy { it.name.trim().lowercase() }
        .values
        .map { list ->
            BroadcasterGroup(
                name = list.first().name.trim(),
                countries =
                    list
                        .mapNotNull { it.country }
                        .distinct()
                        .sorted(),
                worldwide = list.any { it.country == null },
            )
        }.sortedWith(compareBy<BroadcasterGroup> { !it.worldwide }.thenBy { it.countries.firstOrNull() ?: "" }.thenBy { it.name })

/**
 * The game sheet (the sports teams plan → Game sheet): the channels that play the game, in the
 * matcher's order, then the broadcasters the profile has no channel for, grouped
 * ([groupBroadcasters]). [notAnnounced]: nothing is known yet ("TV not announced yet").
 */
data class GameSheet(
    val channels: List<GameChannel>,
    val broadcasters: List<BroadcasterGroup>,
    val notAnnounced: Boolean,
) {
    companion object {
        fun of(channels: List<GameChannel>): GameSheet =
            GameSheet(
                channels = channels.filter { it.stream != null },
                broadcasters =
                    groupBroadcasters(
                        channels.filter { it.how == GameChannelHow.BROADCASTER_ONLY }.mapNotNull { it.broadcaster },
                    ),
                notAnnounced = channels.isEmpty() || channels.all { it.how == GameChannelHow.NOT_ANNOUNCED },
            )
    }
}

/**
 * The ways to watch [game] when its channels can't be matched (the profile's channels or the
 * guide could not be read): its broadcasters as text, or "not announced".
 */
fun unmatchedChannels(game: SportsGame): List<GameChannel> =
    game.broadcasters
        .map { GameChannel(stream = null, programme = null, broadcaster = it, how = GameChannelHow.BROADCASTER_ONLY) }
        .ifEmpty { listOf(GameChannel(null, null, null, GameChannelHow.NOT_ANNOUNCED)) }
