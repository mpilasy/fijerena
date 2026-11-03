package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.robolectric.RobolectricTestRunner

/**
 * Settings → Profiles (FEATURES → Profiles): for an Admin every profile and Add profile; without
 * it, no profile row (Settings shows the profile in use's own rows in place), and Add profile for a
 * grown-up only.
 */
@RunWith(RobolectricTestRunner::class)
class ProfilesSettingsRowsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val profiles =
        listOf(
            ProfileUi(id = "main", name = "Main", colorIndex = 0, isActive = true),
            ProfileUi(id = "kid", name = "Kid", colorIndex = 1, isActive = false),
        )

    private fun show(
        adminMode: Boolean,
        canAddProfile: Boolean = adminMode,
    ) {
        composeTestRule.setContent {
            ProfilesSettingsRows(
                profiles = profiles,
                message = null,
                newProfileColorIndex = { 2 },
                onAdd = { _, _, _, _ -> },
                onEdit = {},
                onDismissMessage = {},
                adminMode = adminMode,
                canAddProfile = canAddProfile,
            )
        }
    }

    @Test
    fun `for an Admin every profile and Add profile show`() {
        show(adminMode = true)

        composeTestRule.onNodeWithText("Main").assertExists()
        composeTestRule.onNodeWithText("Kid").assertExists()
        composeTestRule.onNodeWithContentDescription("Add Profile").assertExists()
    }

    @Test
    fun `for a profile that is not an Admin no profile row shows`() {
        show(adminMode = false)

        composeTestRule.onNodeWithText("Kid").assertDoesNotExist()
        composeTestRule.onNodeWithText("Main").assertDoesNotExist()
    }

    @Test
    fun `a grown-up who is not an Admin gets Add profile`() {
        show(adminMode = false, canAddProfile = true)

        composeTestRule.onNodeWithContentDescription("Add Profile").assertExists()
    }

    @Test
    fun `a kid gets no Add profile`() {
        show(adminMode = false, canAddProfile = false)

        composeTestRule.onNodeWithContentDescription("Add Profile").assertDoesNotExist()
    }

    @Test
    fun `an Admin's Add Profile has the Nerd switch, gone when Child is on`() {
        show(adminMode = true)
        composeTestRule.onNodeWithContentDescription("Add Profile").performClick()

        composeTestRule.onNodeWithText("Nerd").assertExists()
        composeTestRule.onNodeWithText("Child Profile").performClick()
        composeTestRule.onNodeWithText("Nerd").assertDoesNotExist()
    }

    @Test
    fun `a grown-up's Add Profile has no Nerd switch`() {
        show(adminMode = false, canAddProfile = true)
        composeTestRule.onNodeWithContentDescription("Add Profile").performClick()

        composeTestRule.onNodeWithText("Child Profile").assertExists()
        composeTestRule.onNodeWithText("Nerd").assertDoesNotExist()
    }
}
