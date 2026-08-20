package org.njarasoa.fijerena.core.network.jellyfin

import org.njarasoa.fijerena.core.player.domain.SeriesId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Benchmark test for JellyfinMediaProvider.getSeriesDetail() to measure
 * the impact of parallelizing independent API calls.
 *
 * Each API call is mocked with a simulated network delay to represent
 * real-world latency. With 3 independent calls:
 * - Sequential: ~3x latency
 * - Parallel:   ~1x latency (all calls overlap)
 */
class JellyfinSeriesDetailBenchmarkTest {
    private val api = mockk<JellyfinApiService>(relaxed = true)
    private val provider =
        JellyfinMediaProvider(
            providerId = 1L,
            serverUrl = "http://localhost",
            username = "user",
            password = "pass",
            deviceId = "device",
            injectedApi = api,
        )

    private val seriesId = "series1"

    private fun setupMocksWithDelay(delayMs: Long) {
        every { api.isAuthenticated() } returns true

        val seriesItem = JellyfinItem(id = seriesId, name = "Test Series", type = "Series")
        val season1 = JellyfinItem(id = "s1", name = "Season 1", type = "Season", indexNumber = 1)
        val season2 = JellyfinItem(id = "s2", name = "Season 2", type = "Season", indexNumber = 2)
        val season3 = JellyfinItem(id = "s3", name = "Season 3", type = "Season", indexNumber = 3)

        val episodes =
            (1..3).flatMap { seasonNum ->
                (1..10).map { epNum ->
                    JellyfinItem(
                        id = "ep_${seasonNum}_$epNum",
                        name = "S${seasonNum}E$epNum",
                        type = "Episode",
                        indexNumber = epNum,
                        parentIndexNumber = seasonNum,
                    )
                }
            }

        coEvery { api.getItemById(seriesId) } coAnswers {
            delay(delayMs)
            Result.success(seriesItem)
        }

        coEvery { api.getSeasons(seriesId) } coAnswers {
            delay(delayMs)
            Result.success(listOf(season1, season2, season3))
        }

        coEvery {
            api.getItems(
                parentId = seriesId,
                includeItemTypes = "Episode",
                sortBy = any(),
                sortOrder = any(),
            )
        } coAnswers {
            delay(delayMs)
            Result.success(episodes)
        }
    }

    @Test
    fun `benchmark getSeriesDetail with parallel API calls`() =
        runBlocking {
            val simulatedLatencyMs = 100L
            setupMocksWithDelay(simulatedLatencyMs)

            // Warmup
            repeat(3) {
                provider.getSeriesDetail(SeriesId(seriesId))
            }

            // Measure
            val iterations = 5
            val times = mutableListOf<Long>()

            repeat(iterations) {
                val elapsed =
                    measureTimeMillis {
                        val result = provider.getSeriesDetail(SeriesId(seriesId))
                        assertTrue("getSeriesDetail should succeed", result.isSuccess)
                        val detail = result.getOrThrow()
                        // Verify correctness
                        assertTrue("Should have 3 seasons", detail.seasons.size == 3)
                        assertTrue("Should have episodes for 3 seasons", detail.episodes.size == 3)
                    }
                times.add(elapsed)
            }

            val avgMs = times.average()
            val sequentialBaseline = simulatedLatencyMs * 3 // 3 sequential calls

            println("BENCHMARK_RESULT: Simulated per-call latency: ${simulatedLatencyMs}ms")
            println("BENCHMARK_RESULT: Sequential baseline (3 calls): ${sequentialBaseline}ms")
            println("BENCHMARK_RESULT: Measured average: %.1fms".format(avgMs))
            println("BENCHMARK_RESULT: Individual runs: ${times.joinToString(", ")}ms")

            if (avgMs < sequentialBaseline * 0.7) {
                val speedup = sequentialBaseline.toDouble() / avgMs
                println("BENCHMARK_RESULT: Speedup: %.2fx (parallel execution confirmed)".format(speedup))
            } else {
                println("BENCHMARK_RESULT: Calls appear to be running sequentially (avg >= 70% of sequential baseline)")
            }
        }
}
