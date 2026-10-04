package org.njarasoa.fijerena.core.network.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.SyncMerge.DeferReason
import org.njarasoa.fijerena.core.network.sync.SyncMerge.Local
import org.njarasoa.fijerena.core.network.sync.SyncMerge.Presence
import org.njarasoa.fijerena.core.network.sync.SyncMerge.Resolution
import org.njarasoa.fijerena.core.network.sync.SyncMerge.SkipReason

/** Every conflict case of the merge — see docs/plans/archive/20260929_live-sync-plan.md → Conflicts, Deletions. */
class SyncMergeTest {
    private val watchKey = SyncKey("p1", "prov", SyncKind.WATCH, "m1", "MOVIES")
    private val favoriteKey = SyncKey("p1", "prov", SyncKind.FAVORITE_STREAM, "m1", "MOVIES")
    private val onKnownProvider = Local(provider = Presence.KNOWN, profile = Presence.KNOWN)

    private fun record(
        key: SyncKey,
        hlc: Long,
        deleted: Boolean = false,
    ) = SyncRecord(key, hlc, deleted, if (deleted) null else "{}")

    // --- Last writer wins ---

    @Test
    fun `a record for something new applies`() {
        assertEquals(Resolution.Upsert, SyncMerge.resolve(record(favoriteKey, 10), onKnownProvider))
    }

    @Test
    fun `a newer record wins`() {
        assertEquals(Resolution.Upsert, SyncMerge.resolve(record(favoriteKey, 20), onKnownProvider.copy(version = 10)))
    }

    @Test
    fun `an older record loses`() {
        assertEquals(Resolution.Skip(SkipReason.STALE), SyncMerge.resolve(record(favoriteKey, 10), onKnownProvider.copy(version = 20)))
    }

    @Test
    fun `a tie keeps the local version`() {
        assertEquals(Resolution.Skip(SkipReason.STALE), SyncMerge.resolve(record(favoriteKey, 10), onKnownProvider.copy(version = 10)))
    }

    // --- Tombstones ---

    @Test
    fun `a newer remote deletion deletes`() {
        assertEquals(Resolution.Delete, SyncMerge.resolve(record(favoriteKey, 20, deleted = true), onKnownProvider.copy(version = 10)))
    }

    @Test
    fun `an older remote deletion loses to a newer local change`() {
        assertEquals(
            Resolution.Skip(SkipReason.STALE),
            SyncMerge.resolve(record(favoriteKey, 10, deleted = true), onKnownProvider.copy(version = 20)),
        )
    }

    @Test
    fun `a local deletion beats an older remote update`() {
        assertEquals(Resolution.Skip(SkipReason.STALE), SyncMerge.resolve(record(favoriteKey, 10), onKnownProvider.copy(tombstone = 20)))
    }

    @Test
    fun `a remote update newer than the local deletion brings the item back`() {
        assertEquals(Resolution.Upsert, SyncMerge.resolve(record(favoriteKey, 30), onKnownProvider.copy(version = 10, tombstone = 20)))
    }

    @Test
    fun `a deletion of something this device never had is still recorded`() {
        assertEquals(Resolution.Delete, SyncMerge.resolve(record(favoriteKey, 10, deleted = true), onKnownProvider))
    }

    // --- Clear watch history ---

    @Test
    fun `a newer clear drops older watch rows`() {
        val clear = SyncKey("p1", "prov", SyncKind.WATCH_CLEAR)
        assertEquals(Resolution.ClearWatch(30), SyncMerge.resolve(record(clear, 30), onKnownProvider.copy(tombstone = 20)))
    }

    @Test
    fun `an older clear loses to a newer local one`() {
        val clear = SyncKey("p1", "prov", SyncKind.WATCH_CLEAR)
        assertEquals(Resolution.Skip(SkipReason.STALE), SyncMerge.resolve(record(clear, 10), onKnownProvider.copy(tombstone = 20)))
    }

    @Test
    fun `a watch row from before the clear stays cleared`() {
        assertEquals(
            Resolution.Skip(SkipReason.CLEARED),
            SyncMerge.resolve(record(watchKey, 10), onKnownProvider.copy(watchClearedAt = 20)),
        )
    }

