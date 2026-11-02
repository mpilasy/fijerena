package org.njarasoa.fijerena.core.ui.sports

import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.viewmodels.SearchSession
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel

/** The team's page, with what the team knows, so the page and Follow have it. */
fun SportsTeam.route(): Screen.SportsTeam = Screen.SportsTeam(source.id, id, name, sport, league, logoUrl)

/** The league's page. */
fun SportsLeague.route(): Screen.SportsLeague = Screen.SportsLeague(source.id, id, name, sport, logoUrl)

/** The team a route names; null for a source this build does not know. */
fun Screen.SportsTeam.team(): SportsTeam? = sportsTeamOf(source, teamId, name, sport, league, logoUrl)

/** The league a route names; null for a source this build does not know. */
fun Screen.SportsLeague.league(): SportsLeague? = sportsLeagueOf(source, leagueId, name, sport, logoUrl)

/**
 * The channel of a game sheet row, opened as a guide result opens its channel: the preview on that
 * channel in its category, on its source.
 */
fun GameChannel.liveRoute(): Screen.CategoryList =
    Screen.CategoryList(
        contentType = org.njarasoa.fijerena.core.player.domain.ContentType.LIVE_TV,
        initialCategoryId = stream.categoryId,
        initialStreamId = stream.streamId.toString(),
        providerId = stream.providerId,
    )

/**
 * Search on its Teams type, for the Sports section's Find Teams: over everything, opened on Teams
 * ([SearchSession.arrive] sets the type the route brings).
 */
fun teamsSearch(): Screen.Search = Screen.Search(SearchViewModel.CONTENT_TYPE_ALL, initialTeamsTab = true)
