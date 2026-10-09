# TV navigation rail

**Status:** Done 2026-10-08 on the TV emulator (jellyxtream and iptv; no bears, at the user's
request). Not checked: the TV Guide's channel column → rail (no guide source on the emulator
without bears), Search the guide with results, Malagasy labels, themes other than Deep Night, the
Shields / Bravia (overscan). Design agreed with the user 2026-10-08: thin rail, faint icons at
rest; Home stays; the TV Guide is not a rail item; each section keeps its place; Search = current
section, first-launch hint, Home's tiles stay.

## Problem

On the TV, the only way between sections is Home: Back out of wherever you are, then pick a tile.
The section-root button (`SectionRootButton`, from 3 screens deep) only goes back to the start of
the *current* section. Getting from a film's details to Live TV is several Backs and a tile. The
phone solved this with tabs that keep their place; the TV needs something that is reachable at any
time with the remote, without costing screen space.

## Design

### At rest

- A column of faint icons on the left edge, inside the screen's existing side margin
  (`Spacing.tvSafeMarginHorizontal`, 56 dp): about 40 dp wide (20 dp icons), starting ~8 dp from
  the edge so a TV with overscan doesn't crop it. It takes **no content width**: every screen
  already keeps that margin empty, so nothing re-flows and no screen needs re-fitting at a large
  Text & grid size.
- Icons at a low alpha (`CinemaAlpha.textDisabled`-ish); the current section's icon full white with
  a small accent bar beside it, so the rail also says where you are.
- About 4–5 cm on a 48" screen at 100 % Text & grid size (scales with it).

### On focus

- The rail slides out over the content (overlay, no push) to ~200 dp: icons + labels, on a
  near-opaque surface; the screen behind dims (scrim).
- Focus lands on the current section's item.
- Up / Down move through the items; OK opens one; **Right** slides it back in and returns focus
  where it came from; **Back** goes to Home (on Home: back to the content), see Back below.

### Items, top to bottom

