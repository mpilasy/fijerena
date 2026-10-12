# Multi-source search and history

**Status:** Proposed, 2026-10-12. Decisions Q1–Q7 made; phases written from a read-only survey of
the code (2026-10-12). Proposals P1–P5 below need a yes before Phase 1 starts. Nothing has been
built.

## Today

One source is active per device. Search, Recent and Continue Watching only cover that source.
`watch_state` and `favorite_state` (`xtream_v2.db`) are keyed per source, and so are Recent
Categories (per-source SharedPreferences). See [FEATURES.md](../FEATURES.md) → Search, Recent and
Favourites, and [DATABASE_SCHEMA.md](../DATABASE_SCHEMA.md).

## Goal

Sources stay separate, but search and play history are shared across them. A search returns
results from every source the profile has enabled. Playing a result just works, whichever source it
came from. History is merged.

## Summary

- **Browsing stays per source.** Live TV, Movies, TV Shows and the categories still show only the
  source picked in the header, and the source picker stays.
- **New per-profile setting: which sources join in.** Each profile turns sources on or off for
  search and history. A new profile defaults to all on. This switch is separate from the active
  source.
- **Search covers every enabled source.** Results are still grouped by type (Live, Movies, Shows),
  and each item shows a source badge.
- **Playing a result just works.** It plays from the source it came from without switching the
  active source. Back returns to Search. Details, episodes and Live TV open with that source's data.
- **History is merged.** Recent and Continue Watching (TV Home, phone tab Recents) list items from
  every enabled source, newest first, and each item plays from its own source.

## Decisions (2026-10-12)

| # | Question | Decision |
|---|---|---|
| Q1 | The same title on several sources | **One row per source**, each with a source badge. No merging. |
| Q2 | Progress across sources | **Per source.** Progress stays with the copy that was watched; merged history still lists it. Cross-variant TMDB matching stays within a source. |
| Q3 | Favourites | **Stay per source**, as today. |
| Q4 | Live channels in merged history | **Merged list, zap within source.** Recent shows channels from every enabled source; Up/Down zaps only through the playing channel's own source. |
| Q5 | Search the guide (EPG) | **All enabled sources.** Each source's guide sources are searched; an airing opens its channel on its own source. |
| Q6 | Source types | **Xtream only first** (searched in the local cached catalogue). Jellyfin and M3U later. |
| Q7 | Live sync of "enabled sources" | **Synced**, like the profile's content filters. |

## What the code already gives us (survey, 2026-10-12)

- **Data is already per source.** One shared `XtreamDatabase`: every catalogue table, `watch_state`
  and `favorite_state` carry `providerId` (`watch_state` key `(providerId, profileId, itemId,
  contentType)`). No Room migration is needed for this plan.
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

## Proposals (need a yes)

| # | Proposal | Why |
|---|---|---|
| P1 | Store the **sources left out**, not the ones in: a per-profile synced setting holding a JSON array of the left-out sources' `providerKey`s (stable across devices, unlike local ids). | A source added later is in by default, with nothing to update. |
| P2 | **The source in use is always included**, whatever the setting says. The setting only adds or removes the others. | Search and Recent never come up empty on the source being browsed. |
| P3 | Opening another source's item shows **that source's screens** (details, episodes, Live TV on its channel, player), its name in the header, without moving the device's source. Back returns to Search or Home. Browsing onward from there (a category, related titles) stays on that source until Back. | Matches "plays from its own source" without a source switch. |
| P4 | Merged Recent keeps **one cap** per list: the largest `watchHistorySize` of the included sources. Continue Watching unchanged (all resumable). | Simple; a per-source cap makes no sense in one list. |
| P5 | Unchanged per source: Recent Categories, favourites (Q3), search history (already per profile), Mark watched cross-variant matching (Q2). Clear Recent clears the **included sources'** Recent for that list. | Keeps the scope to search and history. |

## Phases

One commit per phase on main, each with its tests, docs and plan update. Xtream sources only (Q6):
a Jellyfin or M3U source is never searched or merged in from another source; when it is the source
in use, it behaves as today.

| Phase | What | Status |
|---|---|---|
| 1 | Source on routes and screens (no visible change) | Not started |
| 2 | "Sources in search and history" setting | Not started |
| 3 | Search across sources | Not started |
| 4 | Merged Recent and Continue Watching | Not started |
| 5 | Live TV Recent across sources, zap within source | Not started |
| 6 | Search the guide across sources, and their guides kept fresh | Not started |
| 7 | Device walks and docs | Not started |

### Phase 1 — Source on routes and screens (no visible change)

- `Screen.MovieDetails`, `EpisodeSelection`, `Player`, `CategoryList`, `EpgGuide` and
  `catchupRoute` get `providerId: Long = 0L` (`0` = the source in use, as `getMediaRepository`).
- Both nav hosts read it at each destination and copy it onto every onward route: details →
  Player, related titles, Watch live, episodes → Player, the guide → catch-up.
- The four VM factories (MovieDetails, SeriesDetails, StreamLoader, Category) take it and call
  `getMediaRepository(providerId)`; `viewModel(key = …)` includes it. Next episode, zap and progress
  writes then follow the VM's repository with no further change.
