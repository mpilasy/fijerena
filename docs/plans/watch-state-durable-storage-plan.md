# Durable Watch State Plan

**Requirement:** playback position and completed state must persist indefinitely. Today both are
fields on a row inside a JSON list truncated to 25 entries on every write, so both are silently
lost to eviction.

**Secondary goal (Phase 5):** when a provider lists the same work several times — five language
variants of one movie or one episode — completing any one of them should mark all of them
complete.

**Secondary goal (Phase 6):** let the user mark a movie or episode watched or unwatched directly
from the UI, rather than only earning completion through playback.

**Scope:** Xtream and the other local-blob providers (SMB, Local, Remote M3U). Jellyfin is
untouched: it short-circuits on `usesServerUserData` and owns this state server-side. Per-provider
isolation is preserved throughout — every new row is keyed by `providerId`, exactly like the seven
existing tables.

**Live TV is in scope, for recency only.** `savePlaybackPosition` does return early on
`ContentType.LIVE_TV` (`MediaRepository.kt:780`), so no live row ever carries a position or a
completion flag. But that is not the only writer: `saveLastPlayedItem`
(`MediaRepository.kt:395-432`) calls `addToWatchHistory` for **every** content type, live included,
and `getRecentItems(ContentType.LIVE_TV)` reads those rows — the repository says so itself at
`:904`, "LIVE_TV degenerates to plain recency". Live rows must therefore move to the new table
along with the rest, or the Live TV Recent row goes empty the moment reads flip. The same writer is
how a Movies or TV Shows session that started and never ticked progress gets into the list at all,
so it is not a live-only concern.

---

## Why the blob exists

Not a decision anyone should be second-guessed for. `6648c0d8` (2026-02-01) introduced watch
history to serve one feature, in its own words:

> "Last Watched" virtual category showing last 25 watched streams (configurable)

That is a browse row, and a bounded JSON list is the right shape for it: one read, one decode,
recency ordering for free, no schema. There was also no alternative — Room did not enter the
codebase until `ff0e53aa` (2026-02-20), nineteen days later, and it arrived for the catalog.
`0c3c4690` and `6f5f81fa` then carried the approach forward as refactors, not decisions.

The semantics drifted six months later and the storage was never revisited:

| date | commit | change |
|---|---|---|
| 2026-08-17 | `6e9abeb9` | record completion when a stream plays to its end |
| 2026-08-17 | `1b3105e4` | show resume bars and watched checks across content lists |
| 2026-08-19 | `cd7c60b7` | merge Continue Watching + Last Watched into one Recent row |
| 2026-08-22 | `faec13d4` | migrate watch history to v3 |

`1b3105e4` is the hinge. Once watched checks appeared on every content list, the 25-row cap
stopped being a display limit and became a correctness bug: a check mark that vanishes is wrong in
a way that a short Recent row never was.

## What is wrong today

1. **Eviction is silent and destructive.** `addToWatchHistory` inserts at index 0 then
   `history.take(providerSettings.watchHistorySize)` (`MediaRepository.kt:528`). Default 25, max
   100 (`ProviderSettings.kt:17`). Finish S01E01–E25 and by E26 the E01 row is gone: the episode
   renders unwatched in both `EpisodeSelectionScreen`s and drops out of the
   `getSeriesWatchProgress` rollup.
2. **A bad decode wipes everything.** `getWatchHistoryLocked` (`MediaRepository.kt:546`) wraps both
   decodes in `catch { emptyList() }`. One malformed blob returns empty and the next write persists
   that empty list. No error surfaces.
3. **No query surface.** `getSeriesWatchProgress` (`:859`) hand-rolls a GROUP BY over a linear
   scan; `getPlaybackPositions` (`:827`) carries a comment explaining it iterates history rather
   than `itemIds` to keep the manual join at `O(HistorySize)`. Both are one SQL statement.
4. **Position and completion share one fate.** They have different durability needs — a stale
   resume point aging out is arguably correct; a watched flag reverting is not.
5. **Every write is a whole-row replace, so starting playback erases progress.**
   `addToWatchHistory` (`:491`) does `removeAll { itemId && contentType }` then inserts a fresh
   `WatchedItem`, and its `playbackPosition`, `duration` and `isCompleted` parameters all default to
   `0L, 0L, false`. `saveLastPlayedItem` passes none of them, so pressing play on a half-watched
   movie zeroes its stored position and drops its completed flag; only the next progress tick puts a
   position back, and nothing puts the flag back. Start-then-quit loses both. The new write path has
   to be field-targeted rather than reproducing this (see Write path below).

---

## Target design

### Table

Lives in `XtreamDatabase` (a single app-wide file, `getInstance(context)`), alongside the existing
seven entities.

```kotlin
@Entity(
    tableName = "watch_state",
    primaryKeys = ["providerId", "itemId", "contentType"],
    indices = [
        Index(value = ["providerId", "contentType", "lastPlayedAt"]),
        Index(value = ["providerId", "seriesId"]),
    ],
)
data class WatchStateEntity(
    val providerId: Long,
    val itemId: String,
    val contentType: String,
    val itemName: String,
    val categoryId: String,
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val updatedAt: Long,
    /** Set by playback only; null when completion came from a manual mark. See Phase 6. */
    val lastPlayedAt: Long? = null,
    val seriesId: String? = null,
    val episodeId: String? = null,
    val seriesName: String? = null,
    val episodeExtension: String? = null,
    val audioTrackIndex: Int? = null,
    val subtitleTrackIndex: Int? = null,
)
```

Column set is `WatchedItem` (`MediaRepository.kt:45`) plus `providerId`, `updatedAt` and
`lastPlayedAt`. `WatchedItem.timestamp` splits into two: `updatedAt` is the row's last-modified
stamp, `lastPlayedAt` records actual playback and drives the Recent row. They are the same value
until Phase 6 introduces a way to set completion without playing anything — but the column belongs
in `MIGRATION_14_15` from the start rather than costing a second migration later.

**No `tmdbId` column.** An earlier draft carried one for Phase 5 dedup. It cannot work: nothing
writes it. `savePlaybackPosition`'s callers (`StreamLoaderViewModel.kt:477,532`) have no TMDB id to
pass, `WatchedItem` has no such field so the backfill cannot supply one either, and the column would
sit null forever — making the Phase 5 sibling lookup match nothing while looking correct. TMDB ids
already live on the catalogue rows (`XtreamStreamEntity.kt:45`, `XtreamEpisodeEntity.kt:31`), which
is the only place they are ever populated, so Phase 5 reaches them by joining watch state to the
catalogue instead of denormalizing a copy that no writer maintains.

