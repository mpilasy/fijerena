package org.njarasoa.fijerena.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone's Admin sections: rows of Settings for an Admin, in TV's rail order
 * (docs/plans/20261111_admin-sections-direct-plan.md); none for a profile that is not an Admin (a
 * grown-up or a child).
 */
class SettingsAdminGroupTest {
    @Test
    fun `a profile that is not an Admin sees no Admin group`() {
        assertTrue(AdminGroup.visibleFor(admin = false).isEmpty())
    }

    @Test
    fun `an Admin sees every Admin group, in TV's rail order`() {
        assertEquals(
            listOf(
                AdminGroup.PROFILES,
                AdminGroup.PARENTAL_CONTROLS,
                AdminGroup.THIS_DEVICE,
                AdminGroup.SOURCE_GUIDE,
                AdminGroup.SYNC_BACKUP,
            ),
            AdminGroup.visibleFor(admin = true),
        )
    }
}
