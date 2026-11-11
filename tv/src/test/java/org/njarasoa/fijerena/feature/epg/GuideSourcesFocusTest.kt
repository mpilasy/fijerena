package org.njarasoa.fijerena.feature.epg

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onNodeWithText
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
 * TV Settings → Guide Sources, one card per guide source (`scripts/focus-walks/epg-management.txt`;
 * the one guide source per URL plan, Design U), on Source One's one weekly guide source: the
 * switch, the card's buttons, the auto-refresh picker and the way back to its button. Source Two
 * has no guide source, so it has its own Add under "Sources Without a Guide Source". Nothing is
 * refreshed, saved or deleted.
 */
@RunWith(RobolectricTestRunner::class)
class GuideSourcesFocusTest : SettingsFocusTest() {
    @Test
    fun `a guide source's card and its auto-refresh picker`() {
        seed(SettingsFixture.ADMIN)
        val sourceId = SettingsFixture.sourceId(context, SettingsFixture.SOURCE_ONE)
        show { TvEpgManagementScreen(providerId = sourceId, onBack = {}) }
        // Entry focus on the switch of the first card Source One uses, once the list has loaded.
        walk.awaitFocused("Use this guide")
        rule.onNodeWithText("Used for: Source One").assertExists()
        walk.run(
            up("Add guide source"),
            down("Use this guide"),
            down("Refresh"),
            right("Edit"),
            right("Auto-refresh"),
            right("Delete"),
            left("Auto-refresh"),
            // The picker opens in place of the list on the current interval; Left closes it
            // unchanged, focus back on Auto-refresh.
            center("Weekly"),
            left("Auto-refresh"),
            left("Edit"),
            left("Refresh"),
            // Source Two has no guide source: its own Add (the heading is not a stop), then Guide
            // Data Maintenance.
            down("Add guide source"),
            down("Guide Data Maintenance"),
            up("Add guide source"),
            up("Refresh"),
            up("Use this guide"),
            // Add is the header's icon action (its label on focus); nothing above it: focus stays.
            up("Add guide source"),
            up("Add guide source"),
        )
    }

    @Test
    fun `opened on a source with no guide source, focus is on its Add`() {
        seed(SettingsFixture.ADMIN)
        val sourceId = SettingsFixture.sourceId(context, SettingsFixture.SOURCE_TWO)
        show { TvEpgManagementScreen(providerId = sourceId, onBack = {}) }
        walk.awaitFocused("Add guide source")
        // Source Two's row is under the heading, after the cards.
        rule.onNodeWithText("Sources Without a Guide Source").assertExists()
        rule.onNodeWithText("Source Two").assertExists()
        // Up reaches the card above it.
        walk.run(up("Refresh"))
    }

    @Test
    fun `the Add dialog opens on its first field, and Cancel closes it on its Add`() {
        seed(SettingsFixture.ADMIN)
        val sourceId = SettingsFixture.sourceId(context, SettingsFixture.SOURCE_TWO)
        show { TvEpgManagementScreen(providerId = sourceId, onBack = {}) }
        walk.awaitFocused("Add guide source")
        // A form opens on its first field, never on Cancel (DialogFirstField).
        walk.press(Key.DirectionCenter)
        walk.awaitFocused("Edit Guide link (XMLTV)")
        // Two sources: Use for lists them.
        rule.onNodeWithText("Use for").assertExists()
        walk.run(
            down("Edit Label (optional)"),
            down("Edit Time correction (hours)"),
            down("Source One"),
            down("Source Two"),
            down("Cancel"),
            // Cancel closes the dialog: focus goes back to the Add that opened it.
            center("Add guide source"),
            up("Refresh"),
        )
    }

    @Test
    fun `the Edit dialog has Use for, and Cancel closes it on Edit`() {
        seed(SettingsFixture.ADMIN)
        show { TvEpgManagementScreen(providerId = 0L, onBack = {}) }
        walk.awaitFocused("Use this guide")
        walk.run(down("Refresh"), right("Edit"))
        walk.press(Key.DirectionCenter)
        walk.awaitFocused("Edit Guide link (XMLTV)")
        // A hand-added guide source and two sources: Use for lists them.
        rule.onNodeWithText("Use for").assertExists()
        walk.run(
            down("Edit Label (optional)"),
            down("Edit Time correction (hours)"),
            down("Source One"),
            down("Source Two"),
            down("Cancel"),
            // Cancel closes the dialog: focus goes back to the card's Edit.
            center("Edit"),
            left("Refresh"),
        )
    }

    @Test
    fun `opened from Settings, on no source, focus is on the first guide source`() {
        seed(SettingsFixture.ADMIN)
        show { TvEpgManagementScreen(providerId = 0L, onBack = {}) }
        walk.awaitFocused("Use this guide")
        walk.run(up("Add guide source"))
    }
}
