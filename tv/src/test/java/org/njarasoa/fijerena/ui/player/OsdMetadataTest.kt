package org.njarasoa.fijerena.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.PlayerMetadata

class OsdMetadataTest {
    private val playing =
        PlayerMetadata(
            title = "YOL TV",
            channelName = "YOL TV",
            description = "News",
            isLive = true,
            logoUrl = "http://logo/yol.png",
        )

    @Test
    fun `the header names the channel being tuned, with its logo`() {
        val shown = osdMetadata(playing, "Arena Sport 1", "http://logo/arena.png")
        assertEquals("Arena Sport 1", shown.channelName)
        assertEquals("http://logo/arena.png", shown.logoUrl)
        assertNull(shown.description)
    }

    @Test
    fun `nothing being tuned keeps the playing channel`() {
        assertSame(playing, osdMetadata(playing, null, null))
    }

    @Test
    fun `a film keeps its own metadata`() {
        val film = playing.copy(isLive = false, title = "The Matrix")
        assertSame(film, osdMetadata(film, "Arena Sport 1", null))
    }
}
