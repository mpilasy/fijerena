# Indigo: a packaged UI theme

**Status:** Done 2026-11-09, on local main, not pushed. The user took every recommended answer on 2026-11-09 (see Answers).

## Goal

The user (idea notes, 2026-11-01): a streaming-radio style UI theme. "Indigo ground ~#1C1E3A, white
accent with navy text on it, yellow badges, pill chips with labels in capitals with letter-spacing,
12 dp cards, optional monospace meta line." Free fonts only, no logos. The name is neutral: no
product name in the UI or in public files.

This plan adds one colour theme, one Look and Feel (if IN-Q2 says so) and one packaged UI theme, on
TV and phone. Layout does not change: a theme changes colours, fonts, corners and focus only.

## What exists (survey 2026-11-09)

- **Colour themes.** `CinemaThemePalette` in core/ui `theme/CinemaThemePalette.kt`; each palette is
  a `val …Palette` (for example `RoyalPalette`, `ClayPalette`) listed in `AllPalettes`.
  `onAccentOverride` sets the text on the accent (Clay uses it); `focusFill` / `onFocusFill`
  default to `textPrimary` / `background`; `groundGlow` / `groundEnd` give a ground with depth.
  `orange` becomes the `highlight` role (`CinemaColorRoles.from`, `theme/CinemaTheme.kt`).
- **Looks.** `UiStyle` in `theme/UiStyle.kt`: `UiShapeTokens` (card, button, chip, dialog),
  `UiTypeTokens` (fonts, weights, `trackingTitleSp`, `trackingBodySp`, `labelFontFamily`,
  `capsLabels`), `UiDialogTokens`, `UiGridTokens`, `UiTopBarTokens`, `spacing`. All in
  `AllUiStyles`. `ModernStyle` has 12 dp cards and 8 dp chips; `MaterialStyle` 16 dp cards and
  20 dp chips; `GrooveStyle` has pill chips (100 dp).
- **Caps and tracking today.** `capsLabels` puts every `TextRole.Label` in capitals (buttons, tabs,
  chips, badges): `lookCase()` in `components/CinemaText.kt`. Labels take `trackingBodySp`
  (`applyUiLabelTokens`). There is no token for chip labels alone.
- **Chips.** TV `TvSelectableButton` (role `TvInputDefaults.chipLabelRole` = Label); phone
  `ui/components/chips/CinemaChip.kt`. Both take the corner from `UiShapeTokens.chip`
  (`CinemaCornerRadius.small`), which also shapes buttons, small cards, fields and controls.
- **Badges.** `CinemaBadge` in `components/LanguageBadge.kt`: `BadgeKind.Neutral` and `OnCard` fill
  with `surfaceRaised` / `surfaceHigh`; `Success` and `Warning` carry `TextTone.OnAccent`. No
  badge takes `highlight` today.
- **Meta lines.** TV `TvDetailHero` (`metaLine`), phone `ui/components/MetaLine`. `UiFonts.Code` is
  the system monospace (log text only).
- **Packaged UI themes.** `PackagedUiTheme` and `UiThemes.packaged` in core/network `UiThemes.kt`.
  `uiThemeNameRes()` (`theme/UiThemeNames.kt`) takes the colour theme's name when its id equals the
  UI theme's id. Pickers list `AllPalettes`, `AllUiStyles` and `UiThemes.packaged` with no other
  code change (`ThemeSettingsCard`, both `SettingsScreen`s, phone `ProfileEditScreen`).
- **Fonts.** `UiFonts` (`res/font/`), licences in `licenses/fonts/<font>-OFL.txt` and
  `config/aboutlibraries/libraries/<font>.json` + `licenses/<font>.json`.
- **Strings.** `theme_<id>` and `ui_style_<id>` in core/ui `res/values`, `values-fr`, `values-mg`.
- **Tests.** core/ui `theme/`: `NewPaletteContrastTest` (4.5:1, on accent, on focus fill),
  `PackagedUiThemesTest`, `PaletteByIdTest`, `StyleByIdTest`, `UiStyleSpacingTest`,
  `UiThemeOrderTest`. Gate: `scripts/check-design-system.sh`.
- **Prior art.** Crimson, Forest, Aurora and Royal came in one commit (f3209d635, release
  0.9.20261101054935): `UiThemes.kt`, `ButtonStyle.kt`, `CinemaThemePalette.kt`, `UiStyle.kt`, three
  `strings.xml`, `NewPaletteContrastTest`, FEATURES, RELEASE_NOTES.

## Decisions (proposed)

| # | Subject | Decision |
|---|---|---|
| IN-D1 | Ids | Colour theme, packaged UI theme and string key share one id (`indigo`, or the IN-Q1 pick), so `uiThemeNameRes()` shows the colour theme's name. |
| IN-D2 | Palette | `IndigoPalette`: background `#1C1E3A`, surface `#24274A`, surfaceVariant `#2D3158`, surfaceLight `#383C66`; accent `#FFFFFF`, accentLight `#C9CCF2`, `onAccentOverride` `#1C1E3A` (navy on white); `orange` (highlight) `#FFD23F` yellow; text `#FFFFFF` / `#C3C5E0` / `#8E91B8` / `#5D6088`; live and error a warm red, warning amber (not the badge yellow). Values are a start; `NewPaletteContrastTest` decides. Dark, not admin only. |
| IN-D3 | Ground | A soft glow (`groundGlow` `#2A2E5C`, `groundEnd` `#14152B`), as Royal. |
| IN-D4 | Focus (changed by the user, 2026-11-09, after the TV walk) | A yellow focus fill with navy text on it: a Main button at rest is white, so a white focus did not show. A focused card's glow is yellow too (user, 2026-11-09). A deleting button's label stays navy on the yellow: red does not read on it. The current chip or tab keeps its bar in the white accent. |
| IN-D5 | Chip labels | New `UiTypeTokens.chipLabel: UiChipLabelTokens?` (`caps`, `trackingSp`); null keeps today's look in every other Look. Read in one place (a chip label style in core/ui) by `TvSelectableButton` and phone `CinemaChip`. Buttons keep their case. Capitals use the app language's locale (`lookCase`). |
| IN-D6 | Strings | `theme_indigo` (+ `ui_style_<id>` for a new Look) in en, fr and mg, British spelling. Malagasy name by the user (IN-Q1). |
| IN-D7 | Docs | FEATURES (Themes, Look and Feel), design.md (Palettes row; Look list), RELEASE_NOTES, CONTRIBUTING only if IN-D5 adds a rule. |

