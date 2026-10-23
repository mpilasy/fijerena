package org.njarasoa.fijerena.core.network.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.SourceShortNames.Source

/** Source badges' short names: the viewer's own, else the shortest start of the name no other source shares. */
class SourceShortNamesTest {
    @Test
    fun aSourceAloneGetsThreeLetters() {
        assertEquals(mapOf(1L to "STR"), SourceShortNames.of(listOf(Source(1, "streamhub", ""))))
    }

    @Test
    fun namesStartingAlikeGetLongerUntilTheyDiffer() {
        val names = SourceShortNames.of(listOf(Source(1, "lions", ""), Source(2, "lionstv", ""), Source(3, "jellyxtream", "")))
        assertEquals(mapOf(1L to "LIONS", 2L to "LIOTV", 3L to "JEL"), names)
    }

    @Test
    fun theViewersOwnWinsAndOthersAvoidIt() {
        val names = SourceShortNames.of(listOf(Source(1, "streamhub premium", "X4P"), Source(2, "x4p", "")))
        assertEquals("X4P", names[1])
        assertEquals("X4P2", names[2])
    }

    @Test
    fun namesSharingALongStartStayFiveLettersOrFewer() {
        val names = SourceShortNames.of(listOf(Source(1, "lions", ""), Source(2, "streamhubb", ""), Source(3, "streamhubp", "")))
        assertEquals(mapOf(1L to "LIO", 2L to "STRB", 3L to "STRP"), names)
        assertTrue(names.values.all { it.length <= SourceShortNames.MAX_LENGTH })
    }

    @Test
    fun theSameNameTwiceGetsANumber() {
        val names = SourceShortNames.of(listOf(Source(1, "streamhub", ""), Source(2, "streamhub", "")))
        assertEquals(mapOf(1L to "STR", 2L to "STR2"), names)
    }

    @Test
    fun renamingASourceChangesItsAutomaticNameOnly() {
        val before = SourceShortNames.of(listOf(Source(1, "lions", ""), Source(2, "premium", "X4P")))
        val after = SourceShortNames.of(listOf(Source(1, "nounours", ""), Source(2, "xstream premium", "X4P")))
        assertEquals("LIO", before[1])
        assertEquals("NOU", after[1])
        assertEquals("X4P", after[2])
    }

    @Test
    fun aShortNameMustBeFreeAndShort() {
        val sources = listOf(Source(1, "lions", ""), Source(2, "streamhub", "X4P"))
        assertTrue(SourceShortNames.isFree("BRS", 1, sources))
        assertTrue("blank is automatic", SourceShortNames.isFree("", 1, sources))
        assertFalse("another source's", SourceShortNames.isFree("x4p", 1, sources))
        assertFalse("too long", SourceShortNames.isFree("LIONSS", 1, sources))
        assertTrue("its own", SourceShortNames.isFree("X4P", 2, sources))
        assertFalse("another source's automatic one", SourceShortNames.isFree("LIO", 2, sources))
    }
}