**Naming.** The table is deliberately *not* `xtream_`-prefixed. `MediaRepository` backs SMB, Local
and Remote M3U as well as Xtream, so those rows are not Xtream rows. This breaks with the
neighbouring table names; the alternative is a name that lies about half its contents. `XtreamDatabase`
itself is by now a misnomer — renaming it is out of scope here.

**Value classes.** `SeriesId` and `EpisodeId` are `@JvmInline value class` over `String`
(`MediaIds.kt:17,22`). Store raw `String?` columns and convert at the mapper boundary rather than
registering a `TypeConverter` — it keeps the DAO signatures plain and matches how `XtreamEpisodeEntity`
already stores ids.

### Write path

**No `@Upsert`, no `OnConflictStrategy.REPLACE`.** Both write the whole row, which is defect 5
above: the two writers know about different halves of it, and whichever fires last would erase the
other's. Each writer gets a targeted `INSERT … ON CONFLICT DO UPDATE` naming only the columns it
owns. SQLite has supported upsert since 3.24 and minSdk is 30 (SQLite 3.28), so this needs no
compatibility hedge.

**Playback progress** — from `savePlaybackPosition`. Owns position, duration, completion and
`lastPlayedAt`; `COALESCE` keeps metadata that this call happens not to carry:

```sql
INSERT INTO watch_state (providerId, itemId, contentType, itemName, categoryId, positionMs,
                         durationMs, isCompleted, updatedAt, lastPlayedAt, seriesId, episodeId,
                         seriesName, episodeExtension, audioTrackIndex, subtitleTrackIndex)
VALUES (:providerId, :itemId, :contentType, :itemName, :categoryId, :positionMs, :durationMs,
        :isCompleted, :now, :now, :seriesId, :episodeId, :seriesName, :episodeExtension,
        :audioTrackIndex, :subtitleTrackIndex)
ON CONFLICT(providerId, itemId, contentType) DO UPDATE SET
    positionMs         = excluded.positionMs,
    durationMs         = excluded.durationMs,
    isCompleted        = excluded.isCompleted,
    updatedAt          = excluded.updatedAt,
    lastPlayedAt       = excluded.lastPlayedAt,
    itemName           = COALESCE(excluded.itemName, watch_state.itemName),
    seriesId           = COALESCE(excluded.seriesId, watch_state.seriesId),
    episodeId          = COALESCE(excluded.episodeId, watch_state.episodeId),
    seriesName         = COALESCE(excluded.seriesName, watch_state.seriesName),
    episodeExtension   = COALESCE(excluded.episodeExtension, watch_state.episodeExtension),
    audioTrackIndex    = COALESCE(excluded.audioTrackIndex, watch_state.audioTrackIndex),
    subtitleTrackIndex = COALESCE(excluded.subtitleTrackIndex, watch_state.subtitleTrackIndex)
```

That `COALESCE` block replaces the read-modify-write `savePlaybackPosition` does today at
`MediaRepository.kt:793-796`, where it loads the existing entry purely to carry its metadata
forward. One statement instead of a read plus a write.

`isCompleted = excluded.isCompleted` becomes `MAX(watch_state.isCompleted, excluded.isCompleted)` in
Phase 6, once there is a UI way to undo a completion — see "Playback must not silently undo a manual
mark" there. Until then it stays a plain assignment, matching today's behaviour exactly.

**Playback start** — from `saveLastPlayedItem`. Owns recency and metadata, and must **not** name
`positionMs`, `durationMs` or `isCompleted` in its `DO UPDATE` list. Inserting supplies the 0/0/false
defaults for a genuinely new row; updating leaves whatever is stored alone. This is what fixes
defect 5 rather than porting it.

**Deliberate divergence from the blob.** Under dual write (Phase 2) the table will therefore hold a
position the blob has just zeroed. Phase 2's parity check has to account for that: compare
`(itemId, contentType)` presence and `isCompleted`, and treat a table position that is non-zero
where the blob's is zero as the fix landing, not as drift. Comparing positions blindly would report
a failure on every session that started and stopped without a progress tick.

### Retention

Unbounded, which is the point, and affordable because the table grows with what was watched rather
than with catalogue size.

A row is roughly 110 bytes for a movie and 160 for an episode — the strings are `itemName` and
`seriesName` — plus about 110 bytes across the primary-key index and the three declared indices.
Call it 250 bytes all-in.

| usage | rows | on disk |
|---|---|---|
| 10 items/week, 5 years | ~2,600 | ~650 KB |
| 20 items/week, 10 years | ~10,400 | ~2.6 MB |

No sweeper. That ceiling is structural: a movie takes two hours to watch, so rows cannot be
produced faster than content can be consumed. This is the difference from the EPG cache, which
needed bounding in `56350d0d` because it grew with catalogue × refresh cycles — 53k channels
repeatedly refreshed reached 84 MB. Watch state has no such multiplier. For scale,
`XtreamStreamEntity` carries ~25 columns including `description`, `cast`, `genre` and icon URLs, so
a large VOD catalogue already occupies tens of MB in `xtream_streams`.

**Provider deletion must delete the rows across all provider types.** `deleteProvider` (`ProviderRepository.kt:116-121`)
clears EPG sources, the stored password, and `xtream_cache_$providerId` — but not
`media_cache_$providerId` and not the provider's Room rows. Deleting a provider therefore already
orphans its watch history and favorites today. That is a bounded leak while history is capped at 25
entries; with unbounded retention it becomes permanent, so `deleteProvider` needs a
`DELETE FROM watch_state WHERE providerId = :id` alongside the existing cleanup.
Because `watch_state` resides in `XtreamDatabase` while `SettingsDatabase` holds provider definitions,
`ProviderRepository` must inject `XtreamDatabase` (or `WatchStateDao`) to execute this delete for **all**
provider types (Xtream, Jellyfin, SMB, Local, Remote M3U). Worth clearing `media_cache_$providerId` in the same change.

### The cap becomes a query — on one read path only

