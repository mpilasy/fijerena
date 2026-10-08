# Phone home overhaul and bottom navigation

**Status:** Done 2026-10-07 on the phone emulator: Phases 1–3 and 8, the header redesign, docs. Home dropped (Phases 4–6 built, not merged). Known gaps: a source with a native guide but no indexed guide has no TV Guide entry from the Live TV tab; a profile whose filters hide every live category still gets an (empty) Live TV tab; "Update failed" and the Jellyfin sign-in prompt not forced on the emulator. On bears (user allowed it): the header shows "● bears (XTREAM) · Updating…" during a manual sync, then "Updated just now"; switching source takes about 4 s (jellyxtream) and 6 s (bears) from the tap to the new tab.
(`20261007_tv-home-overhaul-plan.md`) adapted for touch, with a bottom navigation bar in place of
the section cards. Implementation waits for the user's go-ahead.

## Problem

Phone home (`mobile/.../feature/contentselection/ContentTypeSelectionScreen.kt`) is a "What do you
want to watch?" title, the Continue Watching shelf, then three 100 dp gradient cards (Live TV /
Movies / TV Shows) with a category count. Same gaps as TV had: nothing live on home, no
favourites, no sign of the source's sync state, and reaching another section always means going
back to Home first.

## Target

```
┌ jellyxtream ▾ ────── 📖 🔍 ◉ ⚙ ┐
│ ● Updated 2 hours ago          │   ← status line under the source name
├────────────────────────────────┤
│ Continue watching              │
│ ▸ [card][card][ca…             │
│ Channels                       │
│ ▸ [card][card][ca…             │   ← last watched, favourites, recent; Now + progress
│ Favorite movies                │
│ ▸ [card][card][ca…             │
│ Favorite shows                 │
│ ▸ [card][card][ca…             │
├────────────────────────────────┤
│ 🏠 Home  📺 Live TV  🎬 Movies  📼 Shows │   ← bottom navigation bar, labels always shown
└────────────────────────────────┘
```

Portrait only (as the phone app is today). No clock (the status bar has one). Backdrop unchanged:
the last-played poster wash (touch has no focus to follow).

### Top bar

The source name stays the title (a picker with two or more sources). Under it, the catalogue sync
status from the shared `sourceSyncStatus` (Phase 1): "Updating…" (dot pulsing at draw time),
"Updated 2 hours ago" / "Updated just now", "Update failed"; nothing for a source that never
synced. Tapping "Update failed" shows the reason (raw detail in developer mode), also with a
single source. Search the guide, Search, profile, Settings unchanged.

### Bottom navigation bar

- **Tabs:** Home, then Live TV / Movies / TV Shows — only the types the active source has. Icon
  with its name under it on every tab (Material's standard for 3–5 tabs; this is navigation, not
  the icon-only action buttons of AGENTS.md).
- **Each tab keeps its place:** Navigation Compose's saved back stacks
  (`popUpTo(Home) { saveState = true }`, `restoreState = true`, `launchSingleTop`). Movies → a film
  → TV Shows tab → Movies tab lands on that film. Tapping the tab you're on goes back to its start
  (pops to its root, list at the top). Back goes up within the tab, then to Home; Back on Home
  leaves the app (as today).
- **Where it shows:** on Home and the section screens (`CategoryList` roots); hidden on details,
  episodes, the player, Live TV full screen, Search, guides, Settings and its screens, sign-in,
  safe mode. The Live TV dock sits above it.
- **Source switch, profile switch, live-sync switch** (Back-Stack Rule 4) clear every tab's saved
  stack and land on Home, so no tab keeps a screen holding the previous source's repository.
- **Single content type:** the bar shows Home + that type; the auto-open on launch stays.
- **Jellyfin sign-in pending:** section tabs disabled until signed in.
- **Section-root button removed on the phone** (`SectionRootAction`, 8 call sites): tapping the
  current tab does the same. TV keeps its button.

### Rows

Same data and rules as TV, phone-sized (`MobileDimensions.continueWatchingCardWidth`, 160 dp):
Continue Watching, Channels (`mergeLiveRow`), Favorite movies, Favorite shows; empty rows hidden;
reloaded on resume. Opening a card pushes inside the Home tab (Back returns to Home, scroll kept):