## Open questions

| # | Question | Options |
|---|---|---|
| IN-Q1 | Name (en / fr / mg) | (a) **Indigo / Indigo / Indigo** (a colour name, as Slate and Crimson). Recommended. (b) **Nocturne / Nocturne / Alina**. (c) **Tide / Marée / Onja**. The user gives or confirms the Malagasy word. |
| IN-Q2 | Look and Feel | (a) **A new Look ("Dial", id `dial`)**: Modern's fonts (Manrope titles over Inter, no new font), 12 dp cards, pill chips (100 dp), chip labels in spaced capitals (IN-D5), centred dialogs, focus lift with shadow. Recommended: no current Look has 12 dp cards and pill chips. (b) Modern as it is (8 dp chips, no caps). (c) Material as it is (16 dp cards, system font). |
| IN-Q3 | Yellow badges | (a) **New palette field `badgeFill` (null: today's grey) with `onBadge` from `contentOn()`; Indigo sets it to the yellow for `BadgeKind.Neutral` and `OnCard`.** Recommended. (b) Only the yellow `highlight` for live and accent blocks, badges stay grey. (c) Yellow text on the grey badge (`TextTone.Highlight`). |
| IN-Q4 | Monospace meta line | (a) **Leave it out.** Recommended: smallest change. (b) New `UiTypeTokens.metaFontFamily` with the system monospace (`UiFonts.Code`), read by `TvDetailHero` and `MetaLine`. (c) As (b) with a shipped free font (IBM Plex Mono or JetBrains Mono, OFL, + licence files). |
| IN-Q5 | Pill shape reach | `UiShapeTokens.chip` also shapes buttons, small cards and fields. (a) **Accept it, as Groove does.** Recommended. (b) Split the token (a chip-only radius): a wider change across all Looks. |

## Answers (user, 2026-11-09)

| # | Answer |
|---|---|
| IN-Q1 | (a) Indigo in English, French and Malagasy. |
| IN-Q2 | (a) A new Look, Dial (id `dial`). |
| IN-Q3 | (a) A new palette field `badgeFill`; Indigo sets it to the yellow. |
| IN-Q4 | (a) No monospace meta line. |
| IN-Q5 | (a) Pill shapes also reach buttons, small cards and fields, as in Groove. |

## Phases

| # | Work | Done when |
|---|---|---|
| 1 | Palette (IN-D2, IN-D3), badge fill if IN-Q3 (a), packaged entry in `UiThemes.packaged`, strings en/fr/mg; contrast test entry | Unit tests pass; the theme shows in both pickers |
| 2 | The Look (IN-Q2) and the chip label token (IN-D5); meta font if IN-Q4 (b/c); `UiStyleSpacingTest` value | Unit tests and the design-system gate pass; no other Look changes |
| 3 | Emulator walks (Checks), fixes, docs (IN-D7) | Walk items all pass on TV and phone; docs updated in the same commits |

## Progress

| Phase | Status |
|---|---|
| 1 | Done (6536bd3d9) |
| 2 | Done (02e62d8b4) |
| 3 | Done (0675c2cf0, 2fbe1665e, and the yellow buttons and card glow) |

## Checks

- **Tests.** `NewPaletteContrastTest` with `IndigoPalette`: text on all surfaces, navy on the white
  accent, indigo on the focus fill, and (IN-Q3 a) `onBadge` on the yellow at 4.5:1; also success and
  warning badge text (`TextTone.OnAccent` is navy here). `PackagedUiThemesTest` (name test adds
  `indigo`), `StyleByIdTest`, a new test that `chipLabel = null` keeps label case and tracking.
- **Gates.** ktlint, `check-design-system.sh` (no new raw values outside `/theme/` and style
  classes), core/ui tests, compile of `:tv` and `:mobile`, `lintDebug` before release.
- **Emulator walk, TV (in English, then Malagasy).** Pick the UI theme: Theme and Look and Feel rows
  lock. Home, Live TV, Guide, Movies and TV Shows lists, details, Search with filter chips, Sports,
  Settings and a dialog. Each state: chip at rest, focused, current, focused and current; tab
  focused and current; main and other buttons; badges on cards and on details; poster background on
  and off; empty and loading states. Capitals and tracking do not clip a long Malagasy chip label.
- **Emulator walk, phone.** Same screens and states (tap, no focus), top bar, bottom sheets and
  dialogs, chips in Search and category lists, a text field (pill shape, IN-Q5).
- **Switch back.** From Indigo to Custom and to Royal: nothing keeps Indigo colours or caps.

## Risks

- A white accent equals the white focus fill: a selected item may look focused on TV. IN-D4 and the
  walk cover it.
- Yellow badges on every card can be loud next to posters; IN-Q3 (b) is the fallback.
- Pill chips make buttons and fields pills too (IN-Q5).
- Capitals with tracking make chip labels wider; long Malagasy labels may cut off.
- `onAccent` navy also colours text on the success and warning fills; the contrast test must check
  both.
