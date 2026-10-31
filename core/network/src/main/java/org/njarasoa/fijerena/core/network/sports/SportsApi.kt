package org.njarasoa.fijerena.core.network.sports

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import org.njarasoa.fijerena.core.player.diagnostics.recordFailedCalls
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One GET that answers the body as text, or throws. [SportsApi]'s link to the network; tests give their own. */
fun interface SportsTransport {
    suspend fun get(url: String): String
}

/**
 * The HTTP client of the sports sources (the sports teams plan → Gate): Ktor on
 * `NetworkModule.okHttpClient` as `TmdbApiService`, its own dispatcher and connection pool, a
 * deadline per call, gzip, and an app User-Agent. None of the sources needs a key. A non-2xx
 * answer throws.
 */
class KtorSportsTransport : SportsTransport {
    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            // An own Dispatcher and ConnectionPool, as TmdbApiService: `preconfigured` alone shares
            // NetworkModule.okHttpClient's Dispatcher.
            engine {
                preconfigured = org.njarasoa.fijerena.core.player.network.NetworkModule.okHttpClient
                config {
                    dispatcher(okhttp3.Dispatcher())
                    connectionPool(okhttp3.ConnectionPool(5, 5, java.util.concurrent.TimeUnit.MINUTES))
                }
            }
            install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
            recordFailedCalls()
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            defaultRequest { header(HttpHeaders.UserAgent, USER_AGENT) }
        }
    }

    override suspend fun get(url: String): String {
        val response = client.get(url)
        if (!response.status.isSuccess()) throw IOException("Sports ${url.substringBefore('?')}: HTTP ${response.status.value}")
        return response.bodyAsText()
    }

    companion object {
        // The largest answers (an NHL season, a FIFA page of 500 games) are about 1 MB.
        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val USER_AGENT = "Fijerena (Android)"
    }
}

/**
 * The sports sources' endpoints (the sports teams plan → Data sources). Each answers the raw body,
 * which [SportsCache] keeps and the parsers read. Each source has its own [SportsRateLimiter].
 */
