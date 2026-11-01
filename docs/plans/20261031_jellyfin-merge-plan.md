# Jellyfin in the merged lists

**Status:** Planned 2026-10-31, not started. Open questions JM-Q1 and JM-Q2 wait for the user.

## Goal

A Jellyfin source's films and shows join the merged Movies and TV Shows lists, Search, Recent,
Continue Watching, Favourites and the Home rows, next to the Xtream sources, as an Xtream source's
titles do today. The Jellyfin server stays the master copy of watch history and favourites: what
the app changes goes to the server, and what another Jellyfin client changes comes back to the app.

Today (the multi-source plan, docs/plans/20261012_multi-source-search-history-plan.md, Q6 and O1)
a Jellyfin source has only its own **Browse <source>** entry, and is read live from the server.

## Decisions (user, 2026-10-31)

| # | Question | Decision |
|---|----------|----------|
| JM-D1 | Live queries or a local copy? | A **local copy** of the Jellyfin library, filled by a catalogue sync, so merged screens open at once, TV focus does not jump while results arrive, big A–Z lists sort on the device, and the lists stay when the server is down. |
| JM-D2 | What is merged? | **Films and shows only.** The app does not use Jellyfin's Live TV. |
| JM-D3 | Watch history and favourites? | The **Jellyfin server stays current**: every change in the app goes to the server at once (as today), and changes made on other Jellyfin clients come back to the app. The app keeps a local copy of the server's user state for the merged screens. |

## Open questions

| # | Question | Options |
|---|----------|---------|
| JM-Q1 | Where does the local copy live? | (a) **One shared catalogue** — the `xtream_*` tables take Jellyfin rows; their integer ids become text ids (a migration of the biggest tables). Merged screens and search read one place. Recommended. (b) **New Jellyfin tables** next to the Xtream ones; no migration of the Xtream tables, but every merged query and the search index read two places. |
| JM-Q2 | One Jellyfin login per household (the one Jellyfin login plan, docs/plans/20261024_jellyfin-one-login-plan.md) means every profile shares one Jellyfin user's history and favourites. Merging makes this more visible (a film the Kid watches shows in the Admin's Continue Watching). | (a) Keep it, and say so in Edit Source. (b) A Jellyfin user per profile, as a later plan. |

## What exists (survey 2026-10-31)

- **Shared layer, works for any source:** `MediaItem` with `providerId` / `sourceKey`,
  `MediaProvider.resolvePlayableStream`, the row-per-source routing and the source badges in
  `MergedLists` (core/ui/.../history/MergedLists.kt), the `watch_state` and `favorite_state`
  schemas (text item ids).
- **Merge gates:** each merge point is an `"XTREAM"` filter: `MergedLists` (:176, :207-211,
  :237-256), `CategoryViewModel.mergedCategories` (:711), `ProfileSections` (:53-89),
  `SearchViewModel` (:69), `WantedTitles` (:91), `TmdbTitleSearch` (:53), `RelatedInSources` (:17),
  `ProfileFavorites` (:42), `ExpiringSources` (:28), `ProviderSyncManager.performFullSync` (:128),
  `ProviderSyncRunner.syncProvider` (:126).
- **Catalogue storage is Xtream-shaped:** `xtream_streams` (key `streamId: Int, providerId, type`),
  `xtream_series` (`seriesId: Int`), `xtream_episodes` (`seriesId: Int`, a required
  `containerExtension`), `xtream_categories`; FTS tables for search; `contentHash` on Xtream fields.
  `MediaRepository` looks titles up by `toIntOrNull()` for posters, ratings, category backfill,
  Up Next, marking every copy watched and parental controls (:952, :1557, :1607, :1819-1831,
  :2072-2247, :2355-2420).
- **Jellyfin client** (core/network/.../jellyfin/): libraries from `/Users/{id}/Views`; items from
  `/Users/{id}/Items` with no paging and no time limit; seasons and episodes per show; playback
  through `POST /Items/{id}/PlaybackInfo`; progress reported to `/Sessions/Playing*`; favourites
  written to `/Users/{id}/FavoriteItems/{id}`; state read from each item's `UserData`.
  `JellyfinItem.toDomainItem` already maps to `MediaItem`. List calls do not ask for
  `ProviderIds`, so catalogue rows have no TMDB id.
