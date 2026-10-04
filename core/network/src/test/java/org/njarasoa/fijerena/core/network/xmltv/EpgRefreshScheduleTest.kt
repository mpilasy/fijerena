package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity.Companion.REFRESH_OFF

/** Each guide source refreshes by its own interval — see docs/plans/archive/20261003_sources-guide-profiles-plan.md → P5. */
class EpgRefreshScheduleTest {
    private val hour = 3_600_000L
    private val now = 1_000_000 * hour

    private fun source(
        intervalHours: Int?,
        ingestedHoursAgo: Long?,
        enabled: Boolean = true,
    ) = EpgSourceEntity(
        url = "http://epg.test/$intervalHours-$ingestedHoursAgo",
        providerId = 1,
        enabled = enabled,
        lastIngestedAtMs = ingestedHoursAgo?.let { now - it * hour } ?: 0L,
        refreshIntervalHours = intervalHours,
    )

    @Test
    fun `a scheduled run picks each source by its own interval, stale after half of it`() {
        val sixHoursFourAgo = source(6, 4)
        val dailyFourAgo = source(24, 4)
        val dailyThirteenAgo = source(24, 13)
        val weeklyHundredAgo = source(168, 100)
        val offLongAgo = source(REFRESH_OFF, 1_000)
        val offNeverIngested = source(REFRESH_OFF, null)
        val unsetSevenAgo = source(null, 7)
        val sources = listOf(sixHoursFourAgo, dailyFourAgo, dailyThirteenAgo, weeklyHundredAgo, offLongAgo, offNeverIngested, unsetSevenAgo)

        val due = sources.filter { EpgRefreshSchedule.isDue(it, now, unsetHours = 12) }

        assertEquals(listOf(sixHoursFourAgo, dailyThirteenAgo, weeklyHundredAgo, offNeverIngested, unsetSevenAgo), due)
    }

    @Test
    fun `a source without its own interval follows the old device-wide one, off included`() {
        val unset = source(null, 30)

        assertTrue(EpgRefreshSchedule.isDue(unset, now, unsetHours = 24))
        assertFalse(EpgRefreshSchedule.isDue(unset, now, unsetHours = REFRESH_OFF))
        assertEquals(48, EpgRefreshSchedule.intervalHours(unset, unsetHours = 48))
    }

    @Test
    fun `refreshing stale sources by hand counts an off source as stale after a day`() {
        assertTrue(EpgRefreshSchedule.isStale(source(REFRESH_OFF, 25), now, unsetHours = 24))
        assertFalse(EpgRefreshSchedule.isStale(source(REFRESH_OFF, 23), now, unsetHours = 24))
        assertTrue(EpgRefreshSchedule.isStale(source(12, 7), now, unsetHours = 24))
        assertFalse(EpgRefreshSchedule.isStale(source(12, 5), now, unsetHours = 24))
    }

    @Test
    fun `the periodic work runs at the shortest interval among enabled sources, none when all are off`() {
        val sources =
            listOf(source(24, 1), source(6, 1, enabled = false), source(12, 1), source(REFRESH_OFF, 1), source(null, 1))

        assertEquals(12, EpgRefreshSchedule.workIntervalHours(sources, unsetHours = 24))
        assertEquals(8, EpgRefreshSchedule.workIntervalHours(sources, unsetHours = 8))
        assertNull(EpgRefreshSchedule.workIntervalHours(listOf(source(REFRESH_OFF, 1), source(6, 1, enabled = false)), unsetHours = 24))
        assertNull(EpgRefreshSchedule.workIntervalHours(listOf(source(null, 1)), unsetHours = REFRESH_OFF))
        assertNull(EpgRefreshSchedule.workIntervalHours(emptyList(), unsetHours = 24))
    }

    @Test
    fun `the old device-wide settings map to one interval, off when switched off or never`() {
        assertEquals(12, EpgRefreshSchedule.legacyIntervalHours(autoRefreshEnabled = true, intervalHours = 12))
        assertEquals(REFRESH_OFF, EpgRefreshSchedule.legacyIntervalHours(autoRefreshEnabled = false, intervalHours = 12))
        assertEquals(REFRESH_OFF, EpgRefreshSchedule.legacyIntervalHours(autoRefreshEnabled = true, intervalHours = -1))
    }

    private fun settings(
        autoRefresh: Boolean?,
        interval: Int?,
    ): AppSettings {
        val prefs = FakeSharedPreferences()
        prefs
            .edit()
            .apply {
                autoRefresh?.let { putBoolean("epg_auto_refresh", it) }
                interval?.let { putInt("epg_refresh_interval", it) }
            }.commit()
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_settings", any()) } returns prefs
        return AppSettings(context)
    }

    @Test
    fun `the startup copy hands over the old interval once`() =
        runBlocking {
            val settings = settings(autoRefresh = true, interval = 8)
            val copied = mutableListOf<Int>()

            EpgRefreshSchedule.copyLegacyIntervalOnce(settings) { copied += it }
            EpgRefreshSchedule.copyLegacyIntervalOnce(settings) { copied += it }

            assertEquals(listOf(8), copied)
            assertTrue(settings.epgRefreshIntervalCopied)
        }

    @Test
    fun `the startup copy hands over off when auto-refresh was switched off, and the default on a fresh install`() =
        runBlocking {
            val copied = mutableListOf<Int>()

            EpgRefreshSchedule.copyLegacyIntervalOnce(settings(autoRefresh = false, interval = 24)) { copied += it }
            EpgRefreshSchedule.copyLegacyIntervalOnce(settings(autoRefresh = null, interval = null)) { copied += it }

            assertEquals(listOf(REFRESH_OFF, 24), copied)
        }

    @Test
    fun `a failed copy is tried again on the next start`() =
        runBlocking {
            val settings = settings(autoRefresh = true, interval = 12)

            try {
                EpgRefreshSchedule.copyLegacyIntervalOnce(settings) { error("disk full") }
            } catch (e: IllegalStateException) {
                // expected
            }

            assertFalse(settings.epgRefreshIntervalCopied)
        }
}
