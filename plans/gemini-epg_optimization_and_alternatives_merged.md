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

---

# EPG Storage Alternatives

This document explores alternatives to SQLite for EPG data storage and indexing on Android, specifically targeting the I/O limitations of devices like the NVIDIA Shield (eMMC storage).

When evaluating alternatives, we must balance four primary requirements for EPG data:
1. **Bulk Write Speed:** Ingesting hundreds of thousands of programs quickly.
2. **Time-Series Lookups:** Instantly finding "What's playing now on Channel X".
3. **Full-Text Search (FTS):** Searching across all titles and descriptions.
4. **Memory Constraints:** TV devices aggressively kill apps that use too much RAM.

## 1. Custom Binary Format with Memory Mapping (FlatBuffers / Cap'n Proto)
Instead of a database, parse the XMLTV data and compile it into a highly optimized binary file (like a FlatBuffer). The app then uses Memory-Mapped Files (`mmap`) to read it.

*   **How it works:** You read the binary file directly from disk as if it were in RAM, with zero deserialization overhead.
*   **Pros:** 
    *   **Absolute maximum read performance.** "Now Playing" lookups take microseconds.
    *   Zero SQLite parsing overhead.
    *   Extremely low memory footprint (the OS manages the mapped memory pages).
*   **Cons:** 
    *   **No FTS.** You would have to build your own inverted index for search, or do linear scans (which are fast in memory, but maybe not fast enough for millions of words).
    *   **Immutable.** You cannot easily "update" a FlatBuffer. Refreshing the EPG means writing a completely new file in the background and swapping the pointer.

## 2. High-Performance Key-Value Stores (MMKV / LMDB / RocksDB)
Use a low-level K/V store. Keys would be designed for time-series range scans (e.g., `sourceId:channelId:startTime`) and values would be serialized program data (Protobuf).

*   **How it works:** MMKV (by Tencent) or LMDB use `mmap` under the hood. They are designed for insanely fast bulk inserts and range reads.
*   **Pros:** 
    *   Solves the eMMC bottleneck. K/V stores write sequentially, which eMMC flash storage handles vastly better than the random B-Tree page writes of SQLite.
    *   Very fast time-range queries.
*   **Cons:**
    *   **No FTS.** Like FlatBuffers, you lose the ability to do `MATCH 'news'` easily.
    *   Lose the safety and convenience of Room (requires custom DAOs).

## 3. Object Databases (ObjectBox / Realm)
Replace SQLite with an object-oriented NoSQL database optimized for edge devices.

*   **How it works:** ObjectBox is specifically famous for outperforming SQLite by 10x on bulk inserts.
*   **Pros:** 
    *   Drastically faster bulk ingestion than Room/SQLite.
    *   Keeps an ORM-like developer experience.
*   **Cons:** 
    *   Large library size footprint.
    *   FTS support is less mature than SQLite's battle-tested FTS5 module.
    *   Schema migrations can be notoriously painful compared to Room.

## 4. Hybrid Architecture (The "Split Data" Model)
SQLite struggles because we are shoving giant strings (plot descriptions, cast lists, URLs) into its B-Trees and duplicating them into FTS tables. 

*   **How it works:**
    1.  **SQLite (Thin):** Only stores `programId`, `title`, and the FTS index.
    2.  **Binary Store / K-V (Fat):** Stores the heavy metadata (descriptions, images, massive text blobs) indexed by `programId`.
*   **Pros:** 
    *   Reduces the SQLite database size by 80-90%.
    *   Bulk inserts into SQLite become drastically faster because page sizes are tiny.
    *   We keep SQLite's excellent Full-Text Search.
*   **Cons:** 
    *   Architectural complexity. You now have to maintain transaction safety across two different storage mediums.

## 5. In-Memory Cache with Background Snapshotting
*   **How it works:** Upon app launch, load the entire EPG into memory structures (HashMaps and binary search trees). All queries and FTS searches happen purely in RAM. When a refresh occurs, it updates RAM instantly, and a background thread serializes the memory state to disk (JSON or Protobuf) as a single sequential file write.
*   **Pros:** 
    *   Zero I/O blocking during app usage. Insanely fast.
*   **Cons:** 
    *   **OOM Risk.** An EPG with 100,000 programs can easily consume 50MB-100MB of RAM. Android TV devices (like the 2GB Shield) will ruthlessly kill the app while it's in the background.

## Conclusion for Fijerena
If moving away from SQLite entirely, a **Key-Value Store (MMKV/LMDB)** combined with a custom in-memory Trie/Inverted-Index for search is the most performant route for TV hardware.

However, the **Hybrid Architecture** (SQLite just for search + MMKV for heavy data) or simply **tuning SQLite correctly** (using Incremental Updates, FTS5, and WAL) are usually the sweet spots that balance developer sanity with high performance.
