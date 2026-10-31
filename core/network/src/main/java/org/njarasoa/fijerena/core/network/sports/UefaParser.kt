package org.njarasoa.fijerena.core.network.sports

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * UEFA's answers: the games of its competitions (`match.uefa.com/v5/matches`) and their
 * broadcasters in one country (`broadcaster.uefa.com/v1/broadcasters`). Ids are UEFA's own.
 */
object UefaParser {
    const val SPORT = "soccer"

    /** The UEFA competitions asked for broadcasters: UCL, UEL, UECL, Nations League, UWCL, EURO and World Cup qualifiers. */
    val COMPETITION_IDS = listOf("1", "14", "2019", "2014", "28", "3", "17")

    /** `v5/matches`: the games. */
    fun matches(body: String): List<SportsGame> =
        sportsJson.decodeFromString<List<Match>>(body).mapNotNull { match ->
            val id = match.id.clean() ?: return@mapNotNull null
            val start = isoEpochSec(match.kickOffTime?.dateTime) ?: return@mapNotNull null
            val home = match.homeTeam?.toTeam() ?: return@mapNotNull null
            val competitionId = match.competition?.id.clean() ?: return@mapNotNull null
            SportsGame(
                source = SportsSource.UEFA,
                id = id,
                startEpochSec = start,
                home = home,
                away = match.awayTeam?.toTeam(),
                competition =
                    SportsLeague(
                        SportsSource.UEFA,
                        competitionId,
                        match.competition
                            ?.metaData
                            ?.name
                            .clean() ?: competitionId,
                        SPORT,
                    ),
                state = state(match.status),
            )
        }

    /** One page of `v1/broadcasters` for [country]: the broadcasters by UEFA match id, and the total of matches. */
    data class BroadcasterPage(
        val byMatch: Map<String, List<Broadcaster>>,
        val total: Int,
    )

    /** `v1/broadcasters?countryCode=[country]`: one page. */
    fun broadcasters(
        body: String,
        country: String,
    ): BroadcasterPage {
        val answer = sportsJson.decodeFromString<BroadcastersAnswer>(body)
        val byMatch =
            answer.data
                .mapNotNull { entry ->
                    val matchId = entry.matchId.clean() ?: return@mapNotNull null
                    matchId to
                        entry.broadcasters.mapNotNull { broadcaster ->
                            broadcaster.name.clean()?.let { Broadcaster(it, country, broadcaster.logo.clean(), BroadcasterOrigin.UEFA) }
                        }
                }.groupBy({ it.first }, { it.second })
                .mapValues { (_, lists) -> lists.flatten().distinctBy { it.name.lowercase() } }
        val total =
            answer.meta
                ?.collection
                ?.totalElements
                ?.toIntOrNull() ?: answer.data.size
        return BroadcasterPage(byMatch, total)
    }

    private fun Team.toTeam(): SportsTeam? {
        val id = id.clean() ?: return null
        val name = internationalName.clean() ?: translations?.displayName?.en.clean() ?: return null
        return SportsTeam(
            source = SportsSource.UEFA,
            id = id,
            name = name,
            shortName = teamCode.clean(),
            altNames =
                listOfNotNull(translations?.displayName?.en.clean(), translations?.displayOfficialName?.en.clean())
                    .filter { it != name }
                    .distinct(),
            sport = SPORT,
            logoUrl = mediumLogoUrl.clean(),
        )
    }

    private fun state(status: String?): GameState =
        when (status?.uppercase()) {
            "LIVE" -> GameState.LIVE
            "FINISHED" -> GameState.FINISHED
            "POSTPONED" -> GameState.POSTPONED
            "CANCELLED", "ABANDONED" -> GameState.CANCELLED
            else -> GameState.SCHEDULED
        }

    @Serializable
    private data class Match(
        val id: String? = null,
        val status: String? = null,
        val kickOffTime: KickOff? = null,
        val competition: Competition? = null,
        val homeTeam: Team? = null,
        val awayTeam: Team? = null,
    )

    @Serializable
    private data class KickOff(
        val dateTime: String? = null,
    )

    @Serializable
    private data class Competition(
        val id: String? = null,
        val metaData: MetaData? = null,
    )

    @Serializable
    private data class MetaData(
        val name: String? = null,
    )

    @Serializable
    private data class Team(
        val id: String? = null,
        val internationalName: String? = null,
        val teamCode: String? = null,
        val mediumLogoUrl: String? = null,
        val translations: Translations? = null,
    )

    @Serializable
    private data class Translations(
        val displayName: English? = null,
        val displayOfficialName: English? = null,
    )

    @Serializable
    private data class English(
        @SerialName("EN") val en: String? = null,
    )

    @Serializable
    private data class BroadcastersAnswer(
        val data: List<MatchBroadcasters> = emptyList(),
        val meta: Meta? = null,
    )

    @Serializable
    private data class MatchBroadcasters(
        val matchId: String? = null,
        val broadcasters: List<UefaBroadcaster> = emptyList(),
    )

    @Serializable
    private data class UefaBroadcaster(
        val name: String? = null,
        val logo: String? = null,
    )

    @Serializable
    private data class Meta(
        val collection: Collection? = null,
    )

    @Serializable
    private data class Collection(
        val totalElements: String? = null,
    )
}
