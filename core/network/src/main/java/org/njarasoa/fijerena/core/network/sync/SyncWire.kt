package org.njarasoa.fijerena.core.network.sync

import kotlinx.serialization.Serializable

/** The JSON of the sync server's API (protocol 1) — see `server/README.md`. */
object SyncWire {
    const val PROTOCOL = 1

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** The `n` of a WebSocket `{"head": n}` message; null for anything else. */
    fun parseHead(text: String): Long? = runCatching { json.decodeFromString<Head>(text).head }.getOrNull()

    @Serializable
    data class Info(
        val service: String,
        val protocol: Int,
        val setupSecretRequired: Boolean = false,
    )

    @Serializable
    data class DeviceCredentials(
        val accountId: String,
        val deviceId: String,
        val deviceToken: String,
    )

    @Serializable
    data class Pairing(
        val code: String,
        val expiresAt: Long,
    )

    @Serializable
    data class Record(
        val key: String,
        val providerTag: String? = null,
        val profileTag: String? = null,
        val updatedAt: Long,
        val deleted: Boolean,
        val payload: String,
        val cascade: String? = null,
        val seq: Long? = null,
    )

    @Serializable
    data class PushRequest(
        val records: List<Record>,
    )

    @Serializable
    data class Rejection(
        val key: String,
        val reason: String,
    )

    @Serializable
    data class PushResponse(
        val head: Long,
        val accepted: Int,
        val rejected: List<Rejection>,
    )

    @Serializable
    data class PullResponse(
        val head: Long = 0,
        val more: Boolean = false,
        val records: List<Record> = emptyList(),
        val resync: Boolean = false,
    )

    @Serializable
    data class HandoffOpened(
        val handoffId: String,
        val expiresAt: Long,
    )

    @Serializable
    data class HandoffFill(
        val sealed: String,
        val senderKey: String,
    )

    @Serializable
    data class HandoffFilled(
        val filled: Boolean = true,
    )

    @Serializable
    data class HandoffCollected(
        val ready: Boolean,
        val sealed: String? = null,
        val senderKey: String? = null,
        val expiresAt: Long? = null,
    )

    /** What a handoff carries, sealed to the joining device's one-time key. */
    @Serializable
    data class HandoffSecret(
        val pairingCode: String,
        val accountKey: String,
    )

    @Serializable
    data class Head(
        val head: Long,
    )

    /**
     * What is sealed inside [Record.payload]: the record's own identity — the server's key is
     * one-way — and, unless it is a deletion, the kind's payload (see [SyncPayloads]).
     */
    @Serializable
    data class Envelope(
        val profileKey: String,
        val providerKey: String,
        val kind: String,
        val itemId: String = "",
        val contentType: String = "",
        val payload: String? = null,
    ) {
        fun key() = SyncKey(profileKey, providerKey, kind, itemId, contentType)

        companion object {
            fun of(record: SyncRecord) =
                Envelope(
                    record.key.profileKey,
                    record.key.providerKey,
                    record.key.kind,
                    record.key.itemId,
                    record.key.contentType,
                    record.payload,
                )
        }
    }
}
