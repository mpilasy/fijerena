package org.njarasoa.fijerena.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.ContentType

class MobileBottomBarTest {
    @Test
    fun `every section in bar order whatever order the source lists them in`() {
        assertEquals(
            listOf(MobileTab.HOME, MobileTab.LIVE_TV, MobileTab.MOVIES, MobileTab.TV_SHOWS),
            visibleTabs(listOf(ContentType.TV_SHOWS, ContentType.LIVE_TV, ContentType.MOVIES)),
        )
    }

    @Test
    fun `only the sections the source has`() {
        assertEquals(
            listOf(MobileTab.HOME, MobileTab.MOVIES, MobileTab.TV_SHOWS),
            visibleTabs(listOf(ContentType.MOVIES, ContentType.TV_SHOWS)),
        )
    }

    @Test
    fun `single content type shows Home and that section`() {
        assertEquals(listOf(MobileTab.HOME, MobileTab.MOVIES), visibleTabs(listOf(ContentType.MOVIES)))
    }

    @Test
    fun `Home alone until the source is resolved or signed in`() {
        assertEquals(listOf(MobileTab.HOME), visibleTabs(null))
        assertEquals(listOf(MobileTab.HOME), visibleTabs(emptyList()))
    }

    @Test
    fun `content types map to their tabs`() {
        assertEquals(MobileTab.LIVE_TV, MobileTab.forContentType(ContentType.LIVE_TV))
        assertEquals(MobileTab.MOVIES, MobileTab.forContentType(ContentType.MOVIES))
        assertEquals(MobileTab.TV_SHOWS, MobileTab.forContentType(ContentType.TV_SHOWS))
        assertNull(MobileTab.forContentType("ALL"))
    }
}
