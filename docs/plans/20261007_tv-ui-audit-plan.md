# TV UI audit and polish

**Status:** Not started. Plan written 2026-10-07 at the user's request ("the UI still looks like
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

(Filled in by the audit.)

## Progress

| Step | Status | Commit |
|---|---|---|
| Capture | Not started | |
| Findings | Not started | |
| Review with the user | Not started | |
| Phase A — tokens | Not started | |
| Phase B — shared components | Not started | |
| Phase C — per screen | Not started | |
| Verify + spot checks | Not started | |
