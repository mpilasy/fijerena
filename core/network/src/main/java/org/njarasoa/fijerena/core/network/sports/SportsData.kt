package org.njarasoa.fijerena.core.network.sports

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap

/** What a sports search found: teams, then leagues. */
data class SportsSearchResult(
    val teams: List<SportsTeam>,
    val leagues: List<SportsLeague>,
)

/**
 * What the sports screens call (the sports teams plan → Data sources, Cache): search, a team's
 * coming games, a league's games, a league's teams. Each game carries its broadcasters of every
 * country, merged by country ([mergeBroadcasters]): NHL or MLB for their games, UEFA for UEFA
 * competitions, ESPN (US), then [seasonBroadcasters] (the season table) for the other countries.
 *
 * A source that fails falls back to its stored answer ([SportsCache]); with none, the other sources
 * still answer. A call fails only when every source it needs failed. With the Admin switch off
 * ([enabled]) nothing is called and everything is empty.
 *
 * UEFA's broadcasters take one call per country and week ([SportsApi.uefaBroadcasters]): they are
 * read in [background], and a call waits for them [uefaWaitMs] at most. The countries not read by
 * then are left out of that answer and stored for the next. Only [uefaCountries] are read: in the
 * app, the countries of the profile's channels ([ProfileCountries]).
 */
