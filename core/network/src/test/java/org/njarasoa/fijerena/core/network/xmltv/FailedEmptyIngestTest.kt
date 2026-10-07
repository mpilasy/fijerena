package org.njarasoa.fijerena.core.network.xmltv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** An empty guide download is a failed refresh when the source had channels, else no guide at all. */
class FailedEmptyIngestTest {
    @Test
    fun `empty after having channels - failed, the old guide stays`() {
        assertTrue(isFailedEmptyIngest(channelsIngested = 0, previousChannels = 8_300))
    }

    @Test
    fun `empty and never had any - not a failure, detection may turn the guide off`() {
        assertFalse(isFailedEmptyIngest(channelsIngested = 0, previousChannels = 0))
    }

    @Test
    fun `channels came back - not a failure`() {
        assertFalse(isFailedEmptyIngest(channelsIngested = 8_300, previousChannels = 8_300))
        assertFalse(isFailedEmptyIngest(channelsIngested = 12, previousChannels = 0))
    }
}
