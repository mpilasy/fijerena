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
 * The household's shared setup closes at once for a profile that is not an Admin (the profile rules
 * plan, PR-11); Settings and what only it opens close for a child profile (the admin settings plan).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AdminOnlyGateTest {
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
                composable<Screen.About> {}
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

    private fun adminOnly(screen: Screen): Boolean {
        val nav = navController()
        nav.navigate(screen)
        return nav.currentBackStackEntry.isAdminOnly()
    }

    @Test
    fun `sources, guide sources, live sync and Parental controls are an Admin's`() {
        assertTrue(adminOnly(Screen.EpgManagement()))
        assertTrue(adminOnly(Screen.EpgManagement(providerId = 3)))
        assertTrue(adminOnly(Screen.ProviderSelection))
        assertTrue(adminOnly(Screen.SyncSettings))
        assertTrue(adminOnly(Screen.ParentalControls))
        // Adding a source, and editing one, Jellyfin's sign-in included (PR-12).
        assertTrue(adminOnly(Screen.AddProvider()))
        assertTrue(adminOnly(Screen.AddProvider(editId = 4)))
    }

    @Test
    fun `a profile's own page and Home are not`() {
        assertFalse(adminOnly(Screen.ProfileEdit("kid")))
        assertFalse(adminOnly(Screen.ContentTypeSelection))
        assertFalse(null.isAdminOnly())
    }

    @Test
    fun `Settings and the screens only it opens are closed to a child, Home is not`() {
        assertTrue(settingsArea(Screen.Settings))
        assertTrue(settingsArea(Screen.ProfileEdit("kid")))
        assertTrue(settingsArea(Screen.DeviceInfo))
        assertTrue(settingsArea(Screen.About))
        assertTrue(settingsArea(Screen.Licenses))
        assertTrue(settingsArea(Screen.Terms))
        assertTrue(settingsArea(Screen.Diagnostics))
        assertFalse(settingsArea(Screen.ContentTypeSelection))
        assertFalse(null.isSettingsArea())
    }
}
