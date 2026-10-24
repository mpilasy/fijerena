package org.njarasoa.fijerena.testing

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Rule
import org.njarasoa.fijerena.feature.settings.SettingsScreen

/**
 * The frame of a TV Settings focus test: the whole [SettingsScreen] (or a screen it opens) on
 * [SettingsFixture]'s data, and a [FocusWalk] on it. The subclass runs on Robolectric.
 */
abstract class SettingsFocusTest {
    // A standard (queued) dispatcher, as on a device: with the default unconfined one a
    // LaunchedEffect starts before the rows below it have registered, and the first focus misses.
    @get:Rule
    val rule = createComposeRule(StandardTestDispatcher())

    protected val walk = FocusWalk(rule)

    @After
    fun resetFixture() = SettingsFixture.reset()

    /** Opens Settings with [active] in use; the profiles in [pins] have a PIN. */
    protected fun open(
        active: String,
        pins: Set<String> = emptySet(),
    ) {
        seed(active, pins)
        show { SettingsScreen(onBack = {}, onProviderChanged = {}) }
    }

    /** Writes the fixture's data, with [active] in use; the profiles in [pins] have a PIN. */
    protected fun seed(
        active: String,
        pins: Set<String> = emptySet(),
    ) = SettingsFixture.seed(context, active, pins)

    /** Shows [screen]: Settings, or a screen Settings opens. */
    protected fun show(screen: @Composable () -> Unit) {
        rule.setContent(screen)
        rule.waitForIdle()
    }

    protected val context: Context get() = ApplicationProvider.getApplicationContext()
}
