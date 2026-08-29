# Fijerena Database Schema

This document details the complete database schema for the Fijerena application, including Room SQLite databases and structured SharedPreferences storage.

---

## 1. Settings Database (`providers.db`)
**Version:** 10

Manages media provider configurations, authentication metadata, and persistent EPG source URLs.

### Table: `epg_pipeline_stats`
| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER (PK) | Fixed singleton row ID (always 1, not auto-generated) |
| `updated_at_ms` | INTEGER | Timestamp of last pipeline run |
| `duration_ms` | INTEGER | Total duration of the run |
| `sources_processed` | INTEGER | Number of sources processed |
| `errors` | INTEGER | Number of errors encountered |
| `total_channels` | INTEGER | Total channels from pipeline |
| `total_programmes` | INTEGER | Total programmes from pipeline |

### Table: `providers`
| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER (PK) | Auto-generated ID |
| `name` | TEXT | User-friendly display name |
| `url` | TEXT | Server URL or connection string |
| `username` | TEXT | Username for authentication |
| `type` | TEXT | Provider type: `XTREAM`, `JELLYFIN`, `SMB`, `LOCAL`, `REMOTE_M3U` |
| `config` | TEXT | JSON blob for type-specific config (e.g., SMB share) |
| `providerSettings` | TEXT | JSON blob for per-provider preferences |
| `createdAt` | INTEGER | Timestamp when created |
| `lastUsedAt` | INTEGER | Timestamp of last access |
| `isActive` | INTEGER | Boolean (0/1) if currently selected |
| `lastSyncedAtMs` | INTEGER | Timestamp of last manual/background content sync (added v6) |
| `lastSyncDurationMs` | INTEGER | Duration of last sync (added v6) |
| `lastSyncError` | TEXT | Error message from last failed sync, if any (added v6) |
| `lastSyncInserted` | INTEGER | Rows inserted by the last sync (added v9) |
| `lastSyncUpdated` | INTEGER | Rows updated by the last sync (added v9) |
| `lastSyncDeleted` | INTEGER | Rows deleted by the last sync (added v9) |

The three `lastSync{Inserted,Updated,Deleted}` columns hold the last **successful** sync's `SyncDelta`
(they are `COALESCE`d, not zeroed, on a failed run). All three zero means the catalog did not change.

### Table: `epg_source`
| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER (PK) | Auto-generated ID |
| `url` | TEXT | XMLTV source URL |
| `label` | TEXT | Display label for the source |
| `provider_id` | INTEGER | Owning provider ID (enforced `NOT NULL` in v8, backfilled from active/first provider) |
| `timezone_offset_hours` | INTEGER | Manual offset for parsing |
| `added_at_ms` | INTEGER | Timestamp when added |
| `last_ingested_at_ms` | INTEGER | Timestamp of last successful sync |
| `last_error` | TEXT | Last error message if sync failed |
| `enabled` | INTEGER | Boolean (0/1) toggle |
| `last_channels` | INTEGER | Channel count from last ingest |
| `last_programmes` | INTEGER | Programme count from last ingest |
| `last_download_bytes` | INTEGER | Size of XML data fetched |
| `ingest_method` | TEXT | Ingestion strategy: `DOWNLOADED`, `STREAMED`, or `XTREAM_API` |
| `last_ingestion_duration_ms` | INTEGER | Time spent parsing/inserting |
| `last_download_duration_ms` | INTEGER | Time spent fetching XML file |
| `last_content_sha256` | TEXT | SHA-256 of the last ingested payload, decompressed for `.gz` sources; null = never hashed (added v10) |
| `etag` | TEXT | `ETag` from the last download, sent back as `If-None-Match` (added v10) |
| `last_modified_header` | TEXT | `Last-Modified` from the last download, sent back as `If-Modified-Since` (added v10) |

**Index:** `index_epg_source_provider_id` on `(provider_id)`

The last three columns drive refresh change detection — see `docs/epg_guide.md` → "Change Detection".

---

