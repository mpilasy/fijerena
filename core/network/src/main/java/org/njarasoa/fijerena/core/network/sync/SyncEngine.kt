package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

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
            val result = applier.apply(batch.mapNotNull { decode(it, crypto) })
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
        var pushed = 0
        while (true) {
            val outgoing = local.pending(PUSH_BATCH)
            if (outgoing.isEmpty()) break
            val byKey = outgoing.associateBy { crypto.keyId(it.record.key) }
            val response = api.push(link.serverUrl, link.deviceToken, outgoing.map { encode(it.record, crypto) })
            // Accepted, or rejected as stale — the server has something newer, which the next pull
            // brings: either way this version is done.
            byKey.values.forEach { it.markSent() }
            pushed += response.accepted
            if (outgoing.size < PUSH_BATCH) break
        }
        return pushed
    }

    private fun encode(
        record: SyncRecord,
        crypto: SyncCrypto,
    ): SyncWire.Record {
        val key = record.key
        val keyId = crypto.keyId(key)
        val cascade =
            when {
                !record.deleted -> null
                key.kind == SyncKind.PROVIDER -> "provider"
                key.kind == SyncKind.PROFILE -> "profile"
                else -> null
            }
        return SyncWire.Record(
            key = keyId,
            providerTag = key.providerKey.takeIf { it.isNotEmpty() }?.let(crypto::tag),
            profileTag =
                when {
                    key.kind == SyncKind.PROFILE -> crypto.tag(key.itemId)
                    key.profileKey != SyncKind.SHARED -> crypto.tag(key.profileKey)
                    else -> null
                },
            updatedAt = record.hlc,
            deleted = record.deleted,
            payload = crypto.seal(json.encodeToString(SyncWire.Envelope.of(record)), aad = keyId),
            cascade = cascade,
        )
    }

    /** Null for a record this device can't open (another account key) or read (a newer app's shape). */
    private fun decode(
        wire: SyncWire.Record,
        crypto: SyncCrypto,
    ): SyncRecord? {
        val opened = crypto.open(wire.payload, aad = wire.key) ?: return null
        val envelope = runCatching { json.decodeFromString<SyncWire.Envelope>(opened) }.getOrNull() ?: return null
        // The key must be the one the envelope names, or a server could swap payloads between records.
        if (crypto.keyId(envelope.key()) != wire.key) return null
        return SyncRecord(envelope.key(), wire.updatedAt, wire.deleted, envelope.payload)
    }

    private companion object {
        const val TAG = "SyncEngine"
        const val PUSH_BATCH = 200
        const val MAX_DEFERRED = 1000
    }
}
