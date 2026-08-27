# Durable Watch State Plan

**Requirement:** playback position and completed state must persist indefinitely. Today both are
fields on a row inside a JSON list truncated to 25 entries on every write, so both are silently
lost to eviction.

**Secondary goal (Phase 5):** when a provider lists the same work several times — five language
variants of one movie or one episode — completing any one of them should mark all of them
complete.

**Scope:** Xtream and the other local-blob providers (SMB, Local, Remote M3U). Live TV is
untouched: `savePlaybackPosition` already returns early on `ContentType.LIVE_TV`
(`MediaRepository.kt:783`). Jellyfin is untouched: it short-circuits on `usesServerUserData` and
owns this state server-side. Per-provider isolation is preserved throughout — every new row is
keyed by `providerId`, exactly like the seven existing tables.

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
   `history.take(providerSettings.watchHistorySize)` (`MediaRepository.kt:530`). Default 25, max
   100 (`ProviderSettings.kt:17`). Finish S01E01–E25 and by E26 the E01 row is gone: the episode
   renders unwatched in both `EpisodeSelectionScreen`s and drops out of the
   `getSeriesWatchProgress` rollup.
2. **A bad decode wipes everything.** `getWatchHistoryLocked` (`MediaRepository.kt:546`) wraps both
   decodes in `catch { emptyList() }`. One malformed blob returns empty and the next write persists
   that empty list. No error surfaces.
3. **No query surface.** `getSeriesWatchProgress` (`:857`) hand-rolls a GROUP BY over a linear
   scan; `getPlaybackPositions` (`:826`) carries a comment explaining it iterates history rather
   than `itemIds` to keep the manual join at `O(HistorySize)`. Both are one SQL statement.
4. **Position and completion share one fate.** They have different durability needs — a stale
   resume point aging out is arguably correct; a watched flag reverting is not.

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
        Index(value = ["providerId", "contentType", "updatedAt"]),
        Index(value = ["providerId", "contentType", "tmdbId"]),
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
    val tmdbId: String? = null,
    val seriesId: String? = null,
    val episodeId: String? = null,
    val seriesName: String? = null,
    val episodeExtension: String? = null,
    val audioTrackIndex: Int? = null,
    val subtitleTrackIndex: Int? = null,
)
```

Column set is `WatchedItem` (`MediaRepository.kt:45`) plus `providerId`, `tmdbId`, `updatedAt`.
`WatchedItem.timestamp` becomes `updatedAt`.

**Naming.** The table is deliberately *not* `xtream_`-prefixed. `MediaRepository` backs SMB, Local
and Remote M3U as well as Xtream, so those rows are not Xtream rows. This breaks with the
neighbouring table names; the alternative is a name that lies about half its contents. `XtreamDatabase`
itself is by now a misnomer — renaming it is out of scope here.

**Value classes.** `SeriesId` and `EpisodeId` are `@JvmInline value class` over `String`
(`MediaIds.kt:17,22`). Store raw `String?` columns and convert at the mapper boundary rather than
registering a `TypeConverter` — it keeps the DAO signatures plain and matches how `XtreamEpisodeEntity`
already stores ids.

### Retention

Unbounded, which is the point. A row is roughly 200 bytes; 10,000 watched items is about 2 MB. No
sweeper. This is deliberately unlike the EPG cache, which needed bounding (`56350d0d`) because its
payloads are large — watch-state rows are tiny and each one is something the user actually watched.

### The cap becomes a query — on one read path only

Storage is unbounded; **display is still capped**. `providerSettings.watchHistorySize` keeps its
1–100 range and stops being a retention policy, becoming a display limit. Its meaning changes from
"how much history is kept" to "how many Recent cards are shown", so the settings-screen copy needs
updating in the same change.

The critical distinction is that the two read paths take the cap differently. Applying it to both
would reintroduce the exact bug this plan exists to fix.

**Tier 1 — the Recent row. Capped.** Hundreds of rows on disk, `watchHistorySize` cards on screen.
`getWatchHistory()` has exactly one caller, `getRecentItems` at `MediaRepository.kt:912`, and no UI
callers at all, so this is a contained change.

**Tier 2 — watched checks and resume bars on content lists. Not history at all.** Position and
completion are attributes of a stream, defined for *every* stream: 0 and false until something is
watched. They are not entries in a recency list and the cap is meaningless for them. Restricting
them by recency puts back the disappearing check mark from `1b3105e4` — episode 1 of a long series
would render unwatched the moment 25 newer items exist.

So they should not be looked up separately at all. **`LEFT JOIN` them into the query that already
loads the list**, with `COALESCE` supplying the defaults:

```sql
SELECT s.*,
       COALESCE(w.positionMs, 0)  AS positionMs,
       COALESCE(w.durationMs, 0)  AS durationMs,
       COALESCE(w.isCompleted, 0) AS isCompleted
