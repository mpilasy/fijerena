package org.njarasoa.fijerena.core.network.sync

import android.util.Base64

/**
 * What the server may see of a record: an opaque key, opaque provider/profile tags for its
 * cascades, and a sealed payload. See `docs/plans/20260929_live-sync-plan.md` → Security.
 */
interface SyncCrypto {
    /** The record's key as the server stores it; the same [SyncKey] must always give the same id. */
    fun keyId(key: SyncKey): String

    /** A provider or profile key as the server groups by it. */
    fun tag(value: String): String

    fun seal(plaintext: String): String

    /** Null when [sealed] can't be opened — another account key, or tampering. */
    fun open(sealed: String): String?
}

/**
 * Phase 7 stand-in: readable keys, base64 payloads. **Not secure** — nothing may sync through a
 * server you don't control until Phase 8 replaces it with HMAC keys and AES-GCM payloads.
 */
object PlainSyncCrypto : SyncCrypto {
    override fun keyId(key: SyncKey) = listOf(key.profileKey, key.providerKey, key.kind, key.itemId, key.contentType).joinToString("|")

    override fun tag(value: String) = value

    override fun seal(plaintext: String): String = Base64.encodeToString(plaintext.toByteArray(), Base64.NO_WRAP)

    override fun open(sealed: String): String? =
        try {
            String(Base64.decode(sealed, Base64.NO_WRAP))
        } catch (_: IllegalArgumentException) {
            null
        }
}
