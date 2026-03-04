package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import io.mockk.mockkStatic
import android.os.Looper
import kotlin.system.measureNanoTime

class MediaRepositoryBenchmarkTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var repository: MediaRepository

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val KEY_WATCH_HISTORY = "watch_history_v2"

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)

        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)

        // Mock SharedPreferences
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun benchmarkGetWatchHistory() {
        // Create a large watch history
        val historySize = 25 // Default size
        val history = (1..historySize).map {
            WatchedItem(
                itemId = "item_$it",
                itemName = "Item $it",
                categoryId = "Category $it",
                contentType = "LIVE_TV",
                timestamp = System.currentTimeMillis(),
                playbackPosition = 1000L * it,
                duration = 2000L * it,
                isCompleted = false
            )
        }
        val historyJson = json.encodeToString(history)

        every { sharedPreferences.getString(KEY_WATCH_HISTORY, null) } returns historyJson

        repository = MediaRepository(context, 1L)

        // Warm up
        repeat(100) {
            repository.getWatchHistory()
        }

        // Measure
        val iterations = 50000
        val time = measureNanoTime {
            repeat(iterations) {
                repository.getWatchHistory()
            }
        }

        println("BENCHMARK_RESULT: Time for $iterations iterations: ${time / 1_000_000} ms")
        println("BENCHMARK_RESULT: Average time per call: ${time.toDouble() / iterations} ns")
    }
}
