package org.njarasoa.fijerena.core.network.sports

/**
 * Where a team, a league or a game comes from. [id] is the stored and synced value
 * (`followed_sport.source`); never rename it. See the sports teams plan → Data sources.
 */
enum class SportsSource(
    val id: String,
) {
    ESPN("espn"),
    FIFA("fifa"),
    UEFA("uefa"),
    NHL("nhl"),
    MLB("mlb"),
    EHF("ehf"),
    WORLD_AQUATICS("worldaquatics"),
    ;

    companion object {
        fun byId(id: String): SportsSource? = entries.firstOrNull { it.id == id }
    }
}

/** What a profile follows: a team or a whole league. [id] is the stored value (`followed_sport.kind`). */
enum class FollowKind(
    val id: String,
) {
    TEAM("TEAM"),
    LEAGUE("LEAGUE"),
    ;

    companion object {
        fun byId(id: String): FollowKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A team as one source names it. [league] is the source's league key (ESPN `soccer/eng.1`, a FIFA
 * competition id…); [altNames] are the other names the source gives (location, nickname,
 * abbreviation), used to find the team in guide titles and in other sources.
 */
data class SportsTeam(
    val source: SportsSource,
    val id: String,
    val name: String,
    val shortName: String? = null,
    val altNames: List<String> = emptyList(),
    val sport: String,
    val league: String? = null,
    val leagueName: String? = null,
    val logoUrl: String? = null,
) {
    /** Every name of the team, [name] first, without blanks or repeats. */
    val allNames: List<String>
        get() = (listOf(name) + listOfNotNull(shortName) + altNames).filter { it.isNotBlank() }.distinct()
}

/** A league or competition as one source names it; [id] is the source's league key. */
data class SportsLeague(
    val source: SportsSource,
    val id: String,
    val name: String,
    val sport: String,
    val logoUrl: String? = null,
)

/**
 * A TV channel or service that shows a game in one country. [country] is an ISO 3166-1 alpha-2
 * code in upper case ("US", "GB", "MG"), null when the source does not say. [from] is where the
 * entry comes from: a live source or the season table.
 */
data class Broadcaster(
    val name: String,
    val country: String?,
    val logoUrl: String? = null,
    val from: BroadcasterOrigin,
)

/** Where a [Broadcaster] entry comes from. */
enum class BroadcasterOrigin { ESPN, UEFA, NHL, MLB, SEASON_TABLE }

/**
 * A game between two teams (or an event such as a race, where [away] is null). [startEpochSec] is
 * UTC. [competition] is the league or cup the game is played in, as the source names it.
 * [broadcasters] is empty until they are known.
 */
data class SportsGame(
    val source: SportsSource,
    val id: String,
    val startEpochSec: Long,
    val home: SportsTeam,
    val away: SportsTeam?,
    val competition: SportsLeague,
    val state: GameState = GameState.SCHEDULED,
    val broadcasters: List<Broadcaster> = emptyList(),
)

/** A game's state, as far as the sources tell it. */
enum class GameState { SCHEDULED, LIVE, FINISHED, POSTPONED, CANCELLED }
