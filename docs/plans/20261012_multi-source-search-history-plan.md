# Multi-source: no current source

**Status:** In progress, 2026-10-12. Decisions Q1–Q7, P1–P5 and O1–O4 made 2026-10-12 (P1–P5 widened the
plan: there is no current source any more, browsing is merged too). Phase 1 can start. Nothing has
been built.

## Today

One source is active per device (`ProviderEntity.isActive`), picked in the header. Browsing,
search, Recent, Continue Watching and favourites only cover that source. `watch_state` and
`favorite_state` (`xtream_v2.db`) are keyed per source, and so are Recent Categories (per-source
SharedPreferences). Each profile remembers the source it last picked, and switching profile moves
the device to it. See [FEATURES.md](../FEATURES.md) → Home, Search, Recent and Favourites, Profiles,
and [DATABASE_SCHEMA.md](../DATABASE_SCHEMA.md).

## Goal

The idea of a device's source, or a current source, goes away. Each profile has access to a set of
sources, and everything it sees is merged across them: Live TV, Movies and TV Shows, search,
Recent, Continue Watching, favourites, Recent Categories and Search the guide. Each source's data
stays stored separately; only the screens merge it. Every item plays from its own source.

## Summary

- **Per-profile source access.** Each profile has the sources it can use. A newly added source is
  on for the profile that added it and off for every other profile until turned on on that
  profile's page. A profile never sees or plays a source it has no access to. The setting syncs.
- **Browsing is merged.** Live TV, Movies and TV Shows list the categories of all the profile's
  sources in one list, each tagged with its source. No source picker anywhere.
- **Search covers all the profile's sources.** Grouped by type as today, each result tagged with its
  source, one result per source.
- **Everything plays from its own source.** Details, episodes, Live TV and the player open with the
  item's own source's data, logins and stream addresses.
- **History, favourites and Recent Categories are merged in the UI**, stored per source as today.
  Each source keeps its own Recent size; the merged list is all of them, newest first.

## Decisions (2026-10-12)

| # | Question | Decision |
|---|---|---|
| Q1 | The same title on several sources | **One row per source**, each with a source badge. No merging. |
| Q2 | Progress across sources | **Per source.** Progress stays with the copy that was watched; the merged list still shows it. Cross-variant TMDB matching stays within a source. |
| Q3 | Favourites | **Stored per source, shown merged** (superseded by P5). |
| Q4 | Live channels in merged history | **Merged list, zap within source.** Recent shows channels from every source; Up/Down zaps only through the playing channel's own source. |
| Q5 | Search the guide (EPG) | **All the profile's sources.** Each source's guide sources are searched; an airing opens its channel on its own source. |
| Q6 | Source types | **Xtream only first** (searched in the local cached catalogue). Jellyfin and M3U later — see O1. |
| Q7 | Live sync of source access | **Synced**, like the profile's content filters. |
| P1 | New source | **Opt in.** On for the profile that added it, off for every other profile. The setting stores the sources a profile has (`providerKey`s, stable across devices). |
| P2 | Source access | A profile can only use the sources it has; there is nothing outside them to switch to. |
| P3 | Current source | **Goes away.** No device source, no current source, no source picker. Browsing is merged across the profile's sources, each category tagged with its source. |
| P4 | Merged Recent size | **Each source's own cap** (`watchHistorySize`); the merged list is all of them, newest first. |
| P5 | What else merges | Recent Categories, favourites and search history stay stored as today (per source, or per profile), but **every list shows them merged** across the profile's sources. |

## Open points, decided (2026-10-12)