Storage is unbounded; **display is still capped**. `providerSettings.watchHistorySize` keeps its
1–100 range and stops being a retention policy, becoming a display limit. Its meaning changes from
"how much history is kept" to "how many Recent cards are shown", so the settings-screen copy needs
updating in the same change.

The critical distinction is that the two read paths take the cap differently. Applying it to both
would reintroduce the exact bug this plan exists to fix.

**Tier 1 — the Recent row. Capped.** Hundreds of rows on disk, `watchHistorySize` cards on screen.
`getWatchHistory()` has exactly one caller, `getRecentItems` at `MediaRepository.kt:910`, and no UI
callers at all, so this is a contained change.

**Tier 2 — watched checks and resume bars on content lists. Not history at all.** Position and
completion are attributes of a stream, defined for *every* stream: 0 and false until something is
watched. They are not entries in a recency list and the cap is meaningless for them. Restricting
them by recency puts back the disappearing check mark from `1b3105e4` — episode 1 of a long series
would render unwatched the moment 25 newer items exist.

So `getPlaybackPositions` keeps its shape and only changes where it reads from. It stops decoding a
capped blob and becomes one uncapped statement:

```sql
SELECT * FROM watch_state WHERE providerId = :providerId AND contentType = :contentType
```

Mapped to `Map<String, WatchedItem>` keyed by `itemId`, exactly what the method returns today.

**Why not `LEFT JOIN` the watch columns into the catalogue query.** It was the first design here and
it does not survive contact with the call sites.

- *The values would have nowhere to go.* `refreshPerItemData` (`CategoryViewModel.kt:450-498`)
  publishes `_watchProgress` and `_watchedIds`, two `StateFlow`s that
  `TvCategoryGridScreen.kt:83-84` collects. Joined values arrive on catalogue rows instead, so
  either `MediaItem` grows watch fields — it is a `core/player` domain model, and Jellyfin, SMB and
  the M3U providers would all have to populate them — or the flows get derived from the list anyway,
  which is the lookup map again with extra steps.
- *It would make refreshes more expensive, not less.* `refreshPerItemData` re-runs when the stream
  list identity changes or a favorite is toggled (`:165-174`, `:511`, `:527`), and it is cheap
  because watch state is fetched independently of the catalogue. With the values baked into the
  rows, moving one progress bar after playback — or after a Phase 6 toggle — means re-running the
  whole category query.
- *It would only cover two of the list paths.* `getStreamsByCategory` (`XtreamStreamDao.kt:11`) and
  `getEpisodes` are joinable, but `getAllStreams` (`:18`), `searchByFts` (`:104`) and
  `getItemsIfCached` all produce lists too, and SMB, Local and Remote M3U have no catalogue tables
  to join against at all. Every one of those still needs the lookup, so the join buys a second
  mechanism rather than replacing the first.

The separation is load-bearing, in other words, and the eviction bug never came from it. Keeping it
also means Phase 3 touches one method body rather than the provider interface, the domain model and
four DAOs.

**Fetching the whole content type rather than filtering by id list is deliberate.** It matches what
the blob path does today — the comment at `MediaRepository.kt:827` explains it iterates history
instead of `itemIds` for the same reason — it avoids an `IN (…)` clause with a category's worth of
bind variables, and the row count is bounded by what the user has watched, not by catalogue size:
the Retention table above puts a decade of heavy use at ~10k rows across all content types. The
`(providerId, contentType, lastPlayedAt)` index serves the prefix.

`getPlaybackPositions` and `getPlaybackPositionsSuspend` keep their signatures, so
`CategoryViewModel.kt:471` and both `EpisodeSelectionScreen`s need no change beyond suspending
(see Threading). The non-Xtream providers are covered by the same query, with no special case.

**The TV Shows rollup stays exactly where it is.** A row in the TV Shows list is a series, and
series live in `xtream_series` while watch state is keyed by episode — no per-item lookup of any
kind can resolve a series id. So `refreshPerItemData` keeps the `getSeriesWatchProgress` +
`getEpisodeCountsBySeries` pass at `CategoryViewModel.kt:481-495` and the `progressMap`/`watched`
maps it fills, unchanged. Only the storage under `getSeriesWatchProgress` changes, from a linear
scan of the blob to the aggregate below.

**Why not columns on `xtream_streams` / `xtream_episodes` instead.** It would give a single-query
read, but it puts permanent data in ephemeral rows. `insertAll` is
`OnConflictStrategy.REPLACE` (`XtreamStreamDao.kt:59`), and REPLACE in SQLite is DELETE + INSERT —
every catalogue sync touching a row would zero the watch columns. `deleteAll(providerId, type)`
(`:62`), `XtreamEpisodeDao.deleteAll` (`:20`) and `deleteBySeriesId` (`:23`) do it wholesale. A
title the provider drops would take its watch state with it and come back unwatched. And the
non-Xtream providers have no rows in those tables, so a second mechanism would be needed anyway.
A table of its own keeps watch state outside the catalogue's lifecycle entirely.

#### The Recent query

Ordered by `lastPlayedAt`, not `updatedAt` — the Recent row means "things you played", and Phase 6
introduces rows whose completion was set without playing anything. Filtering on
`lastPlayedAt IS NOT NULL` keeps those out. Until Phase 6 lands the two columns hold the same value
and the filter is a no-op, so the query is correct from Phase 3 onward either way.

```sql
SELECT * FROM watch_state
WHERE providerId = :providerId AND contentType = :contentType AND lastPlayedAt IS NOT NULL
ORDER BY lastPlayedAt DESC LIMIT :limit
```

Two details a naive version of this would get wrong.

**Series collapse before the limit, not after.** `getRecentItems` does
`distinctBy { seriesId?.raw ?: itemId }` for TV Shows so a show appears once rather than once per
episode (`:919`). Limiting first and collapsing second would yield fewer cards than asked for —
25 episodes of one show collapse to a single card. Collapse in SQL, with a window function:

```sql
SELECT * FROM (
    SELECT *, ROW_NUMBER() OVER (
        PARTITION BY COALESCE(seriesId, itemId)
        ORDER BY lastPlayedAt DESC, itemId DESC
    ) AS rn
    FROM watch_state
    WHERE providerId = :providerId AND contentType = 'TV_SHOWS' AND lastPlayedAt IS NOT NULL
)
WHERE rn = 1
ORDER BY lastPlayedAt DESC LIMIT :limit
```

