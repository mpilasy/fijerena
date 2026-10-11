# Mark a season or a whole show watched

**Status:** Proposed, 2026-10-11. Not started; waiting for the go-ahead.

Today only one episode at a time can be marked watched or unwatched
(`MediaRepository.setWatched`). The user wants to mark a whole season or a whole show watched or
unwatched in one action. **The mark stays per episode:** a bulk mark writes one `watch_state` row
per episode that exists at that moment, so an episode added later has no row and shows as
unwatched.

## Design

- **Repository:** `MediaRepository.setEpisodesWatched(seriesId, season: Int?, watched)`. It
  loads the series' episodes (`getSeriesDetail`, which fetches `get_series_info` when the
  30-day episode cache is empty or stale, so a never-opened show works) and applies the existing
  per-episode `markWatched` / `markUnwatched` + `clearGroupCompletion` to each, in one Room
  transaction. `season = null` means the whole show. No schema change.
- **Toggle wording:** "Mark season watched" / "Mark show watched" when any episode in scope is
  unwatched; "Mark season unwatched" / "Mark show unwatched" when all are watched.
- **Unwatched** clears only the watched flag, as the single-episode action does; resume
  positions stay. **Watched** doesn't add anything to Recent (`lastPlayedAt` stays NULL).
- **Providers:** Xtream (and other local-state providers) only, like the single-episode mark.
  Jellyfin keeps its watched state on the server, and the app has no way to write it, so the
  actions are hidden there.
- **Live sync:** each episode row syncs through the existing `watch_state` triggers; a show of
  200 episodes queues 200 small records. No new sync record type.
- **TMDB siblings:** the same episode in another language or quality variant of the show follows,
  through the existing sibling rules.

## Where it appears

| Place | TV | Phone |
|---|---|---|
| Episode screen, season | Hold OK / Menu on a season tab: Mark season watched/unwatched | Long-press a season chip: same |
| Episode screen, show | An action in the show header's action row: Mark show watched/unwatched | The same action in the header / overflow menu |
| Show row in TV Shows lists, Home's Favourite shows, Search | The row action menu (hold OK / Menu) gains Mark show watched/unwatched | The long-press menu gains the same |

New strings in en/fr/mg (British spelling). Focus walks updated if the TV episode screen's
focus changes.

## Phases

| # | Phase | Size |
|---|---|---|
| 1 | Repository + DAO batch + unit tests (season scope, show scope, a later-added episode stays unwatched, unwatched keeps positions, Jellyfin no-op) | S |
| 2 | TV and phone episode screens (season and show actions) | S-M |
| 3 | Show row menus in lists, Home and Search, TV and phone | S |
| 4 | Emulator check on `jellyxtream` (9 shows) | S |

## Progress

| # | Status | Commit |
|---|---|---|
| 1 | Not started | |
| 2 | Not started | |
| 3 | Not started | |
| 4 | Not started | |