    @Test
    fun `a watch row after the clear applies`() {
        assertEquals(Resolution.Upsert, SyncMerge.resolve(record(watchKey, 30), onKnownProvider.copy(watchClearedAt = 20)))
    }

    // --- Dependencies ---

    @Test
    fun `a record for a provider not here yet waits`() {
        assertEquals(
            Resolution.Defer(DeferReason.UNKNOWN_PROVIDER),
            SyncMerge.resolve(record(favoriteKey, 10), Local(provider = Presence.UNKNOWN, profile = Presence.KNOWN)),
        )
    }

    @Test
    fun `a record for a deleted provider is dropped`() {
        assertEquals(
            Resolution.Skip(SkipReason.DELETED_PROVIDER),
            SyncMerge.resolve(record(favoriteKey, 10), Local(provider = Presence.DELETED, profile = Presence.KNOWN)),
        )
    }

    @Test
    fun `a record for a profile not here yet waits`() {
        assertEquals(
            Resolution.Defer(DeferReason.UNKNOWN_PROFILE),
            SyncMerge.resolve(record(favoriteKey, 10), Local(provider = Presence.KNOWN, profile = Presence.UNKNOWN)),
        )
    }

    @Test
    fun `a record for a deleted profile is dropped`() {
        assertEquals(
            Resolution.Skip(SkipReason.DELETED_PROFILE),
            SyncMerge.resolve(record(favoriteKey, 10), Local(provider = Presence.KNOWN, profile = Presence.DELETED)),
        )
    }

    @Test
    fun `a deleted provider wins over everything else about the record`() {
        assertEquals(
            Resolution.Skip(SkipReason.DELETED_PROVIDER),
            SyncMerge.resolve(record(favoriteKey, 10), Local(provider = Presence.DELETED, profile = Presence.UNKNOWN)),
        )
    }

    // --- Jellyfin ---

    @Test
    fun `favourites and history of a provider that keeps them itself are ignored`() {
        val jellyfin = onKnownProvider.copy(providerKeepsUserData = true)
        for (kind in listOf(SyncKind.WATCH, SyncKind.WATCH_CLEAR, SyncKind.FAVORITE_STREAM, SyncKind.FAVORITE_CATEGORY)) {
            assertEquals(
                Resolution.Skip(SkipReason.PROVIDER_KEEPS_USER_DATA),
                SyncMerge.resolve(record(SyncKey("p1", "prov", kind, "x"), 10), jellyfin),
            )
        }
    }

    @Test
    fun `a Jellyfin login still syncs`() {
        val login = SyncKey("p1", "prov", SyncKind.PROVIDER_LOGIN)
        assertEquals(Resolution.Upsert, SyncMerge.resolve(record(login, 10), onKnownProvider.copy(providerKeepsUserData = true)))
    }

    // --- Shared records ---

    @Test
    fun `a provider record depends on nothing`() {
        val provider = SyncKey(SyncKind.SHARED, "prov", SyncKind.PROVIDER)
        assertEquals(Resolution.Upsert, SyncMerge.resolve(record(provider, 10), Local()))
        assertEquals(Resolution.Skip(SkipReason.STALE), SyncMerge.resolve(record(provider, 10), Local(tombstone = 15)))
    }

    // --- Clock skew ---

    @Test
    fun `a device with a slow clock still wins with a later change once it has seen the other's`() {
        // Device A's clock runs an hour ahead; B's is right.
        val hour = 3_600_000L
        val aWrite = Hlc.tick(last = 0, wallMs = 1_000_000L + hour)
        // B receives A's record, then its user changes the same item a minute later (by B's clock).
        val bClock = Hlc.receive(last = Hlc.tick(0, 1_000_000L), remote = aWrite)
        val bWrite = Hlc.tick(last = bClock, wallMs = 1_060_000L)
        assertTrue(bWrite > aWrite)
        assertEquals(Resolution.Upsert, SyncMerge.resolve(record(favoriteKey, bWrite), onKnownProvider.copy(version = aWrite)))
    }

    @Test
    fun `the clock never goes back`() {
        assertEquals(101L, Hlc.tick(last = 100, wallMs = 50))
        assertEquals(200L, Hlc.tick(last = 100, wallMs = 200))
        assertEquals(100L, Hlc.receive(last = 100, remote = 90))
        assertEquals(120L, Hlc.receive(last = 100, remote = 120))
    }
}