**Not a correlated subquery**, which is the obvious way to write this and is quadratic.
`COALESCE(seriesId, itemId)` is not sargable, so no index can serve the correlation and each outer
row rescans the provider's whole TV history — at the row counts this plan is explicitly designed to
reach, that is millions of comparisons on a TV box for one browse row. The window function sorts
once. Window functions need SQLite 3.25; minSdk is 30, which ships 3.28.

The `itemId DESC` tiebreak matters because `lastPlayedAt` is a millisecond clock and two episodes
can share a value — a bare `MAX` would emit both rows for one series and put the same show on screen
twice.

Movies and Live TV keep the plain query. In-progress-first partitioning stays in memory, as today
(`:923`) — it reorders the fetched page rather than selecting it. Live TV never has a resumable row
(`savePlaybackPosition` returns early for it), so that partition is a no-op there and the row stays
plain recency, exactly as the current doc comment describes.

**The cap stops being shared across content types.** Today the 25 rows are global and then filtered
per type, which the code documents as a known wart at `:907-908`: "an in-progress movie pushed out by
heavy channel surfing disappears from here too." A per-`contentType` `LIMIT` gives each type its own
allowance and that wart goes away. This is an intentional improvement, not an accident — but it is a
visible behaviour change and the doc comment at `:900-909` should be rewritten rather than left
describing storage that no longer exists.

`getSeriesWatchProgress` becomes a real aggregate:

```sql
SELECT seriesId, COUNT(DISTINCT COALESCE(episodeId, itemId)) AS completed
FROM watch_state
WHERE providerId = :providerId AND contentType = 'TV_SHOWS'
  AND isCompleted = 1 AND seriesId IS NOT NULL
GROUP BY seriesId
```

still divided by `provider.getEpisodeCountsBySeries()` as today.

### Migration

Two halves, because a Room `Migration` has no `Context` and therefore cannot read a
`SharedPreferences` file.

**Schema, 14 → 15.** Pure DDL, following the `MIGRATION_12_13` precedent (`XtreamDatabase.kt:100`),
which performed this same move for the EPG payload cache:

