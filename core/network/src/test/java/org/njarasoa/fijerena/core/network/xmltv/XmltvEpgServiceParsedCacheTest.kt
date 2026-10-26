package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgResponse

/**
 * GD1 (G-4): the parsed-results cache belongs to one index build. A refreshed index must show
 * without the user pressing Refresh, so an entry parsed from the previous build is a miss.
 * It is also bounded: a long zapping session keeps only the most recently used channels.
 */
class XmltvEpgServiceParsedCacheTest {
    private val context = mockk<Context>(relaxed = true)
    private val service = XmltvEpgService(context, providerId = 1L)

    private fun listings(channel: String) =
        EpgResponse(
            listOf(
                EpgProgram(id = "${channel}_100", title = "Le journal", start = "100", end = "200", channelId = channel),
            ),
        )

    private val data = mapOf("tf1" to listings("tf1"))

    // The cache is per provider for the whole process.
    @Before
    fun clear() {
        service.clearCache()
    }

    @Test
    fun anotherInstanceForTheSameProviderClearsIt() {
        service.cacheEpg(data, indexGeneration = 1_000L)
        XmltvEpgService(context, providerId = 1L).clearCache()

        assertNull(service.getCachedEpg(indexGeneration = 1_000L, itemIds = listOf("tf1")))
    }

    @Test
    fun entryFromTheSameIndexBuildIsAHit() {
        service.cacheEpg(data, indexGeneration = 1_000L)

        assertEquals(data, service.getCachedEpg(indexGeneration = 1_000L, itemIds = listOf("tf1")))
    }

    @Test
    fun entryFromAnOlderIndexBuildIsAMiss() {
        service.cacheEpg(data, indexGeneration = 1_000L)

        assertNull(service.getCachedEpg(indexGeneration = 2_000L, itemIds = listOf("tf1")))
    }

    @Test
    fun nothingCachedIsAMiss() {
        assertNull(service.getCachedEpg(indexGeneration = 1_000L, itemIds = listOf("tf1")))
    }

    @Test
    fun oneMissingChannelIsAMiss() {
        service.cacheEpg(data, indexGeneration = 1_000L)

        assertNull(service.getCachedEpg(indexGeneration = 1_000L, itemIds = listOf("tf1", "france2")))
    }

    @Test
    fun theLeastRecentlyUsedChannelGoesFirst() {
        service.cacheEpg(data, indexGeneration = 1_000L)
        for (i in 1..32) service.cacheEpg(mapOf("ch$i" to listings("ch$i")), indexGeneration = 1_000L)

        assertNull(service.getCachedEpg(indexGeneration = 1_000L, itemIds = listOf("tf1")))
        assertEquals(listings("ch32"), service.getCachedEpg(indexGeneration = 1_000L, itemIds = listOf("ch32"))?.get("ch32"))
    }
}
