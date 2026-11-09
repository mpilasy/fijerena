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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsSyncDao
import org.njarasoa.fijerena.core.network.xtream.db.SyncVersionDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * Requests and answers between a phone and a TV: a request applies only on the TV it names, an
 * answer reaches the device that waits for it, and nothing is written to either database. See the
 * send to TV plan → Phase 1.
 */
class SyncApplierPlayRequestTest {
    private val settingsSync = mockk<SettingsSyncDao>(relaxed = true)
    private val versions = mockk<SyncVersionDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)
    private val xtreamDb = mockk<XtreamDatabase>(relaxed = true)

    private fun applier(me: String) = SyncApplier(mockk<Context>(relaxed = true), thisDeviceId = { me }, playingSessionId = { null })

    private fun request(
        target: String = "tv",
        open: Boolean = true,
        hlc: Long = 7,
    ) = SyncRecord(
        PlayRequests.requestKey(target),
        hlc = hlc,
        payload =
            SyncPayloads.encode(
                SyncPayloads.PlayRequest(
                    "r1",
                    profileId = "anna",
                    providerKey = "pk",
                    streamId = "42",
                    open = open,
                    sentAt = System.currentTimeMillis(),
                ),
            ),
    )

    private fun answer(result: String = SyncPayloads.PlayAnswer.PLAYING) =
        SyncRecord(
            PlayRequests.answerKey("tv"),
            hlc = 9,
            payload = SyncPayloads.encode(SyncPayloads.PlayAnswer("r1", result, "Living Room")),
        )

    private fun applyOn(
        me: String,
        vararg records: SyncRecord,
    ) = runBlocking { applier(me).apply(records.toList()) }

    private fun answersWhileApplying(
        me: String,
        vararg records: SyncRecord,
    ): List<SyncPayloads.PlayAnswer> =
        runBlocking {
            val received = mutableListOf<SyncPayloads.PlayAnswer>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { PlayRequests.answers.collect { received += it } }
            applier(me).apply(records.toList())
            yield()
            collector.cancel()
            received
        }

    @Before
    fun setup() {
        every { settingsDb.settingsSyncDao() } returns settingsSync
        every { xtreamDb.syncVersionDao() } returns versions
        mockkObject(SettingsDatabase.Companion, XtreamDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns settingsDb
        every { XtreamDatabase.getInstance(any()) } returns xtreamDb
        PlayRequests.reset()
    }

    @After
    fun tearDown() {
        PlayRequests.reset()
        unmockkAll()
    }

    @Test
    fun `a request applies on the TV it names, and writes nothing`() {
        applyOn("tv", request())

        assertEquals(
            "anna",
            PlayRequests.incoming.value
                ?.request
                ?.profileId,
        )
        verify { settingsSync wasNot io.mockk.Called }
        verify { versions wasNot io.mockk.Called }
    }

    @Test
    fun `a request for another device does nothing`() {
        applyOn("other", request(target = "tv"))

        assertNull(PlayRequests.incoming.value)
    }

    @Test
    fun `a deleted or unreadable record is skipped, not deferred`() {
        val deleted = SyncRecord(PlayRequests.requestKey("tv"), hlc = 1, deleted = true)
        val unreadable = SyncRecord(PlayRequests.requestKey("tv"), hlc = 2, payload = "not json")

        val result = runBlocking { applier("tv").apply(listOf(deleted, unreadable)) }

        assertEquals(0, result.deferred.size)
        assertNull(PlayRequests.incoming.value)
    }

    @Test
    fun `a closed request closes the banner`() {
        applyOn("tv", request())
        applyOn("tv", request(open = false, hlc = 8))

        assertNull(PlayRequests.incoming.value)
    }

    @Test
    fun `an answer reaches the sender`() {
        PlayRequests.ask("r1")

        assertEquals(
            listOf(SyncPayloads.PlayAnswer("r1", SyncPayloads.PlayAnswer.PLAYING, "Living Room")),
            answersWhileApplying("phone", answer()),
        )
    }

    @Test
    fun `an answer on the TV that gave it does nothing`() {
        assertTrue(answersWhileApplying("tv", answer()).isEmpty())
    }

    @Test
    fun `a build without the feature skips the kinds, a later field is ignored`() {
        val unknownKind = SyncRecord(SyncKey(SyncKind.SHARED, "", "future_kind", "tv"), hlc = 3, payload = "{}")
        val laterField =
            SyncRecord(
                PlayRequests.requestKey("tv"),
                hlc = 4,
                payload =
                    """{"requestId":"r2","profileId":"anna","providerKey":"pk","streamId":"42","sentAt":${System.currentTimeMillis()},"added":1}""",
            )

        applyOn("tv", unknownKind, laterField)

        assertEquals(
            "r2",
            PlayRequests.incoming.value
                ?.request
                ?.requestId,
        )
    }
}