## 2. EPG Index Database (`epg_index.db`)
**Version:** 16

Indexed Electronic Program Guide data from XMLTV sources. Utilizes FTS4 for fast schedule searching. This database is considered transient and may be cleared during schema updates.

### Table: `epg_channel`
| Column | Type | Description |
|--------|------|-------------|
| `xmltv_id` | TEXT (PK) | Unique channel ID from XMLTV (composite PK with `source_id`) |
| `source_id` | INTEGER (PK) | Originating source ID (composite PK with `xmltv_id`) |
| `display_name` | TEXT | Channel name |
| `icon_url` | TEXT | URL to channel logo |

**Index:** `idx_channel_source` on `(source_id)`

### Table: `epg_channel_staging`
Mirrors `epg_channel` exactly (same columns, no indices). Used as a write target during ingestion when staging is enabled, so the live `epg_channel` table stays queryable until the atomic swap (`executeSwapToMain()`) promotes staged rows.

### Table: `epg_programme`
| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER (PK) | Auto-generated ID |
| `channel_id` | TEXT | Reference to `epg_channel.xmltv_id` |
| `title` | TEXT | Program title |
| `title_lowercase` | TEXT | Lowercase title for fallback search |
| `description` | TEXT | Program description |
| `category` | TEXT | Program genre/category |
| `start_epoch` | INTEGER | Start time (Unix epoch) |
| `end_epoch` | INTEGER | End time (Unix epoch) |
| `source_id` | INTEGER | Reference to `epg_source.id` in Settings DB |

**Indices (7):** `idx_programme_start` (start_epoch), `idx_programme_end` (end_epoch), `idx_programme_time_range` (start_epoch, end_epoch), `idx_programme_channel` (channel_id), `idx_programme_dedup` (channel_id, source_id, start_epoch — UNIQUE), `idx_programme_source` (source_id), `idx_programme_channel_source` (channel_id, source_id).

### Table: `epg_programme_staging`
Mirrors `epg_programme` (same columns), with a single unique index `idx_programme_staging_dedup` on `(channel_id, source_id, start_epoch)`. Same role as `epg_channel_staging` — write target during staged ingestion before the atomic swap.

### Virtual Table: `epg_programme_fts` (FTS4)
Provides full-text search over `epg_programme`.
- **Content Entity:** `EpgProgrammeEntity`
- **Columns:** `title`, `description`
- **Tokenizer:** `unicode61`

### Table: `epg_index_metadata`
| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER (PK) | Always 1 |
| `file_size_bytes` | INTEGER | Total index size |
| `file_last_modified_ms`| INTEGER | Last write timestamp |
| `indexed_at_ms` | INTEGER | Last indexing completion |
| `channel_count` | INTEGER | Global channel count |
| `programme_count` | INTEGER | Global programme count |
| `timezone_offset_hours`| INTEGER | Default offset |

---

## 3. Xtream Cache Database (`xtream_v2.db`)
**Version:** 17

Persistent cache for Xtream Codes API metadata to enable offline browsing, plus the durable
`watch_state` and `favorite_state` tables. (v10 added FTS4 search tables for streams/series; v11
added `excluded` flags and indexes; v12 added TMDB detail fields; v13 added `xtream_epg_cache` table;
v14 added `plotFetchedAt` for TMDB synopses; v15 added `watch_state` and an index on
`xtream_streams(providerId, tmdbId)`; v16 added `favorite_state`; v17 added `posterPath` on
`xtream_streams` and `xtream_series` for TMDB poster art caching.)

Despite the file name, neither `watch_state` nor `favorite_state` is Xtream-only — `MediaRepository`
backs Xtream, SMB, Local, and Remote M3U through them. They live here because this is the database
`MediaRepository` already owns; Jellyfin is out of scope (it keeps this state server-side).

