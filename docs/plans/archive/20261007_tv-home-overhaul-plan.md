# TV home overhaul

**Status:** Closed and archived 2026-10-07 at the user's request; the Shield check (recomposition, time to first focus) was never done. Before: All seven phases done on the TV emulator. Checked on bears 2026-10-07 (user asked): Channels row "Last watched • Now: <programme>" with its progress bar (ANT1+ SPORTS 1), pill "Updating…" during a 15 s manual sync, then "Updated just now" on its own. Left: a Shield check (recomposition, time to first focus); "Update failed" not forced. Design agreed with the user 2026-10-07 (direction A, all four extras, two
favourites rows, Live row = last + favourites + recent, no "See all" card, "Updating…" kept, zap
list = the card's list). User said start 2026-10-07. TV only; mobile home unchanged.

## Problem

TV home (`tv/.../feature/contentselection/ContentTypeSelectionScreen.kt`) is three 240 dp gradient
cards (Live TV / Movies / TV Shows) carrying an icon, a subtitle and a category count, with the
Continue Watching shelf under them.

1. **Continue Watching is below the fold.** At default scale the screen is 960×540 dp; margins,
   the display-size title, the cards and the shelf add up to about 690 dp, so the most likely action
   (resume) is partly off screen.
2. **The cards say almost nothing.** The category count is developer information.
3. **Nothing live on home.** No last channel, favourite channels or "on now", though Live TV is the
   main use.
4. **The backdrop is static**: the last-played poster, whatever has focus.
5. **The source pill hides sync state**: no sign of a sync running, failing or being stale.

## Target layout

```
Fijerena  20:41                  [source ▾ ● Updated 2 h ago] [📖][🔍][◉][⚙]
[📺 Live TV] [🎬 Movies] [📼 TV Shows]                       ← 64 dp tiles
Continue watching
 ▸ [card] [card] [card] [card]
Live
 ▸ [Last watched · BBC One · Now: News ▬▬▬──] [fav] [fav] [recent] …
Favourite movies
 ▸ [card] [card] …
Favourite shows
 ▸ [card] [card] …
```

Budget at 960×540: margin 32 + header 40 + tiles 64 + Continue Watching ~240 → the first row is
fully visible and the next row's title and card tops peek. The page scrolls as focus moves (a
scrolling `Column`, not a lazy list — see Phase 3); the tiles scroll off on Down and come back on
Up, the header stays put (clock and source status always in view).

### Header

- "Fijerena" at title size (was display size), the clock beside it ("20:41", system 12/24 h,
  ticks once a minute, not per frame).
