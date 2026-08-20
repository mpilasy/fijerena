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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.EpisodeId
import org.njarasoa.fijerena.core.player.domain.SeriesId

/**
 * Ordering, filtering and dedup rules of the merged "Recent" list — the single row that
 * replaced the separate Continue Watching and Last Watched categories.
 */
class MediaRepositoryRecentItemsTest {
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val keyWatchHistory = "watch_history_v2"

    @Before
    fun setup() {
        clearAllMocks()
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().postDelayed(any(), any()) } returns true
        every { anyConstructed<Handler>().removeCallbacks(any()) } returns Unit
        every { anyConstructed<Handler>().post(any()) } returns true

        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** Watch history is stored newest-first, so list entries in that order. */
    private fun repositoryWith(vararg history: WatchedItem): MediaRepository {
        every { sharedPreferences.getString(keyWatchHistory, null) } returns json.encodeToString(history.toList())
        return MediaRepository(context, 1L)
    }

    private fun movie(
        id: String,
        position: Long = 0L,
        duration: Long = 0L,
        isCompleted: Boolean = false,
    ) = WatchedItem(
        itemId = id,
        itemName = id,
        categoryId = "cat1",
        contentType = ContentType.MOVIES,
        playbackPosition = position,
        duration = duration,
        isCompleted = isCompleted,
    )

    private fun episode(
        id: String,
        seriesId: String?,
        position: Long = 0L,
        duration: Long = 0L,
        isCompleted: Boolean = false,
    ) = WatchedItem(
        itemId = id,
        itemName = id,
        categoryId = "cat1",
        contentType = ContentType.TV_SHOWS,
        playbackPosition = position,
        duration = duration,
        isCompleted = isCompleted,
        episodeId = EpisodeId(id),
        seriesId = seriesId?.let(::SeriesId),
        seriesName = seriesId?.let { "Series $it" },
    )

    private fun channel(id: String) =
        WatchedItem(
            itemId = id,
            itemName = id,
            categoryId = "cat1",
            contentType = ContentType.LIVE_TV,
        )

    @Test
    fun ordersInProgressBeforeRest() {
        val repository =
            repositoryWith(
                movie("finished", position = 100L, duration = 100L, isCompleted = true),
                movie("half", position = 50L, duration = 100L),
                movie("untouched"),
                movie("started", position = 10L, duration = 100L),
            )

        assertEquals(
            listOf("half", "started", "finished", "untouched"),
            repository.getRecentItems(ContentType.MOVIES).map { it.id },
        )
    }

    @Test
    fun liveTvIsPureRecency() {
        val repository = repositoryWith(channel("bbc"), channel("cnn"), channel("arte"))

        assertEquals(
            listOf("bbc", "cnn", "arte"),
            repository.getRecentItems(ContentType.LIVE_TV).map { it.id },
        )
    }

    @Test
    fun tvShowsCollapseToOneCardPerSeries() {
        val repository =
            repositoryWith(
                episode("s1e3", seriesId = "s1", position = 30L, duration = 100L),
                episode("s1e2", seriesId = "s1", position = 100L, duration = 100L, isCompleted = true),
                episode("s1e1", seriesId = "s1", position = 100L, duration = 100L, isCompleted = true),
                episode("s2e1", seriesId = "s2", position = 100L, duration = 100L, isCompleted = true),
            )

        val recent = repository.getRecentItems(ContentType.TV_SHOWS)

        assertEquals(listOf("s1", "s2"), recent.map { it.id })
        assertEquals(BrowseTarget.Series(SeriesId("s1"), resumeEpisodeId = EpisodeId("s1e3")), recent.first().target)
    }

    @Test
    fun tvShowsSeriesCardCarriesResumeMetadata() {
        val repository = repositoryWith(episode("s1e3", seriesId = "s1", position = 30L, duration = 100L))

        val card = repository.getRecentItems(ContentType.TV_SHOWS).single()

        assertEquals("s1", card.id)
        assertEquals("Series s1", card.name)
        assertEquals(BrowseTarget.Series(SeriesId("s1"), resumeEpisodeId = EpisodeId("s1e3")), card.target)
    }

    @Test
    fun legacyEpisodeWithoutSeriesIdStaysAnEpisodeCard() {
        val repository = repositoryWith(episode("orphan", seriesId = null, position = 30L, duration = 100L))

        val card = repository.getRecentItems(ContentType.TV_SHOWS).single()

        assertEquals("orphan", card.id)
        assertEquals(BrowseTarget.Episode(episodeId = EpisodeId("orphan")), card.target)
    }

    @Test
    fun legacyEpisodeWithoutEpisodeIdStillPlaysAsAnEpisode() {
        // Entries written before episode/series ids were recorded: itemId is the episode's stream
        // id, so the card must route to the player, not to a series the provider has no such id for.
        val repository =
            repositoryWith(
                WatchedItem(
                    itemId = "242136",
                    itemName = "EN - Law & Order - S06E18",
                    categoryId = "cat1",
                    contentType = ContentType.TV_SHOWS,
                    playbackPosition = 30L,
                    duration = 100L,
                ),
            )

        val card = repository.getRecentItems(ContentType.TV_SHOWS).single()

        assertEquals("242136", card.id)
        assertEquals(BrowseTarget.Episode(episodeId = EpisodeId("242136")), card.target)
    }

    @Test
    fun respectsResumeBandBoundaries() {
        val repository =
            repositoryWith(
                movie("barelyStarted", position = 1L, duration = 100L),
                movie("almostDone", position = 96L, duration = 100L),
                movie("lowerBound", position = 2L, duration = 100L),
                movie("upperBound", position = 95L, duration = 100L),
            )

        assertEquals(
            listOf("lowerBound", "upperBound", "barelyStarted", "almostDone"),
            repository.getRecentItems(ContentType.MOVIES).map { it.id },
        )
    }

    @Test
    fun excludesOtherContentTypes() {
        val repository =
            repositoryWith(
                movie("film", position = 50L, duration = 100L),
                episode("s1e1", seriesId = "s1"),
                channel("bbc"),
            )

        assertEquals(listOf("film"), repository.getRecentItems(ContentType.MOVIES).map { it.id })
        assertEquals(listOf("s1"), repository.getRecentItems(ContentType.TV_SHOWS).map { it.id })
        assertEquals(listOf("bbc"), repository.getRecentItems(ContentType.LIVE_TV).map { it.id })
    }
}
