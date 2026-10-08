# TV UI audit and polish

**Status:** Findings reviewed with the user (decisions recorded); Phase 1 in progress. Plan written 2026-10-07 at the user's request ("the UI still looks like
crap on the TV … walk through the whole app and audit every single section and every single
widget … and make it better"; then "first make a plan"). Waits for the user's go-ahead.

## Goal

Every TV screen and widget looks finished: aligned, readable at 3 m, consistent from screen to
screen, with nothing cut, crammed or floating. Functionality is not in scope (the user finds it
solid); nothing here changes what a screen does, only how it looks — except where a layout is so
broken that fixing it needs a small behaviour change, which is then called out.

## Scope

- The TV app (`tv/`) and the shared components it draws with (`core:ui`). Not the phone.
- Every destination, every state that looks different (loading, empty, error, focused, selected,
  long text, no artwork), every dialog, overlay and panel.
- Default look first (theme "Deep Night", look and feel "Material", UI scale 100 %, English). Then
  a spot check of the other themes and looks and of the largest UI scale, and one screen per
  language (French, Malagasy) for text that no longer fits.

## Inventory (what gets audited)

| # | Screen / state | Source |
|---|---|---|
| 1 | Profile picker ("Who's watching?") | `feature/profile/ProfilePickerScreen.kt` |
| 2 | Home: header (clock, source pill, icons), section tiles, Continue Watching, Channels, Favorite movies / shows; empty source; Jellyfin sign-in panel; source picker dialog | `feature/contentselection/` |
| 3 | Live TV browse (categories + channels), its header, row actions menu | `feature/category/` (`TwoColumnLayout`, `StreamList`, `CategoryList`) |
| 4 | Live TV preview (video, info under it, channel panel with tabs) | `LiveTvSplitLayout`, `LiveTvChannelPanel` |
| 5 | Live TV full screen: OSD, channel panel over video, "Tuning …", Up next / Now next | `ui/player/`, `TvPlayerControlsOverlay` |
| 6 | Movies / TV Shows browse | `TwoColumnLayout`, `StreamList` |
| 7 | Movie details (hero, action row, tabs: Cast / Details / Similar / Versions) | `feature/movie/`, `TvDetailHero`, `DetailsActions` |
| 8 | TV show episodes (hero, season tabs, episode cards, episode panel) | `feature/episode/` |
| 9 | VOD player: OSD, seek, track dialogs (audio, subtitles, quality, chapters), stats overlay, Up next card, errors and slow-connection banner | `feature/player/`, `ui/player/` |
| 10 | Search (empty, history, results grouped, filter chips, no results) | `feature/search/` |
| 11 | Search the guide (header, filters, airings, Watch confirmation) | `feature/epgbrowser/` |
| 12 | TV Guide grid (header, channel column, programme cells, details panel) | `feature/epg/TvGuideGrid.kt` |
| 13 | Settings (sections rail + panes): Profiles, Source & guide, Playback, Display, Live sync, Backup & storage, About & advanced | `feature/settings/` |
| 14 | Sources (list, row buttons, overflow), Add / Edit Source (form, logins, guide sources row, sync) | `feature/provider/` |
| 15 | Guide sources (cards, on/off switch, refresh, progress) | `feature/epg/TvEpgManagementScreen.kt` |
| 16 | Live sync, Device info, Diagnostics | `feature/settings/` |
| 17 | Profile page (switch to, developer mode, play next, filters) | `feature/settings/` |
| 18 | Safe mode, Newer data, error states (`TvErrorState`), loading screens | `feature/safemode/`, `ui/components/` |
| 19 | Every dialog: confirmations, pickers (`TvSelectorDialog`, `TvOptionRow`), favourites menu, import dialogs | various |

## What counts as a finding

One line each, with the screenshot it was seen on:

1. **Alignment:** off-centre icons and text, baselines that don't line up, ragged edges.
2. **Size and density:** text too small for 3 m (body under ~16 sp), cramped or empty areas,
   inconsistent paddings for the same element.
3. **Contrast:** text or icons hard to read on their background (backdrop, artwork, panels).
4. **Truncation and overflow:** cut text, cropped logos or artwork, content off screen.
5. **Consistency:** the same thing drawn differently on two screens (cards, buttons, tabs,
   headers, chips, focus look, corner radii, empty states).