FROM xtream_streams s
LEFT JOIN watch_state w
  ON w.providerId  = s.providerId
 AND w.contentType = :contentType
 AND w.itemId      = CAST(s.streamId AS TEXT)
WHERE s.providerId = :providerId AND s.type = :type
  AND s.categoryId = :categoryId AND s.excluded = 0
ORDER BY s.num ASC
```

Room maps this to a POJO of `XtreamStreamEntity` plus the three watch columns. The equivalent join
goes on the episode query (`XtreamEpisodeDao.kt:11`), joining on `xtream_episodes.id`.

This deletes work rather than adding it: `CategoryViewModel.kt:471-480` loses the
`getPlaybackPositions` call, the `progressMap`/`watched` HashMap construction, and the merge loop.
No lookup map, no cache layer, one round trip. The `index_watch_state_providerId_contentType_tmdbId`
sibling index on `(providerId, contentType, itemId)` — the primary key — serves the join.

`getPlaybackPositions` survives only for callers that genuinely have a loose id list rather than a
catalogue query behind them, and for the non-Xtream providers (SMB, Local, Remote M3U), which have
no catalogue tables to join against and query `watch_state` directly.

**Why not columns on `xtream_streams` / `xtream_episodes` instead.** It would give the same
single-query read, but it puts permanent data in ephemeral rows. `insertAll` is
`OnConflictStrategy.REPLACE` (`XtreamStreamDao.kt:59`), and REPLACE in SQLite is DELETE + INSERT —
every catalogue sync touching a row would zero the watch columns. `deleteAll(providerId, type)`
(`:62`), `XtreamEpisodeDao.deleteAll` (`:20`) and `deleteBySeriesId` (`:23`) do it wholesale. A
title the provider drops would take its watch state with it and come back unwatched. And the
non-Xtream providers have no rows in those tables, so a second mechanism would be needed anyway.
The join keeps the read model while leaving watch state outside the catalogue's lifecycle.

#### Two ordering details the Recent query must preserve

A naive `ORDER BY updatedAt DESC LIMIT :limit` changes existing behaviour twice over.

**Series collapse before the limit, not after.** `getRecentItems` does
`distinctBy { seriesId?.raw ?: itemId }` for TV Shows so a show appears once rather than once per
episode (`:918`). Limiting first and collapsing second would yield fewer cards than asked for —
25 episodes of one show collapse to a single card. Group in SQL and take each series' most recent
row:

```sql
SELECT * FROM watch_state ws
WHERE providerId = :providerId AND contentType = 'TV_SHOWS'
  AND updatedAt = (
    SELECT MAX(updatedAt) FROM watch_state
    WHERE providerId = ws.providerId AND contentType = ws.contentType
      AND COALESCE(seriesId, itemId) = COALESCE(ws.seriesId, ws.itemId)
  )
