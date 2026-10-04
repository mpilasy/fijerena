package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileEntity

/**
 * A synced setting this version can't use is dropped, not stored: a malformed refresh time used to
 * crash EPG management on every linked device. See
 * docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-09.
 */
class AppSettingsRemoteSettingTest {
    private lateinit var settings: AppSettings

    @Before
    fun setup() {
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences()
        settings = AppSettings(context)
    }

    private fun apply(
        key: String,
        value: JsonPrimitive,
    ) = settings.applyRemoteSetting(key, ProfileEntity.DEFAULT_ID, value)

    @Test
    fun `valid values are applied`() {
        apply("epg_refresh_time", JsonPrimitive("05:30"))
        apply("epg_refresh_interval", JsonPrimitive(12))
        apply("epg_refresh_interval", JsonPrimitive(-1))
        apply("theme_id", JsonPrimitive("midnight"))

        assertEquals("05:30", settings.epgRefreshTime)
        assertEquals(-1, settings.epgRefreshInterval)
        assertEquals("midnight", settings.themeId)
    }

    @Test
    fun `a malformed refresh time is dropped and the local one kept`() {
        apply("epg_refresh_time", JsonPrimitive("03:15"))

        listOf("4:00 AM", "25:00", "12:60", "noon", "", "12:30:00", "-1:30").forEach {
            apply("epg_refresh_time", JsonPrimitive(it))
        }
        apply("epg_refresh_time", JsonPrimitive(4))

        assertEquals("03:15", settings.epgRefreshTime)
    }

    @Test
    fun `an interval the app doesn't offer is dropped`() {
        listOf(0, -5, 3, 100_000).forEach { apply("epg_refresh_interval", JsonPrimitive(it)) }
        apply("epg_refresh_interval", JsonPrimitive("24h"))

        assertEquals(AppSettings.DEFAULT_EPG_REFRESH_INTERVAL, settings.epgRefreshInterval)
    }

    @Test
    fun `a blank theme is dropped`() {
        apply("theme_id", JsonPrimitive(" "))

        assertEquals("deep_night", settings.themeId)
    }

    @Test
    fun `refresh time parsing`() {
        assertEquals(0 to 0, AppSettings.parseRefreshTime("00:00"))
        assertEquals(23 to 59, AppSettings.parseRefreshTime("23:59"))
        assertEquals(4 to 0, AppSettings.parseRefreshTime("4:00"))
        assertNull(AppSettings.parseRefreshTime("4:00 AM"))
        assertNull(AppSettings.parseRefreshTime("24:00"))
    }
}
