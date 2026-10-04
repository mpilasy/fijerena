package org.njarasoa.fijerena.core.network.xtream.manager

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderSettings

/** [AutoXmltvSources]: one automatic guide source per Xtream source, hand-added ones untouched. */
class AutoXmltvSourcesTest {
    private val server = "http://10.0.2.2:8080"

    private fun source(
        id: Long,
        url: String,
        label: String = "10.0.2.2 (Bulk)",
        providerId: Long = 1,
        addedAtMs: Long = id,
    ) = EpgSourceEntity(id = id, url = url, label = label, providerId = providerId, addedAtMs = addedAtMs)

    private fun auto(
        id: Long,
        user: String,
        providerId: Long = 1,
    ) = source(id, AutoXmltvSources.xmltvUrl(server, user, "pw"), providerId = providerId)

    // --- isAutoXmltvSource ---

    @Test
    fun `the provider's xmltv php with a Bulk label is automatic`() {
        assertTrue(AutoXmltvSources.isAutoXmltvSource(auto(1, "test"), server))
        // Trailing slash on either side, host case, explicit default port.
        assertTrue(AutoXmltvSources.isAutoXmltvSource(auto(1, "test"), "$server/"))
        assertTrue(
            AutoXmltvSources.isAutoXmltvSource(
                source(1, "http://Example.com/xmltv.php?username=a&password=b", label = "example.com (Bulk)"),
                "http://example.com:80/",
            ),
        )
        // A password a URI parser would choke on.
        assertTrue(AutoXmltvSources.isAutoXmltvSource(source(1, "$server/xmltv.php?username=a&password=p#%z w"), server))
        // A server with a path.
        assertTrue(AutoXmltvSources.isAutoXmltvSource(source(1, "http://h.tv/panel/xmltv.php?username=a&password=b"), "http://h.tv/panel/"))
    }

    @Test
    fun `hand-added and other servers' sources are not automatic`() {
        // Hand-added label.
        assertFalse(AutoXmltvSources.isAutoXmltvSource(source(1, "$server/xmltv.php?username=a&password=b", label = "My guide"), server))
        // Another host, port, scheme or path.
        assertFalse(AutoXmltvSources.isAutoXmltvSource(source(1, "http://10.0.2.3:8080/xmltv.php?username=a&password=b"), server))
        assertFalse(AutoXmltvSources.isAutoXmltvSource(source(1, "http://10.0.2.2:8081/xmltv.php?username=a&password=b"), server))
        assertFalse(AutoXmltvSources.isAutoXmltvSource(source(1, "https://10.0.2.2:8080/xmltv.php?username=a&password=b"), server))
        assertFalse(AutoXmltvSources.isAutoXmltvSource(source(1, "$server/other/xmltv.php?username=a&password=b"), server))
        // Another file, or no credentials.
        assertFalse(AutoXmltvSources.isAutoXmltvSource(source(1, "$server/epg.xml.gz"), server))
        assertFalse(AutoXmltvSources.isAutoXmltvSource(source(1, "$server/xmltv.php"), server))
        assertFalse(AutoXmltvSources.isAutoXmltvSource(auto(1, "a"), "not a url"))
    }

    // --- reconcile (login) ---

