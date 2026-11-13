package org.njarasoa.fijerena.core.network.sports

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.njarasoa.fijerena.core.network.AppSettings
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** What a sports search found: teams, then leagues. */
data class SportsSearchResult(
    val teams: List<SportsTeam>,
    val leagues: List<SportsLeague>,
)

/**
 * What the sports screens call (the sports teams plan → Data sources, Cache): search, a team's
 * coming games, a league's games, a league's teams, all from ESPN. Each game carries the TV networks
 * ESPN names (US), or the NHL's or MLB's for their games ([Broadcaster]): the matcher uses them only
 * to find the profile's channels with those names.
 *
 * A call that fails falls back to its stored answer ([SportsCache]). A failed NHL or MLB call leaves
 * ESPN's networks. With the Admin switch off ([enabled]) nothing is called and everything is empty.
 *
 * Every team's and league's games read are handed to [onGames]: the game reminders move to a
 * game's new time ([GameReminders.onGamesRead]).
 */
class SportsData(
    private val api: SportsApi,
    private val cache: SportsCache,
    private val enabled: () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis,
    private val onGames: (List<SportsGame>) -> Unit = {},
) {
    /**
     * ESPN's teams and leagues whose names match [query] (2 letters at least). A soccer team gets
     * its league's name ("German Bundesliga", "UEFA Women's Champions League"), which also tells a
     * club's men's and women's teams apart (ESPN names both "Bayern München"). A short name ESPN
     * ranks low ("euro") also asks for its full name ([LEAGUE_QUERY_ALIASES]), whose leagues come first.
     */
    suspend fun search(query: String): SportsSearchResult {
        val text = query.trim()
        if (!enabled() || text.length < MIN_QUERY) return SportsSearchResult(emptyList(), emptyList())
        val failures = mutableListOf<Exception>()
        return coroutineScope {
            val espnTeams = async { attempt(failures, "ESPN teams") { EspnParser.teamSearch(api.espnTeamSearch(text)) } }
            val espnLeagues = async { attempt(failures, "ESPN leagues") { EspnParser.leagueSearch(api.espnLeagueSearch(text)) } }
            val alias = LEAGUE_QUERY_ALIASES[text.lowercase()]
            val aliasLeagues =
                alias?.let { full ->
                    async { attempt(mutableListOf(), "ESPN leagues") { EspnParser.leagueSearch(api.espnLeagueSearch(full)) } }
                }
            val teams = espnTeams.await()
            val found = espnLeagues.await()
            if (teams.isEmpty() && found.isEmpty() && failures.size == SEARCH_SOURCES) throw failures.first()
            val leagues = (aliasLeagues?.await().orEmpty().filter { it.name.equals(alias, ignoreCase = true) } + found).distinctBy { it.id }
            SportsSearchResult(withLeagueNames(teams), leagues)
        }
    }

    /** [teams] with the league name of those that have only a league slug (soccer); as they are when the list can't be read. */
    private suspend fun withLeagueNames(teams: List<SportsTeam>): List<SportsTeam> {
        val sports = teams.filter { it.leagueName == null && it.league != null }.map { it.sport }.distinct()
        if (sports.isEmpty()) return teams
        val names =
            sports.associateWith { sport ->
                attempt(mutableListOf(), "ESPN $sport leagues") {
                    cache
                        .get("leagues_espn_$sport", { EspnParser.leagueNames(it).toList() }, { SportsCache.LIST_MAX_AGE_MS }) {
                            api.espnLeagueList(sport)
                        }
                }.toMap()
            }
        return teams.map { team ->
            val slug = EspnParser.splitLeague(team.league)?.second
            val name = slug?.let { names[team.sport]?.get(it) }
            if (team.leagueName == null && name != null) team.copy(leagueName = name) else team
        }
    }

    /** [team]'s coming games, [limit] at most, soonest first, with their broadcasters. */
    suspend fun teamGames(
        team: SportsTeam,
        limit: Int = TEAM_GAMES,
    ): List<SportsGame> {
        if (!enabled()) return emptyList()
        val games = if (team.source == SportsSource.ESPN) espnTeamSchedule(team) else emptyList()
        return withBroadcasters(coming(games).take(limit)).also(onGames)
    }

    /**
     * [team]'s results of the last [days] days, newest first: the games that ended, with both
     * scores. A soccer team's come from its played games of every competition; another team's from
     * its season schedule. A read that fails gives none; nothing throws.
     */
    suspend fun teamResults(
        team: SportsTeam,
        days: Int = RESULT_DAYS,
    ): List<SportsGame> {
        if (!enabled() || team.source != SportsSource.ESPN) return emptyList()
        val games =
            attempt(mutableListOf(), "ESPN results") {
                if (team.sport == EspnParser.SOCCER) espnSoccerResults(team) else espnTeamSchedule(team)
            }
        val fromSec = now() / 1000 - days * DAY_SEC
        return games
            .filter { it.state == GameState.FINISHED && it.homeScore != null && it.awayScore != null && it.startEpochSec >= fromSec }
            .distinctBy { it.id }
            .sortedByDescending { it.startEpochSec }
    }

    /** [league]'s games of the next [days] days, soonest first, with their broadcasters. */
    suspend fun leagueGames(
        league: SportsLeague,
        days: Int = LEAGUE_DAYS,
    ): List<SportsGame> {
        if (!enabled()) return emptyList()
        val games = if (league.source == SportsSource.ESPN) espnLeagueGames(league, days) else emptyList()
        val endSec = now() / 1000 + days * DAY_SEC
        return withBroadcasters(coming(games).filter { it.startEpochSec <= endSec }).also(onGames)
    }

    /** [league]'s teams: ESPN's list, kept 7 days. */
    suspend fun leagueTeams(league: SportsLeague): List<SportsTeam> {
        if (!enabled() || league.source != SportsSource.ESPN) return emptyList()
        val (sport, slug) = EspnParser.splitLeague(league.id) ?: return emptyList()
        return cache.get("teams_espn_${league.id}", { EspnParser.leagueTeams(it, sport, slug) }, { SportsCache.LIST_MAX_AGE_MS }) {
            api.espnLeagueTeams(sport, slug)
        }
    }

    /** Forgets every stored sports answer. */
    suspend fun clear() = cache.clear()

    // ESPN

    private suspend fun espnTeamSchedule(team: SportsTeam): List<SportsGame> {
        val parts = team.id.split('/')
        val sport = parts.first()
        val league = if (parts.size == 3) parts[1] else EspnParser.splitLeague(team.league)?.second
        return scheduleFromCache("schedule_espn_${team.id}", { EspnParser.schedule(it, sport, league) }) {
            api.espnTeamSchedule(sport, league, parts.last())
        }
    }

    /** A soccer team's played games of every competition (each game names its own). */
    private suspend fun espnSoccerResults(team: SportsTeam): List<SportsGame> {
        val espnId = EspnParser.espnTeamId(team.id)
        // At most an hour old, so a game that has just ended comes (the answer had no sign of it).
        return cache.get(
            "results_espn_soccer_$espnId",
            { EspnParser.schedule(it, EspnParser.SOCCER, null) },
            { minOf(SportsCache.scheduleMaxAgeMs(it, now()), SportsCache.SCHEDULE_SOON_MAX_AGE_MS) },
        ) { api.espnSoccerTeamResults(espnId) }
    }

    private suspend fun espnLeagueGames(
        league: SportsLeague,
        days: Int,
    ): List<SportsGame> {
        val (sport, slug) = EspnParser.splitLeague(league.id) ?: return emptyList()
        val today = today()
        val failures = mutableListOf<Exception>()
        // ESPN's days are US days: one more day covers the games after midnight UTC.
        val games =
            coroutineScope {
                (0..days)
                    .map { offset ->
                        async {
                            val day = today.plusDays(offset.toLong())
                            attempt(failures, "ESPN ${league.id} $day") {
                                scheduleFromCache("scoreboard_espn_${league.id}_$day", { EspnParser.scoreboard(it, sport, slug) }) {
                                    api.espnScoreboard(sport, slug, day)
                                }
                            }
                        }
                    }.awaitAll()
            }.flatten()
        if (games.isEmpty() && failures.size == days + 1) throw failures.first()
        return games.distinctBy { it.id }
    }

    // Broadcasters

    private suspend fun withBroadcasters(games: List<SportsGame>): List<SportsGame> {
        if (games.isEmpty()) return games
        val (nhl, mlb) =
            coroutineScope {
                listOf(async { nhlBroadcasters(games) }, async { mlbBroadcasters(games) }).awaitAll()
            }
        return games.map { game ->
            val key = gameKey(game)
            game.copy(broadcasters = mergeBroadcasters(listOf(nhl[key], mlb[key], game.broadcasters).map { it.orEmpty() }))
        }
    }

    /** The NHL's TV of ESPN's NHL games, read from the home team's NHL schedule. */
    private suspend fun nhlBroadcasters(games: List<SportsGame>): Map<String, List<Broadcaster>> =
        games
            .filter { it.competition.id == SportsTeamIds.ESPN_NHL }
            .groupBy { SportsTeamIds.nhlTriCode(it.home) }
            .flatMap { (triCode, teamGames) ->
                if (triCode == null) return@flatMap emptyList()
                val nhlGames =
                    attempt(mutableListOf(), "NHL $triCode") {
                        scheduleFromCache("schedule_nhl_$triCode", NhlParser::clubSchedule) { api.nhlClubSchedule(triCode) }
                    }
                matchBroadcasters(teamGames, nhlGames)
            }.toMap()

    /** MLB's TV of ESPN's MLB games, read from the home team's MLB schedule. */
    private suspend fun mlbBroadcasters(games: List<SportsGame>): Map<String, List<Broadcaster>> =
        games
            .filter { it.competition.id == SportsTeamIds.ESPN_MLB }
            .groupBy { SportsTeamIds.mlbTeamId(it.home) }
            .flatMap { (teamId, teamGames) ->
                if (teamId == null) return@flatMap emptyList()
                val from = utcDate(teamGames.minOf { it.startEpochSec }).minusDays(1)
                val to = utcDate(teamGames.maxOf { it.startEpochSec }).plusDays(1)
                val mlbGames =
                    attempt(mutableListOf(), "MLB $teamId") {
                        scheduleFromCache("schedule_mlb_${teamId}_${from}_$to", MlbParser::schedule) { api.mlbSchedule(teamId, from, to) }
                    }
                matchBroadcasters(teamGames, mlbGames)
            }.toMap()

    /** For each of [games], the broadcasters of the same game in [others]. */
    private fun matchBroadcasters(
        games: List<SportsGame>,
        others: List<SportsGame>,
    ): List<Pair<String, List<Broadcaster>>> =
        games.mapNotNull { game -> others.firstOrNull { TeamNames.sameGame(game, it) }?.let { gameKey(game) to it.broadcasters } }

    private suspend fun scheduleFromCache(
        key: String,
        parse: (String) -> List<SportsGame>,
        fetch: suspend () -> String,
    ): List<SportsGame> = cache.get(key, parse, { SportsCache.scheduleMaxAgeMs(it, now()) }, fetch)

    /** The games that have not ended (one that started up to 3 hours ago may still be on), soonest first. */
    private fun coming(games: List<SportsGame>): List<SportsGame> {
        val fromSec = now() / 1000 - LIVE_SEC
        return games
            .filter { it.state != GameState.FINISHED && it.state != GameState.CANCELLED && it.startEpochSec >= fromSec }
            .sortedBy { it.startEpochSec }
    }

    private fun today(): LocalDate = utcDate(now() / 1000)

    private fun utcDate(epochSec: Long): LocalDate = Instant.ofEpochSecond(epochSec).atZone(ZoneOffset.UTC).toLocalDate()

    private suspend fun <T> attempt(
        failures: MutableList<Exception>,
        what: String,
        block: suspend () -> List<T>,
    ): List<T> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what: ${e.message}")
            synchronized(failures) { failures += e }
            emptyList()
        }

    companion object {
        private const val TAG = "SportsData"
        private const val MIN_QUERY = 2
        private const val SEARCH_SOURCES = 2
        private const val DAY_SEC = 24 * 60 * 60L
        private const val LIVE_SEC = 3 * 60 * 60L

        /**
         * Short league queries and the full name searched with them: ESPN finds the European
         * Championship for "euro" only after nine other leagues.
         */
        val LEAGUE_QUERY_ALIASES =
            mapOf(
                "euro" to "UEFA European Championship",
                "euros" to "UEFA European Championship",
            )

        /** A team page's games at most. */
        const val TEAM_GAMES = 10

        /** A team page's results: the days back. */
        const val RESULT_DAYS = 30

        /** A league page's days. */
        const val LEAGUE_DAYS = 7

        /**
         * One list of [byPriority] lists, best source first: for each country, the entries of the
         * first list that has that country; entries with no country from every list. One entry per
         * name and country, grouped by country.
         */
        fun mergeBroadcasters(byPriority: List<List<Broadcaster>>): List<Broadcaster> {
            val covered = mutableSetOf<String>()
            val merged = mutableListOf<Broadcaster>()
            for (list in byPriority) {
                merged += list.filter { it.country == null || it.country !in covered }
                covered += list.mapNotNull { it.country }
            }
            return merged.distinctBy { it.name.lowercase() to it.country }.sortedBy { it.country ?: NO_COUNTRY }
        }

        // Entries with no country go last.
        private const val NO_COUNTRY = "~"

        private fun gameKey(game: SportsGame) = "${game.source.id}/${game.id}"

        @Volatile
        private var instance: SportsData? = null

        /** The app's sports data, stored in `cacheDir/sports`, off while Admin's Sports switch is off. */
        fun getInstance(context: Context): SportsData =
            instance ?: synchronized(this) {
                instance ?: run {
                    val app = context.applicationContext
                    val settings = AppSettings(app)
                    SportsData(
                        SportsApi(),
                        SportsCache(File(app.cacheDir, "sports")),
                        enabled = { settings.showSports },
                        onGames = { GameReminders.getInstance(app).onGamesRead(it) },
                    )
                }.also { instance = it }
            }
    }
}
