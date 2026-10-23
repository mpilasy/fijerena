package org.njarasoa.fijerena.core.network.wanted

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.sync.SettingsSyncQueue
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendationsResponse
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Search's "Not in your sources" section keeps the films and shows TMDB finds that no source has,
 * drops people and the titles a source has (by id, or by the original title), and never calls TMDB
 * for a child profile or a short query. See the wanted titles plan, Phase 3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TmdbTitleSearchTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tmdb = mockk<TmdbApiService>()
    private lateinit var db: XtreamDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(context, XtreamDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        mockkObject(XtreamDatabase.Companion, SettingsSyncQueue)
        every { XtreamDatabase.getInstance(any()) } returns db
        every { SettingsSyncQueue.setting(any(), any(), any()) } returns Unit
        mockkConstructor(ProviderRepository::class)
        coEvery { anyConstructed<ProviderRepository>().profileSourcesList(any()) } returns
            listOf(ProviderEntity(id = A, name = "A", url = "http://a.test", username = "u", type = "XTREAM", providerKey = "a"))
        db.categoryDao().insertAll(listOf(XtreamCategoryEntity("films", A, "Films", type = XtreamStreamEntity.TYPE_VOD)))
        db.streamDao().insertAll(
            listOf(
                XtreamStreamEntity(1, A, XtreamStreamEntity.TYPE_VOD, 1, "EN - Dune", "movie", categoryId = "films", tmdbId = "438631"),
                XtreamStreamEntity(
                    2,
                    A,
                    XtreamStreamEntity.TYPE_VOD,
                    2,
                    "FR - Intouchables",
                    "movie",
                    categoryId = "films",
                    releaseDate = "2011",
                ),
            ),
        )
        every { tmdb.hasApiKey() } returns true
        coEvery { tmdb.searchMulti(any()) } returns
            TmdbRecommendationsResponse(
                listOf(
                    result(438631, "movie", "Dune", year = "2021"),
                    result(77338, "movie", "The Intouchables", original = "Intouchables", year = "2011"),
                    result(693134, "movie", "Dune: Part Two", year = "2024"),
                    result(1, "person", "Denis Villeneuve"),
                    result(90228, "tv", "Dune: Prophecy", year = "2024"),
                ),
            )
        AppSettings(context).setChildProfile(AppSettings(context).activeProfileId, false)
    }

    @After
    fun tearDown() {
        unmockkAll()
        db.close()
    }

    private fun search() = TmdbTitleSearch(context, tmdb)

    @Test
    fun `keeps only the films and shows no source has, in TMDB's order`() =
        runBlocking {
            val titles = search().search("dune", setOf(ContentType.MOVIES, ContentType.TV_SHOWS))

            assertEquals(listOf("693134", "90228"), titles.map { it.tmdbId })
        }

    @Test
    fun `keeps only the content types asked for`() =
        runBlocking {
            assertEquals(listOf("90228"), search().search("dune", setOf(ContentType.TV_SHOWS)).map { it.tmdbId })
        }

    @Test
    fun `a child profile never calls TMDB`() =
        runBlocking {
            AppSettings(context).setChildProfile(AppSettings(context).activeProfileId, true)

            assertEquals(emptyList<TmdbTitle>(), search().search("dune", setOf(ContentType.MOVIES)))
            coVerify(exactly = 0) { tmdb.searchMulti(any()) }
        }

    @Test
    fun `a short query or no key never calls TMDB`() =
        runBlocking {
            search().search("du", setOf(ContentType.MOVIES))
            every { tmdb.hasApiKey() } returns false
            search().search("dune", setOf(ContentType.MOVIES))

            coVerify(exactly = 0) { tmdb.searchMulti(any()) }
        }

    private fun result(
        id: Int,
        mediaType: String,
        title: String,
        original: String? = null,
        year: String? = null,
    ) = TmdbRecommendation(
        id = id,
        title = title.takeIf { mediaType == "movie" },
        name = title.takeIf { mediaType != "movie" },
        originalTitle = original,
        releaseDate = year?.let { "$it-01-01" }.takeIf { mediaType == "movie" },
        firstAirDate = year?.let { "$it-01-01" }.takeIf { mediaType == "tv" },
        mediaType = mediaType,
    )

    private companion object {
        const val A = 1L
    }
}
