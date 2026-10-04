package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileEntity

/**
 * A synced setting this version can't use is dropped, not stored: a malformed refresh time used to
 * crash EPG management on every linked device. See
 * docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-09. The guide
 * auto-refresh keys an older version still sends are retired (each guide source has its own
 * interval, docs/plans/20261003_sources-guide-profiles-plan.md → P5b) and ignored.
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
        apply("theme_id", JsonPrimitive("midnight"))

        assertEquals("midnight", settings.themeId)
    }

    @Test
    fun `the retired guide auto-refresh keys are neither synced nor applied`() {
        apply("epg_auto_refresh", JsonPrimitive(false))
        apply("epg_refresh_interval", JsonPrimitive(12))
        apply("epg_refresh_time", JsonPrimitive("05:30"))

        assertTrue(settings.epgAutoRefreshEnabled)
        assertEquals(AppSettings.DEFAULT_EPG_REFRESH_INTERVAL, settings.epgRefreshInterval)
        listOf("epg_auto_refresh", "epg_refresh_interval", "epg_refresh_time").forEach {
            assertFalse(it, it in AppSettings.SYNCED_SETTING_KEYS)
        }
    }

    @Test
    fun `a blank theme is dropped`() {
        apply("theme_id", JsonPrimitive(" "))

        assertEquals("deep_night", settings.themeId)
    }
}