```kotlin
private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `watch_state` (...)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_state_providerId_contentType_lastPlayedAt` ...")
        // + the seriesId index
        // + index_xtream_streams_providerId_tmdbId — see below
    }
}
```

Two things that are easy to leave out and both fail loudly rather than silently, so they are cheap
to catch but worth writing down:

- **Register it.** `version = 14` at `XtreamDatabase.kt:20` becomes 15 and `MIGRATION_14_15` joins
  the `addMigrations(...)` chain at `:127`. Room throws on a missing migration path, so this cannot
  ship half-done.
- **Index `xtream_streams` on `(providerId, tmdbId)` in the same migration.** Phase 5 groups the
  catalogue by `tmdbId` and `XtreamStreamEntity` has no index covering it (`:9-15`) — `getByTmdbId`
  (`XtreamStreamDao.kt:52`) scans today. Adding it here rather than in Phase 5 follows the same
  reasoning as `lastPlayedAt`: an index costs nothing while unused and a second migration costs a
  release. `xtream_episodes` needs no equivalent — episode dedup is scoped to one series' rows and
  the table is small.

**Data, app-side.** Lazily, per provider, on first `MediaRepository` use: decode
`watch_history_v3`, insert the rows with `WatchedItem.timestamp` populating both `updatedAt` and
`lastPlayedAt` (every existing row came from playback), set a `watch_state_migrated_v1` boolean in
that provider's prefs. Guarded exactly like `purgeLegacyPrefsCache` (`XtreamEpgManager.kt:99`). Backfill only —
the blob is left intact until Phase 4 so a rollback loses nothing.

**Backfill must be replay-safe.** The flag write is not atomic with the row inserts — a process
death between "rows written" and "flag set" is ordinary, not an edge case, on a background app.
Backfill therefore inserts through the same progress upsert (`ON CONFLICT DO UPDATE`) defined
under Write path above, never a raw `INSERT`. Re-running the whole decode-and-insert loop after a
partial crash then just overwrites the same rows with the same values instead of failing on a
primary-key collision.

---

## Phases

**1 — Schema.** `WatchStateEntity`, `WatchStateDao`, `MIGRATION_14_15`, bump to version 15. Nothing
reads or writes the table yet. Ships dark.

**2 — Dual write + backfill.** **Both** writers dual-write: `savePlaybackPosition` through the
progress upsert and `saveLastPlayedItem` through the recency upsert. Missing the second one is the
easy mistake — it is what carries Live TV, and what puts a Movies or TV Shows row in the list before
any progress tick fires. Backfill runs on first use per provider. Reads still come from the blob.
Reversible, and where parity gets verified: for a provider with history, table and blob should agree
on `(itemId, contentType)` presence and on `isCompleted`, with the position caveat in Write path
above. Also compare `seriesId`/`episodeId` null-ness against the blob's `seriesId`/`episodeId`
fields — a value-class-boundary bug (`SeriesId`/`EpisodeId` raw-`String` conversion, see Table)
would not trip the presence/`isCompleted` checks and would otherwise surface only later, in Phase 3's
series rollup or Phase 5's dedup, far from where it was introduced.

**3 — Flip reads.** `getRecentItems`, `getSeriesWatchProgress`, `getWatchHistory` and
`getPlaybackPositions` (with both `Suspend` variants) move to `WatchStateDao`; the synchronous
`getPlaybackPosition` and its ViewModel passthrough are deleted as dead. The Recent path takes
the `LIMIT` in place of `take(watchHistorySize)`; the content-list path keeps the exact same
`Map<String, WatchedItem>` contract, so `CategoryViewModel.refreshPerItemData` and both
`EpisodeSelectionScreen`s change only in that the call now suspends. Eviction stops here — this is
the phase that satisfies the requirement.

**4 — Retire the blob.** Stop writing `watch_history_v3`. One-time purge of `watch_history_v3` and
`watch_history_v2`, guarded flag, same shape as the EPG purge.

**The purge must check *that provider's* `watch_state_migrated_v1` before deleting its blob — not
a single global flag.** Phases 2, 3 and 4 are separate releases, so a provider can exist through
all three without ever being opened: backfill (Phase 2) only runs on first `MediaRepository` use,
so a provider nobody has selected since the Phase 2 release still has an un-migrated blob sitting
in its prefs when the Phase 4 release lands. A purge that fires unconditionally per install, rather
than per provider gated on that provider's own flag, deletes that history before it was ever
copied anywhere — silent and unrecoverable, on a provider the user simply hasn't gotten around to
opening. Concretely: the purge must run the same per-provider loop the backfill does, and where
the flag is unset it **runs the backfill first**, not skip-and-purge — skipping would just leave
the blob (and the un-migrated history) forever, since nothing revisits it after Phase 4 ships and
the blob-writing path is gone.

**`SettingsExportManager` must be updated in this phase** — it reads and writes
`media_cache_$providerId` by key name (`:229`, `:260`, `:558`, `:604`), so without this, backup and
restore silently stop carrying watch state.

Two details in that update, both of which produce a silently empty restore rather than an error:

- **Remap `providerId` on import.** Restore matches providers by identity and writes under
  `matchingProvider.id` (`:558`, `:604`), which is not necessarily the id the backup was taken
  under — `ProviderEntity.id` is `autoGenerate = true`. Exported `watch_state` rows must be
  nested inside the provider's JSON block in the backup rather than top-level unkeyed entries, so
  import naturally rewrites `watch_state.providerId` to the matched provider's id before inserting.
- **Restoring an old backup must still import its history.** A backup taken before Phase 4 carries
  watch state inside `media_cache_$providerId`, but the restored device may already have
  `watch_state_migrated_v1` set, in which case the lazy backfill will never look at it again.
  Restore has to run the blob-to-table import directly for any `watch_history_v3` it writes, rather
  than relying on the migration flag.
- **The export side needs its own format version, not just importer logic for the old shape.** A
  backup taken *after* Phase 4 has no `watch_history_v3` blob left to nest — `watch_state` rows must
  be serialized as their own structure under the provider's JSON block. That makes two backup
  shapes: pre-Phase-4 (blob inside `media_cache_$providerId`) and post-Phase-4 (table rows of their
  own). `SettingsExportManager` needs an explicit schema/version field on the export so restore can
  tell which shape it is reading, rather than inferring it from which keys happen to be present.

**5 — TMDB dedup.** The feature that motivated the work; see below.

**6 — Manual mark watched / unwatched from the UI.** Depends only on Phase 3, so it can land before
or after Phase 5; see below.

Phases 1–4 are worth landing on their own. They fix eviction, the silent-wipe path, and the
hand-rolled joins regardless of whether 5 and 6 ever ship.

---

## Phase 5 — dedup across catalogue variants

**Read fan-out, not write fan-out.** Store one row for what was actually watched and resolve
equivalence at read time. Writing five rows per play would multiply storage against the catalogue
and require a catalogue scan on every save.

**TMDB ids come from the catalogue, not from `watch_state`.** They are only ever populated on
`XtreamStreamEntity.tmdbId` / `XtreamEpisodeEntity.tmdbId` during sync; no watch-state writer has one
to record (see "No `tmdbId` column" under Table). So dedup joins watch state back to the catalogue
to reach them. That also means dedup is Xtream-only by construction — SMB, Local and Remote M3U have
no catalogue and no TMDB ids, and degrade to no dedup rather than to an empty result.

**One extra query, not a change to the existing read.** It returns the item ids that are completed
*by a sibling* — the caller unions them into the `watched` set it already builds in
`refreshPerItemData`:

```sql
SELECT CAST(s.streamId AS TEXT) AS itemId
FROM xtream_streams s
JOIN (
    SELECT c.tmdbId AS tmdbId
    FROM watch_state w
    JOIN xtream_streams c
      ON c.providerId = w.providerId
     AND c.type       = :streamType
     AND CAST(c.streamId AS TEXT) = w.itemId
    WHERE w.providerId  = :providerId
      AND w.contentType = :contentType
      AND w.isCompleted = 1
      AND c.tmdbId IS NOT NULL
    GROUP BY c.tmdbId
) done ON s.tmdbId = done.tmdbId
WHERE s.providerId = :providerId AND s.type = :streamType AND s.excluded = 0
```

The `c.type = :streamType` predicate is load-bearing: `xtream_streams` is keyed
`(streamId, providerId, type)`, where `type` is the catalogue string (`"movie"`, `"live"`, etc.)
while `w.contentType` uses domain constants (`ContentType.MOVIES = "movies"`). The query maps
`:contentType` to `w.contentType` and `:streamType` to `s.type` so a VOD id and a live id never
collide, and without it a watched movie could mark an unrelated channel. `GROUP BY c.tmdbId`
collapses the sibling set before the outer join, which is what stops one stream matching several
watched variants and multiplying rows. `index_xtream_streams_providerId_tmdbId` (added in
`MIGRATION_14_15`) serves both ends.

**The episode form is not the same statement, and cannot be.** This was the original draft here,
and it shipped once before an on-device check against a real catalogue (`549` Law & Order, `124364`
From, provider 13, 2026-08-27) showed why it never fires:

- **Episode-level `tmdb_id` is essentially never usable.** Every one of Law & Order's 543 + 95
  episodes (two catalogue variants) has `tmdbId IS NULL`. From's episodes are 39/40 NULL in one
  variant, with the single non-NULL value equal to the *series'* own `tmdb_id` (`124364`) leaked
  onto exactly one episode — the exact failure mode the guard below exists for, occurring naturally
  in the wild. Panels that bother to tag episodes at all mostly don't tag them per-episode.
- **The real duplication is not rows sharing one `seriesId` — it is separate `seriesId`s
  entirely.** Unlike movies, where five language variants are five rows in the same flat
  `xtream_streams` table, five language variants of a *show* are five separate `xtream_series` rows
  (confirmed on-device: 25 distinct `seriesId` under provider 13 alone share `xtream_series.tmdbId
  = 124364` for From), each carrying its *own* complete, separately-numbered episode list. A query
  joined and scoped to one `seriesId` on both sides — matching the movies form's shape — can only
  ever compare a series' episodes against themselves. It was structurally incapable of finding a
  sibling, guard or no guard.

What does correlate, confirmed on-device across four of From's language variants:

```
seriesId=6548  (EN)  id=343994   season=1 episodeNum=1
seriesId=17878 (FR)  id=749050   season=1 episodeNum=1
seriesId=34211 (HU)  id=1430071  season=1 episodeNum=1
seriesId=49691 (MAX) id=2129208  season=1 episodeNum=1
```

Four different episode rows, four different `seriesId`s, same real episode — identified by
`(season, episodeNum)` under a `seriesId` whose *own* `xtream_series.tmdbId` matches the currently
displayed series'. So episode dedup joins two levels, not one: first `xtream_series` to find sibling
series sharing this series' `tmdbId`, then `xtream_episodes` to find each sibling's matching
`(season, episodeNum)` row — never touching `XtreamEpisodeEntity.tmdbId` at all.

```sql
SELECT e2.id AS itemId
FROM xtream_episodes e2
WHERE e2.providerId = :providerId AND e2.seriesId = :seriesId
  AND EXISTS (
    SELECT 1
    FROM xtream_episodes sib
    JOIN watch_state w ON w.providerId = sib.providerId AND w.itemId = sib.id
    WHERE sib.providerId = :providerId
      AND sib.season = e2.season AND sib.episodeNum = e2.episodeNum
      AND sib.seriesId IN (
          SELECT s2.seriesId FROM xtream_series s2
          WHERE s2.providerId = :providerId AND s2.tmdbId IS NOT NULL
            AND s2.tmdbId = (
                SELECT s1.tmdbId FROM xtream_series s1
                WHERE s1.providerId = :providerId AND s1.seriesId = :seriesId
            )
      )
      AND w.contentType = 'TV_SHOWS' AND w.isCompleted = 1
  )
