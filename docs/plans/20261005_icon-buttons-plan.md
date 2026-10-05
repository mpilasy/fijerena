# Icon Buttons Plan

**Status:** In progress (written 2026-10-05). Phases 1–2 built, not yet seen on a device; Phases 3–4 next.

## Goal

Action buttons are **icons** again, app-wide. On TV the button's name shows **only while it is
focused**; on mobile an icon carries its name as a content description and shows it in a tooltip
on long-press. Decided by the user on 2026-10-05 (option 2 of three: icons only, icons with the
label on focus, or text where unclear).

The UX overhaul (`docs/plans/archive/20261003_ux-overhaul-plan.md`) replaced icons with always-on
labels as an audit fix (A-10, L-6, F-MD-2) without that being put to the user. This plan undoes
the look and keeps what those phases fixed: aligned row slots, focus order, entry focus, the
long-press row menu.

## The component

### TV: `TvIconAction` (`tv/.../ui/components/buttons/`)

- At rest: a round icon button, `TvDimensions` icon size, the resting container of
  `CinemaSecondaryButton`.
- Focused: the same button widens to show its label beside the icon (`AnimatedVisibility`,
  expand horizontally, `CinemaAnimation` durations; no animation on low-end chipsets, see AGENTS.md
  "Avoid complex animations on mid-range TV chipsets"). Focused colours as today.
- State lives in the icon: filled vs outlined star, check vs empty circle; the label names the
  action ("Favourite", "Watched").
- Danger variant (Remove, Delete) uses the error tint, outlined, as `ProviderDangerButton` does.
- One component replaces `LabelledActionButton` (`DetailsActions.kt`) and `OsdButton`
  (`TvPlayerControlsOverlay.kt`) and the text buttons on rows.
- Rows keep a fixed slot per action (the A-10 fix: columns line up) — a slot is the resting icon's
  width; the focused label may overlap into the gap, not push neighbours. Settle overlap vs push
  on the TV emulator in Phase 1.

### Mobile: icons with tooltips

- Material 3 `IconButton` inside `TooltipBox` (plain tooltip on long-press), `contentDescription`
  = the label, for TalkBack.
- Replaces text buttons in rows and top bars (Sources' Use, Edit Source Logins' Make main /
  Remove, details action rows).

## Scope

Becomes an icon (with label on focus / tooltip):

| Area | TV | Mobile |
|---|---|---|
| Player controls (OSD) | `TvPlayerControlsOverlay` `OsdButton`: Channels, ★, Subtitles, Audio, Quality, More | already icons; check |
| Details heroes | `MovieDetailsScreen`, `EpisodeSelectionScreen` (`LabelledActionButton`: Favourite, Watched, More; not Play) | action rows |
| Sources rows | `ProviderSelectionScreen` Use / Guide / ⋮ (T4) | Use (M3) |
| Edit Source → Logins | Make main, Remove (`ProviderLoginsSection`) — and move them to the right of the row, one focus stop per button, no dead row stop | Make main, Remove |
| Guide sources rows | per-row actions in `TvEpgManagementScreen` / `GuideSettingsRows` | same |
| Headers / toolbars | TV Guide header, category screen's Search / TV Guide buttons, `SectionRootButton`, Device info / Diagnostics Refresh | top-bar actions (already icons) |

Stays text (see Decision 1):

- Dialog buttons (OK, Cancel, Delete in a confirmation).
- A screen's single main action and form submits: Play / Resume, Save connection, Add Source,
  Add login, Sync Data Now, Export / Import, Join, Retry on error screens.
- Value rows and pickers in Settings (a setting's name and its value are text by nature).

## Phases (one commit each)

| Phase | Work |
|---|---|
| 1 | `TvIconAction` + mobile tooltip icon; the Edit Source Logins rows on both (smallest real use); check look, overlap vs push and focus on the TV emulator |
| 2 | TV player controls and details heroes |
| 3 | Sources rows and guide-source rows, TV and mobile |
| 4 | Headers and toolbars; remove `LabelledActionButton`, `OsdButton`, now unused strings; focus walks re-recorded |

Each phase: strings stay (they become labels / content descriptions), focus walks in
`scripts/focus-walks/` re-recorded where the focus order changes, docs (FEATURES,
NAVIGATION_GUIDE, AGENTS.md rule replacing "labelled slots") updated in the same commit.

## Decisions (2026-10-05)

1. **Icons with the label on focus (TV) / tooltip (mobile), app-wide** — user, option 2.
2. **What stays text** (user accepted the proposal, 2026-10-05): dialog buttons, a screen's single
   main action and form submits, setting value rows — the "Stays text" list above.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1 | Done: `TvIconAction`, mobile `IconAction`, Edit Source Logins rows on both; focus walk `edit-source.txt` updated by hand; not yet seen on a device | 696bab1d (branch) |
| 2 | Done: TV OSD buttons and details actions (`LabelledActionButton` now delegates to `TvIconAction`) show their label on focus; mobile player and details were already icons (Play and Category stay text, main actions); not yet seen on a device | |
| 3 | Not started | |
| 4 | Not started | |
