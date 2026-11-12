package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [SyncEngine.pushOnly], the safe-mode send of the device diagnostics sync plan: one push of the
 * records given, sealed with the account key, and nothing else — no pull, no seed, no cursor.
 */
class SyncEnginePushOnlyTest {
    private val accountKey = AccountKeyCrypto.newAccountKey()
    private val crypto = AccountKeyCrypto(accountKey)
    private val api = mockk<SyncApi>()
    private val store = mockk<SyncAccountStore>(relaxed = true)

    private val record =
        SyncRecord(
            SyncKey(SyncKind.SHARED, "", SyncKind.SETTING, "device_report:dev"),
            hlc = 1_800_000_000_000L,
            payload = """{"value":"{}"}""",
        )

    private fun engine() = SyncEngine(mockk<Context>(relaxed = true), api, store)

    @Test
    fun `one record is pushed, sealed, and nothing is pulled`() =
        runBlocking {
            every { store.link } returns SyncAccountStore.Link("https://sync.test", "acc", "dev", "token", accountKey)
            val sent = slot<List<SyncWire.Record>>()
            coEvery { api.push("https://sync.test", "token", capture(sent)) } returns
                SyncWire.PushResponse(head = 7, accepted = 1, rejected = emptyList())

            val accepted = engine().pushOnly(listOf(record))

            assertEquals(1, accepted)
            assertEquals(listOf(record), sent.captured.map { SyncCodec.decode(it, crypto) })
            coVerify(exactly = 1) { api.push(any(), any(), any()) }
            coVerify(exactly = 0) { api.pull(any(), any(), any(), any()) }
            verify(exactly = 0) { store.cursor = any() }
            verify(exactly = 0) { store.seeded = any() }
            verify(exactly = 0) { store.lastSyncAt = any() }
        }

    @Test
    fun `a device that is not linked sends nothing`() {
        every { store.link } returns null

        assertThrows(IllegalStateException::class.java) { runBlocking { engine().pushOnly(listOf(record)) } }
        coVerify(exactly = 0) { api.push(any(), any(), any()) }
    }
}
