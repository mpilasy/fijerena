# Watched follow-ups

**Status:** In progress (2026-10-11): all three phases on main; emulator check pending.

Three follow-ups to [Mark a season or show watched](./archive/20261011_season-show-watched-plan.md),
decided by the user on 2026-10-11.

## Decisions

1. **Play goes to the first unwatched episode.** After episodes are marked watched, the series
   screen's Play and the Home "Continue watching" card point at the first unwatched episode, not
   the old resume point. The "Continue watching" badge follows that episode, and the show stays in
   the Home row.
2. **None of the watched marks apply to Jellyfin.** Jellyfin keeps watched state on its server,
   which the app can't write. The single film/episode "Mark watched" is hidden there too, as the
   season and show marks already are.
3. **Phone list rows get a long-press menu.** It holds the row's actions. The swipe stays as it is.

## Progress

| Phase | What | Status |
|---|---|---|
| 1 | Play and the Home card go to the first unwatched episode | Done (4a4e6824) |
| 2 | Hide every watched mark on Jellyfin | Done (404c6d91) |
| 3 | Phone list rows: long-press menu | Done (be1e5e40) |

## Phase 1: first unwatched episode

- `SeriesDetail.resumeAnchorEpisodeId` (`core/player/.../domain/SeriesDetail.kt`): when the last
  played episode is finished, return the next episode after it that isn't completed. If there is
  none after it, return the first one that isn't completed from the start of the show. If every
  episode is completed, return the first episode. Today it returns only the next episode, even
  when that one is already watched.
- A show with marked episodes but nothing played should open on the first unwatched episode too.
- Series screens (TV and phone): after a season or show mark made on that screen, recompute the
  anchor so Play and the badge move right away, without leaving the screen. A single-episode
  toggle still doesn't move the anchor.
- Home "Continue watching" (`MediaRepository.upNextForShows` / `nextFromProvider`): a finished
  show's card offers the first unwatched episode by the same rule, using completed rows
  (TMDB siblings count, as in `episodesWatchState`). A show where every episode is watched stays
  on the shelf and offers the first episode, instead of leaving it.
- Play's wording doesn't change ("▶ Play: S03E01 · Title"; "Resume" when it has a position).
- Not for Jellyfin (its Continue watching comes from the server).
- Tests: unit tests for the anchor rule (skip over watched, wrap to start, all watched) and for
  the shelf card after a show mark.
- **Done** (4a4e6824): shared rule `nextUnwatchedEpisodeId`; a mark no longer counts as a play (a row
  without `lastPlayedAt` has timestamp 0); after a season or show mark the screens recompute the
  anchor without moving focus or scroll. The shelf keeps every finished show whose episode list is
  known, a one-episode show included, until removed from Recent. `XtreamEpisodeDao.getNextEpisode`
  and its androidTest went with nothing left calling them. Left as is: the screens' anchor counts
  only the show's own rows (TMDB siblings don't move it, the shelf counts them); unmarking a season
  doesn't move Play back.

## Phase 2: no watched marks on Jellyfin

- One gate on `MediaRepository` for every watched mark: rename `supportsMarkEpisodesWatched` to
  `supportsMarkWatched` (same meaning, `!usesServerUserData`).
- Hide the single film/episode Mark watched / Mark unwatched wherever it shows: TV and phone
  episode screens, film details, list row menus and swipe, Home card menus, Search.
- Tests: the gate is false for a server-backed provider (extend an existing repository test).
- **Done** (404c6d91): hidden in film details (TV, phone), TV and phone episode cards, TV list row
  menus, phone swipe (narrower when it has no watched action), TV Home card menus and TV Search
  (phone Search only ever had the show mark). On the phone episode screen a watched episode
  still shows its check on Jellyfin, just not tappable.

## Phase 3: phone long-press menu on list rows

- `MobileCategoryListScreen` rows: long press opens a menu with the same actions as the swipe
  (favourite, mark watched / mark show watched, and any others the swipe offers for that row).
  Reuse the long-press dialog Search already has rather than a new design.
- Live channel rows get it too, with whatever applies to them (favourite).
- The swipe is not changed.
- Jellyfin: the menu has no watched items (Phase 2's gate).
- **Done** (be1e5e40): Search's dialog moved to a shared `ItemActionsDialog`; rows open it on long
  press (TalkBack: "More actions"). Channels: favourite (plus Remove from Recent in Recent).
  Films and Recent episodes: Mark watched / unwatched and favourite. Shows: Mark show watched /
  unwatched and favourite. Not yet checked on the phone emulator.
