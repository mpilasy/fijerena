package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeWatchStateDao
import org.njarasoa.fijerena.core.network.fixtures.WatchHistoryFixtures.episode
import org.njarasoa.fijerena.core.network.fixtures.WatchHistoryFixtures.movie
import org.njarasoa.fijerena.core.network.fixtures.toWatchStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.MediaType

/**
 * The history lists of a child profile on an Xtream source (Recent, Favourites, Continue watching)
 * keep only entries known to be in a category the child sees, or allowed by an exception. Grown-ups
 * and other sources keep the rule that hides only an excluded category.
 */
class MediaRepositoryKidsHistoryTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var watchStateDao: FakeWatchStateDao

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
        prefs = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns mockk(relaxed = true)
        every { prefs.getInt(any(), any()) } answers { secondArg() }
        every { prefs.getBoolean(any(), any()) } answers { secondArg() }
        every { prefs.getBoolean("child_profile_$KID", any()) } returns true
        // Thumbnails are looked up in the catalogue; there is none here.
        mockkObject(XtreamDatabase.Companion)
        every { XtreamDatabase.getInstance(any()) } returns mockk(relaxed = true)

        watchStateDao = FakeWatchStateDao()
    }

    @After
    fun tearDown() = unmockkAll()

    /** A catalogue with the kids' categories "kids" (films) and "toons" (series); "horror" is excluded. */
    private fun catalogue(
        seriesCategories: Map<Int, String> = emptyMap(),
        filmCategories: Map<Int, String> = emptyMap(),
        allowedSeries: List<String> = emptyList(),
    ): XtreamCategoryDao {
        val dao = mockk<XtreamCategoryDao>(relaxed = true)
        every { dao.getVisibleCategoryIds(P, "VOD") } returns listOf("kids")
        every { dao.getVisibleCategoryIds(P, "SERIES") } returns listOf("toons")
        every { dao.getExcludedCategoryIds(P, any()) } returns listOf("horror")
        every { dao.getAllowedItemIds(P, "SERIES") } returns allowedSeries
        every { dao.getSeriesCategoryIds(P, any()) } answers { seriesCategories.filterKeys { it in secondArg<List<Int>>() } }
        every { dao.getStreamCategoryIds(P, "VOD", any()) } answers { filmCategories.filterKeys { it in thirdArg<List<Int>>() } }
        return dao
    }

    private fun repository(
        categories: XtreamCategoryDao,
        profileId: String = KID,
        provider: MediaProvider? = mockk<XtreamMediaProvider>(relaxed = true),
    ): MediaRepository {
        val repo = MediaRepository(context, P, profileId, watchStateDao = watchStateDao, categoryDao = categories)
        provider?.let { repo.setProvider(it) }
        return repo
    }

    private fun show(
        id: String,
        name: String = "Show $id",
        categoryId: String = "",
    ) = MediaItem(id = id, name = name, mediaType = MediaType.SERIES, categoryId = categoryId)

    private fun MediaRepository.shows(vararg items: MediaItem): List<String> =
        runBlocking { withoutHiddenCategories(items.toList(), ContentType.TV_SHOWS) }.map { it.id }

    @Test
    fun `a kid sees nothing from a source whose catalogue never loaded`() {
        val empty = mockk<XtreamCategoryDao>(relaxed = true)
        assertEquals(emptyList<String>(), repository(empty).shows(show("1451", "From", "1451"), show("495")))
    }

    @Test
    fun `a blank category is looked up in the catalogue`() {
        val repo = repository(catalogue(seriesCategories = mapOf(495 to "toons", 488 to "horror")))
        // 495 is in a kids' category, 488 in an excluded one, 7 is not in the catalogue.
        assertEquals(listOf("495"), repo.shows(show("495"), show("488"), show("7")))
    }

    @Test
    fun `an exception shows a series of an excluded category`() {
        val repo = repository(catalogue(allowedSeries = listOf("9")))
        assertEquals(listOf("9"), repo.shows(show("9", "Naruto", "horror"), show("10", "Bleach", "horror")))
    }

    @Test
    fun `a hidden title stays out of a kids' category`() {
        val repo = repository(catalogue())
        assertEquals(listOf("1"), repo.shows(show("1", "Bluey", "toons"), show("2", "South Park", "toons")))
    }

    @Test
    fun `a grown-up keeps entries of an unknown or blank category`() {
        val repo = repository(catalogue(), profileId = GROWN_UP)
        assertEquals(
            listOf("1", "495", "3"),
            repo.shows(show("1", categoryId = "1451"), show("495"), show("2", categoryId = "horror"), show("3")),
        )
    }

    @Test
    fun `a kid on a source that is not Xtream keeps today's rule`() {
        val repo = repository(mockk(relaxed = true), provider = mockk<MediaProvider>(relaxed = true))
        assertEquals(listOf("1", "2"), repo.shows(show("1", categoryId = "unknown"), show("2")))
    }

    @Test
    fun `a kid's Continue watching resolves blank categories too`() {
        listOf(
            movie("11", position = 3_000_000, duration = 6_000_000).copy(categoryId = ""),
            movie("12", position = 3_000_000, duration = 6_000_000).copy(categoryId = ""),
            episode("e1", "495", position = 600_000, duration = 2_400_000).copy(categoryId = ""),
            episode("e2", "1451", position = 600_000, duration = 2_400_000).copy(categoryId = "1451"),
        ).forEachIndexed {
            index,
            item,
            ->
            watchStateDao.seed(item.toWatchStateEntity(providerId = P, at = 1_000_000L - index, profileId = KID))
        }
        val repo = repository(catalogue(seriesCategories = mapOf(495 to "toons"), filmCategories = mapOf(11 to "kids", 12 to "horror")))

        assertEquals(listOf("11", "495"), runBlocking { repo.getContinueWatchingItems() }.map { it.id })
    }

    private companion object {
        const val P = 1L
        const val KID = "kid"
        const val GROWN_UP = "grown-up"
    }
}
