package org.njarasoa.fijerena.core.network.sync

import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.AppSettings

/**
 * The move on the device that starts it (the sync server move plan → Design, steps 0 to 8), and
 * the old-server watch, against two in-memory servers ([FakeSyncServer]).
 */
class SyncServerMoveTest {
    private val kit = SyncMoveTestKit()
    private val old = FakeSyncServer("https://old.test")
    private val new = FakeSyncServer("https://new.test")

    // A: the device that moves the group. B and C: the other devices of the group.
    private val a = kit.Device(old, "Phone")
    private val b = old.addDevice("bravia")
    private val c = old.addDevice("shield")

    /** The records that must reach the new server, as the old server holds them. */
    private val kept = mutableListOf<SyncWire.Record>()

    @Before
    fun setup() {
        kit.mockEngineParts()
        a.knowsBuild(b.deviceId, DeviceBuild.FEATURE_PLAY_REQUEST, DeviceBuild.FEATURE_SERVER_MOVE)
        a.knowsBuild(c.deviceId, DeviceBuild.FEATURE_SERVER_MOVE)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** Old server data: [count] favourites and a deletion (copied), and records that the copy skips. */
    private fun seedOldServer(count: Int = 3) {
        repeat(count) { i -> keep(kit.item("film$i", 1_700_000_000_000L + i)) }
        keep(kit.item("deleted", 1_700_000_000_500L, deleted = true))
        keep(kit.setting("parental_blocked", "[]", 1_700_000_000_600L))
        // Skipped (SM-Q3): volatile, and keyed by an old server device id.
        old.put(kit.seal(kit.volatile(SyncKind.NOW_PLAYING, b.deviceId, 1_700_000_000_700L)))
        old.put(kit.seal(kit.volatile(SyncKind.PLAY_REQUEST, b.deviceId, 1_700_000_000_701L)))
        old.put(kit.seal(kit.setting(AppSettings.deviceBuildSettingKey(b.deviceId), "{}", 1_700_000_000_702L)))
        old.put(kit.seal(kit.setting(AppSettings.deviceReportSettingKey(b.deviceId), "{}", 1_700_000_000_703L)))
        old.put(kit.seal(kit.setting(AppSettings.deviceNameSettingKey(b.deviceId), "Living Room TV", 1_700_000_000_704L)))
    }

    private fun keep(record: SyncRecord) {
        val wire = kit.seal(record)
        old.put(wire)
        kept += wire
    }

    private fun move(
        api: SyncApi = FakeSyncServer.api(old, new),
        secret: String? = null,
        states: MutableList<MoveState> = mutableListOf(),
    ) = runBlocking { a.mover(api).run(new.url, secret) { states += it } }

    /** The copied records, as the new server holds them: key, tags, clock value, deletion flag and sealed payload. */
    private fun shape(records: List<SyncWire.Record>) = records.map { it.copy(seq = null) }.sortedBy { it.key }

    // --- Step 0 ---

    @Test
    fun `a device without server_move blocks the move, named as the group calls it`() =
        runBlocking {
            a.knowsBuild(b.deviceId, DeviceBuild.FEATURE_PLAY_REQUEST)
            a.knowsName(b.deviceId, "Living Room TV")
            // D reported no build; E is revoked, so it does not count.
            old.addDevice("bedroom")
            val e = old.addDevice("gone")
            old.revoke(a.store.link!!.deviceToken, e.deviceId)

            assertEquals(MoveBlock.UpdateFirst(listOf("Living Room TV", "bedroom")), a.mover(FakeSyncServer.api(old, new)).moveBlock())
        }

    @Test
    fun `with server_move on every device, the move is not blocked`() =
        runBlocking {
            assertNull(a.mover(FakeSyncServer.api(old, new)).moveBlock())
        }

    @Test
    fun `an old-server record blocks the move`() =
        runBlocking {
            a.store.previousServerRecord =
                SyncServerMove.PreviousServerRecord(1, SyncServerMove.PreviousServer("https://older.test", "x", "t"))

            assertEquals(MoveBlock.OldServerLeft, a.mover(FakeSyncServer.api(old, new)).moveBlock())
        }

    @Test
    fun `a blocked move does not start`() {
        a.knowsBuild(c.deviceId)
        try {
            move()
            fail("a blocked move must not start")
        } catch (e: IllegalStateException) {
            // expected
        }
        assertEquals(0, new.accountsCreated)
        assertNull(a.store.moveProgress)
    }

    // --- Step 1 ---

    @Test
    fun `a new server without the move feature is refused`() {
        val older = FakeSyncServer("https://new.test", features = emptyList())
        try {
            move(FakeSyncServer.api(old, older))
            fail("a server without move must be refused")
        } catch (e: SyncApiException) {
            assertEquals(SyncApiException.INCOMPATIBLE, e.status)
        }
        assertEquals(0, older.accountsCreated)
        assertNull(a.store.moveProgress)
        assertEquals(old.url, a.store.link?.serverUrl)
    }

    @Test
    fun `the server in use is refused as the new server`() =
        runBlocking {
            try {
                a.mover(FakeSyncServer.api(old, new)).checkTarget("${old.url}/")
                fail("the server in use must be refused")
            } catch (e: SyncApiException) {
                assertEquals(SyncApiException.INCOMPATIBLE, e.status)
            }
        }

    @Test
    fun `the setup secret goes to the new server`() {
        val guarded = FakeSyncServer("https://new.test", setupSecret = "s3cret")
        val api = FakeSyncServer.api(old, guarded)
        assertTrue(runBlocking { a.mover(api).checkTarget(guarded.url) }.setupSecretRequired)
        try {
            move(api, secret = "wrong")
            fail("a wrong secret must be refused")
        } catch (e: SyncApiException) {
            assertEquals(403, e.status)
        }
        assertNull(a.store.moveProgress)

        move(api, secret = "s3cret")

        assertEquals(1, guarded.accountsCreated)
        assertEquals(guarded.url, a.store.link?.serverUrl)
    }

    // --- Steps 2 to 8 ---

    @Test
    fun `the move copies the group's records as they are, and switches this device`() {
        seedOldServer(count = 1_100)
        val late = kit.seal(kit.item("written during the copy", 1_700_000_001_000L))
        var lateWritten = false
        val api =
            FakeSyncServer.api(old, new) { call, url ->
                // Another device writes to the old server after the snapshot, before the move record.
                if (call == "push" && url == old.url && !lateWritten) {
                    old.put(late)
                    lateWritten = true
                }
            }
        val states = mutableListOf<MoveState>()
        val snapshotSize = old.records.size
        a.store.cursor = 42

        move(api, states = states)

        assertEquals(shape(kept + late), shape(new.records.filterNot { kit.open(it)!!.key.itemId == SyncServerMove.KEY_PREVIOUS_SERVER }))
        assertTrue(new.records.none { it.cascade != null })
        // The move record: the new address and one code per other device, each code good on the new server.
        val move = kit.moveRecord(old)!!
        assertEquals(new.url, move.serverUrl)
        assertEquals(setOf(b.deviceId, c.deviceId), move.codes.keys)
        assertNotNull(new.pair(move.codes.getValue(b.deviceId), "bravia"))
        // The old-server record holds this device's old id and token.
        val previous = kit.previousRecord(new)!!
        assertEquals(
            SyncServerMove.PreviousServer(old.url, a.id, "token-${a.id}"),
            SyncPayloads.json.decodeFromString(SyncServerMove.PreviousServer.serializer(), kit.valueOf(previous)),
        )
        assertEquals(old.url, a.store.previousServer?.serverUrl)
        // The switch: the new link from 0, the same key, still seeded; this device stays on the old server's list.
        val link = a.store.link!!
        assertEquals(new.url, link.serverUrl)
        assertTrue(link.accountKey.contentEquals(kit.accountKey))
        assertEquals(0L, a.store.cursor)
        assertTrue(a.store.seeded)
        assertNull(a.store.moveProgress)
        assertFalse(old.devices.first { it.id == a.id }.revoked)
        assertEquals(1, new.accountsCreated)
        // The stages, and the copy's counts against the old server's total.
        assertEquals(MoveState.Done, states.last())
        val stages = states.filterIsInstance<MoveState.Running>().map { it.stage }.distinct()
        assertEquals(MoveState.Stage.entries, stages)
        assertTrue(states.contains(MoveState.Running(MoveState.Stage.COPYING, 1_000, snapshotSize)))
    }

    @Test
    fun `a move that fails at any call continues where it stopped, with one account`() {
        var calls = 0
        seedOldServer(count = 1_100)
        val seed = old.records
        move(FakeSyncServer.api(old, new) { _, _ -> calls++ })
        val clean = calls
        var failures = 0

        for (failAt in 1..clean) {
            val oldServer = FakeSyncServer(old.url)
            val newServer = FakeSyncServer(new.url)
            val device = kit.Device(oldServer, "Phone")
            val others = listOf(oldServer.addDevice("bravia"), oldServer.addDevice("shield"))
            others.forEach { device.knowsBuild(it.deviceId, DeviceBuild.FEATURE_SERVER_MOVE) }
            seed.forEach { oldServer.put(it.copy(seq = null)) }
            var call = 0
            val failing =
                FakeSyncServer.api(oldServer, newServer) { name, _ ->
                    if (++call == failAt) throw SyncApiException(0, "connection reset at $name")
                }

            val failed =
                try {
                    runBlocking { device.mover(failing).run(newServer.url, null) {} }
                    false
                } catch (e: SyncApiException) {
                    true
                }
            if (failed) {
                failures++
                runBlocking { device.mover(FakeSyncServer.api(oldServer, newServer)).run(newServer.url, null) {} }
            }

            assertEquals("call $failAt", 1, newServer.accountsCreated)
            assertEquals("call $failAt", newServer.url, device.store.link?.serverUrl)
            assertEquals(
                "call $failAt",
                shape(kept),
                shape(
                    newServer.records.filterNot {
                        kit.open(it)!!.key.itemId ==
                            SyncServerMove.KEY_PREVIOUS_SERVER
                    },
                ),
            )
            val codes = kit.moveRecord(oldServer)!!.codes
            assertEquals("call $failAt", others.map { it.deviceId }.toSet(), codes.keys)
            codes.values.forEach { newServer.pair(it, "device") }
            assertEquals("call $failAt", 1, oldServer.records.count { kit.open(it)?.key?.itemId == SyncServerMove.KEY_MOVE })
            assertNotNull("call $failAt", kit.previousRecord(newServer))
        }
        assertEquals(clean, failures)
    }

    // --- The old server after the move ---

    @Test
    fun `the purge waits for every other device, then deletes the old account and the old-server record`() =
        runBlocking {
            a.knowsName(c.deviceId, "Bedroom TV")
            move()
            val mover = a.mover(FakeSyncServer.api(old, new))

            assertEquals(OldServerState.NotMoved(listOf("bravia", "Bedroom TV")), mover.oldServerState())
            try {
                mover.purgeOldServer()
                fail("the purge must wait for the other devices")
            } catch (e: SyncApiException) {
                assertEquals(409, e.status)
            }
            assertFalse(old.deleted)

            // B and C moved: they removed themselves from the old server.
            old.revoke("token-${b.deviceId}", b.deviceId)
            old.revoke("token-${c.deviceId}", c.deviceId)
            assertEquals(OldServerState.Ready, mover.oldServerState())
            mover.purgeOldServer()

            assertTrue(old.deleted)
            assertTrue(kit.previousRecord(new)!!.deleted)
            assertNull(a.store.previousServer)
            assertNull(mover.moveBlock())
        }

    @Test
    fun `an old server without purge shows the hint`() =
        runBlocking {
            move()
            val olderOld = FakeSyncServer(old.url, features = emptyList())

            assertEquals(OldServerState.NoPurge, a.mover(FakeSyncServer.api(olderOld, new)).oldServerState())
        }

    @Test
    fun `Forget Old Server deletes the old-server record and purges nothing`() =
        runBlocking {
            move()
            old.reachable = false
            val mover = a.mover(FakeSyncServer.api(old, new))

            assertTrue(mover.oldServerState() is OldServerState.Unreachable)
            mover.forgetOldServer()

            assertTrue(kit.previousRecord(new)!!.deleted)
            assertNull(a.store.previousServer)
            assertFalse(old.deleted)
        }

    @Test
    fun `a device that did not move keeps the old-server record it pulls, and drops it on the deletion`() =
        runBlocking {
            seedOldServer()
            move()
            val api = FakeSyncServer.api(old, new)
            // D: on the new server, never on the old one (added after the move).
            val d = kit.Device(new, "Tablet")

            d.engine(api).syncNow()

            assertEquals(SyncServerMove.PreviousServer(old.url, a.id, "token-${a.id}"), d.store.previousServer)
            assertTrue(kit.applied.isNotEmpty())
            assertTrue(kit.applied.none { SyncServerMove.isMoveKey(it.key) })

            a.mover(api).forgetOldServer()
            d.engine(api).syncNow()

            assertNull(d.store.previousServer)
        }

    @Test
    fun `no pass runs while a move started here is not finished`() =
        runBlocking {
            seedOldServer()
            val api =
                FakeSyncServer.api(old, new) { call, url ->
                    if (call == "push" && url == old.url) throw SyncApiException(0, "connection reset")
                }
            try {
                a.mover(api).run(new.url, null) {}
                fail("the move must stop")
            } catch (e: SyncApiException) {
                // expected
            }

            assertNull(a.engine(FakeSyncServer.api(old, new)).syncNow())
            assertTrue(kit.applied.isEmpty())
        }

    @Test
    fun `a pass waits while the move holds the engine`() =
        runBlocking {
            val engine = a.engine(FakeSyncServer.api(old, new))
            val moveEnds = CompletableDeferred<Unit>()
            val moving = launch { engine.exclusive { moveEnds.await() } }
            yield()
            val pass = async { engine.syncNow() }
            delay(50)

            assertFalse(pass.isCompleted)
            moveEnds.complete(Unit)
            assertNotNull(pass.await())
            moving.join()
        }

    @Test
    fun `the copy filter`() {
        assertFalse(SyncServerMove.copies(kit.volatile(SyncKind.REMOTE_COMMAND, "d", 1)))
        assertFalse(SyncServerMove.copies(kit.setting(AppSettings.deviceBuildSettingKey("d"), "{}", 1)))
        assertFalse(SyncServerMove.copies(kit.setting(AppSettings.deviceReportSettingKey("d"), "{}", 1)))
        assertFalse(SyncServerMove.copies(kit.setting(AppSettings.deviceNameSettingKey("d"), "TV", 1)))
        assertFalse(SyncServerMove.copies(kit.setting(SyncServerMove.KEY_MOVE, "{}", 1)))
        assertFalse(SyncServerMove.copies(kit.setting(SyncServerMove.KEY_PREVIOUS_SERVER, "{}", 1)))
        assertTrue(SyncServerMove.copies(kit.setting(AppSettings.profileSourceSettingKey("p"), "true", 1)))
        assertTrue(SyncServerMove.copies(kit.item("x", 1, deleted = true)))
    }
}
