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
 * A profile without Expert mode sees only its own page, which opens with no PIN; an Expert opens
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
    fun `a profile without Expert mode opens its own page freely`() {
        val nav = navController()
        nav.navigate(Screen.ProfileEdit("kid"))

        assertTrue(nav.currentBackStackEntry.isOwnPageOfNonExpert("kid", expert = false))
    }

    @Test
    fun `a route to another profile shows a non-Expert its own page, freely`() {
        val nav = navController()
        nav.navigate(Screen.ProfileEdit("atr"))

        assertEquals("kid", Screen.ProfileEdit("atr").shownProfileId("kid", expert = false))
        assertTrue(nav.currentBackStackEntry.isOwnPageOfNonExpert("kid", expert = false))
    }

    @Test
    fun `an Expert opens the page the route names, guarded`() {
        val nav = navController()
        nav.navigate(Screen.ProfileEdit("kid"))

        assertEquals("kid", Screen.ProfileEdit("kid").shownProfileId("atr", expert = true))
        assertFalse(nav.currentBackStackEntry.isOwnPageOfNonExpert("atr", expert = true))
        assertFalse(nav.currentBackStackEntry.isOwnPageOfNonExpert("kid", expert = true))
        assertFalse(null.isOwnPageOfNonExpert("kid", expert = false))
    }

    @Test
    fun `Your Profile opens freely, Admin's page of a profile does not`() {
        val nav = navController()
        nav.navigate(Screen.ProfileEdit("atr"))
        assertTrue(nav.currentBackStackEntry.isYourProfilePage())

        nav.navigate(Screen.ProfileEdit("kid", admin = true))
        assertFalse(nav.currentBackStackEntry.isYourProfilePage())
        assertFalse(null.isYourProfilePage())
    }
}
