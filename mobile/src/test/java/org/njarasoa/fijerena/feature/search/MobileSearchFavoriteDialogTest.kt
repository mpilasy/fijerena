package org.njarasoa.fijerena.feature.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.model.FavoriteMenuTarget
import org.robolectric.RobolectricTestRunner

/**
 * A film result's long-press menu in Search offers Mark watched, as the Movies list's does
 * (FEATURES → Search: a result's Favourite and Mark watched act on its own source).
 */
@RunWith(RobolectricTestRunner::class)
class MobileSearchFavoriteDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a film result's menu offers Mark watched`() {
        var toggled = false
        composeTestRule.setContent {
            MobileSearchFavoriteDialog(
                target =
                    FavoriteMenuTarget.Stream(
                        itemId = "1",
                        itemName = "Alice in Wonderland",
                        categoryId = "c",
                        contentType = ContentType.MOVIES,
                        isFavorite = false,
                        isWatched = false,
                    ),
                onConfirm = {},
                onDismiss = {},
                onToggleWatched = { toggled = true },
            )
        }

        composeTestRule.onNodeWithText("Mark as watched").performClick()

        assertTrue(toggled)
    }
}
