package org.njarasoa.fijerena.core.network.sync

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerClockTest {
    private fun device(
        id: String,
        lastSeen: Long,
    ) = SyncWire.Device(id = id, name = id, createdAt = 0, lastSeen = lastSeen, revoked = false, current = false)

    @After
    fun tearDown() = ServerClock.forget()

    @Test
    fun `until the offset is known, the time is the device's own`() {
        assertFalse(ServerClock.isKnown)
        assertEquals(5_000L, ServerClock.now(5_000L))
    }

    @Test
    fun `the time comes from the device's own row, at the middle of the call`() {
        ServerClock.update(listOf(device("other", 1L), device("me", 10_000L)), "me", askedAt = 1_000L, answeredAt = 1_200L)

        assertTrue(ServerClock.isKnown)
        assertEquals(10_000L, ServerClock.now(elapsedNow = 1_100L))
        assertEquals(10_500L, ServerClock.now(elapsedNow = 1_600L))
    }

    @Test
    fun `a change of the device's clock after the reading changes nothing`() {
        ServerClock.update(listOf(device("me", 10_000L)), "me", askedAt = 1_000L, answeredAt = 1_000L)

        assertEquals(10_500L, ServerClock.now(deviceNow = 99_000_000L, elapsedNow = 1_500L))
    }

    @Test
    fun `an answer without the device's own row changes nothing`() {
        ServerClock.update(listOf(device("other", 10_000L)), "me", askedAt = 1_000L, answeredAt = 1_200L)

        assertFalse(ServerClock.isKnown)
        assertEquals(1_100L, ServerClock.now(1_100L))
    }
}
