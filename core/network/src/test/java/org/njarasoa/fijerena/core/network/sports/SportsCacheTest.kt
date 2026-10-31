package org.njarasoa.fijerena.core.network.sports

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class SportsCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_000_000L
    private val cache by lazy { SportsCache(folder.root, now = { clock }) }

    private suspend fun read(fetch: suspend () -> String): String = cache.get("key", { it }, { 1000L }, fetch)

    @Test
    fun `a fresh answer is used without a call, an old one is read again`() =
        runTest {
            var calls = 0
            assertEquals(
                "a",
                read {
                    calls++
                    "a"
                },
            )
            clock += 500
            assertEquals(
                "a",
                read {
                    calls++
                    "b"
                },
            )
            assertEquals(1, calls)
            clock += 1000
            assertEquals(
                "b",
                read {
                    calls++
                    "b"
                },
            )
            assertEquals(2, calls)
        }

    @Test
    fun `a failed call or an answer that does not parse falls back to the stored one`() =
        runTest {
            read { "a" }
            clock += 5000
            assertEquals("a", read { throw IOException("down") })
            assertEquals(
                "a",
                cache.get("key", {
                    require(it != "bad")
                    it
                }, { 1000L }) { "bad" },
            )
        }

    @Test(expected = IOException::class)
    fun `a failed call with nothing stored fails`() =
        runTest {
            read { throw IOException("down") }
        }

    @Test
    fun `a schedule with a game in the next 2 days stays fresh 1 hour, else 6 hours`() {
        val nowMs = epoch("2026-11-01T12:00:00Z") * 1000
        val games = EspnParser.schedule(sportsFixture("espn_schedule_soccer_all.json"), "soccer", null)
        assertEquals(SportsCache.SCHEDULE_SOON_MAX_AGE_MS, SportsCache.scheduleMaxAgeMs(games, nowMs))
        assertEquals(SportsCache.SCHEDULE_MAX_AGE_MS, SportsCache.scheduleMaxAgeMs(games.drop(2), nowMs))
    }
}