### Table: `xtream_categories`
| Column | Type | Description |
|--------|------|-------------|
| `categoryId` | TEXT (PK) | Provider category ID |
| `providerId` | INTEGER (PK)| Foreign key to `providers.id` |
| `type` | TEXT (PK) | `LIVE`, `VOD`, or `SERIES` |
| `categoryName` | TEXT | Display name |
| `parentId` | INTEGER | Parent category reference |
| `contentHash` | INTEGER | For stale data detection |
| `excluded` | INTEGER | Category exclusion toggle flag (added v11) |

**Indices:** `(providerId, type)`, `(providerId, type, excluded)`

### Table: `xtream_streams`
| Column | Type | Description |
|--------|------|-------------|
| `streamId` | INTEGER (PK)| Provider stream ID |
| `providerId` | INTEGER (PK)| Foreign key to `providers.id` |
| `type` | TEXT (PK) | `LIVE` or `VOD` |
| `num` | INTEGER | Provider-supplied ordering number |
| `name` | TEXT | Stream name |
| `categoryId` | TEXT | Foreign key to `xtream_categories` |
| `streamIcon` | TEXT | Thumbnail URL |
| `epgChannelId` | TEXT | External EPG reference |
| `streamType` | TEXT | Protocol hint |
| `added` | TEXT | Timestamp from provider |
| `customSid` | TEXT | Provider custom SID passthrough |
| `directSource` | TEXT | Direct source URL override from provider |
| `tvArchive` | INTEGER | Boolean (0/1) for catch-up support |
| `tvArchiveDuration` | INTEGER | Catch-up window in days |
| `contentHash` | INTEGER | For stale data detection |
| `description` | TEXT | Enriched VOD plot/summary |
| `cast` | TEXT | Comma-separated cast members |
| `director` | TEXT | Director name |
| `genre` | TEXT | Genre string |
| `releaseDate` | TEXT | Release date |
| `rating` | TEXT | Content rating |
| `duration` | TEXT | Runtime |
| `youtubeTrailer` | TEXT | YouTube video ID |
| `excluded` | INTEGER | Exclusion toggle flag (added v11) |
| `contentRating` | TEXT | Age/content classification rating (added v12) |
| `tmdbId` | TEXT | Sourced TMDB ID (added v12) |
| `containerExtension` | TEXT | Extension (e.g. `mp4`, `mkv`) (added v12) |
| `detailFetchedAt` | INTEGER | Timestamp of detail cache fetch (added v12) |
| `posterPath` | TEXT | Sourced TMDB poster path (added v17) |

**Indices:** `(providerId, type)`, `(categoryId, providerId)`, `(providerId, type, categoryId)`, `(providerId, type, categoryId, excluded)`, `(providerId, tmdbId)` (added v15, for TMDB sibling dedup)

### Table: `xtream_series`
| Column | Type | Description |
|--------|------|-------------|
| `seriesId` | INTEGER (PK)| Provider series ID |
| `providerId` | INTEGER (PK)| Foreign key to `providers.id` |
| `num` | INTEGER | Provider-supplied ordering number |
| `name` | TEXT | Series title |
| `categoryId` | TEXT | Foreign key to `xtream_categories` |
| `cover` | TEXT | Poster URL |
| `plot` | TEXT | Series summary |
| `cast` | TEXT | Cast members |
| `director` | TEXT | Director info |
| `genre` | TEXT | Genre string |
| `releaseDate` | TEXT | Launch date |
| `lastModified` | TEXT | Provider last-modified stamp |
| `rating` | TEXT | Content rating |
| `rating5based` | REAL | Numerical score |
| `youtubeTrailer` | TEXT | YouTube video ID |
| `episodeRunTime` | TEXT | Nominal episode runtime |
| `backdropPath` | TEXT | Comma-separated backdrop URLs |
| `contentHash` | INTEGER | For stale data detection |
| `excluded` | INTEGER | Exclusion toggle flag (added v11) |
| `contentRating` | TEXT | Age/content classification rating (added v12) |
| `tmdbId` | TEXT | Sourced TMDB ID (added v12) |
| `detailFetchedAt` | INTEGER | Timestamp of detail cache fetch (added v12) |
| `posterPath` | TEXT | Sourced TMDB poster path (added v17) |

