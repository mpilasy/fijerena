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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xtream.manager.AutoXmltvSources

/**
 * The card's on/off switch is for this device only: the on/off state does not sync. A source's own
 * guide is turned off by itself too, not through "Provides a guide", which syncs with the source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpgManagementSwitchTest {
    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val dao = mockk<EpgSourceDao>(relaxed = true)
    private val repository = mockk<ProviderRepository>(relaxed = true)
    private val owner = ProviderEntity(id = 4, name = "IPTV", url = "http://iptv.test", username = "me", type = "XTREAM")
    private val guide = EpgSourceEntity(id = 9, url = "http://iptv.test/xmltv.php", label = "iptv.test (Bulk)", enabled = true)

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        mockkObject(EpgFileManager.Companion, EpgIndexer.Companion, SettingsDatabase.Companion, EpgIndexDatabase.Companion)
        mockkObject(AutoXmltvSources)
        every { EpgFileManager.getInstance(any()) } returns mockk(relaxed = true)
        every { EpgIndexer.getInstance(any()) } returns mockk(relaxed = true)
        every { SettingsDatabase.getInstance(any()) } returns mockk(relaxed = true) { every { epgSourceDao() } returns dao }
        every { EpgIndexDatabase.getInstance(any()) } returns mockk(relaxed = true)
        coEvery { AutoXmltvSources.reconcileStored(any(), any(), any()) } returns Unit
        coEvery { dao.getSourceById(9) } returns guide
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun card(ownGuideOf: ProviderEntity?) = EpgManagementViewModel.GuideCard(guide, listOf(owner), ownGuideOf, usedByProfile = true)

    @Test
    fun `turning a source's own guide off changes only this device's row`() {
        EpgManagementViewModel(context, repository).setSourceEnabled(card(ownGuideOf = owner), enabled = false)

        coVerify(timeout = 5_000) { dao.updateSource(guide.copy(enabled = false)) }
        coVerify(exactly = 0) { repository.setProvidesGuide(any(), any(), any()) }
        coVerify(exactly = 0) { AutoXmltvSources.reconcileStored(any(), any(), any()) }
    }

    @Test
    fun `turning it on sets Provides a guide only when that is off`() {
        coEvery { repository.getProviderSettings(4) } returns ProviderSettings(providesGuide = false)
        EpgManagementViewModel(context, repository).setSourceEnabled(card(ownGuideOf = owner), enabled = true)
        coVerify(timeout = 5_000) { AutoXmltvSources.reconcileStored(any(), 4, any()) }
        coVerify { repository.setProvidesGuide(4, true, byUser = true) }

        coEvery { repository.getProviderSettings(4) } returns ProviderSettings(providesGuide = null)
        EpgManagementViewModel(context, repository).setSourceEnabled(card(ownGuideOf = owner), enabled = true)
        coVerify(timeout = 5_000, exactly = 2) { AutoXmltvSources.reconcileStored(any(), 4, any()) }
        coVerify(exactly = 1) { repository.setProvidesGuide(any(), any(), any()) }
    }

    @Test
    fun `any other guide source is turned on or off by itself`() {
        EpgManagementViewModel(context, repository).setSourceEnabled(card(ownGuideOf = null), enabled = false)

        coVerify(timeout = 5_000) { dao.updateSource(guide.copy(enabled = false)) }
    }
}
