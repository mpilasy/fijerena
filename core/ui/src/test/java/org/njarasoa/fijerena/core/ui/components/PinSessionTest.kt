package org.njarasoa.fijerena.core.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** How long a typed PIN keeps its profile unlocked (docs/plans/archive/20261017_profile-pin-plan.md, Phase 3). */
class PinSessionTest {
    @Before
    fun setup() {
        PinSession.onActiveProfile("none")
        PinSession.onBackground(1L)
        PinSession.onForeground(1L + PinSession.RELOCK_AFTER_MS)
    }

    @Test
    fun `a typed PIN unlocks its profile only`() {
        PinSession.unlock("kid")

        assertTrue(PinSession.isUnlocked("kid"))
        assertFalse(PinSession.isUnlocked("atr"))
    }

    @Test
    fun `changing the profile in use locks the others`() {
        PinSession.unlock("kid")
        PinSession.unlock("atr")

        PinSession.onActiveProfile("atr")

        assertTrue(PinSession.isUnlocked("atr"))
        assertFalse(PinSession.isUnlocked("kid"))
    }

    @Test
    fun `a short time in the background keeps it, five minutes locks it`() {
        PinSession.unlock("kid")
        PinSession.onBackground(1_000L)
        PinSession.onForeground(1_000L + PinSession.RELOCK_AFTER_MS - 1)
        assertTrue(PinSession.isUnlocked("kid"))

        PinSession.onBackground(10_000_000L)
        PinSession.onForeground(10_000_000L + PinSession.RELOCK_AFTER_MS)
        assertFalse(PinSession.isUnlocked("kid"))
    }
}
