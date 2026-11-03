package org.njarasoa.fijerena.core.navigation

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A profile that is not an Admin sees only its own page, which opens with no PIN; an Admin opens
 * any profile's page, guarded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnPageGateTest {
    private fun navController(): TestNavHostController {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph =
            nav.createGraph(startDestination = Screen.ContentTypeSelection) {
                composable<Screen.ContentTypeSelection> {}
                composable<Screen.ProfileEdit> {}
            }
        return nav
    }

    @Test
    fun `a profile that is not an Admin opens its own page freely`() {
        val nav = navController()
        nav.navigate(Screen.ProfileEdit("kid"))

        assertTrue(nav.currentBackStackEntry.isOwnPageOfNonAdmin("kid", admin = false))
    }

    @Test
    fun `a route to another profile shows a non-Admin its own page, freely`() {
        val nav = navController()
        nav.navigate(Screen.ProfileEdit("atr"))

        assertEquals("kid", Screen.ProfileEdit("atr").shownProfileId("kid", admin = false))
        assertTrue(nav.currentBackStackEntry.isOwnPageOfNonAdmin("kid", admin = false))
    }

    @Test
    fun `an Admin opens the page the route names, guarded`() {
        val nav = navController()
        nav.navigate(Screen.ProfileEdit("kid"))

        assertEquals("kid", Screen.ProfileEdit("kid").shownProfileId("atr", admin = true))
        assertFalse(nav.currentBackStackEntry.isOwnPageOfNonAdmin("atr", admin = true))
        assertFalse(nav.currentBackStackEntry.isOwnPageOfNonAdmin("kid", admin = true))
        assertFalse(null.isOwnPageOfNonAdmin("kid", admin = false))
    }
}
