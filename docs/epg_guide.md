# EPG (Electronic Program Guide) Implementation Guide

Fijerena has two EPG systems: a **Live TV Grid** for browsing channel schedules, and an **EPG Browser** for full-text searching across the entire XMLTV dataset. Both are powered by a shared XMLTV pipeline where the user provides XMLTV URLs in Settings.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [XMLTV Pipeline](#xmltv-pipeline)
3. [SQLite FTS Indexing](#sqlite-fts-indexing)
4. [EPG Grid (TV Guide)](#epg-grid-tv-guide)
5. [EPG Browser (Search)](#epg-browser-search)
6. [Settings & Configuration](#settings--configuration)
7. [Caching Strategy](#caching-strategy)
8. [Data Models Reference](#data-models-reference)
9. [File Inventory](#file-inventory)

---

## Architecture Overview

```
                   ┌──────────────────────┐
                   │   EPG Management      │
                   │  EpgSourceEntity[]    │
                   │  (URL, label, tz)     │
                   └──────────┬───────────┘
                              │
                 ┌────────────▼────────────┐
                 │    EpgFileManager        │
                 │   (singleton)            │
                 │  → Channel pipeline      │
                 │  → concurrent downloads  │
                 │  → parallel ingestion    │
                 └────────────┬─────────────┘
                              │
                 ┌────────────▼────────────┐
                 │     EpgIndexer           │
                 │  (SQLite FTS4 index)     │
                 │  → epg_index.db          │
                 └────────────┬─────────────┘
                              │
            ┌─────────────────┴─────────────────┐
            │                                   │
   ┌────────▼────────┐              ┌───────────▼───────────┐
   │  EPG Grid        │              │   EPG Browser         │
   │  XmltvEpgService │              │   XmltvSearchService  │
   │  → EpgViewModel  │              │   → EpgBrowserVM      │
   │  (24h window,    │              │   (FTS raw→FTS safe)   │
   │   50 channels)   │              │   (no upper limit,    │
   │                  │              │    500 results max)    │
   └─────────────────┘              └───────────────────────┘
```

---

## XMLTV Pipeline

### EpgFileManager

**Singleton** (`core/network/.../xmltv/EpgFileManager.kt`) managing the multi-source download-ingest pipeline.

**Key design decisions:**
- Uses OkHttp `newCall()` for HTTP requests (via `NetworkModule.okHttpClient`)
- 60-second connect timeout, 3-minute read timeout
- **Global Task Retries:** 5 attempts with exponential backoff (1m, 2m, 4m, 8m, 16m) for the entire refresh task
- **Download Retries:** 3 retries with exponential backoff (5s base) for individual file downloads
- **WorkManager Retries:** `EpgSyncWorker` uses `BackoffPolicy.LINEAR` at 10 minutes, both for the periodic schedule and for `EpgSyncDebugReceiver`'s one-shot request. WorkManager's ~30s default just keeps hammering a source that is actively rate-limiting.
- **Truncation Detection:** `input.read()` returning -1 only means the connection closed, which is indistinguishable from a clean end of body. After the read loop, `totalRead` is compared against the response's `Content-Length` when the server sends one; a mismatch is treated as a download error and takes the retry path, rather than surfacing much later as an `XmlPullParserException` deep in ingestion.
- 128KB I/O buffers
- `OutOfMemoryError` caught explicitly

**Change Detection:**

A refresh that would re-download and re-parse an unchanged source is pure waste. Three mechanisms, in order:

1. **Conditional request.** `downloadSource` sends `If-None-Match` / `If-Modified-Since` from the source's stored `etag` / `last_modified_header`. A `304 Not Modified` short-circuits with no body read at all.
2. **Content hash.** For non-`.gz` sources a SHA-256 is computed in the same read pass and compared to `last_content_sha256`. `.gz` sources cannot be hashed at download time — gzip's mtime header taints the raw bytes even when the decompressed content is identical — so `ingestDownloadedSource` hashes the *decompressed* stream instead (`hashDecompressedGzip`, a read-and-discard pass, not a parse) before committing to a real ingest.
3. **Staleness guard (`canSkipIngest`).** A hash match only skips ingestion within `STALENESS_FORCE_INGEST_MS` (24h) of the last real ingest. `ingestFromStream` drops programmes that ended more than 12h ago against wall-clock time (`cutoffEpoch`; there is no future limit), so a byte-identical static file re-ingested days later still clears out long-ended programmes — skipping it forever would let them pile up while the source kept reporting healthy refreshes.

All three need the live index to actually hold the source's rows (`EpgIndexer.hasProgrammesForSource`, checked once per download in `downloadSource` and carried on `DownloadedSource.indexHasRows`). With an empty index the validators are not sent (`buildDownloadRequest`) and the hash never skips, so the source is re-downloaded and re-ingested even when the upstream file has not changed. Two things used to defeat this (G-11, 2026-10-03): on the staging path `markIngested` — validators included — ran per source as soon as `ingestFromStream` finished, while the rows sat in staging until `swapAndRebuildFts` committed after *all* sources, so a kill or a swap failure in between left "ingested" stats pointing at an empty guide and every later refresh answered 304 / hash match; and both clear paths (safe mode "Clear caches", EPG management "Clear all data") destroyed `epg_index.db` without touching `epg_source`. Now `ingestDownloadedSource` returns an `IngestRecord` on the staging path and `swapThenMarkIngested` writes it only after the swap commits (the `useStaging = false` path still marks right after ingest, its rows being live already), and both clear paths call `EpgSourceDao.resetAllIngestionState()` next to `EpgIndexer.clearAll()` — bookkeeping columns only, so the `sync_epg_source_update` trigger does not queue a live-sync change.

A confirmed-unchanged source:
- skips `EpgIndexer.ingestFromStream` entirely,
- is **excluded** from `swapAndRebuildFts`'s id list at every call site — including it would delete its primary rows and transfer nothing back, since staging was never populated for it,
- carries its last known channel/programme counts forward via `EpgSourceDao.markUnchanged` rather than resetting them to zero,
- is flagged `unchanged = true` in its result, which the EPG management screens render as "Unchanged" in place of download/ingest durations (which would otherwise be stale numbers from whenever it last actually ran).

Validators and hash live on `epg_source` (`etag`, `last_modified_header`, `last_content_sha256`, added in `providers.db` MIGRATION_9_10).

**Channel-based producer-consumer pipeline:**

Downloads and ingestion are decoupled via a Kotlin `Channel<DownloadedSource>`. Downloads run concurrently as producers, while 2 parallel workers ingest files into SQLite.

- **Concurrency:** Up to 3 concurrent downloads on mobile (controlled by `Semaphore`), 2 on TV/fixed devices
- **Download phase:** Each source is downloaded to a cache file (`xmltv_source_<id>_tmp`). On success, the `DownloadedSource` is sent to the ingestion channel.
- **Ingestion phase:** 2 parallel workers read from the channel and ingest files into SQLite via `EpgIndexer.ingestFromStream()`.
- **Completion:** After all download coroutines finish, the channel is closed. The ingestion coroutine drains remaining items, then the pipeline completes by updating `EpgPipelineStatsEntity` in `providers.db`.

**Progress tracking (`ActiveSourceProgress`):**

Each active source has real-time progress tracked in a `ConcurrentHashMap<Long, ActiveSourceProgress>`:

| Field | Type | Description |
|-------|------|-------------|
| `label` | String | Source display name |
| `phase` | String | `"Downloading"`, `"Awaiting Ingestion"` or `"Ingesting"` |
| `progressPercent` | Int | 0-100 from bytes read, or -1 if unknown |
| `downloadedBytes` | Long | Bytes downloaded so far |
| `downloadTotalBytes` | Long | Content-Length from server, or -1 |
| `channels` | Int | Channels ingested so far (ingestion phase) |
| `programmes` | Int | Programmes ingested so far (ingestion phase) |

Download progress is computed from `downloadedBytes / contentLength`. Ingestion progress uses a `CountingInputStream` wrapper on the raw file input stream, computing `bytesRead / fileSize`. UI updates are throttled (every 512KB during download, every 50,000 programmes during ingestion).

**State machine (`MultiSourceState` sealed interface):**
| State | Fields | Description |
|-------|--------|-------------|
| `Idle` | — | No processing active |
| `Pending` | — | Waiting for network or confirmation |
| `Processing` | `completedCount`, `totalSources`, `activeSourceLabels`, `activeProgress`, `totalChannels`, `totalProgrammes`, `totalDownloadedBytes`, `completedSourceStats` | Actively processing sources with aggregate progress |
| `Retrying` | `attempt`, `maxAttempts`, `nextRetryAtMs`, `reason` | Task failed and is waiting for the next retry attempt |
| `Completed` | `sourcesProcessed`, `errors`, `sourceStats`, `totalChannels`, `totalProgrammes`, `totalDownloadBytes` | All sources processed, final stats |
| `Error` | `reason` | Processing failed after all retries |
| `Clearing` | — | Blocking data clear in progress |
| `Finalizing` | `phase`, `totalChannels`, `totalProgrammes`, `totalDownloadBytes` | Post-ingestion phase: index rebuild, FTS rebuild, or vacuum |

**Cancel support:**

`cancelProcessing()` cancels the coroutine `processJob` and calls `RefreshQueue.cancelAll()`, which cancels every currently executing task (up to 3 concurrently) and clears all pending tasks. The state is immediately set to `Idle`.

**Lifecycle:**
- `initialize()` — called from `MainActivity.onCreate()`, migrates legacy single-URL config, schedules the WorkManager periodic sync based on the user-selected interval
- `launchRefreshStale()` — refresh all sources older than `staleThresholdMs` (interval/2)
- `launchRefreshFailed()` — retry sources whose last attempt errored
- `launchRefreshSelected(selectedIds)` — refresh a user-selected subset of sources
- `launchProcessSingleSource(sourceId)` — process one source (download then ingest, no pipeline)
- `launchClearAllData()` — cancel processing, set state to `Clearing`, delegate to `EpgIndexer.clearAll()`
- `cancelProcessing()` — cancel the current processing job and all queued tasks
- `updateAutoRefreshSchedule(forceReschedule)` — (re)schedules the periodic `EpgSyncWorker` (`epg_sync`) at the selected interval; on a forced reschedule the first run is delayed to the configured refresh time (`epgRefreshTime`). "Never" cancels it.
- `refreshOutdatedSources(providerId)` — on Xtream session start (`XtreamSessionManager`), submits the stale sources to `RefreshQueue` as `epg_auto_refresh`
- Auto-refresh: `EpgSyncWorker` calls `processAllSources(staleSources)`, which runs `processAllSourcesInternal` directly (not through `RefreshQueue`) so the work stays under the worker's wake lock

Each source URL is managed via `EpgSourceEntity` in Room. Mobile background sync via `EpgSyncWorker` (WorkManager, periodic interval from settings).

### RefreshQueue

**Singleton** (`core/network/.../queue/RefreshQueue.kt`) providing priority-based task execution with up to 3 tasks running concurrently (`Semaphore(3)`) — not strictly sequential.

- Uses a `PriorityQueue<QueuedTask>` with a `Channel<Unit>(CONFLATED)` trigger
- Tasks are deduplicated by `id` against both the pending queue and already-executing tasks (`activeTasks`, guarded by the same mutex as the queue) — a duplicate submission coalesces into the running task's `Deferred` instead of racing a second execution
- `cancelAll()` cancels every active task's `Job` and clears all pending tasks
- Exposes `isProcessing`, `queuedTaskIds`, and `activeTaskIds` as `StateFlow`

### XmltvParser

**Object** (`core/network/.../xmltv/XmltvParser.kt`) providing streaming XMLTV parsing with `XmlPullParser`.

**Key functions:**
- `parse(inputStream, channelFilter, timeWindow)` — full parse with filters applied during parsing to minimize memory
- `parseChannelForIndex(parser)` -> `EpgChannelEntity` — used by EpgIndexer
- `parseProgrammeForIndex(parser, sourceId, timezoneOverrideHours)` -> `EpgProgrammeEntity` — used by EpgIndexer, accepts per-source timezone override
- `parseTimestamp(str)` — XMLTV timestamp parser with timezone override support

**Timezone override:** `@Volatile var timezoneOverrideHours: Int` — applied in `parseTimestamp()` to fix XMLTV sources that encode local times but mislabel them as UTC. Set per-source from `EpgSourceEntity.timezoneOffsetHours` before each ingestion pass.

---

## SQLite FTS Indexing

### Database Schema

**Room database** `epg_index.db` (version 17, WAL mode):

```
epg_channel
├── xmltv_id       TEXT  (Composite PK with source_id)
├── source_id      INTEGER (Composite PK with xmltv_id)
├── display_name   TEXT
└── icon_url       TEXT?

epg_channel_staging  (mirrors epg_channel; write target during staged ingestion,
                      promoted by the atomic swap in swapAndRebuildFts())

epg_programme
├── id               INTEGER  (PK, autoGenerate)
├── channel_id       TEXT     (FK → epg_channel.xmltv_id, CASCADE)
├── title            TEXT
├── title_lowercase  TEXT
├── description      TEXT?
├── category         TEXT?
├── start_epoch      LONG
├── end_epoch        LONG
└── source_id        LONG

Indices (7):
├── idx_programme_start          (start_epoch)
├── idx_programme_end            (end_epoch)
├── idx_programme_time_range     (start_epoch, end_epoch)
├── idx_programme_channel        (channel_id)
├── idx_programme_dedup          (channel_id, source_id, start_epoch) UNIQUE
├── idx_programme_source         (source_id)
└── idx_programme_channel_source (channel_id, source_id)

epg_programme_staging  (mirrors epg_programme; one unique index
                        idx_programme_staging_dedup on the same 3 columns;
                        write target during staged ingestion)

epg_programme_fts  (FTS4 virtual table, content=epg_programme, tokenizer=unicode61)
├── title          TEXT
└── description    TEXT?

epg_index_metadata
├── id                     INTEGER  (PK, always 1)
├── file_size_bytes        LONG
├── file_last_modified_ms  LONG
├── indexed_at_ms          LONG
├── channel_count          INTEGER
├── programme_count        INTEGER
└── timezone_offset_hours  INTEGER  (default 0)

epg_source (in providers.db)
├── id                           LONG     (PK, autoGenerate)
├── url                          TEXT
├── label                        TEXT
├── timezone_offset_hours        INT
├── added_at_ms                  LONG
├── last_ingested_at_ms          LONG
├── last_error                   TEXT?
├── enabled                      BOOLEAN
├── last_channels                INT
├── last_programmes              INT
├── last_download_bytes          LONG
├── ingest_method                TEXT     ("DOWNLOADED", "STREAMED", or "XTREAM_API")
├── last_ingestion_duration_ms   LONG
└── last_download_duration_ms    LONG
```

**Database configuration:**
- `PRAGMA synchronous = NORMAL` for performance
- `PRAGMA cache_size = -64000` (64MB default cache)
- `PRAGMA auto_vacuum = INCREMENTAL` for reclaimable space after deletes

For deep-dive technical details on SQLite freelist reclaim, lazy execution of PRAGMAs in Android's `SQLiteCursor`, WAL checkpointing, and Requery's 1024 page size workaround, see [EPG_INDEX_STORAGE.md](EPG_INDEX_STORAGE.md).

The FTS4 virtual table with `unicode61` tokenizer enables sub-100ms full-text search across millions of programmes.

### EpgIndexer

**Singleton** (`core/network/.../xmltv/epgindex/EpgIndexer.kt`) building the SQLite index from XMLTV streams.

**State machine:** `NotIndexed` -> `Indexing(progressPercent, channelsIndexed, programmesIndexed)` -> `Indexed(channelCount, programmeCount, indexedAtMs)` | `Failed(reason)`

**Key functions:**
- `initialize()` — restores `Indexed` state from metadata without re-indexing
- `setIndexing()` — sets state to `Indexing` if not already `Indexed`. Called once before parallel ingestion begins to coordinate state across concurrent source processing.
- `ingestFromStream(inputStream, sourceId, timezoneOverrideHours, onProgress)` — returns `IngestionStats(channelsIngested, programmesIngested)`. Uses 500-row batch INSERTs with Room `withTransaction`. Commits per-batch (not one giant transaction). Inserts channels with `IGNORE` conflict strategy, programmes with `REPLACE` on unique `(channel_id, source_id, start_epoch)`. Yields CPU between batches (`delay(5)` for channels, `delay(100)` for programmes) to avoid starving video playback. Skips programmes that ended more than 12 hours ago; no future limit.
- `ingestFromXtreamEpg(epgByStreamId, streamInfo, providerId)` — ingests EPG data from the Xtream API. Creates/upserts an `EpgSource` with `ingestMethod=XTREAM_API`, clears old data for that source, then batch-inserts.
- `swapAndRebuildFts(sourceIds)` — staging path: moves the sources' staging rows into the primary tables, rebuilds FTS and writes metadata in **one** transaction. WAL readers see the old guide + old FTS until the commit, then the new pair, so nothing is marked stale and search keeps working through the refresh. Failure rolls both back and restores the previous state; throws.
- `rebuildFtsAndUpdateState()` — direct (low-storage) path and standalone rebuilds: rebuild FTS index and update metadata after all sources processed. Internally calls `markFtsStale()` at entry (so the old index remains valid during the dispatch gap, degrading only for the actual rebuild window) and `markFtsClean()` on success. Callers do not call these flags themselves. Triggers an update to `EpgPipelineStatsEntity` in `providers.db` with the final run summary.
- `clearAll()` — saves source configs, destroys DB file (instant regardless of data size), Room recreates schema, restores sources with stats reset
- `purgeOldProgrammes(cutoffEpoch)` — delete old programmes with FTS rebuild and incremental vacuum
- `incrementalVacuum()` — reclaims free pages via `PRAGMA incremental_vacuum`

**Clear All Data strategy:**

Uses DB destroy+recreate instead of `DELETE FROM` (which takes 10+ minutes on 4M+ rows):

1. Save all `EpgSourceEntity` records (user configuration)
2. Close DB and delete the file + WAL/SHM files via `EpgIndexDatabase.destroy(context)`
3. Call `EpgIndexDatabase.getInstance(context)` which rebuilds from Room schema
4. Restore sources with ingestion stats reset (`lastIngestedAtMs=0`, counts zeroed, error cleared)

The ViewModel uses a `_dbGeneration` counter with `flatMapLatest` so the sources `Flow` re-subscribes after DB recreation.

After all sources are ingested, `EpgFileManager` runs (inline, under the worker's wake lock) `swapAndRebuildFts()` on the staging path — search is never blocked — or `rebuildFtsAndUpdateState()` on the direct path, where FTS has been stale since `beginBulkIngestion()` dropped its triggers:
```sql
INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')
```

---

## EPG Grid (TV Guide)

The EPG Grid is a 24-hour channel schedule view accessible from the Category Grid screen for Live TV.

### XmltvEpgService

**Class** (`core/network/.../xmltv/XmltvEpgService.kt`) bridging XMLTV data into `EpgResponse` format.

**Three-layer data resolution:**
1. **Parsed results cache** (SharedPreferences, 12h TTL, keyed by the index build it was parsed from — `EpgIndexState.Indexed.indexedAtMs`; a rebuilt index makes it a miss, so a refreshed guide shows without pressing Refresh) — instant return
2. **SQLite index** — `epg_index.db` queried for the requested channels (needs `EpgIndexState.Indexed`); no XMLTV file is kept or parsed here
3. **Provider-native EPG** — fallback to Xtream `get_simple_data_table` API (via `MediaRepository.getGuideForItems`, which also reports which layer answered — `GuideData(epg, source: GuideSource, updatedAtMs)`: the index build time for XMLTV, the newest `xtream_epg_cache` row for native; `getEpgBulkForItems` is the same call without the provenance)

**Channel matching** (4-tier fallback): exact `epgChannelId` -> case-insensitive `epgChannelId` -> exact display name -> normalized name match.

### EpgViewModel

**ViewModel** (`core/ui/.../viewmodels/EpgViewModel.kt`) driving the grid UI.

**State (GD1):**
- `Loading`
- `Ready(channelRows, timeSlots, currentTimeSlot, selectedDate, listedCount, totalCount, source, updatedAtMs, devStats)` — `listedCount` counts rows with at least one programme on the day (a channel that answered `[]` is not listed); both screens show "N of M channels have listings · <XMLTV guide|source EPG> · updated <relative>" under the title, and `devStats` ("x/y channels answered · Nms") as a dimmed line beneath it in dev mode only — never in the title.
- `NoListings(reason, selectedDate, source?, updatedAtMs?)` — every row empty for the day; replaces the blank grid with a centred message. `reason`: `STALE` (listings exist but stop before the day), `INDEX_EMPTY` (index built, nothing for these channels), `NONE` (no index, no native data).
- `NoGuide` — the source has no native EPG and the index is `NotIndexed`; the message points at Settings → Source & guide.
- `Error(message)` — channels or guide failed to load.

**Flow:**
1. Loads the channel set: `recent` / `favorites` resolve through `CategoryViewModel.virtualCategoryItems` (the repository's Recent list / favourites snapshot), any other id through `repository.getItems`; category-marker rows (`##### 4K #####`, `MediaItem.isCategoryMarker`) are dropped, then the first 50 kept
2. Calls `repository.getGuideForItems()` (tries XMLTV first, falls back to provider; says which answered and when its data was built)
3. Builds `EpgChannelRow` list filtered by selected date; zero listed rows → `NoListings`
4. Generates 48 x 30-minute `TimeSlot` objects covering the full day

**Features:** Date navigation, force refresh, in-grid search, dev mode load metrics.

### Grid Layout

**Layout engine** (`core/ui/.../guide/GuideLayout.kt`, GD2, unit-tested in `GuideLayoutTest`): pure Kotlin, shared by both platforms. One horizontal scale (1 h = 240 dp, `DP_PER_MINUTE`; the caller converts with its density and UI scale) maps epoch seconds onto a canvas whose x = 0 is the selected day's local midnight: `xFor`, `timeAt`, `widthFor` (true duration — a short programme is never stretched to fit its label; `GuideCell.labelFits` is false below the minimum label width and the label is dropped, the slot keeps its width), `visibleRange` / `composeRange` (the viewport ± one viewport, quantised to half-viewport steps, empty until the viewport is measured), `cellsIn` (a row's programmes overlapping a range, clipped to the day, so a programme straddling midnight starts at x = 0), `tickMarks` (every 30 min on the clock's half-hours), and `programAt` (the programme on at a time, else the nearest — the Up/Down rule).

**TV** (`tv/.../feature/epg/TvEpgGuideScreen.kt` + `TvGuideGrid.kt`, GD2): `TvEpgGuideScreen` renders `Loading`, `Ready` and `NoListings` inside `TvGuideGrid`'s chrome and only `NoGuide` / `Error` as `TvErrorState`. The header holds the title, a "date · N of M channels have listings · source · updated …" line (plus the dev-stats line in dev mode) and a row of labelled buttons — Previous Day, Now, Next Day, Search, Refresh — that keep their icon and text visible when focused. Below it a fixed channel column (`epgChannelColumnWidth`) and a time canvas: the ruler and every row are placed by `GuideLayout` and scrolled by **one** `ScrollState` (each row is a `Layout` of cells at `xFor(start)`, never a `LazyRow`, never a shared `LazyListState`), in a vertical `LazyColumn` under the pinned ruler. Rows compose only the cells in `composeRange`. A vertical now line is drawn across the ruler and the rows, past cells are dimmed, the on-air cell has an accent bar and accent title. The canvas ignores bring-into-view (`LocalBringIntoViewSpec` set to a no-op), so only the grid's own moves scroll it. `NoListings` shows "No listings", the reason and Refresh in the same chrome, so day navigation stays available.

Focus (decided by `GuideFocus` in `onPreviewKeyEvent`, not by geometric search): first open lands on the on-air cell of the first channel with a programme on now (else the first channel with listings), with now about a third in from the canvas's left edge. Left/Right move by programme and scroll the canvas so the new cell's start is on screen; Left from a row's first programme goes to its channel cell, Left on a channel cell stays, Right from a channel cell goes to the on-air cell when now is on screen, else the cell at the canvas's left edge. Up/Down keep the **time**: the target row's cell containing the focused cell's start (its visible start when it began off-screen), else the nearest; the time sticks across rows until a horizontal move, and a row without listings lands on its channel cell without losing it. Channel Up/Down page by the visible row count. Up from the first row is the only way into the header (lands on the last button used, else Previous Day); Down from the header returns to the last cell. "Now" on today scrolls to now and focuses the on-air cell of the current row; on another day it reloads today and then does the same. Day changes keep focus on the button pressed. Search (the in-grid title filter, until GD5) replaces the grid under the header; Back closes it and returns to the cell the user was on, Back on the grid leaves the guide — both intercepted in `onPreviewKeyEvent` on the root. OK on a programme or channel opens the Live TV preview on that channel; Back returns to the cell (`NavReturnFocus`). `scripts/focus-walks/guide.txt` walks it.

**Mobile** (`mobile/.../feature/epg/MobileEpgGuideScreen.kt` + `MobileEpgTimeline.kt`): Scaffold with TopAppBar, LazyColumn + horizontal timeline, swipe date navigation, pull-to-refresh.

---

## EPG Browser (Search)

Standalone screen for full-text searching across the entire XMLTV dataset. Accessed from the home screen (`ContentTypeSelection`) via the date-range icon (only visible when `EpgIndexer.state` is `Indexed`).

### XmltvSearchService

**Class** (`core/network/.../xmltv/XmltvSearchService.kt`) implementing dual-path search.

**Search strategy (in order; no XML-scan fallback exists):**
1. **Raw FTS query** — preserves user-provided FTS operators (OR/NEAR/NOT), appends a prefix wildcard `*` to the last token. Typically <100ms.
2. **Safe FTS retry** — if the raw query returns nothing (or throws, e.g. malformed syntax), strips `" * ( ) :` and retries as a quoted AND-style phrase query.

If the index isn't built yet (`EpgIndexState.NotIndexed`), `search()` returns `null` directly. If the FTS index is stale (`isFtsStale()` — direct-path refresh, or an interrupted rebuild being redone), both FTS steps are skipped for a title-only `LIKE '%…%'` scan of `epg_programme.title_lowercase` (`EpgIndexDao.searchByTitleLike`, `EpgSearchPath.LIKE_FALLBACK`; `\ % _` escaped) — seconds on 2M+ rows, same 10 s timeout. Only if that times out or fails does it throw `EpgIndexBusyException`; `EpgBrowserViewModel` shows why (`UiState.IndexBusy`) and reruns the query once the index is `Indexed`. The staging path never marks it stale.

Programme search covers every programme that hasn't ended yet, with no upper limit (ingest has none either). Channel search covers now to 2 hours ahead. Max 500 results.

### EpgBrowserViewModel

**ViewModel** (`core/ui/.../viewmodels/EpgBrowserViewModel.kt`) orchestrating search and paging.

**States:** `Idle` | `NoEpgFile` | `Searching` | `Indexing(progressPercent, programmesIndexed)` | `Results(query, dateGroups, totalPrograms, totalAirings, truncated, searchTimeMs, searchedFromIndex)` | `Error(message)`

Results are grouped by start date (Today, Tomorrow, weekday name, or full date for later days). Within each date group, programmes are grouped by normalized title+description and sorted by earliest airing time. Paging 3 integration for large datasets (2M+ programmes).

### Search UI

**TV** (`tv/.../feature/epgbrowser/TvEpgBrowserScreen.kt`): GlassPanel search, LazyColumn with date group headers, D-pad navigable, search source indicator, indexing progress banner.

**Mobile** (`mobile/.../feature/epgbrowser/MobileEpgBrowserScreen.kt`): Scaffold, LazyColumn with sticky date headers and expandable programme cards, linear progress during indexing.

---

## Settings & Configuration

EPG is configured via **Settings -> Manage EPG Data** (`Screen.EpgManagement(providerId)`). EPG sources belong to a specific provider and multiple XMLTV sources can be added, edited, and deleted.

**`EpgSourceEntity` fields:**

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Auto-generated primary key |
| `url` | String | XMLTV source URL |
| `label` | String | User-visible label |
| `timezoneOffsetHours` | Int | Per-source timezone override (-12 to +14) |
| `addedAtMs` | Long | When the source was added |
| `lastIngestedAtMs` | Long | Epoch ms of last successful ingest (0 = never) |
| `lastError` | String? | Error message from last failed attempt |
| `enabled` | Boolean | Whether source is included in refresh |
| `lastChannels` | Int | Channel count from last ingest |
| `lastProgrammes` | Int | Programme count from last ingest |
| `lastDownloadBytes` | Long | Download size from last ingest |
| `ingestMethod` | String | `"DOWNLOADED"`, `"STREAMED"`, or `"XTREAM_API"` |

**Status indicators (UI):** green = ingested within `staleThresholdMs` (interval/2), yellow = stale (older than `staleThresholdMs`), red = error, gray = disabled.

**Actions:** Refresh All, Refresh Selected, Cleanup Files, Purge >2 days, Clear All Data (with confirmation dialog), Cancel (visible during processing).

**Refresh Interval:** User-selectable interval (4h, 8h, 12h, 24h, 48h) or "Never". The **stale threshold** (`staleThresholdMs`) is set to **interval/2** — a source is considered stale after half its refresh period has elapsed. This gives the periodic `EpgSyncWorker` a wide catch-up window if they fire slightly off-schedule. If the interval is ≤0 ("Never"), the threshold defaults to 24h. The WorkManager periodic schedule also updates to the selected interval.

**Selective refresh:** Checkboxes on each source row allow selecting multiple sources. A "Refresh Selected (N)" button appears when sources are selected, triggering refresh only for chosen sources.

**Source deletion cleanup:** Deleting a source also removes all associated channels and programmes from the index database.

**Import date filter:** During ingestion, programmes that ended more than 12 hours ago are skipped; nothing ahead is dropped. This reduces database size and speeds up indexing.

**Per-source progress:** Both mobile and TV show per-source progress with percentage, phase label ("Downloading"/"Awaiting Ingestion"/"Ingesting"), byte counts, and channel/programme counts. A cancel button is visible during processing. During `Clearing` state, a blocking overlay is shown.

**Timezone override behavior:** The per-source offset is applied at parse time. Changing it requires re-ingesting the source because epoch values stored in SQLite depend on the parse-time timezone.

**EpgSourceDao notable queries:**
- `resetAllIngestionState()` — zeroes out all ingestion stats and errors across all sources
- `markIngested()` — records successful ingest with stats
- `markError()` — records error for a source
- `getStaleSources(thresholdMs)` — finds sources needing refresh

---

## Caching Strategy

| Cache | Location | TTL | Purpose |
|-------|----------|-----|---------|
| XMLTV temp file (mobile) | `cacheDir/xmltv_source_<id>_tmp` | Deleted after ingest | Download staging |
| SQLite index | `databases/epg_index.db` | Until next refresh | FTS4 search index |
| Parsed EPG results | SharedPreferences per-provider | 12h, and only for the index build it was parsed from (`indexedAtMs`) | XmltvEpgService grid cache |
| Channel matcher maps | In-memory (`EpgChannelMatcher`), process lifetime | Until next sync | `epgChannelId`/normalized-name -> `streamId` lookup maps, used by EPG Browser channel matching |

No persistent XMLTV file. Mobile downloads to a temp file first, then ingests from file, then deletes the temp file.

### Channel Matcher Cache: Persistent Disk Tier (Considered, Deferred)

A persistent (disk-backed) L2 cache for `EpgChannelMatcher` was proposed to avoid rebuilding its normalization maps on cold start (~300-500ms on an NVIDIA Shield, since `EpgBrowserViewModel` must fetch all live streams and re-normalize on first use after a process restart). The proposed design: a `epg_matcher_cache` table in `SettingsDatabase` (`providerId` PK, serialized map blob, a version tag hashed from the stream data for staleness detection), with `EpgChannelMatcher.getOrCreate()` checking memory (L1, 0ms) -> disk cache (L2, ~50ms) -> full rebuild (L3, >300ms), and `ProviderSyncManager` populating L2 after each background sync.

**Decision: deferred.** The "Memory Warming" approach actually implemented — populating the in-memory matcher cache immediately after a sync completes, rather than persisting it to disk — covers most real usage (the cache is already warm by the time a user opens the EPG Browser) without the added DB schema and serialization complexity. The disk-tier design is recorded here in case cold-start latency on this path becomes a real complaint; the trade-off was judged as marginal gains (~200-300ms, on a path that already shows a loading state) against meaningful added complexity.

**Network constraints:**
- EPG downloads: confirmation dialog on cellular, auto-refresh on WiFi/Ethernet
- Streaming downloads (128KB buffers, zero in-memory buffering)
- **Automatic Retries:** Global task retry (5 attempts, exponential backoff up to 16m), plus 3 retries for individual source downloads.

**Memory safety:**
- OkHttp with streaming response body
- Streaming `XmlPullParser` (no in-memory DOM tree)
- 500-row batch INSERTs in Room `withTransaction`
- `OutOfMemoryError` caught at every I/O boundary with `System.gc()` and fallback paths
- CPU yielding between batches (`delay(5)` for channels, `delay(100)` for programmes) to avoid starving video playback

---

## Data Models Reference

### Xtream EPG Models (`core/player/.../model/EpgModels.kt`)

```kotlin
data class EpgProgram(val id: String, val epgId: String?, val title: String,
                      val start: String, val end: String, val description: String?,
                      val channelId: String?, val hasArchive: Int?)
data class EpgResponse(val listings: List<EpgProgram>)
data class EpgChannelRow(val channel: MediaItem, val programs: List<EpgProgram>)
data class TimeSlot(val startTime: Long, val endTime: Long, val slotIndex: Int)
```

### XMLTV Models (`core/network/.../xmltv/XmltvModels.kt`)

```kotlin
data class XmltvChannel(val id: String, val displayName: String, val iconUrl: String?)
data class XmltvProgramme(val channelId: String, val startEpoch: Long, val endEpoch: Long,
                          val title: String, val description: String?, val category: String?)
data class XmltvData(val channels: Map<String, XmltvChannel>,
                     val programmes: Map<String, List<XmltvProgramme>>)
data class XmltvSearchResult(val channels: Map<String, XmltvChannel>,
                             val programmes: List<XmltvProgramme>,
                             val totalScanned: Int, val truncated: Boolean,
                             val searchedFromIndex: Boolean)
```

### EPG Browser Models (`core/network/.../xmltv/EpgBrowserModels.kt`)

```kotlin
data class EpgBrowserProgram(val title: String, val description: String?,
                             val category: String?, val airings: List<EpgBrowserAiring>)
data class EpgBrowserAiring(val channelId: String, val channelName: String,
                            val channelIconUrl: String?, val startEpoch: Long, val endEpoch: Long)
data class EpgBrowserDateGroup(val dateLabel: String, val dayStartEpoch: Long,
                               val programs: List<EpgBrowserProgram>)
```

### EpgFileManager Models (`core/network/.../xmltv/EpgFileManager.kt`)

```kotlin
data class SourceStats(val sourceId: Long, val label: String,
                       val downloadBytes: Long, val channelsIngested: Int,
                       val programmesIngested: Int, val error: String?)

data class ActiveSourceProgress(val label: String, val phase: String,
                                val progressPercent: Int, val downloadedBytes: Long,
                                val downloadTotalBytes: Long, val channels: Int,
                                val programmes: Int)
```
### Room Entities

**Settings Entities (`core/network/.../provider/`)**

```kotlin
data class EpgSourceEntity(val id: Long, val url: String, val label: String,
                           val timezoneOffsetHours: Int, val addedAtMs: Long,
                           val lastIngestedAtMs: Long, val lastError: String?,
                           val enabled: Boolean, val lastChannels: Int,
                           val lastProgrammes: Int, val lastDownloadBytes: Long,
                           val ingestMethod: String, val lastIngestionDurationMs: Long,
                           val lastDownloadDurationMs: Long, val providerId: Long,
                           // Change detection — see "Change Detection" above
                           val lastContentSha256: String?, val etag: String?,
                           val lastModifiedHeader: String?)

data class EpgPipelineStatsEntity(val id: Int = 1, val updatedAtMs: Long,
                                  val durationMs: Long, val sourcesProcessed: Int,
                                  val errors: Int, val totalChannels: Int,
                                  val totalProgrammes: Int)
```

**Index Entities (`core/network/.../xmltv/epgindex/`)**

```kotlin
data class EpgChannelEntity(val xmltvId: String, val sourceId: Long, val displayName: String, val iconUrl: String?)
data class EpgProgrammeEntity(val id: Long, val channelId: String, val title: String,
                              val titleLowercase: String, val description: String?,
                              val category: String?, val startEpoch: Long, val endEpoch: Long,
                              val sourceId: Long)
data class EpgProgrammeFts(val title: String, val description: String?)  // FTS4 content table
data class EpgIndexMetadata(val id: Int, val fileSizeBytes: Long, val fileLastModifiedMs: Long,
                            val indexedAtMs: Long, val channelCount: Int,
                            val programmeCount: Int, val timezoneOffsetHours: Int)
data class EpgSearchResultRow(val id: Long, val channelId: String, val title: String,
                              val titleLowercase: String, val description: String?,
                              val category: String?, val startEpoch: Long, val endEpoch: Long,
                              val channelDisplayName: String, val channelIconUrl: String?)
```

---

## File Inventory

### Core Services (`core/network/.../xmltv/`)

| File | Type | Description |
|------|------|-------------|
| `EpgFileManager.kt` | Singleton | Channel-based download-ingest pipeline manager |
| `XmltvParser.kt` | Object | Streaming XMLTV parser with timezone override |
| `XmltvSearchService.kt` | Class | Two-tier FTS search (raw query, then sanitized safe-AND retry); title-only LIKE scan while FTS is stale; no XML-scan fallback |
| `XmltvEpgService.kt` | Class | XMLTV -> EpgResponse adapter for grid |
| `XmltvModels.kt` | Data | XMLTV channel/programme/search models |
| `EpgBrowserModels.kt` | Data | Browser UI models (program + airings) |
| `EpgSyncWorker.kt` | CoroutineWorker | Periodic background EPG sync on TV and mobile (WorkManager, foreground service so DNS works in Doze); calls `getStaleSources()` (or `getAllSources()` when `force=true` input data) + `processAllSources()` directly in `doWork()` to hold the wake lock for the full download + ingestion cycle |
| `EpgSyncDebugReceiver.kt` | BroadcastReceiver | Debug-only receiver (`src/debug` source set, guarded by the `DUMP` permission so only adb shell can send it); enqueues an immediate `EpgSyncWorker` OneTimeWorkRequest with `force=true`. Trigger: `adb shell am broadcast -a org.njarasoa.fijerena.DEBUG_EPG_SYNC -p org.njarasoa.fijerena` |

### Queue (`core/network/.../queue/`)

| File | Type | Description |
|------|------|-------------|
| `RefreshQueue.kt` | Singleton | Priority-based task executor, up to 3 tasks at once, de-duplicated by id, with cancel support |
| `RefreshTask.kt` | Interface + object | Task contract (id, priority, execute) and the `RefreshPriority` constants |

### SQLite Indexing (`core/network/.../xmltv/epgindex/`)

| File | Type | Description |
|------|------|-------------|
| `EpgIndexer.kt` | Singleton | Index builder (streaming + batch transactional) |
| `EpgIndexDatabase.kt` | Room DB | Database singleton (v17, WAL, incremental auto-vacuum, with destroy/recreate) |
| `EpgIndexDao.kt` | DAO | FTS MATCH, LIKE, paged queries |
| `EpgProgrammeEntity.kt` | Entity | Programme table + FTS4 virtual table |
| `EpgChannelEntity.kt` | Entity | Channel table |
| `EpgIndexMetadata.kt` | Entity | File state tracking for staleness |
| `EpgIndexState.kt` | Sealed | Indexing state machine |

`EpgSourceEntity.kt` and `EpgSourceDao.kt` (guide source config and its CRUD, `resetAllIngestionState()`) live in `core/network/.../provider/`, since `epg_source` is a `providers.db` table.
| `EpgSearchResultRow.kt` | Data | JOIN query result model |

### ViewModels (`core/ui/.../viewmodels/`)

| File | Type | Description |
|------|------|-------------|
| `EpgViewModel.kt` | ViewModel | EPG Grid controller with date navigation |
| `EpgViewModelFactory.kt` | Factory | Creates MediaRepository per category |
| `EpgBrowserViewModel.kt` | ViewModel | Browser with dual-path search + Paging 3 |
| `EpgBrowserViewModelFactory.kt` | Factory | Singleton context wrapper |

### UI Screens

| File | Platform | Description |
|------|----------|-------------|
| `tv/.../feature/epg/TvEpgGuideScreen.kt` | TV | EPG Grid wrapper |
| `tv/.../feature/epg/TvGuideGrid.kt` | TV | Grid implementation (header, channels + time canvas, focus) |
| `core/ui/.../guide/GuideLayout.kt` | Shared | Time → x layout engine for the guide grids |
| `tv/.../feature/epgbrowser/TvEpgBrowserScreen.kt` | TV | EPG Search (Browser) with GlassPanel |
| `mobile/.../feature/epg/MobileEpgGuideScreen.kt` | Mobile | EPG Grid with Scaffold |
| `mobile/.../feature/epg/MobileEpgTimeline.kt` | Mobile | Horizontal timeline component |
| `mobile/.../feature/epgbrowser/MobileEpgBrowserScreen.kt` | Mobile | Expandable card browser |

### Integration Points

| File | How EPG is used |
|------|-----------------|
| `MediaRepository.kt` | `getEpgBulkForItems()` — tries XMLTV then falls back to provider |
| `AppSettings.kt` | `epgUrl`, `epgTimezoneOffsetHours`, `epgAutoRefreshEnabled` |
| `CategoryViewModel.kt` | Loads "What's On Now" for Live TV via `getEpgBulkForItems()` |
| `Screen.kt` (navigation) | `Screen.EpgGuide(categoryId, name)`, `Screen.EpgBrowser` |
| `SettingsScreen.kt` (TV + Mobile) | EPG management, download controls |
