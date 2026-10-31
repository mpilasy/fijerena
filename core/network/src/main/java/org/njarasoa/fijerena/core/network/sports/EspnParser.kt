package org.njarasoa.fijerena.core.network.sports

import kotlinx.serialization.Serializable

/**
 * ESPN's answers (`site.api.espn.com`, `site.web.api.espn.com`) as [SportsTeam], [SportsLeague] and
 * [SportsGame].
 *
 * Ids: a league is `{sport}/{league}` ("soccer/eng.1", "hockey/nhl"). A soccer team is
 * `soccer/{id}`, since ESPN's soccer team ids hold across leagues and a club moves between them; any
 * other team is `{sport}/{league}/{id}`, since ESPN gives the same id to a college's teams in
 * several sports and leagues ("football/college-football/26", "basketball/mens-college-basketball/26").
 * [SportsTeam.shortName] is ESPN's abbreviation ("BOS"); the NHL and MLB tables read it.
 */
object EspnParser {
    const val SOCCER = "soccer"

    /** The team id of ESPN team [teamId] in [sport]'s [league] (see the object kdoc). */
    fun teamId(
        sport: String,
        league: String?,
        teamId: String,
    ): String = if (sport == SOCCER || league == null) "$sport/$teamId" else "$sport/$league/$teamId"

    /** The ESPN team id inside a [teamId]: its last part. */
    fun espnTeamId(teamId: String): String = teamId.substringAfterLast('/')

    /** `{sport}` and `{league}` of a league id `{sport}/{league}`; null when it is not one. */
    fun splitLeague(leagueId: String?): Pair<String, String>? {
        val parts = leagueId?.split('/') ?: return null
        return if (parts.size == 2 && parts.all { it.isNotBlank() }) parts[0] to parts[1] else null
    }

    /** `common/v3/search?type=team`: the teams found. */
    fun teamSearch(body: String): List<SportsTeam> =
        sportsJson
            .decodeFromString<SearchTeams>(body)
            .items
            .mapNotNull { item ->
                val id = item.id.clean() ?: return@mapNotNull null
                val sport = item.sport.clean() ?: return@mapNotNull null
                val name = item.displayName.clean() ?: item.name.clean() ?: return@mapNotNull null
                val league = item.league.clean() ?: item.defaultLeagueSlug.clean()
                SportsTeam(
                    source = SportsSource.ESPN,
                    id = teamId(sport, league, id),
                    name = name,
                    shortName = item.abbreviation.clean(),
                    altNames = listOfNotNull(item.location.clean(), item.name.clean()).filter { it != name },
                    sport = sport,
                    league = league?.let { "$sport/$it" },
                    leagueName =
                        item.label
                            .clean()
                            ?.removeSuffix(" Team")
                            ?.takeIf { it != "Soccer" },
                    logoUrl = item.logos.firstOrNull()?.href,
                )
            }.distinctBy { it.id }

    /** `search/v2?type=league`: the leagues found. */
    fun leagueSearch(body: String): List<SportsLeague> =
        sportsJson
            .decodeFromString<SearchV2>(body)
            .results
            .filter { it.type == "league" }
            .flatMap { it.contents }
            .mapNotNull { item ->
                val sport = item.sport.clean() ?: return@mapNotNull null
                val slug = item.defaultLeagueSlug.clean() ?: return@mapNotNull null
                val name = item.displayName.clean() ?: return@mapNotNull null
                SportsLeague(SportsSource.ESPN, "$sport/$slug", name, sport, item.image?.default)
            }.distinctBy { it.id }

    /** `leagues/dropdown?sport=..`: each league's slug and name ("ger.1" → "German Bundesliga"). */
    fun leagueNames(body: String): Map<String, String> =
        sportsJson
            .decodeFromString<LeagueList>(body)
            .leagues
            .mapNotNull { league -> league.slug.clean()?.let { slug -> league.name.clean()?.let { slug to it } } }
            .toMap()

    /**
     * A league's name when ESPN gives only its slug (an NFL team's schedule names no league):
     * the known US leagues by their usual names, a short slug in capitals, else the slug.
     */
    fun leagueNameOfSlug(slug: String): String =
        US_LEAGUES[slug] ?: if (slug.length <= SHORT_SLUG && slug.all(Char::isLetter)) slug.uppercase() else slug

    private const val SHORT_SLUG = 5