    @Test
    fun `first login with live channels adds one source`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            val refresh = reconcile(dao, user = "test", hasLive = true)
            assertTrue(refresh)
            val added = dao.rows.single()
            assertEquals(AutoXmltvSources.xmltvUrl(server, "test", "pw"), added.url)
            assertEquals("10.0.2.2 (Bulk)", added.label)
        }

    @Test
    fun `a credential change rewrites the source in place and resets its ingest state`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            dao.rows +=
                auto(5, "test").copy(timezoneOffsetHours = 2, lastIngestedAtMs = 99, etag = "e", lastContentSha256 = "h", lastChannels = 10)
            val refresh = reconcile(dao, user = "kilonga", hasLive = true)
            assertTrue(refresh)
            val row = dao.rows.single()
            assertEquals(5L, row.id)
            assertEquals(AutoXmltvSources.xmltvUrl(server, "kilonga", "pw"), row.url)
            assertEquals(2, row.timezoneOffsetHours)
            assertEquals(0L, row.lastIngestedAtMs)
            assertEquals(0, row.lastChannels)
            assertNull(row.etag)
            assertNull(row.lastContentSha256)
        }

    @Test
    fun `duplicates collapse to the one on the current login, with their guide rows`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            dao.rows += listOf(auto(1, "test"), auto(2, "kilonga"), auto(3, "tahiry"))
            val deletedIndex = mutableListOf<Long>()
            val refresh = reconcile(dao, user = "kilonga", hasLive = true, deletedIndex = deletedIndex)
            assertFalse(refresh)
            assertEquals(listOf(2L), dao.rows.map { it.id })
            assertEquals(listOf(1L, 3L), deletedIndex.sorted())
        }

    @Test
    fun `no live channels removes the automatic source and leaves hand-added ones`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            val handAdded = source(9, "http://epg.example/guide.xml.gz", label = "guide")
            val sameUrlHandAdded = source(8, "$server/xmltv.php?username=x&password=y", label = "Mine")
            dao.rows += listOf(auto(1, "test"), handAdded, sameUrlHandAdded, auto(4, "test", providerId = 2))
            reconcile(dao, user = "test", hasLive = false)
            assertEquals(listOf(4L, 8L, 9L), dao.rows.map { it.id }.sorted())
        }

    @Test
    fun `unknown live channels adds nothing and removes only duplicates`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            assertFalse(reconcile(dao, user = "test", hasLive = null))
            assertTrue(dao.rows.isEmpty())

            dao.rows += listOf(auto(1, "test"), auto(2, "kilonga"))
            reconcile(dao, user = "test", hasLive = null)
            assertEquals(listOf(1L), dao.rows.map { it.id })
        }

    @Test
    fun `a hand-added source with the exact URL stops a second one being added`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            dao.rows += source(8, AutoXmltvSources.xmltvUrl(server, "test", "pw"), label = "Mine")
            assertFalse(reconcile(dao, user = "test", hasLive = true))
            assertEquals(listOf(8L), dao.rows.map { it.id })
        }

    @Test
    fun `a server change carries the source over with the new label`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            dao.rows += auto(1, "test")
            val newServer = "http://new.example:8080"
            AutoXmltvSources.reconcile(dao, {}, 1, newServer, "test", "pw", hasLiveChannels = true, previousProviderUrl = server)
            val row = dao.rows.single()
            assertEquals(1L, row.id)
            assertEquals(AutoXmltvSources.xmltvUrl(newServer, "test", "pw"), row.url)
            assertEquals("new.example (Bulk)", row.label)
        }

    // --- Provides a guide (plan D1) ---

    @Test
    fun `the effective value is on unless set off`() {
        assertTrue(ProviderSettings().providesGuideOn)
        assertTrue(ProviderSettings(providesGuide = true, providesGuideSetByUser = true).providesGuideOn)
        assertFalse(ProviderSettings(providesGuide = false).providesGuideOn)
        assertFalse(ProviderSettings(providesGuide = false, providesGuideSetByUser = true).providesGuideOn)
    }

    @Test
    fun `not set or on adds the source as before`() =
        runBlocking {
            listOf(ProviderSettings(), ProviderSettings(providesGuide = true, providesGuideSetByUser = true)).forEach { settings ->
                val dao = FakeEpgSourceDao()
                assertTrue(reconcile(dao, user = "test", hasLive = true, providesGuide = settings.providesGuideOn))
                assertTrue(dao.rows.single().enabled)
            }
        }

    @Test
    fun `off disables the automatic source, keeps it and its stats, and leaves hand-added ones`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            val handAdded = source(9, "http://epg.example/guide.xml.gz", label = "guide")
            dao.rows += listOf(auto(1, "test").copy(lastChannels = 120, lastIngestedAtMs = 99), handAdded)
            val deletedIndex = mutableListOf<Long>()
            assertFalse(reconcile(dao, user = "test", hasLive = true, providesGuide = false, deletedIndex = deletedIndex))
            val row = dao.rows.single { it.id == 1L }
            assertFalse(row.enabled)
            assertEquals(120, row.lastChannels)
            assertEquals(99L, row.lastIngestedAtMs)
            assertTrue(dao.rows.single { it.id == 9L }.enabled)
            assertTrue(deletedIndex.isEmpty())
        }

    @Test
    fun `off never adds one`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            assertFalse(reconcile(dao, user = "test", hasLive = true, providesGuide = false))
            assertTrue(dao.rows.isEmpty())
        }

    @Test
    fun `a login while off doesn't re-enable or duplicate the disabled source`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            dao.rows += auto(1, "test").copy(enabled = false)
            dao.writes = 0
            assertFalse(reconcile(dao, user = "test", hasLive = true, providesGuide = false))
            assertEquals(listOf(1L), dao.rows.map { it.id })
            assertFalse(dao.rows.single().enabled)
            assertEquals(0, dao.writes)

            // A credential change while off rewrites it in place, still disabled.
            reconcile(dao, user = "kilonga", hasLive = true, providesGuide = false)
            val row = dao.rows.single()
            assertEquals(1L, row.id)
            assertEquals(AutoXmltvSources.xmltvUrl(server, "kilonga", "pw"), row.url)
            assertFalse(row.enabled)
        }

    @Test
    fun `a disabled source stops a second one being added`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            dao.rows += auto(1, "test").copy(enabled = false)
            reconcile(dao, user = "test", hasLive = true, providesGuide = true)
            assertEquals(listOf(1L), dao.rows.map { it.id })
        }

    @Test
    fun `turned on again, the same row is enabled with its stats`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            dao.rows += auto(1, "test").copy(enabled = false, lastChannels = 120, lastIngestedAtMs = 99)
            val refresh =
                AutoXmltvSources.reconcile(
                    sourceDao = dao,
                    deleteIndexRows = {},
                    providerId = 1,
                    providerUrl = server,
                    currentUrl = AutoXmltvSources.xmltvUrl(server, "test", "pw"),
                    hasLiveChannels = null,
                    providesGuide = true,
                    enableKept = true,
                )
            assertTrue(refresh)
            val row = dao.rows.single()
            assertEquals(1L, row.id)
            assertTrue(row.enabled)
            assertEquals(120, row.lastChannels)
            assertEquals(99L, row.lastIngestedAtMs)
        }

    @Test
    fun `detection turns off only the automatic source, and only when the viewer hasn't set it`() {
        val autoSource = auto(1, "test")
        assertTrue(AutoXmltvSources.detectsNoGuide(autoSource, server, ProviderSettings()))
        assertTrue(AutoXmltvSources.detectsNoGuide(autoSource, server, ProviderSettings(providesGuide = false)))
        assertFalse(
            AutoXmltvSources.detectsNoGuide(autoSource, server, ProviderSettings(providesGuide = true, providesGuideSetByUser = true)),
        )
        assertFalse(
            AutoXmltvSources.detectsNoGuide(autoSource, server, ProviderSettings(providesGuide = false, providesGuideSetByUser = true)),
        )
        assertFalse(
            AutoXmltvSources.detectsNoGuide(source(9, "http://epg.example/guide.xml.gz", label = "guide"), server, ProviderSettings()),
        )
    }

    @Test
    fun `an empty own guide disables the source without credentials at hand`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            dao.rows += auto(1, "test")
            // What onEmptyIngest runs once detection has turned the setting off.
            val refresh =
                AutoXmltvSources.reconcile(
                    sourceDao = dao,
                    deleteIndexRows = {},
                    providerId = 1,
                    providerUrl = server,
                    currentUrl = null,
                    hasLiveChannels = null,
                    providesGuide = false,
                )
            assertFalse(refresh)
            assertFalse(dao.rows.single().enabled)
        }

    // --- cleanUp (one-time, at start) ---

    @Test
    fun `cleanup keeps the current login's source, removes the rest and is idempotent`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            val handAdded = source(9, "http://epg.example/guide.xml.gz", label = "guide")
            dao.rows += listOf(auto(1, "test"), auto(2, "kilonga"), auto(3, "tahiry"), handAdded)
            // Provider 2: no live channels; provider 3: unknown, no credentials.
            dao.rows += listOf(auto(4, "a", providerId = 2), auto(5, "b", providerId = 3), auto(6, "c", providerId = 3))
            val providers =
                listOf(
                    AutoXmltvSources.ProviderState(1, server, AutoXmltvSources.xmltvUrl(server, "tahiry", "pw"), hasLiveChannels = true),
                    AutoXmltvSources.ProviderState(2, server, AutoXmltvSources.xmltvUrl(server, "a", "pw"), hasLiveChannels = false),
                    AutoXmltvSources.ProviderState(3, server, null, hasLiveChannels = null),
                )
            AutoXmltvSources.cleanUp(providers, dao) {}
            assertEquals(listOf(3L, 5L, 9L), dao.rows.map { it.id }.sorted())
            assertEquals(AutoXmltvSources.xmltvUrl(server, "tahiry", "pw"), dao.rows.first { it.id == 3L }.url)

            val before = dao.rows.toList()
            dao.writes = 0
            AutoXmltvSources.cleanUp(providers, dao) {}
            assertEquals(before, dao.rows)
            assertEquals(0, dao.writes)
        }

    @Test
    fun `cleanup never adds a source`() =
        runBlocking {
            val dao = FakeEpgSourceDao()
            AutoXmltvSources.cleanUp(
                listOf(AutoXmltvSources.ProviderState(1, server, AutoXmltvSources.xmltvUrl(server, "a", "pw"), hasLiveChannels = true)),
                dao,
            ) {}
            assertTrue(dao.rows.isEmpty())
        }

    private suspend fun reconcile(
        dao: FakeEpgSourceDao,
        user: String,
        hasLive: Boolean?,
        deletedIndex: MutableList<Long> = mutableListOf(),
        providesGuide: Boolean = true,
    ): Boolean = AutoXmltvSources.reconcile(dao, { deletedIndex += it }, 1, server, user, "pw", hasLive, providesGuide = providesGuide)

    /** In-memory [EpgSourceDao]: only what [AutoXmltvSources] uses. */
    private class FakeEpgSourceDao : EpgSourceDao {
        val rows = mutableListOf<EpgSourceEntity>()
        var writes = 0
        private var nextId = 100L

        override suspend fun getAllSourcesOnce() = rows.sortedBy { it.addedAtMs }

        override suspend fun insertSource(source: EpgSourceEntity): Long {
            writes++
            val id = nextId++
            rows += source.copy(id = id)
            return id
        }

        override suspend fun updateSource(source: EpgSourceEntity) {
            writes++
            rows.replaceAll { if (it.id == source.id) source else it }
        }

        override suspend fun deleteSources(ids: List<Long>) {
            writes++
            rows.removeAll { it.id in ids }
        }

        override fun getAllSources(): Flow<List<EpgSourceEntity>> = flowOf(rows)

        override suspend fun getSourceById(id: Long) = rows.firstOrNull { it.id == id }

        override suspend fun getSourceByUrl(
            url: String,
            providerId: Long,
        ) = rows.firstOrNull { it.url == url && it.providerId == providerId }

        override fun getSourcesForProvider(providerId: Long): Flow<List<EpgSourceEntity>> =
            flowOf(rows.filter { it.providerId == providerId })

        override suspend fun getSourceIdsForProvider(providerId: Long) = rows.filter { it.providerId == providerId }.map { it.id }

        override suspend fun getEnabledSourcesForProvider(providerId: Long) = rows.filter { it.providerId == providerId && it.enabled }

        override suspend fun deleteSource(id: Long) = deleteSources(listOf(id))

        override suspend fun deleteSourcesForProvider(providerId: Long) = error("unused")

        override suspend fun deleteAllSources() = error("unused")

        override suspend fun markIngested(
            id: Long,
            timestamp: Long,
            channels: Int,
            programmes: Int,
            downloadBytes: Long,
            ingestMethod: String,
            ingestionDurationMs: Long,
            downloadDurationMs: Long,
            contentSha256: String?,
            etag: String?,
            lastModifiedHeader: String?,
        ) = error("unused")

        override suspend fun markUnchanged(
            id: Long,
            timestamp: Long,
        ) = error("unused")

        override suspend fun markError(
            id: Long,
            error: String,
        ) = error("unused")

        override suspend fun getFailedSources(providerId: Long) = error("unused")

        override suspend fun setRefreshInterval(
            id: Long,
            hours: Int,
        ) = error("unused")

        override suspend fun fillUnsetRefreshIntervals(hours: Int) = error("unused")

        override suspend fun getSourceCount() = rows.size

        override suspend fun resetAllIngestionState() = error("unused")
    }
}
