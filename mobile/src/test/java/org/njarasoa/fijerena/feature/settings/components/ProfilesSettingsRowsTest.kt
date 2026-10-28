package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.robolectric.RobolectricTestRunner

/**
 * Settings → Profiles (FEATURES → Profiles): with Expert mode every profile and Add profile; without
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
        expertMode: Boolean,
        canAddProfile: Boolean = expertMode,
    ) {
        composeTestRule.setContent {
            ProfilesSettingsRows(
                profiles = profiles,
                message = null,
                newProfileColorIndex = { 2 },
                onAdd = { _, _, _ -> },
                onEdit = {},
                onDismissMessage = {},
                expertMode = expertMode,
                canAddProfile = canAddProfile,
            )
        }
    }

    @Test
    fun `with Expert mode every profile and Add profile show`() {
        show(expertMode = true)

        composeTestRule.onNodeWithText("Main").assertExists()
        composeTestRule.onNodeWithText("Kid").assertExists()
        composeTestRule.onNodeWithText("Add Profile").assertExists()
    }

    @Test
    fun `without Expert mode no profile row shows`() {
        show(expertMode = false)

        composeTestRule.onNodeWithText("Kid").assertDoesNotExist()
        composeTestRule.onNodeWithText("Main").assertDoesNotExist()
    }

    @Test
    fun `a grown-up without Expert mode gets Add profile`() {
        show(expertMode = false, canAddProfile = true)

        composeTestRule.onNodeWithText("Add Profile").assertExists()
    }

    @Test
    fun `a kid gets no Add profile`() {
        show(expertMode = false, canAddProfile = false)

        composeTestRule.onNodeWithText("Add Profile").assertDoesNotExist()
    }
}
