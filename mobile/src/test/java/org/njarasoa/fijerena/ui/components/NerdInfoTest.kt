package org.njarasoa.fijerena.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.ErrorKind
import org.njarasoa.fijerena.core.network.StoredError
import org.njarasoa.fijerena.core.ui.components.SourceBadge
import org.njarasoa.fijerena.core.ui.history.LocalNerd
import org.njarasoa.fijerena.core.ui.history.ProvideSourceBadges
import org.njarasoa.fijerena.core.ui.utils.storedErrorText
import org.robolectric.RobolectricTestRunner

/**
 * The stats and extra information follow the Nerd flag, not the Admin flag
 * (docs/plans/20261103_nerd-role-sync-failures-plan.md, Lane G): the source badge and the raw
 * text of a kept error.
 */
@RunWith(RobolectricTestRunner::class)
class NerdInfoTest {
    @get:Rule
    val rule = createComposeRule()

    private val settings = AppSettings(ApplicationProvider.getApplicationContext())
    private val profile = "nerd-info-test"

    @After
    fun tearDown() {
        settings.setAdmin(profile, false)
        settings.setNerdMode(profile, false)
    }

    private fun profile(
        admin: Boolean,
        nerd: Boolean,
    ) {
        settings.activeProfileId = profile
        settings.setAdmin(profile, admin)
        settings.setNerdMode(profile, nerd)
    }

    @Test
    fun aNerdSeesTheSourceBadge() {
        profile(admin = false, nerd = true)
        rule.setContent { ProvideSourceBadges { SourceBadge(name = "Alpha") } }

        rule.onAllNodesWithContentDescription("Source: Alpha").assertCountEquals(1)
    }

    @Test
    fun anAdminWhoIsNotANerdSeesNoSourceBadge() {
        profile(admin = true, nerd = false)
        rule.setContent { ProvideSourceBadges { SourceBadge(name = "Alpha") } }

        rule.onAllNodesWithContentDescription("Source: Alpha").assertCountEquals(0)
    }

    private val stored = StoredError(ErrorKind.GENERIC, raw = "HTTP 500 from host").encode()

    @Test
    fun aNerdSeesTheRawTextOfAKeptError() {
        rule.setContent { CompositionLocalProvider(LocalNerd provides true) { Text(storedErrorText(stored)) } }

        rule.onNodeWithText("HTTP 500 from host", substring = true).assertExists()
    }

    @Test
    fun anotherProfileSeesOnlyTheFriendlyMessage() {
        rule.setContent { CompositionLocalProvider(LocalNerd provides false) { Text(storedErrorText(stored)) } }

        rule.onNodeWithText("Something went wrong", substring = true).assertExists()
        rule.onAllNodesWithText("HTTP 500 from host", substring = true).assertCountEquals(0)
    }
}