- Source pill: name ▾ plus a status dot and short text, from the active `ProviderEntity`
  (`lastSyncedAtMs`, `lastSyncError`, re-read on resume). Not `SyncManager`: that is live sync, not
  the catalogue sync. Nothing observable says a catalogue sync is running now (WorkManager job or
  TV's in-process refresh), so "Updating…" needs a small `StateFlow` in core/network
  (`ProviderSyncManager`), added in Phase 1 (see Decisions):
  - green dot, "Updated 2 h ago"
  - animated dot, "Updating…" (animation read in `graphicsLayer`, no recomposition per frame)
  - red dot, "Update failed"; the picker dialog shows the friendly error, raw message beneath it in
    developer mode
  - one source only: the pill still shows status but is not clickable and cannot take focus
- Search the guide, Search, profile, Settings unchanged.

### Section tiles

Live TV / Movies / TV Shows as one row of 64 dp tiles: gradient kept, icon + label. Category count
only in developer mode. Empty Live TV tile dimmed and unfocusable, as today. Single content type
still opens it straight away (`hasAutoSkippedSingleContentType`, unchanged).

### Rows (each hidden when empty)

1. **Continue watching**: today's `TvContinueWatchingShelf` (`getContinueWatchingItems`, 10 max).
2. **Live**: last channel first (`getLastItemId(LIVE_TV)`, labelled "Last watched"), then favourite
   channels (`getFavoritesForContentTypeSuspend(LIVE_TV)`), then recent channels
   (`getRecentItemsSuspend(LIVE_TV)`); deduplicated, ~20 max. Card: logo on a dark surface, name,
   "Now: <programme>" + progress from the guide index (`getNowPlayingFromIndex`), no Now line without
   an index. Now lines refresh each minute while home is resumed. OK pushes
   `CategoryList(initialCategoryId = recent | favorites, initialStreamId)` with the preview, so the
   zap list is the card's own list (see Decisions) and Back returns to home.
3. **Favourite movies**: `getFavoritesForContentTypeSuspend(MOVIES)`, opens details.
4. **Favourite shows**: same for TV_SHOWS, opens episodes.

Each row loads on its own, off the main thread, and appears when ready (no shimmer wall). Jellyfin
sign-in still replaces everything under the header.

### Focus (AGENTS.md focus contract)

1. Open: first Continue Watching card; else first Live card; else first focusable tile.
2. Up/Down between rows lands on the column that row had last (default first card). Up from the top
   row goes to the tiles, Up from the tiles to the header.
3. Left on a row's first card and Right on its last do nothing.
4. Down from the header goes to the tile focused last (today's F-H-2 rule); Down from the tiles goes
   to the first row.
5. Back from anything opened on home returns to that control: `NavReturnFocus` keys become
   `<row>:<itemId>`, restoring page scroll, row scroll and column.
6. Back on home does nothing (unchanged).

### Backdrop follows focus

Focused card's art behind the page via `AmbientBackdrop`: ~250 ms settle delay so fast scrolling
doesn't flicker, 300 ms crossfade. Live cards use the channel logo blurred (the guide index stores
no programme images; adding them would mean an EPG index schema change, out of scope). Focus on tiles or header keeps the last backdrop. Image swaps at draw
time, not by recomposing the page.

## Phases

One commit per phase on main; each updates this plan's Progress and the affected docs (FEATURES →
Home, NAVIGATION_GUIDE → return-focus list, RELEASE_NOTES). New strings in en, fr and mg.

| Phase | Change | Where |
|---|---|---|
| 1 | Header: smaller title, clock, source pill status (dot + text, single-source pill unfocusable, error in the picker) | `ContentTypeSelectionScreen.kt` (TV), strings, `ProviderSyncManager` (running-sync flow) |
| 2 | Section tiles replace the hero cards; counts developer-only; existing tile focus rules kept | same, `TvDimensions` |
| 3 | Page becomes a column of rows; Continue Watching moves into it; row focus memory, entry focus, return keys `<row>:<itemId>` | same, `TvContinueWatchingShelf.kt`, `NavReturnFocus.kt` |
| 4 | Live row: data merge, channel card with Now + progress, minute refresh, open preview | new `TvLiveRow.kt`, `TvNavHost.kt` (new `onLiveChannelSelected`) |
| 5 | Favourite movies and Favourite shows rows | new row composable (reuses the shelf card), `TvNavHost.kt` |
| 6 | Backdrop follows focus | `AmbientBackdrop.kt`, home |
| 7 | Rewrite `scripts/focus-walks/home.txt`; run it and the emulator checks below | focus walk |

## Core and data impact

No Room schema change, no migration, no model change: every row and the pill read existing
columns and `MediaRepository` / `EpgIndex` APIs. Core touched only for new strings
(`core/ui/src/main/res`, en/fr/mg) and the "Updating…" flow in `ProviderSyncManager`.

## Decisions (user, 2026-10-07)

- **"Updating…" kept**: Phase 1 adds a running-sync `StateFlow` to core/network
  (`ProviderSyncManager`), set by the catalogue worker and TV's in-process refresh.
- **Live card zap list = the list the card came from**: "Last watched" and recent cards open with
  `initialCategoryId = CategoryViewModel.RECENT_CATEGORY_ID`, favourite cards with
  `FAVORITES_CATEGORY_ID`; `LiveTvSplitLayout` already turns these into `ChannelContext.Recent` /
  `Favorites`, so Up/Down in full screen zap through that list. A channel both favourite and recent
  shows once, as a favourite (first occurrence wins in the merge).

## Risks

