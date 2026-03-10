# EPG Ingestion Optimization Plan

## The Core Problem
Currently, when a refresh task starts for any EPG source, the app executes a "Nuclear Option" by calling `beginBulkIngestion()`. This function executes SQL commands to drop all existing database indexes and Full-Text Search (FTS) triggers. 

If you have multiple EPG sources and you refresh just **one** of them, the app drops the indexes for **all sources**, inserts the new data for the 1 source, and then completely rebuilds the indexes and the Full-Text Search (FTS) database for **all sources** via `endBulkIngestion()` and `rebuildFtsAndUpdateState()`.

On modern phones or emulators with NVMe/UFS storage, this massive I/O operation finishes very quickly. However, on the NVIDIA Shield, which uses an older eMMC storage chip notoriously slow at random 4KB writes, this process causes severe bottlenecks:
1. **The 5-Minute Delay Before Downloading:** Moving hundreds of thousands of index pages to the free-list and writing changes to the WAL chokes the storage, causing the app to hang in the "Queued/Awaiting" state.
2. **The 5-Minute Delay at the End:** Reading the entire table, sorting data, writing new B-Tree indexes, and parsing millions of words to rebuild the FTS index takes several minutes, leaving the app stuck at 100%.

## The Proposed Solution

We need to move away from the "Drop & Rebuild" strategy for routine updates.

### 1. Context-Aware Ingestion (Incremental vs. Bulk)
We will modify `EpgIndexer` to have two distinct modes:
*   **Incremental Mode (Default):** Used when refreshing specific sources (Stale, Selected, or Single). We **do not** drop indexes or disable FTS triggers. We simply `INSERT OR REPLACE` the new programmes in batches. SQLite will update the B-Trees and FTS incrementally. While per-row insertion is slightly slower computationally, it hides the I/O latency behind the network download time and completely eliminates the 5-minute freezes.
*   **True Bulk Mode:** Only used when the database is entirely empty (e.g., initial setup or after "Clear All"). This is the only time the "Drop & Rebuild" strategy makes sense, as there is no existing data to carry over.

### 2. Smart Deletions for Stale Data
Currently, when a source refreshes, it might leave behind orphaned programmes if a channel was removed from the XMLTV file. We will add:
*   A pre-ingestion step that flags existing programmes for a `sourceId` as "stale" (e.g., by updating a timestamp or flag).
*   A post-ingestion step that deletes any programmes for that `sourceId` that weren't updated in the current run. 
This ensures the DB stays clean without requiring a full table wipe or full index rebuild.

### 3. SQLite Pragma Tuning for TV
We will ensure the database connection specifically uses Write-Ahead Logging (`WAL`) with `PRAGMA synchronous = NORMAL`. This allows SQLite to write changes to a temporary log file without waiting for the slow eMMC to physically sync the bytes to the main file, keeping the app responsive.

## Expected Result
*   **Before start:** Instant transition to "Downloading". The app will no longer spend 5 minutes destroying indexes.
*   **During ingestion:** Might take slightly longer than before (e.g., 60 seconds instead of 40 seconds) because it's updating the index on the fly, but the progress bar will move smoothly.
*   **At 100%:** Instant transition to "Finished". No more 5-minute FTS rebuilds.
