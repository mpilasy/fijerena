package org.njarasoa.fijerena.core.network.sports

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * FIFA's answers (`api.fifa.com/api/v3`) as [SportsTeam], [SportsLeague] and [SportsGame]. FIFA has
 * no TV data. Ids are FIFA's own (`IdTeam`, `IdCompetition`, `IdMatch`).
 *
 * Many leagues share a name ("Premier League", "Ligue 1"), so a league's name carries its member
 * association: "Premier League (ENG)".
 */
object FifaParser {
    const val SPORT = "soccer"

    /** `calendar/matches?from=..&to=..`: one page of games. */
    fun matches(body: String): List<SportsGame> =
        sportsJson.decodeFromString<Page<Match>>(body).results.mapNotNull { match ->
            val id = match.idMatch.clean() ?: return@mapNotNull null
            val start = isoEpochSec(match.date) ?: return@mapNotNull null
            val home = match.home?.toTeam() ?: return@mapNotNull null
            val competitionId = match.idCompetition.clean() ?: return@mapNotNull null
            SportsGame(
                source = SportsSource.FIFA,
                id = id,
                startEpochSec = start,
                home = home,
                away = match.away?.toTeam(),
                competition =
                    SportsLeague(SportsSource.FIFA, competitionId, match.competitionName.text() ?: competitionId, SPORT),
                state = state(match.matchStatus),
            )
        }

    /** `competitions/search?name=..`: the leagues found. */
    fun competitions(body: String): List<SportsLeague> =
        sportsJson.decodeFromString<Page<Competition>>(body).results.mapNotNull { competition ->
            val id = competition.idCompetition.clean() ?: return@mapNotNull null
            val name = competition.name.text() ?: return@mapNotNull null
            val association = competition.idMemberAssociation.firstOrNull().clean()
            SportsLeague(SportsSource.FIFA, id, if (association != null) "$name ($association)" else name, SPORT)
        }

    /** `teams/search?name=..`: the teams found. */
    fun teams(body: String): List<SportsTeam> =
        sportsJson.decodeFromString<Page<Team>>(body).results.mapNotNull { team ->
            val id = team.idTeam.clean() ?: return@mapNotNull null
            val name = team.name.text() ?: return@mapNotNull null
            SportsTeam(
                source = SportsSource.FIFA,
                id = id,
                name = name,
                shortName = team.abbreviation.clean(),
                altNames = listOfNotNull(team.shortClubName.clean()).filter { it != name },
                sport = SPORT,
                logoUrl = pictureUrl(team.pictureUrl),
            )
        }

    private fun MatchTeam.toTeam(): SportsTeam? {
        val id = idTeam.clean() ?: return null
        val name = teamName.text() ?: shortClubName.clean() ?: return null
        return SportsTeam(
            source = SportsSource.FIFA,
            id = id,
            name = name,
            shortName = abbreviation.clean(),
            altNames = listOfNotNull(shortClubName.clean()).filter { it != name },
            sport = SPORT,
            logoUrl = pictureUrl(pictureUrl),
        )
    }

    // FIFA's picture URLs are templates: "https://api.fifa.com/api/v3/picture/flags-{format}-{size}/BRA".
    private fun pictureUrl(template: String?): String? = template.clean()?.replace("{format}", "sq")?.replace("{size}", "4")

    // FIFA's match states: 0 played, 1 to be played, 3 live, 4 abandoned, 7 postponed, 8 cancelled.
    private fun state(status: Int?): GameState =
        when (status) {
            0 -> GameState.FINISHED
            3 -> GameState.LIVE
            4, 8 -> GameState.CANCELLED
            7 -> GameState.POSTPONED
            else -> GameState.SCHEDULED
        }

    private fun List<Localized>.text(): String? =
        firstOrNull { it.locale?.startsWith("en") == true }?.description.clean() ?: firstOrNull()?.description.clean()

    @Serializable
    private data class Page<T>(
        @SerialName("Results") val results: List<T> = emptyList(),
    )

    @Serializable
    private data class Localized(
        @SerialName("Locale") val locale: String? = null,
        @SerialName("Description") val description: String? = null,
    )

    @Serializable
    private data class Match(
        @SerialName("IdMatch") val idMatch: String? = null,
        @SerialName("IdCompetition") val idCompetition: String? = null,
        @SerialName("CompetitionName") val competitionName: List<Localized> = emptyList(),
        @SerialName("Date") val date: String? = null,
        @SerialName("Home") val home: MatchTeam? = null,
        @SerialName("Away") val away: MatchTeam? = null,
        @SerialName("MatchStatus") val matchStatus: Int? = null,
    )

    @Serializable
    private data class MatchTeam(
        @SerialName("IdTeam") val idTeam: String? = null,
        @SerialName("TeamName") val teamName: List<Localized> = emptyList(),
        @SerialName("ShortClubName") val shortClubName: String? = null,
        @SerialName("Abbreviation") val abbreviation: String? = null,
        @SerialName("PictureUrl") val pictureUrl: String? = null,
    )

    @Serializable
    private data class Competition(
        @SerialName("IdCompetition") val idCompetition: String? = null,
        @SerialName("Name") val name: List<Localized> = emptyList(),
        @SerialName("IdMemberAssociation") val idMemberAssociation: List<String> = emptyList(),
    )

    @Serializable
    private data class Team(
        @SerialName("IdTeam") val idTeam: String? = null,
        @SerialName("Name") val name: List<Localized> = emptyList(),
        @SerialName("ShortClubName") val shortClubName: String? = null,
        @SerialName("Abbreviation") val abbreviation: String? = null,
        @SerialName("PictureUrl") val pictureUrl: String? = null,
    )
}
