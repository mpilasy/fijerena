package org.njarasoa.fijerena.core.network.xtream.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sync.SyncKind

/**
 * [XtreamSyncTriggers] queue every local change with the sync clock, in the same transaction.
 * Uses the real database (the triggers are installed on open), with its own provider ids.
 *
 * Runs against this test APK's own databases, never the app's.
 */
@RunWith(AndroidJUnit4::class)
class XtreamSyncTriggersTest {
    private val db = XtreamDatabase.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)

    private fun queued(providerId: Long) = runBlocking { db.syncVersionDao().getPending(10_000).filter { it.providerId == providerId } }

    private fun favorite(
        providerId: Long,
        itemId: String,
        kind: String = FavoriteKind.STREAM,
    ) = FavoriteStateEntity(providerId, "p", itemId, "MOVIES", kind, "Name", null, 1L)

    @Test
    fun aFavoriteIsQueuedOnceWithTheLatestClock() {
        val provider = 9001L
        db.favoriteStateDao().upsertClearingTombstone(favorite(provider, "m1"))
        val first = queued(provider).single()
        assertEquals(SyncKind.FAVORITE_STREAM, first.kind)
        assertTrue(first.pending)
        assertTrue(first.hlc > 1_600_000_000_000L) // milliseconds since the epoch, not 0

        db.favoriteStateDao().upsertClearingTombstone(favorite(provider, "m1"))
        val second = queued(provider).single()
        assertTrue(second.hlc > first.hlc)

        db.favoriteStateDao().upsertClearingTombstone(favorite(provider, "c1", FavoriteKind.CATEGORY))
        assertEquals(SyncKind.FAVORITE_CATEGORY, queued(provider).single { it.itemId == "c1" }.kind)
    }

    @Test
    fun aRemovalIsStampedAndQueued() {
        val provider = 9002L
        db.favoriteStateDao().upsertClearingTombstone(favorite(provider, "m1"))
        val added = queued(provider).single().hlc

        db.favoriteStateDao().deleteRecordingTombstone(provider, "p", "m1", "MOVIES", FavoriteKind.STREAM)

        val tombstone = runBlocking { db.syncTombstoneDao().getAll(provider) }.single()
        val entry = queued(provider).single()
        assertTrue(tombstone.deletedAt > added)
        assertEquals(tombstone.deletedAt, entry.hlc)
    }

    @Test
    fun watchProgressAndAHistoryClearAreQueued() {
        val provider = 9003L
        runBlocking {
            db.watchStateDao().upsertProgress(
                providerId = provider,
                profileId = "p",
                itemId = "m1",
                contentType = "MOVIES",
                itemName = "Film",
                categoryId = "c1",
                positionMs = 1_000L,
                durationMs = 5_000L,
                isCompleted = false,
                now = 1L,
                seriesId = null,
                episodeId = null,
                seriesName = null,
                episodeExtension = null,
                audioTrackIndex = null,
                subtitleTrackIndex = null,
            )
            assertEquals(SyncKind.WATCH, queued(provider).single().kind)

            db.watchStateDao().deleteAllRecordingClear(provider, "p")
        }
        val clear = queued(provider).single { it.kind == SyncKind.WATCH_CLEAR }
        assertEquals(clear.hlc, runBlocking { db.syncTombstoneDao().getAll(provider) }.single().deletedAt)
    }

    @Test
    fun theClockOnlyMovesForward() {
        val provider = 9004L
        repeat(50) { db.favoriteStateDao().upsertClearingTombstone(favorite(provider, "m$it")) }
        val stamps = queued(provider).sortedBy { it.itemId.drop(1).toInt() }.map { it.hlc }
        assertEquals(stamps.sorted(), stamps)
        assertEquals(stamps.size, stamps.toSet().size)
    }

    @Test
    fun nothingIsQueuedWhileApplyingRemoteChanges() {
        val provider = 9005L
        val raw = db.openHelper.writableDatabase
        raw.execSQL("UPDATE sync_clock SET applying = 1")
        try {
            db.favoriteStateDao().upsertClearingTombstone(favorite(provider, "m1"))
        } finally {
            raw.execSQL("UPDATE sync_clock SET applying = 0")
        }
        assertTrue(queued(provider).isEmpty())
    }
}
