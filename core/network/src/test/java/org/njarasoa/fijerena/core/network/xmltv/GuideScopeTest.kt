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

    private fun guide(id: Long) = EpgSourceEntity(id = id, url = "http://g$id.test/xmltv.php")

    private val guides =
        mapOf(
            1L to listOf(guide(11), guide(12)),
            2L to listOf(guide(21)),
            3L to listOf(guide(31)),
            4L to listOf(guide(41)),
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
            assertEquals(mapOf(11L to listOf(1L), 12L to listOf(1L), 21L to listOf(2L)), scope.providerIdsBySourceId)
        }

    @Test
    fun `a guide source read by several sources maps to each, in the profile's order`() =
        runTest {
            val shared = mapOf(1L to listOf(guide(11), guide(50)), 2L to listOf(guide(50), guide(21)))
            val scope = GuideScope.of(listOf(xtreamB, xtreamA)) { shared.getValue(it) }

            assertEquals(listOf(50L, 21L, 11L), scope.sourceIds)
            assertEquals(listOf(2L, 1L), scope.providerIdsOf(50L))
            assertEquals(listOf(1L), scope.providerIdsOf(11L))
            assertTrue(scope.providerIdsOf(99L).isEmpty())
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
                providerIdsBySourceId = mapOf(11L to listOf(1L), 21L to listOf(2L)),
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
        assertEquals(listOf(2L), matcher.providerIdsOf(21L))
        assertNull(matcher.match(99L, "bbc1.uk", "BBC One"))
    }

    @Test
    fun `hidden categories are each source's own`() {
        val matcher =
            GuideAiringMatcher(
                providerIdsBySourceId = mapOf(11L to listOf(1L), 21L to listOf(2L)),
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

    private fun sharedMatcher(hiddenCategoryIds: Map<Long, Set<String>> = emptyMap()) =
        GuideAiringMatcher(
            providerIdsBySourceId = mapOf(50L to listOf(1L, 2L)),
            matchers =
                mapOf(
                    1L to EpgChannelMatcher(listOf(stream(100, "Kids One", "kids.uk", 1L, categoryId = "7")), 1L),
                    2L to EpgChannelMatcher(listOf(stream(200, "Kids One HD", "kids.uk", 2L, categoryId = "7")), 2L),
                ),
            hiddenCategoryIds = hiddenCategoryIds,
        )

    @Test
    fun `a shared guide source matches the channel on each source that has it`() {
        val matcher = sharedMatcher()

        assertEquals(listOf(100 to 1L, 200 to 2L), matcher.matchAll(50L, "kids.uk", "Kids One").map { it.streamId to it.providerId })
        assertTrue(matcher.matchAll(50L, "none.uk", "None").isEmpty())
        assertEquals(100, matcher.match(50L, "kids.uk", "Kids One")?.streamId)
    }

    @Test
    fun `a shared guide source keeps each source's hidden categories`() {
        val matcher = sharedMatcher(hiddenCategoryIds = mapOf(1L to setOf("7")))
        val (onA, onB) = matcher.matchAll(50L, "kids.uk", "Kids One")

        assertTrue(matcher.isHidden(onA))
        assertFalse(matcher.isHidden(onB))
        // The first match that is not hidden.
        assertEquals(200, matcher.match(50L, "kids.uk", "Kids One")?.streamId)
        assertTrue(matcher.isShown(50L, "kids.uk", "Kids One"))
    }

    @Test
    fun `a channel hidden on every source of a shared guide source stays hidden`() {
        val matcher = sharedMatcher(hiddenCategoryIds = mapOf(1L to setOf("7"), 2L to setOf("7")))

        assertTrue(matcher.isHidden(matcher.match(50L, "kids.uk", "Kids One")!!))
        assertFalse(matcher.isShown(50L, "kids.uk", "Kids One"))
        // A channel no source has stays in the paged results, as before.
        assertTrue(matcher.isShown(50L, "none.uk", "None"))
    }

    @Test
    fun `the worker refreshes the guide sources of every searched source in one run`() =
        runTest {
            val providerIds = GuideScope.searchedProviders(listOf(xtreamA, jellyfin, xtreamB)).map { it.id }
            val sources = EpgSyncWorker.sourcesToRefresh(providerIds) { guides.getValue(it) }

            assertEquals(listOf(11L, 12L, 21L), sources.map { it.id })
        }

    @Test
    fun `the worker skips a source with nothing to refresh`() =
        runTest {
            val sources = EpgSyncWorker.sourcesToRefresh(listOf(1L, 2L)) { if (it == 1L) emptyList() else guides.getValue(it) }

            assertEquals(listOf(21L), sources.map { it.id })
        }

    @Test
    fun `the worker refreshes a guide source that two sources read one time`() =
        runTest {
            val shared = mapOf(1L to listOf(guide(11), guide(12)), 2L to listOf(guide(12), guide(21)))
            val sources = EpgSyncWorker.sourcesToRefresh(listOf(1L, 2L)) { shared.getValue(it) }

            assertEquals(listOf(11L, 12L, 21L), sources.map { it.id })
        }
}
