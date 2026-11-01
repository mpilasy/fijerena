package org.njarasoa.fijerena.core.ui.sports

import org.junit.Assert.assertEquals
import org.junit.Test

class ShownChannelNameTest {
    @Test
    fun `a channel name loses the tags its badges show`() {
        assertEquals("ESPN DEPORTES", shownChannelName("US| ESPN DEPORTES ᴴᴰ ⁶⁰ᶠᵖˢ"))
        assertEquals("SPORT KLUB 1 ᴿᴬᵂ", shownChannelName("HR: SPORT KLUB 1 ᴿᴬᵂ"))
    }

    @Test
    fun `a separator left at an end goes`() {
        assertEquals("Paramount+ 19", shownChannelName(":Paramount+ 19"))
        assertEquals("Paramount+ 20", shownChannelName("Paramount+ 20 :"))
    }

    @Test
    fun `the logo letter is the first of the shown name`() {
        assertEquals('E', channelLetter("US| ESPN DEPORTES ᴴᴰ"))
    }
}
