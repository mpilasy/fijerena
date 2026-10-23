package org.njarasoa.fijerena.core.network.xmltv

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity

/** Search the guide across the profile's sources (multi-source plan, Phase 7). */
class GuideScopeTest {
    private val xtreamA = ProviderEntity(id = 1, name = "A", url = "http://a.test", username = "me")
    private val xtreamB = ProviderEntity(id = 2, name = "B", url = "http://b.test", username = "me")
    private val jellyfin = ProviderEntity(id = 3, name = "J", url = "http://j.test", username = "me", type = "JELLYFIN")
    private val m3u = ProviderEntity(id = 4, name = "M", url = "http://m.test", username = "", type = "REMOTE_M3U")

    private fun guide(
        id: Long,
        providerId: Long,
    ) = EpgSourceEntity(id = id, url = "http://g$id.test/xmltv.php", providerId = providerId)

    private val guides =
        mapOf(
            1L to listOf(guide(11, 1), guide(12, 1)),
            2L to listOf(guide(21, 2)),
            3L to listOf(guide(31, 3)),
            4L to listOf(guide(41, 4)),
        )

    private fun stream(
        id: Int,
        name: String,
        epgId: String,
        providerId: Long,
        categoryId: String = "news",
    ) = XtreamStreamEntity(
        streamId = id,
        name = name,
        categoryId = categoryId,
        providerId = providerId,
        epgChannelId = epgId,
        streamType = "live",
        streamIcon = "",
        num = id,
        added = "0",
        type = XtreamStreamEntity.TYPE_LIVE,
    )

    @Test
    fun `searched sources are the profile's Xtream ones`() {
        assertEquals(listOf(xtreamA, xtreamB), GuideScope.searchedProviders(listOf(xtreamA, xtreamB, jellyfin)))
    }

    @Test
    fun `an M3U source is searched too`() {
        assertEquals(listOf(xtreamB, m3u), GuideScope.searchedProviders(listOf(xtreamB, jellyfin, m3u)))
    }

    @Test
    fun `guide source ids of every searched source, each mapped to its source`() =
        runTest {
            val scope = GuideScope.of(listOf(xtreamA, xtreamB)) { guides.getValue(it) }

            assertEquals(listOf(11L, 12L, 21L), scope.sourceIds)
            assertEquals(mapOf(11L to 1L, 12L to 1L, 21L to 2L), scope.providerIdBySourceId)
        }

    @Test
    fun `source names only when more than one source is searched`() =
        runTest {
            assertEquals(mapOf(1L to "A", 2L to "B"), GuideScope.of(listOf(xtreamA, xtreamB)) { guides.getValue(it) }.sourceNames)
            assertTrue(GuideScope.of(listOf(xtreamA)) { guides.getValue(it) }.sourceNames.isEmpty())
        }

    @Test
    fun `matchers are cached per source`() {
        EpgChannelMatcher.clearCache()
        val a = EpgChannelMatcher.getOrCreate(1L) { listOf(stream(100, "BBC One", "bbc1.uk", 1L)) }
        val b = EpgChannelMatcher.getOrCreate(2L) { listOf(stream(200, "BBC One HD", "bbc1.uk", 2L)) }

        assertEquals(a, EpgChannelMatcher.getOrCreate(1L) { error("cached") })
        assertEquals(b, EpgChannelMatcher.getOrCreate(2L) { error("cached") })
        assertEquals(100, a.match("bbc1.uk", "BBC One")?.streamId)
        assertEquals(200, b.match("bbc1.uk", "BBC One")?.streamId)
        EpgChannelMatcher.clearCache()
    }

    @Test
    fun `an airing is matched on its own source's channel`() {
        val matcher =
            GuideAiringMatcher(
                providerIdBySourceId = mapOf(11L to 1L, 21L to 2L),
                matchers =
                    mapOf(
                        1L to EpgChannelMatcher(listOf(stream(100, "BBC One", "bbc1.uk", 1L)), 1L),
                        2L to EpgChannelMatcher(listOf(stream(200, "BBC One HD", "bbc1.uk", 2L)), 2L),
                    ),
                hiddenCategoryIds = emptyMap(),
            )

        val onA = matcher.match(11L, "bbc1.uk", "BBC One")
        val onB = matcher.match(21L, "bbc1.uk", "BBC One")

        assertEquals(100 to 1L, onA?.streamId to onA?.providerId)
        assertEquals(200 to 2L, onB?.streamId to onB?.providerId)
        assertEquals(2L, matcher.providerIdOf(21L))
        assertNull(matcher.match(99L, "bbc1.uk", "BBC One"))
    }

    @Test
    fun `hidden categories are each source's own`() {
        val matcher =
            GuideAiringMatcher(
                providerIdBySourceId = mapOf(11L to 1L, 21L to 2L),
                matchers =
                    mapOf(
                        1L to EpgChannelMatcher(listOf(stream(100, "Kids One", "kids.uk", 1L, categoryId = "7")), 1L),
                        2L to EpgChannelMatcher(listOf(stream(200, "Kids One", "kids.uk", 2L, categoryId = "7")), 2L),
                    ),
                hiddenCategoryIds = mapOf(1L to setOf("7")),
            )

        assertTrue(matcher.isHidden(matcher.match(11L, "kids.uk", "Kids One")!!))
        assertFalse(matcher.isHidden(matcher.match(21L, "kids.uk", "Kids One")!!))
    }

    @Test
    fun `the worker refreshes the guide sources of every searched source, one run each`() =
        runTest {
            val providerIds = GuideScope.searchedProviders(listOf(xtreamA, jellyfin, xtreamB)).map { it.id }
            val runs = EpgSyncWorker.sourcesToRefresh(providerIds) { guides.getValue(it) }

            assertEquals(listOf(listOf(11L, 12L), listOf(21L)), runs.map { run -> run.map { it.id } })
        }

    @Test
    fun `the worker skips a source with nothing to refresh`() =
        runTest {
            val runs = EpgSyncWorker.sourcesToRefresh(listOf(1L, 2L)) { if (it == 1L) emptyList() else guides.getValue(it) }

            assertEquals(listOf(listOf(21L)), runs.map { run -> run.map { it.id } })
        }
}
