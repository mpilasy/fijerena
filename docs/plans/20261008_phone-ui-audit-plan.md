# Phone UI audit and polish

**Status:** Plan written 2026-10-08 at the user's request ("do the same exercise on the phone" — the
TV audit, `archive/20261007_tv-ui-audit-plan.md`); capture starting.

## Goal

Every phone screen and widget looks finished: aligned, readable, consistent from screen to screen,
comfortable for a thumb, nothing cut or crammed. Functionality is not in scope; a layout broken
enough to need a small behaviour change is called out.

## Scope

- The phone app (`mobile/`) and the shared `core:ui` pieces it draws with. Not the TV.
- Every destination, every state that looks different (loading, empty, error, long text, no
  artwork, playing / docked / full screen / PiP), every sheet, dialog and menu.
- Default look first: theme Deep Night, English, system font size 100 %, portrait (the app is
  portrait-locked; the player goes landscape). Then a spot check of the other themes, the largest
  system font size, a narrow phone width (360 dp), French and Malagasy.

## Inventory

| # | Screen / state | Source |
|---|---|---|
| 1 | Bottom bar (tabs, selected state, insets) | `navigation/MobileBottomBar.kt` |
| 2 | Tab header: section title, source line (status, picker), Search the guide, Search, avatar; profile sheet; source picker dialog; failure dialog | `feature/contentselection/MobileSourceTopBar.kt`, `components/MobileSourceLine.kt`, `feature/profile/ProfileSheet.kt` |
| 3 | Live TV tab: categories, channel list, swipe-reveal actions, the dock (docked, Recent / Favorites swipe, full screen, landscape split, PiP) | `feature/category/MobileCategoryListScreen.kt` |
| 4 | Movies / TV Shows tabs: categories, lists, rows | same |
| 5 | Movie details (hero, actions, tabs) | `feature/movie/`, `ui/components/MobileDetailHero.kt`, `MobileDetailMeta.kt` |
| 6 | Episodes (hero, seasons, episode cards, panel) | `feature/episode/` |
| 7 | Player (landscape): controls, seek, track sheets, stats, Up next, errors | `feature/player/` |
| 8 | Search (empty, history, results, filter chips, no results) | `feature/search/` |
| 9 | Search the guide, TV Guide grid, programme sheet | `feature/epgbrowser/`, `feature/epg/` |
| 10 | Settings and its pages: profiles, source & guide, playback, display, live sync, backup, about; Sources; Add / Edit Source; guide sources; Device info; Diagnostics | `feature/settings/`, `feature/provider/`, `feature/epg/` |
| 11 | Safe mode, Newer data, loading, error and empty states | `feature/safemode/`, shared |
| 12 | Every dialog, bottom sheet and menu | various |

## What counts as a finding

As on the TV — alignment, size and density, contrast, truncation, consistency, hierarchy, states,
wording, motion — plus the phone's own:
- **Touch:** targets under 48 dp, controls too close together, actions only reachable by a hidden
  gesture, nothing that says a swipe exists.
- **Insets:** content under the status bar, the gesture bar or the bottom bar; the dock or a sheet
  covering what it shouldn't.
- **Reach:** primary actions far from the thumb when they could be closer.

Severity: **B** broken, **R** rough, **N** nit.

## Method

1. **Capture** on the phone emulator (Pixel_10, 1080×2424) screen by screen with taps and swipes,
   screenshots to `/home/tahiry/data/sary/screenshots/phone-audit/` (outside the repo). Sources:
   jellyxtream (films and shows), iptv (live), bears where a guide is needed (allowed by the user
   for the TV audit; asked again before use).
2. **Findings** into this plan, shared causes first, then per screen.
3. **Review with the user** before code: plain polish fixed without asking; anything that changes
   a screen's look or layout beyond that listed as "needs your decision".
4. **Fix** in phases: shared pieces first (header, rows, focus/press states, empty states,
   thumbnails), then per area by agents in worktrees on non-overlapping files; merged one at a
   time onto main and re-captured.
5. **Verify:** re-capture, unit tests, ktlint, Android Lint, the `scripts/check-*.sh` gates, spot
   checks (themes, font size, width, languages).

## Rules that stay

AGENTS.md; action buttons are icons (a tooltip on long-press on the phone) — never text buttons
without the user's decision; no poster grid for Movies / TV Shows lists; the phone's tabs, header
and Home-less layout as decided on 2026-10-07; PiP only while live video plays; one commit per
phase with its docs; never install, uninstall or clear data on a real device without asking.

## Findings

(Filled in by the audit.)

## Progress

| Step | Status | Commit |
|---|---|---|
| Capture | In progress | |
| Findings | Not started | |
| Review with the user | Not started | |
| Fixes | Not started | |
| Verify + spot checks | Not started | |