- Continue Watching / favourites: details or episodes, as today's shelf and the lists route them.
- Channels: Live TV docked on the channel. The dock's list starts on the card's list — Favorites
  for a favourite, Recent for the others (`MobileCategoryListScreen`'s `listSource` initialised
  from `initialCategoryId`; today it always starts on Recent).

**Long-press a card** opens a small bottom sheet:
- Continue Watching: Remove from Continue watching (`removeFromRecent`).
- Channels: Add to / Remove from favourites.
- Favorite movies / shows: Remove from favourites.
The row updates at once; the action syncs like the lists' swipe actions do.

**Pull to refresh** on home starts a catalogue sync of the active source
(`ProviderSyncManager.startManualSync`, the status line shows "Updating…") and reloads the rows.
The spinner ends when the rows have reloaded, not when the sync ends (a bears sync can take
minutes); a sync already running is joined, not started twice.

## Shared code (core)

No Room schema change, no migration, no model change. Moves so both apps use one copy:
`mergeLiveRow` / `LiveRowEntry` and `sourceSyncStatus` / `SourceSyncStatus` from `tv` to
`core:ui` (TV imports them; their tests move with them). Strings reused from the TV work
(`home_source_*`, `home_live_*`, `home_favorite_*`); new ones (tab names if the existing labels
don't fit, sheet actions) in en, fr and mg.

## Phases

One commit per phase on main, each with its docs (FEATURES → Home, NAVIGATION_GUIDE → mobile
back-stack rules and the section-root rule, RELEASE_NOTES) and this plan's Progress.

| Phase | Change | Where |
|---|---|---|
| 1 | Move `mergeLiveRow`, `sourceSyncStatus` and their tests to `core:ui`; TV unchanged in behaviour | `core:ui`, `tv` |
| 2 | Top bar status line; "Update failed" reason on tap | phone home |
| 3 | Bottom navigation bar: tabs, saved back stacks, reselect to root, visibility, switches clear stacks, single type, sign-in; section cards and "What do you want to watch?" removed from home; `SectionRootAction` removed | `MobileNavHost.kt`, new `MobileBottomBar.kt`, phone home, 8 screens |
| 4 | Rows: Channels (dock list from the card), Favorite movies, Favorite shows | phone home, `MobileCategoryListScreen.kt`, `MobileNavHost.kt` |
| 5 | Long-press sheet on home cards | phone home |
| 6 | Pull to refresh (sync + reload) | phone home |
| 7 | Checks on the phone emulator; NAVIGATION_GUIDE's phone rules rewritten for tabs | docs |

## Redesign: no Home on the phone (user, 2026-10-07, after Phase 3)

Seen on the emulator with the bar in place, the user asked whether Home needs to exist at all if
the header is on every tab. Decided: **drop Home and its tab**; **nothing extra on the tabs** —
each section already opens on its Recent list (in progress first) with Favorites one tap away,
and Live TV opens docked on the last channel, so rows would repeat them. **The app opens on the
last tab used** (per profile; first launch Live TV, else Movies, else TV Shows). This supersedes
the Target sketch, the Home tab, the Rows / long-press / pull-to-refresh sections and Phases 4–6
(built on a branch, not merged, dropped).

Phase 8 — drop Home (defaults, not asked; the user can change them):
- **Tabs:** Live TV, Movies, TV Shows — only the types the source has; with a single type, no
  bar. Back on a tab root leaves the app.
- **Header on every tab root:** no back arrow; title = the source name (a picker with two or
  more sources) with the sync status line under it (`MobileSourceStatusLine`, Phase 2); actions:
  the section's own Search and, on Live TV, its TV Guide (as today), then Search the guide (when
  the index is ready), the profile avatar and Settings. If that is more than fits, Search the
  guide moves to Live TV only.
- **What Home did moves to the nav host / tab roots:** resolving the source's content types (the
  tabs), the Jellyfin sign-in prompt and panel (shown in the tab root while sign-in is pending),
  the source picker dialog.
- **Last tab** stored per profile; source, profile and live-sync switches clear every tab's
  stack and open the new profile's last tab (or the first the source has); Remote Stop's
  `onHome` goes to the Live TV tab root.
