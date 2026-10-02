package org.njarasoa.fijerena.core.network

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A provider changed in the app reaches whoever caches it beyond the factory — `AppContainer`'s
 * `MediaRepository`. See docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-06 step 2.
 */
class ProviderChangedListenerTest {
    @After
    fun tearDown() {
        MediaProviderFactory.providerChangedListener = null
    }

    @Test
    fun `providerChanged tells the listener which provider changed`() {
        val told = mutableListOf<Long>()
        MediaProviderFactory.providerChangedListener = { told += it }

        MediaProviderFactory.providerChanged(5L)
        MediaProviderFactory.providerChanged(9L)

        assertEquals(listOf(5L, 9L), told)
    }

    @Test
    fun `clearCache alone, as sync uses it mid-transaction, tells nobody`() {
        val told = mutableListOf<Long>()
        MediaProviderFactory.providerChangedListener = { told += it }

        MediaProviderFactory.clearCache(5L)

        assertEquals(emptyList<Long>(), told)
    }
}
