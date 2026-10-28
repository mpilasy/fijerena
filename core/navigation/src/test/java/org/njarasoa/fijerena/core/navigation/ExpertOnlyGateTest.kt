package org.njarasoa.fijerena.core.navigation

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The household's shared setup closes at once for a profile without Expert mode (the profile rules
 * plan, PR-11); Settings and what only it opens close for a child profile (the admin settings plan).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExpertOnlyGateTest {
    private fun navController(): TestNavHostController {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph =
            nav.createGraph(startDestination = Screen.ContentTypeSelection) {
                composable<Screen.ContentTypeSelection> {}
                composable<Screen.EpgManagement> {}
                composable<Screen.ProviderSelection> {}
                composable<Screen.SyncSettings> {}
                composable<Screen.ParentalControls> {}
                composable<Screen.ProfileEdit> {}
                composable<Screen.AddProvider> {}
                composable<Screen.Settings> {}
                composable<Screen.DeviceInfo> {}
                composable<Screen.Licenses> {}
                composable<Screen.Terms> {}
                composable<Screen.Diagnostics> {}
            }
        return nav
    }

    private fun settingsArea(screen: Screen): Boolean {
        val nav = navController()
        nav.navigate(screen)
        return nav.currentBackStackEntry.isSettingsArea()
    }

    private fun expertOnly(screen: Screen): Boolean {
        val nav = navController()
        nav.navigate(screen)
        return nav.currentBackStackEntry.isExpertOnly()
    }

    @Test
    fun `sources, guide sources, live sync and Parental controls are an Expert's`() {
        assertTrue(expertOnly(Screen.EpgManagement()))
        assertTrue(expertOnly(Screen.EpgManagement(providerId = 3)))
        assertTrue(expertOnly(Screen.ProviderSelection))
        assertTrue(expertOnly(Screen.SyncSettings))
        assertTrue(expertOnly(Screen.ParentalControls))
        // Adding a source, and editing one, Jellyfin's sign-in included (PR-12).
        assertTrue(expertOnly(Screen.AddProvider()))
        assertTrue(expertOnly(Screen.AddProvider(editId = 4)))
    }

    @Test
    fun `a profile's own page and Home are not`() {
        assertFalse(expertOnly(Screen.ProfileEdit("kid")))
        assertFalse(expertOnly(Screen.ContentTypeSelection))
        assertFalse(null.isExpertOnly())
    }

    @Test
    fun `Settings and the screens only it opens are closed to a child, Home is not`() {
        assertTrue(settingsArea(Screen.Settings))
        assertTrue(settingsArea(Screen.ProfileEdit("kid")))
        assertTrue(settingsArea(Screen.DeviceInfo))
        assertTrue(settingsArea(Screen.Licenses))
        assertTrue(settingsArea(Screen.Terms))
        assertTrue(settingsArea(Screen.Diagnostics))
        assertFalse(settingsArea(Screen.ContentTypeSelection))
        assertFalse(null.isSettingsArea())
    }
}
