package org.njarasoa.fijerena.core.network.sync

import io.mockk.coEvery
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The move on the other devices (the sync server move plan → The move, on the other devices, and
 * Dormant devices): a device that pulls the move record from the old server joins the new server
 * with its code, copies what came after the move record, removes itself from the old server and
 * switches, against two in-memory servers ([FakeSyncServer]).
 */
class SyncServerMoveFollowTest {
    private val kit = SyncMoveTestKit()

    /** Two servers and a group of three devices, A, B and C, that A moved to the new server. */
    private inner class World {
        val old = FakeSyncServer("https://old.test")
        val new = FakeSyncServer("https://new.test")
        val a = kit.Device(old, "Phone")
        val b = kit.Device(old, "bravia")
        val c = kit.Device(old, "shield")

        init {
            listOf(b, c).forEach { a.knowsBuild(it.id, DeviceBuild.FEATURE_SERVER_MOVE) }
            old.put(kit.seal(kit.item("film", T)))
            // Deleted 30 days before the move: the old server still has the tombstone.
            old.put(kit.seal(kit.item("deleted lately", T + 1, deleted = true)))
            runBlocking { a.mover(api()).run(new.url, null) {} }
        }

        fun api(before: (String, String) -> Unit = { _, _ -> }) = FakeSyncServer.api(old, new, before = before)

        fun keys(server: FakeSyncServer) = kit.data(server).map { it.key.itemId }
    }

    @Before
    fun setup() {
        kit.mockEngineParts()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `two devices read the move record and move to the new server`() {
        val w = World()
        w.b.knowsName(w.b.id, "Living Room TV")

        for (device in listOf(w.b, w.c)) {
            val outcome = runBlocking { device.engine(w.api()).syncNow() }!!

            assertTrue(outcome.serverSwitched)
            assertEquals(w.new.url, device.store.link?.serverUrl)
            assertTrue(
                device.store.link!!
                    .accountKey
                    .contentEquals(kit.accountKey),
            )
            assertTrue(
                w.old.devices
                    .first { it.id == device.id }
                    .revoked,
            )
            assertNull(device.store.moveFollow)
            // It pulled from the new server: the old-server record is here too now.
            assertEquals(w.new.head, device.store.cursor)
            assertEquals(w.old.url, device.store.previousServer?.serverUrl)
        }
        // Each device joined with its current name: the group's name, else the old server's.
        assertEquals(listOf("Phone", "Living Room TV", "shield"), w.new.devices.map { it.name })
        assertEquals(OldServerState.Ready, runBlocking { w.a.mover(w.api()).oldServerState() })
        runBlocking { w.a.mover(w.api()).purgeOldServer() }
        assertTrue(w.old.deleted)
    }

    @Test
    fun `records written to the old server after the move record reach the new server`() {
        val w = World()
        // C has not moved yet and writes to the old server.
        w.old.put(kit.seal(kit.item("written late", T + 5)))
        w.old.put(kit.seal(kit.volatile(SyncKind.NOW_PLAYING, w.c.id, T + 6)))

        runBlocking { w.b.engine(w.api()).syncNow() }
        runBlocking { w.c.engine(w.api()).syncNow() }

        assertEquals(1, w.keys(w.new).count { it == "written late" })
        assertTrue(kit.data(w.new).none { it.key.kind == SyncKind.NOW_PLAYING })
    }

    @Test
    fun `a device that is not in the move record shows where the group moved`() {
        val w = World()
        // D joined the old server after the move started.
        val d = kit.Device(w.old, "tablet")

        val outcome = runBlocking { d.engine(w.api()).syncNow() }!!

        assertFalse(outcome.serverSwitched)
        assertEquals(w.new.url, d.store.movedAway)
        assertEquals(w.old.url, d.store.link?.serverUrl)
        assertEquals(MoveBlock.MoveInProgress, runBlocking { d.mover(w.api()).moveBlock() })
    }

    @Test
    fun `a moving device sends its pending changes to the new server, and not all its data again`() {
        val w = World()
        kit.pendingChange(kit.item("changed while closed", T + 10))

        runBlocking { w.b.engine(w.api()).syncNow() }

        assertTrue("changed while closed" in w.keys(w.new))
        assertFalse("changed while closed" in w.keys(w.old))
        assertEquals(0, kit.seeds)
        assertTrue(w.b.store.seeded)
    }

    @Test
    fun `a dormant device does not bring back an item deleted long before the copy`() {
        val w = World()
        // C still holds "ancient", deleted on another device more than 90 days before the move: no
        // server has its tombstone. Queueing all of C's local data would send it again.
        coEvery { anyConstructed<LocalRecords>().seedEverything() } answers { kit.pendingChange(kit.item("ancient", T - 1)) }
        kit.applied.clear()

        runBlocking { w.c.engine(w.api()).syncNow() }

        assertFalse("ancient" in w.keys(w.new))
        // The deletion younger than 90 days is in the copy, and C applies it on its first pull.
        assertTrue(kit.applied.any { it.key.itemId == "deleted lately" && it.deleted })
    }

    @Test
    fun `a device that stops part way continues the move and joins the new server one time`() {
        var calls = 0
        World().let { w ->
            w.old.put(kit.seal(kit.item("written late", T + 5)))
            runBlocking { w.b.engine(w.api { _, _ -> calls++ }).syncNow() }
        }
        val clean = calls

        for (failAt in 1..clean) {
            val w = World()
            w.old.put(kit.seal(kit.item("written late", T + 5)))
            var call = 0
            val failing = w.api { name, _ -> if (++call == failAt) throw SyncApiException(0, "connection reset at $name") }

            val failed =
                try {
                    runBlocking { w.b.engine(failing).syncNow() }
                    false
                } catch (e: SyncApiException) {
                    true
                }
            if (failed) runBlocking { w.b.engine(w.api()).syncNow() }

            assertEquals(
                "call $failAt",
                w.new.url,
                w.b.store.link
                    ?.serverUrl,
            )
            assertEquals("call $failAt", 1, w.new.devices.count { it.name == "bravia" })
            assertTrue(
                "call $failAt",
                w.old.devices
                    .first { it.id == w.b.id }
                    .revoked,
            )
            assertTrue("call $failAt", "written late" in w.keys(w.new))
            assertNull("call $failAt", w.b.store.moveFollow)
        }
    }

    private companion object {
        const val T = 1_700_000_000_000L
    }
}
