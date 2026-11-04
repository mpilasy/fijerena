package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.EpgSourceProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xtream.manager.AutoXmltvSources
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.UiText

/**
 * The Guide Sources page shows each guide source once, with the sources it is used for, then the
 * sources with no guide source; Add and Edit set the sources (the one guide source per URL plan,
 * Design U).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuideSourceCardsTest {
    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val dao = mockk<EpgSourceDao>(relaxed = true)
    private val providers = mockk<ProviderRepository>(relaxed = true)

    private val xtream = ProviderEntity(id = 1, name = "Xtream", url = "http://a", username = "u")
    private val jellyfin = ProviderEntity(id = 2, name = "Jellyfin", url = "http://j", username = "u", type = "JELLYFIN")
    private val m3u = ProviderEntity(id = 3, name = "M3U", url = "http://m", username = "", type = "REMOTE_M3U")
    private val second = ProviderEntity(id = 4, name = "Second", url = "http://b", username = "u")

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
        every { providers.getAllProviders() } returns flowOf(listOf(xtream, jellyfin, m3u, second))
        // The profile in use has only the Xtream source.
        every { providers.profileSources(any()) } returns flowOf(listOf(xtream))
        listOf(xtream, jellyfin, m3u, second).forEach { coEvery { providers.getProviderById(it.id) } returns it }
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun guide(
        id: Long,
        addedAtMs: Long = id,
    ) = EpgSourceEntity(id = id, url = "http://g/$id.xml", addedAtMs = addedAtMs)

    /** Xtream source 1's own guide (`<host> (Bulk)`). */
    private val ownGuide =
        EpgSourceEntity(id = 50, url = AutoXmltvSources.storedUrl(xtream.url), label = AutoXmltvSources.label(xtream.url))

    private fun given(
        guides: List<EpgSourceEntity>,
        vararg links: Pair<Long, Long>,
    ) {
        every { dao.getAllSources() } returns flowOf(guides)
        coEvery { dao.getAllSourcesOnce() } returns guides
        every { dao.getAllLinks() } returns flowOf(links.map { EpgSourceProviderEntity(it.first, it.second, 0) })
        links.groupBy({ it.first }) { it.second }.forEach { (id, ids) -> coEvery { dao.getProviderIds(id) } returns ids }
    }

    private fun page() = runBlocking { EpgManagementViewModel(context, providers).page.first() }

    @Test
    fun `a guide source several sources use is one card, with its sources in the Sources list's order`() {
        given(listOf(guide(10)), 10L to 3L, 10L to 1L)

        val card = page().cards.single()

        assertEquals(10L, card.source.id)
        assertEquals(listOf("Xtream", "M3U"), card.usedBy.map { it.name })
        assertNull(card.ownGuideOf)
        // The profile has one of its sources.
        assertEquals(true, card.usedByProfile)
    }

    @Test
    fun `cards follow their first source in the Sources list, then the order they were added`() {
        given(
            listOf(guide(30, addedAtMs = 1), guide(11, addedAtMs = 9), guide(10, addedAtMs = 5), guide(90)),
            30L to 3L,
            11L to 1L,
            10L to 1L,
            // A guide source of a deleted source (9): left out, Shrink Database deletes it.
            90L to 9L,
        )

        val cards = page().cards

        assertEquals(listOf(10L, 11L, 30L), cards.map { it.source.id })
        // The M3U source is not the profile's: its card says so.
        assertEquals(listOf(true, true, false), cards.map { it.usedByProfile })
    }

    @Test
    fun `a source's own guide names its source`() {
        given(listOf(ownGuide), 50L to 1L)

        assertEquals(xtream, page().cards.single().ownGuideOf)
    }

    @Test
    fun `the sources with channels and no guide source follow the cards`() {
        given(listOf(guide(10)), 10L to 1L)

        val page = page()

        // No Jellyfin (no channels).
        assertEquals(listOf(3L, 4L), page.sourcesWithoutGuide.map { it.id })
        assertEquals(listOf(1L, 3L, 4L), page.providers.map { it.id })
    }

    @Test
    fun `Add makes one guide source for the URL, read by each source picked`() {
        given(emptyList())
        val url = "http://g/new.xml"

        EpgManagementViewModel(context, providers).addSource(url, "New", 0, providerIds = listOf(1L, 3L))

        coVerify(timeout = TIMEOUT_MS, exactly = 1) { dao.insertGuide(match { it.url == url && it.label == "New" }, listOf(1L, 3L)) }
    }

    @Test
    fun `Add with the URL of a guide source links the sources to it and says so`() =
        runBlocking {
            given(listOf(guide(10).copy(url = "http://g/new.xml")), 10L to 1L)
            val viewModel = EpgManagementViewModel(context, providers)
            val next = async(start = CoroutineStart.UNDISPATCHED) { viewModel.userMessage.first() }

            // The same URL once normalised (OG-Q5), the scheme in capitals too.
            viewModel.addSource("HTTP://G:80/new.xml/", "New", 0, providerIds = listOf(1L, 3L))

            coVerify(timeout = TIMEOUT_MS) { dao.setLinks(10, listOf(1L, 3L)) }
            coVerify(exactly = 0) { dao.insertGuide(any(), any()) }
            val message = withTimeout(TIMEOUT_MS) { next.await() } as UiText.StringResource
            assertEquals(R.string.epg_source_already_added_for, message.resId)
            assertEquals(listOf<Any>("M3U"), message.args.toList())
        }

    @Test
    fun `Add with the URL of a source's own guide makes a guide source of its own`() {
        given(listOf(ownGuide), 50L to 1L)

        EpgManagementViewModel(context, providers).addSource(ownGuide.url, "", 0, providerIds = listOf(4L))

        coVerify(timeout = TIMEOUT_MS, exactly = 1) { dao.insertGuide(match { it.url == ownGuide.url }, listOf(4L)) }
        coVerify(exactly = 0) { dao.setLinks(any(), any()) }
    }

    @Test
    fun `Edit sets the sources of a hand-added guide source`() {
        val source = guide(10)
        given(listOf(source), 10L to 1L)
        coEvery { dao.getSourceById(10) } returns source

        EpgManagementViewModel(context, providers).updateSource(source.copy(label = "Renamed"), providerIds = setOf(3L, 4L))

        coVerify(timeout = TIMEOUT_MS) { dao.updateSource(match { it.id == 10L && it.label == "Renamed" }) }
        coVerify(timeout = TIMEOUT_MS) { dao.setLinks(10, setOf(3L, 4L)) }
    }

    @Test
    fun `Edit keeps a source's own guide on its one source`() {
        given(listOf(ownGuide), 50L to 1L)
        coEvery { dao.getSourceById(50) } returns ownGuide

        EpgManagementViewModel(context, providers).updateSource(ownGuide.copy(timezoneOffsetHours = 1), providerIds = setOf(3L))

        coVerify(timeout = TIMEOUT_MS) { dao.updateSource(match { it.id == 50L && it.timezoneOffsetHours == 1 }) }
        coVerify(exactly = 0) { dao.setLinks(any(), any()) }
    }

    @Test
    fun `Edit to the URL of another guide source is refused and says so`() =
        runBlocking {
            val other = guide(20).copy(url = "http://g/other.xml", label = "Other")
            val source = guide(10)
            given(listOf(source, other), 10L to 1L, 20L to 3L)
            coEvery { dao.getSourceById(10) } returns source
            val viewModel = EpgManagementViewModel(context, providers)
            val next = async(start = CoroutineStart.UNDISPATCHED) { viewModel.userMessage.first() }

            viewModel.updateSource(source.copy(url = "HTTP://G/other.xml/"), providerIds = setOf(1L))

            val message = withTimeout(TIMEOUT_MS) { next.await() } as UiText.StringResource
            assertEquals(R.string.epg_source_url_in_use, message.resId)
            assertEquals(listOf<Any>("Other"), message.args.toList())
            coVerify(exactly = 0) { dao.updateSource(any()) }
            coVerify(exactly = 0) { dao.setLinks(any(), any()) }
        }

    @Test
    fun `Delete deletes the guide source for every source`() {
        given(listOf(guide(10)), 10L to 1L, 10L to 3L)

        EpgManagementViewModel(context, providers).deleteSource(10)

        coVerify(timeout = TIMEOUT_MS) { dao.deleteGuide(10) }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
