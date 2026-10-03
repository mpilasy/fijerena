package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgResponse

/**
 * GD1 (G-4): the parsed-results cache belongs to one index build. A refreshed index must show
 * without the user pressing Refresh, so an entry parsed from the previous build is a miss.
 */
class XmltvEpgServiceParsedCacheTest {
    private val prefs = FakeSharedPreferences()
    private val context =
        mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns prefs
        }
    private val service = XmltvEpgService(context, providerId = 1L)

    private val data =
        mapOf(
            "tf1" to
                EpgResponse(
                    listOf(
                        EpgProgram(id = "tf1_100", title = "Le journal", start = "100", end = "200", channelId = "tf1"),
                    ),
                ),
        )

    @Test
    fun entryFromTheSameIndexBuildIsAHit() {
        service.cacheEpg(data, indexGeneration = 1_000L)

        assertEquals(data, service.getCachedEpg(indexGeneration = 1_000L))
    }

    @Test
    fun entryFromAnOlderIndexBuildIsAMiss() {
        service.cacheEpg(data, indexGeneration = 1_000L)

        assertNull(service.getCachedEpg(indexGeneration = 2_000L))
    }

    @Test
    fun nothingCachedIsAMiss() {
        assertNull(service.getCachedEpg(indexGeneration = 1_000L))
    }
}
