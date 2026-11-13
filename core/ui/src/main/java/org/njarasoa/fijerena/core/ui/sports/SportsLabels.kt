package org.njarasoa.fijerena.core.ui.sports

import android.content.res.Resources
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.GameChannelHow
import org.njarasoa.fijerena.core.network.sports.GameState
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.DateText

// The words of the sports screens, the same on TV and phone (the sports teams plan → Screens).

/** A sport's name in the app language, from the source's sport key ("soccer", "hockey"); null for one the app does not name. */
fun sportLabel(
    resources: Resources,
    sport: String?,
): String? =
    when (sport?.lowercase()) {
        "soccer" -> R.string.sport_soccer
        "football" -> R.string.sport_american_football
        "basketball" -> R.string.sport_basketball
        "baseball" -> R.string.sport_baseball
        "hockey" -> R.string.sport_hockey
        "rugby" -> R.string.sport_rugby
        "rugby-league" -> R.string.sport_rugby_league
        "cricket" -> R.string.sport_cricket
        "tennis" -> R.string.sport_tennis
        "golf" -> R.string.sport_golf
        "mma" -> R.string.sport_mma
        "racing" -> R.string.sport_racing
        "australian-football" -> R.string.sport_australian_football
        "lacrosse" -> R.string.sport_lacrosse
        "volleyball" -> R.string.sport_volleyball
        else -> null
    }?.let(resources::getString)

/** A team's second line in a list: its sport and its league, the parts known. */
fun teamDetails(
    resources: Resources,
    team: SportsTeam,
): String = listOfNotNull(sportLabel(resources, team.sport), team.leagueName?.takeIf { it.isNotBlank() }).joinToString(" · ")

/** A game's title: "Arsenal v Chelsea", or the one name of an event with no second team. */
fun gameTitle(
    resources: Resources,
    game: SportsGame,
): String = game.away?.let { resources.getString(R.string.sports_game_format, game.home.name, it.name) } ?: game.home.name

/** A result's title with the score, home first as in [gameTitle]: "Germany 2 – 0 Serbia". */
fun resultTitle(
    resources: Resources,
    game: SportsGame,
): String =
    game.away?.let {
        resources.getString(R.string.sports_result_format, game.home.name, game.homeScore.orEmpty(), game.awayScore.orEmpty(), it.name)
    }
        ?: game.home.name

/** Whether [game] is on with both scores known: its rows show the score and the clock (the team results plan, Phase 4). */
fun showsLiveScore(game: SportsGame): Boolean = game.state == GameState.LIVE && game.homeScore != null && game.awayScore != null

/** A game row's or sheet's title: with the score while the game is on ([resultTitle]), else [gameTitle]. */
fun gameRowTitle(
    resources: Resources,
    game: SportsGame,
): String = if (showsLiveScore(game)) resultTitle(resources, game) else gameTitle(resources, game)

/** Where a game that is on is ("66'", "HT"), else [otherwise] (its start). */
fun clockOr(
    game: SportsGame,
    otherwise: String,
): String = game.clock?.takeIf { game.state == GameState.LIVE } ?: otherwise

/** A result's competition and day: "UEFA Nations League · Yesterday", "… · Sat 1 Nov". */
fun resultDetails(
    resources: Resources,
    game: SportsGame,
): String =
    listOfNotNull(
        game.competition.name.takeIf { it.isNotBlank() },
        DateText.shortDayLabel(resources, DateText.day(game.startEpochSec)),
    ).joinToString(" · ")

/** Whether [team] plays [game] at home: the game's home side has its id. */
fun isHomeGame(
    game: SportsGame,
    team: SportsTeam,
): Boolean = game.home.source == team.source && game.home.id == team.id

/** [team]'s opponent in [game], null for an event with no second team. */
fun opponentIn(
    game: SportsGame,
    team: SportsTeam,
): SportsTeam? = if (isHomeGame(game, team)) game.away else game.home

/** The teams whose logos a game's card shows: on [team]'s page its opponent alone; else both, home first (one for a game without an away team). */
fun gameLogoTeams(
    game: SportsGame,
    team: SportsTeam?,
): List<SportsTeam> = if (team != null) listOf(opponentIn(game, team) ?: game.home) else listOfNotNull(game.home, game.away)

/**
 * What a channel row says under the channel's name: "In the guide" for a guide programme, "Event
 * channel" for a channel whose name names the game, "On ESPN" for a network's channel.
 */
fun channelNote(
    resources: Resources,
    channel: GameChannel,
): String =
    when (channel.how) {
        GameChannelHow.GUIDE_ON_BROADCASTER, GameChannelHow.GUIDE -> {
            resources.getString(R.string.sports_in_guide)
        }

        GameChannelHow.CHANNEL_NAME -> {
            resources.getString(R.string.sports_event_channel)
        }

        GameChannelHow.BROADCASTER_CHANNEL -> {
            channel.broadcaster?.let { resources.getString(R.string.sports_on_broadcaster, it.name) }.orEmpty()
        }
    }

/**
 * What a game row says for its channels: [channels] null while they are matched ("Looking for
 * channels…"), empty when none of the profile's channels has the game ("Not found on your channels").
 */
fun noChannelNote(
    resources: Resources,
    channels: List<GameChannel>?,
): String = resources.getString(if (channels == null) R.string.sports_matching else R.string.sports_not_found)

/** Whether a tap on one of [game]'s channels asks first (Watch now or Remind Me): the game has not started. */
fun asksBeforeWatching(
    game: SportsGame,
    nowSec: Long,
): Boolean = game.startEpochSec > nowSec