```

The sibling-series subquery naturally includes `:seriesId` itself (a series' own `tmdbId` always
matches itself), so a directly-completed episode satisfies the `EXISTS` via `sib = e2` without
needing a separate self-check — the caller's existing `if (watched.isCompleted) add(id)` becomes
redundant-but-harmless against this result, not incorrect. A series with no `tmdbId` of its own
degrades to no dedup (`s2.tmdbId = NULL` matches nothing), same fail-safe shape as everywhere else
in this design.

`clearGroupCompletion`'s episode form (Phase 6) mirrors this: given the target episode's `(season,
episodeNum, seriesId)`, clear completion on every episode at that `(season, episodeNum)` across
every sibling series sharing that series' `tmdbId` — the same two-level join, `UPDATE` instead of
`SELECT`.

**The write-time guard survives, demoted.** `XtreamEpisodeEntity.tmdbId` is no longer read by dedup
at all, so nulling a series-level value copied onto more than one episode no longer changes dedup's
behavior either way. Left in place anyway: it is still correct data hygiene for the column
independent of what currently consumes it, and removing it would only save a few lines while adding
risk if something else ever comes to depend on that column meaning what it claims to.

**Dedup `isCompleted` only. Never `playbackPosition`.** Completion is a boolean and transfers
safely. A millisecond offset does not: a different variant has different intros, ads and runtime,
so 40 minutes into the EN rip is not 40 minutes into the FR one. Resume stays per-variant. Keeping
dedup in a query that returns nothing but item ids enforces this structurally — there is no column
in the result a sibling's position could travel through.

**Ship movies first.** Movies are a flat namespace with no hierarchy and panels populate VOD
`tmdb_id` fairly reliably. Episodes needed the redesign above before they worked at all.

**Expect partial coverage.** A series with no `tmdb_id` of its own (not an episode-level one —
series-level `tmdb_id` is what this now depends on) degrades to no dedup for that show. On the
provider checked, series-level `tmdb_id` was present for both Law & Order and From; coverage will
vary by panel.

---

## Phase 6 — mark watched / unwatched from the UI

Let the user set completion directly instead of only earning it by playback.

**Why it cannot ship before Phase 3.** Manually marking something watched writes a row that the
25-entry cap will evict like any other. The feature would break silently and in exactly the way this
plan exists to fix, so it needs the durable table underneath it first. It does not depend on
Phase 5.

### Repository surface

One call, replacing the half-measure that exists today:

```kotlin
suspend fun setWatched(itemId: String, contentType: String, watched: Boolean)
```

`clearPlaybackPosition` (`MediaRepository.kt:1105`) already sets `playbackPosition = 0` and
`isCompleted = false` — mark-unwatched, with no UI caller anywhere in `tv/` or `mobile/`. It gets
folded into `setWatched(..., false)` rather than kept alongside it.

Marking watched must **not** route through `savePlaybackPosition`. That method early-returns when
`position <= 0 && duration <= 0` (`:784`), deliberately, so an empty session cannot overwrite a real
resume point — and a manual mark looks exactly like an empty session. It needs its own upsert path.

A manual mark on an item with no existing row inserts one with `isCompleted = 1`, `positionMs = 0`,
`durationMs = 0`, and `lastPlayedAt` left null. That renders correctly with no extra work:
`resumeProgress()` returns null when `duration <= 0` (`:69`), so the card gets a check and no
progress bar, and the null `lastPlayedAt` keeps it out of the Recent row — the reason that column
exists (see Table above).

On an item that *does* have a row, `setWatched` names `isCompleted` and `updatedAt` only — it leaves
`positionMs`, `durationMs` and `lastPlayedAt` as they are, same discipline as the two playback
writers. Marking a half-watched movie watched hides its bar (`resumeProgress()` returns null once
`isCompleted`) without discarding the position, so unmarking it brings the bar back where it was.

### Three behaviours to get right

**Playback must not silently undo a manual mark.** `savePlaybackPosition` derives completion from
the position every time — `isCompleted = progressPercent > 95f` (`:791`) — so under the current rule,
marking a film watched and then opening it for two minutes writes `isCompleted = false` and the check
disappears with no user action that means "unwatch". From Phase 6 onward completion is **sticky**:
the progress upsert may raise `isCompleted` from false to true but never lowers it, and only
`setWatched(false)` clears it. Position keeps updating either way, so a rewatch still resumes.

The rule lands *with* Phase 6 rather than before it, deliberately — sticky completion needs the
manual escape hatch to exist, otherwise a title that crossed 95% by accident could never be
un-marked. In the upsert this is `isCompleted = MAX(watch_state.isCompleted, excluded.isCompleted)`.

**Unwatched must clear the whole TMDB group, not just one row — once Phase 5 exists.** Phase 5 adds
a sibling's completion to the displayed set. Clearing one variant while a sibling still holds
`isCompleted = 1` means the check comes straight back and the action appears to do nothing. So
`setWatched(false)` clears completion on every row whose catalogue entry shares that `tmdbId`, while
`setWatched(true)` writes the single row and lets the sibling query spread it:

```sql
UPDATE watch_state SET isCompleted = 0, updatedAt = :now
WHERE providerId = :providerId AND contentType = :contentType
  AND itemId IN (
      SELECT CAST(c.streamId AS TEXT) FROM xtream_streams c
      WHERE c.providerId = :providerId AND c.type = :type AND c.tmdbId = (
          SELECT tmdbId FROM xtream_streams
          WHERE providerId = :providerId AND type = :type AND streamId = CAST(:itemId AS INTEGER)
      )
  )
