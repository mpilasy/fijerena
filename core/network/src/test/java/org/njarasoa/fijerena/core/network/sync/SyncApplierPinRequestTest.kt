package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsSyncDao
import org.njarasoa.fijerena.core.network.xtream.db.SyncVersionDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * PIN requests and answers between devices: a request shows on the other devices while it is open
 * and recent, an answer reaches only the device that waits for it, once, and nothing is written to
 * either database. See the PIN on the phone plan → Phase 1.
 */
class SyncApplierPinRequestTest {
    private val settingsSync = mockk<SettingsSyncDao>(relaxed = true)
    private val versions = mockk<SyncVersionDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)
    private val xtreamDb = mockk<XtreamDatabase>(relaxed = true)

    private fun applier(me: String) = SyncApplier(mockk<Context>(relaxed = true), thisDeviceId = { me }, playingSessionId = { null })

    private fun request(
        requestId: String = "r1",
        open: Boolean = true,
        sentAt: Long = System.currentTimeMillis(),
        hlc: Long = 7,
        from: String = "tv",
    ) = SyncRecord(
        PinRequests.requestKey(from),
        hlc = hlc,
        payload =
            SyncPayloads.encode(
                SyncPayloads.PinRequest(
                    requestId,
                    profileId = "anna",
                    profileName = "Anna",
                    fromDeviceName = "Living Room",
                    open = open,
                    sentAt = sentAt,
                ),
            ),
    )

    private fun answer(
        requestId: String = "r1",
        result: String = SyncPayloads.PinAnswer.UNLOCKED,
        target: String = "tv",
        hlc: Long = 9,
    ) = SyncRecord(
        PinRequests.answerKey(target),
        hlc = hlc,
        payload = SyncPayloads.encode(SyncPayloads.PinAnswer(requestId, result, fromDeviceName = "Pixel 8")),
    )

    /** Applies each batch on [me] in turn and returns every answer emitted meanwhile. */
    private fun answersWhileApplying(
        me: String,
        vararg batches: List<SyncRecord>,
    ): List<SyncPayloads.PinAnswer> =
        runBlocking {
            val received = mutableListOf<SyncPayloads.PinAnswer>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { PinRequests.answers.collect { received += it } }
            batches.forEach { applier(me).apply(it) }
            yield()
            collector.cancel()
            received
        }

    private fun applyOn(
        me: String,
        vararg records: SyncRecord,
    ) = runBlocking { applier(me).apply(records.toList()) }

    @Before
    fun setup() {
        every { settingsDb.settingsSyncDao() } returns settingsSync
        every { xtreamDb.syncVersionDao() } returns versions
        mockkObject(SettingsDatabase.Companion, XtreamDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns settingsDb
        every { XtreamDatabase.getInstance(any()) } returns xtreamDb
        PinRequests.reset()
    }

    @After
    fun tearDown() {
        PinRequests.reset()
        unmockkAll()
    }

    @Test
    fun `an open request from another device is held, and writes nothing`() {
        applyOn("phone", request())

        assertEquals(
            "anna",
            PinRequests.incoming.value["tv"]
                ?.request
                ?.profileId,
        )
        verify { settingsSync wasNot io.mockk.Called }
        verify { versions wasNot io.mockk.Called }
    }

    @Test
    fun `a device never holds its own request`() {
        applyOn("tv", request())

        assertTrue(PinRequests.incoming.value.isEmpty())
    }

    @Test
    fun `a closed request removes the open one and is never shown again`() {
        applyOn("phone", request())
        applyOn("phone", request(open = false, hlc = 8))
        applyOn("phone", request(hlc = 9))

        assertTrue(PinRequests.incoming.value.isEmpty())
    }

    @Test
    fun `a request more than five minutes old is ignored`() {
        applyOn("phone", request(sentAt = System.currentTimeMillis() - PinRequests.MAX_AGE_MS - 60_000))

        assertTrue(PinRequests.incoming.value.isEmpty())
    }

    @Test
    fun `an older record does not replace a newer one`() {
        applyOn("phone", request(requestId = "r2", hlc = 10))
        applyOn("phone", request(requestId = "r1", hlc = 7))

        assertEquals(
            "r2",
            PinRequests.incoming.value["tv"]
                ?.request
                ?.requestId,
        )
    }

    @Test
    fun `a request answered here is not shown again`() {
        applyOn("phone", request())
        PinRequests.done("r1")
        applyOn("phone", request(hlc = 8))

        assertTrue(PinRequests.incoming.value.isEmpty())
    }

    @Test
    fun `an answer reaches the device that waits for it, once`() {
        PinRequests.ask("r1")

        val answers = answersWhileApplying("tv", listOf(answer()), listOf(answer(hlc = 10)))

        assertEquals(listOf(SyncPayloads.PinAnswer("r1", SyncPayloads.PinAnswer.UNLOCKED, "Pixel 8")), answers)
    }

    @Test
    fun `an answer for another device or a request nobody waits for does nothing`() {
        PinRequests.ask("r1")

        assertTrue(answersWhileApplying("phone", listOf(answer(target = "other"))).isEmpty())
        assertTrue(answersWhileApplying("tv", listOf(answer(requestId = "r9"))).isEmpty())
    }

    @Test
    fun `a late answer after the device stopped waiting does nothing`() {
        PinRequests.ask("r1")
        PinRequests.stopWaiting("r1")

        assertTrue(answersWhileApplying("tv", listOf(answer())).isEmpty())
    }

    @Test
    fun `an answer with an unknown result does nothing`() {
        PinRequests.ask("r1")

        assertFalse(PinRequests.receiveAnswer(SyncPayloads.PinAnswer("r1", "maybe")))
    }
}