| # | Question | Decision |
|---|---|---|
| O1 | Jellyfin and M3U sources with no current source | **Temporary entry.** A "Browse <source>" entry per Jellyfin or M3U source the profile has opens that source's sections as today. They stay out of merged browsing, search and history until a later plan merges them. |
| O2 | Upgrade: which sources each existing profile gets | **Only its last-picked source.** A profile that never picked one gets the device's current source at upgrade. Others are off until turned on. |
| O3 | Header sync status with several sources | **One summary line** ("Updated 2 hours ago", "Updating 1 of 3…", "1 failed"); tap / OK opens a sheet with each source's status and reason. |
| O4 | Content filters and guide sources edited for "the source in use" | **Source chooser** wherever that happens (profile page, Search the guide's buttons): pick the source, then its filters or guide sources. |

## What the code already gives us (survey, 2026-10-12)

- **Data is already per source.** One shared `XtreamDatabase`: every catalogue table, `watch_state`
  and `favorite_state` carry `providerId` (`watch_state` key `(providerId, profileId, itemId,
  contentType)`). No Room migration is needed for this plan (none is made: `isActive` stays, unused).
- **Several sources can be open at once.** `AppContainer.getMediaRepository(providerId)` builds and
  caches one `MediaRepository` per source; `0` means the active one. The source switcher already
  calls it with an explicit id.
- **Playback is already source-correct.** Stream, episode and catch-up URLs come from the
  provider instance's own session, and `XtreamLoginPicker.forPlayback(providerId, url)` picks that
  source's logins. Progress is written with the repository's own `providerId`.
- **Hidden categories hold for every source.** `ProviderRepository.applyCategoryFiltersForSwitch`
  recomputes the `excluded` flag of every Xtream source on a profile switch, so the FTS search's
  hidden-category filter is right for any source, not only the active one.
- **What ties everything to the active source is the UI layer.** No route carries a source
  (`Screen.MovieDetails`, `EpisodeSelection`, `Player`, `CategoryList`, `EpgGuide`, `catchupRoute`);
  `MovieDetailsViewModel`, `SeriesDetailsViewModel`, `StreamLoaderViewModel`, `CategoryViewModel`,
  `SearchViewModel`, `EpgViewModel` and `EpgBrowserViewModel` call `getMediaRepository()` with no id;
  TV Home builds its rows from the active repository (`ContentTypeSelectionScreen.kt`); `MediaItem`,
  `BrowseTarget`, `ContinueWatchingItem`, `SearchResult` and `EpgBrowserMatchedStream` carry no
  source; list keys, Live row dedup (`LiveRow.kt`), the stable Recent order (`RecentChannels.kt`)
  and zap neighbours (`LiveTvSplitLayout.neighborChannel`) are keyed by item id only, so the same
  id on two sources would collide.
- **Guide search already takes a list of guide sources** (`EpgIndexDao.searchByTitleFts`,
  `source_id IN (...)`), but the channel matcher is cached for one provider
  (`EpgChannelMatcher.getOrCreate`), and only the active source's guide sources are refreshed
  (`EpgSyncWorker`), so other sources' guides go stale.
- **Profile settings sync** as per-profile `SETTING` records (`AppSettings` `PER_PROFILE_SETTING_KEYS`
  / `SYNCED_SETTING_KEYS`, `SettingsSyncQueue.setting`); the server sees ciphertext only, so nothing
  changes there.

## Phases

One commit per phase on main, each with its tests, docs and plan update. Phases 1–5 keep a
current source under the hood and only widen what is shown; Phase 6 removes it. Lanes: 1 and 2 in
parallel; then 3, 4–5 and 7 in parallel; then 6; then 8. Phase 7 may land before Phase 6.

| Phase | What | Status |
|---|---|---|
| 1 | Source on routes and screens (no visible change) | Done (2026-10-12, `0abab248`) |
| 2 | Per-profile source access | Done (2026-10-12, `e2af80e0`) |
| 3 | Search across the profile's sources | Done (2026-10-12, `4460c434`) |
| 4 | Merged Recent, Continue Watching, favourites and Recent Categories | Done (2026-10-12, `a31954e4`) |
| 5 | Live TV Recent across sources, zap within source | Done (2026-10-12, `896e7200`) |
| 6 | Merged browsing; the current source goes | Done (2026-10-12, `7f1ce07a`, `a9bc5339`) |
| 7 | Search the guide across sources, and their guides kept fresh | Done (2026-10-12, `ba78d9a1`) |
| 8 | Device walks and docs | Not started — held until every known problem is fixed |

### Phase 1 — Source on routes and screens (no visible change)

- `Screen.MovieDetails`, `EpisodeSelection`, `Player`, `CategoryList`, `EpgGuide` and
  `catchupRoute` get `providerId: Long = 0L` (`0` = the current source, until Phase 6 removes it).
- Both nav hosts read it at each destination and copy it onto every onward route: details →
  Player, related titles, Watch live, episodes → Player, the guide → catch-up.
- The VM factories (MovieDetails, SeriesDetails, StreamLoader, Category, Epg) take it and call
  `getMediaRepository(providerId)`; `viewModel(key = …)` includes it. Next episode, zap and progress
  writes then follow the VM's repository with no further change.
- `MediaItem` / `BrowseTarget` (or a wrapper) carry the source id so the cards that build routes
  can pass it.
- Header source name on details, episodes and the TV two-column layout: the route's source.
- Tests: route round-trip with and without the id; a VM built with an explicit id reads that
  source (fake repositories).
- **Done 2026-10-12 (`0abab248`).** `ProviderRepository.getProviderOrActive(id)`; `MediaItem.providerId`
  set on rows `MediaRepository` builds itself (Recent, favourites), catalogue rows stay 0. No new
  `viewModel(key=)`: each nav entry has its own store. Left for Phases 5–6: the Live TV preview
  and phone dock loaders are created once per screen and must be rebuilt (or keyed) when a zap or
  list crosses sources. Still on 0 until their phase: search results, guide-search results, TV
  Home's Live row, Continue Watching and section roots.

### Phase 2 — Per-profile source access

- Per-profile synced setting in `AppSettings`: the `providerKey`s the profile has (P1).
  `PER_PROFILE_SETTING_KEYS`, `SYNCED_SETTING_KEYS`, `applyRemoteSetting` / `syncedSetting`;
  removed on profile delete; a new profile starts with the sources of the profile in use (as
  filters are copied today).
- Adding a source turns it on for the profile in use only; deleting one drops it everywhere. A
  source synced in from another device is on for the profile that added it there (its key arrives
  in that profile's setting).
- Upgrade (O2): each existing profile gets only its last-picked source; one that never picked
  gets the device's current source.
- One resolver, `profileSources(profileId)`: the profile's sources that still exist. Re-emits when
  sources or the setting change. Until Phase 6, the current source is kept inside the profile's
  sources (switching to a source the profile lacks is not offered; P2).
- Profile page, beside **Content filters ›**: **Sources ›**, every source with a switch. TV
  `ProfileEditPane` sub-pane like the filters one; phone `MobileProfileEditScreen`. A profile must
  keep at least one source.
- Strings in en/fr/mg (British spelling).
- Tests: resolver, opt-in for other profiles, sync round-trip, profile copy/delete, source delete.
- **Done 2026-10-12 (`e2af80e0`).** Setting `profile_sources_<profileId>` (sorted JSON array of
  `providerKey`s), resolver `ProviderRepository.profileSources` / `resolveProfileSources`, upgrade
  flag `profile_sources_seeded_v1` (not synced, so a later-upgrading device can't overwrite an
  earlier choice). Also beyond the plan: Manage Sources hides **Use** on sources the profile lacks;
  deleting the source in use moves to another of the profile's sources. **Sources ›** shows with 2+
  sources ("N of M"); TV pane, phone dialog. Left for Phase 8: the `sources.txt` focus walk needs the
  profile to have both sources (after the upgrade it has one); the new `settings.txt` step is not
  re-recorded; settings export/import doesn't carry a profile's sources (done in Phase 8: each
  exported source has `inProfile` for the active profile; an import applies it to the active
  profile for added and overwritten sources).

### Phase 3 — Search across the profile's sources

- `SearchViewModel` runs `repo.search` on every one of the profile's Xtream sources in parallel,
  the category-name matches and the hidden counts too; `"ALL"` is the union of their content types.
- `SearchResult` / `CategorySearchResult` get `providerId` and the source name. One result per
  source (Q1), with a source badge when the profile has more than one source. Grouping unchanged
  (type, then categories, then items); ranking as today.
- List keys include the source (TV `SearchScreen` row keys, phone list keys).
- Opening a result passes its `providerId`. Favourite, Mark watched and Mark show watched use the
  result's own repository.
- A source whose search fails is left out quietly (developer mode: its error under the results).
- Search history: unchanged (already per profile).
- Tests: merge across two fake sources, a failing source, keys unique across sources.
- **Done 2026-10-12 (`4460c434`).** `SearchViewModel.ensureSources` (the profile's Xtream sources when
  the active one is Xtream, else only the active one); results get `providerId`, `sourceName` (null
  with one source, so no badge) and `listKey`; favourite/watched take the result's `providerId`
  (screens keep it in `favoriteMenuSource`). Tests cover the cache-scan path only (mockk can't
  stub `MediaRepository.search`'s nullable `Result`). Opening Search now connects every Xtream
  source of the profile. Badge look and opening on another source: Phase 8 walk.

### Phase 4 — Merged Recent, Continue Watching, favourites and Recent Categories

- A merger over the profile's sources' repositories: Recent per content type (each source's own
  cap, P4), Continue Watching, favourites and Recent Categories, newest first (favourites by name as
  today), each item tagged with its source. Listens to every repository's flows, so a Live sync
  change on any of them shows.
- TV Home (`ContentTypeSelectionScreen`): Continue watching, Channels, Favourite channels /
  movies / shows from the merger. Posters and up-next still come from each item's own repository
  (`rehydrateThumbnails`, `upNextForShows`).
- `CategoryViewModel` Recent and Favourites virtual categories (TV and phone tabs) from the merger.
- Remove from Recent / Favourites and their Undo act on the item's source (`RecentRemoval` gets the
  provider); Clear Recent clears every source of the profile for that list.
- Dedup and list keys by (source, id) — `LiveRow.mergeLiveRow`, Recent and Favourites lists.
- Cards open with their `providerId`.
- Tests: two-source merge order with per-source caps, removal/Undo on the right source, dedup keys.
- **Done 2026-10-12 (`a31954e4`).** `core/ui/.../history/MergedLists.kt` (`forProfile`, `withOthers`,
  `repoFor`, `recentItems` flow over every source, `favorites`, `continueWatching`,
  `recentCategories`, `lastWatchedChannel`, `removeFromRecent` / `restoreRecent` / `clearRecent`);
  `MediaItem.lastPlayedAt` / `resumable` / `sourceKey`; `ContinueWatchingItem.providerId`;
  `RecentRemoval.providerId`; `AppContainer.getStoredDataRepository(id)` (other sources restore
  their session in the background). **Carried into Phase 6:** per-row state in `CategoryViewModel`
  (`favoriteIds`, `watchProgress`, `watchedIds`, `nowPlaying`) is still keyed by raw id, so the same
  id on two sources can show the other row's star or progress; the merged Recent Categories row id
  is still `recent_cat_<id>` (probable favourite mismatch, unverified). TV Home reloads on resume,
  not on a live-sync change while open (as before).

### Phase 5 — Live TV Recent across sources, zap within source (Q4)

- `rememberStableRecentOrder` and `neighborChannel` key by (source, id); Up/Down in full screen
  steps only through channels of the playing channel's source, keeping the list's order.
- "Last watched" (first card of Home's Channels row, the phone dock's start channel): the newest
  across the profile's sources.
- Tests: zap skips other sources' channels and wraps within the source; stable order with
  duplicate ids across sources.
- **Done 2026-10-12 (`896e7200`).** `neighborChannel` zaps within the playing channel's source;
  preview and dock loaders keyed `loader:<source>` / `dockLoader:<source>`, the old one stopped with
  `StreamLoaderViewModel.abandon()` so its delayed Recent write doesn't land. The phone's Last
  watched sheet stays on the loader's own source. Focus after removal/Undo and Home return-focus
  (rekeyed to `sourceKey`) need the Phase 8 walk.

### Phase 6 — Merged browsing; the current source goes (P3)

- Live TV, Movies and TV Shows: the category list is every source's categories, each tagged with
  its source (badge, and source name on the category's header). Category ids are keyed by
  (source, id). A category opens on its own source; its channel list and zap stay within it.
- Sections shown: the union of the profile's sources' content types.
- Remove: `ProviderEntity.isActive` and `setActiveProvider` / `getActiveProvider` callers, the
  per-profile last source (`activateLastProvider` and its synced setting), the source picker
  (TV `TvSections`, phone `MobileSourceTopBar`), `LocalActiveProviderId`, and
  `getMediaRepository()`'s `0` default — every caller passes a source. **No migration** (user,
  2026-10-12): the `isActive` column stays in `providers.db` v17, unused, so an older build still
  opens the database; DATABASE_SCHEMA notes it as unused.
- Header: one summary line for all the profile's sources (O3) — "Updated 2 hours ago",
  "Updating 1 of 3…", "1 failed"; tap / OK opens a sheet with each source's status and reason
  (raw detail in developer mode).
- Catalogue sync: runs for every source of the profile in use, not only one (still
  foreground-only).
- Content filters and guide sources: a source chooser first (O4) on the profile page and Search
  the guide's Guide sources button.
- Jellyfin and M3U (O1): a "Browse <source>" entry per such source the profile has (TV rail, phone
  tab header) opens that source's sections as today, scoped to it through the Phase 1 route source.
- Live sync: the "last source" setting is dropped; older builds that still send it are ignored.
- Tests: merged category list and keys, every former current-source caller given a source.
- **Part 1 done 2026-10-12 (`7f1ce07a`), merged browsing.** `MediaCategory.providerId` / `key`
  (`"<source>/<id>"`), `categoryKey`, `sourceKey`; `CategoryViewModel` merges every Xtream source's
  categories around `MergedLists.browsingProviderId` (the profile's first Xtream source); per-row
  state (`favoriteIds`, `watchProgress`, `watchedIds`, `nowPlaying`, `catchupDays`) keyed by source.
  Recent/Favourite Categories rows `recent_cat_<source>/<id>` / `fav_cat_<source>/<id>` (the Phase 4
  mismatch was real: it favourited a stream named `recent_cat_X`; fixed). `core/ui/home/ProfileSections`
  (`merged`, `browse`, `opening`); TV rail Browse items under the sections (dialog when several
  sections); phone Browse rows in the profile sheet; SMB and Local count as Browse sources too.
  Malagasy "Jereo %1$s" for Browse needs a check.
- **Part 2 (removal) must handle** what part 1 left on the active source: the fallbacks in
  `MergedLists.browsingProviderId`, `TvSections.loadRailSource`, `loadActiveSource`; the rail hidden
  with no active provider; `ActiveSource` and the phone tab header (source line, sync status,
  `MobileSourceTopBar` picker); `JellyfinSignInPanel` / `showsSignIn`; TV Home
  (`ContentTypeSelectionScreen`: repo, rows, pill, `onCapabilitiesResolved`);
  `CategoryViewModel.lastPlayedItemId` / `supportsNativeEpg` (primary source only; the current-row
  marker compares bare ids); the TwoColumnLayout header count and fallback name; `TvNavHost` Xtream
  auto-restore, `MobileNavHost`, `EpgBrowserViewModel`, Settings / Provider / Profiles VMs,
  `CoreInfo`, Movie / Episode screens (`getProviderOrActive`). Known risk: a section fails as a
  whole when the profile's first Xtream source can't connect.
- **Part 2 done 2026-10-12 (`a9bc5339`), the current source goes.** No migration: `isActive` stays in
  v17, never written; read once by the O2 upgrade (`ProviderDao.legacyActiveProviderKey()`). Summary
  line `syncSummary` (running first, then failures, then "Updated" from the least recently updated
  source) with a "Your sources" sheet; `SourceChoice.of` choosers (content filters, Guide sources,
  phone Edit Source); a section is built from the sources that load (fixes part 1's regression);
  profiles without Xtream get Browse buttons, with no source Add Source. Routes require
  `providerId` except `CategoryList` (0 = a section's merged list). Fixed the part 1 test flake
  (`CategoryViewModelMultiSourceTest` leaked coroutines into `EpgGuidePagingTest`).
  **Calls made beyond the plan:** a profile with no Xtream source searches
  its Jellyfin/M3U sources; Search the guide also covers M3U and Local sources; catalogue sync still
  covers every Xtream source on the device; Settings' subscription block (expiry, max connections,
  trial) removed. Focus walks home, home-rows, sources and settings edited by hand, not re-recorded.
  User, 2026-10-12: keep the Search, Search the guide and catalogue-sync calls; bring the
  subscription block back — done per source on Manage sources (each source's URL, expiry, max
  connections, trial, under its name with several).

### Phase 7 — Search the guide across sources, and their guides kept fresh (Q5)

- `EpgBrowserViewModel` / `XmltvSearchService`: guide source ids of every source of the profile;
  `EpgChannelMatcher` cache keyed by provider; each airing matched against its own source's
  channels and hidden categories; `EpgBrowserMatchedStream` gets `providerId`.
- "Matched only" keeps airings matched on any of the profile's sources; "In <category> only" stays
  on the guide's own source. Opening an airing opens Live TV on its channel and source.
- Guide freshness: `EpgSyncWorker` refreshes the stale guide sources of every source of the
  profile (inline in `doWork`, foreground as today). The header's freshness line covers all of
  them.
- Now-playing on channel cards of any source reads that source's guide.
- Tests: multi-source source-id list, matcher per provider, airing → (source, channel).
- **Done 2026-10-12 (`ba78d9a1`).** `GuideScope` (searched providers = the profile's Xtream sources
  plus the active one whatever its type; guide-source id → provider map), `GuideAiringMatcher`,
  `EpgChannelMatcher` cached per provider, `Screen.EpgBrowser(providerId)` for "In <category> only",
  `EpgFileManager.stateFor(Set)` / `launchRefreshStale(List)`, `EpgSyncWorker` one run per provider
  inline in `doWork()`. Known costs, to watch in Phase 8: each provider's run rebuilds the search
  index once (several rebuilds per worker run — slow on a Shield; a single rebuild at the end is
  the fix if it shows); one matcher per searched source kept in memory (30–60 MB each for large
  sources). Header subtitle and Guide sources still on the active source until Phase 6.

### Phase 8 — Device walks and docs

- Emulators with two Xtream sources (jellyxtream for VOD, iptv for live) and two profiles with
  different sources: browse, search, open and play from either source, Recent, Continue Watching
  and favourites on TV Home and phone tabs, zap within source, guide search, source access synced
  between two devices, a profile never seeing a source it lacks.
- Docs: FEATURES (Home, Search, Search the guide, Recent and Favourites, Profiles, Sources, Live
  sync), NAVIGATION_GUIDE (no source picker; routes with a source), RUN_GUIDE if adding sources
  changes, RELEASE_NOTES, DATABASE_SCHEMA (Phase 6).
