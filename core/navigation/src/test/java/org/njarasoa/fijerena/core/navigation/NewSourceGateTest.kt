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
 * The nav gate refuses Add Source for a new source to a profile without Expert mode; the edit mode
 * (the Browse sign-in) and other screens are not refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewSourceGateTest {
    private fun navController(): TestNavHostController {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph =
            nav.createGraph(startDestination = Screen.ContentTypeSelection) {
                composable<Screen.ContentTypeSelection> {}
                composable<Screen.AddProvider> {}
            }
        return nav
    }

    @Test
    fun `Add Source without an edit id is a new source`() {
        val nav = navController()
        nav.navigate(Screen.AddProvider())

        assertTrue(nav.currentBackStackEntry.isNewSource())
    }

    @Test
    fun `the edit mode stays open for the Browse sign-in`() {
        val nav = navController()
        nav.navigate(Screen.AddProvider(editId = 3L))

        assertFalse(nav.currentBackStackEntry.isNewSource())
    }

    @Test
    fun `other screens are not a new source`() {
        val nav = navController()

        assertFalse(nav.currentBackStackEntry.isNewSource())
        assertFalse(null.isNewSource())
    }
}
