package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.ProviderEntity

/**
 * A profile page's Sources pane: which switches are on, and which may go off (multi-source plan,
 * Phase 2); and the sources its Content filters choose from (Phase 6, O4).
 */
class ProfileSourceRowsTest {
    // An older build's active flag: unused since Phase 6, it changes nothing.
    private val a = ProviderEntity(id = 1, name = "A", url = "http://a.test", username = "me", providerKey = "a", isActive = true)
    private val b = ProviderEntity(id = 2, name = "B", url = "http://b.test", username = "me", providerKey = "b")
    private val c = ProviderEntity(id = 3, name = "C", url = "http://c.test", username = "me", providerKey = "c")

    @Test
    fun `a profile shows its own sources only`() {
        val rows = ProfilesViewModel.profileSourceRows(listOf(a, b, c), setOf("b", "c"))

        assertEquals(listOf(false, true, true), rows.map { it.isOn })
        assertEquals(listOf(false, true, true), rows.map { it.canTurnOff })
    }

    @Test
    fun `a profile's last source can't go off`() {
        val rows = ProfilesViewModel.profileSourceRows(listOf(a, b), setOf("b"))

        assertFalse(rows[1].canTurnOff)
        assertNull(ProfilesViewModel.toggledSourceKeys(setOf("b"), rows, "b", on = false))
    }

    @Test
    fun `turning a source on or off keeps keys whose source isn't on this device`() {
        val rows = ProfilesViewModel.profileSourceRows(listOf(a, b, c), setOf("b", "c", "elsewhere"))

        assertEquals(
            setOf("a", "b", "c", "elsewhere"),
            ProfilesViewModel.toggledSourceKeys(setOf("b", "c", "elsewhere"), rows, "a", on = true),
        )
        assertEquals(setOf("c", "elsewhere"), ProfilesViewModel.toggledSourceKeys(setOf("b", "c", "elsewhere"), rows, "b", on = false))
    }

    @Test
    fun `a profile with no stored sources has none on`() {
        val rows = ProfilesViewModel.profileSourceRows(listOf(a, b, c), null)

        assertTrue(rows.none { it.isOn })
        assertEquals(setOf("c"), ProfilesViewModel.toggledSourceKeys(emptySet(), rows, "c", on = true))
        assertNull(ProfilesViewModel.toggledSourceKeys(emptySet(), rows, "unknown", on = true))
    }

    @Test
    fun `content filters choose among the profile's Xtream sources`() {
        val jellyfin = c.copy(type = "JELLYFIN")

        assertEquals(listOf(FilterSource(1, "A"), FilterSource(2, "B")), ProfilesViewModel.filterSourcesOf(listOf(a, b, jellyfin)))
        assertTrue(ProfilesViewModel.filterSourcesOf(listOf(jellyfin)).isEmpty())
    }
}
