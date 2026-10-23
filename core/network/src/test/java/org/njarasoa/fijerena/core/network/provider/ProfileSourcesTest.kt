package org.njarasoa.fijerena.core.network.provider

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A profile's sources (multi-source plan, Phase 2): what they resolve to. Since Phase 6 there is no
 * device source to add to them.
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
    fun `nothing else is added to them`() {
        assertEquals(listOf(a), ProviderRepository.resolveProfileSources(listOf(a, b, c), setOf("a")))
        assertEquals(emptyList<ProviderEntity>(), ProviderRepository.resolveProfileSources(listOf(a, b, c), null))
        assertEquals(emptyList<ProviderEntity>(), ProviderRepository.resolveProfileSources(listOf(a, b, c), emptySet()))
    }
}
