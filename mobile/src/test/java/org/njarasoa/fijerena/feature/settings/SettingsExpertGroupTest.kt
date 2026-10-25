package org.njarasoa.fijerena.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone's Expert groups (the settings guarded by page plan, SG-D1): rows at the top of
 * Settings for an Expert, in TV's rail order, each opening its page after the PIN; none for a
 * profile without Expert mode (a grown-up or a child).
 */
class SettingsExpertGroupTest {
    @Test
    fun `a profile without Expert mode sees no Expert group`() {
        assertTrue(ExpertGroup.visibleFor(admin = false).isEmpty())
    }

    @Test
    fun `an Expert sees every Expert group, in TV's rail order`() {
        assertEquals(
            listOf(
                ExpertGroup.PROFILES,
                ExpertGroup.PARENTAL_CONTROLS,
                ExpertGroup.THIS_DEVICE,
                ExpertGroup.SOURCE_GUIDE,
                ExpertGroup.SYNC_BACKUP,
                ExpertGroup.ADVANCED,
            ),
            ExpertGroup.visibleFor(admin = true),
        )
    }
}
