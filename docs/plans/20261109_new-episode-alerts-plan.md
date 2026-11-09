# New episode alerts

**Status:** Planned 2026-11-09, not started. The user took every recommended answer on 2026-11-09 (see Answers).

## Goal

When a source adds a new episode (or a new season) of a show that the profile follows, the app
tells the profile. The show gets a **New** badge on its cards, and Home gets a **New Episodes** row,
on TV and phone. A notification is possible (NE-Q4). Xtream sources only in this plan.

## What exists (survey 2026-11-09)

- **Catalogue sync.** `XtreamContentManager.syncSeries()` reads the whole show list (`get_series`)
  as a `RefreshTask`. Each show's `last_modified` goes into `xtream_series.lastModified` and into
  `XtreamSeriesEntity.computeHash`. A changed hash rewrites the row (`insertKeepingDetailCache`) and
  clears `episodesFetchedAt`. The sync runs every 4 hours in `XtreamSyncWorker` (WorkManager, a
  foreground service via `trySetForeground`, scheduled by `ProviderSyncManager`), and in-process on
  TV. `ProviderSyncRunner.catalogueUpdates` goes up after each successful sync; `FijerenaApplication`
  already checks the Wishlist (`WantedTitles.refresh()`) and gone favourites on it. This is the
  catalogue sync, not live sync: the "live sync is foreground-only" rule does not apply to it.
- **Episode lists are fetched on demand.** `XtreamMediaProvider.getSeriesDetail` calls
  `get_series_info` when the show page opens and stores the list in `xtream_episodes`
  (`XtreamEpisodeDao`), fresh for 30 days (`EPISODE_LIST_CACHE_TTL_MS`, `episodesFetchedAt`). A show
  never opened has no stored episodes. One `get_series_info` = one request per show.
- **Favourites.** `favorite_state` (per provider and profile, `contentType` = `TV_SHOWS`, `tmdbId`,
  `tags`, synced). `MergedLists.favorites(TV_SHOWS)` (core/ui/history) merges every source of the
  profile; Home's **Favourite Shows** row uses it.
- **Watch state.** `watch_state` per profile (`seriesId`, `episodeId`, `isCompleted`, synced;
  `setEpisodesWatched`). Continue Watching = `MergedLists.continueWatching()`
  (`ContinueWatchingItem`, core/player). Episode cards show the "Continue Watching" badge
  (`series_continue_watching_badge`, `TvContinueWatchingShelf`, both `EpisodeSelectionScreen`s).
- **Home.** TV: `ContentTypeSelectionScreen` loads its rows in `LaunchedEffect`s from `MergedLists`;
  order and focus follow `HomeShelf` (tv/.../contentselection/HomeCardFocus.kt). Phone:
  `MobileHomeScreen`. The TV Shows group (`SectionPanel`) holds Favourite Shows, Wishlist shows and
  the chart rows. The row order plan (docs/plans/20261101_home-row-order-plan.md) is not built; it
  orders `HomeShelf` entries.
- **Prior art: the Wishlist.** `WantedTitles` works out "Available" on each device after each
  catalogue sync, never stored; `wanted_title.seenAt` clears "new" on open; `notifiedAt` makes the
  Home banner show once ("2 titles on your Wishlist are now available"); the row title counts new
  ones ("Wishlist · 2 new").
- **Card second line.** "Gone from source" (`MediaRepository.goneFromSource`) and "Available" are
  shown on the card's second line.
- **Notifications.** `GameReminderNotifications` (core/network/sports, channel `game_reminders`);
  on TV the app shows a banner (`TvGameReminderBanner`) in place of a notification.
- **Child profile.** Kids' Home is favourites only (docs/plans/20261019_kids-home-plan.md); the
  content filters and the kids' filter (docs/plans/20261020_kids-filter-plan.md) remove hidden
  titles from Recent, Favourites, Continue Watching and Home rows.
- **Not in scope.** Jellyfin keeps state on its server and is not merged yet; M3U, SMB and Local
  have no series episode lists.
- **Database.** `xtream_v2.db` is at version 31.

## Decisions (proposed)

