# Fijerena Database Schema

This document details the complete database schema for the Fijerena application, including Room SQLite databases and structured SharedPreferences storage.

---

## 1. Settings Database (`providers.db`)
**Version:** 14

Manages media provider configurations, authentication metadata, persistent EPG source URLs, and
user profiles. (v11 added `profiles`; v12 added `profiles.colorIndex`; v13 added
`providers.providerKey` and `sync_tombstone` for live sync; v14 added `epg_source.source_key`,
`sync_outbox` and `sync_clock`, filled by triggers.)

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

### Table: `profiles` (added v11)
The people using the app. Favourites, watch state, Recent Categories and the last-browsed
bookmarks are per profile; providers, EPG sources and settings are shared. See
`docs/plans/20260929_live-sync-plan.md` → User profiles.

| Column | Type | Description |
|--------|------|-------------|
| `id` | TEXT (PK) | `default` for the profile every install starts with; a random UUID for any other |
| `name` | TEXT | Display name |
| `createdAt` | INTEGER | Creation timestamp |
| `colorIndex` | INTEGER | Avatar colour, an index into `CinemaProfileColors.palette` in core:ui; default 0 (added v12) |

The `default` row is inserted by `MIGRATION_10_11` on upgrade and by the database's `onCreate`
callback on a fresh install (`INSERT OR IGNORE` in both). A fixed id rather than a per-device UUID,
so that once sync lands every device's pre-existing data converges on one profile. Which profile a
device is using is `active_profile_id` in `app_settings` (§5), not a column here.

`default` can be deleted once another profile is in use. Its data lives in provider-level storage
rather than `_profile_` files, so `ProfileRepository` clears it there: Jellyfin providers'
`providers.username` and the password/session in `provider_creds_<id>`, and the Recent Categories,
`last_*` bookmarks and legacy blobs in each `media_cache_<id>` (migration flags set, so the legacy
backfill never recreates rows for it). Other providers' logins in `provider_creds_<id>` are shared
and stay.

### Table: `sync_tombstone` (added v13)
Deleted providers and profiles, kept so live sync can tell other devices (see
`docs/plans/20260929_live-sync-plan.md` → Deletions). Written in the same transaction as the delete
(`ProviderDao.deleteProviderRecordingTombstone`, `ProfileDao.deleteRecordingTombstone`); pruned after
90 days at startup. Favourite and history deletions have their own `sync_tombstone` in `xtream_v2.db`.

| Column | Type | Description |
|--------|------|-------------|
| `kind` | TEXT (PK) | `provider`, `profile` or `epg_source` (v14) |
| `itemKey` | TEXT (PK) | The provider's `providerKey`, the profile id, or the source's `source_key` |
| `deletedAt` | INTEGER | When it was deleted, on the sync clock (v14: written as 0 and stamped by trigger) |

**Index:** `(deletedAt)`

### Table: `providers`
| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER (PK) | Auto-generated ID |
| `name` | TEXT | User-friendly display name |
| `url` | TEXT | Server URL or connection string |
| `username` | TEXT | Username for authentication |
| `type` | TEXT | Provider type: `XTREAM`, `JELLYFIN`, `SMB`, `LOCAL`, `REMOTE_M3U` |
| `config` | TEXT | JSON blob for type-specific config (e.g., SMB share) |
| `providerSettings` | TEXT | JSON blob for per-provider preferences. Category filters are no longer kept here but per profile in `category_filters` (section 6); on upgrade they were copied to every profile and stripped from this JSON |
| `createdAt` | INTEGER | Timestamp when created |
| `lastUsedAt` | INTEGER | Timestamp of last access |
| `isActive` | INTEGER | Boolean (0/1) if currently selected |
| `lastSyncedAtMs` | INTEGER | Timestamp of last manual/background content sync (added v6) |
| `lastSyncDurationMs` | INTEGER | Duration of last sync (added v6) |
| `lastSyncError` | TEXT | Error message from last failed sync, if any (added v6) |
| `lastSyncInserted` | INTEGER | Rows inserted by the last sync (added v9) |
| `lastSyncUpdated` | INTEGER | Rows updated by the last sync (added v9) |
| `lastSyncDeleted` | INTEGER | Rows deleted by the last sync (added v9) |
| `providerKey` | TEXT (unique) | Random UUID naming the provider in live sync — the same on every device, unlike `id`, and unchanged by editing URL or name. Backfilled for existing rows by `MIGRATION_12_13` (added v13) |

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
| `source_key` | TEXT (unique) | Random UUID naming the source in live sync, like `providers.providerKey`; backfilled by `MIGRATION_13_14` (added v14) |

