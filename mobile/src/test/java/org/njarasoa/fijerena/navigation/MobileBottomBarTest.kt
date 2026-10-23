package org.njarasoa.fijerena.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.ContentType

class MobileBottomBarTest {
    @Test
    fun `a compact phone shows icons only, a wider screen names the tabs`() {
        assertFalse(showsTabLabels(360)) // Xperia XZ2 Compact
        assertFalse(showsTabLabels(379))
        assertTrue(showsTabLabels(380))
        assertTrue(showsTabLabels(411)) // Pixel
    }

    @Test
    fun `Home first, every section in bar order whatever order the source lists them in`() {
        assertEquals(
            listOf(MobileTab.HOME, MobileTab.LIVE_TV, MobileTab.MOVIES, MobileTab.TV_SHOWS, MobileTab.SETTINGS),
            visibleTabs(listOf(ContentType.TV_SHOWS, ContentType.LIVE_TV, ContentType.MOVIES)),
        )
    }

    @Test
    fun `only the sections the source has, between Home and Settings`() {
        assertEquals(
            listOf(MobileTab.HOME, MobileTab.MOVIES, MobileTab.TV_SHOWS, MobileTab.SETTINGS),
            visibleTabs(listOf(ContentType.MOVIES, ContentType.TV_SHOWS)),
        )
    }

    @Test
    fun `a single section still shows the bar, for Home and Settings`() {
        assertEquals(
            listOf(MobileTab.HOME, MobileTab.MOVIES, MobileTab.SETTINGS),
            visibleTabs(listOf(ContentType.MOVIES)),
        )
    }

    @Test
    fun `Settings is never the tab the app opens on`() {
        assertEquals(MobileTab.HOME, startTab(MobileTab.SETTINGS.contentType, ALL))
        assertEquals(MobileTab.HOME, startTab(MobileTab.SETTINGS.contentType, null))
    }

    @Test
    fun `Home is not a section but opens when it was the last tab`() {
        assertFalse(MobileTab.HOME.isSection)
        assertEquals(MobileTab.HOME, startTab(MobileTab.HOME.contentType, ALL))
        assertEquals(MobileTab.HOME, startTab(MobileTab.HOME.contentType, null))
    }

    @Test
    fun `a Search tab left from an older version opens Home`() {
        assertEquals(MobileTab.HOME, startTab("SEARCH", ALL))
    }

    @Test
    fun `no bar until the source is resolved or signed in`() {
        assertEquals(emptyList<MobileTab>(), visibleTabs(null))
        assertEquals(emptyList<MobileTab>(), visibleTabs(emptyList()))
    }

    @Test
    fun `opens on the last tab when the source has it`() {
        assertEquals(MobileTab.TV_SHOWS, startTab(ContentType.TV_SHOWS, ALL))
        assertEquals(MobileTab.MOVIES, startTab(ContentType.MOVIES, listOf(ContentType.MOVIES)))
    }

    @Test
    fun `first launch opens on Home`() {
        assertEquals(MobileTab.HOME, startTab(null, ALL))
        assertEquals(MobileTab.HOME, startTab(null, listOf(ContentType.TV_SHOWS)))
    }

    @Test
    fun `a last tab the source lacks opens Home`() {
        assertEquals(MobileTab.HOME, startTab(ContentType.LIVE_TV, listOf(ContentType.MOVIES, ContentType.TV_SHOWS)))
        assertEquals(MobileTab.HOME, startTab("ALL", ALL))
    }

    @Test
    fun `sections not known yet open the last tab or Home`() {
        assertEquals(MobileTab.MOVIES, startTab(ContentType.MOVIES, null))
        assertEquals(MobileTab.HOME, startTab(null, null))
        assertEquals(MobileTab.HOME, startTab(null, emptyList()))
    }

    @Test
    fun `no Live TV tab when the profile's sources have no channels`() {
        // ProfileSections.merged when no Xtream source of the profile has live categories.
        val sections = setOf(ContentType.MOVIES, ContentType.TV_SHOWS)
        assertEquals(listOf(MobileTab.HOME, MobileTab.MOVIES, MobileTab.TV_SHOWS, MobileTab.SETTINGS), visibleTabs(sections))
        assertEquals(MobileTab.HOME, startTab(ContentType.LIVE_TV, sections))
    }

    @Test
    fun `no tabs without merged sections, the tab roots show the sources panel`() {
        assertEquals(emptyList<MobileTab>(), visibleTabs(emptySet()))
    }

    private companion object {
        val ALL = listOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS)
    }
}
