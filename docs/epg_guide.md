# EPG (Electronic Program Guide) Implementation Guide

Fijerena has two guide features: the **TV Guide** grid for browsing channel schedules, and **"Search the guide"** (the EPG Browser) for full-text search across the indexed guide. Both read the XMLTV guide sources of the active source (provider), downloaded and indexed into `epg_index.db`; the TV Guide falls back to the source's own EPG (Xtream `get_simple_data_table`) for channels the index doesn't know.

Tables and columns are in [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) (`epg_source` and `epg_pipeline_stats` in §1, `epg_index.db` in §2, `xtream_epg_cache` in §3); the index's PRAGMAs, page size and space reclaim in [EPG_INDEX_STORAGE.md](EPG_INDEX_STORAGE.md).

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [XMLTV Pipeline](#xmltv-pipeline)
3. [Background Work](#background-work)
4. [The EPG Index](#the-epg-index)
5. [TV Guide Grid](#tv-guide-grid)
6. [Search the Guide (EPG Browser)](#search-the-guide-epg-browser)
7. [Settings & Configuration](#settings--configuration)
8. [Caching Strategy](#caching-strategy)
9. [File Inventory](#file-inventory)

---

## Architecture Overview

```
     Guide sources (epg_source rows, per provider)
     hand-added, or the automatic Xtream one (AutoXmltvSources)
                              │
        EpgManagementViewModel / XtreamSessionManager ──► RefreshQueue
        EpgSyncWorker (periodic, foreground) ─────────────┐   │
                              ▼                           ▼   ▼
                 ┌──────────────────────────────────────────────┐
                 │ EpgFileManager (singleton)                   │
                 │  concurrent downloads → Channel → 2 ingesters │
                 │  change detection (304 / content hash)       │
                 └──────────────────────┬───────────────────────┘
                                        ▼
                 ┌──────────────────────────────────────────────┐
                 │ EpgIndexer → epg_index.db (FTS4)             │
                 │  staging tables + atomic swap, or direct     │
                 └───────────┬──────────────────────┬───────────┘
                             ▼                      ▼
              TV Guide (EpgViewModel)       Search the guide
              XmltvEpgService, pages of     XmltvSearchService
              30 rows for one day;          → EpgBrowserViewModel
              native EPG fallback           (FTS, max 500 results)
```

---

## XMLTV Pipeline

### EpgFileManager

**Singleton** (`core/network/.../xmltv/EpgFileManager.kt`) running the multi-source download-ingest pipeline. One pipeline serves every provider: a refresh covers one provider's sources, and screens read `stateFor(providerId)`, which reports `Idle` for another provider's run.

**Key design decisions:**
- OkHttp client derived from `NetworkModule.okHttpClient`: 60-second connect timeout, 3-minute read timeout
- **Task retries** (refreshes launched from the screens, through `RefreshQueue`): a failed task is retried up to 5 times, after 1, 2, 4, 8 and 16 minutes, with the state on `Retrying` in between
- **Download retries:** up to 5 attempts per file, waiting 5 s, 10 s, 20 s, 40 s between them (doubling, capped at 60 s)
- **WorkManager retries:** `EpgSyncWorker` (see [Background Work](#background-work))
- **Truncation detection:** `input.read()` returning -1 only means the connection closed, which is indistinguishable from a clean end of body. After the read loop, `totalRead` is compared against the response's `Content-Length` when the server sends one; a mismatch is treated as a download error and takes the retry path, rather than surfacing much later as an `XmlPullParserException` deep in ingestion.
- 128KB I/O buffers
- **One ingestion at a time:** `ingestMutex` serialises runs — the staging tables are a single process-wide pair, and a background sync and a screen's refresh can otherwise overlap
- Task ids carry the provider (`epg_refresh_stale_<providerId>` …), because `RefreshQueue` coalesces same-id submissions

**Staged or direct ingestion:** with at least 1.5× the current `epg_index.db` size free (`shouldUseStaging`; always on a fresh install), a run writes into the staging tables and the live guide stays fully queryable until `EpgIndexer.swapAndRebuildFts` promotes the rows. With less space it writes straight into `epg_programme` / `epg_channel` (the direct path): the query-only indexes are dropped for the run and FTS is stale until the rebuild at the end, so search falls back to a title scan meanwhile.

**Change Detection:**

A refresh that would re-download and re-parse an unchanged source is pure waste. Three mechanisms, in order:

1. **Conditional request.** `downloadSource` sends `If-None-Match` / `If-Modified-Since` from the source's stored `etag` / `last_modified_header`. A `304 Not Modified` short-circuits with no body read at all.
2. **Content hash.** For non-`.gz` sources a SHA-256 is computed in the same read pass and compared to `last_content_sha256`. `.gz` sources cannot be hashed at download time — gzip's mtime header taints the raw bytes even when the decompressed content is identical — so `ingestDownloadedSource` hashes the *decompressed* stream instead (`hashDecompressedGzip`, a read-and-discard pass, not a parse) before committing to a real ingest.
3. **Staleness guard (`canSkipIngest`).** A hash match only skips ingestion within `STALENESS_FORCE_INGEST_MS` (24h) of the last real ingest. `ingestFromStream` drops programmes that ended more than 12h ago against wall-clock time (there is no future limit), so a byte-identical static file re-ingested days later still clears out long-ended programmes — skipping it forever would let them pile up while the source kept reporting healthy refreshes.

All three need the live index to actually hold the source's rows (`EpgIndexer.hasProgrammesForSource`, checked once per download and carried on `DownloadedSource.indexHasRows`). With an empty index the validators are not sent (`buildDownloadRequest`) and the hash never skips, so the source is re-downloaded and re-ingested even when the upstream file has not changed. The bookkeeping is kept honest on both sides (G-11): on the staging path `ingestDownloadedSource` returns an `IngestRecord` and `swapThenMarkIngested` writes it (`markIngested`, validators included) only after the swap commits — the direct path marks right after ingest, its rows being live already — and every path that destroys the index (guide settings "Clear All Data", safe mode "Clear caches") calls `EpgSourceDao.resetAllIngestionState()` next to `EpgIndexer.clearAll()`. Those are bookkeeping columns only, so the `sync_epg_source_update` trigger does not queue a live-sync change.

A confirmed-unchanged source:
- skips `EpgIndexer.ingestFromStream` entirely,
- is **excluded** from `swapAndRebuildFts`'s id list at every call site — including it would delete its primary rows and transfer nothing back, since staging was never populated for it,
- carries its last known channel/programme counts forward via `EpgSourceDao.markUnchanged` rather than resetting them to zero,
- is flagged `unchanged = true` in its result, which the EPG management screens render as "Unchanged" in place of download/ingest durations (which would otherwise be stale numbers from whenever it last actually ran).

**Channel-based producer-consumer pipeline (`processAllSourcesInternal`):**

Downloads and ingestion are decoupled via a Kotlin `Channel<DownloadedSource>`.

- **Concurrency:** up to 3 concurrent downloads on phones, 2 on TVs and other fixed devices (a `Semaphore`); 2 ingestion workers
- **Download phase:** each source is downloaded to a cache file (`cacheDir/xmltv_source_<id>_tmp`, on every device). On success the `DownloadedSource` is sent to the ingestion channel; an unchanged one is recorded and skipped.
- **Ingestion phase:** the workers wait for `EpgIndexer.beginBulkIngestion(useStaging)` (started in parallel with the downloads), then ingest each file via `EpgIndexer.ingestFromStream()` in batches of 500 rows on phones, 5000 on TVs.
- **Finalizing:** once nothing is left, `Finalizing("Rebuilding indexes…")`; if any source ingested rows, `Finalizing("Swapping to primary guide…")` and `swapThenMarkIngested` on the staging path, or `rebuildFtsAndUpdateState()` on the direct path; then `incrementalVacuum()` and `endBulkIngestion()`. All inline in the caller's coroutine, so under `EpgSyncWorker` the wake lock covers it. The parsed-EPG cache of the providers whose sources ingested is cleared.
- **Completion:** `Completed` is emitted only after the swap / rebuild and index restore, and the run summary is written to `epg_pipeline_stats` (`updateLastPipelineStats`).

**Progress tracking (`ActiveSourceProgress`):**

Each active source has real-time progress tracked in a `ConcurrentHashMap<Long, ActiveSourceProgress>`:

| Field | Type | Description |
|-------|------|-------------|
| `sourceId` | Long | The source |
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
| `Pending` | — | Task submitted, waiting for the queue or the cellular confirmation |
| `Processing` | `completedCount`, `totalSources`, `activeSourceLabels`, `activeProgress`, `totalChannels`, `totalProgrammes`, `totalDownloadedBytes`, `completedSourceStats` | Actively processing sources with aggregate progress |
| `Retrying` | `attempt`, `maxAttempts`, `nextRetryAtMs`, `reason` | Task failed and is waiting for the next retry attempt |
| `Finalizing` | `phase`, `totalChannels`, `totalProgrammes`, `totalDownloadBytes`, `durationMs` | Post-ingestion: index rebuild, swap + FTS rebuild |
| `Completed` | `sourcesProcessed`, `errors`, `sourceStats`, `totalChannels`, `totalProgrammes`, `totalDownloadBytes`, `updatedAtMs`, `durationMs` | All sources processed, final stats |
| `Error` | `reason` | Processing failed after all retries |
| `Clearing` | — | "Clear All Data" in progress |

A cancelled run (`CancellationException`) restores the bulk-ingestion state and goes back to `Idle`, not `Error`.

**Lifecycle:**
- `initialize()` — called from `FijerenaApplication.onCreate()`: migrates the legacy global `epg_url` into an `epg_source` row (once), runs the one-time cleanup of automatic Xtream guide sources (below), restores the index state (`EpgIndexer.initialize()`), deletes stray `xmltv_*` cache files, enqueues `EpgFtsRebuildWorker` when a previous session left FTS stale, copies the retired device-wide refresh interval into guide sources without one of their own (once, `epg_refresh_interval_copied_v1`), and then keeps the periodic sync scheduled: it watches `epg_source` and reschedules whenever the shortest interval changes
- `launchRefreshStale(providerId)` — refresh the provider's enabled sources never ingested or stale by their own interval (`EpgRefreshSchedule.isStale`)
- `launchRefreshFailed(providerId)` — retry sources whose last attempt errored
- `launchRefreshSelected(providerId, selectedIds)` — refresh a user-selected subset
- `launchProcessSingleSource(providerId, sourceId)` — one source, same download / staging / swap steps
- `launchClearAllData()` — cancel processing, set `Clearing`, wait for `ingestMutex`, then `EpgIndexer.clearAll()` and `EpgSourceDao.resetAllIngestionState()`
- `cancelProcessing()` — cancel the current job and every queued or running `RefreshQueue` task, state to `Idle`
- `setRefreshInterval(sourceId, hours)` — one guide source's auto-refresh interval (`-1` = off); the schedule follows by itself
- `scheduleAutoRefresh(intervalHours)` (private) — one periodic `EpgSyncWorker` (unique work `epg_sync`, `ExistingPeriodicWorkPolicy.UPDATE`, needing a connected network) at the shortest interval among the enabled guide sources of every provider (`EpgRefreshSchedule.workIntervalHours`); no time of day. Cancelled when every source is off (or there are none)
- `refreshOutdatedSources(providerId)` — submits the provider's sources due by their own interval (`EpgRefreshSchedule.isDue`) to `RefreshQueue` as `epg_auto_refresh`; called by `XtreamSessionManager` when the automatic guide source was added or rewritten
- `processAllSources(sources)` — the pipeline run directly, without `RefreshQueue` or the task retries; used by `EpgSyncWorker`

Each `launch*` call takes an `onCellularConfirm` callback: on a cellular network the EPG management screen asks before downloading.

**Automatic Xtream guide sources** (`xtream/manager/AutoXmltvSources.kt`, GD0c):

An Xtream source gets at most one guide source by itself: `<server>/xmltv.php?username=…&password=…`, labelled `<host> (Bulk)`. No column marks it — `isAutoXmltvSource` recognises the provider's own server (scheme, host, port, path) plus `/xmltv.php` with credentials, and the ` (Bulk)` label; anything else is hand-added and never touched (a renamed one included). After every successful login (`login`, `restoreSession`, `updateProviderUrl`) `XtreamSessionManager.reconcileAutoXmltvSource` runs `AutoXmltvSources.reconcile`: the source is rewritten in place when the credentials (or, on a URL change, the server) changed, with its ingest state and validators reset so the next refresh is a real download; duplicates are deleted; it is added only when the account has live channels and deleted when it has none. Live channels = any `LIVE` row in this source's catalogue, else one `get_live_categories` request (empty = none; a failure or a 10 s timeout = unknown, which adds and removes nothing). An added or rewritten source is refreshed at once (`refreshOutdatedSources`). A one-time cleanup at start (`AutoXmltvSources.cleanUpOnce`, flag `auto_xmltv_sources_cleaned_v1` in `AppSettings`) collapses what older builds left: per Xtream source it keeps the row on the current login (rewritten if none is), deletes the rest, and deletes it when no live channels are known (catalogue has films but no live channels after a clean full sync); it adds nothing. Deleted sources lose their guide index rows. All writes go through `EpgSourceDao`, so live sync carries the rewrites and deletions to the group's other devices.

**Provides a guide** (`ProviderSettings.providesGuide`, plan `20261003_sources-guide-profiles-plan.md` D1/P3): null = not decided (on), else the detected or chosen value; `providesGuideSetByUser` marks the viewer's choice, which detection never changes. Off, `reconcile` keeps the automatic source **disabled** (`enabled = false`, never deleted for it, stats kept), adds none and skips the live-channels request; a disabled one also stops a second being added. Detection: after a refresh, a source ingested with 0 channels (no error, not unchanged) that is an Xtream source's automatic source turns the setting off unless the viewer set it (`EpgFileManager.detectEmptyOwnGuides` → `AutoXmltvSources.onEmptyIngest`), which disables the source. Edit Source's switch stores the viewer's choice (`ProviderRepository.setProvidesGuide`) and runs `AutoXmltvSources.reconcileStored`: off disables the source; on enables the same row again (or adds one when the catalogue has live channels) and refreshes it. The guide sources screen labels the disabled automatic source as the source's own guide, off. The setting travels with the provider row (sync, export); readers decode with `ignoreUnknownKeys`, so older versions ignore it.

Older builds also created `xtream://<providerId>` sources for an Xtream-API ingest that no longer exists; `EpgIndexer.purgeXtreamApiSources()` deletes any left, with their index rows, at every start.

### RefreshQueue

**Singleton** (`core/network/.../queue/RefreshQueue.kt`) providing priority-based task execution with up to 3 tasks running concurrently (`Semaphore(3)`) — not strictly sequential.

- Uses a `PriorityQueue<QueuedTask>` with a `Channel<Unit>(CONFLATED)` trigger
- Tasks are deduplicated by `id` against both the pending queue and already-executing tasks (`activeTasks`, guarded by the same mutex as the queue) — a duplicate submission coalesces into the running task's `Deferred` instead of racing a second execution
- `cancelAll()` cancels every active task's `Job` and clears all pending tasks
- Exposes `isProcessing`, `queuedTaskIds`, and `activeTaskIds` as `StateFlow`

### XmltvParser

**Object** (`core/network/.../xmltv/XmltvParser.kt`): streaming XMLTV parsing with `XmlPullParser`, used by `EpgIndexer`.

- `parseChannelForIndex(parser, sourceId)` -> `EpgChannelEntity`
- `parseProgrammeForIndex(parser, sourceId, timezoneOverrideHours)` -> `EpgProgrammeEntity`
- `parseTimestamp(str, timezoneOverrideHours)` — XMLTV timestamp parser
- `parse(inputStream, channelFilter, timeWindowSeconds)` — whole-file parse into `XmltvData`; no caller in the app any more

**Timezone override:** a non-zero offset replaces the timezone in XMLTV timestamps, to fix sources that encode local times but label them UTC. It is per source: `ingestDownloadedSource` passes `EpgSourceEntity.timezoneOffsetHours` down to `parseProgrammeForIndex`. The object's own `timezoneOverrideHours` default is never set (0).

---

## Background Work

| Worker / receiver | Work | What it does |
|---|---|---|
| `EpgSyncWorker` | periodic, unique `epg_sync` (all devices) | Runs as a foreground service (`setForeground`, data-sync type on API 34+) so DNS keeps working in Doze. Waits up to 15 s for an active network. Runs at the shortest guide-source interval (see `scheduleAutoRefresh`), so with no guide source on, it doesn't run at all. Works on the **active provider only**: first `ProviderRepository.sweepOrphanedCatalogData(onlyIfPending = false)` (never throws), then its sources due by their own interval (`EpgRefreshSchedule.isDue`: never ingested, or older than half their interval; an off source only if never ingested) — or all its enabled sources with input `force=true` — through `EpgFileManager.processAllSources()` directly inside `doWork()`, not `RefreshQueue`, so the wake lock covers download, ingest, swap and FTS rebuild. Retries (`BackoffPolicy.LINEAR`, 10 minutes; WorkManager's ~30 s default just keeps hammering a source that is rate-limiting) when every source failed or the run threw, up to 5 times; a `CancellationException` propagates as a cancellation. |
| `EpgFtsRebuildWorker` | one-shot, unique `epg_fts_rebuild` (`KEEP`) | Enqueued by `EpgFileManager.initialize()` when `fts_stale` was left set (the process died mid-rebuild). Foreground, like the sync worker; runs `rebuildFtsAndUpdateState()` then `incrementalVacuum()`; up to 3 retries. |
| `EpgSyncDebugReceiver` | one-shot, unique `epg_sync_debug` (`REPLACE`) | Debug builds only (`core/network/src/debug`), guarded by `android.permission.DUMP` so only the adb shell can send it. Enqueues `EpgSyncWorker` with `force=true` and the same backoff. Trigger: `adb shell am broadcast -a org.njarasoa.fijerena.DEBUG_EPG_SYNC -p org.njarasoa.fijerena` |

---

## The EPG Index

`epg_index.db` (schema in [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) §2, storage in [EPG_INDEX_STORAGE.md](EPG_INDEX_STORAGE.md)): channels, programmes, their staging copies, and an FTS4 table (`unicode61`) over programme titles and descriptions. Every query is scoped to the source ids of the active provider's enabled guide sources.

### EpgIndexer

**Singleton** (`core/network/.../xmltv/epgindex/EpgIndexer.kt`) building the index from XMLTV streams. All writes take its `writeMutex`.

**State machine (`EpgIndexState`):** `NotIndexed` -> `Indexing(progressPercent, channelsIndexed, programmesIndexed)` -> `Optimizing(channelCount, programmeCount)` -> `Indexed(channelCount, programmeCount, indexedAtMs)` | `Failed(reason)`. A refresh of an index that is already `Indexed` stays `Indexed` until its rebuild (`Optimizing`).

**FTS staleness:** `isFtsStale()` is true while the FTS table doesn't match `epg_programme`. It is persisted (`fts_stale` in `epg_indexer_state`) so that a rebuild killed mid-way is redone at the next start (`EpgFtsRebuildWorker`). Only the direct path and standalone rebuilds set it; the staging path never does.

**Key functions:**
- `initialize()` — restores `Indexed` from `epg_index_metadata` (repairing the row from actual counts when it's missing), restores the `fts_stale` flag and returns it
- `setIndexing()` — sets `Indexing` unless already `Indexed`; called once before parallel ingestion begins
- `beginBulkIngestion(useStaging)` / `endBulkIngestion(useStaging)` — drop Room's FTS sync triggers for the run (a single rebuild at the end is far cheaper than per-row FTS maintenance) and restore them after; on the direct path also drop and recreate the six query-only indexes on `epg_programme` (the unique dedup index stays, `REPLACE` needs it) and mark FTS stale
- `ingestFromStream(inputStream, sourceId, timezoneOverrideHours, batchSize, useStaging, isPlaybackActive, onProgress)` — returns `IngestionStats(channelsIngested, programmesIngested)`. Batches of `batchSize` rows, each its own `withTransaction`; channels inserted with `IGNORE`, programmes with `REPLACE` on the unique `(channel_id, source_id, start_epoch)`. Writes the staging tables when `useStaging`. Skips programmes that ended more than 12 hours ago; no future limit. Sleeps between batches (5 ms for channels, 100 ms for programmes) only while video is playing. An `OutOfMemoryError` is rethrown as an `IOException`, failing that source.
- `swapAndRebuildFts(sourceIds)` — staging path: in **one** transaction, `EpgIndexDao.executeSwap` (delete those sources' primary rows, copy their staging rows in, clear their staging), the FTS rebuild and the metadata row. WAL readers see the old guide + old FTS until the commit, then the new pair, so nothing is marked stale and search keeps working through the refresh. Failure rolls both back and restores the previous state; throws.
- `rebuildFtsAndUpdateState()` — direct path and standalone rebuilds (`EpgFtsRebuildWorker`, `purgeXtreamApiSources`): marks FTS stale at entry, checkpoints the WAL (`PASSIVE`), runs `INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')`, writes the metadata row, marks FTS clean and publishes `Indexed`; `Failed` on error
- `clearAll()` — deletes the database file with its WAL/SHM (`EpgIndexDatabase.destroy`) and reopens it empty: instant, where `DELETE FROM` took 10+ minutes on 4M+ rows. Guide sources live in `providers.db` and are not touched; the callers reset their ingest state
- `purgeOldProgrammes(cutoffEpoch)` — delete programmes ended before the cutoff, rebuild FTS, update metadata, incremental vacuum
- `incrementalVacuum()` — reclaims free pages; see [EPG_INDEX_STORAGE.md](EPG_INDEX_STORAGE.md) §3
- `hasProgrammesForSource(sourceId)` — whether the live (primary) tables hold any programme of the source; false on failure
- `purgeXtreamApiSources()` — see "Automatic Xtream guide sources" above

---

## TV Guide Grid

A one-day channel schedule for one list of live channels: a category, or Recent / Favourites.

### XmltvEpgService

**Class** (`core/network/.../xmltv/XmltvEpgService.kt`, one per provider) bridging the index into `EpgResponse`.

**Guide pages (GD4):** `MediaRepository.getGuideForItemsInWindow(items, dayStart, dayEnd)` answers one page of rows for the selected day: `XmltvEpgService.getEpgForChannelsInWindow` matches the page's channels and runs **one** index query, `EpgIndexDao.getProgrammesInWindow` (`channel_id IN (…) AND source_id IN (…) AND end_epoch > :windowStart AND start_epoch < :windowEnd`, only the columns a cell draws, no join; plan: `SEARCH epg_programme USING INDEX idx_programme_dedup (channel_id=? AND source_id=? AND start_epoch<?)` — no new index). A programme straddling either edge of the day is in; one ending exactly at midnight or starting exactly at the next is not. A channel indexed from several sources keeps one programme per start time. No parsed cache on this path (the ViewModel caches pages). The index answers whenever it knows these channels — rows in the window, or, when the window is empty, `getLatestEndForChannels` finds programmes elsewhere (the answer then carries `GuideData.latestEndSec`, "the data stops before this day"); only when it knows nothing about them does the page go to the source's native EPG (`provider.getEpgBulk`, cached in `xtream_epg_cache`), fetched whole and cut to the window (`windowListings`, the same predicate). `GuideData.source` says which answered (`XMLTV` or `NATIVE`), `updatedAtMs` the index build time or the newest `xtream_epg_cache` row. Debug builds log each page: `GuidePage: guide page: 30 channels from the index in N ms` (or `from the source EPG`).

**Outside the grid:** `MediaRepository.getGuideForItems` / `getEpgBulkForItems` (the player) read the index for now − 24 h to now + 24 h through the parsed-EPG cache (see [Caching Strategy](#caching-strategy)), then fall back to the native EPG; `getNowPlayingFromIndex` answers the Live TV list's "what's on now".

**Channel matching** (`matchChannel`, 6-tier fallback): exact `epgChannelId` -> case-insensitive `epgChannelId` -> exact display name -> normalized name -> normalized `epgChannelId` against normalized names -> contains match (min 4 chars). `matchGuideChannels` exposes the result (item id → xmltv id) for "Search the guide".

### EpgViewModel

**ViewModel** (`core/ui/.../viewmodels/EpgViewModel.kt`) driving both grids.

**State (GD1):**
- `Loading`
- `Ready(channelRows, timeSlots, currentTimeSlot, selectedDate, listedCount, totalCount, source, updatedAtMs, devStats, loadedCount, lastListingEndSec)` — `channelRows` holds **every** channel of the list (GD4, no cap); rows of a page not loaded yet have no programmes. `listedCount` counts loaded rows with at least one programme on the day (a channel that answered `[]` is not listed), `loadedCount` the rows on loaded pages, `totalCount` all channels. Both screens show "N of M channels have listings · <XMLTV guide|source EPG> · updated <relative>" under the title once every page is loaded, and "N of K loaded channels have listings · M in all · …" until then; `devStats` ("x/y channels answered · p/P pages · first Nms") as a dimmed line beneath it in dev mode only — never in the title. `source` is the layer behind the first page with listings. `lastListingEndSec` is the latest end among the loaded day's listings ("Listings end at …").
- `NoListings(reason, selectedDate, source?, updatedAtMs?)` — every row empty for the day; replaces the blank grid with a centred message. `reason`: `STALE` (listings exist but stop before the day), `INDEX_EMPTY` (index built, nothing for these channels), `NONE` (no index, no native data).
- `NoGuide` — decided **per source** (GD4, `MediaRepository.hasGuideForSource`): the source has no native EPG and no enabled guide source of its own; another source's indexed guide does not count. The message points at Edit Source → Guide sources.
- `Error(message)` — channels or guide failed to load.

**Flow (GD4, paged):**
1. Loads the channel set once (kept across day changes, reloaded by Refresh): `recent` / `favorites` resolve through `CategoryViewModel.virtualCategoryItems` (the repository's Recent list / favourites snapshot), any other id through `repository.getItems`; category-marker rows (`##### 4K #####`, `MediaItem.isCategoryMarker`) are dropped (`guideChannels`). Every channel is kept — ids, names, logos are cheap.
2. Listings load per **page of `PAGE_SIZE` = 30 rows**, for the selected day only (`repository.getGuideForItemsInWindow`, one index query per page), through `GuidePager`, which caches pages per (day, page) for the screen's life; Refresh (`forceRefresh`) clears it along with the native EPG and parsed-EPG caches. A day revisited, or rows scrolled back to, come from the cache.
3. On open (and on a day change) it loads the pages of the rows last on screen (first open: page 0). If none of them has a listing it reads on, up to `PROBE_PAGES` = 4 pages, before deciding: every channel loaded and none listed → `NoListings` (reason from `GuideData.latestEndSec`: data that stops before the day → `STALE`); otherwise `Ready`, empty rows and all, with the partial status line.
4. The grids report the rows on screen (`onRowsVisible(first, last)`): the VM loads the pages they fall on, plus the next page once they come within `PREFETCH_ROWS` = 5 rows of it (`pagesToLoad`). Each page that arrives re-emits `Ready`; a page that fails stays empty (no error screen) and is asked for again when its rows next come into view. A day change cancels the old day's page loads.
5. Generates 48 x 30-minute `TimeSlot` objects covering the full day.

**Row actions (GD6):** `isFavoriteChannel`, `toggleFavoriteChannel` (removing one in the Favourites guide reloads the rows without it) and, in the Recent guide of a source that keeps its own history, `removeFromRecent`.

There is no in-grid search (GD5, G-9): the grid's Search opens "Search the guide" with this guide's list as `Screen.EpgBrowser(categoryId, categoryName)`.

**Entry points (GD5):** the category header's TV Guide (that category); Search the guide's TV Guide button (`recent`, plan `20261003_sources-guide-profiles-plan.md` D7; Home has no TV Guide button since P7); on TV, the full-screen OSD's Guide button (the list being zapped through, `focusChannelId` = the playing channel — entry focus lands on its row; the mobile grid scrolls to it).

### Grid Layout

**Layout engine** (`core/ui/.../guide/GuideLayout.kt`, GD2, unit-tested in `GuideLayoutTest`): pure Kotlin, shared by both platforms. One horizontal scale per platform (TV 1 h = 240 dp, `DP_PER_MINUTE`; phone 1 h = 160 dp, `PHONE_DP_PER_MINUTE`; the caller converts with its density and UI scale) maps epoch seconds onto a canvas whose x = 0 is the selected day's local midnight: `xFor`, `timeAt`, `widthFor` (true duration — a short programme is never stretched to fit its label; `GuideCell.labelFits` is false below the minimum label width and the label is dropped, the slot keeps its width), `visibleRange` / `composeRange` (the viewport ± one viewport, quantised to half-viewport steps, empty until the viewport is measured), `cellsIn` (a row's programmes overlapping a range, clipped to the day, so a programme straddling midnight starts at x = 0), `tickMarks` (every 30 min on the clock's half-hours), and `programAt` (the programme on at a time, else the nearest — the Up/Down rule).

**TV** (`tv/.../feature/epg/TvEpgGuideScreen.kt` + `TvGuideGrid.kt`, GD2): `TvEpgGuideScreen` renders `Loading`, `Ready` and `NoListings` inside `TvGuideGrid`'s chrome and only `NoGuide` / `Error` as `TvErrorState`. The header holds the title, a "date · N of M channels have listings · source · updated …" line (plus the dev-stats line in dev mode), a line with the focused programme's title · time (a short cell drops its label, GD4 — this is where it can always be read) and, on the right of it, "Listings end at <time>" when now is on the day shown and past its last listing (instead of rows that go silently empty), and a row of labelled buttons — Previous Day, Now, Next Day, Search, Refresh — that keep their icon and text visible when focused. Below it a fixed channel column (`epgChannelColumnWidth`) and a time canvas: the ruler and every row are placed by `GuideLayout` and scrolled by **one** `ScrollState` (each row is a `Layout` of cells at `xFor(start)`, never a `LazyRow`, never a shared `LazyListState`), in a vertical `LazyColumn` under the pinned ruler. Rows compose only the cells in `composeRange`. A vertical now line is drawn across the ruler and the rows, past cells are dimmed, the on-air cell has an accent bar and accent title. The canvas ignores bring-into-view (`LocalBringIntoViewSpec` set to a no-op), so only the grid's own moves scroll it. `NoListings` shows "No listings", the reason and Refresh in the same chrome, so day navigation stays available.

Focus (decided by `GuideFocus` in `onPreviewKeyEvent`, not by geometric search): first open lands on the on-air cell of the first channel with a programme on now (else the first channel with listings), with now about a third in from the canvas's left edge. Left/Right move by programme and scroll the canvas so the new cell's start is on screen; Left from a row's first programme goes to its channel cell, Left on a channel cell stays, Right from a channel cell goes to the on-air cell when now is on screen, else the cell at the canvas's left edge. Up/Down keep the **time**: the target row's cell containing the focused cell's start (its visible start when it began off-screen), else the nearest; the time sticks across rows until a horizontal move, and a row without listings lands on its channel cell without losing it. Channel Up/Down page by the visible row count. **Paging (GD4):** a row whose page has not loaded is its channel cell over an empty track, so Up/Down and Channel Up/Down walk it like a row without listings — focus lands on the channel cell and the kept time survives; the move scrolls the row on screen, which asks for its page, and when the page arrives (focus still on that channel cell, inside the grid) focus moves on to the cell at the kept time. Up from the first row is the only way into the header (lands on the last button used, else Previous Day); Down from the header returns to the last cell. "Now" on today scrolls to now and focuses the on-air cell of the current row; on another day it reloads today and then does the same. Day changes keep focus on the button pressed. Opened with a `focusChannelId`, first open lands in that channel's row instead (on its channel cell while its page loads, then on the on-air cell). Search opens "Search the guide" on this guide's channels; Back from it lands on the Search button (`NavReturnFocus`). Back on the grid leaves the guide — intercepted in `onPreviewKeyEvent` on the root. **OK (GD6):** on a programme it opens the programme's details panel, whose "Watch channel" opens the Live TV preview on that channel; on a channel cell it opens the preview directly. Long-press OK or the Menu key on either kind of cell opens the channel's row actions (favourite, and Remove from Recent in the Recent guide). Both are dialogs: Back closes them and focus returns to the cell (`NavReturnFocus`). `scripts/focus-walks/guide.txt` walks it.

**Mobile** (`mobile/.../feature/epg/MobileEpgGuideScreen.kt` + `MobileGuideGrid.kt`, GD3): the top bar's title is "TV Guide · <category>" on one line (ellipsized) with the "N of M channels have listings · source · updated …" line beneath it (dev stats, in dev mode, sit dimmed under the bar). Below it date tabs — Today, Tomorrow, then the next five weekdays (`DAY_TAB_COUNT` = 7; a tab calls `EpgViewModel.loadEpgData(date)`) — and a "Now" chip; the tabs stay while a day loads and in `NoListings` (message + Refresh). The grid is the TV's construction at phone scale: a fixed channel column (`posterWidth`; logo with the name beneath, or the name alone) and a time canvas where the pinned ruler and every row are `horizontalScroll` windows onto **one** `ScrollState`, each row a `Layout` of cells at `xFor(start)` with their true width (label dropped below `minLabelWidthPx`), composing only `composeRange`, in a vertical `LazyColumn`; a horizontal drag on any row or the ruler moves them all. Each row has a dimmed track, so a channel without listings is its channel cell and an empty track. Now line, dimmed past cells, on-air cell with an accent bar and accent title. First open puts now a third in from the left edge; "Now" scrolls there (animated) on today, or loads today and then does. The scroll and list states live in the screen and are saveable, so a day change keeps the time of day and a return from the dock keeps the position. Tap a programme → `ModalBottomSheet` with title, channel, day and time, description, Close and "Watch channel" (`onProgramSelected` → the Live TV dock); tap a channel → tune (`onChannelSelected`). Pull-to-refresh (`PullToRefreshBox`) runs `forceRefresh`. **Paging (GD4):** the grid reports the `LazyListState`'s visible rows to `EpgViewModel.onRowsVisible`; rows of pages not loaded yet are their channel cell and an empty track until the page lands. "Listings end at <time>" sits above the grid when now is on the day shown and past its last listing. The top bar's Search opens "Search the guide" on this guide's channels (GD5); a `focusChannelId` scrolls the grid to that row on first open.

---

## Search the Guide (EPG Browser)

Full-text search across the active provider's indexed guide. Opened from Home's "Search the guide" button (book icon, shown only while `EpgIndexer.state` is `Indexed`) as `Screen.EpgBrowser()`, or from a TV Guide's Search as `Screen.EpgBrowser(categoryId, categoryName)`.

### XmltvSearchService

**Class** (`core/network/.../xmltv/XmltvSearchService.kt`). Searches only the active provider's enabled guide sources; returns `null` when the index is `NotIndexed`. Each programme-search step has a 10 s timeout.

**Programme search (`search`), in order; no XML-scan fallback exists:**
1. **Raw FTS query** — preserves user-provided FTS operators (AND/OR/NOT/NEAR, as whole words) and quotes; otherwise appends a prefix wildcard `*` to the last token. Typically <100ms. (`EpgSearchPath.FTS_PHRASE`)
2. **Safe FTS retry** — if the raw query returns nothing, fails (e.g. malformed syntax) or times out, strips `" * ( ) :` and retries as one quoted phrase with a prefix wildcard (`"…"*`; reported as `FTS_AND`).

If the FTS index is stale (`isFtsStale()` — direct-path refresh, or an interrupted rebuild being redone), both FTS steps are skipped for a title-only `LIKE '%…%'` scan of `epg_programme.title_lowercase` (`EpgIndexDao.searchByTitleLike`, `EpgSearchPath.LIKE_FALLBACK`; `\ % _` escaped) — seconds on 2M+ rows, same timeout. Only if that times out or fails does it throw `EpgIndexBusyException`; `EpgBrowserViewModel` shows why (`UiState.IndexBusy`) and reruns the query once the index is `Indexed`. The staging path never marks it stale.

Programme search covers every programme that hasn't ended yet, with no upper limit (ingest has none either). Max 500 results. There is no channel-name search (removed in P7).

### EpgBrowserViewModel

**ViewModel** (`core/ui/.../viewmodels/EpgBrowserViewModel.kt`) orchestrating search and paging. One mode, programme titles; queries shorter than 2 characters are ignored; each search is added to the profile's EPG search history.

**States:** `Idle` | `NoEpgFile` | `Searching` | `IndexBusy(query, refreshing)` | `Results(query, dateGroups, totalPrograms, totalAirings, truncated, searchTimeMs, searchedFromIndex, searchPath)` | `Error(message)`

Results are grouped by start date (Today, Tomorrow, weekday name, or full date for later days); within each date group, programmes are grouped by normalized title+description and sorted by earliest airing time. Channel mode groups by channel instead. Each airing is matched to a stream of the active source (`EpgChannelMatcher`, `matchedStream`); airings on excluded categories are dropped. Paging 3 serves the large result sets (`searchByTitleFtsPaged`, `getPagedNowPlaying`, 50 per page).

**Filters (screen side):** "Matched only" (`filterMatchedOnly`) keeps airings matched to a stream. Opened from a TV Guide (GD5), the ViewModel resolves that guide's channels the way the guide does (`CategoryViewModel.virtualCategoryItems`, else `getItems`) into `contextChannels: GuideChannels` — their stream ids, and the same streams by the guide channel (`MediaRepository.matchGuideChannels`, xmltv id lowercased). Both screens then show an "In <category> only" toggle, on by default, applied after "Matched only" (`filterToStreams`): an airing is kept when its matched stream is one of the guide's, or when it is on one of the guide's guide channels — then it is pointed at the guide's own stream, so it shows and plays the channel the guide shows.

### Search UI

**TV** (`tv/.../feature/epgbrowser/TvEpgBrowserScreen.kt`): GlassPanel search, LazyColumn with date group headers, D-pad navigable, search source indicator, "In <category> only" and "Matched only" toggles. The header has **TV Guide** (`EpgGuide` for Recent; Back from the grid lands on it), Refresh, then **Guide sources** (when the source in use has live channels, `EpgBrowserViewModel.guideSourcesProviderId`) opening `EpgManagement` for it; Back lands on that button (its own `NavReturnFocus`, which also holds off the search field's first-open focus).

**Mobile** (`mobile/.../feature/epgbrowser/MobileEpgBrowserScreen.kt`): Scaffold, LazyColumn with sticky date headers and expandable programme cards, the same toggles; the top bar's actions are TV Guide, Refresh and Guide sources, as on TV.

---

## Settings & Configuration

**Guide sources** (`Screen.EpgManagement(providerId)`, titled "Guide sources · <source>"; `TvEpgManagementScreen` / `MobileEpgManagementScreen`, `EpgManagementViewModel`): opened from the source's Edit Source (Guide sources ›, with the count and last refresh as its value), the Sources list's Guide button, or Search the guide's Guide sources button (the source in use); Back returns focus to the control that opened it. Guide sources belong to one provider (`epg_source.provider_id`); several XMLTV sources can be added, edited, enabled/disabled and deleted. Fields: see `epg_source` in [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) §1.

**Actions:** Refresh Stale (N), Retry Failed (N), Refresh (N) for the sources selected with the row checkboxes, a per-source Refresh, Edit, Auto-refresh and Delete, and Delete selected.

**Auto-refresh per row:** each row shows its interval ("Refreshes daily", "Refreshes every 6 hours", "Auto-refresh off"; `EpgManagementViewModel.refreshIntervalHours(source)`, i.e. `EpgRefreshSchedule.intervalHours` with the retired device-wide interval for a not-set row). Its **Auto-refresh** button opens a picker — TV: `SettingsPickerPane` in place of the list, opening on the current value, Left/Back closing it onto the button; phone: `SettingsPickerDialog` — with Off, every 6 h, 12 h, daily and weekly (`REFRESH_INTERVAL_CHOICES`). A value that isn't one of them (4, 8 or 48 h copied from the old setting) is added as an extra checked option (`refreshIntervalOptions`), so it stays until another is picked. Picking calls `EpgFileManager.setRefreshInterval(sourceId, hours)`.

**Status indicators:** green = ingested within the source's stale threshold (`EpgManagementViewModel.staleThresholdMs(source)`), yellow = older than that, red = last attempt errored, gray = disabled or never ingested. A source refreshed as "unchanged" shows "Unchanged" in place of its durations.

**Per-source progress:** percentage, phase ("Downloading" / "Awaiting Ingestion" / "Ingesting", then the finalizing phases), byte counts, and channel/programme counts.

**Guide settings** (`GuideSettingsRows`): no device-wide auto-refresh any more (P5b of `docs/plans/20261003_sources-guide-profiles-plan.md`: each guide source has its own, above). The retired keys `epg_auto_refresh` / `epg_refresh_interval` are only read, for a guide source without an interval of its own (one synced from an older app version) and by the one-time copy; `epg_refresh_time` is unused. Under Settings → Backup & storage, **Guide data maintenance** — the pipeline's current status and last run (`epg_pipeline_stats`), Cleanup (delete stray `xmltv_*` cache files), Purge (programmes ended more than two days ago) and Clear All Data (with confirmation; status shows "Clearing" while it runs).

**Refresh scheduling (per guide source):** each `epg_source` row has its own `refresh_interval_hours` (`-1` = off; null = not set, which uses the retired device-wide interval; new sources start at 24). `EpgRefreshSchedule` (`xmltv/EpgRefreshSchedule.kt`) holds the rules. A source counts as stale after **half its own interval** (`staleAfterMs`), so the periodic `EpgSyncWorker` firing slightly off-schedule doesn't skip a whole cycle; an off source counts as stale after 24h for Refresh stale and the status colour, but an automatic run (the worker, `refreshOutdatedSources`) refreshes it only while it has never been ingested. The one periodic `epg_sync` runs at the shortest interval among enabled sources of every provider and still refreshes only the active provider's due sources; adding, removing, switching or changing the interval of a source reschedules it. The interval syncs (`SyncPayloads.EpgSource.refreshIntervalHours`, absent = keep the local value) and is exported (`ExportedEpgSource.refreshIntervalHours`; an older file's sources take off when its `epgAutoRefreshEnabled` was off, else stay not set). The retired device-wide keys are neither synced nor exported any more (P5b).

**Source deletion cleanup:** deleting a source also removes its channels and programmes from the index database.

**Import date filter:** programmes that ended more than 12 hours ago are skipped at ingest; nothing ahead is dropped.

**Timezone override:** the per-source offset (-12 to +14) is applied at parse time. Changing it requires re-ingesting the source, because the stored epoch values depend on it.

**EpgSourceDao notable queries:**
- `resetAllIngestionState()` — zeroes every source's ingest stats and error and clears its validators and content hash
- `markIngested()` — records a successful ingest with stats and validators
- `markUnchanged()` — records a confirmed-unchanged refresh (timestamp, error cleared)
- `markError()` — records an error for a source
- `getStaleSources(providerId, thresholdMs)`, `getFailedSources(providerId)`, `getEnabledSourcesForProvider(providerId)`

---

## Caching Strategy

| Cache | Location | TTL | Purpose |
|-------|----------|-----|---------|
| XMLTV temp file | `cacheDir/xmltv_source_<id>_tmp` | Deleted after ingest; leftovers removed at start and by Cleanup | Download staging |
| SQLite index | `databases/epg_index.db` | Until next refresh | Guide data + FTS4 search index |
| Parsed EPG results | `xmltv_cache_<providerId>` SharedPreferences | 12h, and only for the index build it was parsed from (`indexedAtMs`); cleared after a sync that ingested the provider's sources and by the guide's Refresh | `XmltvEpgService.getEpgForChannels` (player); not the grid since GD4 |
| Native EPG | `xtream_epg_cache` table in `xtream_v2.db` | 6h | The source's own EPG, the fallback when the index has nothing for a channel |
| Guide pages | In-memory (`GuidePager` in `EpgViewModel`), per (day, page of 30 rows) | The guide screen's life; Refresh clears it | TV Guide grid listings |
| Channel matcher | In-memory (`EpgChannelMatcher`), per provider | Until cleared on a provider change | Guide channel → stream matching for "Search the guide"; warmed after each catalogue sync (`ProviderSyncRunner`) |

No XMLTV file is kept: every device downloads to the temp file, ingests from it, then deletes it.

A disk-backed tier for `EpgChannelMatcher` (a `providers.db` table so a cold start skips the ~300-500 ms rebuild on a Shield) was considered and deferred: warming the in-memory cache right after a sync covers most real use, without the schema and serialization cost.

**Network constraints:**
- Refreshes started from the screens ask for confirmation on a cellular network; the periodic worker needs any connected network
- Streaming downloads (128KB buffers, zero in-memory buffering)
- Retries: see [EpgFileManager](#epgfilemanager)

**Memory safety:**
- OkHttp with streaming response body
- Streaming `XmlPullParser` (no in-memory DOM tree)
- Batched INSERTs (500 rows on phones, 5000 on TVs), each in its own `withTransaction`
- `OutOfMemoryError` during ingest is caught in `EpgIndexer.ingestFromStream` (with `System.gc()`) and fails that source as an `IOException`; in "Search the guide" it shows an error ("EPG file too large for search")
- Sleeps between batches (`delay(5)` for channels, `delay(100)` for programmes) while video is playing, to avoid starving playback

---

## File Inventory

### Core Services (`core/network/.../xmltv/`)

| File | Type | Description |
|------|------|-------------|
| `EpgFileManager.kt` | Singleton | Channel-based download-ingest pipeline manager |
| `XmltvParser.kt` | Object | Streaming XMLTV parser with per-source timezone override |
| `XmltvSearchService.kt` | Class | Two-tier FTS search (raw query, then sanitized phrase retry); title-only LIKE scan while FTS is stale; channel search |
| `XmltvEpgService.kt` | Class | Index -> `EpgResponse` adapter: guide pages, player EPG, now playing, channel matching |
| `EpgChannelMatcher.kt` | Class | Guide channel → stream matching for "Search the guide" |
| `XmltvModels.kt` | Data | `XmltvChannel`, `XmltvProgramme`, `XmltvData`, `XmltvSearchResult`, `EpgSearchPath` |
| `EpgBrowserModels.kt` | Data | Browser models (`EpgBrowserProgram`, `EpgBrowserAiring`, `EpgBrowserMatchedStream`, `EpgBrowserDateGroup`, `GuideChannels`) and filters (`filterMatchedOnly`, `filterToStreams`) |
| `EpgSyncWorker.kt` | CoroutineWorker | Periodic background sync — see [Background Work](#background-work) |
| `EpgRefreshSchedule.kt` | Object | Per-source auto-refresh rules: stale / due, the periodic work's interval, the one-time copy of the retired device-wide interval |
| `EpgFtsRebuildWorker.kt` | CoroutineWorker | FTS rebuild after an interrupted one |
| `src/debug/.../EpgSyncDebugReceiver.kt` | BroadcastReceiver | Debug-only adb trigger for `EpgSyncWorker` |

`EpgSourceEntity.kt`, `EpgSourceDao.kt`, `EpgPipelineStatsEntity.kt` and `EpgPipelineStatsDao.kt` live in `core/network/.../provider/`, since their tables are in `providers.db`. `AutoXmltvSources.kt` and `XtreamSessionManager.kt` (automatic guide source) and `XtreamEpgManager.kt` (native EPG, `xtream_epg_cache`) are in `core/network/.../xtream/manager/`.

### Queue (`core/network/.../queue/`)

| File | Type | Description |
|------|------|-------------|
| `RefreshQueue.kt` | Singleton | Priority-based task executor, up to 3 tasks at once, de-duplicated by id, with cancel support |
| `RefreshTask.kt` | Interface + object | Task contract (id, priority, execute) and the `RefreshPriority` constants |

### SQLite Indexing (`core/network/.../xmltv/epgindex/`)

| File | Type | Description |
|------|------|-------------|
| `EpgIndexer.kt` | Singleton | Index builder (streaming, batched, staging swap, FTS rebuild, vacuum); `execPragma` |
| `EpgIndexDatabase.kt` | Room DB | Database singleton (v17, WAL, page-size seeding, incremental auto-vacuum, destroy/recreate) |
| `EpgIndexDao.kt` | DAO | Staging swap, FTS MATCH, LIKE, paged and window queries; `EpgWindowRow` |
| `EpgProgrammeEntity.kt` | Entity | Programme table + FTS4 virtual table |
| `EpgChannelEntity.kt` | Entity | Channel table |
| `EpgProgrammeStagingEntity.kt`, `EpgChannelStagingEntity.kt` | Entity | Staging tables |
| `EpgIndexMetadata.kt` | Entity | Last build's counts and time |
| `EpgIndexState.kt` | Sealed | Indexing state machine |
| `EpgSearchResultRow.kt` | Data | JOIN query result model |

### Models elsewhere

`core/player/.../model/EpgModels.kt`: `EpgProgram`, `EpgResponse`, `EpgChannelRow`, `TimeSlot`. `MediaRepository.kt`: `GuideData`, `GuideSource`.

### ViewModels (`core/ui/.../viewmodels/`)

| File | Type | Description |
|------|------|-------------|
| `EpgViewModel.kt` | ViewModel | TV Guide: paging (`GuidePager`), day navigation, row actions |
| `EpgViewModelFactory.kt` | Factory | Creates `EpgViewModel` for a category |
| `EpgBrowserViewModel.kt` | ViewModel | "Search the guide": programme search, matching, context filter, Paging 3 |
| `EpgBrowserViewModelFactory.kt` | Factory | Creates `EpgBrowserViewModel` |
| `EpgManagementViewModel.kt` | ViewModel | Guide sources screen (each source's auto-refresh choices) and guide maintenance |

### UI Screens

| File | Platform | Description |
|------|----------|-------------|
| `core/ui/.../guide/GuideLayout.kt` | Shared | Time → x layout engine for the guide grids |
| `tv/.../feature/epg/TvEpgGuideScreen.kt` | TV | TV Guide screen |
| `tv/.../feature/epg/TvGuideGrid.kt` | TV | Grid (header, channels + time canvas, focus, details panel, row actions) |
| `tv/.../feature/epgbrowser/TvEpgBrowserScreen.kt` | TV | "Search the guide" |
| `tv/.../feature/epg/TvEpgManagementScreen.kt` | TV | Guide sources |
| `tv/.../feature/settings/components/GuideSettingsRows.kt` | TV | Guide data maintenance row and sub-pane |
| `mobile/.../feature/epg/MobileEpgGuideScreen.kt` | Mobile | TV Guide screen (date tabs, details sheet) |
| `mobile/.../feature/epg/MobileGuideGrid.kt` | Mobile | Grid (channel column + time canvas on `GuideLayout`) |
| `mobile/.../feature/epgbrowser/MobileEpgBrowserScreen.kt` | Mobile | "Search the guide" |
| `mobile/.../feature/epg/MobileEpgManagementScreen.kt` | Mobile | Guide sources |
| `mobile/.../feature/settings/components/GuideSettingsRows.kt` | Mobile | Guide data maintenance row and dialog |

### Integration Points

| File | How EPG is used |
|------|-----------------|
| `MediaRepository.kt` | `getGuideForItemsInWindow()` (guide pages), `getGuideForItems()` / `getEpgBulkForItems()` (index, then native EPG), `getNowPlayingFromIndex()`, `hasGuideForSource()`, `matchGuideChannels()` |
| `CategoryViewModel.kt` | Live TV "what's on now": `getNowPlayingFromIndex()` for the first 50 channels, then the native EPG for the unmatched ones |
| `StreamLoaderViewModel.kt` | Player EPG via `getEpgBulkForItems()` |
| `AppSettings.kt` | `epgAutoRefreshEnabled`, `epgRefreshInterval` (retired, read only); `epgUrl` / `epgTimezoneOffsetHours` only for the one-time migration |
| `FijerenaApplication.kt` | `EpgFileManager.initialize()`, `EpgIndexer.purgeXtreamApiSources()` at start |
| `Screen.kt` (navigation) | `Screen.EpgGuide(categoryId, categoryName, focusChannelId?)`, `Screen.EpgBrowser(categoryId?, categoryName?)`, `Screen.EpgManagement(providerId)` |