6. **Focus:** focus hard to see, focus that jumps, focus look differing between widgets.
7. **Hierarchy:** the eye doesn't land on what matters; labels that say nothing; dev text shown
   to users.
8. **States:** loading, empty and error states missing, ugly or inconsistent.
9. **Wording:** text that's wrong, clumsy, inconsistent in case or term (in English; FR/MG checked
   for fit only).
10. **Motion:** jank, jumps, animations that distract.

Each finding gets a severity: **broken** (unreadable, cut, misaligned enough to look buggy),
**rough** (clearly unpolished), **nit** (small but visible).

Already seen while writing this plan (Home, profile picker, 2026-10-07):
- Home → Channels: channel logos are cropped to 16:9 ("BBC WORLD NEWS" cut to "WORLD NEW", France
  24's logo cut) — logos need to fit, not fill.
- Home → Continue Watching: the episode line is cut ("35m r…").
- Profile picker: small avatars and labels alone in the middle of an empty screen.

## Method

1. **Capture.** I drive the TV emulator screen by screen (scripted D-pad, one driver per device)
   and screenshot every state in the inventory into `/home/tahiry/data/sary/screenshots/tv-audit/`
   (outside the repo), named `NN_screen_state.png`. Sources: jellyxtream (films and shows with
   artwork), iptv and bears (live with and without a guide), njarasoa (Jellyfin) where it differs.
2. **Findings.** Each screenshot reviewed against the list above; findings written into this plan
   under Findings, grouped by screen, with severity. Root causes noted where a shared component
   is to blame (one fix, many screens).
3. **Review with the user.** The findings and the proposed fixes go to the user before any code:
   plain polish (alignment, contrast, spacing, truncation, consistency with an existing pattern) is
   listed as "will fix"; anything that changes a screen's look or layout beyond that (new layout,
   new component style, different colours) is listed as "needs your decision" — per the standing
   rule that look-and-feel changes need the user's say.
4. **Fix**, in phases, shared components first:
   - Phase A — design tokens: type scale, spacing, corner radii, focus look, panel and scrim
     colours (`TvDimensions`, `Spacing`, `TvFocusTokens`, theme), so later fixes have one source.
   - Phase B — shared components: cards (`cards/`), buttons (`buttons/`, `TvIconAction`), tabs
     (`TvSectionTabs`), panels (`TvGlassPanel`), rows (`TvOptionRow`, `StreamList` rows), dialogs
     (`TvSelectorDialog`, `CinemaAlertDialog`), headers, empty / error / loading states.
   - Phase C — per screen, in inventory order, for what Phase B didn't fix.
   - Fixes are split across agents in worktrees by non-overlapping files (a component or a screen
     each); I merge onto main one at a time and re-capture the affected screens.
5. **Verify.** After each phase: re-capture the affected screens and compare with the audit shots;
   run the TV focus walks (`scripts/focus-walks/`, re-recording where a header gains or loses a
   button); ktlint, Android Lint, unit tests, the `scripts/check-*.sh` gates. Then the themes /
   looks / UI-scale / language spot checks.

## Rules that stay

AGENTS.md's TV focus contract; action buttons are icons with the name on focus (never text
buttons without the user's decision); no poster grid for the Movies / TV Shows lists; one commit
per phase on main with its docs; never install, uninstall or clear data on a real device without
asking (emulators only here).

## Findings

