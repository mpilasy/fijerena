package org.njarasoa.fijerena.core.network.sync

import android.util.Log
import kotlinx.serialization.json.Json

/** A [SyncRecord] to and from what the server stores — see `docs/plans/archive/20260929_live-sync-plan.md` → Security. */
internal object SyncCodec {
    private const val TAG = "SyncCodec"
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(
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

    /**
     * Null for a record this device can't open (another account key), read (a newer app's shape),
     * or trust: its key, or its clock value and deletion flag, differ from what was sealed.
     */
    fun decode(
        wire: SyncWire.Record,
        crypto: SyncCrypto,
    ): SyncRecord? {
        val opened = crypto.open(wire.payload, aad = wire.key) ?: return null
        val envelope = runCatching { json.decodeFromString<SyncWire.Envelope>(opened) }.getOrNull() ?: return null
        // The key must be the one the envelope names, or a server could swap payloads between records.
        if (crypto.keyId(envelope.key()) != wire.key) return null
        // The server needs updatedAt and deleted in the clear (last-write-wins, cascades), so they
        // travel twice: sealed copies in the envelope, which the server can't alter, must match.
        // Otherwise a server could turn any record into a deletion, or rewrite which write wins.
        // Records sealed before these copies existed carry neither and are taken as they come.
        // See docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-07.
        val tampered =
            (envelope.updatedAt != null && envelope.updatedAt != wire.updatedAt) ||
                (envelope.deleted != null && envelope.deleted != wire.deleted)
        if (tampered) {
            Log.w(TAG, "Dropped a ${envelope.kind} record whose clock value or deletion flag doesn't match what was sealed")
            return null
        }
        return SyncRecord(envelope.key(), wire.updatedAt, wire.deleted, envelope.payload)
    }
}
