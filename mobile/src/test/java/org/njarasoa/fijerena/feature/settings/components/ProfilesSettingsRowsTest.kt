package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.robolectric.RobolectricTestRunner

/**
 * Settings → Profiles (FEATURES → Profiles): with Expert mode every profile and Add profile; without
 * it, only the profile in use, whose row opens its own page.
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
        onEdit: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            ProfilesSettingsRows(
                profiles = profiles,
                message = null,
                newProfileColorIndex = { 2 },
                onAdd = { _, _, _ -> },
                onEdit = onEdit,
                onDismissMessage = {},
                expertMode = expertMode,
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
    fun `without Expert mode only the profile in use shows, and it opens its own page`() {
        var edited: String? = null
        show(expertMode = false, onEdit = { edited = it })

        composeTestRule.onNodeWithText("Kid").assertDoesNotExist()
        composeTestRule.onNodeWithText("Add Profile").assertDoesNotExist()
        composeTestRule.onNodeWithText("Main").performClick()

        assertEquals("main", edited)
    }
}