class SportsData(
    private val api: SportsApi,
    private val cache: SportsCache,
    private val seasonBroadcasters: suspend (SportsGame) -> List<Broadcaster> = { emptyList() },
    private val enabled: () -> Boolean = { true },
    private val uefaCountries: suspend () -> List<String> = { listOf(ProfileCountries.MADAGASCAR) },
    private val background: CoroutineScope = AppScopes.create("SportsData", Dispatchers.IO),
    private val uefaWaitMs: Long = UEFA_WAIT_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    // The UEFA broadcaster reads under way, by cache key, so two screens share one read.
    private val uefaReads = ConcurrentHashMap<String, Deferred<Map<String, List<Broadcaster>>>>()

    /**
     * Teams and leagues whose names match [query] (2 letters at least): ESPN's first, then FIFA's
     * and World Aquatics' that ESPN does not have.
     */
    suspend fun search(query: String): SportsSearchResult {
        val text = query.trim()
        if (!enabled() || text.length < MIN_QUERY) return SportsSearchResult(emptyList(), emptyList())
        val failures = mutableListOf<Exception>()
        return coroutineScope {
            val espnTeams = async { attempt(failures, "ESPN teams") { EspnParser.teamSearch(api.espnTeamSearch(text)) } }
            val espnLeagues = async { attempt(failures, "ESPN leagues") { EspnParser.leagueSearch(api.espnLeagueSearch(text)) } }
            val fifaTeams = async { attempt(failures, "FIFA teams") { FifaParser.teams(api.fifaTeamSearch(text)) } }
            val fifaLeagues = async { attempt(failures, "FIFA leagues") { FifaParser.competitions(api.fifaCompetitionSearch(text)) } }
            val waterPolo =
                async {
                    attempt(failures, "World Aquatics") {
                        val wanted = TeamNames.normalize(text)
                        worldAquaticsCompetitions().map { it.league }.filter { TeamNames.normalize(it.name).contains(wanted) }
                    }
                }
            val espnTeamList = espnTeams.await()
            val teams = espnTeamList + fifaTeams.await().filter { fifa -> espnTeamList.none { TeamNames.sameTeam(it, fifa) } }
            val fifaLeagueList = fifaLeagues.await()
            val espnSoccer = if (fifaLeagueList.isEmpty()) emptyList() else espnSoccerLeaguesOrEmpty()
            val leagues = espnLeagues.await() + fifaLeagueList.filter { !espnHas(it, espnSoccer) } + waterPolo.await()
            if (teams.isEmpty() && leagues.isEmpty() && failures.size == SEARCH_SOURCES) throw failures.first()
            SportsSearchResult(teams, leagues)
        }
    }

    /** [team]'s coming games, [limit] at most, soonest first, with their broadcasters. */
    suspend fun teamGames(
        team: SportsTeam,
        limit: Int = TEAM_GAMES,
    ): List<SportsGame> {
        if (!enabled()) return emptyList()
        val games =
            when (team.source) {
                SportsSource.ESPN -> espnTeamSchedule(team)
                SportsSource.FIFA -> fifaGames(competitionId = null, teamId = team.id, days = TEAM_DAYS)
                SportsSource.WORLD_AQUATICS -> worldAquaticsGames().filter { it.home.id == team.id || it.away?.id == team.id }
                else -> emptyList()
            }
        return withBroadcasters(coming(games).take(limit))
    }

    /** [league]'s games of the next [days] days, soonest first, with their broadcasters. */
    suspend fun leagueGames(
        league: SportsLeague,
        days: Int = LEAGUE_DAYS,
    ): List<SportsGame> {
        if (!enabled()) return emptyList()
        val games =
            when (league.source) {
                SportsSource.ESPN -> espnLeagueGames(league, days)
                SportsSource.FIFA -> fifaGames(competitionId = league.id, teamId = null, days = days)
                SportsSource.WORLD_AQUATICS -> worldAquaticsGames(league.id)
                else -> emptyList()
            }
        val endSec = now() / 1000 + days * DAY_SEC
        return withBroadcasters(coming(games).filter { it.startEpochSec <= endSec })
    }

    /** [league]'s teams: ESPN's list (kept 7 days), else the teams of its coming games. */
    suspend fun leagueTeams(league: SportsLeague): List<SportsTeam> {
        if (!enabled()) return emptyList()
        return when (league.source) {
            SportsSource.ESPN -> {
                val (sport, slug) = EspnParser.splitLeague(league.id) ?: return emptyList()
                cache.get("teams_espn_${league.id}", { EspnParser.leagueTeams(it, sport, slug) }, { SportsCache.LIST_MAX_AGE_MS }) {
                    api.espnLeagueTeams(sport, slug)
                }
            }

            SportsSource.FIFA -> {
                teamsOf(fifaGames(competitionId = league.id, teamId = null, days = TEAM_DAYS))
            }

            SportsSource.WORLD_AQUATICS -> {
                teamsOf(worldAquaticsGames(league.id))
            }

            else -> {
                emptyList()
            }
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

    private suspend fun espnSoccerLeaguesOrEmpty(): List<SportsLeague> =
        attempt(mutableListOf(), "ESPN soccer leagues") {
            cache.get("leagues_espn_soccer", EspnParser::soccerLeagues, { SportsCache.LIST_MAX_AGE_MS }) { api.espnSoccerLeagues() }
        }

    /**
     * Whether ESPN lists FIFA league [fifa] ("Premier League (ENG)"): an ESPN soccer league of the
     * same country (ESPN's slug starts with FIFA's association code: "eng.1") whose name has all
     * the words of FIFA's.
     */
    private fun espnHas(
        fifa: SportsLeague,
        espnSoccer: List<SportsLeague>,
    ): Boolean {
        val association =
            FIFA_ASSOCIATION
                .find(fifa.name)
                ?.groupValues
                ?.get(1)
                ?.lowercase() ?: return false
        val words =
            TeamNames
                .normalize(fifa.name.substringBeforeLast(" ("))
                .split(' ')
                .filter { it.isNotEmpty() }
                .toSet()
        return espnSoccer.any { espn ->
            espn.id.removePrefix("${EspnParser.SOCCER}/").startsWith("$association.") &&
                TeamNames.normalize(espn.name).split(' ').containsAll(words)
        }
    }

    // FIFA

    /** FIFA's games of one competition or team from today for [days] days, read page by page. */
    private suspend fun fifaGames(
        competitionId: String?,
        teamId: String?,
        days: Int,
    ): List<SportsGame> {
        val from = today().atStartOfDay().toInstant(ZoneOffset.UTC)
        val to = from.plus(days.toLong(), ChronoUnit.DAYS)
        val key = "fifa_${competitionId ?: "team_$teamId"}_${today()}_$days"
        return scheduleFromCache(key, { body -> FifaParser.matches(body).distinctBy { it.id } }) {
            fifaPages(from, to, competitionId, teamId)
        }
    }

    // Every page, as one FIFA answer: a full page asks the next from its last game's start.
    private suspend fun fifaPages(
        from: Instant,
        to: Instant,
        competitionId: String?,
        teamId: String?,
    ): String {
        val results = mutableListOf<JsonElement>()
        var start = from
        for (page in 0 until FIFA_MAX_PAGES) {
            val items =
                sportsJson
                    .parseToJsonElement(
                        api.fifaMatches(start, to, competitionId, teamId),
                    ).jsonObject["Results"]
                    ?.jsonArray
                    .orEmpty()
            results += items
            val last =
                items
                    .lastOrNull()
                    ?.jsonObject
                    ?.get("Date")
                    ?.jsonPrimitive
                    ?.content
                    ?.let(::isoEpochSec)
                    ?.let(Instant::ofEpochSecond)
            if (items.size < SportsApi.FIFA_PAGE || last == null || !last.isAfter(start)) break
            start = last
        }
        return JsonObject(mapOf("Results" to JsonArray(results))).toString()
    }

    // World Aquatics

    private suspend fun worldAquaticsCompetitions(): List<WorldAquaticsParser.Competition> =
        cache.get("worldaquatics_competitions", WorldAquaticsParser::competitions, { SportsCache.LIST_MAX_AGE_MS }) {
            api.worldAquaticsCompetitions(today().minusDays(WATER_POLO_PAST_DAYS), today().plusDays(WATER_POLO_AHEAD_DAYS))
        }

    /** The games of the water polo competitions on now or in the next 180 days; of [competitionId] only when given. */
    private suspend fun worldAquaticsGames(competitionId: String? = null): List<SportsGame> {
        val nowSec = now() / 1000
        val competitions =
            worldAquaticsCompetitions().filter { competition ->
                (competitionId == null || competition.league.id == competitionId) &&
                    competition.toEpochSec >= nowSec &&
                    competition.fromEpochSec <= nowSec + TEAM_DAYS * DAY_SEC
            }
        return competitions.flatMap { competition ->
            scheduleFromCache("schedule_worldaquatics_${competition.league.id}", { WorldAquaticsParser.schedule(it, competition.league) }) {
                api.worldAquaticsSchedule(competition.league.id)
            }
        }
    }

    // Broadcasters

    private suspend fun withBroadcasters(games: List<SportsGame>): List<SportsGame> {
        if (games.isEmpty()) return games
        val (nhl, mlb, uefa) =
            coroutineScope {
                listOf(async { nhlBroadcasters(games) }, async { mlbBroadcasters(games) }, async { uefaBroadcasters(games) }).awaitAll()
            }
        return games.map { game ->
            val key = gameKey(game)
            game.copy(
                broadcasters =
                    mergeBroadcasters(
                        listOf(nhl[key], mlb[key], uefa[key], game.broadcasters, season(game)).map { it.orEmpty() },
                    ),
            )
        }
    }

    private suspend fun season(game: SportsGame): List<Broadcaster> =
        attempt(mutableListOf(), "Season table for ${game.id}") {
            seasonBroadcasters(game)
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

    /** UEFA's broadcasters of every country for the UEFA competition games of the next 2 weeks. */
    private suspend fun uefaBroadcasters(games: List<SportsGame>): Map<String, List<Broadcaster>> {
        val nowSec = now() / 1000
        val uefaGames = games.filter { isUefaGame(it) && it.startEpochSec <= nowSec + UEFA_DAYS * DAY_SEC }
        if (uefaGames.isEmpty()) return emptyMap()
        return uefaGames
            .groupBy { weekStart(it.startEpochSec) }
            .flatMap { (week, weekGames) ->
                val matches =
                    attempt(mutableListOf(), "UEFA matches $week") {
                        cache.get(
                            "uefa_matches_$week",
                            UefaParser::matches,
                            { SportsCache.BROADCASTERS_MAX_AGE_MS },
                        ) { uefaMatchPages(week) }
                    }
                if (matches.isEmpty()) return@flatMap emptyList()
                val byMatch = uefaWeekBroadcasters(week)
                weekGames.mapNotNull { game ->
                    val match =
                        matches.firstOrNull {
                            (game.source == SportsSource.UEFA && it.id == game.id) ||
                                TeamNames.sameGame(
                                    game,
                                    it,
                                )
                        }
                    match?.let { byMatch[it.id] }?.let { gameKey(game) to it }
                }
            }.toMap()
    }

    // Every country's broadcasters of [week], by UEFA match id: what is read within uefaWaitMs.
    private suspend fun uefaWeekBroadcasters(week: LocalDate): Map<String, List<Broadcaster>> {
        val reads = attempt(mutableListOf(), "UEFA countries") { uefaCountries() }.map { country -> uefaRead(country, week) }
        val done =
            withTimeoutOrNull(uefaWaitMs) {
                reads.map { read ->
                    attempt(mutableListOf(), "UEFA broadcasters") { listOf(read.await()) }
                }
            }
        val finished =
            done
                ?: reads.filter { it.isCompleted && !it.isCancelled }.map { read ->
                    attempt(mutableListOf(), "UEFA broadcasters") { listOf(read.await()) }
                }
        return finished
            .flatten()
            .flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, lists) -> lists.flatten() }
    }

    private fun uefaRead(
        country: String,
        week: LocalDate,
    ): Deferred<Map<String, List<Broadcaster>>> {
        val key = "uefa_broadcasters_${country}_$week"
        // A finished read is not kept: the next call reads the cache again.
        uefaReads[key]?.takeIf { !it.isCompleted }?.let { return it }
        val read =
            background.async {
                cache.get(key, { body -> UefaParser.broadcasters(body, country).byMatch }, { SportsCache.BROADCASTERS_MAX_AGE_MS }) {
                    uefaBroadcasterPages(country, week)
                }
            }
        uefaReads[key] = read
        read.invokeOnCompletion { uefaReads.remove(key, read) }
        return read
    }

    // Every page of a country's broadcasters, as one UEFA answer.
    private suspend fun uefaBroadcasterPages(
        country: String,
        week: LocalDate,
    ): String {
        val data = mutableListOf<JsonElement>()
        var total = Int.MAX_VALUE
        while (data.size < total && data.size < UEFA_MAX_ITEMS) {
            val body = api.uefaBroadcasters(country, week, week.plusDays(6), offset = data.size)
            val answer = sportsJson.parseToJsonElement(body).jsonObject
            val items = answer["data"]?.jsonArray.orEmpty()
            total = UefaParser.broadcasters(body, country).total
            if (items.isEmpty()) break
            data += items
        }
        val meta = JsonObject(mapOf("collection" to JsonObject(mapOf("totalElements" to JsonPrimitive(data.size.toString())))))
        return JsonObject(mapOf("data" to JsonArray(data), "meta" to meta)).toString()
    }

    // Every page of a week's UEFA games, as one UEFA answer.
    private suspend fun uefaMatchPages(week: LocalDate): String {
        val matches = mutableListOf<JsonElement>()
        while (matches.size < UEFA_MAX_ITEMS) {
            val items = sportsJson.parseToJsonElement(api.uefaMatches(week, week.plusDays(6), offset = matches.size)).jsonArray
            matches.addAll(items)
            if (items.size < SportsApi.UEFA_PAGE) break
        }
        return JsonArray(matches).toString()
    }

    // Helpers

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

    private fun teamsOf(games: List<SportsGame>): List<SportsTeam> =
        games.flatMap { listOfNotNull(it.home, it.away) }.distinctBy { it.id }.sortedBy { it.name }

    private fun today(): LocalDate = utcDate(now() / 1000)

    private fun utcDate(epochSec: Long): LocalDate = Instant.ofEpochSecond(epochSec).atZone(ZoneOffset.UTC).toLocalDate()

    private fun weekStart(epochSec: Long): LocalDate = utcDate(epochSec).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

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
        private val FIFA_ASSOCIATION = Regex("\\(([A-Z]{3})\\)$")
        private const val MIN_QUERY = 2
        private const val SEARCH_SOURCES = 5
        private const val DAY_SEC = 24 * 60 * 60L
        private const val LIVE_SEC = 3 * 60 * 60L
        private const val FIFA_MAX_PAGES = 4
        private const val UEFA_MAX_ITEMS = 500
        private const val UEFA_DAYS = 14
        private const val UEFA_WAIT_MS = 8_000L
        private const val WATER_POLO_PAST_DAYS = 30L
        private const val WATER_POLO_AHEAD_DAYS = 365L

        /** A team page's games at most. */
        const val TEAM_GAMES = 10

        /** How far ahead a team's games are read from sources that need a range: 180 days. */
        const val TEAM_DAYS = 180

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

        /** Whether [game] is in a UEFA competition. */
        fun isUefaGame(game: SportsGame): Boolean =
            game.source == SportsSource.UEFA ||
                game.competition.id.startsWith("soccer/uefa.") ||
                game.competition.name.contains("UEFA", ignoreCase = true)

        private fun gameKey(game: SportsGame) = "${game.source.id}/${game.id}"

        @Volatile
        private var instance: SportsData? = null

        /**
         * The app's sports data, stored in `cacheDir/sports`, with the season table's broadcasters
         * and UEFA's for the countries of the profile's channels, off while Admin's Sports switch is off.
         */
        fun getInstance(context: Context): SportsData =
            instance ?: synchronized(this) {
                instance ?: run {
                    val app = context.applicationContext
                    val settings = AppSettings(app)
                    val seasonTable = SeasonTable.fromAssets(app)
                    SportsData(
                        SportsApi(),
                        SportsCache(File(app.cacheDir, "sports")),
                        seasonBroadcasters = seasonTable::broadcasters,
                        enabled = { settings.showSports },
                        uefaCountries = ProfileCountries(app)::get,
                    )
                }.also { instance = it }
            }
    }
}
