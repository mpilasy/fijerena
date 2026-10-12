package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.ProviderEntity

/** A profile page's Sources pane: which switches are on, and which may go off (multi-source plan, Phase 2). */
class ProfileSourceRowsTest {
    private val a = ProviderEntity(id = 1, name = "A", url = "http://a.test", username = "me", providerKey = "a", isActive = true)
    private val b = ProviderEntity(id = 2, name = "B", url = "http://b.test", username = "me", providerKey = "b")
    private val c = ProviderEntity(id = 3, name = "C", url = "http://c.test", username = "me", providerKey = "c")

    @Test
    fun `another profile shows its own sources only`() {
        val rows = ProfilesViewModel.profileSourceRows(listOf(a, b, c), setOf("b", "c"), profileInUse = false)

        assertEquals(listOf(false, true, true), rows.map { it.isOn })
        assertTrue(rows.none { it.inUse })
        assertEquals(listOf(false, true, true), rows.map { it.canTurnOff })
    }

    @Test
    fun `the source in use counts as on for the profile in use, and can't go off`() {
        val rows = ProfilesViewModel.profileSourceRows(listOf(a, b, c), setOf("b"), profileInUse = true)

        assertEquals(listOf(true, true, false), rows.map { it.isOn })
        assertTrue(rows[0].inUse)
        assertFalse(rows[0].canTurnOff)
        assertTrue(rows[1].canTurnOff)
    }

    @Test
    fun `a profile's last source can't go off`() {
        val rows = ProfilesViewModel.profileSourceRows(listOf(a, b), setOf("b"), profileInUse = false)

        assertFalse(rows[1].canTurnOff)
        assertNull(ProfilesViewModel.toggledSourceKeys(setOf("b"), rows, "b", on = false))
    }

    @Test
    fun `turning a source on or off keeps keys whose source isn't on this device`() {
        val rows = ProfilesViewModel.profileSourceRows(listOf(a, b, c), setOf("b", "c", "elsewhere"), profileInUse = false)

        assertEquals(
            setOf("a", "b", "c", "elsewhere"),
            ProfilesViewModel.toggledSourceKeys(setOf("b", "c", "elsewhere"), rows, "a", on = true),
        )
        assertEquals(setOf("c", "elsewhere"), ProfilesViewModel.toggledSourceKeys(setOf("b", "c", "elsewhere"), rows, "b", on = false))
    }

    @Test
    fun `a change for the profile in use stores the source it is on too`() {
        val rows = ProfilesViewModel.profileSourceRows(listOf(a, b, c), null, profileInUse = true)

        assertEquals(setOf("a", "c"), ProfilesViewModel.toggledSourceKeys(emptySet(), rows, "c", on = true))
        assertNull(ProfilesViewModel.toggledSourceKeys(emptySet(), rows, "a", on = false))
        assertNull(ProfilesViewModel.toggledSourceKeys(emptySet(), rows, "unknown", on = true))
    }
}
