# Catalog Sync Cache Churn Plan

**Status:** Phases 1-2 done (2026-10-02). Phases 3-4 not started.

Opening a show the app already has on disk should not re-download it. Today it does: a
finished 18-season show like Law & Order is fetched again in full from Xtream, plus several TMDB
calls, far more often than its content changes. The 24h episode-list cache and the 7-day TMDB
detail cache that were meant to prevent this are wiped by every catalog sync.

Context: `docs/plans/20260827_refresh-change-detection-plan.md` (the `contentHash` change
detection this builds on).

## Findings (2026-10-02, bears, read-only copies of the app databases)

Databases copied with `run-as … tar` from the phone (`b048cf47`), darcy (`.21`, last bears sync
13:53) and mdarcy (`.20`, last bears sync 17:13). Nothing installed or cleared. Both Shields hold
the same 47,513 series, so the two snapshots compare one provider 3h20m apart.

1. **Xtream `num` is a list position, not an identity.** Between the two syncs, `num` changed on
   38,888 of 47,513 series and on 49,285 of 180,873 VOD streams. Live streams: 0 of 54,605.
2. **`num` is in `contentHash`** (`XtreamSeriesEntity.computeHash`, `XtreamStreamEntity.computeHash`),
   so those rows look changed on every sync. mdarcy's last sync reported `lastSyncUpdated` 39,754.
3. **A "changed" row is rewritten with `@Insert(onConflict = REPLACE)`** (`XtreamSeriesDao.insertAll`,
   `XtreamStreamDao.insertAll`) from a freshly built entity, so every cache column resets to its
   default:
   - series: `episodesFetchedAt`, `detailFetchedAt`, `contentRating`, `posterPath`
   - VOD: `detailFetchedAt`, `contentRating`, `containerExtension`, `posterPath`
4. **Effect on devices.** mdarcy has stored episodes for 7 series (Law & Order: 543); all 7 have
   `episodesFetchedAt` and `detailFetchedAt` null. darcy: 0 stamps. Phone: 1 of 50. VOD on mdarcy:
   17 of 180,881 have `detailFetchedAt`.
5. **Bears' `last_modified` is a reliable "new episodes" signal.** 39 series changed it in the
   3h20m window, nearly all currently airing (Slow Horses, Ted Lasso, Boston Blue, Brothers…), most
   last bumped about a week earlier. `EN - Law & Order (1990)` last changed 2026-05-18. ~1,050
   series changed in the last 7 days; ~27,800 not in over a year.
6. **The jellyxtream bridge does not bump it.** `tools/jellyfin-xtream` sends the series'
   Jellyfin `DateCreated` as `last_modified`, which never changes when episodes are added.
7. **TMDB on every episode refetch** (`XtreamMediaProvider.getSeriesDetail`): `tv/{id}` is called
   unconditionally, and if any episode lacks a synopsis `fetchTmdbOverviews` fetches every season
   of the show.

Series are listed `ORDER BY name`, so their `num` is unused. Streams are listed `ORDER BY num`, so
a stream's `num` must keep tracking the provider's order.

## Phase 1 — Catalog sync stops wiping cache columns

- Drop `num` from both `computeHash` functions.
- Streams: when only `num` changed, update `num` alone (one `UPDATE … SET num` per changed row, in
  the existing batch transaction) instead of rewriting the row. Needs the stored `num` next to the
  stored hash (extend `getStreamHashes`).
- When a row really changed, keep its cache columns: read them for the changed ids before the
  batch write and copy them into the new entity. Exception: a changed series gets
  `episodesFetchedAt = null` on purpose. That is the "provider changed this show, refetch its
  episodes" trigger.
- No schema change, no migration. The first sync after upgrade sees every hash differ (new
  formula) and rewrites everything once, keeping cache columns per the rule above.

Tests (unit, fake DAO): `num` shift only → no row rewrite and `num` updated for streams; real
change → cache columns kept, series `episodesFetchedAt` cleared; unchanged → untouched.