ORDER BY updatedAt DESC LIMIT :limit
```

Movies and the rest keep the plain query. In-progress-first partitioning stays in memory, as today
(`:924`) — it reorders the fetched page rather than selecting it.

**The cap stops being shared across content types.** Today the 25 rows are global and then filtered
per type, which the code documents as a known wart at `:908`: "an in-progress movie pushed out by
heavy channel surfing disappears from here too." A per-`contentType` `LIMIT` gives each type its own
allowance and that wart goes away. This is an intentional improvement, not an accident — but it is a
visible behaviour change and the doc comment at `:905-909` should be rewritten rather than left
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_state_providerId_contentType_updatedAt` ...")
        // + the two remaining indices
    }
}
```

**Data, app-side.** Lazily, per provider, on first `MediaRepository` use: decode
`watch_history_v3`, insert the rows, set a `watch_state_migrated_v1` boolean in that provider's
prefs. Guarded exactly like `purgeLegacyPrefsCache` (`XtreamEpgManager.kt:98`). Backfill only —
the blob is left intact until Phase 4 so a rollback loses nothing.

---

## Phases

**1 — Schema.** `WatchStateEntity`, `WatchStateDao`, `MIGRATION_14_15`, bump to version 15. Nothing
reads or writes the table yet. Ships dark.

**2 — Dual write + backfill.** `savePlaybackPosition` writes both blob and table. Backfill runs on
first use per provider. Reads still come from the blob. This phase is reversible and is where
parity gets verified — for a provider with history, table rows and blob rows should agree on
`(itemId, contentType, positionMs, isCompleted)`.

**3 — Flip reads.** `getPlaybackPosition(s)`, `getRecentItems`, `getSeriesWatchProgress`,
`getWatchHistory` move to the DAO. The `LIMIT` replaces `take(watchHistorySize)`. Eviction stops
here — this is the phase that satisfies the requirement.

**4 — Retire the blob.** Stop writing `watch_history_v3`. One-time purge of `watch_history_v3` and
`watch_history_v2`, guarded flag, same shape as the EPG purge. **`SettingsExportManager` must be
updated in this phase** — it reads and writes `media_cache_$providerId` by key name (`:229`,
`:260`, `:558`, `:604`), so without this, backup and restore silently stop carrying watch state.

**5 — TMDB dedup.** The feature that motivated the work; see below.

Phases 1–4 are worth landing on their own. They fix eviction, the silent-wipe path, and the
hand-rolled joins regardless of whether Phase 5 ever ships.

---

## Phase 5 — dedup across catalogue variants

**Read fan-out, not write fan-out.** Store one row for what was actually watched and resolve
equivalence at read time. Writing five rows per play would multiply storage against the catalogue
and require a catalogue scan on every save.

The seam is narrow. Every completion read already funnels through one call —
`CategoryViewModel.kt:471`, `EpisodeSelectionScreen.kt:388` (TV) and `:312` (mobile) — so
`getPlaybackPositions` is the single place to add TMDB resolution. The id → `tmdbId` lookup is
already local: `XtreamStreamEntity.tmdbId:45`, `XtreamEpisodeEntity.tmdbId:31`. No network.

**Dedup `isCompleted` only. Never `playbackPosition`.** Completion is a boolean and transfers
safely. A millisecond offset does not: a different variant has different intros, ads and runtime,
so 40 minutes into the EN rip is not 40 minutes into the FR one. Resume stays per-variant.

**Mandatory guard on episodes.** Panels frequently copy the *series* `tmdb_id` into every episode's
info block. Unguarded, completing S01E01 would mark the entire series complete — silent and far
worse than the bug being fixed. Rule: if the same `tmdbId` appears on more than one episode of a
series, it is a series-level value and must be refused as an episode key. Detectable locally
against `xtream_episodes`, no network.

**Ship movies first.** Movies are a flat namespace with no hierarchy and panels populate VOD
`tmdb_id` fairly reliably. Episodes carry the collapse risk above.

**Expect partial coverage.** Panels derive `tmdb_id` per listing from their own scraper; the EN
listing often matches while the FR or 4K listing comes back null. Dedup will cover the subset the
panel matched and silently miss the rest. Worth knowing before judging the result broken.

---

## Performance

The blob is not fast because it is a blob. It is fast because it is cached: decoded once into
`cachedWatchHistory` with a lazily built `watchHistoryLookup` map keyed `(itemId, contentType)`
(`MediaRepository.kt:818`). Steady-state reads are HashMap hits.

With the join above, most of that cache stops being needed rather than being ported. The list query
already reads from disk; carrying three more columns on rows it is already returning costs an
indexed lookup per row and removes a second round trip plus a HashMap build. There is no separate
watch-state working set to hold in RAM, because nothing looks watch state up on its own.

What is worth keeping is on the **write** side: the existing 500 ms debounce
(`MediaRepository.kt:536`) still earns its place, coalescing a progress tick stream into one row
write.

| | blob today (25 rows) | blob at "forever" | Room + cache |
|---|---|---|---|
| Read, warm | HashMap hit | HashMap hit | HashMap hit |
| Read, cold | decode whole list | decode whole list | indexed query |
| Write one position | re-serialize whole list, rewrite whole XML file | re-serialize **whole list**, rewrite whole XML file | one row upsert |
| RAM | whole list resident | whole list resident | bounded working set |

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
every process that opened it (`XtreamEpgManager.kt:90`).

One genuine hazard, and it is not SQLite: a Room query on the main thread. See Threading below.

## Threading

Checked; smaller than it looks. `CategoryViewModel.kt:471` already runs inside
`withContext(Dispatchers.Default)`, and both `EpisodeSelectionScreen`s already call the `Suspend`
variants. Only `CategoryViewModel.kt:434` — a synchronous passthrough to
`repository.getPlaybackPosition` — needs converting to suspend.

## Known adjacent problems, deliberately out of scope

- **Favorites have the identical defect.** Same prefs file, same whole-blob rewrite, same
  `catch { emptyList() }` wipe path, capped at 100. Same fix applies; not required by this
  requirement.
- **`XtreamUserDataManager` keeps its own parallel blob** in `xtream_cache_$providerId` — its own
  unversioned `watch_history` of `List<WatchedStream>` keyed on **int** stream ids, against
  `MediaRepository`'s String ids. It will hold a stale copy of the same flags after this lands. The
  sync plan's audit already concluded hooks attach to `MediaRepository`; retiring the duplicate is
  its own change.
- **The `last_*` navigation keys are duplicated across both prefs files** and disagree on type
  (String item id vs int stream id). Unrelated to retention.

## Interaction with the sync plan

`plans/xtream-multi-device-sync-plan.md` specifies a server-side
`watch_history(profile_id, item_id, content_type, position_ms, duration_ms, is_completed, ...)`
table with `PRIMARY KEY (profile_id, item_id, content_type)`. The local schema above is that shape
with `providerId` in place of `profile_id`, which makes the eventual outbox a column mapping rather
than a reshape. `tmdbId` riding along as a column is also what would let rows from different panels
be joined later, if that is ever wanted — without it crossing providers locally, which this plan
does not do.