**Index:** `index_epg_source_provider_id` on `(provider_id)`; `index_epg_source_source_key` (unique) on `(source_key)`

### Tables: `sync_outbox`, `sync_clock` (added v14)
The `providers.db` counterparts of `xtream_v2.db`'s (§3): `sync_outbox` holds keys changed locally
and not yet sent — `kind` (`provider`, `profile`, `epg_source`, `provider_login`,
`category_filters`, `setting`), `profileId` (the profile for per-person kinds, `shared` otherwise),
`itemKey` (`providerKey`, profile id, `source_key` or setting key), `hlc`; primary key
`(kind, profileId, itemKey)`, indexed on `hlc`. `sync_clock` is this database's one-row hybrid
logical clock plus the `applying` flag. Deleting a provider removes its logins' and filters'
entries; deleting a profile removes its entries (their tombstones cover them).

### Sync triggers (v14, installed on every open)
`SettingsSyncTriggers.install`, in `onOpen`. Each ticks `sync_clock` and queues into `sync_outbox`
in the writing transaction, unless `applying` is set. Updates count only when a synced column
changes, so sync statistics, activation and EPG ingestion bookkeeping are never queued.

| Trigger | Fires on | Queues |
|---|---|---|
| `sync_providers_insert` / `_update` | insert; update changing `name`, `url`, `username`, `type`, `config` or `providerSettings` | `provider` |
| `sync_profiles_insert` / `_update` | insert; update changing `name` or `colorIndex` | `profile` |
| `sync_epg_source_insert` / `_update` | insert; update changing `url`, `label`, `timezone_offset_hours`, `enabled` or `provider_id` | `epg_source` |
| `sync_epg_source_delete` | delete | records an `epg_source` tombstone (which queues it) |
| `sync_tombstone_insert` | a new `sync_tombstone` row | the tombstone's kind; stamps `deletedAt` when it is 0 |

Values kept in SharedPreferences are queued by `SettingsSyncQueue` instead, just after they are
written (no shared transaction): provider passwords (`provider`), Jellyfin logins
(`provider_login`), category filters (`category_filters`), and the synced settings (`setting`):
`theme_id`, `dev_mode` (per profile), `epg_auto_refresh`, `epg_refresh_time`, `epg_refresh_interval`.

The last three columns drive refresh change detection — see `docs/epg_guide.md` → "Change Detection".

---

## 2. EPG Index Database (`epg_index.db`)
**Version:** 17

Indexed Electronic Program Guide data from XMLTV sources. Utilizes FTS4 for fast schedule searching. This database is considered transient and may be cleared during schema updates — no `addMigrations()` is registered for any version jump, only `fallbackToDestructiveMigration(true)`; a version bump always rebuilds empty and re-syncs from the configured XMLTV sources on the next run, which is the intended, accepted behavior for this specific database (unlike `xtream_v2.db`, it holds no durable user data). v17 added `source_id` indices to both staging tables.

### Table: `epg_channel`
| Column | Type | Description |
|--------|------|-------------|
| `xmltv_id` | TEXT (PK) | Unique channel ID from XMLTV (composite PK with `source_id`) |
| `source_id` | INTEGER (PK) | Originating source ID (composite PK with `xmltv_id`) |
| `display_name` | TEXT | Channel name |
| `icon_url` | TEXT | URL to channel logo |

**Index:** `idx_channel_source` on `(source_id)`

### Table: `epg_channel_staging`
Mirrors `epg_channel` exactly (same columns). Used as a write target during ingestion when staging is enabled, so the live `epg_channel` table stays queryable until the atomic swap (`executeSwapToMain()`) promotes staged rows.

**Index (added v17):** `idx_channel_staging_source` on `(source_id)` — backs `EpgIndexDao`'s per-source staging queries (`clearStagingChannelsForSources`, `transferChannelsFromStaging`); `source_id` is the second column of the primary key, not the leading one, so those queries had no usable index before this.

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
Mirrors `epg_programme` (same columns). Same role as `epg_channel_staging` — write target during staged ingestion before the atomic swap.