Check: after two bears syncs on one device, `lastSyncUpdated` drops from ~40k to the hundreds and
`episodesFetchedAt` survives on an opened, unchanged series.

**Done (2026-10-02).** `computeHash` drops `num` for series and streams. Stream sync collects
position-only moves and applies them with `XtreamStreamDao.updateNums`; series `num` is left as
first stored (series are listed by name). Changed and new rows go through
`insertKeepingDetailCache` on both DAOs, which reads the detail columns of the existing rows
(chunked at 900 ids) and carries them over via `withDetailCache`; a changed series still gets
`episodesFetchedAt = null`. Unit tests: `CatalogDetailCacheCarryOverTest` (the carry-over rules;
the fake-DAO sync test was not written, there is no Room unit-test setup in `core:network`).

Verified on the TV emulator, bears, Kid profile:
- Sync 1 (one-time, new hash formula): 20 added, 282,998 updated, 23s.
- Opened `AR-SUBS - Law & Order (1990)` and `4K-AR - Inception (2010)`: both got their stamps.
- Sync 2, 5 minutes later: 0 added, **17 updated**, 11s. Both stamps, content rating, poster and
  container extension survived.
- Not exercised on device: no list positions moved in those 5 minutes (0 `num` changes), so the
  `updateNums` path and carry-over on a really changed row are covered by code review and the
  unit tests only.

## Phase 2 — Episode list refreshes on change, not on a clock

Replace the 24h `EPISODE_LIST_CACHE_TTL_MS` rule in `getSeriesDetail`. Use the stored episodes
unless:

- `episodesFetchedAt` is null (first open, or Phase 1 cleared it because the catalog changed),
- the user refreshed by hand (already expires the cache),
- the stored list is older than 30 days, a safety net for providers that never bump
  `last_modified` (see finding 6).

**Done (2026-10-02).** `EPISODE_LIST_CACHE_TTL_MS` is 30 days. The first two triggers needed no
new code: a null stamp already read as "not fresh", and manual refresh already expired it. One gap
fixed: `getSeriesDetail` returned the in-memory `seriesDetailCache` before looking at the stamp, and
that cache lives 7 days on the UI's provider instance, which the sync (its own instance) can't
clear. The in-memory copy is now used only while the stored stamp is fresh. Unit tests:
`XtreamSeriesEpisodeFreshnessTest` (21-day-old list served from disk, 31-day-old list refetched, a
series the sync marked changed is refetched even while held in memory). Not checked on a device:
it needs a provider to change a series between two syncs.

## Phase 3 — Stop repeat TMDB calls on refetch

- Skip `tmdb.getTvDetails` while `detailFetchedAt` is fresh (7 days, as the content rating
  already does) and the fields it fills (release date, year, plot) are already set.
- When some episodes lack a synopsis, ask TMDB only for the seasons containing them, and only if
  they haven't been asked within the TMDB cache window. Check first whether `plotFetchedAt` on
  `xtream_episodes` already records "asked, TMDB had nothing"; reuse it if so.

## Phase 4 (optional) — Make the bridge behave like bears

`tools/jellyfin-xtream`: send the newest episode's `DateCreated` as the series' `last_modified`, so
jellyxtream exercises the Phase 1/2 trigger on the emulators.

## Open questions

1. ~~Safety-net age for Phase 2~~ — decided 2026-10-02: 30 days.
2. Refetch the episode list once when an episode fails to play (stream id or container changed),
   then retry? Not needed if Phase 1 + 2 are enough; decide after Phase 2 lands.

## Roadmap

| Phase | What | Depends on |
|---|---|---|
| 1 | Hash without `num`, keep cache columns — **done 2026-10-02** | — |
| 2 | Episode list refresh on change — **done 2026-10-02** | 1 |
| 3 | TMDB repeat calls | — |
| 4 | Bridge `last_modified` | — (useful for testing 2) |