1. **Profile avatar** — the profile picker (`Screen.ProfilePicker`).
2. **Home** (`Screen.ContentTypeSelection`).
3. **Live TV**, 4. **Movies**, 5. **TV Shows** — only the sections the active source has (like the
   phone's `visibleTabs`).
6. **Search** — see open question Q1.
7. **Settings** — at the bottom, apart from the rest.

The TV Guide stays where it is (header action on Live TV, Search the guide, Home) — not on the rail.

### Reaching it

**Left from the leftmost focusable thing on any screen** with the rail:

| Screen | Leftmost item that hands Left to the rail |
|---|---|
| Home | the first card of each row, the first section tile, the header's first item |
| Live TV / Movies / TV Shows browse | the categories column |
| Movie details / episodes | the first action button, the first tab, the first season chip, the first episode row |
| Search | the search field; the first card of a result row |
| Search the guide | the field; the first result |
| TV Guide | **the channel column only** — Left inside the grid still goes earlier in time (exception 1) |
| Settings | the left pane |
| Sources, Edit Source, Guide sources, Live sync, Device info, Diagnostics | their list rows / sections |

Today several of these stop Left on purpose (`HomeRow` sets `left = FocusRequester.Cancel` on the
first card); those become "go to the rail".

### Where the rail is hidden

- **Player** (`Screen.Player`) and the **Live TV preview layer** (full-screen video, channel panel):
  Left keeps its current job there (preview → browse; the player's own panel). Leaving to browse
  brings the rail back. (Exception 2.)
- First-run and recovery screens: Add Source, Source selection with no source yet, Profile picker
  at launch, Safe mode, Newer data.

### Sections keep their place

Like the phone (`docs/plans/archive/20261007_phone-home-overhaul-plan.md`): each section has its
own saved back stack. Picking Movies, then Live TV, then Movies returns to the film you were on.
Picking the section you are already in goes to its start (what the section-root button does today),
so **`SectionRootButton` goes away**.

- Navigation Compose keys saved stacks by destination: sections get their own root routes. The
  phone already has `Screen.LiveTvTab` / `MoviesTab` / `TvShowsTab` in `core/navigation`; the TV
  reuses them as section roots that host the browse screen, instead of `Screen.CategoryList`
  pushed from Home.
- Rail pick = `navigate(section) { popUpTo(Home) { saveState = true }; launchSingleTop = true;
  restoreState = true }`. Home stays the start destination and is never popped.
- Search the guide, the TV Guide, Settings and Search opened from the rail sit on top of whatever
  section you were in (no saved state of their own), as today.

### Back

- Back inside a section behaves as now until the section's start.
- **Back at a section's start moves focus to the rail** (the Google TV pattern), instead of going
  straight to Home.
- Back on the rail goes to Home; Back on Home leaves the app, as now.

### Headers

The shared header (`TvScreenHeader`) loses the actions the rail now carries: **Search, Settings,
the avatar**. It keeps the title, the source line and the screen's own actions (Refresh, the
guide's day buttons, Search the guide, TV Guide). Home's header keeps the clock and the source
pill.

## Decisions (2026-10-08)

- **Search on the rail** opens Search for the current section (Live TV search in Live TV, Movies in
  Movies…), everything from Home, Settings and other non-section screens — what the header's Search
  does today.
- **First-launch hint:** once per device, the rail slides out on its own with "Press Left for
  sections", then slides back in.
- **Home's section tiles stay** for now; revisit after using the rail.

## Phases

One commit per phase, with docs; emulator check after each (TV emulator, jellyxtream for VOD, iptv
for live; bears only if the user allows).

| Phase | What | Check |
|---|---|---|
| 0 | Spike: measure every screen's real left margin (some may use less than 56 dp); try `androidx.tv.material3` `NavigationDrawer` / `ModalNavigationDrawer` (tv-material 1.0.0-alpha10) against a custom overlay — faint icons at rest inside the margin may need the custom one | Notes in this plan |
| 1 | `TvNavRail` component: rest / expanded states, scrim, focus enter on current item, Right / Back out with focus restore; items from the source's sections; strings en / fr / mg | Preview + Home only |
| 2 | Section roots and saved stacks in `TvNavHost`; rail wired into the nav host scaffold; Home stays start; same-section pick = section start; Back at section start → rail | Walk: Home → Movies → film → rail → Live TV → rail → Movies lands on the film |
| 3 | Left hands over to the rail on every screen in the table; guide exception; rail hidden on player / preview / first-run screens | `scripts/focus-walks/` walk per screen; `check-focus-retry.sh` |
| 4 | Headers drop Search / Settings / avatar; `SectionRootButton` removed (and `sectionRootFor` if unused) | Every header at 60 / 100 / 120 % |
| 5 | Polish and checks: first-launch hint, themes, French and Malagasy labels, 100 % Text & grid size, overscan on the Bravia, Shield check | Screens captured; Shields / Bravia only when the user says |
| 6 | Docs: NAVIGATION_GUIDE (rail, saved sections, Back), FEATURES, AGENTS focus contract (the rail rule: leftmost Left goes to the rail, exceptions), RELEASE_NOTES; archive this plan | — |

## Phase 0 findings (2026-10-08)

- **Margins:** every TV screen pads its content with `Spacing.tvSafeMarginHorizontal` (56 dp):
  Home, browse and the Live TV split, Search, Search the guide, the TV Guide grid, Settings, Sources,
  Add / Edit Source, Guide sources, Live sync, Device info, Diagnostics, the profile picker, safe
  mode. Movie details and episodes keep 56 dp on their content but their hero picture is
  full-bleed: there the rail sits over the picture, so the rail at rest gets a dark left-edge
  gradient behind its icons. No screen needs re-fitting.
- **Drawer:** a custom overlay, not tv-material's `ModalNavigationDrawer` (present in 1.0.0-alpha10).
  Chosen on reading its API, not on a prototype: its collapsed width comes from
  `NavigationDrawerItem`'s 56 dp item plus padding (wider than the margin), its items aren't faint at
  rest, and it has no way to return focus to the item focus came from. The custom one is a `Box`
  overlay: a ~40 dp column at rest, ~200 dp with labels and a scrim on focus.
- **Interface** (committed in Phase 0, `tv/.../ui/components/rail/`):
  - `RailItem` — PROFILE, HOME, LIVE_TV, MOVIES, TV_SHOWS, SEARCH, SETTINGS.
  - `TvNavRailState` — `entry` (focus enters there), `current`, `expanded`, `visible`, `returnTo`,
    `focusRail()`; provided as `LocalTvNavRail` (null without a rail: everything no-ops).
  - `Modifier.leftToRail()` — on a screen's leftmost item: Left → rail, and it becomes `returnTo`.
  - `HideTvNavRail()` — hides the rail while composed.
  - `TvNavRail(state, items, profileInitial, onSelect, modifier)` — the rail (placeholder in
    Phase 0).
- **Navigation rules** (lane N):
  - Sections are the tab routes the phone already uses (`Screen.LiveTvTab`, `MoviesTab`,
    `TvShowsTab`), each hosting `TvCategoryGridScreen`; Home navigates to them too, so the tiles
    and the rail share one saved place per section.
  - Picking a section: drop Settings-tree entries on top (Settings, Sources, Add / Edit Source,
    Guide sources, Live sync, Device info, Diagnostics — they aren't a section's place), then
    `navigate(section) { popUpTo(Home) { saveState = true }; launchSingleTop = true;
    restoreState = true }`. Picking the current section: back to its start.
  - Home: `popBackStack(Home, inclusive = false, saveState = true)`.
  - Search: `Screen.Search(current section's type, or ALL)` on top. Settings: on top (or back to
    Settings' start if already in it). Profile: the profile picker.
  - `current`: the section whose tab route is in the back stack; HOME on Home; SETTINGS while a
    Settings-tree screen is on top; SEARCH while Search is on top.
  - Back at a section's start (browse, no layer open) → `focusRail()`; Back in the rail → Home;
    on Home, Back in the rail returns to the content.
  - Hidden: Player, the Live TV preview layer, Profile picker, Add Source, Safe mode, Newer data,
    and whenever there is no source.
  - The section-root button and `sectionRootFor` go in lane N (the rail's current-section pick does
    its job); screens drop their `sectionRoot` parameter there, so lanes A–D don't touch it.

## How the work runs

Five steps; phases 3 and 4 touch the same screen files, so they are split by screen group, not by
phase. Agents work in their own worktrees and run the gates (compile, ktlint, lint, unit tests,
`check-*.sh`); the coordinator merges one lane at a time and runs the emulator checks and focus
walks (one TV emulator).

1. **Phase 0** alone. Ends by fixing the rail's interface (items, current item, the "Left goes to
   the rail" hook) in this plan.
2. **Phases 1 and 2** in parallel: lane R (the rail component) and lane N (section roots, saved
   stacks, Back, the scaffold), against the interface from step 1.
3. **Phases 3 + 4** in parallel, one lane per screen group, each doing the Left hand-over and the
   header cleanup for its screens:
   - **A** Home;
   - **B** Live TV / Movies / TV Shows browse, the TV Guide (exception);
   - **C** movie details, episodes, Search;
   - **D** Settings, Sources, Edit Source, Guide sources, Live sync, Device info, Diagnostics,
     Search the guide.
4. **Phase 5** checks, after every lane is merged.
5. **Phase 6** docs and archive.

## Progress

| Phase | State | Commit |
|---|---|---|
| 0 | Done (findings above; interface in `ui/components/rail/`) | |
| 1 | Done (lane R) | 5c5002dd → main |
| 2 | Done (lane N) + fixes from the emulator walk: the first-launch hint was spent while the profile picker hid the rail (now shown after 1.5 s on screen, recorded once shown in full); the profile item said "A" (now the name and colour); Home's source switch kept the old source's saved sections (now cleared). Walked: hint, rail at rest, Back at a section start → rail, Right back, sections keep their place, Live TV preview hides the rail, video stops on leaving and doesn't restart on return, Back on the rail → Home | a665803d, 5998e5fb → main |
| 3 + 4 A Home | Done; walked: Left from the first card of each row, the first tile and the header → rail, Right back; `home.txt` 14 steps and `home-rows.txt` 22 steps, 0 mismatches | 8ad0557f → main |
| 3 + 4 B browse, guide | Done + fix: the categories column's pane kept Left (`tvPane(leftToRail = true)` lets it through). Walked: Left from a category → rail, Right back to that category; header Refresh only. Guide channel column not walked (no guide on the emulator without bears) | b3e40fb3 → main, 633822fd |
| 3 + 4 C details, episodes, Search | Done; walked: details Resume and the first tab → rail and back; Movies → film → TV Shows → Movies lands on the film; Search from the rail searches the section; `search.txt` rail steps OK (3 mismatches on recent-search data). Fix: from Settings or Search over a section, its item returns to where you were | 405139b9 → main, 633822fd |
| 3 + 4 D Settings and the rest | Done; walked: Settings groups and Sources rows → rail and back; `sources.txt` 8 steps, 0 mismatches; `settings.txt` first 34 steps OK (then a profile page that needs another source) | e4b2c2a7 → main |
| 5 | Done: rail at 100 % Text & grid size and in French; rail sections follow Home's source switch (fix, 633822fd); emulator restored (English, 60 %) | |
| 6 | Done: NAVIGATION_GUIDE (Rule 6, episodes, Home, panes, adding a screen), AGENTS, FEATURES, RELEASE_NOTES | |

## Risks

- **Focus everywhere.** Phase 3 touches every TV screen's left edge, the part of the focus contract
  that took the most fixing. Each screen gets a focus walk before the phase lands.
- **Accidental opening.** One Left too many at the start of a row slides the rail out. It is
  overlay and dimmed, so it is obvious, and Right undoes it.
- **Saved stacks and the preview layer.** The Live TV preview is a layer inside browse, not a
  destination; leaving Live TV from the rail must stop the preview the way leaving it today does
  (`closeLivePreview`), and coming back must not resume video on its own.
- **Process death.** Saved section stacks must come back after process death the way the phone's
  do; checked with `adb shell am kill` in Phase 2.

No model, database or migration change; TV UI and `TvNavHost` only (the tab routes already exist in
`core/navigation`).
