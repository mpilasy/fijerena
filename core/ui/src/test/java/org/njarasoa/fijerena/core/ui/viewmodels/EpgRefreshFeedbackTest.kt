package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.queue.RefreshQueue
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.UiText

/**
 * A guide source a refresh was asked for shows as waiting, and its Refresh then only says it is
 * refreshing already: it never launches the refresh a second time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpgRefreshFeedbackTest {
    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val dao = mockk<EpgSourceDao>(relaxed = true)
    private val providers = mockk<ProviderRepository>(relaxed = true)
    private val fileManager = mockk<EpgFileManager>(relaxed = true)
    private val queued = MutableStateFlow<Set<String>>(emptySet())
    private val active = MutableStateFlow<Set<String>>(emptySet())
    private val source = EpgSourceEntity(id = 10, url = "http://g/10.xml")

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        mockkObject(EpgFileManager.Companion, EpgIndexer.Companion, SettingsDatabase.Companion, EpgIndexDatabase.Companion, RefreshQueue)
        every { EpgFileManager.getInstance(any()) } returns fileManager
        every { EpgIndexer.getInstance(any()) } returns mockk(relaxed = true)
        val settingsDb = mockk<SettingsDatabase>(relaxed = true)
        every { settingsDb.epgSourceDao() } returns dao
        every { SettingsDatabase.getInstance(any()) } returns settingsDb
        every { EpgIndexDatabase.getInstance(any()) } returns mockk(relaxed = true)
        every { RefreshQueue.queuedTaskIds } returns queued
        every { RefreshQueue.activeTaskIds } returns active
        every { providers.getAllProviders() } returns flowOf(emptyList())
        every { providers.profileSources(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun viewModel() = EpgManagementViewModel(context, providers)

    @Test
    fun `a second Refresh on a guide source asked for already says so and launches nothing`() =
        runBlocking {
            val viewModel = viewModel()
            val busy = launch { viewModel.busySourceIds.collect {} }
            viewModel.refreshSource(source)
            val next = async(start = CoroutineStart.UNDISPATCHED) { viewModel.userMessage.first() }

            viewModel.refreshSource(source)

            assertEquals(R.string.epg_refresh_in_queue, (withTimeout(5_000) { next.await() } as UiText.StringResource).resId)
            verify(exactly = 1) { fileManager.launchProcessSingleSource(10, any(), any()) }
            assertTrue(10L in viewModel.busySourceIds.value)
            busy.cancel()
        }

    @Test
    fun `a one-source refresh in the queue shows its guide source as waiting, also on a page opened again`() =
        runBlocking {
            active.value = setOf("epg_refresh_source_10")
            val viewModel = viewModel()
            val busy = launch { viewModel.busySourceIds.collect {} }

            assertEquals(setOf(10L), viewModel.busySourceIds.first { it.isNotEmpty() })
            viewModel.refreshSource(source)
            verify(exactly = 0) { fileManager.launchProcessSingleSource(any(), any(), any()) }
            busy.cancel()
        }

    @Test
    fun `a task seen in the queue and gone from it stops its guide sources waiting`() =
        runBlocking {
            val viewModel = viewModel()
            val busy = launch { viewModel.busySourceIds.collect {} }
            viewModel.refreshSource(source)
            active.value = setOf("epg_refresh_source_10")
            active.value = emptySet()

            assertTrue(viewModel.busySourceIds.first { it.isEmpty() }.isEmpty())
            busy.cancel()
        }

    @Test
    fun `only a one-source task names its guide source`() {
        assertEquals(10L, EpgManagementViewModel.singleSourceIdOf("epg_refresh_source_10"))
        assertNull(EpgManagementViewModel.singleSourceIdOf("epg_refresh_stale_1_2"))
    }
}
