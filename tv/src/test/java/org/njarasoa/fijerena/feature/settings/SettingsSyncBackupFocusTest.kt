package org.njarasoa.fijerena.feature.settings

import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.testing.SettingsFixture
import org.njarasoa.fijerena.testing.SettingsFocusTest
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.robolectric.RobolectricTestRunner

/**
 * TV Settings → Sync & Backup (`scripts/focus-walks/settings.txt`): its rows. They open Live sync's
 * screen or the system's file picker, so nothing here presses OK.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsSyncBackupFocusTest : SettingsFocusTest() {
    @Test
    fun `an Expert walks Sync and Backup`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            down("Parental Controls"),
            down("Playback"),
            down("Display"),
            down("This Device"),
            down("Sources & Guide"),
            down("Sync & Backup"),
            // No backup waits in Downloads, so no Restore from Downloads row.
            right("Live Sync"),
            down("Back Up to a File"),
            down("Restore from a File / From a backup file: you choose what to restore"),
            down("Restore from a File"),
            up("Back Up to a File"),
            up("Live Sync"),
            left("Sync & Backup"),
        )
    }
}
