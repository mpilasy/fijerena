package org.njarasoa.fijerena.core.network.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A profile's sources (multi-source plan, Phase 2): what they resolve to, and where a profile
 * switch takes the device.
 */
class ProfileSourcesTest {
    private val a = ProviderEntity(id = 1, name = "A", url = "http://a.test", username = "me", providerKey = "a")
    private val b = ProviderEntity(id = 2, name = "B", url = "http://b.test", username = "me", providerKey = "b")
    private val c = ProviderEntity(id = 3, name = "C", url = "http://c.test", username = "me", providerKey = "c")

    @Test
    fun `a profile's sources are those of its keys this device has, in list order`() {
        val result = ProviderRepository.resolveProfileSources(listOf(a, b, c), setOf("c", "a", "deleted-elsewhere"))

        assertEquals(listOf(a, c), result)
    }

    @Test
    fun `the source the device is on is always among them`() {
        val active = b.copy(isActive = true)

        assertEquals(listOf(a, active), ProviderRepository.resolveProfileSources(listOf(a, active, c), setOf("a")))
        assertEquals(listOf(active), ProviderRepository.resolveProfileSources(listOf(a, active, c), null))
        assertEquals(listOf(active), ProviderRepository.resolveProfileSources(listOf(a, active, c), emptySet()))
    }

    @Test
    fun `a profile switch goes to its last pick when it has it`() {
        val target = ProviderRepository.profileSourceToActivate(listOf(a.copy(isActive = true), b, c), setOf("b", "c"), lastKey = "c")

        assertEquals(c, target)
    }

    @Test
    fun `a profile switch never goes to a source the profile lacks`() {
        val active = a.copy(isActive = true)

        // Its last pick is not one of its sources: its first one instead.
        assertEquals(b, ProviderRepository.profileSourceToActivate(listOf(active, b, c), setOf("b", "c"), lastKey = "a"))
        assertEquals(b, ProviderRepository.profileSourceToActivate(listOf(active, b, c), setOf("b"), lastKey = null))
    }

    @Test
    fun `a profile switch stays put on a source the profile has`() {
        val active = b.copy(isActive = true)

        assertNull(ProviderRepository.profileSourceToActivate(listOf(a, active, c), setOf("a", "b"), lastKey = "deleted"))
        assertNull(ProviderRepository.profileSourceToActivate(listOf(a, active, c), setOf("a", "b"), lastKey = "b"))
    }

    @Test
    fun `a profile with none of its sources here follows its last pick as before`() {
        val active = a.copy(isActive = true)

        assertEquals(c, ProviderRepository.profileSourceToActivate(listOf(active, b, c), null, lastKey = "c"))
        assertEquals(c, ProviderRepository.profileSourceToActivate(listOf(active, b, c), setOf("not-here"), lastKey = "c"))
        assertNull(ProviderRepository.profileSourceToActivate(listOf(active, b, c), null, lastKey = null))
    }
}
