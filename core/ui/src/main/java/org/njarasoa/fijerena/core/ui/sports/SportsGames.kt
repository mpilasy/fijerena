package org.njarasoa.fijerena.core.ui.sports

import org.njarasoa.fijerena.core.network.sports.SportsGame
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// What the sports screens show of a game, the same on TV and phone (the sports teams plan → Screens).

/** A game's key on a screen: its source and its id in that source. */
val SportsGame.key: String get() = "${source.id}/$id"

/** The games of one day in the device's time zone, soonest first. */
data class GameDay(
    val date: LocalDate,
    val games: List<SportsGame>,
)

/** [games] by day in [zone], days and games soonest first. */
fun gamesByDay(
    games: List<SportsGame>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<GameDay> =
    games
        .sortedBy { it.startEpochSec }
        .groupBy { Instant.ofEpochSecond(it.startEpochSec).atZone(zone).toLocalDate() }
        .map { (date, list) -> GameDay(date, list) }
