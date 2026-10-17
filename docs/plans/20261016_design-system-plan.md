# Design system: every look from the theme and Look and Feel

**Status:** Built 2026-10-16. Every file in both apps and core is at 0 in `scripts/check-design-system.sh` (allow-list empty, 2,700+ sites moved); checked on both emulators in Daylight with Standard and Serif. Left: a full walk of every screen in each theme and Look and Feel (Phase 8), the TV button look the user has not settled.

## Goal

Every pixel of both apps — buttons, text, labels, titles, text fields, rows, cards, chips, tabs,
switches, dialogs, borders, dividers, badges, progress, the dock and the rail — takes its look from
two inputs and nothing else:

- the **colour theme** (`CinemaThemePalette`: Deep night, AMOLED black, Amethyst, Teal…);
- the **Look and Feel** (`UiStyle`: Standard, Rounded, Sharp, Balanced).

So:

1. Changing one attribute of a Look and Feel option, or adding a colour theme, is one edit in one
   file, and every screen follows.
2. A new button, field or screen looks right without knowing about either: it uses a component,
   and the component reads its style.
3. Nothing in feature code names a colour, size, weight, corner, border or font.

## Where it stands (inventory, 2026-10-16)

Buttons are done (`ButtonStyle` + `TvButtonStyle` / `PhoneButtonStyle`, b7fc9e3d). Everything else
is partly tokenised: colours come from the palette and corners from the style through
`CinemaCornerRadius`, but feature code still decides much of the look itself.

| Raw in feature code | TV | Phone | Shared |
|---|---|---|---|
| Text fields (`OutlinedTextField`, `TextField`) | 4 | 31 | 0 |
| Cards / clickable surfaces | 17 | 15 | 3 |
| Switches, checkboxes, radio buttons | 1 | 18 | 0 |
| Chips, tabs | 0 | 10 | 0 |
| Material dialogs (not `CinemaAlertDialog`) | 0 | 8 | 3 |
| Dividers | 1 | 6 | 0 |
| `.border(` drawn by hand | 12 | 3 | 2 |
| Progress / sliders | 10 | 21 | 1 |
| `Text(` calls (each picks its own style and colour) | 181 | 599 | 12 |
| Ad-hoc text style (`typography.x.copy(…)`) | 37 | 3 | 0 |
| Fixed weights (`FontWeight.Bold`…) | 11 | 12 | 2 |
| Fixed text sizes (`N.sp`) | 1 | 1 | 1 |
| Always-round shapes (`CircleShape`) | 22 | 13 | 5 |
| Raw `Color.White` / `Color.Black`… | 3 | 3 | 8 |
| `N.dp` literals in feature code | 9 | 25 | 7 |
| `MaterialTheme.colorScheme.*` read directly | 45 | 244 | 0 |

Look and Feel values: `dialog.position` is applied by `CinemaAlertDialog` (Rounded: bottom sheet,
Sharp: full screen) — on TV too, until D3; the icon stroke width was never applied and was removed (Material icons have fixed line widths); `grid.focusUsesShadow`
is applied to cards only, not buttons. Palette colours with hard-coded defaults shared by every
theme: success, warning, error, live, and all four text colours.

## Architecture

Four layers. Only layer 1 decides looks; each layer reads only the one above it.

1. **Inputs** — `CinemaThemePalette` and `UiStyle`. Adding a theme or a Look and Feel option = one
   new entry. Every palette states every colour (no shared defaults), so a light theme is possible.
2. **Semantic tokens** — one `CinemaTheme` entry point in core/ui, derived from the inputs and
   provided through composition locals:
   - `colors`: roles, not hues — background, surface, surfaceRaised, onSurface, text (primary,
     secondary, tertiary, disabled), accent, onAccent, danger, success, warning, live, outline,
     divider, scrim, focusRing.
   - `type`: roles — display, title, heading, body, bodySmall, label, caption — built from the
     style's weights and tracking (today's `Type.kt`, both apps, made the only source).
   - `shapes`: button, chip, field, card, dialog, and `round` (avatars, dots, icon buttons) — which
     follows the style too (D2).
   - `borders`: hairline, outline, focus widths; `spacing`; `motion`; `elevation` / glow.
   TV and phone each extend it with their sizes and, on TV, focus (today's `TvFocusTokens`,
   `TvDimensions`, `MobileDimensions` folded in).