**Indices:** `(providerId)`, `(categoryId, providerId)`, `(providerId, categoryId, excluded)`

### Table: `xtream_episodes`
| Column | Type | Description |
|--------|------|-------------|
| `id` | TEXT (PK) | Xtream episode ID |
| `providerId` | INTEGER (PK)| Foreign key to `providers.id` |
| `seriesId` | INTEGER | Foreign key to `xtream_series.seriesId` |
| `season` | INTEGER | Season number |
| `episodeNum` | INTEGER | Episode number |
| `title` | TEXT | Episode title |
| `containerExtension` | TEXT | Extension (e.g. `mp4`, `mkv`) |
| `overview` | TEXT | Episode plot summary |
| `plot` | TEXT | Extended plot summary (added v9) |
| `airDate` | TEXT | Original air date (added v9) |
| `duration` | TEXT | Runtime as reported by provider (`HH:MM:SS`) |
| `durationSecs` | INTEGER | Runtime in seconds (added v9) |
| `bitrate` | INTEGER | Encoded bitrate (added v9) |
| `rating` | TEXT | Episode rating |
| `movieImage` | TEXT | Episode still/thumbnail URL |
| `tmdbId` | TEXT | TMDB identifier, used for synopsis enrichment (added v9) |
| `plotFetchedAt` | INTEGER | Timestamp of TMDB synopsis fetch (added v14) |
| `contentHash` | INTEGER | For stale data detection |

**Indices:** `(seriesId, providerId)`, `(providerId)`

### Table: `xtream_epg_cache` (added v13)
Per-stream EPG payload cache table.

| Column | Type | Description |
|--------|------|-------------|
| `providerId` | INTEGER (PK)| Foreign key to `providers.id` |
| `streamId` | INTEGER (PK)| Provider stream ID |
| `payload` | TEXT | JSON string of EPG listings |
| `updatedAt` | INTEGER | Timestamp when cached |

### Table: `watch_state` (added v15)
Durable playback position and completion state, kept forever. Replaces the `watch_history_v3`
SharedPreferences blob, which truncated to `providerSettings.watchHistorySize` on every write and
silently evicted anything older. See `docs/plans/watch-state-durable-storage-plan.md`.

| Column | Type | Description |
|--------|------|-------------|
| `providerId` | INTEGER (PK) | Foreign key to `providers.id` |
| `itemId` | TEXT (PK) | Movie / episode / channel ID |
| `contentType` | TEXT (PK) | `LIVE_TV`, `MOVIES`, or `TV_SHOWS` |
| `itemName` | TEXT | Display name at the time of writing |
| `categoryId` | TEXT | Owning category |
| `positionMs` | INTEGER | Saved playback position |
| `durationMs` | INTEGER | Item duration as known at save time |
| `isCompleted` | INTEGER | Boolean (0/1). Sticky on progress upserts (`MAX(existing, new)`); only `setWatched(false)` clears it |
| `updatedAt` | INTEGER | Last-modified stamp for this row |
| `lastPlayedAt` | INTEGER | Set by playback only; drives the Recent row. Stays null for a manual watched/unwatched mark |
| `seriesId` | TEXT | Owning series, for episodes |
| `episodeId` | TEXT | Episode ID, for episodes |
| `seriesName` | TEXT | Series display name |
| `episodeExtension` | TEXT | Container extension needed to rebuild the episode URL |
| `audioTrackIndex` | INTEGER | Last selected audio track, restored on replay (series-level fallback) |
| `subtitleTrackIndex` | INTEGER | Last selected subtitle track, restored on replay |

**Indices:** `(providerId, contentType, lastPlayedAt)`, `(providerId, seriesId)`

