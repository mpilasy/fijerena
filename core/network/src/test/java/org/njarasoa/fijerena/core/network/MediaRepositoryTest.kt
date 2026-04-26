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
import io.mockk.mockkStatic
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
        mockkStatic(android.os.Looper::class)
        val mainLooper = mockk<android.os.Looper>(relaxed = true)
        every { android.os.Looper.getMainLooper() } returns mainLooper

        io.mockk.mockkConstructor(android.os.Handler::class)
        every { anyConstructed<android.os.Handler>().postDelayed(any(), any()) } returns true
        every { anyConstructed<android.os.Handler>().removeCallbacks(any()) } returns Unit

        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        clearAllMocks()

        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)

        mockkStatic(Handler::class)
        val mockHandler = mockk<Handler>(relaxed = true)
        every { mockHandler.removeCallbacks(any()) } returns Unit
        every { mockHandler.postDelayed(any(), any()) } returns true

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().postDelayed(any(), any()) } returns true
        every { anyConstructed<Handler>().removeCallbacks(any()) } returns Unit
        every { anyConstructed<Handler>().post(any()) } returns true

        io.mockk.mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)
        io.mockk.mockkConstructor(android.os.Handler::class)
        every { anyConstructed<android.os.Handler>().removeCallbacks(any()) } returns Unit
        every { anyConstructed<android.os.Handler>().postDelayed(any(), any()) } returns true

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor // Int is likely used in other places or mocked generally
        every { editor.remove(any()) } returns editor
    }

    @After
    fun tearDown() {
        unmockkAll()
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
