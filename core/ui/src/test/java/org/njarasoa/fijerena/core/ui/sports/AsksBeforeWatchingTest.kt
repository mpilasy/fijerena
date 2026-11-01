package org.njarasoa.fijerena.core.ui.sports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam

class AsksBeforeWatchingTest {
    private val league = SportsLeague(SportsSource.ESPN, "eng.1", "Premier League", "soccer")
    private val game =
        SportsGame(
            SportsSource.ESPN,
            "g",
            1_000,
            home = SportsTeam(SportsSource.ESPN, "1", "Team 1", sport = "soccer"),
            away = null,
            competition = league,
        )

    @Test
    fun `a channel of a game that has not started asks first`() {
        assertTrue(asksBeforeWatching(game, nowSec = 999))
    }

    @Test
    fun `a channel of a game that has started plays at once`() {
        assertFalse(asksBeforeWatching(game, nowSec = 1_000))
        assertFalse(asksBeforeWatching(game, nowSec = 5_000))
    }
}