3. **Component styles** — one class per kind of element, built like `ButtonStyle`: a base in core/ui
   from the semantic tokens, a TV and a phone subclass for sizes and focus. Kinds: text (`CinemaText`
   with a role), button (done), icon button, text field, row / card / panel (`SurfaceStyle`), chip,
   tab, switch / checkbox / radio, dialog (honours `dialog.position`), divider, badge, progress,
   dock (`MobileDockStyle`, folded in), rail.
4. **Components** — one per kind and platform, reading its style. Feature code uses these only.
   A new element kind gets a style class and a component first.

**Enforcement** — `scripts/check-design-system.sh`, run in CI like the other `check-*` gates: fails
on a raw Material component, a colour literal, a `.dp` / `.sp` literal, a `FontWeight`, a
`RoundedCornerShape` / `CircleShape`, a `Text(` with its own colour or style, or
`MaterialTheme.colorScheme` outside the theme, style and component files. An allowlist starts with
today's offenders and must only shrink; it is empty when the plan is done. AGENTS.md gets the rule.

## Decisions

- **D1 — text roles.** Decided: the seven above. Every `Text` picks one; colour comes with the role
  (secondary text is a role, not a colour argument).
- **D2 — round things under Sharp.** Decided: icon buttons, chips and badges follow the style
  (square on Sharp); avatars and colour dots stay round in every style, as identity marks.
- **D3 — dialog position.** Decided: honour it on the phone (Rounded: bottom sheet, Sharp: full
  screen); TV keeps every dialog centred, since a bottom sheet doesn't fit the remote.
- **D4 — focus glow.** Decided: buttons and rows glow on focus where the style says so
  (`focusUsesShadow`), as cards already do.
- **D5 — gate strictness.** Decided: the gate lands in Phase 1 with the allowlist, so nothing new
  slips in while the rest is migrated.
- **D6 — order.** Decided: shared tokens and gate first, then one kind of element per phase, phone
  and TV together, each phase walked on both emulators in two Look and Feel options and two colour
  themes.

## Test themes

Two colour themes added to prove the inputs drive everything: **Forest** (dark, green accent) and
**Daylight** (the first light theme: dark text on near-white). Every screen must read correctly in
both; Daylight exposes anything still assuming a dark background.

Look and Feel **Serif** proves the same for shapes and type: a serif font for every piece of text
(`UiTypeTokens.fontFamily`) and cut corners — an octagon-like outline — for every corner
(`UiShapeTokens.cut`, built by `cornerShape()` / `UiShapeTokens.shape()`). Any corner or font that
does not change under Serif is still hard-coded.

## Execution

The shared layer is built first: `CinemaText` (roles and tones), `CinemaTextField`, `CinemaCheckbox`,
`CinemaSwitch`, `CinemaRadioButton`, `CinemaDivider`, `CinemaPanel` (core/ui `components/`),
`ControlStyle`, the media roles (`onMedia`, `mediaScrim`, `clear`). The gate counts raw `Text(` and
direct palette reads too (2,213 sites in 103 files). Then the feature code is moved file by file,
split by area across up to four helpers, each file to 0 in the allow-list.

## Phases

| Phase | Work | Status |
|---|---|---|
| 1 | `CinemaTheme` semantic tokens (colours, type, shapes, borders, spacing, motion) from palette + style; palettes state every colour; `check-design-system.sh` with allowlist; AGENTS rule | Built 2026-10-16: `CinemaTheme.kt`, one Material 3 mapping for both apps (`CinemaMaterialMapping.kt`), both app themes on `ProvideCinemaTheme`; gate in CI with 519 sites in 81 files allowed |
| 2 | Text: `CinemaText(role)`; the ~790 `Text(` calls and 40 ad-hoc styles move to roles; fixed weights and sizes gone | Done 2026-10-16 |
| 3 | Surfaces: rows, cards, panels, glass (`SurfaceStyle`); hand-drawn borders and dividers | Done 2026-10-16 |
| 4 | Inputs: text fields, switches, checkboxes, radio buttons, sliders | Done 2026-10-16 |
| 5 | Chips, tabs, badges, progress; round shapes per D2 | Done 2026-10-16 |
| 6 | Dialogs and sheets (`DialogStyle`, D3); remaining raw Material dialogs | Done 2026-10-16 |
| 7 | Navigation: dock (phone), rail and headers (TV), icon buttons; focus glow (D4); `icon.strokeWidth` | Done 2026-10-16 |
| 8 | Allowlist empty; walk both emulators in every Look and Feel × two colour themes; docs (FEATURES, RELEASE_NOTES, AGENTS) | Partly: allow-list empty, docs and AGENTS rule done; Daylight × Standard / Serif walked on a few screens per app |
