package org.njarasoa.fijerena.core.ui.sports

import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsTeam

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
 * channel in its category, on its source. Null for a row with no channel.
 */
fun GameChannel.liveRoute(): Screen.CategoryList? =
    stream?.let { channel ->
        Screen.CategoryList(
            contentType = org.njarasoa.fijerena.core.player.domain.ContentType.LIVE_TV,
            initialCategoryId = channel.categoryId,
            initialStreamId = channel.streamId.toString(),
            providerId = channel.providerId,
        )
    }
