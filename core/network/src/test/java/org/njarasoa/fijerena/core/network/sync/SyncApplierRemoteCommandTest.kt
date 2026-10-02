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
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsSyncDao
import org.njarasoa.fijerena.core.network.xtream.db.SyncVersionDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * A remote Stop fires only for this device and the session playing right now, once, and writes to
 * neither database. See docs/plans/20261001_live-sync-now-playing-plan.md → Phase 4.3.
 */
class SyncApplierRemoteCommandTest {
    private val settingsSync = mockk<SettingsSyncDao>(relaxed = true)
    private val versions = mockk<SyncVersionDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)
    private val xtreamDb = mockk<XtreamDatabase>(relaxed = true)

    private var playing: String? = "session-now"

    private fun applier() = SyncApplier(mockk<Context>(relaxed = true), thisDeviceId = { "tv" }, playingSessionId = { playing })

    private fun command(
        sessionId: String,
        target: String = "tv",
        command: String = SyncPayloads.RemoteCommand.STOP,
        hlc: Long = 7,
    ) = SyncRecord(
        SyncKey(SyncKind.SHARED, "", SyncKind.REMOTE_COMMAND, target),
        hlc = hlc,
        payload = SyncPayloads.encode(SyncPayloads.RemoteCommand(command, sessionId, fromDeviceName = "Pixel 8", issuedBy = "phone")),
    )

    /** Applies each batch in turn and returns every Stop emitted meanwhile. */
    private fun stopsWhileApplying(vararg batches: List<SyncRecord>): List<RemoteCommands.Stop> =
        runBlocking {
            val received = mutableListOf<RemoteCommands.Stop>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { RemoteCommands.stops.collect { received += it } }
            batches.forEach { applier().apply(it) }
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
        RemoteCommands.reset()
    }

    @After
    fun tearDown() {
        RemoteCommands.reset()
        unmockkAll()
    }

    @Test
    fun `a stop for the session playing now fires once, and writes nothing`() {
        val stops = stopsWhileApplying(listOf(command("session-now")))

        assertEquals(listOf(RemoteCommands.Stop("session-now", "Pixel 8")), stops)
        verify { settingsSync wasNot io.mockk.Called }
        verify { versions wasNot io.mockk.Called }
    }

    @Test
    fun `the same record replayed does not fire twice`() {
        // A later pull, or a resync from 0, brings the same command back while still playing.
        val stops = stopsWhileApplying(listOf(command("session-now")), listOf(command("session-now", hlc = 8)))

        assertEquals(1, stops.size)
    }

    @Test
    fun `a stop for an earlier session never fires`() {
        // Re-read after a resync: that playback ended long ago, a new one is on.
        val stops = stopsWhileApplying(listOf(command("session-before")))

        assertEquals(emptyList<RemoteCommands.Stop>(), stops)
    }

    @Test
    fun `nothing playing, nothing fires`() {
        playing = null

        assertEquals(emptyList<RemoteCommands.Stop>(), stopsWhileApplying(listOf(command("session-now"))))
    }

    @Test
    fun `an unknown command is ignored`() {
        val stops = stopsWhileApplying(listOf(command("session-now", command = "eject")))

        assertEquals(emptyList<RemoteCommands.Stop>(), stops)
    }

    @Test
    fun `a command for another device is ignored`() {
        val result = runBlocking { applier().apply(listOf(command("session-now", target = "shield"))) }

        assertEquals(0, result.applied)
        assertEquals(1, result.skipped)
        assertEquals(emptyList<RemoteCommands.Stop>(), stopsWhileApplying(listOf(command("session-now", target = "shield"))))
        verify { settingsSync wasNot io.mockk.Called }
        verify { versions wasNot io.mockk.Called }
    }

    @Test
    fun `a payload that does not decode is skipped, not deferred`() {
        val unreadable = SyncRecord(SyncKey(SyncKind.SHARED, "", SyncKind.REMOTE_COMMAND, "tv"), hlc = 7, payload = "{\"command\":\"stop\"}")

        val result = runBlocking { applier().apply(listOf(unreadable)) }

        assertEquals(0, result.applied)
        assertEquals(1, result.skipped)
        assertEquals(0, result.deferred.size)
    }

    @Test
    fun `only the first claim on a stop wins`() {
        val stop = RemoteCommands.Stop("session-now", "Pixel 8")

        assertEquals(listOf(true, false), listOf(RemoteCommands.claim(stop), RemoteCommands.claim(stop)))
    }
}
