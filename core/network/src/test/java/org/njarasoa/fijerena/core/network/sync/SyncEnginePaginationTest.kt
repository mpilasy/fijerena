package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * [SyncEngine.syncNow] against a fake server ([SyncApi] mock), a fake applier and an in-memory
 * [SyncAccountStore].
 *
 * F-09: the cursor was saved per page but the deferred records only after the loop, so a later
 * page failing lost what earlier pages deferred — the cursor had already moved past them.
 * F-08 (engine side): a record this device can't read must not hold the cursor back.
 * F-22 (client side): a record sealed over the server's limit is dropped, not sent with the rest.
 * See docs/plans/20261001_rock-solid-stability-resilience-plan.md.
 */
class SyncEnginePaginationTest {
    private val accountKey = AccountKeyCrypto.newAccountKey()
    private val crypto = AccountKeyCrypto(accountKey)
    private val api = mockk<SyncApi>()
    private val store = mockk<SyncAccountStore>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    // The store's state, as the engine leaves it.
    private var cursor = 0L
    private var deferred = emptySet<String>()

    /** Every batch the applier was given, in order. */
    private val applied = mutableListOf<List<SyncRecord>>()

    /** Items the applier defers (their provider isn't here yet). */
    private val waitingItems = mutableSetOf("waits")

    private fun record(
        item: String,
        hlc: Long,
    ) = SyncRecord(SyncKey("profile", "provider", SyncKind.FAVORITE_STREAM, item, "MOVIES"), hlc, payload = """{"name":"$item"}""")

    private fun wire(
        item: String,
        seq: Long,
    ) = SyncCodec.encode(record(item, seq * 10), crypto).copy(seq = seq)

    @Before
    fun setup() {
        mockkObject(SettingsDatabase.Companion, XtreamDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns mockk(relaxed = true)
        every { XtreamDatabase.getInstance(any()) } returns mockk(relaxed = true)
        every { store.link } returns SyncAccountStore.Link("https://sync.test", "acc", "dev", "token", accountKey)
        every { store.seeded } returns true
        every { store.cursor } answers { cursor }
        every { store.deferred } answers { deferred }
        every { store.cursor = any() } answers { cursor = firstArg() }
        every { store.deferred = any() } answers { deferred = firstArg() }
        every { store.savePullProgress(any(), any()) } answers {
            cursor = firstArg()
            deferred = secondArg()
        }

        mockkConstructor(SyncApplier::class)
        coEvery { anyConstructed<SyncApplier>().apply(any()) } answers {
            val batch = firstArg<List<SyncRecord>>()
            applied += batch
            val waiting = batch.filter { it.key.itemId in waitingItems }
            SyncApplier.Result(batch.size - waiting.size, 0, waiting, emptySet(), emptySet(), false)
        }
        mockkConstructor(LocalRecords::class)
        coEvery { anyConstructed<LocalRecords>().pending(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun engine() = SyncEngine(mockk<Context>(relaxed = true), api, store)

    @Test
    fun `records deferred on an earlier page survive a later page failing`() =
        runBlocking {
            coEvery { api.pull(any(), any(), 0L, any()) } returns
                SyncWire.PullResponse(head = 4, more = true, records = listOf(wire("waits", 1), wire("ok", 2)))
            coEvery { api.pull(any(), any(), 2L, any()) } throws SyncApiException(0, "connection reset")

            try {
                engine().syncNow()
                fail("the failing page must fail the pass")
            } catch (e: SyncApiException) {
                // expected
            }

            assertEquals(2L, cursor)
            val saved = deferred.map { SyncCodec.decode(json.decodeFromString<SyncWire.Record>(it), crypto)?.key?.itemId }
            assertEquals(listOf("waits"), saved)

            // Next pass: the server has nothing new, the provider has arrived — the record applies.
            waitingItems.clear()
            coEvery { api.pull(any(), any(), 2L, any()) } returns SyncWire.PullResponse(head = 4, more = false)
            val outcome = engine().syncNow()

            assertEquals(listOf("waits"), applied.last().map { it.key.itemId })
            assertEquals(1, outcome?.pulled)
            assertTrue(deferred.isEmpty())
        }

    @Test
    fun `the cursor and the waiting records are saved together on every page`() =
        runBlocking {
            coEvery { api.pull(any(), any(), 0L, any()) } returns
                SyncWire.PullResponse(head = 4, more = true, records = listOf(wire("waits", 1), wire("ok", 2)))
            coEvery { api.pull(any(), any(), 2L, any()) } returns
                SyncWire.PullResponse(head = 4, more = false, records = listOf(wire("later", 3), wire("ok2", 4)))

            engine().syncNow()

            coVerify(exactly = 2) { store.savePullProgress(any(), any()) }
            // The record waiting since page 1 was offered again with page 2.
            assertEquals(listOf("waits", "later", "ok2"), applied[1].map { it.key.itemId })
            assertEquals(4L, cursor)
            assertEquals(1, deferred.size)
        }

    @Test
    fun `a record this device can't read doesn't hold the cursor back`() =
        runBlocking {
            val otherAccount = AccountKeyCrypto(AccountKeyCrypto.newAccountKey())
            val unreadable = SyncCodec.encode(record("foreign", 10), otherAccount).copy(seq = 1)
            val garbage = wire("ok", 2).copy(payload = "not a sealed payload")
            coEvery { api.pull(any(), any(), 0L, any()) } returns
                SyncWire.PullResponse(head = 3, more = false, records = listOf(unreadable, garbage, wire("fine", 3)))

            engine().syncNow()

            assertEquals(3L, cursor)
            assertEquals(listOf("fine"), applied.single().map { it.key.itemId })
            assertTrue(deferred.isEmpty())
        }

    @Test
    fun `a record sealed over the server's limit is dropped, and the rest still sent`() =
        runBlocking {
            coEvery { api.pull(any(), any(), any(), any()) } returns SyncWire.PullResponse()
            val sent = mutableListOf<String>()
            val small = record("small", 1)
            val huge = record("huge", 2).copy(payload = """{"name":"${"x".repeat(64 * 1024)}"}""")
            coEvery { anyConstructed<LocalRecords>().pending(any()) } returns
                listOf(
                    LocalRecords.Outgoing(huge) { sent += "huge" },
                    LocalRecords.Outgoing(small) { sent += "small" },
                )
            val pushed = slot<List<SyncWire.Record>>()
            coEvery { api.push(any(), any(), capture(pushed)) } answers
                { SyncWire.PushResponse(head = 1, accepted = pushed.captured.size, rejected = emptyList()) }

            val outcome = engine().syncNow()

            assertEquals(listOf(crypto.keyId(small.key)), pushed.captured.map { it.key })
            // Both done: the oversized one can never succeed, so it must not block the queue.
            assertEquals(setOf("huge", "small"), sent.toSet())
            assertEquals(1, outcome?.pushed)
        }
}
