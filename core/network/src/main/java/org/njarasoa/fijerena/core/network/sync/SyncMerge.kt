package org.njarasoa.fijerena.core.network.sync

/**
 * Decides what a record received from another device does to this one. Pure: the caller looks up
 * the local facts ([Local]) and carries out the [Resolution]. See
 * `docs/plans/20260929_live-sync-plan.md` → Conflicts, Deletions, Jellyfin.
 *
 * Last writer wins per record, on the hybrid logical clock: a record applies only if its [SyncRecord.hlc]
 * is newer than everything this device knows about the same key — its own version and its
 * tombstone. A tie keeps the local version: equal clocks from two devices changing the same item in
 * the same millisecond are rare enough to accept the divergence until the next change.
 */
object SyncMerge {
    /** Whether something a record depends on (its provider, its profile) exists here. */
    enum class Presence { NOT_APPLICABLE, KNOWN, UNKNOWN, DELETED }

    /** What this device knows about the record's key. */
    data class Local(
        /** Clock of this device's current version of the key (local or received), if any. */
        val version: Long? = null,
        /** Clock of this device's tombstone for the key, if it deleted it. */
        val tombstone: Long? = null,
        /** For a `watch` record: clock of the latest "clear watch history" of its provider and profile. */
        val watchClearedAt: Long? = null,
        val provider: Presence = Presence.NOT_APPLICABLE,
        val profile: Presence = Presence.NOT_APPLICABLE,
        /** The record's provider keeps favourites and history itself (Jellyfin). */
        val providerKeepsUserData: Boolean = false,
    )

    sealed interface Resolution {
        /** Write the payload as this device's version. */
        data object Upsert : Resolution

        /** Delete the item and keep the record as this device's tombstone. */
        data object Delete : Resolution

        /** Drop this provider and profile's watch rows older than [before]; keep the marker. */
        data class ClearWatch(val before: Long) : Resolution

        /** Nothing to do, ever. */
        data class Skip(val reason: SkipReason) : Resolution

        /** Can't apply yet: retry once what it depends on has arrived. */
        data class Defer(val reason: DeferReason) : Resolution
    }

    enum class SkipReason {
        /** This device already has a version at least as new. */
        STALE,

        /** A watch row older than a "clear watch history" of its provider and profile. */
        CLEARED,

        /** Favourites and history of a provider that keeps them itself are never synced. */
        PROVIDER_KEEPS_USER_DATA,
        DELETED_PROVIDER,
        DELETED_PROFILE,
    }

    enum class DeferReason { UNKNOWN_PROVIDER, UNKNOWN_PROFILE }

    /** Favourites and watch history — never synced for a provider that keeps them itself. */
    private val userDataKinds = setOf(SyncKind.WATCH, SyncKind.WATCH_CLEAR, SyncKind.FAVORITE_STREAM, SyncKind.FAVORITE_CATEGORY)

    fun resolve(
        remote: SyncRecord,
        local: Local,
    ): Resolution {
        when (local.provider) {
            Presence.DELETED -> return Resolution.Skip(SkipReason.DELETED_PROVIDER)
            Presence.UNKNOWN -> return Resolution.Defer(DeferReason.UNKNOWN_PROVIDER)
            else -> Unit
        }
        if (remote.key.kind in userDataKinds && local.providerKeepsUserData) {
            return Resolution.Skip(SkipReason.PROVIDER_KEEPS_USER_DATA)
        }
        when (local.profile) {
            Presence.DELETED -> return Resolution.Skip(SkipReason.DELETED_PROFILE)
            Presence.UNKNOWN -> return Resolution.Defer(DeferReason.UNKNOWN_PROFILE)
            else -> Unit
        }

        val localLatest = maxOf(local.version ?: Long.MIN_VALUE, local.tombstone ?: Long.MIN_VALUE)
        if (remote.hlc <= localLatest) return Resolution.Skip(SkipReason.STALE)

        return when {
            remote.key.kind == SyncKind.WATCH_CLEAR -> Resolution.ClearWatch(remote.hlc)
            remote.deleted -> Resolution.Delete
            remote.key.kind == SyncKind.WATCH && local.watchClearedAt != null && remote.hlc <= local.watchClearedAt ->
                Resolution.Skip(SkipReason.CLEARED)
            else -> Resolution.Upsert
        }
    }
}

/**
 * The hybrid logical clock's two rules, as the SQL in the sync triggers and the apply path runs
 * them — kept here so the merge's clock-skew behaviour can be tested without a database.
 */
object Hlc {
    /** A local change: never behind the wall clock, always after the last value. */
    fun tick(
        last: Long,
        wallMs: Long,
    ): Long = maxOf(wallMs, last + 1)

    /** A received record: this device's next change must come after it. */
    fun receive(
        last: Long,
        remote: Long,
    ): Long = maxOf(last, remote)
}
