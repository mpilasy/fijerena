package org.njarasoa.fijerena.core.ui.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.sports.ReminderChannel
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserAiring
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserMatchedStream

class ProgrammeReminderOfTest {
    private val stream = EpgBrowserMatchedStream(streamId = 7, streamName = "UK| BBC ONE ᴴᴰ", categoryId = "uk", providerId = 2)
    private val airing =
        EpgBrowserAiring("bbc1", "BBC One", null, startEpoch = 1_000, endEpoch = 2_000, matchedStream = stream, providerId = 2)

    @Test
    fun `an airing not started reminds of its title on its channel`() {
        val reminder = programmeReminderOf("Match of the Day", airing, nowSec = 900)!!
        assertEquals("Match of the Day", reminder.title)
        assertEquals(1_000, reminder.startEpochSec)
        assertEquals(ReminderChannel(2, 7, "uk", "BBC ONE"), reminder.channel)
    }

    @Test
    fun `the airing's source stands in for a stream without one`() {
        val reminder = programmeReminderOf("News", airing.copy(matchedStream = stream.copy(providerId = 0)), nowSec = 900)!!
        assertEquals(2, reminder.channel.providerId)
    }

    @Test
    fun `no reminder for an airing that has started or has no channel`() {
        assertNull(programmeReminderOf("News", airing, nowSec = 1_000))
        assertNull(programmeReminderOf("News", airing.copy(matchedStream = null), nowSec = 900))
    }
}
