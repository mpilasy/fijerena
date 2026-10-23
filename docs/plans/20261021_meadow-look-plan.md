# Meadow: a hand-painted colour theme and Look and Feel

**Status:** Built 2026-10-21 (Phases 1–3), checked on the TV and phone emulators.

## Goal

A look inspired by hand-painted animated films (the name in the app is neutral: **Meadow**). The
mockups are on the canvas "Fijerena Meadow look".

- **Meadow dusk** (dark, for everyone): a deep blue-violet sky with a few stars and a moon, dark
  hills, warm dark cards with cream text, amber (lantern) accent and focus.
- **Meadow** (day, light, Expert only like Daylight): a soft blue sky with clouds, green hills,
  cream paper cards, moss-green text, a green accent and a sun-yellow highlight.
- **Meadow Look and Feel:** Klee One titles (hand-written feel), Zen Maru Gothic body (rounded),
  round 20 dp corners, a gentle lift on focus with a soft shadow.

Rules, as for every look: everything through the theme and Look tokens and the style classes; the
design-system gate stays at 0; British spelling; strings in en/fr/mg; the poster background stays
the profile's own switch.

## Design

- **Scenery.** `CinemaThemePalette.scenery: Scenery?` (null: today's ground). `Scenery.Meadow`
  holds the sky's top and bottom colours, three hill colours, and whether the sky has stars and a
  moon. `Modifier.cinemaGround` paints it: the sky as a vertical gradient, the hills as three
  curved bands in the bottom part of the screen. One static draw, no animation. The Console's
  `Scenery.Flat` uses the same field (Console plan, Phase 1).
- **Palettes.** `MeadowDuskPalette` (`meadow_dusk`) and `MeadowPalette` (`meadow`, light,
  `expertOnly`). Contrast as the other palettes: text on cards 4.5:1 or more.
- **Fonts.** Klee One SemiBold and Zen Maru Gothic Regular, Medium and Bold, cut to Latin
  characters (the full files hold Japanese and weigh 4–9 MB; the cut files 24–52 KB). The OFL
  allows a modified version; neither font has a Reserved Font Name. OFL text and licences-screen
  entries as for the other fonts.
- **Look.** `MeadowStyle` (`meadow`): card 20 dp, button 20 dp, chip 20 dp, dialog 24 dp; Zen Maru
  Gothic throughout with Klee One titles (weight 600); focus scale 1.05 with shadow; filled icons.
- **Packaged UI themes.** "Meadow" (`meadow`: Meadow theme + Meadow Look, Expert only) and
  "Meadow dusk" (`meadow_dusk`: Meadow dusk theme + Meadow Look).

## Decisions (2026-10-21)

| # | Decision |
|---|---|
| M1 | Two colour themes (day, dusk) and one Look. Day is Expert only (user). |
| M2 | Neutral names: "Meadow" and "Meadow dusk" (fr "Prairie", "Prairie au crépuscule"; mg "Saha", "Saha hariva"). |
| M3 | Fonts cut to Latin (size). |
| M4 | Artwork stays; the poster background is the profile's own switch. |

## Phases

| Phase | Work | Status |
|---|---|---|
| 1 | `Scenery` (shared with Console Phase 1), the two palettes, names (en/fr/mg), tests | |
| 2 | Fonts (files, OFL, licences screen), `UiFonts.KleeOne` / `ZenMaruGothic`, `MeadowStyle`, name, tests | |
| 3 | Packaged UI themes "Meadow" and "Meadow dusk", screenshots on the TV and phone emulators, docs | |

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. Scenery and palettes | Done (one commit with the other look's Phase 1: they share `Scenery` and the palette file) | ad314a1c |
| 2. Fonts and Look | Done (one commit with the other look's Phase 2) | (this commit) |
| 3. UI themes, screenshots | Done: TV and phone emulators, Meadow dusk with and without the poster background; the moon is placed from the screen's shorter side so it stays behind the phone header | 5bcdc7ed, (this commit) |
