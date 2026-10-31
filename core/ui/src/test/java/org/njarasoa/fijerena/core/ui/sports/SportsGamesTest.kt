package org.njarasoa.fijerena.core.ui.sports

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** The pages' days (the sports teams plan → Screens). */
class SportsGamesTest {
    @Test
    fun `games fall on the day of the device's time zone, not UTC's`() {
        val zone = ZoneId.of("America/New_York")
        // 01:00 UTC on 2 November is 21:00 on 1 November in New York.
        val late = game("late", ZonedDateTime.of(2026, 11, 2, 1, 0, 0, 0, ZoneId.of("UTC")).toEpochSecond())
        val next = game("next", ZonedDateTime.of(2026, 11, 2, 18, 0, 0, 0, ZoneId.of("UTC")).toEpochSecond())

        val days = gamesByDay(listOf(next, late), zone)

        assertEquals(listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)), days.map { it.date })
        assertEquals(listOf("late"), days[0].games.map { it.id })
    }

    private fun game(
        id: String,
        startSec: Long,
    ) = SportsGame(
        source = SportsSource.ESPN,
        id = id,
        startEpochSec = startSec,
        home = SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer"),
        away = SportsTeam(SportsSource.ESPN, "soccer/363", "Chelsea", sport = "soccer"),
        competition = SportsLeague(SportsSource.ESPN, "soccer/eng.1", "Premier League", "soccer"),
    )
}
