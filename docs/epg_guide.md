# EPG (Electronic Program Guide) Implementation Guide

Fijerena has two complete EPG systems working in tandem: a **Live TV Grid** for browsing channel schedules, and an **EPG Browser** for full-text searching across the entire XMLTV dataset. Both are powered by a shared XMLTV pipeline that supports manual URL entry and automatic guide detection via the iptv-org open database.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [EPG Data Sources](#epg-data-sources)
3. [XMLTV Pipeline](#xmltv-pipeline)
4. [SQLite FTS Indexing](#sqlite-fts-indexing)
5. [EPG Grid (TV Guide)](#epg-grid-tv-guide)
6. [EPG Browser (Search)](#epg-browser-search)
7. [iptv-org Auto-Detection Pipeline](#iptv-org-auto-detection-pipeline)
8. [Settings & Configuration](#settings--configuration)
9. [Caching Strategy](#caching-strategy)
10. [Data Models Reference](#data-models-reference)
11. [File Inventory](#file-inventory)

---

## Architecture Overview

```
                     ┌────────────────────────────┐
                     │     User Settings           │
                     │  epgMode: "auto" | "manual" │
                     └──────────┬─────────────────┘
                                │
              ┌─────────────────┴─────────────────┐
              │                                   │
     ┌────────▼────────┐              ┌───────────▼───────────┐
     │  Manual Mode    │              │   Auto-Detect Mode    │
     │  User-provided  │              │ IptvOrgGuideResolver  │
     │  XMLTV URL      │              │ → match → select →    │
     │                 │              │   download → merge    │
     └────────┬────────┘              └───────────┬───────────┘
              │                                   │
              └─────────────────┬─────────────────┘
                                │
                   ┌────────────▼────────────┐
                   │    EpgFileManager        │
                   │   (singleton, WiFi-only) │
                   │  → xmltv_global.xml      │
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

## EPG Data Sources

EPG data enters the system through one of two modes, configured via `AppSettings.epgMode`.

### Manual Mode

The user pastes an XMLTV URL in Settings. `EpgFileManager` downloads the file directly using `HttpURLConnection` with streaming I/O (64KB buffers). Supports both `.xml` and `.gz` files (gzip is decompressed in a second file-to-file pass). The file is saved to `cache/xmltv_global.xml` and refreshed every 24 hours.

### Auto-Detect Mode (iptv-org)

The app automatically determines which guide files to download based on the user's Live TV channels. This is a multi-step pipeline orchestrated by `IptvOrgGuideResolver`:

1. `CategoryViewModel` loads Live TV items and extracts `ChannelRef` (epgChannelId + name)
2. `MediaRepository.triggerAutoDetectEpg()` passes them to `IptvOrgGuideResolver.resolve()`
3. The resolver fetches `channels.json` and `guides.json` from the iptv-org API (7-day cache)
4. `IptvOrgChannelMatcher` matches user channels against the iptv-org database (2-phase: ID then name)
5. `IptvOrgGuideSelector` picks the minimum set of per-site guide files using greedy set-cover (max 15)
6. `EpgFileManager.downloadAndMergeGuides()` downloads each guide file
7. `XmltvMerger` merges them into a single `xmltv_global.xml` (deduplicating channels)
8. `EpgIndexer` builds the SQLite FTS index

Auto-detect refreshes every 12 hours (vs 24h for generic XMLTV).

---

## XMLTV Pipeline

### EpgFileManager

**Singleton** (`core/network/.../xmltv/EpgFileManager.kt`) managing the entire download lifecycle.

**Key design decisions:**
- Uses `java.net.HttpURLConnection` instead of Ktor to avoid in-memory buffering of 500MB+ files
- `Accept-Encoding: identity` prevents automatic gzip decompression
- WiFi-only enforcement via `NetworkMonitor.currentNetworkType`
- 3 retries with exponential backoff (5s base)
- 64KB I/O buffers, 10-minute read timeout
- `OutOfMemoryError` caught explicitly

**State machine:**

| State | Description |
|-------|-------------|
| `NoUrl` | Manual mode, no URL configured |
| `AutoDetecting` | Auto mode, waiting for resolver |
| `Downloading` | Actively downloading |
| `Ready(file, sizeBytes, lastModifiedMs)` | File available on disk |
| `Failed(reason)` | Download failed, no cached file |
| `Error(reason, file?)` | Error occurred, stale file may exist |

**Lifecycle:**
- `initialize()` — called from `MainActivity.onCreate()`, routes to auto or manual mode
- `triggerDownload()` — called when user changes EPG URL in settings
- `downloadAndMergeGuides(guides)` — called by resolver for auto-detect mode
- `reindexIfNeeded()` — called from settings when timezone override changes
- `getEpgFile()` — returns the cached file or null

### XmltvParser

**Object** (`core/network/.../xmltv/XmltvParser.kt`) providing streaming XMLTV parsing with `XmlPullParser`.

**Key functions:**
- `parse(inputStream, channelFilter, timeWindow)` — full parse with channel and time filters applied during parsing to minimize memory
- `searchByTitle(inputStream, query, timeWindowSeconds)` — streaming title search (fallback when no SQLite index)
- `parseChannelForIndex(parser)` → `EpgChannelEntity` — used by EpgIndexer
- `parseProgrammeForIndex(parser)` → `EpgProgrammeEntity` — used by EpgIndexer
- `parseTimestamp(str)` — XMLTV timestamp parser with timezone override support

**Timezone override:** `@Volatile var timezoneOverrideHours: Int` — applied in `parseTimestamp()` to fix XMLTV sources that encode local times but mislabel them as UTC. Set from `AppSettings.epgTimezoneOffsetHours`.

### XmltvMerger

**Object** (`core/network/.../xmltv/XmltvMerger.kt`) merging multiple XMLTV files into one.

Used exclusively by the auto-detect pipeline when multiple per-site guide files need to be combined. Uses streaming `XmlPullParser` for reading and `BufferedWriter` for output. Deduplicates `<channel>` elements by ID across files. Single-file inputs are just copied (no parse overhead).

---

## SQLite FTS Indexing

### Database Schema

**Room database** `epg_index.db` (version 3, destructive migration):

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
└── end_epoch        LONG

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
```

The FTS4 virtual table with `unicode61` tokenizer enables sub-100ms full-text search across millions of programmes. The composite `(start_epoch, end_epoch)` index accelerates time-range queries.

### EpgIndexer

**Singleton** (`core/network/.../xmltv/epgindex/EpgIndexer.kt`) building the SQLite index from XMLTV files.

**State machine:**

| State | Description |
|-------|-------------|
| `NotIndexed` | No index exists |
| `Indexing(progressPercent, channelsIndexed, programmesIndexed)` | Actively indexing |
| `Indexed(channelCount, programmeCount, indexedAtMs)` | Ready for queries |
| `Failed(reason)` | Indexing failed |

**Key functions:**
- `needsReindex(file)` — compares file size, last modified, and timezone offset against stored metadata
- `initialize()` — restores `Indexed` state from metadata without re-indexing
- `startIndexing(file)` — streaming ingestion with 1000-row batch INSERTs
- `startTransactionalIndexing(file)` — atomic ingestion for files <100MB (collects all data in memory, writes in single `@Transaction`)

Progress is tracked via `CountingInputStream` wrapping the file input stream.

### Ingestion Strategies

| Strategy | When Used | Behavior |
|----------|-----------|----------|
| **Full Rebuild** | Generic XMLTV (non-iptv-org) | Destroys DB, recreates, parses entire file |
| **Clear and Load** | iptv-org sources | Deletes stale programmes (>24h), upserts new data with REPLACE |
| **Transactional** | Files <100MB | Collects all data in memory, writes atomically in single `@Transaction` |
| **Streaming** | Files >=100MB | 1000-row batch INSERTs, lower memory footprint |

The transactional strategy prevents the UI from seeing partial or empty EPG data. If it OOMs, it automatically falls back to streaming mode.

After all inserts, the FTS index is rebuilt:
```sql
INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')
```

---

## EPG Grid (TV Guide)

The EPG Grid is a 24-hour channel schedule view accessible from the Category Grid screen for Live TV.

### XmltvEpgService

**Class** (`core/network/.../xmltv/XmltvEpgService.kt`) bridging XMLTV data into the Xtream-compatible `EpgResponse` format used by the grid.

**Three-layer data resolution:**
1. **Parsed results cache** (SharedPreferences, 12h TTL) — instant return
2. **Local XMLTV file** — parse from `xmltv_global.xml` with channel + time filters
3. **Provider-native EPG** — fallback to Xtream `get_simple_data_table` API

**Channel matching** (4-tier fallback):
1. Exact match on `epgChannelId`
2. Case-insensitive match on `epgChannelId`
3. Exact display name match
4. Normalized name match (strip non-alphanumeric, collapse whitespace, lowercase)

The parsed XMLTV is filtered to a 48-hour window (24h past + 24h future) during parse to minimize memory.

### EpgViewModel

**ViewModel** (`core/ui/.../viewmodels/EpgViewModel.kt`) driving the grid UI.

**State:** `Loading` → `Success(channelRows, timeSlots, currentTimeSlot, selectedDate)` | `Error`

**Flow:**
1. Loads items for category (max 50 channels for performance)
2. Calls `repository.getEpgBulkForItems()` (tries XMLTV first, falls back to provider)
3. Builds `EpgChannelRow` list filtered by selected date
4. Generates 48 x 30-minute `TimeSlot` objects covering the full day
5. Calculates `currentTimeSlot` index for auto-scroll

**Features:**
- Date navigation: `selectPreviousDay()`, `selectNextDay()`, `jumpToNow()`
- Force refresh: clears both EPG and XMLTV caches
- In-grid search: filters programmes by title across all loaded channel rows
- Dev mode: shows EPG load time and match count

### Grid Layout

**TV** (`tv/.../feature/epg/EpgGuideScreen.kt` + `EpgGridLayout.kt`):
- Two-pane layout: channel list (20% width) + time grid (80% width)
- 48 x 30-minute time slots covering 24 hours
- Current time column highlighted
- Auto-scrolls to current time on load
- Synchronized horizontal scrolling across all channel rows
- D-pad navigable, press OK on channel or programme to start playback
- Date navigation arrows + "Jump to Now" button
- In-grid search with GlassPanel input field

**Mobile** (`mobile/.../feature/epg/MobileEpgGuideScreen.kt` + `MobileEpgTimeline.kt`):
- Scaffold with TopAppBar
- LazyColumn for channel list
- Horizontal scrollable timeline
- Swipe gestures for date navigation
- Pull-to-refresh

---

## EPG Browser (Search)

The EPG Browser is a standalone screen for full-text searching across the entire XMLTV dataset. Accessed from Content Type Selection via the book icon (only visible when `xmltv_global.xml` exists).

### XmltvSearchService

**Class** (`core/network/.../xmltv/XmltvSearchService.kt`) implementing dual-path search.

**Search strategy (in order):**
1. **SQLite FTS MATCH** — when `EpgIndexer.state` is `Indexed`, queries `epg_programme_fts`. Wraps query as `"<sanitized>"*` for prefix matching. Typically <100ms.
2. **SQLite LIKE** — fallback if FTS returns empty (due to tokenization edge cases) or throws. Uses `title_lowercase LIKE '%query%'`.
3. **XML scan** — fallback when index not yet built. Uses `XmltvParser.searchByTitle()` streaming parse. Takes 1-2 seconds.

All queries are time-windowed: past 1 day to future 6 days. Max 500 results.

The `XmltvSearchResult` includes a `searchedFromIndex: Boolean` flag so the UI can show `[indexed]` or `[XML scan]`.

### EpgBrowserViewModel

**ViewModel** (`core/ui/.../viewmodels/EpgBrowserViewModel.kt`) orchestrating search and paging.

**State machine:**

| State | Description |
|-------|-------------|
| `Idle` | Ready for search |
| `NoEpgFile` | No `xmltv_global.xml` exists |
| `Searching` | Search in progress |
| `Indexing(progressPercent, programmesIndexed)` | FTS index being built |
| `Results(query, programs, totalAirings, truncated, searchTimeMs, searchedFromIndex)` | Search results |
| `Error(message)` | Search failed |

**Result grouping:** `XmltvSearchResult.programmes` are grouped by `title.trim().lowercase()`, sorted by airing count descending (most-aired first). Each group becomes an `EpgBrowserProgram` with a list of `EpgBrowserAiring` (channel name + time range).

**Paging 3 integration:** For large datasets (2M+ programmes), the ViewModel exposes `PagingData<EpgSearchResultRow>` flows for both "Now Playing" and search results, using Room's built-in `PagingSource` support.

### Search UI

**TV** (`tv/.../feature/epgbrowser/EpgBrowserScreen.kt`):
- GlassPanel search field
- TvLazyColumn with ProgramCard items
- D-pad navigable
- Shows search source indicator: `[indexed]` or `[XML scan]`
- Indexing progress banner with percentage and programme count

**Mobile** (`mobile/.../feature/epgbrowser/MobileEpgBrowserScreen.kt`):
- Scaffold with search field
- LazyColumn with expandable cards (first 3 airings shown, expand for all)
- Linear progress indicator during indexing
- Keyboard integration

---

## iptv-org Auto-Detection Pipeline

This pipeline automatically determines which XMLTV guide files to download based on the user's Live TV channels, using the open-source [iptv-org](https://github.com/iptv-org) database.

### IptvOrgApiCache

**Singleton** (`core/network/.../xmltv/IptvOrgApiCache.kt`) downloading and caching iptv-org API files.

**Endpoints:**
- `https://iptv-org.github.io/api/channels.json` — full channel database
- `https://iptv-org.github.io/api/guides.json` — guide file index

**Caching layers:**
1. `@Volatile` in-memory cache (within session)
2. Disk cache at `cache/iptv_org_channels.json` and `cache/iptv_org_guides.json` (7-day TTL)

WiFi-only downloads. On cellular, returns stale cached data if available. Uses `Json.decodeFromStream()` for memory-safe parsing of large JSON files.

### IptvOrgChannelMatcher

**Object** (`core/network/.../xmltv/IptvOrgChannelMatcher.kt`) matching user channels to the iptv-org database.

**Two-phase matching:**

| Phase | Method | Example |
|-------|--------|---------|
| 1 | Exact `epgChannelId` → iptv-org `channel.id` | `"BBCOne.uk"` → `"BBCOne.uk"` |
| 1 | Case-insensitive `epgChannelId` | `"bbcone.uk"` → `"BBCOne.uk"` |
| 2 | Normalized name → iptv-org `name` + `alt_names` | `"BBC One"` → `"BBC One"` |

Name normalization: lowercase → strip non-alphanumeric (keep spaces) → collapse whitespace → trim.

Returns `MatchResult(matchedChannelIds, matchCount, totalChannels)`.

### IptvOrgGuideSelector

**Object** (`core/network/.../xmltv/IptvOrgGuideSelector.kt`) selecting the optimal set of guide files.

Uses a **greedy set-cover algorithm**: repeatedly picks the guide file covering the most uncovered channels, with tie-breaking preference for the user's preferred language. Capped at 15 guide files to bound download time and storage.

Each guide file is identified by `site|lang` (e.g., `directv.us|en`) and maps to a URL like:
```
https://iptv-org.github.io/epg/guides/en/directv.us.xml
```

### IptvOrgGuideResolver

**Singleton** (`core/network/.../xmltv/IptvOrgGuideResolver.kt`) orchestrating the full pipeline.

**State machine:**

| State | Description |
|-------|-------------|
| `Idle` | Not started |
| `Resolving` | Pipeline running |
| `Resolved(guides, matchCount, totalChannels)` | Guides selected |
| `NoMatch` | No channels matched iptv-org |
| `Failed(reason)` | Pipeline failed |

**Resolution is cached:** resolved guide URLs are persisted in SharedPreferences (as serialized JSON) and survive app restarts. Re-resolution only triggers every 24 hours, or on explicit "Re-detect" from settings.

**Pipeline steps:**
1. Fetch iptv-org API data (`IptvOrgApiCache`)
2. Match channels (`IptvOrgChannelMatcher`)
3. Select guides (`IptvOrgGuideSelector`)
4. Persist results to SharedPreferences
5. Trigger download (`EpgFileManager.downloadAndMergeGuides()`)

---

## Settings & Configuration

**AppSettings keys:**

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `epg_mode` | String | `"auto"` | `"auto"` for iptv-org, `"manual"` for user URL |
| `epg_url` | String | `""` | User-provided XMLTV URL (manual mode) |
| `epg_timezone_offset` | Int | `0` | Timezone override (-12 to +14 hours) |
| `epg_preferred_lang` | String | `"en"` | Preferred language for auto-detect guide selection |

**Settings screen controls** (both TV and mobile):
- EPG Mode selector (Auto / Manual)
- EPG URL field (manual mode only)
- Timezone override cycle button (Auto/UTC-12 to UTC+14)
- Preferred language field (auto mode only)
- Download button, status display (state, file size, last refreshed)
- Index status (programme/channel counts or indexing progress)
- Re-detect button (auto mode, forces `IptvOrgGuideResolver.forceResolve()`)

**Timezone override behavior:** Changing the timezone triggers automatic re-indexing because stored epoch values in SQLite depend on the parse-time timezone interpretation.

---

## Caching Strategy

| Cache | Location | TTL | Purpose |
|-------|----------|-----|---------|
| iptv-org `channels.json` | `cache/iptv_org_channels.json` | 7 days | Channel database for matching |
| iptv-org `guides.json` | `cache/iptv_org_guides.json` | 7 days | Guide index for selection |
| Resolved guides | SharedPreferences (JSON) | 24h re-resolve | Per-site XMLTV URLs |
| XMLTV file | `cache/xmltv_global.xml` | 12h (iptv-org) / 24h (generic) | Merged EPG data |
| Per-site XMLTVs | `cache/epg_guides/*.xml` | Temporary | Individual guides before merge |
| SQLite index | `databases/epg_index.db` | Until XMLTV changes | FTS4 search index |
| Parsed EPG results | SharedPreferences per-provider | 12h | XmltvEpgService grid cache |

**Network constraints:**
- All EPG downloads: WiFi/Ethernet only (skip on cellular, use stale cache)
- iptv-org API: in-memory + disk cache with 7-day TTL
- XMLTV: streaming downloads (64KB buffers, zero in-memory buffering)
- 3 retries with exponential backoff on all downloads

**Memory safety:**
- `HttpURLConnection` instead of Ktor (avoids buffering entire response)
- Streaming `XmlPullParser` (no in-memory DOM tree)
- 1000-row batch INSERTs (~200KB per batch)
- `OutOfMemoryError` caught at every I/O boundary with `System.gc()` and fallback paths

---

## Data Models Reference

### Xtream EPG Models (`core/player/.../model/EpgModels.kt`)

Used by the EPG Grid and provider-native EPG:

```kotlin
data class EpgProgram(
    val id: String,
    val epgId: String?,
    val title: String,
    val start: String,        // epoch seconds as string
    val end: String,
    val description: String?,
    val channelId: String?,
    val hasArchive: Int?
)

data class EpgResponse(val listings: List<EpgProgram>)
data class EpgChannelRow(val channel: MediaItem, val programs: List<EpgProgram>)
data class TimeSlot(val startTime: Long, val endTime: Long, val slotIndex: Int)
```

### XMLTV Models (`core/network/.../xmltv/XmltvModels.kt`)

Internal models for parsed XMLTV data:

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
```

### iptv-org Models (`core/network/.../xmltv/IptvOrgApiModels.kt`)

```kotlin
data class IptvOrgChannel(val id: String, val name: String, val altNames: List<String>,
                          val country: String?, val languages: List<String>)
data class IptvOrgGuide(val channel: String, val site: String, val lang: String)
data class SelectedGuide(val url: String, val site: String, val lang: String,
                         val channelIds: Set<String>)
data class ChannelRef(val epgChannelId: String?, val name: String)
```

### Room Entities (`core/network/.../xmltv/epgindex/`)

```kotlin
data class EpgChannelEntity(val xmltvId: String, val displayName: String, val iconUrl: String?)

data class EpgProgrammeEntity(val id: Long, val channelId: String, val title: String,
                              val titleLowercase: String, val description: String?,
                              val category: String?, val startEpoch: Long, val endEpoch: Long)

data class EpgProgrammeFts(val title: String)  // FTS4 content table

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
| `EpgFileManager.kt` | Singleton | Background XMLTV download lifecycle manager |
| `XmltvParser.kt` | Object | Streaming XMLTV parser with timezone override |
| `XmltvSearchService.kt` | Class | Dual-path search (SQLite FTS → LIKE → XML scan) |
| `XmltvEpgService.kt` | Class | XMLTV → EpgResponse adapter for grid |
| `XmltvMerger.kt` | Object | Multi-file XMLTV merger (streaming) |
| `XmltvModels.kt` | Data | XMLTV channel/programme/search models |
| `EpgBrowserModels.kt` | Data | Browser UI models (program + airings) |
| `IptvOrgEpgSource.kt` | Object | URL builders, ingestion config, detection helpers |

### iptv-org Integration (`core/network/.../xmltv/`)

| File | Type | Description |
|------|------|-------------|
| `IptvOrgGuideResolver.kt` | Singleton | Main auto-detection orchestrator |
| `IptvOrgGuideSelector.kt` | Object | Greedy set-cover guide selection |
| `IptvOrgChannelMatcher.kt` | Object | 2-phase channel matching (ID + name) |
| `IptvOrgApiCache.kt` | Singleton | API file downloader with 7-day TTL |
| `IptvOrgApiModels.kt` | Data | API response models |

### SQLite Indexing (`core/network/.../xmltv/epgindex/`)

| File | Type | Description |
|------|------|-------------|
| `EpgIndexer.kt` | Singleton | Index builder (streaming + transactional) |
| `EpgIndexDatabase.kt` | Room DB | Database singleton (v3, destructive migration) |
| `EpgIndexDao.kt` | DAO | FTS MATCH, LIKE, paged queries, cleanup |
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

### Xtream EPG Models (`core/player/.../model/`)

| File | Type | Description |
|------|------|-------------|
| `EpgModels.kt` | Data | EpgProgram, EpgResponse, EpgChannelRow, TimeSlot |
| `EpgUtils.kt` | Utility | `isCurrentProgram()` for highlighting |

### UI Screens

| File | Platform | Description |
|------|----------|-------------|
| `tv/.../feature/epg/EpgGuideScreen.kt` | TV | EPG Grid wrapper (loading/success/error) |
| `tv/.../feature/epg/EpgGridLayout.kt` | TV | Grid implementation (channels + time) |
| `tv/.../feature/epgbrowser/EpgBrowserScreen.kt` | TV | EPG Browser with GlassPanel |
| `mobile/.../feature/epg/MobileEpgGuideScreen.kt` | Mobile | EPG Grid with Scaffold |
| `mobile/.../feature/epg/MobileEpgTimeline.kt` | Mobile | Horizontal timeline component |
| `mobile/.../feature/epgbrowser/MobileEpgBrowserScreen.kt` | Mobile | Expandable card browser |

### Integration Points

| File | How EPG is used |
|------|-----------------|
| `MediaRepository.kt` | `getEpgBulkForItems()` (XMLTV → Xtream adapter), `triggerAutoDetectEpg()` |
| `AppSettings.kt` | `epgMode`, `epgUrl`, `epgTimezoneOffsetHours`, `epgPreferredLang` |
| `CategoryViewModel.kt` | Extracts `ChannelRef` from Live TV, triggers auto-detect |
| `Screen.kt` (navigation) | `Screen.EpgGuide(categoryId, name)`, `Screen.EpgBrowser` |
| `SettingsScreen.kt` (TV + Mobile) | EPG mode, URL, timezone, download controls |
