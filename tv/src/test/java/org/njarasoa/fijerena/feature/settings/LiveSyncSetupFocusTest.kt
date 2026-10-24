package org.njarasoa.fijerena.feature.settings

import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.testing.SettingsFixture
import org.njarasoa.fijerena.testing.SettingsFocusTest
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.up
import org.robolectric.RobolectricTestRunner

/**
 * TV Settings → Sync & Backup → Live Sync on a device that is not linked (the setup panel, which
 * `scripts/focus-walks/live-sync.txt` doesn't walk). The linked screen needs a sync group: its
 * walk keeps it.
 */
@RunWith(RobolectricTestRunner::class)
class LiveSyncSetupFocusTest : SettingsFocusTest() {
    @Test
    fun `the setup panel opens on the empty server address`() {
        seed(SettingsFixture.ADMIN)
        show { SyncSettingsScreen() }
        // No address yet: Check server is disabled, so the address's Edit button has focus.
        walk.awaitFocused("Edit Sync server address")
        walk.run(
            // Disabled, Check server is still a focus stop under it.
            down("Check server"),
            down("Check server"),
            up("Edit Sync server address"),
        )
    }
}
