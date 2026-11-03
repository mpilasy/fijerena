package org.njarasoa.fijerena.feature.settings

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
 * TV Settings → Admin → Sync & Backup (`scripts/focus-walks/settings.txt`): its rows. They open
 * Live sync's screen or the system's file picker, so nothing here presses OK.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsSyncBackupFocusTest : SettingsFocusTest() {
    @Test
    fun `an Expert walks Sync and Backup`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            *toAdminSection("Sync & Backup"),
            // The last row of Admin's list: Down stays put.
            down("Sync & Backup / Live sync with your other devices, and backups to a file"),
            // No backup waits in Downloads, so no Restore from Downloads row.
            center("Live Sync"),
            down("Back Up to a File"),
            down("Restore from a File / From a backup file: you choose what to restore"),
            // The end of the section: Shrink Database is in Sources & Guide.
            down("Restore from a File"),
            up("Back Up to a File"),
            up("Live Sync"),
            // Left closes the section on its row, then goes to the rail.
            left("Sync & Backup"),
            left("Admin"),
        )
    }
}
