package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer

/** One Guide Sources page for every source: its groups and Add (the one guide sources page plan). */
@OptIn(ExperimentalCoroutinesApi::class)
class GuideSourceGroupsTest {
    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val dao = mockk<EpgSourceDao>(relaxed = true)
    private val providers = mockk<ProviderRepository>(relaxed = true)

    private val xtream = ProviderEntity(id = 1, name = "Xtream", url = "http://a", username = "u")
    private val jellyfin = ProviderEntity(id = 2, name = "Jellyfin", url = "http://j", username = "u", type = "JELLYFIN")
    private val m3u = ProviderEntity(id = 3, name = "M3U", url = "http://m", username = "", type = "REMOTE_M3U")

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        mockkObject(EpgFileManager.Companion, EpgIndexer.Companion, SettingsDatabase.Companion, EpgIndexDatabase.Companion)
        every { EpgFileManager.getInstance(any()) } returns mockk(relaxed = true)
        every { EpgIndexer.getInstance(any()) } returns mockk(relaxed = true)
        val settingsDb = mockk<SettingsDatabase>(relaxed = true)
        every { settingsDb.epgSourceDao() } returns dao
        every { SettingsDatabase.getInstance(any()) } returns settingsDb
        every { EpgIndexDatabase.getInstance(any()) } returns mockk(relaxed = true)
        every { providers.getAllProviders() } returns flowOf(listOf(xtream, jellyfin, m3u))
        // The profile in use has only the Xtream source.
        every { providers.profileSources(any()) } returns flowOf(listOf(xtream))
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun guide(
        id: Long,
        providerId: Long,
    ) = EpgSourceEntity(id = id, url = "http://g/$id.xml", providerId = providerId)

    @Test
    fun `a group for each source with channels, every source on the device`() =
        runBlocking {
            every { dao.getAllSources() } returns flowOf(listOf(guide(10, 1), guide(30, 3), guide(31, 3), guide(90, 9)))

            val groups = EpgManagementViewModel(context, providers).groups.first()

            // No Jellyfin group (no channels), none for a guide source of a deleted source (9).
            assertEquals(listOf(1L, 3L), groups.map { it.provider.id })
            assertEquals(listOf(listOf(10L), listOf(30L, 31L)), groups.map { group -> group.sources.map { it.id } })
            // The M3U source is not the profile's, and still shows.
            assertEquals(listOf(true, false), groups.map { it.usedByProfile })
        }

    @Test
    fun `a source with no guide source still has its group`() =
        runBlocking {
            every { dao.getAllSources() } returns flowOf(listOf(guide(10, 1)))

            val groups = EpgManagementViewModel(context, providers).groups.first()

            assertEquals(emptyList<EpgSourceEntity>(), groups.single { it.provider.id == 3L }.sources)
        }

    @Test
    fun `Add adds the guide source to each source picked, skipping one that has it`() {
        val url = "http://g/new.xml"
        coEvery { dao.getSourceByUrl(url, 1) } returns guide(10, 1).copy(url = url)
        coEvery { dao.getSourceByUrl(url, 3) } returns null

        EpgManagementViewModel(context, providers).addSource(url, "New", 0, providerIds = listOf(1L, 3L))

        coVerify(
            timeout = TIMEOUT_MS,
            exactly = 1,
        ) { dao.insertSource(match { it.url == url && it.providerId == 3L && it.label == "New" }) }
        coVerify(exactly = 0) { dao.insertSource(match { it.providerId == 1L }) }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
