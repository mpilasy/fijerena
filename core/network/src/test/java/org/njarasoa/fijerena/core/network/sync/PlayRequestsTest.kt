package org.njarasoa.fijerena.core.network.sync

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The send to TV store: one use, an age limit, closes and each answer once. See the send to TV plan → Phase 1. */
class PlayRequestsTest {
    private val now = 1_800_000_000_000L

    private fun request(
        requestId: String = "r1",
        open: Boolean = true,
        sentAt: Long = now,
    ) = SyncPayloads.PlayRequest(requestId, profileId = "anna", providerKey = "pk", streamId = "42", open = open, sentAt = sentAt)

    private fun receive(
        request: SyncPayloads.PlayRequest,
        hlc: Long = 7,
        at: Long = now,
    ) = PlayRequests.receiveRequest(request, hlc, at)

    private fun answers(vararg sent: SyncPayloads.PlayAnswer): List<SyncPayloads.PlayAnswer> =
        runBlocking {
            val received = mutableListOf<SyncPayloads.PlayAnswer>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { PlayRequests.answers.collect { received += it } }
            sent.forEach { PlayRequests.receiveAnswer(it) }
            yield()
            collector.cancel()
            received
        }

    @Before
    fun setup() = PlayRequests.reset()

    @After
    fun tearDown() = PlayRequests.reset()

    @Test
    fun `an open recent request is held`() {
        assertTrue(receive(request()))

        assertEquals(
            "r1",
            PlayRequests.incoming.value
                ?.request
                ?.requestId,
        )
        assertEquals(now, PlayRequests.incoming.value?.receivedAt)
    }

    @Test
    fun `a request is shown once, also after it is done`() {
        receive(request())
        PlayRequests.done("r1")

        assertFalse(receive(request(), hlc = 9))
        assertNull(PlayRequests.incoming.value)
    }

    @Test
    fun `a closed request removes the open one and stays out`() {
        receive(request())

        assertTrue(receive(request(open = false), hlc = 8))
        assertNull(PlayRequests.incoming.value)
        assertFalse(receive(request(), hlc = 9))
    }

    @Test
    fun `a closed request for another id leaves the open one`() {
        receive(request())

        assertFalse(receive(request(requestId = "r2", open = false), hlc = 8))
        assertEquals(
            "r1",
            PlayRequests.incoming.value
                ?.request
                ?.requestId,
        )
    }

    @Test
    fun `a TV that opens after the phone closed the request does nothing`() {
        assertFalse(receive(request(open = false)))
        assertFalse(receive(request(), hlc = 9, at = now + 5_000))
        assertNull(PlayRequests.incoming.value)
    }

    @Test
    fun `a request older than the limit is ignored, and a wrong clock fails safe`() {
        assertFalse(receive(request(sentAt = now - PlayRequests.MAX_AGE_MS - 1)))
        assertFalse(receive(request(requestId = "r2", sentAt = now + PlayRequests.MAX_AGE_MS + 1), hlc = 8))
        assertNull(PlayRequests.incoming.value)
    }

    @Test
    fun `a request at the limit is held, and then expires by its receive time`() {
        assertTrue(receive(request(sentAt = now - PlayRequests.MAX_AGE_MS)))
        val held = PlayRequests.incoming.value!!

        assertFalse(PlayRequests.isExpired(held, now + PlayRequests.MAX_AGE_MS))
        assertTrue(PlayRequests.isExpired(held, now + PlayRequests.MAX_AGE_MS + 1))
    }

    @Test
    fun `a newer request replaces the old one, an older record does not`() {
        receive(request(), hlc = 7)
        assertTrue(receive(request(requestId = "r2"), hlc = 9))
        assertFalse(receive(request(requestId = "r3"), hlc = 8))

        assertEquals(
            "r2",
            PlayRequests.incoming.value
                ?.request
                ?.requestId,
        )
    }

    @Test
    fun `a request without an id, a profile or a source is ignored`() {
        assertFalse(receive(request().copy(requestId = "")))
        assertFalse(receive(request().copy(profileId = ""), hlc = 8))
        assertFalse(receive(request().copy(providerKey = ""), hlc = 9))
    }

    @Test
    fun `done clears only that request`() {
        receive(request())
        PlayRequests.done("other")

        assertEquals(
            "r1",
            PlayRequests.incoming.value
                ?.request
                ?.requestId,
        )
        PlayRequests.done("r1")
        assertNull(PlayRequests.incoming.value)
    }

    @Test
    fun `each result reaches the device that waits, once`() {
        PlayRequests.ask("r1")

        val heard =
            answers(
                SyncPayloads.PlayAnswer("r1", SyncPayloads.PlayAnswer.RECEIVED),
                SyncPayloads.PlayAnswer("r1", SyncPayloads.PlayAnswer.RECEIVED),
                SyncPayloads.PlayAnswer("r1", SyncPayloads.PlayAnswer.PLAYING),
            )

        assertEquals(listOf(SyncPayloads.PlayAnswer.RECEIVED, SyncPayloads.PlayAnswer.PLAYING), heard.map { it.result })
    }

    @Test
    fun `an answer nobody waits for, a late one and an unknown result do nothing`() {
        assertFalse(PlayRequests.receiveAnswer(SyncPayloads.PlayAnswer("r9", SyncPayloads.PlayAnswer.PLAYING)))
        PlayRequests.ask("r1")
        assertFalse(PlayRequests.receiveAnswer(SyncPayloads.PlayAnswer("r1", "maybe")))
        PlayRequests.stopWaiting("r1")
        assertFalse(PlayRequests.receiveAnswer(SyncPayloads.PlayAnswer("r1", SyncPayloads.PlayAnswer.PLAYING)))
    }

    @Test
    fun `clear drops the held request`() {
        receive(request())
        PlayRequests.clear()

        assertNull(PlayRequests.incoming.value)
    }
}
