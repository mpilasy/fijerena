package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test

/** Add profile's PIN, the same in Settings and on TV's picker (the settings flow review, E7). */
class AddProfilePinTest {
    private val holders = listOf("admin", "other-admin")

    @Test
    fun `an Expert in use with its PIN typed is asked nothing`() {
        assertEquals(emptyList<String>(), ProfilesViewModel.addProfilePins(inUseExpert = true, inUseOpen = true, adminPinHolders = holders))
    }

    @Test
    fun `an Expert in use with no PIN is asked nothing`() {
        assertEquals(
            emptyList<String>(),
            ProfilesViewModel.addProfilePins(inUseExpert = true, inUseOpen = true, adminPinHolders = listOf("other-admin")),
        )
    }

    @Test
    fun `an Expert in use with its PIN not typed gives any Expert's PIN`() {
        assertEquals(holders, ProfilesViewModel.addProfilePins(inUseExpert = true, inUseOpen = false, adminPinHolders = holders))
    }

    @Test
    fun `a profile that is not Expert gives any Expert's PIN`() {
        assertEquals(holders, ProfilesViewModel.addProfilePins(inUseExpert = false, inUseOpen = true, adminPinHolders = holders))
    }

    @Test
    fun `with no Expert PIN nothing is asked`() {
        assertEquals(
            emptyList<String>(),
            ProfilesViewModel.addProfilePins(inUseExpert = false, inUseOpen = true, adminPinHolders = emptyList()),
        )
    }
}