    private val US_LEAGUES =
        mapOf(
            "college-football" to "NCAA Football",
            "mens-college-basketball" to "NCAA Men's Basketball",
            "womens-college-basketball" to "NCAA Women's Basketball",
            "college-baseball" to "NCAA Baseball",
        )

    /** `{sport}/{league}/teams`: the league's teams. */
    fun leagueTeams(
        body: String,
        sport: String,
        league: String,
    ): List<SportsTeam> {
        val answer = sportsJson.decodeFromString<TeamsAnswer>(body)
        val espnLeague =
            answer.sports
                .firstOrNull()
                ?.leagues
                ?.firstOrNull()
        val leagueName = espnLeague?.name.clean()
        return espnLeague?.teams.orEmpty().mapNotNull { entry ->
            entry.team.toTeam(sport, league)?.copy(leagueName = leagueName)
        }
    }

    /**
     * `teams/{id}/schedule`: the team's games. [sport] and [league] are the request's; a soccer game
     * names its own competition (`soccer/all/...` mixes them).
     */
    fun schedule(
        body: String,
        sport: String,
        league: String?,
    ): List<SportsGame> {
        val answer = sportsJson.decodeFromString<EventsAnswer>(body)
        return answer.events.mapNotNull { event ->
            val slug = event.league?.slug.clean() ?: league ?: return@mapNotNull null
            val competition =
                SportsLeague(
                    SportsSource.ESPN,
                    "$sport/$slug",
                    event.league?.name.clean() ?: event.league?.abbreviation.clean() ?: leagueNameOfSlug(slug),
                    sport,
                )
            event.toGame(sport, slug, competition)
        }
    }

    /** `{sport}/{league}/scoreboard?dates=..`: the league's games of those days. */
    fun scoreboard(
        body: String,
        sport: String,
        league: String,
    ): List<SportsGame> {
        val answer = sportsJson.decodeFromString<EventsAnswer>(body)
        val espnLeague = answer.leagues.firstOrNull()
        val competition =
            SportsLeague(
                SportsSource.ESPN,
                "$sport/$league",
                espnLeague?.name.clean() ?: espnLeague?.abbreviation.clean() ?: leagueNameOfSlug(league),
                sport,
                espnLeague?.let(::leagueLogo),
            )
        return answer.events.mapNotNull { it.toGame(sport, league, competition) }
    }

    private fun Event.toGame(
        sport: String,
        league: String,
        competition: SportsLeague,
    ): SportsGame? {
        val contest = competitions.firstOrNull() ?: return null
        val id = id.clean() ?: return null
        val start = isoEpochSec(contest.date ?: date) ?: return null
        val home = contest.competitors.firstOrNull { it.homeAway == "home" } ?: contest.competitors.firstOrNull()
        val away = contest.competitors.firstOrNull { it !== home }
        val homeTeam = home?.team?.toTeam(sport, league) ?: return null
        val broadcasts = contest.geoBroadcasts.ifEmpty { contest.broadcasts }
        return SportsGame(
            source = SportsSource.ESPN,
            id = id,
            startEpochSec = start,
            home = homeTeam,
            away = away?.team?.toTeam(sport, league),
            competition = competition,
            state = (contest.status ?: status).toState(),
            broadcasters = broadcasters(broadcasts),
        )
    }

    private fun TeamRef.toTeam(
        sport: String,
        league: String,
    ): SportsTeam? {
        val id = id.clean() ?: return null
        val name = displayName.clean() ?: name.clean() ?: return null
        return SportsTeam(
            source = SportsSource.ESPN,
            id = teamId(sport, league, id),
            name = name,
            shortName = abbreviation.clean(),
            altNames =
                listOfNotNull(shortDisplayName.clean(), location.clean(), this.name.clean(), nickname.clean())
                    .filter { it != name }
                    .distinct(),
            sport = sport,
            league = if (sport == SOCCER) null else "$sport/$league",
            logoUrl = logo.clean() ?: logos.firstOrNull()?.href,
        )
    }

    /** ESPN's TV and streaming services of a game: US ones (`region` "us"); radio is left out. */
    private fun broadcasters(broadcasts: List<Broadcast>): List<Broadcaster> =
        broadcasts
            .filter { it.type?.shortName?.equals("radio", ignoreCase = true) != true }
            .flatMap { broadcast ->
                val country = broadcast.region.clean()?.uppercase() ?: US
                listOfNotNull(broadcast.media?.shortName.clean()).plus(broadcast.names.mapNotNull { it.clean() }).map { name ->
                    Broadcaster(name, country, broadcast.media?.logo.clean(), BroadcasterOrigin.ESPN)
                }
            }.distinctBy { it.name.lowercase() to it.country }