**TMDB dedup:** a title cached under several catalogue variants (language/quality) is watched once
and reads as watched everywhere. Movies join `xtream_streams` on a shared `tmdbId`. Episodes cannot —
episode-level `tmdbId` is effectively never populated by providers — so the episode query is a
two-level join: `xtream_series` finds sibling series by shared **series-level** `tmdbId`, then
`xtream_episodes` matches each sibling's `(season, episodeNum)`. Dedup is Xtream-only by
construction; other providers have no catalogue table to join against and degrade to no dedup.

### Virtual Table: `xtream_streams_fts` (FTS4, added v10)
Full-text search over `xtream_streams.name`. Content table: `xtream_streams`. Tokenizer: `unicode61`.

### Virtual Table: `xtream_series_fts` (FTS4, added v10)
Full-text search over `xtream_series.name`. Content table: `xtream_series`. Tokenizer: `unicode61`.

---

### Table: `favorite_state` (added v16)
Durable favourites, kept forever. Replaces the `favorites_v2` and `favorite_categories`
SharedPreferences blobs, which were capped at `providerSettings.favoritesMaxSize` (default 100) and
truncated on every write, silently evicting the oldest entry. See
`docs/plans/favorites-durable-storage-plan.md`.

One table serves both blobs; `kind` discriminates. For `CATEGORY` rows, `itemId` **is** the category
id and `parentCategoryId` is NULL.

| Column | Type | Description |
|--------|------|-------------|
| `providerId` | INTEGER (PK) | Foreign key to `providers.id` |
| `itemId` | TEXT (PK) | Stream ID, or the category ID when `kind = CATEGORY` |
| `contentType` | TEXT (PK) | `LIVE_TV`, `MOVIES`, or `TV_SHOWS` |
| `kind` | TEXT (PK) | `STREAM` or `CATEGORY` |
| `name` | TEXT | Display name at the time of favouriting |
| `parentCategoryId` | TEXT? | The stream's owning category; NULL for `kind = CATEGORY` |
| `createdAt` | INTEGER | When it was favourited; drives the newest-first ordering the UI shows |

**Index:** `(providerId, kind, contentType, createdAt)`

**No cap.** Rows are inserted and deleted only — nothing truncates. `MediaRepository` still serves
reads from an in-memory snapshot of this table, because Compose calls `isFavorite()` synchronously
during composition; the snapshot is filled in `setProvider()`, which runs on `Dispatchers.IO`.

---

## 4. Per-Provider Local Storage (SharedPreferences)

Located in `media_cache_{providerId}.xml`. Stores user-specific data that is not provided by the media server.

### Stored JSON Objects
Data is stored as serialized JSON strings of Kotlin Data Classes.

| Key | Data Class | Description |
|-----|------------|-------------|
| `recent_categories_{contentType}` | `List<RecentCategory>` | Last 20 browsed categories, one key per content type (`LIVE_TV`, `MOVIES`, `TV_SHOWS`). Still a capped blob — see the note below. |

**Retired:** `watch_history_v3` (and its `watch_history_v2` predecessor) no longer exist. Watch
position and completion live in the `watch_state` table (§3). On the first `setProvider()` after
upgrade, `MediaRepository.backfillAndPurgeWatchState()` copies the blob into `watch_state`, sets
`watch_state_migrated_v1`, then removes both keys. The flag is per-provider, never global, so a
provider not opened between the dual-write and purge releases still gets copied before it is purged.

**Retired:** `favorites_v2` and `favorite_categories` no longer exist either. Favourites live in the
`favorite_state` table (§3). `MediaRepository.backfillAndPurgeFavorites()` copies both blobs on the
first `setProvider()` after upgrade, sets `favorites_migrated_v1`, then removes both keys — again
per-provider, never global.

`recent_categories_{contentType}` is the last remaining capped blob (20 entries, and a decode failure
yields an empty list). It is left as-is deliberately: it is a convenience list the user never
curates, so eviction there is the intended behaviour rather than data loss.

### Scalar Keys (last-browsed position restore)

