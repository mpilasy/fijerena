# One button look across the app

**Status:** Built 2026-10-16 (Phases 1–3; Phase 4 partly: the profile page and its dialog seen on both emulators). The TV look is not settled — the user does not like it yet; it is changed in `TvButtonStyle` alone.

## Goal

Every text button in the TV and phone apps looks the same: same shape, label always centred, and
one of three fills chosen by what the button does. A button never looks like a settings row.
Icon buttons (`CinemaIconButton`, TV's `TvIconAction`) keep their round look but take their colours
and size from the same style.

The look is defined **once per app**: `ButtonStyle` (core/ui) holds what both apps share — the shape
from the Display style, the three kinds' colours, the outline, the icon buttons' colours — and
`TvButtonStyle` (tv) and `PhoneButtonStyle` (mobile) extend it with each app's sizes, and on TV the
focus and the button area. Every button component reads its app's style, and no call site sets a
colour, shape or border. Changing how buttons look later is an edit to those files.

## Why

The buttons grew one component at a time, with no shared rule:

| App | Component | Uses | Look today |
|---|---|---|---|
| TV | `CinemaPrimaryButton` | 27 | Fixed small corner (ignores the Display style), label at the left when wide |
| TV | `CinemaSecondaryButton` | 37 | Same, grey fill and corner of a settings row: reads as a row |
| TV | `CinemaButton` / `CinemaDangerButton` | 13 / 3 | Fixed small corner; 10 call sites pass their own colours |
| TV | `CinemaDialogActionButton` (core/ui, phone Material) | 22 | Follows the Display style; 21 pass their own colours |
| TV | Raw `Button` in `CacheManagementSection` | 1 | Its own look |
| Phone | `CinemaButton` | 31 | Follows the Display style, theme primary fill |
| Phone | `CinemaOutlinedButton` | 35 | Faint fill and a light outline |
| Phone | `CinemaTextButton` / `CinemaDialogTextButton` | 5 / 30 | No fill, no outline: a coloured word |
| Phone | `CinemaDialogActionButton` | 25 | 11 pass their own colours |
| Phone | Raw `TextButton` / `Button` / `OutlinedButton` | 11 / 1 / 0 | M3 defaults |

## The rules

1. **Shape:** the Display style's button corner (`LocalUiStyle.current.shapes.button`: Standard
   20 dp, Rounded pill, Sharp 2 dp, Balanced 8 dp), everywhere. The style setting already exists;
   the TV components just stop ignoring it.
2. **Label:** centred, at any width. A full-width button centres its label.
3. **Three kinds**, by what the button does:
   - **Main** — the one action the screen or dialog is for (Save, Add, Play, OK): accent fill.
   - **Other** — every other action (Cancel, Back, Switch to this profile, Retry): see D1.
   - **Danger** — deletes or wipes something (Delete profile, Clear data): the error colour (red).
4. Call sites pick a kind, never colours. The ~45 call sites passing their own `colors` or
   `border` are mapped onto a kind.
5. TV focus: the focused button keeps its look and gains a white ring and the style's focus scale,
   so it stands out from the others on the screen.
6. TV stacks of buttons sit in `CinemaButtonArea`: an invisible area half the page wide against its
   right edge; every button in it as wide as the widest label, centred in it.

## Decisions

- **D1 — the "other" button's look.** Decided (outlined): the phone's outlined look, a faint fill with a
  light outline, on both apps. It reads as a button against a card and against a settings row.
- **D2 — dialogs' text-only Cancel.** Decided (a button): becomes an "other" button like the rest, so a
  dialog shows two buttons, not a button and a word.

## Phases

| Phase | Work | Status |
|---|---|---|
| 1 | `ButtonStyle` base and `TvButtonStyle`; TV text and icon buttons built on them, labels centred, `CinemaButtonArea`; TV dialogs use the TV buttons | Done 2026-10-16 |
| 2 | `PhoneButtonStyle`; the phone button (`CinemaDialogActionButton`, moved from core) and the `CinemaButton` family and icon buttons on it; raw M3 buttons moved to the components | Done 2026-10-16 |
| 3 | The call sites with their own colours or border mapped onto a kind; the API dropped `colors` / `border` | Done 2026-10-16 |
| 4 | Walk both emulators (no playback): Settings, Edit Source, dialogs, error screens; Display styles Standard and Sharp; docs (FEATURES, RELEASE_NOTES, CONTRIBUTING rule) | Partly: profile page and delete dialog seen on both; docs and CONTRIBUTING rule done; the rest not walked; TV look to settle |