**Indices:** `idx_programme_staging_dedup` (channel_id, source_id, start_epoch — UNIQUE), `idx_programme_staging_source` (source_id, added v17 — same rationale as `epg_channel_staging`'s, backing `clearStagingProgrammesForSources`/`transferProgrammesFromStaging`).

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
**Version:** 22

Persistent cache for Xtream Codes API metadata to enable offline browsing, plus the durable
`watch_state` and `favorite_state` tables. (v10 added FTS4 search tables for streams/series; v11
added `excluded` flags and indexes; v12 added TMDB detail fields; v13 added `xtream_epg_cache` table;
v14 added `plotFetchedAt` for TMDB synopses; v15 added `watch_state` and an index on
`xtream_streams(providerId, tmdbId)`; v16 added `favorite_state`; v17 added `posterPath` on
`xtream_streams` and `xtream_series` for TMDB poster art caching; v18 added indices on
`xtream_series(providerId, tmdbId)` and `xtream_episodes(providerId, season, episodeNum)` for the
TMDB sibling-dedup joins below, which had no covering index on either table; v19 added
`xtream_series.episodesFetchedAt`, a persisted freshness stamp so a series detail screen can skip
the network round trip for its episode list when a stored copy is under 24 hours old, instead of
re-fetching the whole list on every single open; v20 added `profileId` to the primary key of
`watch_state` and `favorite_state`, rebuilding both tables and assigning every existing row to the
`default` profile — see `docs/plans/20260929_live-sync-plan.md` → User profiles; v21 added
`sync_tombstone` for live sync; v22 added `sync_outbox` and `sync_clock`, filled by triggers.)

Every connection also gets `PRAGMA synchronous = NORMAL` and `PRAGMA journal_size_limit = 10485760`
(10MB) set on open (added v18, no schema change) — NORMAL trades the fsync-per-transaction durability
FULL gives against an OS-level crash (not an app crash) for throughput under WAL; journal_size_limit
caps how large the WAL file is allowed to sit at after a checkpoint instead of growing unbounded
across this catalogue's frequent syncs.

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
| `episodesFetchedAt` | INTEGER | Timestamp episodes were last fetched/persisted for this series; backs the 24-hour episode-list cache in `XtreamMediaProvider.getSeriesDetail` (added v19) |

**Indices:** `(providerId)`, `(categoryId, providerId)`, `(providerId, categoryId, excluded)`, `(providerId, tmdbId)` (added v18, for the TMDB sibling-dedup joins below)

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

**Indices:** `(seriesId, providerId)`, `(providerId)`, `(providerId, season, episodeNum)` (added v18, for the sibling-episode joins below)

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
silently evicted anything older. See `docs/plans/20260828_watch-state-durable-storage-plan.md`.

| Column | Type | Description |
|--------|------|-------------|
| `providerId` | INTEGER (PK) | Foreign key to `providers.id` |
| `profileId` | TEXT (PK) | Foreign key to `profiles.id` in `providers.db` (added v20) |
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

**Indices:** `(providerId, profileId, contentType, lastPlayedAt)`, `(providerId, profileId, seriesId)`

Every read and write is for one provider **and** one profile, including the TMDB sibling joins
below. Deleting a provider deletes every profile's rows.

**TMDB dedup:** a title cached under several catalogue variants (language/quality) is watched once
and reads as watched everywhere. Movies join `xtream_streams` on a shared `tmdbId`. Episodes cannot —
episode-level `tmdbId` is effectively never populated by providers — so the episode query is a
two-level join: `xtream_series` finds sibling series by shared **series-level** `tmdbId`, then
`xtream_episodes` matches each sibling's `(season, episodeNum)`. Dedup is Xtream-only by
construction; other providers have no catalogue table to join against and degrade to no dedup.

### Virtual Table: `xtream_streams_fts` (FTS4, added v10)
Full-text search over `xtream_streams.name`. Content table: `xtream_streams`. Tokenizer: `unicode61`.
Room auto-generates `room_fts_content_sync_xtream_streams_fts_*` triggers (AFTER INSERT/UPDATE,
BEFORE UPDATE/DELETE) for the `@Fts4(contentEntity = ...)` entity, which keep the index in sync on
every insert/update/delete — no manual rebuild call is needed or should be added (a redundant
`INSERT INTO xtream_streams_fts(xtream_streams_fts) VALUES('rebuild')` used to run after every
stream sync; it duplicated what Room's triggers already did and, on this catalog's row count, held
SQLite's single writer connection for 30-60+ seconds, blocking every other write in the app — removed
in `XtreamContentManager.syncStreams`).

### Virtual Table: `xtream_series_fts` (FTS4, added v10)
Full-text search over `xtream_series.name`. Content table: `xtream_series`. Tokenizer: `unicode61`.
Same Room-generated trigger sync as `xtream_streams_fts` above; the redundant post-sync rebuild was
removed from `XtreamContentManager.syncSeries` for the same reason.

---

### Table: `favorite_state` (added v16)
Durable favourites, kept forever. Replaces the `favorites_v2` and `favorite_categories`
SharedPreferences blobs, which were capped at `providerSettings.favoritesMaxSize` (default 100) and
truncated on every write, silently evicting the oldest entry. See
`docs/plans/20260828_favorites-durable-storage-plan.md`.

One table serves both blobs; `kind` discriminates. For `CATEGORY` rows, `itemId` **is** the category
id and `parentCategoryId` is NULL.

| Column | Type | Description |
|--------|------|-------------|
| `providerId` | INTEGER (PK) | Foreign key to `providers.id` |
| `profileId` | TEXT (PK) | Foreign key to `profiles.id` in `providers.db` (added v20) |
| `itemId` | TEXT (PK) | Stream ID, or the category ID when `kind = CATEGORY` |
| `contentType` | TEXT (PK) | `LIVE_TV`, `MOVIES`, or `TV_SHOWS` |
| `kind` | TEXT (PK) | `STREAM` or `CATEGORY` |
| `name` | TEXT | Display name at the time of favouriting |
| `parentCategoryId` | TEXT? | The stream's owning category; NULL for `kind = CATEGORY` |
| `createdAt` | INTEGER | When it was favourited; drives the newest-first ordering the UI shows |

**Index:** `(providerId, profileId, kind, contentType, createdAt)`

**No cap.** Rows are inserted and deleted only — nothing truncates. `MediaRepository` still serves
reads from an in-memory snapshot of this table, because Compose calls `isFavorite()` synchronously
during composition; the snapshot is filled in `setProvider()`, which runs on `Dispatchers.IO`.

### Table: `sync_tombstone` (added v21)
Removed favourites and cleared watch histories, kept so live sync can tell other devices — without
them the next device to sync would bring the item back. See
`docs/plans/20260929_live-sync-plan.md` → Deletions (tombstones).

| Column | Type | Description |
|--------|------|-------------|
| `providerId` | INTEGER (PK) | The provider, by local id; the sync client sends its `providerKey` |
| `profileId` | TEXT (PK) | The profile the favourite or history belonged to |
| `kind` | TEXT (PK) | `favorite_stream`, `favorite_category` or `watch_clear` |
| `itemId` | TEXT (PK) | The favourite's `itemId`; empty for `watch_clear` |
| `contentType` | TEXT (PK) | The favourite's `contentType`; empty for `watch_clear` |
| `deletedAt` | INTEGER | When it was removed, on the sync clock (v22); for `watch_clear`, every older watch row of that provider and profile is dropped |

**Index:** `(deletedAt)`

Written by `FavoriteStateDao` and `WatchStateDao` in the same transaction as the delete, with
`deletedAt = 0`, which the `sync_tombstone_insert` trigger replaces with the sync clock (v22); re-adding
the same favourite (or restoring it from an export or a provider copy) removes its tombstone. Not
written for Jellyfin's history, which it keeps server-side. Deleting a provider or profile removes
its rows here (its own tombstone in `providers.db` covers them). Pruned after 90 days at startup.

### Table: `sync_outbox` (added v22)
Keys changed locally and not yet sent to the sync server — only the key: the sync client reads the
current row (or its tombstone) when it sends, so repeated changes to one item collapse into one
entry. Same key columns as `sync_tombstone`, plus `hlc` (sync clock at the latest change; indexed).
`kind` is `watch`, `favorite_stream`, `favorite_category` or `watch_clear`. Deleting a provider or
profile removes its entries (its own tombstone covers them).

### Table: `sync_clock` (added v22)
One row (`id` = 1): `hlc`, this database's hybrid logical clock in milliseconds — each local change
moves it to `max(now, hlc + 1)` — and `applying`, set while changes received from another device are
written so they aren't queued straight back.

### Sync triggers (v22, installed on every open)
Room doesn't manage these; `XtreamSyncTriggers.install` drops and recreates them in the database's
`onOpen`, so fresh installs, migrations and destructive rebuilds all get them, and an app update
that changes one replaces it. They queue with delete-then-insert, never `INSERT OR REPLACE`: inside
a trigger SQLite applies the firing statement's conflict rule, and Room's `@Insert`/`@Update` run as
`OR ABORT`. Each ticks
`sync_clock` and upserts the changed key into `sync_outbox` in the writing transaction, unless
`applying` is set:

| Trigger | Fires on | Queues |
|---|---|---|
| `sync_watch_state_insert` / `_update` | any insert/update of `watch_state` | `watch` |
| `sync_favorite_state_insert` / `_update` | any insert/update of `favorite_state` | `favorite_stream` / `favorite_category` |
| `sync_tombstone_insert` | a new `sync_tombstone` row | the tombstone's own kind; stamps `deletedAt` when it is 0 |

Row deletions fire nothing: a removal is queued through its tombstone, and rows deleted with their
provider or profile are covered by that one's tombstone.

### Catalog Lifecycle, Orphan Pruning & Compaction
Catalog entries (`xtream_streams`, `xtream_series`, `xtream_episodes`, `xtream_categories`, `favorite_state`, `xtream_epg_cache`, and `watch_state`) reside in `xtream_v2.db`, while the provider entities that own them live in `providers.db`. Because SQLite cannot enforce cross-database foreign key cascades, deleting a provider in `providers.db` does not automatically purge its rows in `xtream_v2.db`.

1. **Cascaded Provider Deletion:** `ProviderRepository.deleteProvider(id)` cascades through all catalog tables in `xtream_v2.db`, deletes associated encrypted/plaintext SharedPreferences (`provider_creds_{id}.xml`, `media_cache_{id}.xml`, `xtream_cache_{id}.xml`, and every profile's `provider_creds_{id}_profile_*` / `media_cache_{id}_profile_*`), removes associated EPG sources, and invokes `VACUUM` followed by `PRAGMA wal_checkpoint(TRUNCATE)`.
2. **Orphan Pruning (`pruneOrphanedCatalogData`):** Sweeps `xtream_v2.db` across all catalog DAOs using `deleteOrphaned(validProviderIds)` (`WHERE providerId NOT IN (:validProviderIds)`), sweeps orphaned SharedPreferences files, and deletes orphaned EPG sources.
3. **Safety Circuit Breaker:** If `validProviderIds.isEmpty()`, `pruneOrphanedCatalogData()` immediately aborts and returns `(0, 0)`, preventing accidental deletion if provider loading ever returned empty.
4. **Automatic Background Maintenance:** A non-blocking background sweep runs on startup in `TvNavHost` and `MobileNavHost` on `Dispatchers.IO`, and during scheduled runs in `EpgSyncWorker`.
5. **Manual Maintenance ("Shrink Database"):** Exposed in Settings → Data & Sync → "Shrink Database" (`SettingsViewModel.pruneDatabase()`), passing `forceVacuum = true` to force compaction and report rows removed and bytes reclaimed.
6. **SQLite WAL Compaction:** In SQLite WAL mode, `VACUUM` shifts freed pages into the WAL file (`xtream_v2.db-wal`). Executing `PRAGMA wal_checkpoint(TRUNCATE)` immediately following `VACUUM` is required to truncate the WAL file and release space back to the Android filesystem.

---

## 4. Per-Provider Local Storage (SharedPreferences)

Located in `media_cache_{providerId}.xml`. Stores user-specific data that is not provided by the media server.

**Per profile:** `recent_categories_{contentType}` and the `last_*` bookmarks belong to one profile.
The `default` profile keeps them in `media_cache_{providerId}.xml`, where they always lived; any
other profile has its own `media_cache_{providerId}_profile_{profileId}.xml` holding the same keys.
The migration flags stay in the shared file. Provider deletion and the orphan sweep remove the
per-profile files along with the shared one.

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
| `dev_mode_<profileId>` | BOOLEAN | Toggles developer features for that profile (absent = off). Replaced the install-wide `dev_mode`, copied to every profile on upgrade |
| `active_profile_id` | TEXT | Profile using this device; absent means `default`. Per device, never synced |
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
| `category_filters` | `{providerId}_{profileId}` | Category filters (`CategoryFilters` JSON) of one profile on one provider, via `CategoryFiltersStore`. No key = no filters. A new profile copies the creating profile's keys; provider and profile deletion remove theirs. |
| `drive_sync_prefs` | `sync_enabled`, `last_sync` | Google Drive settings-sync toggle and last successful sync timestamp. |
| `player_prefs` | `hints_dismissed` | Whether the player control discoverability hints have been dismissed (TV only). |
| `provider_creds_{id}` | per-provider | (Encrypted) Passwords and sensitive tokens per provider, via `EncryptedSharedPreferences`. |
| `provider_creds_{id}_profile_{profileId}` | per-provider, per-profile | (Encrypted) A non-Default profile's own Jellyfin login: `username`, `password`, `jellyfin_token`, `jellyfin_user_id`. The Default profile uses `provider_creds_{id}` and `providers.username`. Only Jellyfin logins are per profile. |
| `xtream_secure_credentials` | `url`, `username`, `password`, `auth_response`, `remember_me` | (Encrypted) Xtream login credentials and cached auth response, held by `AccountManager`. |
