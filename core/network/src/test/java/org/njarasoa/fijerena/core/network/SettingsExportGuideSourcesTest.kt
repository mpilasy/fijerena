package org.njarasoa.fijerena.core.network

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.SettingsExportManager.Companion.exportedEpgSources
import org.njarasoa.fijerena.core.network.SettingsExportManager.Companion.importEpgSources
import org.njarasoa.fijerena.core.network.SettingsExportManager.ExportedEpgSource
import org.njarasoa.fijerena.core.network.SettingsExportManager.ExportedSettings
import org.njarasoa.fijerena.core.network.SettingsExportManager.ExportedSourceRef
import org.njarasoa.fijerena.core.network.SettingsExportManager.GlobalSettings
import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.EpgSourceProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity

/** One guide source per URL in a settings file (the one guide source per URL plan, Design E). */
class SettingsExportGuideSourcesTest {
    private val a = ProviderEntity(id = 1, name = "A", url = "http://a.test", username = "me", type = "M3U")
    private val b = ProviderEntity(id = 2, name = "B", url = "http://b.test", username = "me", type = "M3U")
    private val x = ProviderEntity(id = 3, name = "X", url = "http://iptv.test", username = "me", type = "XTREAM")
    private val providers = listOf(a, b, x)

    private fun dao(
        sources: List<EpgSourceEntity> = emptyList(),
        links: List<EpgSourceProviderEntity> = emptyList(),
    ) = mockk<EpgSourceDao>(relaxed = true).also {
        coEvery { it.getAllSourcesOnce() } returns sources
        coEvery { it.getAllLinksOnce() } returns links
        coEvery { it.insertGuide(any(), any()) } returns 99L
    }

    private fun entry(
        url: String,
        vararg owners: ProviderEntity,
        legacy: Boolean = false,
    ) = ExportedEpgSource(
        url = url,
        providerName = owners.first().name,
        providerUrl = owners.first().url,
        providers = if (legacy) emptyList() else owners.map { ExportedSourceRef(it.name, it.url) },
    )

    @Test
    fun `export writes one entry for each guide, with its sources in link order`() {
        val guide = EpgSourceEntity(id = 5, url = "http://epg.test/g.xml", label = "G")
        val links = listOf(EpgSourceProviderEntity(5, b.id, 1), EpgSourceProviderEntity(5, a.id, 2))

        val exported = exportedEpgSources(listOf(guide), links, providers.associateBy { it.id }).single()

        assertEquals(listOf(ExportedSourceRef("B", "http://b.test"), ExportedSourceRef("A", "http://a.test")), exported.providers)
        assertEquals("B", exported.providerName)
        assertEquals("http://b.test", exported.providerUrl)
    }

    @Test
    fun `export leaves out an automatic guide and a guide no source reads`() {
        val auto = EpgSourceEntity(id = 5, url = "http://iptv.test/xmltv.php", label = "iptv.test (Bulk)")
        val unlinked = EpgSourceEntity(id = 6, url = "http://epg.test/g.xml")

        val exported = exportedEpgSources(listOf(auto, unlinked), listOf(EpgSourceProviderEntity(5, x.id)), providers.associateBy { it.id })

        assertTrue(exported.isEmpty())
    }

    @Test
    fun `an older file without the sources list is read`() {
        val file = """{"version":5,"epgSources":[{"url":"u","providerName":"A","providerUrl":"http://a.test"}]}"""

        val source =
            SettingsExportManager.json
                .decodeFromString<ExportedSettings>(file)
                .epgSources
                .single()

        assertTrue(source.providers.isEmpty())
    }

    @Test
    fun `import adds one guide with every source of the entry`() =
        runBlocking {
            val dao = dao()
            val inserted = slot<EpgSourceEntity>()
            coEvery { dao.insertGuide(capture(inserted), listOf(a.id, b.id)) } returns 7L

            val result = importEpgSources(listOf(entry("http://epg.test/g.xml", a, b)), GlobalSettings(), providers, dao)

            assertEquals(1 to 0, result)
            assertEquals("http://epg.test/g.xml", inserted.captured.url)
        }

    @Test
    fun `legacy entries for one URL make one guide with both sources`() =
        runBlocking {
            val dao = dao()

            val result =
                importEpgSources(
                    listOf(entry("http://epg.test/g.xml", a, legacy = true), entry("HTTP://epg.test/g.xml/", b, legacy = true)),
                    GlobalSettings(),
                    providers,
                    dao,
                )

            assertEquals(2 to 0, result)
            coVerify(exactly = 1) { dao.insertGuide(any(), listOf(a.id)) }
            coVerify(exactly = 1) { dao.setLinks(99L, listOf(a.id, b.id)) }
        }

    @Test
    fun `a guide already here gets the missing sources, and nothing when it has them all`() =
        runBlocking {
            val here = EpgSourceEntity(id = 5, url = "http://epg.test/g.xml")
            val dao = dao(listOf(here), listOf(EpgSourceProviderEntity(5, a.id)))

            val result =
                importEpgSources(
                    listOf(entry("http://epg.test/g.xml", a, b), entry("http://epg.test/g.xml", a)),
                    GlobalSettings(),
                    providers,
                    dao,
                )

            assertEquals(1 to 1, result)
            coVerify(exactly = 1) { dao.setLinks(5L, listOf(a.id, b.id)) }
            coVerify(exactly = 0) { dao.insertGuide(any(), any()) }
        }

    @Test
    fun `import never merges with an automatic guide, and skips an entry with no source here`() =
        runBlocking {
            val auto = EpgSourceEntity(id = 5, url = "http://iptv.test/xmltv.php", label = "iptv.test (Bulk)")
            val dao = dao(listOf(auto), listOf(EpgSourceProviderEntity(5, x.id)))
            val gone = ProviderEntity(name = "Gone", url = "http://gone.test", username = "me")

            val result =
                importEpgSources(
                    listOf(entry("http://iptv.test/xmltv.php", a), entry("http://epg.test/g.xml", gone)),
                    GlobalSettings(),
                    providers,
                    dao,
                )

            assertEquals(1 to 1, result)
            coVerify(exactly = 1) { dao.insertGuide(any(), listOf(a.id)) }
            coVerify(exactly = 0) { dao.setLinks(any(), any()) }
        }

    @Test
    fun `an entry that is an Xtream source's own guide is skipped`() =
        runBlocking {
            val dao = dao()

            val result =
                importEpgSources(listOf(entry("http://iptv.test/xmltv.php?username=me&password=pw", x)), GlobalSettings(), providers, dao)

            assertEquals(0 to 1, result)
            coVerify(exactly = 0) { dao.insertGuide(any(), any()) }
        }
}
