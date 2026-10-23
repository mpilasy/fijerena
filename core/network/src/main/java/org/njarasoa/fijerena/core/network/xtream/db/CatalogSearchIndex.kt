package org.njarasoa.fijerena.core.network.xtream.db

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Compacts the catalogue's search indexes ([XtreamStreamFts], [XtreamSeriesFts]).
 *
 * Room's triggers update them row by row at each catalogue sync. FTS4 appends a segment for each
 * change and keeps the old entries until segments merge, so after a few syncs an index is eight
 * times its compact size (138 MB against 14 MB on the TV emulator). `optimize` merges every segment
 * into one; searches return the same rows. See the disk footprint plan → Phase 1.
 */
internal object CatalogSearchIndex {
    private const val TAG = "CatalogSearchIndex"
    private val TABLES = listOf("xtream_streams_fts", "xtream_series_fts")

    /**
     * Merges each index that has more than one segment; an index already in one segment (no change
     * since the last call) is left alone. Never throws: a failure leaves the index as it was.
     */
    suspend fun compact(context: Context) =
        withContext(Dispatchers.IO) {
            val db = XtreamDatabase.getInstance(context).openHelper.writableDatabase
            TABLES.forEach { table ->
                try {
                    val segments = db.query("SELECT COUNT(*) FROM `${table}_segdir`").use { if (it.moveToFirst()) it.getInt(0) else 0 }
                    if (segments > 1) {
                        val startMs = System.currentTimeMillis()
                        db.execSQL("INSERT INTO `$table`(`$table`) VALUES('optimize')")
                        Log.i(TAG, "$table: merged $segments segments in ${System.currentTimeMillis() - startMs} ms")
                    }
                } catch (e: Exception) {
                    // cancellation-ok: a blocking SQLite call, no suspension point inside
                    Log.w(TAG, "$table: optimize failed", e)
                }
            }
        }
}
