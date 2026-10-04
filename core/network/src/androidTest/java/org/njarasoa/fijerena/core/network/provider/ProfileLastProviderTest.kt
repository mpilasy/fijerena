package org.njarasoa.fijerena.core.network.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfileRepository

/**
 * Each profile returns to the provider it last picked — see
 * docs/plans/archive/20261002_profile-last-provider-plan.md.
 *
 * Runs against this test APK's own databases and prefs, never the app's. Uses profiles it creates
 * itself: another test in this APK deletes `default`.
 */
@RunWith(AndroidJUnit4::class)
class ProfileLastProviderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun eachProfileReturnsToItsPick_andNoPickStaysPut() =
        runBlocking {
            val appSettings = AppSettings(context)
            val providers = ProviderRepository(context)
            val a = ProfileRepository(context).addProfile("A", 1)
            val b = ProfileRepository(context).addProfile("B", 2)
            val fresh = ProfileRepository(context).addProfile("Fresh", 3)

            appSettings.activeProfileId = a
            val x = providers.addProvider("x", "http://x.test", "u", "p", "XTREAM")
            val y = providers.addProvider("y", "http://y.test", "u", "p", "XTREAM", activate = false)
            providers.pickProvider(x)

            appSettings.activeProfileId = b
            providers.pickProvider(y)

            // Switch to A: back on X.
            appSettings.activeProfileId = a
            assertTrue(providers.activateLastProvider(a))
            assertEquals(x, providers.getActiveProvider()?.id)
            // Already there: nothing to do.
            assertFalse(providers.activateLastProvider(a))

            // Switch to B: back on Y.
            appSettings.activeProfileId = b
            assertTrue(providers.activateLastProvider(b))
            assertEquals(y, providers.getActiveProvider()?.id)

            // A profile that never picked stays on the current provider.
            appSettings.activeProfileId = fresh
            assertFalse(providers.activateLastProvider(fresh))
            assertEquals(y, providers.getActiveProvider()?.id)
        }

    @Test
    fun automaticChangesAreNotRemembered_andAMissingProviderStaysPut() =
        runBlocking {
            val appSettings = AppSettings(context)
            val providers = ProviderRepository(context)
            val a = ProfileRepository(context).addProfile("A2", 1)
            appSettings.activeProfileId = a
            val x = providers.addProvider("x2", "http://x2.test", "u", "p", "XTREAM")
            val y = providers.addProvider("y2", "http://y2.test", "u", "p", "XTREAM", activate = false)
            val xKey = providers.getProviderById(x)!!.providerKey

            // Adding a provider (activated) counts as the profile's pick.
            assertEquals(xKey, appSettings.lastProviderKey(a))

            // setActiveProvider is the automatic path: not remembered.
            providers.setActiveProvider(y)
            assertEquals(xKey, appSettings.lastProviderKey(a))

            // The remembered provider is gone: stay on the current one.
            providers.deleteProvider(x)
            assertFalse(providers.activateLastProvider(a))
            assertEquals(y, providers.getActiveProvider()?.id)
        }
}
