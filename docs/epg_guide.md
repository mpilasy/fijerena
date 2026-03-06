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
   │  (24h window,    │              │   (FTS→LIKE→XML scan) │
   │   50 channels)   │              │   (7-day window,      │
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
- 3 retries with exponential backoff (5s base, multiplied by attempt number)
- 128KB I/O buffers
- `OutOfMemoryError` caught explicitly

**Channel-based producer-consumer pipeline:**

Downloads and ingestion are decoupled via a Kotlin `Channel<DownloadedSource>`. Downloads run concurrently as producers, while 2 parallel workers ingest files into SQLite.

- **Concurrency:** Up to 3 concurrent downloads on mobile (controlled by `Semaphore`), 2 on TV/fixed devices
- **Download phase:** Each source is downloaded to a cache file (`xmltv_source_<id>_tmp`). On success, the `DownloadedSource` is sent to the ingestion channel.
- **Ingestion phase:** 2 parallel workers read from the channel and ingest files into SQLite via `EpgIndexer.ingestFromStream()`.
- **Completion:** After all download coroutines finish, the channel is closed. The ingestion coroutine drains remaining items, then the pipeline completes.

**Progress tracking (`ActiveSourceProgress`):**

Each active source has real-time progress tracked in a `ConcurrentHashMap<Long, ActiveSourceProgress>`:

| Field | Type | Description |
|-------|------|-------------|
| `label` | String | Source display name |
| `phase` | String | `"Downloading"` or `"Ingesting"` |
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
| `Processing` | `completedCount`, `totalSources`, `activeSourceLabels`, `activeProgress`, `totalChannels`, `totalProgrammes`, `totalDownloadedBytes`, `completedSourceStats` | Actively processing sources with aggregate progress |
| `Completed` | `sourcesProcessed`, `errors`, `sourceStats`, `totalChannels`, `totalProgrammes`, `totalDownloadBytes` | All sources processed, final stats |
| `Error` | `reason` | Processing failed |
| `Clearing` | — | Blocking data clear in progress |

**Cancel support:**

`cancelProcessing()` cancels the coroutine `processJob` and calls `RefreshQueue.cancelAll()`, which cancels the currently executing task and clears all pending tasks. The state is immediately set to `Idle`.

**Lifecycle:**
- `initialize()` — called from `MainActivity.onCreate()`, migrates legacy single-URL config, schedules auto-refresh, schedules WorkManager periodic sync on mobile
- `launchProcessAllSources()` — process all enabled sources via the pipeline
- `launchProcessSources(sources, taskId)` — process a pre-filtered list of sources
- `launchProcessSingleSource(sourceId)` — process one source (download then ingest, no pipeline)
- `launchClearAllData()` — cancel processing, set state to `Clearing`, delegate to `EpgIndexer.clearAll()`
- Auto-refresh: checks for stale sources (>24h) every 4 hours, refreshes only stale ones

Each source URL is managed via `EpgSourceEntity` in Room. Mobile background sync via `EpgSyncWorker` (WorkManager, 24h periodic).

### RefreshQueue

**Singleton** (`core/network/.../queue/RefreshQueue.kt`) providing priority-based sequential task execution.

- Uses a `PriorityQueue<QueuedTask>` with a `Channel<Unit>(CONFLATED)` trigger
- Tasks are deduplicated by `id` — submitting a task with an existing ID replaces it
- Tracks `currentJob` for the actively executing task
- `cancelAll()` cancels `currentJob` and clears all pending tasks
- Exposes `isProcessing` and `queuedTaskIds` as `StateFlow`

### XmltvParser

**Object** (`core/network/.../xmltv/XmltvParser.kt`) providing streaming XMLTV parsing with `XmlPullParser`.

**Key functions:**
- `parse(inputStream, channelFilter, timeWindow)` — full parse with filters applied during parsing to minimize memory
- `searchByTitle(inputStream, query, timeWindowSeconds)` — streaming title search (fallback when no SQLite index)
- `parseChannelForIndex(parser)` -> `EpgChannelEntity` — used by EpgIndexer
- `parseProgrammeForIndex(parser, sourceId, timezoneOverrideHours)` -> `EpgProgrammeEntity` — used by EpgIndexer, accepts per-source timezone override
- `parseTimestamp(str)` — XMLTV timestamp parser with timezone override support

**Timezone override:** `@Volatile var timezoneOverrideHours: Int` — applied in `parseTimestamp()` to fix XMLTV sources that encode local times but mislabel them as UTC. Set per-source from `EpgSourceEntity.timezoneOffsetHours` before each ingestion pass.

---

## SQLite FTS Indexing

### Database Schema

**Room database** `epg_index.db` (version 8, WAL mode):

```
epg_channel
├── xmltv_id       TEXT  (PK)
├── display_name   TEXT
└── icon_url       TEXT?

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

Indices:
├── idx_programme_start        (start_epoch)
├── idx_programme_end          (end_epoch)
├── idx_programme_time_range   (start_epoch, end_epoch)
├── idx_programme_channel      (channel_id)
└── idx_programme_title_lower  (title_lowercase)

epg_programme_fts  (FTS4 virtual table, content=epg_programme, tokenizer=unicode61)
└── title          TEXT

epg_index_metadata
├── id                     INTEGER  (PK, always 1)
├── file_size_bytes        LONG
├── file_last_modified_ms  LONG
├── indexed_at_ms          LONG
├── channel_count          INTEGER
├── programme_count        INTEGER
└── timezone_offset_hours  INTEGER  (default 0)

epg_source
├── id                     LONG     (PK, autoGenerate)
├── url                    TEXT
├── label                  TEXT
├── timezone_offset_hours  INT
├── added_at_ms            LONG
├── last_ingested_at_ms    LONG
├── last_error             TEXT?
├── enabled                BOOLEAN
├── last_channels          INT
├── last_programmes        INT
├── last_download_bytes    LONG
└── ingest_method          TEXT     ("DOWNLOADED", "STREAMED", or "XTREAM_API")
```

**Database configuration:**
- `PRAGMA synchronous = NORMAL` for performance
- `PRAGMA cache_size = -8000` (8MB cache)
- `PRAGMA auto_vacuum = INCREMENTAL` for reclaimable space after deletes

The FTS4 virtual table with `unicode61` tokenizer enables sub-100ms full-text search across millions of programmes.

### EpgIndexer

**Singleton** (`core/network/.../xmltv/epgindex/EpgIndexer.kt`) building the SQLite index from XMLTV streams.

**State machine:** `NotIndexed` -> `Indexing(progressPercent, channelsIndexed, programmesIndexed)` -> `Indexed(channelCount, programmeCount, indexedAtMs)` | `Failed(reason)`

**Key functions:**
- `initialize()` — restores `Indexed` state from metadata without re-indexing
- `setIndexing()` — sets state to `Indexing` if not already `Indexed`. Called once before parallel ingestion begins to coordinate state across concurrent source processing.
- `ingestFromStream(inputStream, sourceId, timezoneOverrideHours, onProgress)` — returns `IngestionStats(channelsIngested, programmesIngested)`. Uses 500-row batch INSERTs with Room `withTransaction`. Commits per-batch (not one giant transaction). Inserts channels with `IGNORE` conflict strategy, programmes with `REPLACE` on unique `(channel_id, start_epoch)`. Yields CPU between batches (`delay(5)` for channels, `delay(100)` for programmes) to avoid starving video playback. Skips programmes whose end time is before yesterday.
- `ingestFromXtreamEpg(epgByStreamId, streamInfo, providerId)` — ingests EPG data from the Xtream API. Creates/upserts an `EpgSource` with `ingestMethod=XTREAM_API`, clears old data for that source, then batch-inserts.
- `rebuildFtsAndUpdateState()` — rebuild FTS index and update metadata after all sources processed
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

After all sources are ingested, `EpgFileManager` calls `rebuildFtsAndUpdateState()` to rebuild the FTS index:
```sql
INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')
```

---

## EPG Grid (TV Guide)

The EPG Grid is a 24-hour channel schedule view accessible from the Category Grid screen for Live TV.

### XmltvEpgService

**Class** (`core/network/.../xmltv/XmltvEpgService.kt`) bridging XMLTV data into `EpgResponse` format.

**Three-layer data resolution:**
1. **Parsed results cache** (SharedPreferences, 12h TTL) — instant return
2. **Local XMLTV file** — parse from `xmltv_global.xml` with channel + time filters
3. **Provider-native EPG** — fallback to Xtream `get_simple_data_table` API

**Channel matching** (4-tier fallback): exact `epgChannelId` -> case-insensitive `epgChannelId` -> exact display name -> normalized name match.

### EpgViewModel

**ViewModel** (`core/ui/.../viewmodels/EpgViewModel.kt`) driving the grid UI.

**State:** `Loading` -> `Success(channelRows, timeSlots, currentTimeSlot, selectedDate)` | `Error`

**Flow:**
1. Loads items for category (max 50 channels)
2. Calls `repository.getEpgBulkForItems()` (tries XMLTV first, falls back to provider)
3. Builds `EpgChannelRow` list filtered by selected date
4. Generates 48 x 30-minute `TimeSlot` objects covering the full day

**Features:** Date navigation, force refresh, in-grid search, dev mode load metrics.

### Grid Layout

**TV** (`tv/.../feature/epg/EpgGuideScreen.kt` + `EpgGridLayout.kt`): Two-pane (20% channels + 80% time grid), 48 x 30-min slots, current time highlighted, auto-scroll, synchronized scrolling, D-pad navigable, date navigation + "Jump to Now".

**Mobile** (`mobile/.../feature/epg/MobileEpgGuideScreen.kt` + `MobileEpgTimeline.kt`): Scaffold with TopAppBar, LazyColumn + horizontal timeline, swipe date navigation, pull-to-refresh.

---

## EPG Browser (Search)

Standalone screen for full-text searching across the entire XMLTV dataset. Accessed from Content Type Selection via the book icon (only visible when `EpgIndexer.state` is `Indexed`).

### XmltvSearchService

**Class** (`core/network/.../xmltv/XmltvSearchService.kt`) implementing dual-path search.

**Search strategy (in order):**
1. **SQLite FTS MATCH** — when index is built, queries `epg_programme_fts`. Prefix matching. Typically <100ms.
2. **SQLite LIKE** — fallback if FTS returns empty. Uses `title_lowercase LIKE '%query%'`.
3. **XML scan** — fallback when index not yet built. Streaming parse, 1-2 seconds.

All queries time-windowed: past 1 day to future 6 days. Max 500 results.

### EpgBrowserViewModel

**ViewModel** (`core/ui/.../viewmodels/EpgBrowserViewModel.kt`) orchestrating search and paging.

**States:** `Idle` | `NoEpgFile` | `Searching` | `Indexing(progressPercent, programmesIndexed)` | `Results(query, dateGroups, totalPrograms, totalAirings, truncated, searchTimeMs, searchedFromIndex)` | `Error(message)`

Results are grouped by start date (Today, Tomorrow, weekday name, or full date for later days). Within each date group, programmes are grouped by normalized title+description and sorted by earliest airing time. Paging 3 integration for large datasets (2M+ programmes).

### Search UI

**TV** (`tv/.../feature/epgbrowser/EpgBrowserScreen.kt`): GlassPanel search, TvLazyColumn with date group headers, D-pad navigable, search source indicator, indexing progress banner.

**Mobile** (`mobile/.../feature/epgbrowser/MobileEpgBrowserScreen.kt`): Scaffold, LazyColumn with sticky date headers and expandable programme cards, linear progress during indexing.

---

## Settings & Configuration

EPG is configured via **Settings -> Manage EPG Data** (`Screen.EpgManagement`). Multiple XMLTV sources can be added, edited, and deleted.

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

**Status indicators (UI):** green = ingested <24h, yellow = >24h stale, red = error, gray = disabled.

**Actions:** Refresh All, Refresh Selected, Cleanup Files, Purge >2 days, Clear All Data (with confirmation dialog), Cancel (visible during processing).

**Selective refresh:** Checkboxes on each source row allow selecting multiple sources. A "Refresh Selected (N)" button appears when sources are selected, triggering refresh only for chosen sources.

**Source deletion cleanup:** Deleting a source also removes all associated channels and programmes from the index database.

**Import date filter:** During ingestion, programmes whose end time is before yesterday (current time - 24h) are skipped. This reduces database size and speeds up indexing.

**Per-source progress:** Both mobile and TV show per-source progress with percentage, phase label ("Downloading"/"Ingesting"), byte counts, and channel/programme counts. A cancel button is visible during processing. During `Clearing` state, a blocking overlay is shown.

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
| Parsed EPG results | SharedPreferences per-provider | 12h | XmltvEpgService grid cache |

No persistent XMLTV file. Mobile downloads to a temp file first, then ingests from file, then deletes the temp file.

**Network constraints:**
- EPG downloads: confirmation dialog on cellular, auto-refresh on WiFi/Ethernet
- Streaming downloads (128KB buffers, zero in-memory buffering)
- 3 retries with exponential backoff

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

### Room Entities (`core/network/.../xmltv/epgindex/`)

```kotlin
data class EpgChannelEntity(val xmltvId: String, val displayName: String, val iconUrl: String?)
data class EpgProgrammeEntity(val id: Long, val channelId: String, val title: String,
                              val titleLowercase: String, val description: String?,
                              val category: String?, val startEpoch: Long, val endEpoch: Long,
                              val sourceId: Long)
data class EpgProgrammeFts(val title: String)  // FTS4 content table
data class EpgIndexMetadata(val id: Int, val fileSizeBytes: Long, val fileLastModifiedMs: Long,
                            val indexedAtMs: Long, val channelCount: Int,
                            val programmeCount: Int, val timezoneOffsetHours: Int)
data class EpgSourceEntity(val id: Long, val url: String, val label: String,
                           val timezoneOffsetHours: Int, val addedAtMs: Long,
                           val lastIngestedAtMs: Long, val lastError: String?,
                           val enabled: Boolean, val lastChannels: Int,
                           val lastProgrammes: Int, val lastDownloadBytes: Long,
                           val ingestMethod: String)
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
| `XmltvSearchService.kt` | Class | Dual-path search (SQLite FTS -> LIKE -> XML scan) |
| `XmltvEpgService.kt` | Class | XMLTV -> EpgResponse adapter for grid |
| `XmltvModels.kt` | Data | XMLTV channel/programme/search models |
| `EpgBrowserModels.kt` | Data | Browser UI models (program + airings) |
| `EpgSyncWorker.kt` | CoroutineWorker | Mobile background EPG sync (WorkManager) |

### Queue (`core/network/.../queue/`)

| File | Type | Description |
|------|------|-------------|
| `RefreshQueue.kt` | Singleton | Priority-based sequential task executor with cancel support |
| `RefreshTask.kt` | Interface | Task contract (id, priority, execute) |
| `RefreshPriority.kt` | Enum | Task priority levels |

### SQLite Indexing (`core/network/.../xmltv/epgindex/`)

| File | Type | Description |
|------|------|-------------|
| `EpgIndexer.kt` | Singleton | Index builder (streaming + batch transactional) |
| `EpgIndexDatabase.kt` | Room DB | Database singleton (v8, WAL, with destroy/recreate) |
| `EpgSourceEntity.kt` | Entity | EPG source config (URL, label, tz, enabled, stats, ingestMethod) |
| `EpgSourceDao.kt` | DAO | CRUD for EPG sources, resetAllIngestionState() |
| `EpgIndexDao.kt` | DAO | FTS MATCH, LIKE, paged queries |
| `EpgProgrammeEntity.kt` | Entity | Programme table + FTS4 virtual table |
| `EpgChannelEntity.kt` | Entity | Channel table |
| `EpgIndexMetadata.kt` | Entity | File state tracking for staleness |
| `EpgIndexState.kt` | Sealed | Indexing state machine |
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
| `tv/.../feature/epg/EpgGuideScreen.kt` | TV | EPG Grid wrapper |
| `tv/.../feature/epg/EpgGridLayout.kt` | TV | Grid implementation (channels + time) |
| `tv/.../feature/epgbrowser/EpgBrowserScreen.kt` | TV | EPG Browser with GlassPanel |
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