- **Removed:** the phone home screen and its components (Continue Watching shelf included —
  Continue Watching lives in each section's Recent list).

## Decisions taken (user, 2026-10-07)

Bottom navigation bar (not tiles); each tab keeps its place; labels always shown; section-root
button removed on the phone; pull to refresh = sync + reload; long-press sheet on home cards;
backdrop as is.

## Open points (defaults unless the user says otherwise)

- **Cards opened from Home stay in the Home tab** (Back returns to Home), even a channel or a
  film — the Live TV / Movies tabs keep their own place untouched. Alternative: switch to that
  tab first.
- **Tab icons:** the section icons Home already uses (`CinemaIcons.LiveTv`, `Movie`, `Tv`) and
  a home icon.

## Risks

- Saved back stacks with type-safe routes and the Live TV dock: the dock's player is
  Activity-scoped, so leaving the Live TV tab must stop it as Back does today (checked in Phase 3).
- Rule 6 (section root) and Rule 4 (switches) in NAVIGATION_GUIDE change for the phone; the TV
  rules stay.
- Phone emulator (Pixel_10) needs 4 GB; start it only for the checks.

## Checks

Phone emulator (jellyxtream for VOD rows, iptv for Channels; bears only if asked): tab switches
keep place, reselect to root, Back order, dock above the bar and stopped on tab change, switches
land on Home, long-press actions, pull to refresh shows "Updating…". Never install, uninstall or
clear data without asking.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1 Shared code to core | Done: `core.ui.home` `LiveRow.kt` / `SourceSyncStatus.kt`, tests moved + `SourceSyncStatusTest`; TV only re-imports (compile + unit tests; TV emulator not re-run, no behaviour change) | 52438b1b |
| 2 Top bar status | Done: `MobileSourceStatusLine` under the source name (own minute tick, pulse at draw time), "Update failed" opens a dialog with the reason; the bar grows to 84 dp (`homeTopBarHeight`) while a status shows, so the line keeps a 48 dp touch target. Checked on the phone emulator: bears shows "● Updated 1 hour ago". "Updating…" to check with Phase 6's pull to refresh | 71b8efe4 |
| 3 Bottom navigation bar | Done: one route per tab root (`Screen.LiveTvTab` / `MoviesTab` / `TvShowsTab` — saved stacks are keyed by destination, and the three `CategoryList(type)` entries share one); `MobileBottomBar.kt` + test; dock reports full screen / PiP / landscape split to hide the bar and is stopped on a tab change; switches `clearBackStack` the tabs; `SectionRootAction` deleted. Checked on the phone emulator: tab state kept (Movies → BG ФИЛМ category → TV Shows → Movies back on it), Back → Home, ANT1 docked above the bar and stopped (Idle) on switching to Movies, full screen hides the bar, after switching bears → jellyxtream the Movies tab opens on jellyxtream. The bar is hidden on details, so a tab keeps its root only | 567338c0 |
| 4 Rows | Dropped: built on a branch (e4e9d2bb, f499bbff, 6997c5e2), not merged — Home is gone | |
| 5 Long-press sheet | Dropped: built on a branch (e4e9d2bb, f499bbff, 6997c5e2), not merged — Home is gone | |
| 6 Pull to refresh | Dropped: built on a branch (e4e9d2bb, f499bbff, 6997c5e2), not merged — Home is gone | |
| 7 Checks + nav docs | Done: FEATURES (Phone: no Home), NAVIGATION_GUIDE Rules 2–7 (Rule 7 phone tabs), AGENTS.md Flow, RELEASE_NOTES | this commit |
| 8 Drop Home | Done: tab routes only (`MobileTab` Live TV / Movies / TV Shows), start tab `startTab(AppSettings.lastTab, sections)` per profile, header on tab roots, `startOver()` for every switch, `openLiveTvRoot` for Remote Stop, sign-in panel with no bar, phone home + shelf removed. Checked on the phone emulator: tabs keep state, Back on a tab root leaves the app, cold start on the last tab, source switch with the dock open stops it and lands on the last tab with the new source's sections, njarasoa (Jellyfin) → Movies + TV Shows | 241dd2a3 |
| Header redesign | Done (user: "that header looks like shit"; chose section title + source line): section name over one source line (dot · source · status ▾, tap to switch / see the failure), plain icons — Live TV: Search the guide, Search, avatar; Movies / TV Shows: Search, avatar; avatar sheet (profiles, Settings) replaces the phone profile picker; TV Guide calendar off the tab header; Live TV tab hidden for a synced Xtream source with no live categories. Checked on the phone emulator: Movies (njarasoa, no status), Live TV (iptv: 3 icons, dock on last channel), avatar sheet, jellyxtream → Movies + TV Shows only | 8423a42d |
