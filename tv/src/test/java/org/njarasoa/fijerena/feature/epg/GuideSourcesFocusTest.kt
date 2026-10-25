package org.njarasoa.fijerena.feature.epg

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
 * TV Settings → Guide Sources, one page for every source (`scripts/focus-walks/epg-management.txt`), on Source
 * One's one weekly guide source: the switch, the row's buttons, the auto-refresh picker and the
 * way back to its button. Nothing is refreshed or deleted.
 */
@RunWith(RobolectricTestRunner::class)
class GuideSourcesFocusTest : SettingsFocusTest() {
    @Test
    fun `a guide source's row and its auto-refresh picker`() {
        seed(SettingsFixture.ADMIN)
        val sourceId = SettingsFixture.sourceId(context, SettingsFixture.SOURCE_ONE)
        show { TvEpgManagementScreen(providerId = sourceId, onBack = {}) }
        // Entry focus on the first guide source's switch, once the list has loaded.
        walk.awaitFocused("Use this guide")
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
            // Source Two's group has no guide source: its own Add, then Guide Data Maintenance.
            down("Add guide source"),
            down("Guide Data Maintenance"),
            up("Add guide source"),
            up("Refresh"),
            up("Use this guide"),
            up("Add guide source"),
            // Nothing above Add: focus stays.
            up("Add guide source"),
        )
    }

    @Test
    fun `opened on a source with no guide source, focus is on that group's Add`() {
        seed(SettingsFixture.ADMIN)
        val sourceId = SettingsFixture.sourceId(context, SettingsFixture.SOURCE_TWO)
        show { TvEpgManagementScreen(providerId = sourceId, onBack = {}) }
        walk.awaitFocused("Add guide source")
        // Both sources' groups show, Source Two's with no guide source.
        rule.onNodeWithText("Source One").assertExists()
        rule.onNodeWithText("Source Two").assertExists()
        rule.onNodeWithText("No guide sources configured").assertExists()
        // The group's own Add, under Source One's guide source: Up reaches that guide source's row.
        walk.run(up("Refresh"))
        // The Add dialog's Use for list is checked on the emulator: its text field's cursor keeps
        // Compose from going idle here.
    }

    @Test
    fun `opened from Settings, on no source, focus is on the first guide source`() {
        seed(SettingsFixture.ADMIN)
        show { TvEpgManagementScreen(providerId = 0L, onBack = {}) }
        walk.awaitFocused("Use this guide")
        walk.run(up("Add guide source"))
    }
}
