package org.njarasoa.fijerena.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.feature.contentselection.ActiveSource

class MobileBottomBarTest {
    @Test
    fun `every section in bar order whatever order the source lists them in`() {
        assertEquals(
            listOf(MobileTab.LIVE_TV, MobileTab.MOVIES, MobileTab.TV_SHOWS, MobileTab.SEARCH, MobileTab.SETTINGS),
            visibleTabs(listOf(ContentType.TV_SHOWS, ContentType.LIVE_TV, ContentType.MOVIES)),
        )
    }

    @Test
    fun `only the sections the source has, then Search and Settings`() {
        assertEquals(
            listOf(MobileTab.MOVIES, MobileTab.TV_SHOWS, MobileTab.SEARCH, MobileTab.SETTINGS),
            visibleTabs(listOf(ContentType.MOVIES, ContentType.TV_SHOWS)),
        )
    }

    @Test
    fun `a single section still shows the bar, for Search and Settings`() {
        assertEquals(
            listOf(MobileTab.MOVIES, MobileTab.SEARCH, MobileTab.SETTINGS),
            visibleTabs(listOf(ContentType.MOVIES)),
        )
    }

    @Test
    fun `Settings is never the tab the app opens on`() {
        assertEquals(MobileTab.LIVE_TV, startTab(MobileTab.SETTINGS.contentType, ALL))
        assertEquals(MobileTab.LIVE_TV, startTab(MobileTab.SETTINGS.contentType, null))
    }

    @Test
    fun `Search is never the tab the app opens on`() {
        assertEquals(false, MobileTab.SEARCH.isSection)
        assertEquals(MobileTab.LIVE_TV, startTab(MobileTab.SEARCH.contentType, ALL))
        assertEquals(MobileTab.LIVE_TV, startTab(MobileTab.SEARCH.contentType, null))
        assertEquals(MobileTab.MOVIES, startTab(MobileTab.SEARCH.contentType, listOf(ContentType.MOVIES)))
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
    fun `first launch opens on Live TV, else Movies, else TV Shows`() {
        assertEquals(MobileTab.LIVE_TV, startTab(null, ALL))
        assertEquals(MobileTab.MOVIES, startTab(null, listOf(ContentType.TV_SHOWS, ContentType.MOVIES)))
        assertEquals(MobileTab.TV_SHOWS, startTab(null, listOf(ContentType.TV_SHOWS)))
    }

    @Test
    fun `a last tab the source lacks falls back in bar order`() {
        assertEquals(MobileTab.MOVIES, startTab(ContentType.LIVE_TV, listOf(ContentType.MOVIES, ContentType.TV_SHOWS)))
        assertEquals(MobileTab.LIVE_TV, startTab("ALL", ALL))
    }

    @Test
    fun `sections not known yet open the last tab or Live TV`() {
        assertEquals(MobileTab.MOVIES, startTab(ContentType.MOVIES, null))
        assertEquals(MobileTab.LIVE_TV, startTab(null, null))
        assertEquals(MobileTab.LIVE_TV, startTab(null, emptyList()))
    }

    @Test
    fun `no Live TV tab for a source without channels`() {
        val source = ActiveSource(1L, "jellyxtream", "XTREAM", needsSignIn = false, supportedTypes = ALL.toSet(), noChannels = true)
        assertEquals(listOf(MobileTab.MOVIES, MobileTab.TV_SHOWS, MobileTab.SEARCH, MobileTab.SETTINGS), visibleTabs(source.sections))
        assertEquals(MobileTab.MOVIES, startTab(ContentType.LIVE_TV, source.sections))
        assertEquals(ALL.toSet(), source.copy(noChannels = false).sections)
    }

    private companion object {
        val ALL = listOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS)
    }
}
