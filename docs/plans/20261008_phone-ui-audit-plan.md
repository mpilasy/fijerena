# Phone UI audit and polish

**Status:** Plan written 2026-10-08 at the user's request ("do the same exercise on the phone" — the
TV audit, `archive/20261007_tv-ui-audit-plan.md`); capture and findings done; review with the user in progress.

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

Captured 2026-10-08 on the phone emulator (Pixel_10, 1080×2424, Android 17), profile atr (developer
mode on), theme Deep Night, English, system font 100 %. Sources: jellyxtream (films, shows), iptv
(live). Shots: `/home/tahiry/data/sary/screenshots/phone-audit/NN_*.png` (24). Not captured: the
landscape dock split, PiP, the TV Guide grid (needs a source with guide data — bears), Add Source,
Live sync's page, the track sheets, safe mode. Severity: **B** broken, **R** rough, **N** nit.

### Shared causes

| # | Finding | Sev | Seen on | Likely fix |
|---|---|---|---|---|
| P1 | **List rows:** every title in accent blue; tiny pill-shaped thumbnails; resume bars along the card's bottom edge across the full width | R | 01–03, 10, 22, 24 | Row titles in the primary text colour (accent only for "current"/playing); 16:9 thumbnails with the resume bar on them; channel logos whole (`CinemaThumbnail` LIVE_TV, already fit since the TV audit) |
| P2 | **Wording:** "7 streams" / "1 streams" / "3 streams", "Search streams…", "Category: en" | R | 01–03, 09, 10, 22 | Channels / films / shows counts (plural-aware), "Search films, shows and channels", the category name alone |
| P3 | **Category chips** each carry a ☆ favourite toggle — noisy, and a small touch target next to the chip's own | R | 01–03, 24 | Favourite a category from a long-press / menu, not a star on every chip |
| P4 | **Rating vs favourite:** rating is an orange ★, favourites a white ☆, the player's favourite a heart | R | 04, 06, 08 | As on TV: rating as plain "8.2/10", favourite a star everywhere |
| P5 | **Player** (same as TV before): no title in the controls; subtitles draw into the control bar; heart | B | 08 | Title above the seek bar; lift subtitles while controls show; star |
| P6 | **"This device / This source" badges** on every Settings row | R | 13–15 | Said once per section (as decided for TV) |
| P7 | **Text action buttons** where the rule is icons: Device info / Diagnostics (Refresh, Share, Clear log), Settings' Export / Import pills, Edit Source's "Edit" | R | 15, 17, 18, 20, 21 | Icon actions in the top bar with long-press tooltips (Refresh, Share, Clear); Export / Import as rows |
| P8 | **Empty states** at the top of an empty screen (guide sources) | N | 19 | One centred empty-state component, as on TV |
| P9 | **Developer suffix** "(XTREAM)" / "(REMOTE_M3U)" cuts the source line short ("jellyxtream (XTRE…") — developer mode only, but it hides the status | N | 01, 22 | Drop the suffix from the line (keep it in the picker) |

### Per screen

| # | Screen | Finding | Sev | Shot |
|---|---|---|---|---|
| 1 | Movies / TV Shows tabs | Two rows of pills (Recent / Favorites / Recent Categories, then categories with ☆) before any content | R | 01–03 |
| 2 | Movie details | Top bar says "Movie Details" while the title is printed on the poster's corner; facts line sparse (no length); ○ for "Mark as watched"; Details tab shows "TMDB: 603" and a full-width "Category: 4k" pill | R | 04, 05 |
| 3 | Episodes | Facts line runs off the right edge ("5 seasons · €…"); season chips cut at the edge without a hint; episode title "Over" in the Resume button reads oddly ("Resume: S02E10 · Over (19:04)") | R | 06 |
| 4 | Player | See P5; status bar icons dimmed over the controls; small "Ends at" in accent | B | 07, 08 |
| 5 | Search | Field with two circled icons inside it; "Search All Content" title; results as P1/P2 | R | 09, 10 |
| 6 | Profile sheet | Fine | — | 11 |
| 7 | Source picker | Rows fine; "Close" small text link | N | 12 |
| 8 | Settings | P6; "Auto-resume, Recent row size and stream format are set per source" is a note styled as a tappable row; avatar in the top bar repeats the sheet | N | 13–15 |
| 9 | Sources | Each row has both ⋮ and › (two ways to the same place) | N | 16 |
| 10 | Edit Source | A proper touch form (fields), fine; "25 [Edit]" for the Recent row size; m3u8 / ts and m3u_plus / simple as loose chips rather than a segmented control | R | 17, 18 |
| 11 | Guide sources | **The bottom bar shows** though it's reached through Settings (which hides it); empty state at the top | R | 19 |
| 12 | Live TV dock | **A sideways swipe on a row opens its delete action instead of switching Recent ↔ Favorites, and the open delete stays open after the dock closes**; "LIVE PREVIEW" label; letter tiles for missing logos | B | 22–24 |
| 13 | Device info | "Detected as GENERIC_MOBILE" (detection, not looks — as on TV) | N | 20 |

## Progress

| Step | Status | Commit |
|---|---|---|
| Capture | Done 2026-10-08: 24 shots | |
| Findings | Done: 9 shared causes, 13 per-screen rows | |
| Review with the user | In progress | |
| Fixes | Not started | |
| Verify + spot checks | Not started | |
