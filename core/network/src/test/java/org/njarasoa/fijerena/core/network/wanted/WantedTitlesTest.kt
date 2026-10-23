package org.njarasoa.fijerena.core.network.wanted

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.coEvery
import io.mockk.every
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
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.WantedTitleEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [WantedTitles] works out, for the profile in use, its Wanted list, which titles its Xtream
 * sources have, and its gone favourites; a child profile gets nothing. See
 * the wanted titles plan, Phase 2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WantedTitlesTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: XtreamDatabase

    // Its own instance per test: Robolectric gives each test a new context, and the singleton keeps the first.
    private val titles by lazy { WantedTitles(context) }

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
            listOf(
                ProviderEntity(id = A, name = "A", url = "http://a.test", username = "u", type = "XTREAM", providerKey = "a"),
                ProviderEntity(id = M3U, name = "M", url = "http://m.test", username = "", type = "REMOTE_M3U", providerKey = "m"),
            )
        db.categoryDao().insertAll(listOf(XtreamCategoryEntity("films", A, "Films", type = XtreamStreamEntity.TYPE_VOD)))
        db.streamDao().insertAll(
            listOf(XtreamStreamEntity(1, A, XtreamStreamEntity.TYPE_VOD, 1, "EN - Dune", "movie", categoryId = "films", tmdbId = "438631")),
        )
        AppSettings(context).activeProfileId = PROFILE
        AppSettings(context).setChildProfile(PROFILE, false)
    }

    @After
    fun tearDown() {
        unmockkAll()
        db.close()
    }

    @Test
    fun `works out the list, what is available and the gone favourites`() =
        runBlocking {
            db.wantedTitleDao().upsert(wanted("438631", "Dune"))
            db.wantedTitleDao().upsert(wanted("1", "Not Anywhere"))
            db.favoriteStateDao().upsert(FavoriteStateEntity(A, PROFILE, "99", ContentType.MOVIES, FavoriteKind.STREAM, "Gone", "films", 1))

            titles.refresh()

            val state = titles.state.value
            assertEquals(2, state.titles.size)
            assertEquals(setOf(WantedTitles.Key(ContentType.MOVIES, "438631")), state.available.keys)
            assertEquals(listOf("99"), state.goneFavorites.map { it.itemId })
        }

    @Test
    fun `a child profile gets nothing`() =
        runBlocking {
            db.wantedTitleDao().upsert(wanted("438631", "Dune"))
            AppSettings(context).setChildProfile(PROFILE, true)

            titles.refresh()

            assertEquals(WantedTitles.State(), titles.state.value)
        }

    @Test
    fun `removing a title records its removal and updates the state`() =
        runBlocking {
            titles.add(wanted("438631", "Dune"))

            titles.remove(WantedTitles.Key(ContentType.MOVIES, "438631"))

            assertEquals(emptyList<WantedTitleEntity>(), titles.state.value.titles)
        }

    private fun wanted(
        tmdbId: String,
        title: String,
    ) = WantedTitleEntity(PROFILE, ContentType.MOVIES, tmdbId, title, addedAt = 1)

    private companion object {
        const val A = 1L
        const val M3U = 2L
        const val PROFILE = "p"
    }
}
