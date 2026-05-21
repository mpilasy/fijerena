package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.PlaybackStatus
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import kotlin.system.measureTimeMillis

class PlaybackPositionBenchmarkTest {
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var repository: MediaRepository
    private lateinit var provider: MediaProvider

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        val mainLooper = mockk<Looper>(relaxed = true)
        every { Looper.getMainLooper() } returns mainLooper

        io.mockk.mockkConstructor(android.os.Handler::class)
        every { anyConstructed<android.os.Handler>().postDelayed(any(), any()) } returns true
        every { anyConstructed<android.os.Handler>().removeCallbacks(any()) } returns Unit

        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        provider = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences

        // Setup provider with server user data capability
        every { provider.capabilities } returns ProviderCapabilities(
            supportedContentTypes = setOf(ContentType.TV_SHOWS),
            supportsServerUserData = true,
            supportsEpg = false,
            supportsSearch = false,
            supportsAuthentication = false,
            supportsProgressSync = false
        )

        repository = MediaRepository(context, 1L)
        repository.setProvider(provider)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun benchmarkSequentialGetPlaybackPosition() = runBlocking {
        val episodeCount = 50
        val episodeIds = (1..episodeCount).map { "ep_$it" }

        // Simulate network latency
        coEvery { provider.getPlaybackPosition(any()) } coAnswers {
            kotlinx.coroutines.delay(10) // 10ms latency per request
            PlaybackStatus(1000L, 2000L, false)
        }

        val time = measureTimeMillis {
            for (id in episodeIds) {
                repository.getPlaybackPositionSuspend(id, ContentType.TV_SHOWS)
            }
        }

        println("BENCHMARK_RESULT: Sequential time for $episodeCount episodes: $time ms")
    }

    @Test
    fun benchmarkBulkGetPlaybackPosition() = runBlocking {
        val episodeCount = 50
        val episodeIds = (1..episodeCount).map { "ep_$it" }

        // Simulate network latency for bulk call
        coEvery { provider.getPlaybackPositions(any()) } coAnswers {
            kotlinx.coroutines.delay(10) // 10ms latency total for the bulk request
            kotlin.Result.success(episodeIds.associateWith { PlaybackStatus(1000L, 2000L, false) })
        }

        val time = measureTimeMillis {
            repository.getPlaybackPositionsSuspend(episodeIds, ContentType.TV_SHOWS)
        }

        println("BENCHMARK_RESULT: Bulk time for $episodeCount episodes: $time ms")
    }
}
