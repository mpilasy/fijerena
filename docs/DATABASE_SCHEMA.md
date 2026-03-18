# Fijerena Database Schema

This document details the complete database schema for the Fijerena application, including Room SQLite databases and structured SharedPreferences storage.

---

## 1. Settings Database (`providers.db`)
**Version:** 5

Manages media provider configurations, authentication metadata, and persistent EPG source URLs.

### Table: `epg_pipeline_stats`
| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER (PK) | Auto-generated ID (Always 1) |
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
| `type` | TEXT | Provider type: `XTREAM`, `JELLYFIN`, `SMB`, `LOCAL` |
| `config` | TEXT | JSON blob for type-specific config (e.g., SMB share) |
| `providerSettings` | TEXT | JSON blob for per-provider preferences |
| `createdAt` | INTEGER | Timestamp when created |
| `lastUsedAt` | INTEGER | Timestamp of last access |
| `isActive` | INTEGER | Boolean (0/1) if currently selected |

### Table: `epg_source`
| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER (PK) | Auto-generated ID |
| `url` | TEXT | XMLTV source URL |
| `label` | TEXT | Display label for the source |
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

---

## 2. EPG Index Database (`epg_index.db`)
**Version:** 13

Indexed Electronic Program Guide data from XMLTV sources. Utilizes FTS4 for fast schedule searching. This database is considered transient and may be cleared during schema updates.

### Table: `epg_channel`
| Column | Type | Description |
|--------|------|-------------|
| `xmltv_id` | TEXT (PK) | Unique channel ID from XMLTV |
| `source_id` | INTEGER (PK) | Originating source ID (Composite PK with `xmltv_id`) |
| `display_name` | TEXT | Channel name |
| `icon_url` | TEXT | URL to channel logo |

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

### Virtual Table: `epg_programme_fts` (FTS4)
Provides full-text search over `epg_programme`.
- **Content Entity:** `EpgProgrammeEntity`
- **Columns:** `title`
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
**Version:** 7

Persistent cache for Xtream Codes API metadata to enable offline browsing.

### Table: `xtream_categories`
| Column | Type | Description |
|--------|------|-------------|
| `categoryId` | TEXT (PK) | Provider category ID |
| `providerId` | INTEGER (PK)| Foreign key to `providers.id` |
| `type` | TEXT (PK) | `LIVE`, `VOD`, or `SERIES` |
| `categoryName` | TEXT | Display name |
| `parentId` | INTEGER | Parent category reference |
| `contentHash` | INTEGER | For stale data detection |

### Table: `xtream_streams`
| Column | Type | Description |
|--------|------|-------------|
| `streamId` | INTEGER (PK)| Provider stream ID |
| `providerId` | INTEGER (PK)| Foreign key to `providers.id` |
| `type` | TEXT (PK) | `LIVE` or `VOD` |
| `name` | TEXT | Stream name |
| `categoryId` | TEXT | Foreign key to `xtream_categories` |
| `streamIcon` | TEXT | Thumbnail URL |
| `epgChannelId` | TEXT | External EPG reference |
| `streamType` | TEXT | Protocol hint |
| `added` | TEXT | Timestamp from provider |
| `tvArchive` | INTEGER | Boolean (0/1) for catch-up support |
| `tvArchiveDuration` | INTEGER | Catch-up window in days |
| `description` | TEXT | Enriched VOD plot/summary |
| `cast` | TEXT | Comma-separated cast members |
| `director` | TEXT | Director name |
| `genre` | TEXT | Genre string |
| `releaseDate` | TEXT | Release date |
| `rating` | TEXT | Content rating |
| `duration` | TEXT | Runtime |
| `youtubeTrailer` | TEXT | YouTube video ID |

### Table: `xtream_series`
| Column | Type | Description |
|--------|------|-------------|
| `seriesId` | INTEGER (PK)| Provider series ID |
| `providerId` | INTEGER (PK)| Foreign key to `providers.id` |
| `name` | TEXT | Series title |
| `categoryId` | TEXT | Foreign key to `xtream_categories` |
| `cover` | TEXT | Poster URL |
| `plot` | TEXT | Series summary |
| `cast` | TEXT | Cast members |
| `director` | TEXT | Director info |
| `genre` | TEXT | Genre string |
| `releaseDate` | TEXT | Launch date |
| `rating` | TEXT | Content rating |
| `rating5based` | REAL | Numerical score |
| `youtubeTrailer` | TEXT | YouTube video ID |

### Table: `xtream_episodes`
| Column | Type | Description |
|--------|------|-------------|
| `id` | TEXT (PK) | Xtream episode ID |
| `providerId` | INTEGER (PK)| Foreign key to `providers.id` |
| `seriesId` | INTEGER | Foreign key to `xtream_series.seriesId` |
| `season` | INTEGER | Season number |
| `episodeNum` | INTEGER | Episode number |
| `title` | TEXT | Episode title |
| `overview` | TEXT | Episode plot summary |

---

## 4. Per-Provider Local Storage (SharedPreferences)

Located in `media_cache_{providerId}.xml`. Stores user-specific data that is not provided by the media server.

### Stored JSON Objects
Data is stored as serialized JSON strings of Kotlin Data Classes.

| Key | Data Class | Description |
|-----|------------|-------------|
| `watch_history_v2` | `List<WatchedItem>` | Playback positions and completion status. |
| `favorites_v2` | `List<FavoriteItem>` | User-starred streams. |
| `favorite_categories`| `List<FavoriteCategoryItem>`| User-starred categories. |
| `favorite_shows` | `List<FavoriteShowItem>` | User-starred TV series. |
| `recent_categories` | `List<RecentCategory>` | Last 20 browsed categories. |

---

## 5. App Global Settings (SharedPreferences)

Located in `app_settings.xml`.

| Key | Type | Description |
|-----|------|-------------|
| `theme_id` | TEXT | Current dark theme variant |
| `ui_scale` | FLOAT | UI scaling factor (0.4 - 1.0) |
| `is_dev_mode` | BOOLEAN | Toggles developer features |
| `epg_auto_refresh` | BOOLEAN | Background sync toggle |
| `active_provider_id`| LONG | Current global provider selection |

---

## 6. Other Persistent Storage (SharedPreferences)

The application uses several specialized SharedPreferences files for internal state management.

| Filename | Purpose |
|----------|---------|
| `epg_file_manager_prefs` | Tracks ingest status, last refresh timestamps, and file counts for EPG sources. |
| `drive_settings_sync_prefs`| Stores Google Drive sync metadata, including last sync time and folder IDs. |
| `player_prefs` | Stores in-player state, such as whether control discoverability hints have been shown. |
| `provider_creds_{id}` | (Encrypted) Stores passwords and sensitive tokens for individual providers using `EncryptedSharedPreferences`. |
