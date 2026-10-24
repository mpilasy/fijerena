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

/** A profile without Expert mode opens its own page with no PIN; any other page stays guarded. */
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
    fun `an Expert's own page and another profile's page stay guarded`() {
        val nav = navController()
        nav.navigate(Screen.ProfileEdit("atr"))

        assertFalse(nav.currentBackStackEntry.isOwnPageOfNonExpert("atr", expert = true))
        assertFalse(nav.currentBackStackEntry.isOwnPageOfNonExpert("kid", expert = false))
        assertFalse(null.isOwnPageOfNonExpert("kid", expert = false))
    }
}