| # | Subject | Decision |
|---|---|---|
| NE-D1 | Trigger | No new periodic work. After each successful catalogue sync of a source, a check step runs in the same work (the worker's foreground service, or in-process). |
| NE-D2 | Candidates | `syncSeries` collects the followed shows (NE-Q1) of the source whose hash changed in this sync (mostly a new `last_modified`). Only these get a `get_series_info` call. |
| NE-D3 | Request budget | At most 20 `get_series_info` calls per source per check, one at a time, through the existing provider session. Candidates over the cap wait for the next sync, oldest change first. A fallback round checks one followed show not checked for 7 days per sync, for panels that do not change `last_modified` (NE-Q5). |
| NE-D4 | Baseline | The stored `xtream_episodes` rows of the show are the "known" episodes. An episode in the new list whose (season, episode number) pair is not stored is new (Risk 4). A show with no stored list gets its first list as the baseline, with no alert. |
| NE-D5 | Storage | One new table `new_episode` (`providerId`, `seriesId`, `episodeId` PK; `season`, `episodeNum`, `foundAt`), catalogue data, local to the device, not synced (as Wishlist "Available"). Rows go after 30 days, and with the show or the source. Room migration 31 → 32. |
| NE-D6 | Open path | `getSeriesDetail` stores a list through the same compare step. A show page opened after a source added an episode records it as new for a followed show. The open does not swallow the alert. |
| NE-D7 | New for a profile | A show is new for a profile when it is followed and has a `new_episode` row whose episode the profile has not started or finished (`watch_state`), found after the profile followed the show. Worked out on each device at read time. Seen state rides on `watch_state`, which already syncs (NE-Q3). |
| NE-D8 | Badge | A **New** badge on the show's card (accent pill, the shared card badge style) in Favourite Shows, New Episodes, the TV Shows lists (Favourites, Recent, categories) and Search. On the show page, each new episode has a **New** mark next to its number. |
| NE-D9 | Home row | **New Episodes** (Title Case): the first row of the TV Shows group, before Favourite Shows, on TV and phone, only when it has a card. One card per show, newest find first, at most 20. Second line: "S02E05" for one new episode, "3 new episodes" for more, "New season" when a new season number is in the list. OK opens the show page on the season of the first new episode. New `HomeShelf.NEW_EPISODES` entry, so the row order plan picks it up. |
| NE-D10 | Multi-source | The alert is on the followed copy's source. A new episode on another copy (same TMDB id) does not count in this plan. |
| NE-D11 | Child profile | The row and the badge use the same filtered lists as Favourite Shows. A show hidden by the content filters or the kids' filter gives no card and no notification. The kids' Home gets the row (it is built from its favourites). |
| NE-D12 | Card menu | TV Hold OK / Menu and phone long-press on a New Episodes card: **Mark New Episodes Watched** (through `setEpisodesWatched`, with Undo) and the favourite toggle. |
| NE-D13 | TV focus | The row follows `HomeShelf` order and the focus contract in CONTRIBUTING.md. After a card leaves (its episodes watched), focus goes to the next card, else the next row. |

## Open questions

| # | Question | Options |
|---|---|---|
| NE-Q1 | What "follows" means | (a) Favourite shows only. (b) **Favourite shows, plus shows the profile watched in the last 60 days that are not finished.** Recommended: Continue Watching shows are the ones a viewer waits on; the cap in NE-D3 keeps the cost low. (c) As (b), plus every show in Recent. |
| NE-Q2 | When the "New" goes | (a) Opening the show page clears it (as the Wishlist's `seenAt`). (b) **Watching each new episode (or marking it watched) clears it; after 30 days it goes in all cases.** Recommended: opening a page without watching keeps the reminder, and watch state already syncs, so no new sync kind is needed. (c) (a) for the badge on other cards, (b) for the Home row. |
| NE-Q3 | Sync the "seen" state | (a) **No new sync: seen follows `watch_state` (NE-Q2 (b)).** Recommended. (b) A new sync kind `episode_seen` per profile, needed only with NE-Q2 (a); new kinds need the one-time re-pull. |
| NE-Q4 | Notification | (a) None; the Home row and badge only. (b) **A Home banner once per find, as the Wishlist banner ("3 shows you follow have new episodes"), on TV and phone; no system notification.** Recommended: TV notifications are not seen, and the sync often runs at night. (c) As (b), plus a phone notification after a background sync, behind a This Device switch (off by default). |
| NE-Q5 | Fallback round for panels without a `last_modified` change | (a) **One followed show per source per sync not checked for 7 days.** Recommended: at most 6 extra requests per source per day. (b) None: rely on `last_modified` only. (c) A user switch per source. |

## Answers (user, 2026-11-09)

| # | Answer |
|---|---|
| NE-Q1 | (b) Favourite shows, plus shows watched in the last 60 days that are not finished. |
| NE-Q2 | (b) Watching (or marking watched) each new episode clears it; after 30 days it goes in all cases. |
| NE-Q3 | (a) No new sync kind; seen follows `watch_state`. |
| NE-Q4 | (b) A Home banner once per find, TV and phone; no system notification. |
| NE-Q5 | (a) One followed show per source per sync not checked for 7 days. |

## Phases

| # | Work | Done when |
|---|---|---|
| 1 | Data: `new_episode` table, entity, DAO, migration 31 → 32; the compare step shared by the check and `getSeriesDetail`; pruning (30 days, show gone, source deleted) | Unit tests for the compare step (new id, new season, no baseline, removed episode) and the migration test pass |
| 2 | Check: candidate list from `syncSeries`, the budget and the fallback round, the step after a successful sync (`ProviderSyncRunner`); followed set across the device's profiles per source | Tests with a fake service count the calls (cap, order, fallback); the step never fails the sync |
| 3 | Read side in core/ui: `NewEpisodes.forProfile(...)` over `MergedLists` and `watch_state`, filtered as Favourite Shows; the banner state (NE-Q4) | `NewEpisodesTest` (watched clears, 30 days, followed-after, child filter, two sources) |
| 4 | TV: New Episodes row, badge on cards, **New** mark on episodes, card menu, banner, `HomeShelf.NEW_EPISODES` | Robolectric focus test for the row; `scripts/focus-walks` file |
| 5 | Phone: the same on `MobileHomeScreen`, list cards and `EpisodeSelectionScreen` | Compose tests for the row and the badge |
| 6 | Strings (en, fr, mg in one change; British spelling; Title Case in English only), docs: FEATURES (Home, TV Shows, Child Profile), DATABASE_SCHEMA, RELEASE_NOTES | Lint clean; docs updated in the same commits |
| W | Merge, full suite, lint, gates, walks on both emulators | Walk below passes on TV and phone |

Phases 4 and 5 can run in parallel after Phase 3.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1 | Not started | |
| 2 | Not started | |
| 3 | Not started | |
| 4 | Not started | |
| 5 | Not started | |
| 6 | Not started | |
| W | Not started | |

## Checks

- **Tests:** the unit tests named in each phase; `XtreamDatabaseMigrationTest` for 31 → 32. The
  migration updates docs/DATABASE_SCHEMA.md (new table, version line, history paragraph) and the
  rollback helpers in `XtreamDatabaseMigrationTest` in the same commit.
- **Gates:** ktlint, touched-module tests and compile per phase; `./gradlew lintDebug` with 0
  errors before a push; the full suite once at merge.
- **Emulator walk (TV and phone, jellyxtream test provider):** favourite a show and open it (the
  baseline); add an episode on the jellyxtream bridge; run a catalogue sync. Then check: the New
  Episodes row and its second line, the **New** badge on Favourite Shows and in the lists, the
  episode's **New** mark, the banner once, OK opens the right season. Play the episode past the
  watch mark: the card leaves and focus moves on (TV). Check the other device after live sync.
  Check a child profile with the show in a hidden category: no card. Count the `get_series_info`
  calls in the log for one sync. Language: Malagasy on both emulators (English text on screen is a
  finding).

## Risks

1. **Request limits.** A source can rate-limit or block. The cap, one call at a time, and the
   hash-change filter keep it to a few calls per sync. A failed call keeps the candidate for the
   next sync.
2. **`last_modified` not changed by some panels,** or changed for a metadata edit only. The fallback
   round covers the first case late (up to 7 days); the confirm call makes the second case cost one
   request and no alert.
3. **A lost baseline.** "Shrink Database" or a source reset removes `xtream_episodes`. The next list
   is then a new baseline with no alert: a missed alert, never a false one.
4. **A source that renumbers or re-uploads episodes** gives new ids for old episodes: a false alert.
   Compare on (season, episode number) as well as the id, and alert only on a pair not seen before.
5. **Several devices.** Each device finds new episodes on its own sync, at a different time. The
   row can show on one device first; seen state agrees through `watch_state`.
6. **Room migration.** A new table only; the rollback helpers must know it.
