package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamSeriesDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamDao
import kotlin.system.measureNanoTime

class XtreamRepositoryBenchmarkTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var accountManager: AccountManager
    private lateinit var database: XtreamDatabase
    private lateinit var categoryDao: XtreamCategoryDao
    private lateinit var streamDao: XtreamStreamDao
    private lateinit var seriesDao: XtreamSeriesDao
    private lateinit var repository: XtreamRepository

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val KEY_WATCH_HISTORY = "watch_history"

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        accountManager = mockk(relaxed = true)
        database = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        streamDao = mockk(relaxed = true)
        seriesDao = mockk(relaxed = true)

        // Mock XtreamDatabase.getInstance
        mockkObject(XtreamDatabase.Companion)
        every { XtreamDatabase.getInstance(any()) } returns database
        every { database.categoryDao() } returns categoryDao
        every { database.streamDao() } returns streamDao
        every { database.seriesDao() } returns seriesDao

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
            WatchedStream(
                streamId = it,
                streamName = "Stream $it",
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

        repository = XtreamRepository(accountManager, context)

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

    @Test
    fun verifyCacheUpdates() {
        every { sharedPreferences.getString(KEY_WATCH_HISTORY, null) } returns null
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { sharedPreferences.edit() } returns editor

        repository = XtreamRepository(accountManager, context)

        // Initial state
        val initial = repository.getWatchHistory()
        assert(initial.isEmpty())

        // Add item
        val streamId = 123
        val streamName = "Test Stream"

        repository.saveLastPlayedStream("cat1", streamId, streamName, "LIVE_TV")

        // Verify cache is updated immediately without reading prefs
        val updated = repository.getWatchHistory()
        assert(updated.size == 1)
        assert(updated[0].streamId == streamId)
        assert(updated[0].streamName == streamName)
    }
}