- `MediaItem` / `BrowseTarget` (or a wrapper) carry the source id so the cards that build routes
  can pass it.
- Header source name on details, episodes and the TV two-column layout: the route's source, not
  the active one.
- Tests: route round-trip with and without the id; a VM built with an explicit id reads that
  source (fake repositories).

### Phase 2 — "Sources in search and history" setting

- Per-profile synced setting (P1) in `AppSettings`: getter/setter, `PER_PROFILE_SETTING_KEYS`,
  `SYNCED_SETTING_KEYS`, `applyRemoteSetting` / `syncedSetting`, removed on profile delete, copied
  on profile add.
- One resolver, `includedSources(profileId)`: Xtream sources with a login, minus the left-out ones,
  plus the source in use (P2). Re-emits when sources, the setting or the source in use change.
- Profile page, beside **Content filters ›**: **Sources in search and history ›**, a list of the
  Xtream sources with a switch each (the source in use shown on and locked). Shown only with two or
  more Xtream sources. TV `ProfileEditPane` sub-pane like the filters one; phone
  `MobileProfileEditScreen`.
- Strings in en/fr/mg (British spelling).
- Tests: resolver (new source in by default, deleted source ignored, source in use always in), sync
  round-trip of the setting, profile copy/delete.

### Phase 3 — Search across sources

- `SearchViewModel` runs `repo.search` on every included source's repository in parallel, the
  category-name matches and the hidden counts too; `"ALL"` is the union of their content types.
- `SearchResult` / `CategorySearchResult` get `providerId` and the source name. One result per
  source (Q1), with a source badge when more than one source is included. Grouping unchanged
  (type, then categories, then items); ranking as today, then source in use first on ties.
- List keys include the source (TV `SearchScreen` row keys, phone list keys).
- Opening a result passes its `providerId` (Phase 1 routes) from both nav hosts. Favourite,
  Mark watched and Mark show watched use the result's own repository.
- A source whose search fails is left out quietly (developer mode: its error under the results).
- Tests: merge and ordering across two fake sources, a failing source, keys unique across sources.

### Phase 4 — Merged Recent and Continue Watching

- A small merger over the included sources' repositories: Recent per content type (P4 cap) and
  Continue Watching, newest `lastPlayedAt` first, each item tagged with its source. Listens to
  every included repository's `recentItems` flow, so a Live sync change on any of them shows.
- TV Home (`ContentTypeSelectionScreen`): Continue watching and the Channels row from the merger;
  favourites rows stay on the source in use (Q3). Posters and up-next still come from each item's
  own repository (`rehydrateThumbnails`, `upNextForShows`).
- `CategoryViewModel` Recent virtual category (TV and phone tabs) from the merger.
- Remove from Recent and its Undo act on the item's source (`RecentRemoval` gets the provider);
  Clear Recent on the included sources (P5).
- Dedup and list keys by (source, id) — `LiveRow.mergeLiveRow`, Recent lists.
- Cards open with their `providerId`.
- Tests: two-source merge order and cap, removal/Undo on the right source, dedup keys.

### Phase 5 — Live TV Recent across sources, zap within source (Q4)

- The Recent list shows every included source's channels (from Phase 4).
- `rememberStableRecentOrder` and `neighborChannel` key by (source, id); Up/Down in full screen
  steps only through channels of the playing channel's source, keeping the list's order.
- "Last watched" (first card of Home's Channels row, the phone dock's start channel): the newest
  across included sources.
- Playing a channel of another source opens Live TV on that source (P3); its own category lists
  are that source's.
- Tests: zap skips other sources' channels and wraps within the source; stable order with
  duplicate ids across sources.

### Phase 6 — Search the guide across sources, and their guides kept fresh (Q5)

- `EpgBrowserViewModel` / `XmltvSearchService`: guide source ids of every included source;
  `EpgChannelMatcher` cache keyed by provider; each airing matched against its own source's
  channels and hidden categories; `EpgBrowserMatchedStream` gets `providerId`.
- "Matched only" keeps airings matched on any included source; "In <category> only" stays on the
  guide's own source. Opening an airing opens Live TV on its channel and source.
- Guide freshness: `EpgSyncWorker` refreshes the stale guide sources of every included source, not
  only the source in use (inline in `doWork`, foreground as today). The header's freshness line
  covers all searched guide sources.
- Now-playing on Recent channel cards of another source reads that source's guide.
- Tests: multi-source source-id list, matcher per provider, airing → (source, channel).

### Phase 7 — Device walks and docs

- Emulators with two Xtream sources (jellyxtream for VOD, iptv for live; see the test-provider
  notes): search, open and play from the other source, Recent and Continue Watching on TV Home and
  phone tabs, zap within source, guide search, the setting synced between two devices.
- Docs: FEATURES (Search, Search the guide, Recent and Favourites, Profiles, Live sync),
  NAVIGATION_GUIDE (routes with a source), RELEASE_NOTES; DATABASE_SCHEMA only if a schema changes.
