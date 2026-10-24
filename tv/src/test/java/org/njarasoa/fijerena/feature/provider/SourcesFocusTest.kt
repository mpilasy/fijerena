package org.njarasoa.fijerena.feature.provider

import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.testing.SettingsFixture
import org.njarasoa.fijerena.testing.SettingsFocusTest
import org.njarasoa.fijerena.testing.center
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.robolectric.RobolectricTestRunner

/**
 * TV Settings → Sources & Guide → Manage Sources (`scripts/focus-walks/sources.txt` and
 * `edit-source.txt`): the Sources list with its row slots and ⋮ menu, and Edit Source's two
 * columns, on the fixture's two Xtream sources. Nothing is saved or deleted.
 */
@RunWith(RobolectricTestRunner::class)
class SourcesFocusTest : SettingsFocusTest() {
    @Test
    fun `the Sources list keeps its columns`() {
        seed(SettingsFixture.ADMIN)
        show { TvProviderSelectionScreen(onAddProvider = {}, onEditProvider = {}, onManageEpg = {}, onBack = {}) }
        // Entry focus on the first row, once the list has loaded; rows are ordered by name.
        walk.awaitFocused("Source One")
        walk.run(
            right("Guide"),
            right("More actions for Source One"),
            // Down from ⋮ lands on the ⋮ of the next row.
            down("More actions for Source Two"),
            left("Guide"),
            left("Source Two"),
            up("Source One"),
            // Up from the first row lands on the header's Add Source.
            up("Add Source"),
            down("Source One"),
            right("Guide"),
            right("More actions for Source One"),
            // The menu (a dialog) opens on Duplicate, never on Delete. Back closes a dialog through
            // its window, which a test key doesn't reach: the walk keeps that move.
            center("Duplicate"),
            down("Copy to"),
            down("Delete"),
        )
    }

    @Test
    fun `Edit Source walks its two columns`() {
        seed(SettingsFixture.ADMIN)
        val sourceId = SettingsFixture.sourceId(context, SettingsFixture.SOURCE_ONE)
        show { TvAddProviderScreen(editId = sourceId, onBack = {}, onSuccess = {}) }
        walk.awaitFocused("Edit Source name")
        walk.run(
            down("Edit Server URL"),
            down("Edit Username"),
            down("Edit Password"),
            // Down from the last field lands on Cancel, not on the button under the edit icon.
            down("Cancel"),
            right("Save connection"),
            // Right from Save connection enters the settings column on its first control.
            right("Edit Short Name / Short Name"),
            // With only the main login, its row is one focus stop: the user name, then Main login.
            down("user / Main login"),
            down("Add login"),
            down("Provides a Guide"),
            down("Guide Sources"),
            down("Stream Output Format"),
            down("Playlist Type"),
            // Left goes back to the Connection column, on the row it left; Right comes back.
            left("Save connection"),
            right("Playlist Type"),
            down("Sync data now"),
            up("Playlist Type"),
        )
    }
}
