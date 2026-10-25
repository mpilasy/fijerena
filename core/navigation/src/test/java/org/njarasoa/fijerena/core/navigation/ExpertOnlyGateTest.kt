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

/** The household's shared setup closes at once for a profile without Expert mode (the profile rules plan, PR-11). */
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
            }
        return nav
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
}