- Home once recomposed at 60 fps (live pulse read in the body, measured on the Shield). Pill
  animation and clock must not reintroduce that: check recomposition counts on the Shield after
  Phases 1 and 6.
- Several queries at home open (favourites ×3, recent, now-playing). Load per row, lazily; measure
  time-to-first-focus on the Shield before and after Phase 4.
- More focusable rows mean more focus edges; Phase 7's walk is the gate.

## Checks

User OK'd installing on the TV emulator after every phase (2026-10-07). TV emulator first (jellyxtream for VOD rows, iptv for the Live row and guide Now lines), scripted
focus walk + text checks. Then a Shield for performance. Never install, uninstall or clear data on a
device without asking.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1 Header | Done: `ProviderSyncRunner.running` (count per source, tested); pill status + clock + smaller title; "just now" under a minute (reuses `live_sync_just_now`); checked on the TV emulator (Updated / just now / none for M3U, picker, home walk 0 mismatches). "Updating…" and "Update failed" not seen live: a jellyxtream sync takes 0.6 s, failure not forced | |
| 2 Section tiles | Done: `SectionTile` (64 dp, icon + name, count developer-only, subtitles and their strings removed); tiles at the top, shelf right under them; Left/Right pinned at the row's ends (Left from Movies with Live TV dimmed fell into the shelf); checked on the TV emulator (iptv: home walk 0 mismatches; jellyxtream: dimmed Live TV skipped, shelf fully visible). Down from a tile still lands geometrically on the shelf (Phase 3) | |
| 3 Rows scaffold + focus | Done: `HomeRow` (title + `LazyRow`, `focusRestorer` with first-card fallback, ends cancel) carries Continue Watching; tile row `focusRestorer` + `focusGroup` (without `focusGroup` the restorer never ran on a plain `Row`); entry focus on the first Continue Watching card (waits for the shelf's first load); page stays a scrolling `Column`, not a lazy list: at most five rows, and every row stays composed for Back's hand-back. Checked on the TV emulator (jellyxtream: entry, Up/Down memory both ways, Back to the opened card; iptv: home walk 0 mismatches) | |
| 4 Live row | Done: `mergeLiveRow` (tested: order, dedupe as favourite, last watched found among favourites, cap 20), `TvLiveRow` card on `HomeRow`, Now lines refreshed on the minute, reload on resume, `onLiveChannelSelected` → `CategoryList(initialCategoryId = favorites/recent)`, return key `live:<id>`, entry focus falls back to it. Row title "Channels" (the tile already says Live TV). Checked on the TV emulator, iptv: last watched first, reload on resume, Recent vs Favorites list on open, Back to the card, home walk 0 mismatches (new start). Now line not seen: iptv's channels aren't in the guide index and bears is off-limits | |
| 5 Favourites rows | Done: `TvFavoritesRow` on `HomeRow` (16:9 art, title with badge, year • genre), loaded with the Live row and on resume, `onFavoriteSelected` → `MovieDetails` / `EpisodeSelection` as the lists route them, return keys `favMovie:` / `favShow:`, entry focus falls through to them. Checked on the TV emulator, jellyxtream: Favorite movies (The Godfather) and Favorite shows (Agatha Christie's Marple, favourited for the check — left in place) open details / episodes, Back to the card, Up returns to Continue Watching's last card | |
| 6 Backdrop follows focus | Done: rows' `itemModifier` records the focused card's art (`onFocusChanged`, blank art ignored); `HomeBackdrop` reads it in its own scope, waits 250 ms, hands it to `AmbientBackdrop` (which already blurs and crossfades); tiles/header keep the last one. Checked on the TV emulator (API 36), jellyxtream: 101 Dalmatians → Marple art on focus. Shield recomposition count not measured (no Shield in this round) | |
| 7 Focus walk + checks | Done: new `home-rows.txt` (jellyxtream: row ends, Up/Down memory across three rows and the tiles, header Down), recorded then checked, 17 steps 0 mismatches; `home.txt` (iptv, Channels row first) 11 steps 0 mismatches. Left: Shield (recomposition count, time to first focus) and the Now line on a source whose channels are in the guide | |
