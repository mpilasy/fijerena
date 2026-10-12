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

/**
 * Each profile's sources: per profile, synced, seeded once on upgrade. See
 * docs/plans/20261012_multi-source-search-history-plan.md → Phase 2.
 */
class AppSettingsProfileSourcesTest {
    private lateinit var settings: AppSettings
    private val prefs = FakeSharedPreferences()

    @Before
    fun setup() {
        settings = newSettings(prefs)
    }

    private fun newSettings(prefs: FakeSharedPreferences = FakeSharedPreferences()): AppSettings {
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_settings", any()) } returns prefs
        return AppSettings(context)
    }

    /** The source [profileId] last picked, as a build from before Phase 6 stored it. */
    private fun legacyLastPick(
        profileId: String,
        providerKey: String,
    ) {
        prefs.edit().putString("last_provider_$profileId", providerKey).commit()
    }

    @Test
    fun `each profile has its own sources, none stored until set`() {
        assertNull(settings.profileSourceKeys(OTHER))

        settings.setProfileSourceKeys(ProfileEntity.DEFAULT_ID, setOf("a", "b"))
        settings.setProfileSourceKeys(OTHER, setOf("c"))

        assertEquals(setOf("a", "b"), settings.profileSourceKeys(ProfileEntity.DEFAULT_ID))
        assertEquals(setOf("c"), settings.profileSourceKeys(OTHER))
    }

    @Test
    fun `adding a source keeps the ones the profile has`() {
        settings.setProfileSourceKeys(OTHER, setOf("a"))

        settings.addProfileSource(OTHER, "b")
        settings.addProfileSource(ProfileEntity.DEFAULT_ID, "c")

        assertEquals(setOf("a", "b"), settings.profileSourceKeys(OTHER))
        assertEquals(setOf("c"), settings.profileSourceKeys(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `it syncs per profile, and what is sent is what another device stores`() {
        assertTrue("profile_sources" in AppSettings.SYNCED_SETTING_KEYS)
        assertTrue("profile_sources" in AppSettings.PER_PROFILE_SETTING_KEYS)
        settings.setProfileSourceKeys(OTHER, setOf("b", "a"))

        val sent = settings.syncedSetting("profile_sources", OTHER)!!
        assertTrue(sent.isString)
        assertNull(settings.syncedSetting("profile_sources", ProfileEntity.DEFAULT_ID))

        val otherDevice = newSettings()
        otherDevice.applyRemoteSetting("profile_sources", OTHER, sent)
        assertEquals(setOf("a", "b"), otherDevice.profileSourceKeys(OTHER))
        assertEquals(sent, otherDevice.syncedSetting("profile_sources", OTHER))
    }

    @Test
    fun `a received value that isn't a list of keys is dropped, keeping the local one`() {
        settings.setProfileSourceKeys(OTHER, setOf("a"))

        settings.applyRemoteSetting("profile_sources", OTHER, JsonPrimitive(42))
        settings.applyRemoteSetting("profile_sources", OTHER, JsonPrimitive("not json"))
        settings.applyRemoteSetting("profile_sources", OTHER, JsonPrimitive("{\"a\":1}"))

        assertEquals(setOf("a"), settings.profileSourceKeys(OTHER))
    }

    @Test
    fun `removing a profile's sources forgets them`() {
        settings.setProfileSourceKeys(OTHER, setOf("a"))

        settings.removeProfileSources(OTHER)

        assertNull(settings.profileSourceKeys(OTHER))
    }

    @Test
    fun `upgrade gives each profile its last-picked source, else the device's current one`() {
        legacyLastPick(ProfileEntity.DEFAULT_ID, "a")
        legacyLastPick(OTHER, "gone")

        settings.seedProfileSourcesOnce(listOf(ProfileEntity.DEFAULT_ID, OTHER, NEVER_PICKED), setOf("a", "b"), activeKey = "b")

        assertEquals(setOf("a"), settings.profileSourceKeys(ProfileEntity.DEFAULT_ID))
        // Its last pick no longer exists here: the device's current source, as for one that never picked.
        assertEquals(setOf("b"), settings.profileSourceKeys(OTHER))
        assertEquals(setOf("b"), settings.profileSourceKeys(NEVER_PICKED))
    }

    @Test
    fun `upgrade with no source on the device stores nothing`() {
        settings.seedProfileSourcesOnce(listOf(ProfileEntity.DEFAULT_ID), emptySet(), activeKey = null)

        assertNull(settings.profileSourceKeys(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `upgrade runs once`() {
        settings.seedProfileSourcesOnce(listOf(ProfileEntity.DEFAULT_ID), setOf("a", "b"), activeKey = "a")
        settings.setProfileSourceKeys(ProfileEntity.DEFAULT_ID, setOf("a", "b"))
        settings.removeProfileSources(OTHER)

        settings.seedProfileSourcesOnce(listOf(ProfileEntity.DEFAULT_ID, OTHER), setOf("a", "b"), activeKey = "b")

        assertEquals(setOf("a", "b"), settings.profileSourceKeys(ProfileEntity.DEFAULT_ID))
        assertNull(settings.profileSourceKeys(OTHER))
    }

    @Test
    fun `upgrade keeps sources received from another device before it ran`() {
        settings.applyRemoteSetting("profile_sources", OTHER, JsonPrimitive("[\"x\",\"y\"]"))
        legacyLastPick(OTHER, "a")

        settings.seedProfileSourcesOnce(listOf(ProfileEntity.DEFAULT_ID, OTHER), setOf("a"), activeKey = "a")

        assertEquals(setOf("x", "y"), settings.profileSourceKeys(OTHER))
        assertEquals(setOf("a"), settings.profileSourceKeys(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `the retired last pick is no longer synced, and one received from an older build is ignored`() {
        legacyLastPick(OTHER, "a")
        settings.applyRemoteSetting("last_provider", ProfileEntity.DEFAULT_ID, JsonPrimitive("b"))

        assertTrue("last_provider" !in AppSettings.SYNCED_SETTING_KEYS)
        assertNull(settings.syncedSetting("last_provider", OTHER))
        settings.seedProfileSourcesOnce(listOf(ProfileEntity.DEFAULT_ID, OTHER), setOf("a", "b", "c"), activeKey = "c")
        // The received pick didn't land: Default gets the device's source, as one that never picked.
        assertEquals(setOf("c"), settings.profileSourceKeys(ProfileEntity.DEFAULT_ID))
        assertEquals(setOf("a"), settings.profileSourceKeys(OTHER))
    }

    private companion object {
        const val OTHER = "5d0e8c1a-other-profile"
        const val NEVER_PICKED = "9f1b2c3d-never-picked"
    }
}
