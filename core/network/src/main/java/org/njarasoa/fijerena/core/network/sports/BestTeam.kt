package org.njarasoa.fijerena.core.network.sports

/**
 * The team a Teams search most likely means (the user usually looks for one team): its games show
 * at once at the top of the results. ESPN's order is usually right, so [of] only moves a team up
 * for two reasons, in this order:
 *
 * 1. A name of the team (its name, short name or other names) is the query, or starts with it,
 *    once both are normalized ([TeamNames.normalize]: no accents, no club words). "bayern" finds
 *    "Bayern München"; "chiefs" finds "Kansas City Chiefs" by its other name "Chiefs".
 * 2. A men's senior team comes before a women's team ("UEFA Women's Champions League",
 *    "ned.w.1") and a reserve or youth team ("Bayern Munich II", "Jong Ajax", "U19"), unless the
 *    query names them ("bayern women", "bayern ii"): then those come first.
 *
 * Then ESPN's order. One college's teams in two sports share a name ("Alabama Crimson Tide" in
 * football and men's basketball): ESPN's first wins.
 */
object BestTeam {
    // Words that make a team a women's team, in its names, its league's name or the query.
    private val WOMEN_WORDS = setOf("women", "womens", "woman", "ladies", "feminin", "feminine", "femenino", "femminile", "frauen")

    // Words that make a team a reserve or youth team ("Bayern Munich II", "Jong Ajax", "Ajax 2nd XI").
    private val RESERVE_WORDS =
        setOf("ii", "iii", "b", "jong", "reserve", "reserves", "amateur", "amateurs", "youth", "academy", "2nd", "3rd", "4th")

    // An age group: "U19", "U-21".
    private val AGE_GROUP = Regex("^u\\d{2}$")

    // A women's league slug: "soccer/ned.w.1", "soccer/uefa.wchampions", "basketball/womens-college-basketball".
    private val WOMEN_LEAGUE = Regex("\\.w(\\.|$)|\\.wchampions|women")

    // A youth league slug: "soccer/uefa.youth", "soccer/eng.u21".
    private val YOUTH_LEAGUE = Regex("youth|\\.u\\d{2}")

    /** The best of [teams] for [query]; null when there is no team. */
    fun of(
        query: String,
        teams: List<SportsTeam>,
    ): SportsTeam? {
        val wanted = TeamNames.normalize(query)
        val queryWords = wanted.split(' ').toSet()
        val queryWomen = queryWords.any { it in WOMEN_WORDS }
        val queryReserve = queryWords.any { it in RESERVE_WORDS || AGE_GROUP.matches(it) }
        // sortedWith is stable: teams equal on both keys keep ESPN's order.
        return teams
            .sortedWith(
                compareBy(
                    { !namesMatch(it, wanted) },
                    { isWomen(it) != queryWomen || isReserve(it) != queryReserve },
                ),
            ).firstOrNull()
    }

    private fun namesMatch(
        team: SportsTeam,
        wanted: String,
    ): Boolean = wanted.isNotEmpty() && team.allNames.map(TeamNames::normalize).any { it.startsWith(wanted) }

    private fun nameWords(team: SportsTeam): Set<String> =
        (listOf(team.name) + team.altNames)
            .flatMap { TeamNames.normalize(it).split(' ') }
            .toSet()

    private fun isWomen(team: SportsTeam): Boolean =
        nameWords(team).any { it in WOMEN_WORDS } ||
            team.league?.lowercase()?.let { WOMEN_LEAGUE.containsMatchIn(it) } == true ||
            team.leagueName
                ?.let(TeamNames::normalize)
                ?.split(' ')
                ?.any { it in WOMEN_WORDS } == true

    private fun isReserve(team: SportsTeam): Boolean =
        nameWords(team).any { it in RESERVE_WORDS || AGE_GROUP.matches(it) } ||
            team.league?.lowercase()?.let { YOUTH_LEAGUE.containsMatchIn(it) } == true
}
