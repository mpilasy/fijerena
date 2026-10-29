package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileEntity

/** The Trending rows' "show titles your sources don't have" switch: per profile, kept, synced. */
class AppSettingsChartsShowMissingTest {
    private val settings = freshSettings()

    @Test
    fun `off until turned on, and each profile has its own`() {
        assertFalse(settings.chartsShowMissing(ProfileEntity.DEFAULT_ID))
        settings.setChartsShowMissing(OTHER, true)
        assertTrue(settings.chartsShowMissing(OTHER))
        assertFalse(settings.chartsShowMissing(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `it syncs to the same profile on another device`() {
        assertTrue(KEY in AppSettings.SYNCED_SETTING_KEYS)
        assertTrue(KEY in AppSettings.PER_PROFILE_SETTING_KEYS)
        settings.setChartsShowMissing(OTHER, true)
        val sent = settings.syncedSetting(KEY, OTHER)!!
        assertEquals(JsonPrimitive(true), sent)
        assertNull(settings.syncedSetting(KEY, ProfileEntity.DEFAULT_ID))

        val otherDevice = freshSettings()
        otherDevice.applyRemoteSetting(KEY, OTHER, sent)
        assertTrue(otherDevice.chartsShowMissing(OTHER))
        assertFalse(otherDevice.chartsShowMissing(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `a deleted profile's choice goes`() {
        settings.setChartsShowMissing(OTHER, true)
        settings.removeChartsShowMissing(OTHER)
        assertFalse(settings.chartsShowMissing(OTHER))
    }

    private fun freshSettings(): AppSettings {
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences()
        return AppSettings(context)
    }

    private companion object {
        const val KEY = AppSettings.KEY_CHARTS_SHOW_MISSING
        const val OTHER = "5d0e8c1a-other-profile"
    }
}