    private fun Status?.toState(): GameState {
        val type = this?.type
        val name = type?.name.orEmpty()
        return when {
            "POSTPONED" in name || "DELAYED" in name -> GameState.POSTPONED
            "CANCELED" in name || "CANCELLED" in name || "ABANDONED" in name || "FORFEIT" in name -> GameState.CANCELLED
            type?.state == "in" -> GameState.LIVE
            type?.state == "post" || type?.completed == true -> GameState.FINISHED
            else -> GameState.SCHEDULED
        }
    }

    private fun leagueLogo(league: LeagueRef): String? = league.logos.firstOrNull()?.href

    private const val US = "US"

    @Serializable
    private data class SearchTeams(
        val items: List<SearchTeam> = emptyList(),
    )

    @Serializable
    private data class SearchTeam(
        val id: String? = null,
        val displayName: String? = null,
        val sport: String? = null,
        val league: String? = null,
        val defaultLeagueSlug: String? = null,
        val label: String? = null,
        val location: String? = null,
        val name: String? = null,
        val abbreviation: String? = null,
        val logos: List<Logo> = emptyList(),
    )

    @Serializable
    private data class Logo(
        val href: String? = null,
    )

    @Serializable
    private data class SearchV2(
        val results: List<SearchV2Group> = emptyList(),
    )

    @Serializable
    private data class SearchV2Group(
        val type: String? = null,
        val contents: List<SearchV2Item> = emptyList(),
    )

    @Serializable
    private data class SearchV2Item(
        val displayName: String? = null,
        val defaultLeagueSlug: String? = null,
        val sport: String? = null,
        val image: SearchV2Image? = null,
    )

    @Serializable
    private data class SearchV2Image(
        val default: String? = null,
    )

    @Serializable
    private data class LeagueRef(
        val id: String? = null,
        val name: String? = null,
        val abbreviation: String? = null,
        val slug: String? = null,
        val logos: List<Logo> = emptyList(),
        val teams: List<TeamEntry> = emptyList(),
    )

    @Serializable
    private data class TeamsAnswer(
        val sports: List<TeamsSport> = emptyList(),
    )

    @Serializable
    private data class LeagueList(
        val leagues: List<LeagueRef> = emptyList(),
    )

    @Serializable
    private data class TeamsSport(
        val leagues: List<LeagueRef> = emptyList(),
    )

    @Serializable
    private data class TeamEntry(
        val team: TeamRef = TeamRef(),
    )

    @Serializable
    private data class TeamRef(
        val id: String? = null,
        val abbreviation: String? = null,
        val displayName: String? = null,
        val shortDisplayName: String? = null,
        val location: String? = null,
        val name: String? = null,
        val nickname: String? = null,
        val logo: String? = null,
        val logos: List<Logo> = emptyList(),
    )

    @Serializable
    private data class EventsAnswer(
        val leagues: List<LeagueRef> = emptyList(),
        val events: List<Event> = emptyList(),
    )

    @Serializable
    private data class Event(
        val id: String? = null,
        val date: String? = null,
        val league: LeagueRef? = null,
        val status: Status? = null,
        val competitions: List<Competition> = emptyList(),
    )

    @Serializable
    private data class Competition(
        val date: String? = null,
        val status: Status? = null,
        val competitors: List<Competitor> = emptyList(),
        // A schedule's `broadcasts` and a scoreboard's `geoBroadcasts` have this form; a
        // scoreboard's `broadcasts` has only `names`.
        val broadcasts: List<Broadcast> = emptyList(),
        val geoBroadcasts: List<Broadcast> = emptyList(),
    )

    @Serializable
    private data class Competitor(
        val homeAway: String? = null,
        val team: TeamRef? = null,
    )

    @Serializable
    private data class Broadcast(
        val type: BroadcastType? = null,
        val media: Media? = null,
        val names: List<String> = emptyList(),
        val region: String? = null,
    )

    @Serializable
    private data class BroadcastType(
        val shortName: String? = null,
    )

    @Serializable
    private data class Media(
        val shortName: String? = null,
        val logo: String? = null,
    )

    @Serializable
    private data class Status(
        val type: StatusType? = null,
    )

    @Serializable
    private data class StatusType(
        val name: String? = null,
        val state: String? = null,
        val completed: Boolean = false,
    )
}
