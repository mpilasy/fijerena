package org.njarasoa.fijerena.core.network.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [GuideMerge]: which copies of a guide merge, and which values the merged guide keeps. */
class GuideMergeTest {
    private fun row(
        id: Long,
        providerId: Long,
        key: String = "key-$id",
        url: String = "http://epg.test/guide.xml",
        offset: Int = 0,
        interval: Int? = 24,
        enabled: Boolean = true,
        ingested: Long = 0,
        added: Long = id,
        hlc: Long? = null,
        exists: Boolean = true,
        auto: Boolean = false,
    ) = GuideMerge.Row(
        id = id,
        url = url,
        label = "G$id",
        providerId = providerId,
        providerExists = exists,
        auto = auto,
        sourceKey = key,
        offsetHours = offset,
        intervalHours = interval,
        enabled = enabled,
        lastIngestedAtMs = ingested,
        addedAtMs = added,
        hlc = hlc,
    )

    @Test
    fun `copies of one URL merge, an automatic guide and a gone source's row stay alone`() {
        val groups =
            GuideMerge.groups(
                listOf(
                    row(1, 1),
                    row(2, 2, url = "HTTP://EPG.TEST:80/guide.xml/"),
                    row(3, 3, url = "http://other.test/guide.xml"),
                    row(4, 1, url = "http://iptv.test/xmltv.php", auto = true),
                    row(5, 2, url = "http://iptv.test/xmltv.php", auto = true),
                    row(6, 9, exists = false),
                ),
            )

        assertEquals(listOf(listOf(1L, 2L), listOf(3L), listOf(4L), listOf(5L), listOf(6L)), groups.map { g -> g.map { it.id } })
    }

    @Test
    fun `the newest import keeps its id, the lowest key and its row's label win`() {
        val guide = GuideMerge.merge(listOf(row(1, 1, key = "b", ingested = 10), row(2, 2, key = "a", ingested = 20)))

        assertEquals(2L, guide.survivorId)
        assertEquals("a", guide.sourceKey)
        assertEquals("G2", guide.label)
        assertEquals(mapOf("b" to 1L), guide.aliases)
        // The guide-key row's source is the first link.
        assertEquals(listOf(2L, 1L), guide.links.keys.toList())
        assertEquals(0L, guide.links[2L])
    }

    @Test
    fun `the offset edited last wins, else the guide-key row's`() {
        assertEquals(
            5,
            GuideMerge.merge(listOf(row(1, 1, key = "a", offset = 1, hlc = 10), row(2, 2, key = "b", offset = 5, hlc = 20))).offsetHours,
        )
        assertEquals(1, GuideMerge.merge(listOf(row(1, 1, key = "a", offset = 1), row(2, 2, key = "b", offset = 5))).offsetHours)
    }

    @Test
    fun `the shortest interval that is on wins, off only when all are off`() {
        val off = EpgSourceEntity.REFRESH_OFF
        assertEquals(
            6,
            GuideMerge.merge(listOf(row(1, 1, interval = 24), row(2, 2, interval = 6), row(3, 3, interval = off))).intervalHours,
        )
        assertEquals(off, GuideMerge.merge(listOf(row(1, 1, interval = off), row(2, 2, interval = null))).intervalHours)
        assertNull(GuideMerge.merge(listOf(row(1, 1, interval = null), row(2, 2, interval = null))).intervalHours)
    }

    @Test
    fun `on when one copy is on, read by the sources whose copy was on`() {
        val guide = GuideMerge.merge(listOf(row(1, 1, enabled = false), row(2, 2, enabled = true)))
        assertTrue(guide.enabled)
        assertEquals(setOf(2L), guide.links.keys)

        val allOff = GuideMerge.merge(listOf(row(1, 1, enabled = false), row(2, 2, enabled = false)))
        assertFalse(allOff.enabled)
        assertEquals(setOf(1L, 2L), allOff.links.keys)
    }

    @Test
    fun `a gone source's row keeps its switch and has no link`() {
        val guide = GuideMerge.merge(listOf(row(6, 9, exists = false, enabled = true)))

        assertTrue(guide.enabled)
        assertTrue(guide.links.isEmpty())
    }
}
