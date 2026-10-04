package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity.Companion.REFRESH_OFF
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.UiText
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel.Companion.REFRESH_INTERVAL_CHOICES
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel.Companion.refreshIntervalLabel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel.Companion.refreshIntervalOptions
import org.njarasoa.fijerena.core.ui.viewmodels.EpgManagementViewModel.Companion.refreshIntervalSummary

/**
 * Each guide source's auto-refresh, set from its row: the picker's options (an interval copied
 * from the retired device-wide setting stays on offer) and the write. See
 * docs/plans/20261003_sources-guide-profiles-plan.md → P5b.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpgManagementRefreshIntervalTest {
    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val fileManager = mockk<EpgFileManager>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        mockkObject(EpgFileManager.Companion, EpgIndexer.Companion, SettingsDatabase.Companion, EpgIndexDatabase.Companion)
        every { EpgFileManager.getInstance(any()) } returns fileManager
        every { EpgIndexer.getInstance(any()) } returns mockk(relaxed = true)
        every { SettingsDatabase.getInstance(any()) } returns mockk(relaxed = true)
        every { EpgIndexDatabase.getInstance(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun source(hours: Int?) = EpgSourceEntity(id = 7, url = "u", providerId = 1, refreshIntervalHours = hours)

    @Test
    fun `a choice offers the five choices, off first`() {
        assertEquals(listOf(REFRESH_OFF, 6, 12, 24, 168), REFRESH_INTERVAL_CHOICES)
        REFRESH_INTERVAL_CHOICES.forEach { assertEquals(REFRESH_INTERVAL_CHOICES, refreshIntervalOptions(it)) }
    }

    @Test
    fun `an interval that isn't a choice is offered in its place too`() {
        assertEquals(listOf(REFRESH_OFF, 4, 6, 12, 24, 168), refreshIntervalOptions(4))
        assertEquals(listOf(REFRESH_OFF, 6, 8, 12, 24, 168), refreshIntervalOptions(8))
        assertEquals(listOf(REFRESH_OFF, 6, 12, 24, 48, 168), refreshIntervalOptions(48))
    }

    @Test
    fun `labels and row text`() {
        assertText(R.string.common_off, refreshIntervalLabel(REFRESH_OFF))
        assertText(R.string.epg_automation_freq_hours, refreshIntervalLabel(4), 4)
        assertText(R.string.epg_automation_freq_daily, refreshIntervalLabel(24))
        assertText(R.string.epg_automation_freq_weekly, refreshIntervalLabel(168))
        assertText(R.string.epg_source_refresh_off, refreshIntervalSummary(REFRESH_OFF))
        assertText(R.string.epg_source_refresh_hours, refreshIntervalSummary(48), 48)
        assertText(R.string.epg_source_refresh_daily, refreshIntervalSummary(24))
        assertText(R.string.epg_source_refresh_weekly, refreshIntervalSummary(168))
    }

    @Test
    fun `a source's own interval wins, one without uses the retired device-wide interval`() {
        every { prefs.getBoolean("epg_auto_refresh", any()) } returns true
        every { prefs.getInt("epg_refresh_interval", any()) } returns 8
        val viewModel = EpgManagementViewModel(context, providerId = 1)

        assertEquals(12, viewModel.refreshIntervalHours(source(12)))
        assertEquals(8, viewModel.refreshIntervalHours(source(null)))
        assertEquals(listOf(REFRESH_OFF, 6, 8, 12, 24, 168), refreshIntervalOptions(viewModel.refreshIntervalHours(source(null))))
    }

    @Test
    fun `picking an interval writes it for that source`() {
        val viewModel = EpgManagementViewModel(context, providerId = 1)

        viewModel.setRefreshInterval(7, 168)
        viewModel.setRefreshInterval(7, REFRESH_OFF)

        coVerify(timeout = 5_000) { fileManager.setRefreshInterval(7, 168) }
        coVerify(timeout = 5_000) { fileManager.setRefreshInterval(7, REFRESH_OFF) }
    }

    private fun assertText(
        resId: Int,
        text: UiText,
        vararg args: Any,
    ) {
        val resource = text as UiText.StringResource
        assertEquals(resId, resource.resId)
        assertEquals(args.toList(), resource.args.toList())
    }
}
