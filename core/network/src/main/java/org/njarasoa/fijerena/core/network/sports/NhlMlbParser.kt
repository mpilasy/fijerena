package org.njarasoa.fijerena.core.network.sports

import kotlinx.serialization.Serializable

/**
 * The NHL's club schedule (`api-web.nhle.com/v1/club-schedule-season/{TRI}/now`) as [SportsGame]s
 * with their US and Canadian TV. Teams are NHL tri-codes ("BOS", "NJD").
 */
object NhlParser {
    const val SPORT = "hockey"

    fun clubSchedule(body: String): List<SportsGame> =
        sportsJson.decodeFromString<Schedule>(body).games.mapNotNull { game ->
            val id = game.id?.toString() ?: return@mapNotNull null
            val start = isoEpochSec(game.startTimeUTC) ?: return@mapNotNull null
            val home = game.homeTeam?.toTeam() ?: return@mapNotNull null
            SportsGame(
                source = SportsSource.NHL,
                id = id,
                startEpochSec = start,
                home = home,
                away = game.awayTeam?.toTeam(),
                competition = SportsLeague(SportsSource.NHL, "nhl", "NHL", SPORT),
                state = state(game.gameState, game.gameScheduleState),
                broadcasters =
                    game.tvBroadcasts
                        .mapNotNull { tv ->
                            tv.network.clean()?.let { Broadcaster(it, tv.countryCode.clean()?.uppercase(), null, BroadcasterOrigin.NHL) }
                        }.distinctBy { it.name to it.country },
            )
        }

    private fun Team.toTeam(): SportsTeam? {
        val abbrev = abbrev.clean() ?: return null
        val place = placeName?.default.clean()
        val common = commonName?.default.clean()
        val name = listOfNotNull(place, common).joinToString(" ").ifBlank { abbrev }
        return SportsTeam(
            source = SportsSource.NHL,
            id = abbrev,
            name = name,
            shortName = abbrev,
            altNames = listOfNotNull(common, place),
            sport = SPORT,
            league = "nhl",
            logoUrl = logo.clean(),
        )
    }

    // gameState: FUT, PRE, LIVE, CRIT, OFF, FINAL; gameScheduleState: OK, PPD, CNCL…
    private fun state(
        gameState: String?,
        scheduleState: String?,
    ): GameState =
        when {
            scheduleState == "PPD" || scheduleState == "SUSP" -> GameState.POSTPONED
            scheduleState == "CNCL" -> GameState.CANCELLED
            gameState == "LIVE" || gameState == "CRIT" -> GameState.LIVE
            gameState == "OFF" || gameState == "FINAL" -> GameState.FINISHED
            else -> GameState.SCHEDULED
        }

    @Serializable
    private data class Schedule(
        val games: List<Game> = emptyList(),
    )

    @Serializable
    private data class Game(
        val id: Long? = null,
        val startTimeUTC: String? = null,
        val gameState: String? = null,
        val gameScheduleState: String? = null,
        val tvBroadcasts: List<Tv> = emptyList(),
        val homeTeam: Team? = null,
        val awayTeam: Team? = null,
    )

    @Serializable
    private data class Tv(
        val network: String? = null,
        val countryCode: String? = null,
    )

    @Serializable
    private data class Team(
        val abbrev: String? = null,
        val commonName: Default? = null,
        val placeName: Default? = null,
        val logo: String? = null,
    )

    @Serializable
    private data class Default(
        val default: String? = null,
    )
}

/**
 * MLB's schedule (`statsapi.mlb.com/api/v1/schedule?...&hydrate=broadcasts`) as [SportsGame]s with
 * their TV (radio is left out). Teams are MLB team ids ("111").
 */
object MlbParser {
    const val SPORT = "baseball"

    fun schedule(body: String): List<SportsGame> =
        sportsJson.decodeFromString<Schedule>(body).dates.flatMap { it.games }.mapNotNull { game ->
            val id = game.gamePk?.toString() ?: return@mapNotNull null
            val start = isoEpochSec(game.gameDate) ?: return@mapNotNull null
            val home =
                game.teams
                    ?.home
                    ?.team
                    ?.toTeam() ?: return@mapNotNull null
            SportsGame(
                source = SportsSource.MLB,
                id = id,
                startEpochSec = start,
                home = home,
                away =
                    game.teams.away
                        ?.team
                        ?.toTeam(),
                competition = SportsLeague(SportsSource.MLB, "mlb", "MLB", SPORT),
                state = state(game.status),
                broadcasters =
                    game.broadcasts
                        .filter { it.type == "TV" }
                        .mapNotNull { tv -> tv.name.clean()?.let { Broadcaster(it, country(it), null, BroadcasterOrigin.MLB) } }
                        .distinctBy { it.name.lowercase() },
            )
        }

    /**
     * MLB gives no country: the Blue Jays' Canadian networks (Sportsnet, TSN, RDS) are Canada's, the
     * rest the US's.
     */
    fun country(network: String): String {
        val upper = network.uppercase()
        val canadian = CANADIAN_PREFIXES.any { upper.startsWith(it) }
        return if (canadian) "CA" else "US"
    }

    private val CANADIAN_PREFIXES = listOf("SN", "SPORTSNET", "TSN", "RDS")

    private fun TeamRef.toTeam(): SportsTeam? {
        val id = id?.toString() ?: return null
        val name = name.clean() ?: return null
        return SportsTeam(SportsSource.MLB, id, name, sport = SPORT, league = "mlb")
    }

    private fun state(status: Status?): GameState {
        val detailed = status?.detailedState.orEmpty()
        return when {
            "Postponed" in detailed || "Suspended" in detailed -> GameState.POSTPONED
            "Cancelled" in detailed -> GameState.CANCELLED
            status?.abstractGameState == "Live" -> GameState.LIVE
            status?.abstractGameState == "Final" -> GameState.FINISHED
            else -> GameState.SCHEDULED
        }
    }

    @Serializable
    private data class Schedule(
        val dates: List<Date> = emptyList(),
    )

    @Serializable
    private data class Date(
        val games: List<Game> = emptyList(),
    )

    @Serializable
    private data class Game(
        val gamePk: Long? = null,
        val gameDate: String? = null,
        val status: Status? = null,
        val teams: Teams? = null,
        val broadcasts: List<Broadcast> = emptyList(),
    )

    @Serializable
    private data class Status(
        val abstractGameState: String? = null,
        val detailedState: String? = null,
    )

    @Serializable
    private data class Teams(
        val home: Side? = null,
        val away: Side? = null,
    )

    @Serializable
    private data class Side(
        val team: TeamRef? = null,
    )

    @Serializable
    private data class TeamRef(
        val id: Long? = null,
        val name: String? = null,
    )

    @Serializable
    private data class Broadcast(
        val name: String? = null,
        val type: String? = null,
    )
}
