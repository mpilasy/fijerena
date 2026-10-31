package org.njarasoa.fijerena.core.network.sports

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * World Aquatics' water polo (`api.worldaquatics.com/fina`): its competitions and their games. Only
 * national teams play there (World Cups, World Championships); a competition's games are listed
 * once its draw is made. No TV data.
 *
 * Ids: a competition is World Aquatics' number ("5134"); a team is its country code and gender
 * ("SRB/men", "ESP/women"), since the men's and the women's teams share the country code.
 */
object WorldAquaticsParser {
    const val SPORT = "water-polo"

    /** A water polo competition with its dates (epoch seconds). */
    data class Competition(
        val league: SportsLeague,
        val fromEpochSec: Long,
        val toEpochSec: Long,
    )

    /** `competitions?disciplines=WP`: the competitions. */
    fun competitions(body: String): List<Competition> =
        sportsJson.decodeFromString<CompetitionsAnswer>(body).content.mapNotNull { item ->
            val id = item.id?.toString() ?: return@mapNotNull null
            val name = item.name.clean() ?: return@mapNotNull null
            val from = isoEpochSec(item.dateFrom) ?: return@mapNotNull null
            val to = isoEpochSec(item.dateTo) ?: from
            Competition(SportsLeague(SportsSource.WORLD_AQUATICS, id, name, SPORT), from, to + DAY_SEC)
        }

    /** `competitions/{id}/schedule`: the games of [competition]. */
    fun schedule(
        body: String,
        competition: SportsLeague,
    ): List<SportsGame> =
        sportsJson.decodeFromString<List<ScheduleItem>>(body).mapNotNull { item ->
            val start = isoEpochSec(item.utcDateTime) ?: return@mapNotNull null
            val gender = if (item.disciplineName?.startsWith("Women", ignoreCase = true) == true) WOMEN else MEN
            val home = team(item.teamHomeCode, item.teamHomeName, gender) ?: return@mapNotNull null
            SportsGame(
                source = SportsSource.WORLD_AQUATICS,
                id = "${competition.id}/$gender/${item.matchNo ?: start}",
                startEpochSec = start,
                home = home,
                away = team(item.teamAwayCode, item.teamAwayName, gender),
                competition = competition,
                state = state(item.resultStatus),
            )
        }

    private fun team(
        code: String?,
        name: String?,
        gender: String,
    ): SportsTeam? {
        val country = code.clean() ?: return null
        return SportsTeam(
            source = SportsSource.WORLD_AQUATICS,
            id = "$country/$gender",
            name = name.clean() ?: country,
            shortName = country,
            sport = SPORT,
            leagueName = if (gender == WOMEN) "Women's water polo" else "Men's water polo",
        )
    }

    private fun state(status: String?): GameState =
        when (status?.uppercase()) {
            "OFFICIAL", "UNOFFICIAL" -> GameState.FINISHED
            "LIVE", "RUNNING", "INTERMEDIATE" -> GameState.LIVE
            "CANCELLED" -> GameState.CANCELLED
            "POSTPONED" -> GameState.POSTPONED
            else -> GameState.SCHEDULED
        }

    private const val MEN = "men"
    private const val WOMEN = "women"
    private const val DAY_SEC = 24 * 60 * 60L

    @Serializable
    private data class CompetitionsAnswer(
        val content: List<CompetitionItem> = emptyList(),
    )

    @Serializable
    private data class CompetitionItem(
        val id: Long? = null,
        val name: String? = null,
        val dateFrom: String? = null,
        val dateTo: String? = null,
    )

    @Serializable
    private data class ScheduleItem(
        @SerialName("UtcDateTime") val utcDateTime: String? = null,
        @SerialName("DisciplineName") val disciplineName: String? = null,
        @SerialName("MatchNo") val matchNo: Int? = null,
        @SerialName("TeamHomeCode") val teamHomeCode: String? = null,
        @SerialName("TeamHomeName") val teamHomeName: String? = null,
        @SerialName("TeamAwayCode") val teamAwayCode: String? = null,
        @SerialName("TeamAwayName") val teamAwayName: String? = null,
        @SerialName("ResultStatus") val resultStatus: String? = null,
    )
}
