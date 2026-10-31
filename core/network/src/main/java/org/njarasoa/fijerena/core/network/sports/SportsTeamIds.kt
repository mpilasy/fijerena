package org.njarasoa.fijerena.core.network.sports

/**
 * The NHL and MLB ids of ESPN's NHL and MLB teams, by ESPN's abbreviation ([SportsTeam.shortName]):
 * the NHL's and MLB's own APIs have better TV data than ESPN (the sports teams plan → Teams across
 * sources). `SportsTeamIdsTest` checks every ESPN team against both leagues' team lists.
 */
object SportsTeamIds {
    /** ESPN's NHL league id. */
    const val ESPN_NHL = "hockey/nhl"

    /** ESPN's MLB league id. */
    const val ESPN_MLB = "baseball/mlb"

    // ESPN abbreviations that differ from the NHL's tri-codes; the others are the same.
    private val NHL_DIFFERENT =
        mapOf(
            "NJ" to "NJD",
            "LA" to "LAK",
            "SJ" to "SJS",
            "TB" to "TBL",
            "UTAH" to "UTA",
        )

    // ESPN abbreviation → MLB statsapi team id.
    private val MLB_IDS =
        mapOf(
            "ARI" to 109,
            "ATH" to 133,
            "ATL" to 144,
            "BAL" to 110,
            "BOS" to 111,
            "CHC" to 112,
            "CHW" to 145,
            "CIN" to 113,
            "CLE" to 114,
            "COL" to 115,
            "DET" to 116,
            "HOU" to 117,
            "KC" to 118,
            "LAA" to 108,
            "LAD" to 119,
            "MIA" to 146,
            "MIL" to 158,
            "MIN" to 142,
            "NYM" to 121,
            "NYY" to 147,
            "PHI" to 143,
            "PIT" to 134,
            "SD" to 135,
            "SF" to 137,
            "SEA" to 136,
            "STL" to 138,
            "TB" to 139,
            "TEX" to 140,
            "TOR" to 141,
            "WSH" to 120,
        )

    /** The NHL tri-code of ESPN NHL team abbreviation [espnAbbreviation]. */
    fun nhlTriCode(espnAbbreviation: String): String {
        val upper = espnAbbreviation.uppercase()
        return NHL_DIFFERENT[upper] ?: upper
    }

    /** The MLB statsapi team id of ESPN MLB team abbreviation [espnAbbreviation]; null when unknown. */
    fun mlbTeamId(espnAbbreviation: String): Int? = MLB_IDS[espnAbbreviation.uppercase()]

    /** The NHL tri-code of [team] when it is an ESPN NHL team, else null. */
    fun nhlTriCode(team: SportsTeam): String? =
        team.shortName?.takeIf { team.source == SportsSource.ESPN && team.league == ESPN_NHL }?.let(::nhlTriCode)

    /** The MLB team id of [team] when it is an ESPN MLB team, else null. */
    fun mlbTeamId(team: SportsTeam): Int? =
        team.shortName?.takeIf { team.source == SportsSource.ESPN && team.league == ESPN_MLB }?.let(::mlbTeamId)
}