```

with a `tmdbId IS NOT NULL` guard so a null-TMDB title clears only itself rather than every other
null-TMDB title. Asymmetric, and necessarily so. Without Phase 5 both directions touch one row and
the asymmetry does not arise — but writing it in from the start costs nothing and avoids a confusing
bug later.

**Manual marks stay out of the Recent row.** Handled by the schema: `lastPlayedAt` is set by
playback only, and the Recent query filters on `lastPlayedAt IS NOT NULL`. Marking a five-year-old
movie watched must not push it to the front of Recent.

### UI placement

Follow the affordances already in place rather than inventing new ones.

- **TV, content lists:** long-press already opens `FavoriteContextMenuDialog`
  (`tv/.../category/components/FavoriteMenuDialog.kt:24`), wired through `StreamList.kt:335` and
  `TwoColumnLayout.kt:114`. Add a watched/unwatched entry beside the favourite toggle. Search has the
  same long-press hook (`SearchScreen.kt:702`).
- **TV, movie details:** next to `onToggleFavorite` (`tv/.../movie/MovieDetailsScreen.kt:161`).
- **Mobile, movie details:** a second `CinemaIconButton` beside the favourite one
  (`mobile/.../movie/MovieDetailsScreen.kt:94`).
- **Episode lists:** per-row action in both `EpisodeSelectionScreen`s, which already render the
  completion state they would be toggling.

Label follows current state — "Mark as watched" / "Mark as unwatched" — rather than a checkbox, so
the outcome is unambiguous on a 10-foot UI.

**Refreshing after a toggle costs nothing extra**, because watch state is looked up separately from
the catalogue rather than joined into it. `toggleFavoriteStream` already ends in
`viewModelScope.launch { refreshPerItemData() }` (`CategoryViewModel.kt:527`); a watched toggle ends
the same way, re-reads `watch_state`, and republishes `_watchedIds`. No catalogue re-query, no list
invalidation. This is the second reason the join was rejected — see "Why not `LEFT JOIN` …" above.

**Optional, not scoped here:** mark a whole season or series watched. It falls out of the same call
applied over an episode set, and `getSeriesWatchProgress` picks it up with no further change, but it
needs its own confirmation affordance and is easy to trigger by accident on a D-pad.

---

## Performance

The blob is not fast because it is a blob. It is fast because it is cached: decoded once into
`cachedWatchHistory` with a lazily built `watchHistoryLookup` map keyed `(itemId, contentType)`
(`MediaRepository.kt:818-821`). Steady-state reads are HashMap hits.

The read side is close to a wash, and that is the honest claim. `getPlaybackPositions` is called
once per `refreshPerItemData`, not once per item, so today's list load already costs one blob decode
(cached) plus one map build; afterwards it costs one indexed query over the provider's watched rows
plus the same map build. What disappears is the decode of a JSON array on cold start and the
permanent residency of that array; what appears is a query per list load. Neither is the bottleneck,
and neither was ever the reason for this plan — the read side is being changed because the blob
cannot store the rows, not because it reads them slowly.

What is worth keeping is on the **write** side: the existing 500 ms debounce
(`MediaRepository.kt:536`) still earns its place, coalescing a progress tick stream into one row
write.

| | blob today (25 rows) | blob at "forever" | Room table |
|---|---|---|---|
| List read | decode (cached) + HashMap merge | decode (cached) + HashMap merge | indexed query over watched rows + HashMap merge |
| Cold start | decode whole list | decode **whole list** | no read until something asks |
| Write one position | re-serialize whole list, rewrite whole XML file | re-serialize **whole list**, rewrite whole XML file | one row upsert |
| RAM | whole list resident | **whole list resident** | one content type's rows, transient |

Writes are where it matters, and the middle column is the point. Every `savePlaybackPosition`
re-serializes the entire list and `commit()`s the whole prefs file. At 25 rows that is roughly
7 KB and genuinely fine — 500 ms debounced, off the main thread. At the retention this plan
requires it is not: a 10,000-row JSON array rewritten on every progress tick, resident in RAM for
the life of every process that touches the file.

So the honest framing is the inverse of the question. Moving to Room does not cost performance —
**staying on the blob is what makes the requirement unaffordable.** Raising `watchHistorySize` to
get durability would be the bad outcome; Room is flat in the row count. This codebase has already
run the experiment: `MIGRATION_12_13` moved the EPG payload cache out of SharedPreferences for
exactly this reason, where a 53k-channel provider reached an 84 MB prefs file parsed into RAM by
every process that opened it (`XtreamEpgManager.kt:95`).

One genuine hazard, and it is not SQLite: a Room query on the main thread. See Threading below.

## Threading

Every watch-state entry point that is synchronous today becomes a disk read or write, so each one
needs a decision rather than a spot check. The full list:

| today | line | disposition |
|---|---|---|
| `getPlaybackPositions` | `:827` | becomes suspend; sole caller `CategoryViewModel.kt:471` is already inside a suspend `refreshPerItemData` |
| `getPlaybackPositionsSuspend` | `:1084` | already suspend; both `EpisodeSelectionScreen`s already call it |
| `getPlaybackPosition` | `:814` | **delete.** Its only caller is the `CategoryViewModel.kt:431-434` passthrough, which has no callers of its own anywhere in `tv/` or `mobile/`. Delete both. |
| `getPlaybackPositionSuspend` | `:1065` | already suspend; used by `MovieDetailsViewModel:118`, `StreamLoaderViewModel:182` and both episode screens |
| `getWatchHistory` | `:540` | becomes suspend; one production caller |
| `getRecentItems` | `:910` | folds into `getRecentItemsSuspend` (`:1010`), its only production caller |
| `getSeriesWatchProgress` | `:859` | already suspend |
| `savePlaybackPosition` | `:766` | stays non-suspend — see below |
| `saveLastPlayedItem` | `:395` | stays non-suspend — see below |

**The two writers stay fire-and-forget**, because their callers
(`StreamLoaderViewModel.kt:235,462,477,517,532`) treat them as such and there is nothing to await.
The Room upsert runs on `writeScope` — `CoroutineScope(SupervisorJob() +
Dispatchers.IO.limitedParallelism(1))` at `MediaRepository.kt:157` — which is already the
repository's serialized write lane. `limitedParallelism(1)` is what keeps a start write and a
progress write from racing on the same row, so the two upserts land in call order without any
locking of their own.

**Write batching / debounce:** The 500 ms debounce replaces the `HandlerThread("WatchHistoryWriter")`
with a coroutine pipeline on `writeScope` (e.g. via a conflated `Channel` or debounced `MutableSharedFlow`)
to avoid writing to SQLite every 500ms during fast progress ticks while still persisting reliably on stop.

**Code style constraints:** All modified and newly introduced functions in `MediaRepository` and DAOs
must adhere to project rules: single return statement per function, with early returns eliminated via
structured `if/when` control flow.

Only `getRecentItemsSuspend` runs Room and Compose-facing work in the same call, and it is already
suspend and already fetches icons from `db.streamDao()` (`:1050-1052`), so it needs no change beyond
its new data source.

## Known adjacent problems, deliberately out of scope

- ~~**Favorites have the identical defect.**~~ **Fixed 2026-08-28** — same prefs file, same
  whole-blob rewrite, same `catch { emptyList() }` wipe path, capped at 100. Ported to a
  `favorite_state` table on the same pattern; see
  [favorites-durable-storage-plan.md](favorites-durable-storage-plan.md).
- ~~**`XtreamUserDataManager` keeps its own parallel blob** in `xtream_cache_$providerId`.~~
  **Deleted 2026-08-28.** Its unversioned `watch_history` of `List<WatchedStream>` was keyed on
  **int** stream ids against `MediaRepository`'s String ids, and held a stale copy of the same flags
  once Phase 4 landed. The whole class went, along with `XtreamRepository`'s fifteen passthroughs,
  the `WatchedStream`/`FavoriteStream` types, their two `XtreamMapper` extensions and the
  `XtreamCacheKeys` entries only it used — 385 lines plus a benchmark test that existed solely to
  exercise it.

  It was not quite dead. Two live buttons called it: **Clear Favorites** and **Clear Progress** on
  the Add/Edit Provider screens, in both `:tv` and `:mobile`. All three of their defects were
  pre-existing and mutually compounding — they cleared the dead `xtream_cache` blobs instead of the
  live `favorites_v2` / `watch_state`; the screens construct `XtreamRepository(...)` with the
  default `providerId = 0L`, so they never addressed the provider being edited; and
  `MediaRepository.clearWatchHistory()` still removed only the retired blob keys, so after Phase 4
  it cleared nothing a user could see. Both now route through
  `AppContainer.getInstance(context).getMediaRepository(editId)`, which keeps the cached instance's
  in-memory views consistent, and `clearWatchHistory()` is suspending, deletes the `watch_state`
  rows, and empties the published Recent lists.

- ~~**The `last_*` navigation keys are duplicated across both prefs files** and disagree on type
  (String item id vs int stream id).~~ **Resolved 2026-08-28** as a consequence of the above — the
  `xtream_cache_$providerId` half lived in `XtreamUserDataManager` and went with it. Only
  `MediaRepository`'s String-keyed set remains.
- ~~**`getSeriesWatchProgress()` (the % on a TV Shows row in the grid) does not apply Phase 5's TMDB
  dedup.**~~ **Fixed 2026-08-28.** It counted only rows actually `isCompleted = 1` in `watch_state`
  under *that* `seriesId`, while the sibling union lived in the read path for the per-item watched
  *check* (`getPlaybackPositions` and the episode-list screens). So marking episode A watched left
  its language-variant sibling B correctly checked in the episode list while the series row above
  it read 0%. Found during the Phase 6 adversarial pass (2026-08-27) and initially left as-is.

  The denominator question raised here — whether `getEpisodeCountsBySeries()` also treats language
  variants as separate episodes — was settled by measurement rather than reasoning: pulled the
  catalogue off a device (47,552 series, 303 cached episodes) and found **zero** duplicate
  `(seriesId, season, episodeNum)` rows, so `COUNT(*)` already equals the distinct count and the
  denominator needed no change. Each variant is its own `xtream_series` row with its own episode
  list, so per-series counts never inflate.

  Only the numerator changed: `XtreamEpisodeDao.getSiblingCompletedCountsBySeries()`, the aggregate
  form of `getSiblingCompletedEpisodeIds()`. Shaped as a CTE driven from `watch_state` outwards
  rather than the per-series `EXISTS` form, which re-derived each series' `tmdbId` per candidate
  episode — 223ms versus 0.1ms on that catalogue, fully index-driven. `MediaRepository` takes the
  **max** of the direct and deduplicated counts, never the sum: they overlap rather than add, the
  dedup query cannot see series with a NULL `tmdbId`, and the direct count cannot see siblings, so
  each covers what the other misses. Verified on the real catalogue: completing S1E1 under series
  `6548` credits all six cached variants of `tmdbId 124364`, matching what the episode list already
  showed.

## Interaction with the sync plan

`docs/plans/xtream-multi-device-sync-plan.md` specifies a server-side
`watch_history(profile_id, item_id, content_type, position_ms, duration_ms, is_completed, ...)`
table with `PRIMARY KEY (profile_id, item_id, content_type)`. The local schema above is that shape
with `providerId` in place of `profile_id`, which makes the eventual outbox a column mapping rather
than a reshape.

Cross-panel matching — the same film watched on two different providers — would need a TMDB id on
the synced row, which `watch_state` deliberately does not store (see Table). Resolving it through
the catalogue at outbox time is the same join Phase 5 already uses, so nothing here forecloses it;
it just is not a column carried for a use case that does not exist yet.
