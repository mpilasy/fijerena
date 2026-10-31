package org.njarasoa.fijerena.core.ui.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.sports.Broadcaster
import org.njarasoa.fijerena.core.network.sports.BroadcasterOrigin
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.GameChannelHow
import org.njarasoa.fijerena.core.network.sports.LiveChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** The game sheet's groups and the pages' days (the sports teams plan → Screens). */
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

    @Test
    fun `one broadcaster in many countries shows once, with every country`() {
        val region = listOf("ZA", "NG", "KE", "MG", "GH").map { Broadcaster("SuperSport", it, from = BroadcasterOrigin.SEASON_TABLE) }
        val others =
            listOf(
                Broadcaster("Canal+", "FR", from = BroadcasterOrigin.SEASON_TABLE),
                Broadcaster("supersport", "ZM", from = BroadcasterOrigin.SEASON_TABLE),
                Broadcaster("DAZN", null, from = BroadcasterOrigin.SEASON_TABLE),
            )

        val groups = groupBroadcasters(region + others)

        assertEquals(listOf("DAZN", "Canal+", "SuperSport"), groups.map { it.name })
        assertTrue(groups[0].worldwide)
        assertEquals(listOf("GH", "KE", "MG", "NG", "ZA", "ZM"), groups[2].countries)
        assertTrue(hasMoreCountries(groups[2]))
        assertFalse(hasMoreCountries(groups[1]))
    }

    @Test
    fun `the sheet keeps the channels in order and groups the broadcasters with none`() {
        val channel = LiveChannel(1, 7, "FR: Canal+ Sport", "canalplus.fr", "c")
        val canal = Broadcaster("Canal+", "FR", from = BroadcasterOrigin.UEFA)
        val channels =
            listOf(
                GameChannel(channel, null, canal, GameChannelHow.BROADCASTER_CHANNEL),
                GameChannel(
                    null,
                    null,
                    Broadcaster("SuperSport", "ZA", from = BroadcasterOrigin.SEASON_TABLE),
                    GameChannelHow.BROADCASTER_ONLY,
                ),
                GameChannel(
                    null,
                    null,
                    Broadcaster("SuperSport", "NG", from = BroadcasterOrigin.SEASON_TABLE),
                    GameChannelHow.BROADCASTER_ONLY,
                ),
            )

        val sheet = GameSheet.of(channels)

        assertEquals(listOf(channel), sheet.channels.map { it.stream })
        assertEquals(listOf("SuperSport"), sheet.broadcasters.map { it.name })
        assertFalse(sheet.notAnnounced)
        assertTrue(GameSheet.of(listOf(GameChannel(null, null, null, GameChannelHow.NOT_ANNOUNCED))).notAnnounced)
    }

    @Test
    fun `a game with no channel match keeps its broadcasters as text`() {
        val withTv = game("a", 0).copy(broadcasters = listOf(Broadcaster("ESPN", "US", from = BroadcasterOrigin.ESPN)))

        assertEquals(listOf(GameChannelHow.BROADCASTER_ONLY), unmatchedChannels(withTv).map { it.how })
        assertEquals(listOf(GameChannelHow.NOT_ANNOUNCED), unmatchedChannels(game("b", 0)).map { it.how })
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