class SportsApi(
    private val transport: SportsTransport = KtorSportsTransport(),
    private val limiters: Map<SportsSource, SportsRateLimiter> = SportsSource.entries.associateWith { SportsRateLimiter() },
) {
    // ESPN

    /** ESPN teams whose name matches [query], 10 at most. */
    suspend fun espnTeamSearch(query: String): String =
        call(SportsSource.ESPN, url("$ESPN_WEB/common/v3/search", "query" to query, "type" to "team", "limit" to "10"))

    /** ESPN leagues whose name matches [query]. `common/v3/search?type=league` answers HTTP 400; `search/v2` works. */
    suspend fun espnLeagueSearch(query: String): String =
        call(SportsSource.ESPN, url("$ESPN_WEB/search/v2", "query" to query, "type" to "league", "limit" to "10"))

    /** Every soccer league ESPN lists, with names (the `sports.core.api.espn.com` list gives only links). */
    suspend fun espnSoccerLeagues(): String =
        call(
            SportsSource.ESPN,
            url(
                "$ESPN_SITE/leagues/dropdown",
                "sport" to "soccer",
                "limit" to "300",
            ),
        )

    /** The teams of ESPN league `{sport}/{league}`. */
    suspend fun espnLeagueTeams(
        sport: String,
        league: String,
    ): String = call(SportsSource.ESPN, "$ESPN_SITE/sports/$sport/$league/teams")

    /**
     * A team's games. A soccer team's games of every competition (league, cups, UEFA) come only
     * from the `all` league with `fixture=true`; without it ESPN sends the played games of one
     * league. Other sports send the whole season of the team's league.
     */
    suspend fun espnTeamSchedule(
        sport: String,
        league: String?,
        espnTeamId: String,
    ): String =
        if (sport == EspnParser.SOCCER) {
            call(SportsSource.ESPN, url("$ESPN_SITE/sports/soccer/all/teams/$espnTeamId/schedule", "fixture" to "true"))
        } else {
            call(SportsSource.ESPN, "$ESPN_SITE/sports/$sport/$league/teams/$espnTeamId/schedule")
        }

    /**
     * A league's games of one [day]. The range form (`dates=YYYYMMDD-YYYYMMDD`) answers HTTP 400
     * ("Failed to get events endpoint") since at least 2026-10-31, so a range is read day by day.
     */
    suspend fun espnScoreboard(
        sport: String,
        league: String,
        day: LocalDate,
    ): String = call(SportsSource.ESPN, url("$ESPN_SITE/sports/$sport/$league/scoreboard", "dates" to day.toString().replace("-", "")))

    // FIFA

    /**
     * FIFA's games from [from] to [to], of one competition or one team when given, [FIFA_PAGE] at
     * most, oldest first. FIFA's `ContinuationToken` could not be used (every form tried answers
     * "Both token and hash are required"), so the next page is asked from the last game's start.
     */
    suspend fun fifaMatches(
        from: Instant,
        to: Instant,
        competitionId: String? = null,
        teamId: String? = null,
    ): String =
        call(
            SportsSource.FIFA,
            url(
                "$FIFA/calendar/matches",
                "from" to from.truncatedTo(ChronoUnit.SECONDS).toString(),
                "to" to to.truncatedTo(ChronoUnit.SECONDS).toString(),
                "count" to FIFA_PAGE.toString(),
                "language" to "en",
                "idCompetition" to competitionId,
                "idTeam" to teamId,
            ),
        )

    /** FIFA competitions whose name matches [query]. */
    suspend fun fifaCompetitionSearch(query: String): String =
        call(SportsSource.FIFA, url("$FIFA/competitions/search", "name" to query, "language" to "en"))

    /** FIFA teams whose name matches [query]. */
    suspend fun fifaTeamSearch(query: String): String =
        call(
            SportsSource.FIFA,
            url(
                "$FIFA/teams/search",
                "name" to query,
                "language" to "en",
            ),
        )

    // UEFA

    /** UEFA's games from [from] to [to] of [competitionIds] (one team's when [teamId] is given), [limit] from [offset]. */
    suspend fun uefaMatches(
        from: LocalDate,
        to: LocalDate,
        competitionIds: List<String> = UefaParser.COMPETITION_IDS,
        teamId: String? = null,
        offset: Int = 0,
        limit: Int = UEFA_PAGE,
    ): String =
        call(
            SportsSource.UEFA,
            url(
                "https://match.uefa.com/v5/matches",
                "competitionId" to competitionIds.joinToString(","),
                "teamId" to teamId,
                "fromDate" to from.toString(),
                "toDate" to to.toString(),
                "order" to "ASC",
                "offset" to offset.toString(),
                "limit" to limit.toString(),
            ),
        )

    /**
     * UEFA's broadcasters in [country] (ISO alpha-2) of every UEFA game from [from] to [to].
     * `countryCode` is required: without it UEFA redirects to the country of the caller's address,
     * so every country takes its own call. 100 games a page at most.
     */
    suspend fun uefaBroadcasters(
        country: String,
        from: LocalDate,
        to: LocalDate,
        offset: Int = 0,
    ): String =
        call(
            SportsSource.UEFA,
            url(
                "https://broadcaster.uefa.com/v1/broadcasters",
                "countryCode" to country,
                "startDate" to from.toString(),
                "endDate" to to.toString(),
                "offset" to offset.toString(),
                "limit" to UEFA_PAGE.toString(),
            ),
        )

    // NHL and MLB

    /** The NHL club [triCode]'s season, with each game's TV. `now` redirects to the current season. */
    suspend fun nhlClubSchedule(triCode: String): String =
        call(SportsSource.NHL, "https://api-web.nhle.com/v1/club-schedule-season/$triCode/now")

    /** MLB team [teamId]'s games from [from] to [to], with their broadcasters. */
    suspend fun mlbSchedule(
        teamId: Int,
        from: LocalDate,
        to: LocalDate,
    ): String =
        call(
            SportsSource.MLB,
            url(
                "https://statsapi.mlb.com/api/v1/schedule",
                "sportId" to "1",
                "teamId" to teamId.toString(),
                "startDate" to from.toString(),
                "endDate" to to.toString(),
                "hydrate" to "broadcasts",
            ),
        )

    // World Aquatics

    /** World Aquatics' water polo competitions from [from] to [to]. */
    suspend fun worldAquaticsCompetitions(
        from: LocalDate,
        to: LocalDate,
    ): String =
        call(
            SportsSource.WORLD_AQUATICS,
            url(
                "$WORLD_AQUATICS/competitions",
                "pageSize" to "100",
                "venueDateFrom" to "${from}T00:00:00Z",
                "venueDateTo" to "${to}T00:00:00Z",
                "disciplines" to "WP",
                "sort" to "dateFrom,asc",
            ),
        )

    /** The games of World Aquatics competition [competitionId]; empty until its draw. */
    suspend fun worldAquaticsSchedule(competitionId: String): String =
        call(SportsSource.WORLD_AQUATICS, "$WORLD_AQUATICS/competitions/$competitionId/schedule")

    private suspend fun call(
        source: SportsSource,
        url: String,
    ): String = limiters.getValue(source).execute { transport.get(url) }

    companion object {
        private const val ESPN_SITE = "https://site.api.espn.com/apis/site/v2"
        private const val ESPN_WEB = "https://site.web.api.espn.com/apis"
        private const val FIFA = "https://api.fifa.com/api/v3"
        private const val WORLD_AQUATICS = "https://api.worldaquatics.com/fina"

        /** The games of one FIFA page. */
        const val FIFA_PAGE = 500

        /** UEFA's largest page: a bigger `limit` is refused. */
        const val UEFA_PAGE = 100

        /** [base] with the [parameters] that have a value, encoded. */
        fun url(
            base: String,
            vararg parameters: Pair<String, String?>,
        ): String {
            val query =
                parameters
                    .filter { it.second != null }
                    // The String charset name: the Charset overload needs API 33 (minSdk is 30).
                    .joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8").replace("%2C", ",")}" }
            return if (query.isEmpty()) base else "$base?$query"
        }
    }
}