- **Server-side state branches:** `MediaRepository` switches on `usesServerUserData` (:421):
  Continue Watching is empty for Jellyfin (:1730-1738), Recent and Favourites ask the server, Mark
  watched and Remove from Recent are off (:423-432, :2343). Live sync skips Jellyfin state
  (`LocalRecords` :225-227, `SyncApplier` :381).

## Design

### Catalogue sync

- A Jellyfin path in the catalogue sync (`ProviderSyncRunner`, the background job), next to
  Xtream's: libraries become categories (films and shows), items are read **in pages**
  (`StartIndex` / `Limit`) with `Fields` that include `ProviderIds` (TMDB id), the overview,
  images, run time, rating and `UserData`; shows bring their seasons and episodes.
- Later syncs read only what changed (`MinDateLastSaved`), and remove what the server no longer has.
- Same progress line and failure rules as an Xtream sync ("Updating 1 of 3…", Your Sources).
- Playback is unchanged: a local row keeps the Jellyfin item id; the server gives the stream at
  play time (direct play, direct stream or transcode).

### Watch state and favourites (JM-D3)

- **App → server:** unchanged calls (play, progress, stop, favourite), plus a write to the local
  copy at once, so the merged screens update without waiting for the server. A call that fails
  (server down) is kept and sent again later, oldest first, as live sync does.
- **Server → app:** the catalogue sync stores each item's `UserData` (position, watched,
  favourite, last played). Between syncs, a light check asks for items whose user state changed
  since the last check (`MinDateLastSavedForUser`), on app start and on return to Home.
- **Conflicts:** the newest change wins (last played or saved time).
- **Local copy:** Jellyfin rows in `watch_state` and `favorite_state`, marked as server-owned, so
  Continue Watching, Recent and Favourites read one place. The `usesServerUserData` branches in
  `MediaRepository` read the local copy instead of the server.
- **Live sync:** keeps skipping Jellyfin state. Each device talks to the Jellyfin server, which
  keeps the devices in step.
- Mark watched and Remove from Recent can come on for Jellyfin (Mark watched via
  `/Users/{id}/PlayedItems/{id}`; Remove from Recent via hiding the item from resume).

### Merging

- Open the `"XTREAM"` gates above to "has a local catalogue" (Xtream and Jellyfin).
- Search: Jellyfin titles enter the local search index; one ranking for all sources.
- Wishlist, TMDB charts, actor pages, More Like This and "on your sources" match Jellyfin titles by
  TMDB id, as Xtream titles.
- Parental controls and category hiding apply to Jellyfin libraries as to Xtream categories.
- The **Browse <source>** entry goes for Jellyfin (it stays for M3U).

## Phases

| Phase | What | Visible change |
|---|---|---|
| 1 | Storage (JM-Q1) and the Jellyfin catalogue sync, paged, with TMDB ids and `UserData` | None; Your Sources shows the Jellyfin sync |
| 2 | Open the gates: Jellyfin films and shows in merged Movies, TV Shows, Search, Wishlist, charts, actor pages; Browse entry gone | Jellyfin titles merged |
| 3 | Watch state and favourites: local copy, write-through to the server with retry, the changed-state check, Continue Watching and Mark watched for Jellyfin | Jellyfin titles in Continue Watching, Recent and Favourites, kept current both ways |

Phase 2 gives most of the value. Phase 3 carries the most risk (two-way state).

## Progress

| Phase | Status |
|---|---|
| 1 | Not started |
| 2 | Not started |
| 3 | Not started |

## Risks

- A large library makes the first sync long; paging and the progress line keep it visible.
- The `xtream_*` id migration (JM-Q1 a) touches the biggest tables; it needs the migration tests
  and the rollback helpers in `XtreamDatabaseMigrationTest`, and docs/DATABASE_SCHEMA.md.
- Two-way state can lose a change if both sides change the same item between checks; newest wins.
- Shared Jellyfin login (JM-Q2) shows every profile's Jellyfin history to all profiles.
