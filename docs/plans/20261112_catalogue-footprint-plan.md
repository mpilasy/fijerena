# Catalogue footprint

**Status:** Parked 2026-11-12, not started. The user asked to keep the ideas in a plan; nothing is
decided. Before Phase 1, measure the catalogue database on the real devices (Device Info → Storage).

## Goal

A smaller app footprint on the device, most of all on TVs with little storage, with no change to
what the user sees or to how fast the lists open.

## What exists (survey 2026-11-12)

- **The catalogue database is almost all of the footprint.** On the TV emulator on 2026-11-12,
  Device Info showed `xtream_v2.db` at 306 MB, with the guide database (`epg_index.db`) at 14 MB,
  the image cache at 6 MB, all caches at 10 MB and app files at 1.5 MB.
- **The image cache is already bounded:** Coil's `DiskCache` (`FijerenaApplication`, limit
  `ImageCache.maxBytes`: 2% of the storage, 64 MB to 512 MB). It removes the least recently used
  images first: each read writes a `READ` line in its journal and moves the entry to the recent
  end of an access-ordered map, so the order lasts across restarts (checked in the Coil 3.6.3
  classes). Android can also clear the app's cache folder when the storage is low. No work needed.
- **Deleted rows give their space back:** `xtream_v2.db` runs `auto_vacuum = FULL` (see
  `ProviderRepository`), so a periodic `VACUUM` would save nothing more.
- **Where the space goes**, measured with `dbstat` on an emulator copy of `xtream_v2.db` from
  2026-11-06 (305 MB, schema 31, page size 4096, no free pages; four sources, 601,000 channels and
  films, 122,000 series):

  | Part | Size | Notes |
  |---|---|---|
  | `xtream_streams` rows | 110 MB | `streamIcon` 43 MB, `name` 18 MB, `contentHash` 6 MB |
  | `xtream_series` rows | 108 MB | `plot` 37 MB, `backdropPath` 21 MB, `cover` 9 MB |
  | `xtream_streams` indexes | 54 MB | five indexes and the primary key |
  | Search (FTS) | 21 MB | streams 18.5 MB, series 2.6 MB |
  | `xtream_series` indexes | 8 MB | |

  - Image addresses (`streamIcon`, `cover`, `backdropPath`) are 73 MB together. Each one is a full
    address, and most of a source's addresses start with the same server and path (for example a
    TMDB image server and size folder), repeated in every row.
  - `index_xtream_streams_providerId_type` (8.3 MB) is a prefix of
    `index_xtream_streams_providerId_type_categoryId`, which SQLite can use for the same queries.
    On `xtream_series`, `Index(providerId)` is a prefix of `Index(providerId, tmdbId)` and
    `Index(providerId, allowed)`.

## Phases

| Phase | What | Saves (on the 305 MB copy) | Risk |
|---|---|---|---|
| 1 | Drop the indexes that are a prefix of another index (`XtreamStreamEntity` `[providerId, type]`, `XtreamSeriesEntity` `[providerId]`). Check with `EXPLAIN QUERY PLAN` that every DAO query still uses an index. New Room migration; update docs/DATABASE_SCHEMA.md and the rollback helpers in `XtreamDatabaseMigrationTest`. | About 9 MB (3%) | Low |
| 2 | Store each source's common image address start once, and only the rest in each row; join them again where the address is read (sync writes, the 7 files that read `streamIcon`, the 5 that read `backdropPath`). New Room migration that rewrites the rows. | About 40 to 50 MB (15%) | Medium: touches sync and every screen with an image |

## Not planned

- **Load series descriptions only when a series opens** (up to 37 MB): details would open more
  slowly and would not show without a connection.
- **A periodic image-cache cleanup by age:** the size limit already bounds the cache, and an age rule
  would only make the app download posters again sooner.

## Open questions

| ID | Question | Options |
|---|---|---|
| CF-Q1 | Do Phase 1 at all? | (a) Yes, with the next database change. (b) No. |
| CF-Q2 | Do Phase 2 at all? | (a) Yes, after measuring the real devices. (b) No. |
| CF-Q3 | Phase 2: how to find the common start? | (a) The longest start shared by most of a source's addresses, found at each sync. (b) A fixed list of known image servers (TMDB, Jellyfin). |

## Progress

| Phase | Status |
|---|---|
| 1 | Not started |
| 2 | Not started |
