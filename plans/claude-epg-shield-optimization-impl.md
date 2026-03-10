# EPG Refresh Performance Optimization for NVIDIA Shield

## Context

On NVIDIA Shield devices, EPG refresh has two ~5-minute delays that don't occur on phones/emulators:
1. **Pre-download**: stays cyan for ~5 min before any download starts
2. **Post-100%**: appears stuck at 100% for ~5 min after ingestion completes

**Root causes:**
- Delay 1: `beginBulkIngestion()` (drops 6 indexes + 4 FTS triggers on a 500MB-1GB database) runs synchronously *before* downloads start
- Delay 2: `endBulkIngestion()` (recreates 6 indexes) + `rebuildFtsAndUpdateState()` (FTS4 full rebuild) + `incrementalVacuum()` run with no progress indication

Both are I/O-bound SQLite operations that are fast on phone UFS/NVMe but slow on Shield eMMC.

---

## Changes

### 1. Overlap `beginBulkIngestion()` with downloads

Downloads write to cache files and never touch the database, so they can run in parallel with index drops.

**`EpgFileManager.kt` — `processAllSourcesInternal()` (~line 393-394):**
- Wrap `beginBulkIngestion()` in `async(Dispatchers.IO)`, store as `bulkReady`
- Start download producers immediately (no change needed — they don't touch DB)
- In each ingestion consumer, call `bulkReady.await()` before the first `ingestDownloadedSource()` call

**`EpgFileManager.kt` — `processSingleSourceInternal()` (~line 546-547):**
- Same pattern: `async` the `beginBulkIngestion()`, start download immediately, `await` before ingestion

### 2. Add `Finalizing` state for post-ingestion progress

**`EpgFileManager.kt` — `MultiSourceState` sealed interface (~line 124):**
```kotlin
data class Finalizing(
    val phase: String, // "Rebuilding indexes", "Rebuilding search index", "Optimizing storage"
    val totalChannels: Int = 0,
    val totalProgrammes: Int = 0,
    val totalDownloadBytes: Long = 0,
    val durationMs: Long = 0
) : MultiSourceState
```

**`EpgFileManager.kt` — `processAllSourcesInternal()` (~lines 487-495):**
Emit `Finalizing` state before each step:
- `_state.value = Finalizing("Rebuilding indexes…", ...)` → `endBulkIngestion()`
- `_state.value = Finalizing("Rebuilding search index…", ...)` → `rebuildFtsAndUpdateState()`
- `_state.value = Finalizing("Optimizing storage…", ...)` → `incrementalVacuum()`

**`EpgFileManager.kt` — `processSingleSourceInternal()` (~lines 602-609):**
Same pattern.

**`TvEpgManagementScreen.kt`:**
- Handle `Finalizing` in the top status bar area (alongside `Processing`, `Completed`, etc.) — show phase text with a working indicator
- Per-source dots should show completed (green) during finalization
- Add handling around lines 182-242 (status section) and 483-525 (per-source section)

**`MobileEpgManagementScreen.kt`:**
- Same treatment: handle `Finalizing` around lines 151-211 (status) and 438-480 (per-source)

### 3. Defer FTS rebuild to background

The FTS rebuild is the single heaviest post-ingestion operation. Deferring it lets the user see `Completed` sooner.

**`EpgIndexer.kt`:**
- Add `private var ftsStale = AtomicBoolean(false)` field
- Add `isFtsStale()` public accessor
- On `initialize()`, check metadata for stale flag and resume background rebuild if needed
- Add `markFtsStale()` / `markFtsClean()` that persist to `EpgIndexMetadata` or SharedPreferences

**`EpgFileManager.kt` — both `processAllSourcesInternal` and `processSingleSourceInternal`:**
- After `endBulkIngestion()`, mark FTS as stale
- Emit `Completed` state immediately
- Launch FTS rebuild in background: `scope.launch { indexer.rebuildFtsAndUpdateState(); indexer.markFtsClean(); indexer.incrementalVacuum() }`

**Search fallback** (wherever EPG search is invoked):
- Check `indexer.isFtsStale()` — if true, use `searchByTitleLike()` (already exists in DAO) instead of FTS query
- The `idx_programme_title_lower` index ensures LIKE queries are acceptable

---

## Files to modify

| File | Changes |
|------|---------|
| `core/network/.../xmltv/EpgFileManager.kt` | Overlap bulk setup with downloads, add `Finalizing` state, defer FTS rebuild |
| `core/network/.../xmltv/epgindex/EpgIndexer.kt` | Add `ftsStale` flag, persist/restore stale state |
| `tv/.../epg/TvEpgManagementScreen.kt` | Render `Finalizing` state in UI |
| `mobile/.../epg/MobileEpgManagementScreen.kt` | Render `Finalizing` state in UI |

## Verification

1. On emulator/phone: verify no regression — refresh should work identically, `Finalizing` phases flash briefly
2. On Shield with large EPG database:
   - Downloads should start immediately (no cyan delay)
   - After ingestion hits 100%, UI shows "Rebuilding indexes…" then completes
   - FTS rebuild runs in background — search still works via LIKE fallback
   - Once FTS rebuild finishes, search uses FTS again
3. Test EPG search while FTS is stale — should return correct results via LIKE
4. Test crash during background FTS rebuild — on next launch, should detect stale flag and re-trigger rebuild

---

## Future: Alternatives to SQLite for EPG Storage

The current SQLite/Room approach works but the index/FTS maintenance is the root cause of Shield delays. These alternatives could eliminate the problem at a deeper level.

### 1. Shadow/swap table pattern (recommended next step)
Stay with Room/SQLite but ingest into a shadow table (`epg_programme_new`), then atomic `ALTER TABLE RENAME` swap.

- Old data remains fully queryable (with indexes and FTS) while new data ingests into unindexed shadow table
- Index build happens once on the shadow table before swap; swap is instant (metadata-only)
- No `beginBulkIngestion`/`endBulkIngestion` needed; crash-safe (old table untouched on failure)
- **Trade-off:** Temporarily doubles storage. Room doesn't natively support table renames — requires raw SQL and careful DAO invalidation.

### 2. SQLite + Lucene for search
Keep Room for structured queries (time-range, channel, paging) but replace FTS4 with Apache Lucene.

- Lucene's inverted index is far more efficient than FTS4 for both building and querying
- Incremental index updates are cheap (no full rebuild needed)
- Lucene index can be built fully in background without blocking Room writes
- **Trade-off:** Two storage engines to manage. `lucene-core` (~2.5MB) works on Android but isn't officially supported. Index consistency requires coordination.

### 3. ObjectBox
NoSQL object database for Android/Kotlin, no ORM layer.

- Writes are 5-10x faster than SQLite — no SQL parsing, no index B-tree rebalancing per row
- Built-in Kotlin data class support, no migration headaches
- "Now playing" and time-range queries map well to ObjectBox's indexed property queries
- **Trade-off:** No FTS built-in — need a separate search solution. Requires rewriting all DAOs. ~2MB library size.

### 4. Source-sharded SQLite
One Room database per EPG source instead of one monolithic DB.

- `beginBulkIngestion()` only affects the source being refreshed — other sources stay indexed and searchable
- Index drops/rebuilds operate on a much smaller dataset; can delete a source by deleting its file (instant)
- **Trade-off:** Cross-source queries (search across all EPG) need to fan out across N databases and merge results. Paging 3 can't natively page across multiple databases.

### 5. Flat binary format + memory-mapped reads
Custom binary format (FlatBuffers/Protobuf) with pre-sorted arrays for indexed access.

- Zero index rebuild — data is written pre-sorted by (channel_id, start_epoch); binary search replaces B-tree lookups
- Ingestion is sequential write only (append to file); memory-mapped reads let the OS handle caching
- **Trade-off:** No query language — every access pattern needs hand-written code. No FTS. No transactions or crash recovery. High engineering cost.

### 6. Pre-compute FTS offline
Build the FTS index during ingestion by writing FTS entries directly (bypass triggers), or ship a pre-built SQLite DB from the server.

- No post-ingestion FTS rebuild needed at all
- **Trade-off:** Requires server-side infrastructure or XMLTV provider cooperation. Not viable for arbitrary XMLTV URLs.

### Ranking

| Rank | Option | Rationale |
|------|--------|-----------|
| 1 | Shadow/swap table | Smallest change, stays in Room, eliminates both delays without new dependencies |
| 2 | SQLite + Lucene | Directly solves the FTS rebuild bottleneck with a proven search engine |
| 3 | ObjectBox | Best raw write perf, but requires full DAO rewrite and solving search separately |
| 4 | Source-sharded SQLite | Good if source-scoped queries dominate; awkward for cross-source search |
