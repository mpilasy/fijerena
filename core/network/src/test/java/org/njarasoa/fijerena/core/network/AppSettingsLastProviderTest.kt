package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileEntity

/** Each profile remembers its last picked provider — see docs/plans/20261002_profile-last-provider-plan.md. */
class AppSettingsLastProviderTest {
    private lateinit var settings: AppSettings

    @Before
    fun setup() {
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences()
        settings = AppSettings(context)
    }

    @Test
    fun `each profile has its own provider`() {
        settings.setLastProviderKey(ProfileEntity.DEFAULT_ID, "provider-x")
        settings.setLastProviderKey(OTHER, "provider-y")

        assertEquals("provider-x", settings.lastProviderKey(ProfileEntity.DEFAULT_ID))
        assertEquals("provider-y", settings.lastProviderKey(OTHER))
    }

    @Test
    fun `a profile that never picked has none`() {
        assertNull(settings.lastProviderKey(OTHER))
        assertNull(settings.syncedSetting("last_provider", OTHER))
    }

    @Test
    fun `it syncs per profile`() {
        assertTrue("last_provider" in AppSettings.SYNCED_SETTING_KEYS)
        assertTrue("last_provider" in AppSettings.PER_PROFILE_SETTING_KEYS)
        settings.setLastProviderKey(OTHER, "provider-y")

        assertEquals(JsonPrimitive("provider-y"), settings.syncedSetting("last_provider", OTHER))
        assertNull(settings.syncedSetting("last_provider", ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `a received value is stored for its profile, a non-string one ignored`() {
        settings.applyRemoteSetting("last_provider", OTHER, JsonPrimitive("provider-z"))
        settings.applyRemoteSetting("last_provider", ProfileEntity.DEFAULT_ID, JsonPrimitive(42))

        assertEquals("provider-z", settings.lastProviderKey(OTHER))
        assertNull(settings.lastProviderKey(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `removing a profile's provider forgets it`() {
        settings.setLastProviderKey(OTHER, "provider-y")

        settings.removeLastProvider(OTHER)

        assertNull(settings.lastProviderKey(OTHER))
    }

    private companion object {
        const val OTHER = "5d0e8c1a-other-profile"
    }
}
