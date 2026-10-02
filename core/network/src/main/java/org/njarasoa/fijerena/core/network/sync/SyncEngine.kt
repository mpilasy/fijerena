package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog

/**
 * One sync pass with the server this device is linked to: pull everything new and apply it, then
 * push everything pending. See `docs/plans/20260929_live-sync-plan.md` → Flow.
 *
 * Order matters on a device's first pass after linking: it pulls first, so providers it already
 * had adopt the keys other devices gave them and received items get their versions; only then is
 * everything local queued ([LocalRecords.seedEverything]) and pushed — so an item both devices had
 * is sent only if this device's copy is the newer.
 *
 * Passes never overlap. Transport failures surface as [SyncApiException] for the caller to retry.
 */
class SyncEngine(
    private val context: Context,
    private val api: SyncApi = SyncApi(),
    private val store: SyncAccountStore = SyncAccountStore(context),
) {
    /** What a pass changed that the UI layer must react to. */
    interface Listener {
        fun onUserDataChanged(providerIds: Set<Long>) {}

        /** Another device deleted the profile this one is using: switch away, then sync again. */
        fun onActiveProfileDeleted() {}
    }

    data class Outcome(
        val pulled: Int,
        val pushed: Int,
        val deferred: Int,
        val activeProfileDeleted: Boolean,
    )

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    val isLinked: Boolean get() = store.link != null

    suspend fun syncNow(listener: Listener? = null): Outcome? =
        mutex.withLock {
            val link = store.link ?: return@withLock null
            val crypto = AccountKeyCrypto(link.accountKey)
            try {
                val pull = pull(link, crypto, listener)
                if (!store.seeded) {
                    LocalRecords(context).seedEverything()
                    store.seeded = true
                }
                val pushed = push(link, crypto)
                store.lastSyncAt = System.currentTimeMillis()
                store.lastError = null
                Outcome(pull.applied, pushed, pull.deferred, pull.activeProfileDeleted)
            } catch (e: SyncApiException) {
                store.lastError = e.message
                if (e.status == 401) Log.w(TAG, "Device token refused — revoked from another device?")
                throw e
            }
        }

    private data class PullOutcome(
        val applied: Int,
        val deferred: Int,
        val activeProfileDeleted: Boolean,
    )

    private suspend fun pull(
        link: SyncAccountStore.Link,
        crypto: SyncCrypto,
        listener: Listener?,
    ): PullOutcome {
        val applier = SyncApplier(context)
        var applied = 0
        var activeProfileDeleted = false
        var waiting = store.deferred.mapNotNull { runCatching { json.decodeFromString<SyncWire.Record>(it) }.getOrNull() }
        while (true) {
            val page = api.pull(link.serverUrl, link.deviceToken, store.cursor)
            if (page.resync) {
                // Tombstones this device never saw are gone from the server: start over from 0.
                // Everything is re-applied against local versions, so only real changes land.
                Log.i(TAG, "Cursor ${store.cursor} is past the server's tombstone horizon; pulling everything")
                store.cursor = 0
                continue
            }
            // Records waiting for a provider or profile get another go with each page.
            val batch = waiting + page.records
            val result = applier.apply(batch.mapNotNull { SyncCodec.decode(it, crypto) })
            applied += result.applied
            activeProfileDeleted = activeProfileDeleted || result.activeProfileDeleted
            if (result.userDataChangedProviderIds.isNotEmpty()) listener?.onUserDataChanged(result.userDataChangedProviderIds)
            val deferredKeys = result.deferred.map { crypto.keyId(it.key) }.toSet()
            waiting = batch.filter { it.key in deferredKeys }.takeLast(MAX_DEFERRED)
            // Together, every page: saving only the cursor here (and the waiting records after
            // the loop) lost the records this page deferred whenever a later page failed — the
            // cursor had already moved past them, so nothing ever fetched them again. See
            // docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-09.
            store.savePullProgress(
                cursor = page.records.lastOrNull()?.seq ?: store.cursor,
                deferred = waiting.map { json.encodeToString(it) }.toSet(),
            )
            if (!page.more) break
        }
        if (activeProfileDeleted) listener?.onActiveProfileDeleted()
        return PullOutcome(applied, waiting.size, activeProfileDeleted)
    }

    private suspend fun push(
        link: SyncAccountStore.Link,
        crypto: SyncCrypto,
    ): Int {
        val local = LocalRecords(context)
        val clock = SettingsDatabase.getInstance(context).settingsSyncDao()
        var pushed = 0
        // Volatile records (now playing) have no version row: they ride with the first batch,
        // stamped now — a handful at most, well inside the server's 500-record batch limit.
        var volatile = VolatileRecords.outbox.take { clock.nextClock() }
        while (true) {
            val outgoing = volatile + local.pending(PUSH_BATCH)
            volatile = emptyList()
            if (outgoing.isEmpty()) break
            val encoded = outgoing.map { it to SyncCodec.encode(it.record, crypto) }
            // The server refuses payloads over its limit. Sending one anyway used to fail the whole
            // batch, and since the oldest pending records always go first, nothing from this
            // device was ever pushed again. It can't succeed later either: drop it, loudly. See
            // docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-22.
            val (sendable, oversized) = encoded.partition { (_, wire) -> wire.payload.length <= MAX_PAYLOAD_CHARS }
            oversized.forEach { (pending, wire) ->
                val e = IllegalStateException("${pending.record.key.kind} record not synced: ${wire.payload.length} chars sealed, over the server's $MAX_PAYLOAD_CHARS")
                Log.e(TAG, e.message, e)
                CrashLog.record("sync push", e)
                pending.markSent()
            }
            if (sendable.isNotEmpty()) {
                val response = api.push(link.serverUrl, link.deviceToken, sendable.map { it.second })
                response.rejected.filter { it.reason != "stale" }.forEach { Log.w(TAG, "Server rejected a record: ${it.reason}") }
                // Accepted, rejected as stale (the server has something newer, which the next pull
                // brings) or rejected as invalid (it would be every time): this version is done.
                sendable.forEach { it.first.markSent() }
                pushed += response.accepted
            }
            if (outgoing.size < PUSH_BATCH) break
        }
        return pushed
    }

    private companion object {
        const val TAG = "SyncEngine"
        const val PUSH_BATCH = 200
        const val MAX_DEFERRED = 1000

        /** The server's `MAX_PAYLOAD` (server/src/account.ts). */
        const val MAX_PAYLOAD_CHARS = 64 * 1024
    }
}