| Key | Type | Description |
|-----|------|-------------|
| `watch_state_migrated_v1` | BOOLEAN | One-time flag: this provider's watch-history blob has been copied into `watch_state` |
| `favorites_migrated_v1` | BOOLEAN | One-time flag: this provider's favourites blobs have been copied into `favorite_state` |
| `last_content_type` | TEXT | Content type last browsed |
| `last_live_category` / `last_live_item` | TEXT | Last Live TV category and item |
| `last_movies_category` / `last_movies_item` | TEXT | Last Movies category and item |
| `last_tvshows_category` / `last_tvshows_item` | TEXT | Last TV Shows category and item |

---

## 5. App Global Settings (SharedPreferences)

Located in `app_settings.xml`. Backed by `AppSettings` (`core/network/.../AppSettings.kt`).

| Key | Type | Description |
|-----|------|-------------|
| `dev_mode` | BOOLEAN | Toggles developer features |
| `theme_id` | TEXT | Current dark theme variant (default `deep_night`) |
| `ui_style_id` | TEXT | Look-and-feel preset, independent of color (default `material`) |
| `ui_scale` | FLOAT | UI scaling factor (0.4 - 1.0) |
| `app_language` | TEXT | ISO 639-1 code (`en`, `mg`) |
| `provider_name` | TEXT | Display name shown for the provider |
| `has_provider_cache` | BOOLEAN | Cached "at least one provider exists" flag for fast cold start |
| `watch_history_size` | INT | Max watch-history entries (1-100, default 25) |
| `favorites_max_size` | INT | Max favorites (10-500, default 100) |
| `watch_delay_seconds`| INT | Delay before a live channel counts as watched (5-120) |
| `auto_resume_enabled`| BOOLEAN | Resume playback from stored position |
| `cache_expiry_hours` | INT | Content cache lifetime (1-168) |
| `epg_url` | TEXT | Legacy global XMLTV URL |
| `epg_timezone_offset`| INT | Global XMLTV timezone override (-12..14) |
| `epg_auto_refresh` | BOOLEAN | Background EPG sync toggle |
| `epg_refresh_time` | TEXT | EPG refresh start time `HH:mm` (default `02:00`) |
| `epg_refresh_interval`| INT | EPG refresh interval hours: 4/8/12/24/48, or -1 (Never) |
| `content_auto_refresh`| BOOLEAN | Background provider content sync toggle |
| `content_refresh_time`| TEXT | Content refresh start time `HH:mm` (default `04:00`) |
| `cellular_live_multiplier` | FLOAT | Live buffer multiplier on cellular (0.5-3.0) |
| `cellular_vod_multiplier` | FLOAT | VOD buffer multiplier on cellular (0.5-3.0) |
| `search_history` | TEXT | Last 20 search terms, U+001F-separated |
| `epg_search_history` | TEXT | Last 20 EPG search terms, U+001F-separated |
| `has_seen_favorite_hint` | BOOLEAN | One-time long-press-to-favorite hint dismissed |

The active provider is **not** stored here — it is the `providers.isActive` column in `providers.db`.

---

## 6. Other Persistent Storage (SharedPreferences)

The application uses several specialized SharedPreferences files for internal state management.

| Filename | Keys | Purpose |
|----------|------|---------|
| `epg_file_manager` | `migrated_to_sources_v1` | One-time flag: legacy single-EPG-file state has been migrated to `epg_source` rows. |
| `epg_indexer_state` | `fts_stale` | Survives process death so an interrupted FTS rebuild is retried on next indexer run. |
| `drive_sync_prefs` | `sync_enabled`, `last_sync` | Google Drive settings-sync toggle and last successful sync timestamp. |
| `player_prefs` | `hints_dismissed` | Whether the player control discoverability hints have been dismissed (TV only). |
| `provider_creds_{id}` | per-provider | (Encrypted) Passwords and sensitive tokens per provider, via `EncryptedSharedPreferences`. |
| `xtream_secure_credentials` | `url`, `username`, `password`, `auth_response`, `remember_me` | (Encrypted) Xtream login credentials and cached auth response, held by `AccountManager`. |
