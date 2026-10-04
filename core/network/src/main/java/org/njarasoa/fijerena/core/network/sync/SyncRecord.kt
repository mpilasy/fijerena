package org.njarasoa.fijerena.core.network.sync

/**
 * Identity of one synced thing — see `docs/plans/archive/20260929_live-sync-plan.md` → Record model.
 * [profileKey] is a profile id for per-person kinds and [SyncKind.SHARED] otherwise; [providerKey]
 * is the provider's `providerKey` for provider-scoped kinds and empty otherwise; [itemId] and
 * [contentType] are the item within them (empty where a kind has none: a provider record's own
 * item is its [providerKey], a profile's [itemId] is its id, an EPG source's its `source_key`, a
 * setting's its key).
 */
data class SyncKey(
    val profileKey: String,
    val providerKey: String,
    val kind: String,
    val itemId: String = "",
    val contentType: String = "",
)

/**
 * One version of a synced thing as it travels between devices. [hlc] is the sync clock of the
 * change that produced it; [deleted] makes it a tombstone, with no [payload]. The payload is the
 * kind's own JSON (Phase 8 encrypts it).
 */
data class SyncRecord(
    val key: SyncKey,
    val hlc: Long,
    val deleted: Boolean = false,
    val payload: String? = null,
)
