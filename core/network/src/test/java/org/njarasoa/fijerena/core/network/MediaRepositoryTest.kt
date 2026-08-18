package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.ContentType

class MediaRepositoryTest {
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var repository: MediaRepository

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val KEY_WATCH_HISTORY = "watch_history_v2"
    private val KEY_LAST_LIVE_CATEGORY = "last_live_category"
    private val KEY_LAST_LIVE_ITEM = "last_live_item"
    private val KEY_LAST_CONTENT_TYPE = "last_content_type"

    @Before
    fun setup() {
        clearAllMocks()
        mockkStatic(Looper::class)
        val mainLooper = mockk<Looper>(relaxed = true)
        every { Looper.getMainLooper() } returns mainLooper

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().postDelayed(any(), any()) } returns true
        every { anyConstructed<Handler>().removeCallbacks(any()) } returns Unit
        every { anyConstructed<Handler>().post(any()) } returns true

        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        
        // AppSettings might also need mocking if it's used in constructor
        every { context.getSharedPreferences("app_settings", any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun savePlaybackPosition_marksCompletedPastThreshold() {
        every { sharedPreferences.getString(KEY_WATCH_HISTORY, null) } returns null
        repository = MediaRepository(context, 1L)

        // Played to the end: finalizeSession reports position == duration for PlaybackState.Ended.
        repository.savePlaybackPosition("ep1", "Episode 1", "cat1", ContentType.TV_SHOWS, 2_500_000L, 2_500_000L)

        val saved = repository.getWatchHistory().single { it.itemId == "ep1" }
        assert(saved.isCompleted) { "an item watched to the end must be marked completed" }
        assert(saved.resumeProgress() == null) { "a completed item must not offer a resume point" }
    }

    @Test
    fun savePlaybackPosition_emptySessionDoesNotEraseExistingEntry() {
        val existing =
            listOf(
                WatchedItem(
                    itemId = "ep1",
                    itemName = "Episode 1",
                    categoryId = "cat1",
                    contentType = ContentType.TV_SHOWS,
                    playbackPosition = 2_400_000L,
                    duration = 2_500_000L,
                    isCompleted = true,
                ),
            )
        every { sharedPreferences.getString(KEY_WATCH_HISTORY, null) } returns json.encodeToString(existing)
        repository = MediaRepository(context, 1L)

        // Leaving while idle/buffering reports 0/0 — it must not overwrite what is already there.
        repository.savePlaybackPosition("ep1", "Episode 1", "cat1", ContentType.TV_SHOWS, 0L, 0L)

        val saved = repository.getWatchHistory().single { it.itemId == "ep1" }
        assert(saved.isCompleted) { "an empty write must not clear the completed mark" }
        assert(saved.playbackPosition == 2_400_000L) { "an empty write must not zero the position" }
    }

    @Test
    fun getWatchHistory_empty() {
        every { sharedPreferences.getString(KEY_WATCH_HISTORY, null) } returns null
        repository = MediaRepository(context, 1L)
        assert(repository.getWatchHistory().isEmpty())
    }

    @Test
    fun getWatchHistory_cachesResult() {
        val history = listOf(WatchedItem("1", "Test", "cat1", "LIVE_TV"))
        val historyJson = json.encodeToString(history)

        // Return JSON on first call
        every { sharedPreferences.getString(KEY_WATCH_HISTORY, null) } returns historyJson

        repository = MediaRepository(context, 1L)

        // First call should hit SharedPreferences
        val result1 = repository.getWatchHistory()
        assert(result1 == history)
        verify(exactly = 1) { sharedPreferences.getString(KEY_WATCH_HISTORY, null) }

        // Second call should hit memory cache
        val result2 = repository.getWatchHistory()
        assert(result2 == history)
        verify(exactly = 1) { sharedPreferences.getString(KEY_WATCH_HISTORY, null) }
    }

    @Test
    fun saveLastPlayedItem_updatesCache() {
        every { sharedPreferences.getString(KEY_WATCH_HISTORY, null) } returns null

        repository = MediaRepository(context, 1L)

        // Initially empty
        assert(repository.getWatchHistory().isEmpty())

        // Add item
        repository.saveLastPlayedItem("cat1", "1", "Test", ContentType.LIVE_TV)

        // Verify SharedPreferences updates for last played state
        verify { editor.putString(KEY_LAST_LIVE_CATEGORY, "cat1") }
        verify { editor.putString(KEY_LAST_LIVE_ITEM, "1") }
        verify { editor.putString(KEY_LAST_CONTENT_TYPE, ContentType.LIVE_TV) }

        // Verify cache updated without reading prefs again
        val history = repository.getWatchHistory()
        assert(history.size == 1)
        assert(history[0].itemId == "1")
        verify(exactly = 1) { sharedPreferences.getString(KEY_WATCH_HISTORY, null) } // Only initial check
    }

    @Test
    fun clearWatchHistory_clearsCache() {
        val history = listOf(WatchedItem("1", "Test", "cat1", "LIVE_TV"))
        val historyJson = json.encodeToString(history)
        every { sharedPreferences.getString(KEY_WATCH_HISTORY, null) } returns historyJson

        repository = MediaRepository(context, 1L)
        assert(repository.getWatchHistory().isNotEmpty())

        repository.clearWatchHistory()

        assert(repository.getWatchHistory().isEmpty())
        verify(exactly = 1) { sharedPreferences.getString(KEY_WATCH_HISTORY, null) } // Only initial check
    }
}