Captured 2026-10-07 on the TV emulator (1920×1080), profile atr (developer mode on), theme Deep
Night, look Material, **Text & grid size 60 %** (the setting's default is 80 %), English. Sources:
bears (Live TV, until the user asked to get off it), iptv (Live TV), jellyxtream (films, shows).
Shots: `/home/tahiry/data/sary/screenshots/tv-audit/NN_*.png` (45). Severity: **B** broken,
**R** rough, **N** nit. Not captured: a filled-in TV Guide grid (needs a source with guide data;
bears is off-limits), Live sync's Manage page, Add Source, the profile page, safe mode / newer
data, the Up next card, loading screens.

### Shared causes (one fix, many screens)

| # | Finding | Sev | Seen on | Likely fix |
|---|---|---|---|---|
| X1 | **Sizes:** "Text & grid size" defaults to 80 % and goes down to 40 %; it scales only some screens (browse, home, details), not Settings, dialogs or the player, so the same text is different sizes from screen to screen; at 60–80 % body text is too small for 3 m | B | all | Rework the scale: 100 % default, a narrower range (e.g. 90–120 %), applied to every screen through the type scale and spacing tokens |
| X2 | **Focused list rows:** blue fill with blue (accent) text — low contrast; rating stars and secondary text vanish on it; the focus look differs between widgets (rows: blue fill; cards and settings: white or blue outline; tabs: outlined pill; buttons: paler fill) | B | 05, 06, 07, 12, 14, 25, 28 | One focus look for rows (light surface or outline + white text), applied in `StreamList`, `CategoryList`, channel panel, search results |
| X3 | **Channel logos cropped** to 16:9 (`CinemaThumbnail` always `ContentScale.Crop`): "WORLD NEWS" cut, France 24 cut | B | 02, 05, 06, 12 | Logos (live items) drawn with `Fit` on a neutral tile with padding; posters stay `Crop` |
| X4 | **Truncated second lines** on cards ("35m r…", "Pays et marchés du mo…", "Avengers: Age …") at the card widths used | R | 02, 18 | Two-line titles where it's the title; shorter secondary lines (drop the redundant part) |
| X5 | **Inconsistent selectors:** radio list (Settings pickers), pill pair with a left accent bar (Edit Source), `Switch` (toggles), outlined Material text field (Source Type) | R | 39, 42 | One selected-state style for pills; no Material text field on TV |
| X6 | **Headers differ on every screen:** browse (two round icons glued to a 3-line stacked title + the source repeated top-right), Search ("Search / All Content"), Search the guide (title — source, status text and three icons), Settings (title + avatar · source), Sources (title + "+") | R | 06, 14, 27, 29, 32, 41 | One TV screen header: title (+ subtitle) left, icon actions right, same sizes and spacing |
| X7 | **Empty / error states:** "Error Loading Guide" in red for a source that simply has no channels; "No channels in this category" + "0 streams" on TV Shows; "No guide sources configured" floating at the top | R | 26, 31, 43 | One empty-state component (icon, sentence, optional action), centred; errors only for real failures; wording per section |
| X8 | **Technical wording shown to users:** "streams", "Enter stream name…", "Category: en", "(XTREAM)" in names outside developer mode, "No channels" for shows | R | 25, 27, 28 | Words per section: channels / films / shows; "Search films, shows and channels" |
| X9 | **Heart vs star:** the player's Favorite is a heart; everywhere else favourites are a star (and ratings are also a blue star, confusable with favourite) | R | 10, 21, 25 | Star for favourite everywhere; rating as "8.2" with a neutral icon or none |
| X10 | **Durations:** "01:33:44" on episodes vs "1h 33m" elsewhere | N | 26 | One duration format |

### Per screen

| # | Screen | Finding | Sev | Shot |
|---|---|---|---|---|
| 1 | Profile picker | Small avatars and labels alone in the middle of an empty screen | R | 01 |
| 2 | Home | Section titles small; Live TV tile's live dot invisible; category counts differ from the browse header ("4 categories" vs "7 categories") | N | 02, 13, 14 |
| 3 | Source picker | Rows dark on dark; tiny "Close" text link; no status per source | N | 04 |
| 4 | Live TV preview | Hint "OK Play · OK again Full screen · Hold OK Options" low contrast, floating at the bottom far from what it explains; info under the video sparse (no Now/Next when no guide) | R | 05, 11 |
| 5 | Live TV browse | Two refresh buttons (categories and list); "Recent Categories" looks like a category; super-script quality tags ("UHD ³⁸⁴⁰ᴾ") add noise | R | 06, 07 |
| 6 | Row actions menu | Fine; "Remove from Favorites" in red reads as destructive | N | 08 |
| 7 | Live TV full screen | Channel panel is see-through over the video and mostly empty below its rows; placeholder letter tiles ("A", "9") for missing logos | R | 12 |
| 8 | Live / VOD controls (OSD) | Hard black band at the top (dev line + clock) instead of a gradient; small unlabelled icon row; heart for favourite (X9) | R | 10, 21 |
| 9 | VOD controls | **No title** — only the plot line; **subtitles draw over the controls** (plot, timeline); focus: Down from Pause stays, then lands on nothing visible, then on More (last button), not the first | B | 21, 22, 23 |
| 10 | Stats overlay | Very small monospace text (a developer tool) | N | 24 |
| 11 | Movies / Shows browse | Opens part-scrolled with the first row cut at the top; resume bars sit on the boundary between rows; rows carry the title only (no year / length / rating) | R | 14, 25 |
| 12 | Movie details | **No title when the film has no logo image** (The Matrix); hero ends in a hard horizontal edge instead of fading; boxed "8.2 Community Rating" chip; "Details" tab starts with a lone "jellyxtream"; "More Like This" repeats the "Similar" tab name; Play is paler when focused than unfocused; ○ for "mark watched" is unclear | B | 15–19 |
| 13 | Episodes | Opens scrolled with the show's logo cut at the top; two stacked tab rows of the same style (sections / seasons) | R | 26 |
| 14 | Search | Separate search button beside a field that already searches; results are thin squashed thumbnails with "Category: en" | R | 27, 28 |
| 15 | Search the guide | Search inside its own panel with the "Matched only" chip; header crams status text with three icons | R | 29, 30 |
| 16 | TV Guide (no channels) | Shown as a red error with Retry (X7) | R | 31 |
| 17 | Settings | Panes fill only the top; values in odd columns (source row: "jellyxtream ›", "Oct 7, 2027", "5"); every row has a tiny "This device / This source" badge; "Add profile" a bare text row | R | 32–40 |
| 18 | Sources | Cards end at two-thirds width with icon buttons floating far right, gaps where a button is missing; ⋮ blue while the others are white | R | 41 |
| 19 | Edit Source | Three control styles (X5); "Source Name:" label-colon rows with pencil buttons; "25 Edit"; Cancel / Save small | R | 42 |
| 20 | Guide sources (empty) | Sentence and button at the top of an empty screen (X7) | N | 43 |
| 21 | Device info | "Detected as GENERIC_MOBILE" on a TV (detection, not looks — flagged for a separate fix) | N | 44 |

## Decisions (user, 2026-10-07)

- **Text & grid size (X1):** keep the range (40–100 %) and the 80 % default, but apply it to every
  screen — Settings, dialogs, the player included — so the same text is the same size everywhere.
- **Focused list rows (X2):** light lift + white outline; text stays white, accent only for the
  "current" bar.
- **Headers (X6):** one shared TV screen header — title (+ short subtitle) left, icon actions right.
- **Selectors (X5):** unified — choices as picker rows, Source Type read-only, scope badges out of
  every row.
- Everything else in Findings is plain polish and gets fixed without a further decision, except
  #21 (device detection), which is not a looks issue and is left for a separate fix.

## Fix phases

| Phase | Who | Files (non-overlapping) | Covers |
|---|---|---|---|
| 1 Tokens + shared components | one agent, alone (touches most screens) | theme / `LocalUiScale` plumbing, `TvDimensions`, `Spacing`, new `TvScreenHeader`, new `TvEmptyState`, row focus look, `CinemaThumbnail` logo fit | X1, X2 (the look itself), X3, X6 (component), X7 (component) |
| 2a Browse + Live TV | agent | `feature/category/**` | X2–X4, X7, X8 there; #4, #5, #7, #11; header adoption |
| 2b Details + episodes | agent | `feature/movie/**`, `feature/episode/**`, `TvDetailHero`, `DetailsActions`, `RelatedTitlesRow` | #12, #13, X9 (rating), X10 |
| 2c Player | agent | `feature/player/**`, `ui/player/**` | #8, #9, #10, X9 (favourite star), #7 panel opacity |
| 2d Settings + sources | agent | `feature/settings/**`, `feature/provider/**`, `feature/epg/TvEpgManagementScreen.kt` | #17–#20, X5, header adoption |
| 2e Search, guide, home, profiles | agent | `feature/search/**`, `feature/epgbrowser/**`, `feature/epg/TvGuideGrid.kt` (+ guide screen), `feature/contentselection/**`, `feature/profile/**` | #1–#3, #14–#16, X8 there, header adoption |
| 3 Verify | me | — | re-capture every shot, focus walks, lint / tests, spot checks |

Phase 2 agents start from Phase 1 merged on main; I merge them one at a time and re-capture.

## Progress

| Step | Status | Commit |
|---|---|---|
| Capture | Done 2026-10-07: 45 shots (see Findings for what wasn't captured) | |
| Findings | Done 2026-10-07: 10 shared causes, 21 per-screen rows | |
| Review with the user | Done 2026-10-07: decisions above | |
| Phase 1 — tokens + shared components | In progress | |
| Phase 2a–2e — per area | Not started | |
| Verify + spot checks | Not started | |
