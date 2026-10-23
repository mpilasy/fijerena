# Console: a starship-console colour theme and Look and Feel

**Status:** Built 2026-10-21 (Phases 1–5), checked on the TV and phone emulators. Not done from the first proposal: rail segments in block colours, block tiles with bottom-right labels, the dialog top bar (D12, D13).

"LCARS" is the working name of this plan only. LCARS and Star Trek are trademarks of their owner,
so the app, its strings, its code ids and its store text use a neutral name (D1; release plan U-M4).
This plan says **Console** for that name.

## Goal

A profile can pick a colour theme and a Look and Feel that look like the ship's computer screens in
Star Trek: The Next Generation:

- **Colours:** a black ground. Flat, bright blocks in soft orange, peach, lavender, pale blue, tan
  and salmon. No gradients, no shadows, no glass.
- **Shapes:** pill buttons. Thick bars with large rounded "elbows" frame the screen. On TV the rail
  and the top bar join as one L-shaped elbow.
- **Text:** tall, narrow capitals in a condensed font (Antonio, SIL OFL 1.1).
- **Focus:** a block changes colour or gets brighter. No glow, no scale, no ring.
- **Layout:** coloured panels; a block's label sits right-aligned at its bottom; small decorative
  reference numbers on some blocks.

The work follows the design system: every look comes from the colour theme and the Look and Feel,
through `CinemaTheme` roles and one style class per element kind. No screen gets a value of its own.

Fixed by the user, not open:

- No one-off raw values. `scripts/check-design-system.sh` stays at 0 for every file.
- Actions stay icon buttons. On TV the label shows only on focus. No text buttons in their place.
- No change to TV text size or scale. The type scale sizes stay as they are; only the font changes.
- No poster grid for the Movies and TV Shows lists.
- British spelling. Every new string in `values/`, `values-fr/` and `values-mg/` of `core/ui`.

## Today

### Inputs

- **Colour theme:** `CinemaThemePalette` in
  `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/theme/CinemaThemePalette.kt`. Nine palettes
  (`deep_night` default, `midnight`, `ember`, `aurora`, `graphite`, `amoled_black`, `amethyst`,
  `forest`, `daylight` expert-only). `AllPalettes`, `palettesFor(admin, current)`, `paletteById(id)`
  (retired ids map through `RETIRED_THEMES`). Each palette states every colour. `groundGlow` and
  `groundEnd` give the 2026-10-17 themes a gradient ground.
- **Look and Feel:** `UiStyle` in `.../core/ui/theme/UiStyle.kt`: `UiShapeTokens` (card, button,
  chip, dialog), `UiTypeTokens` (`fontFamily`, weights, tracking, `titleFontFamily`,
  `titleWeight`), `UiDialogTokens` (corner, `DialogPosition`, scrim), `UiGridTokens` (spacing,
  `focusScale`, `focusUsesOutline`, `focusUsesShadow`), `UiIconTokens`. Eight Looks (`material`
  Standard, `cupertino` Rounded, `roku` Sharp, `bravia` Balanced, `modern` default, `cinema`,
  `soft`, `grotesk`), `AllUiStyles`, `styleById(id)` (`serif` retired to `cinema`).
- **Fonts:** `UiFonts.kt` (same folder) loads variable fonts at weights 400–800 from
  `core/ui/src/main/res/font/`. Licences: `licenses/fonts/*-OFL.txt` and one
  `config/aboutlibraries/libraries/<font>.json` plus `config/aboutlibraries/licenses/<font>.json` per
  font, which the in-app licences screen lists (`core/ui/.../licenses/OpenSourceLicenses.kt`).
  Barlow Condensed (one weight, 600) is already in the APK for Sharp's titles.
- **Storage:** `AppSettings.themeId` / `uiStyleId` per profile, synced, exported by
  `SettingsExportManager` (`core/network`). An unknown id falls back in `paletteById` / `styleById`.
- **Picker:** the profile's page: TV `feature/settings/components/ProfilesSettingsCard.kt`, phone
  `feature/settings/ProfileEditScreen.kt`. Each Look row draws itself in its own Look
  (`ProvideLook`, in both apps' `ui/theme/Theme.kt`). `PaletteSwatches` (core/ui components) shows
  four dots per palette.
- **Names:** `theme_*` and `ui_style_*` strings in `core/ui/src/main/res/values*/strings.xml`.

### Token and style layers

- `CinemaTheme.kt` (core/ui theme): `CinemaColorRoles.from(palette)`, `CinemaTypeRoles` (seven
  roles), `CinemaShapeRoles.from(style)` (button, chip, field, card, dialog, control, identity),
  `CinemaBorders.from(style)`, `ProvideCinemaTheme`, `ProvideMediaTheme` (player stays dark).
- Each app builds its type roles from its typography: `tv/.../ui/theme/Type.kt` and
  `mobile/.../ui/theme/Type.kt` (`cinemaTypography(tokens)` with `applyUiTypeTokens` /
  `applyUiTitleTokens`). Theme entry: `FirstVideoPlayerTheme` in each app's `ui/theme/Theme.kt`.
- **Text:** `CinemaText(role, tone)` in `core/ui/.../components/CinemaText.kt`. No caps option.
- **Buttons:** `ButtonStyle` (core/ui theme) with `TvButtonStyle`
  (`tv/.../ui/components/buttons/TvButtonStyle.kt`) and `PhoneButtonStyle`
  (`mobile/.../ui/components/buttons/PhoneButtonStyle.kt`). Main = accent fill; Other = faint fill
  with a light outline; Danger = danger fill. TV focus: solid white (`textPrimary`) with dark
  content, the Look's scale, no ring. `TvIconAction` is a round button that widens to show its label
  while focused.
- **TV focus:** `tv/.../ui/theme/TvFocusTokens.kt` (`focusedContainer`, `focusFill`,
  `focusedRowOutline`, `focusedGlow`, `restingGlow`, `cardFocusGlow` in every Look,
  `cardFocusBorder` only where the Look does not scale). Rows: `TvListRowDefaults`; inputs:
  `TvInputDefaults`; cards: `TvCardLooks` in `TvCard.kt`; generic: `FocusModifiers.kt`.
- **Surfaces and controls:** `ControlStyle` and `CinemaPanel` (`CinemaControls.kt`), `GlassPanel`
  (core/ui), `TvGlassPanel` (tv), `CinemaAlertDialog` (core/ui), phone `CinemaCard`, `CinemaChip`,
  `CinemaTabRow`.
- **Ground:** `Modifier.cinemaGround` in `CinemaGround.kt`: a faint accent wash from the top, or the
  palette's glow and end gradient. Used by `AmbientBackdrop.kt` in both apps.
- **Gradients in chrome today:** `CinemaGround.kt`, `AccentBlock.kt` (TV placeholder tiles),
  `TvNavRail.kt` (overlay scrim), `CategoryList.kt` (panel border shine). Gradients over pictures
  (`GradientOverlay`, `CinemaThumbnail`, `TvDetailHero`, `MobileDetailHero`, Home backdrop in
  `ContentTypeSelectionScreen.kt`, player overlays) are media scrims: they keep text readable on art.
- **TV rail and header:** `TvNavRail.kt`, `TvNavRailDefaults.kt` (rest width = the 56 dp safe
  margin, 200 dp slid out), hosted in `tv/.../navigation/TvSections.kt`; `HideTvNavRail()` on the
  player, Live TV preview, profile picker, Add / Edit Source and safe mode. `TvScreenHeader.kt`:
  one header for every TV screen, inside the safe margins (56 dp sides, 32 dp top).
- **Phone dock and header:** `mobile/.../navigation/MobileDockStyle.kt` (floating bar, the button
  corner, selected tab in a lighter pill) and `MobileBottomBar.kt`. Tab roots use
  `MobileSourceTopBar.kt`. 19 other phone screens use a raw Material `TopAppBar` (the gate does not
  count it).
- **Gate:** `scripts/check-design-system.sh` with `scripts/check-design-system-allowlist.txt`
  (empty) and `scripts/check-button-text-tone.py`. Theme files (`/theme/`), `*Style.kt` and
  `*Defaults.kt` may hold raw values.

Found while reading: README.md → "Artwork and fonts" lists only Manrope, at old paths
(`tv/` and `mobile/` `res/font/manrope_variable.ttf`, `docs/licenses/Manrope-OFL.txt`). The fonts
the Looks use live in `core/ui/src/main/res/font/` with licences in `licenses/fonts/`. Phase 2 adds
Antonio there; the user decides if that commit also lists the other fonts.

## Design

Sections 1–8 are the first proposal (2026-10-20). Where they differ from the Decisions and the
Phases below (2026-10-21), those win: no reference numbers, no `blocks` palette field, no phone
frame, no dialog top bar.

### 1. Colour theme "Console" (id `console`)

A new `CinemaThemePalette`. Values are a first proposal; Phase 1 tunes them against the contrast
tests below.

| Palette field | Role | Value | Colour |
|---|---|---|---|
| `background`, `surface` | ground | `#000000` | black |
| `surfaceVariant` | row at rest | `#14121A` | near black |
| `surfaceLight` | focused row | `#3B3050` | dark lavender |
| `accent` / `accentDark` / `accentLight` | accent | `#FF9966` / `#CC6633` / `#FFCC99` | orange / burnt orange / peach |
| `orange` / `orangeDark` / `orangeLight` | highlight | `#CC99CC` / `#996699` / `#DDBBDD` | lavender |
| `textPrimary` / `Secondary` / `Tertiary` / `Disabled` | text | `#F5E9DC` / `#99CCFF` / `#CC9966` / `#6B5D52` | warm white / pale blue / tan / brown grey |
| `error` | danger | `#CC4444` | red |
| `live` / `success` / `warning` | status | `#FF5533` / `#99CC66` / `#FFCC66` | |
| `glassBackground` / `glassBorder` | glass | `#000000` / `#00000000` | opaque, no edge |
| `scrim` | scrim | `#B3000000` | |
| `groundGlow` / `groundEnd` | ground | null | flat |

Two new palette fields, stated by **every** palette (the design-system rule: no shared defaults):

- `blocks: BlockColors` — the frame colour and four block colours (Console: frame peach `#FFCC99`;
  blocks orange `#FF9966`, lavender `#CC99CC`, pale blue `#99CCFF`, salmon `#FF9999`; tan
  `#CC9966` for reference numbers through `textTertiary`). `at(index)` cycles the four blocks for
  tiles and rail segments.
- `focusFill` — the colour a focused button, chip, tab, rail segment or tile turns. Console:
  `#FFF2E0`. Every other palette states its `textPrimary` here, so today's white TV focus does not
  change.

New roles in `CinemaColorRoles` (`CinemaTheme.kt`): `blocks`, `focusFill`, `onFocusFill`
(`contentOn(focusFill)`). `TvFocusTokens.focusFill` and `TvButtonStyle.focusedContainer` read
`focusFill` instead of `textPrimary`; for every existing palette the value is the same.

Rule for fills: resting pills use only the mid-tone blocks (orange, lavender, salmon, danger red).
The light colours (peach, pale blue) go only on the frame and on text. Then a focused pill
(`#FFF2E0`, luminance about 0.90) stands out against every resting pill (orange about 0.45,
lavender about 0.40): a contrast ratio of about 1.9 or more. A unit test holds every palette to it.

The Console theme alone works with every Look and Feel: it gives colours only.

### 2. Look and Feel "Console" (id `console`)

New tokens in `UiStyle.kt`. Every existing Look keeps its value through the default, so nothing
else changes:

| Token | Type | Console | Others |
|---|---|---|---|
| `UiTypeTokens.labelFontFamily` | `FontFamily?` | `UiFonts.Antonio` | null (as `fontFamily`) |
| `UiTypeTokens.capsTitles` | `Boolean` | true (display, title, heading) | false |
| `UiTypeTokens.capsLabels` | `Boolean` | true (label: buttons, tabs, chips, focus labels) | false |
| `UiGridTokens.focusFill` | `Boolean` | true: focus is a fill change only | false |
| `UiStyle.blocks` | `UiBlockTokens?` | see below | null |

`UiBlockTokens` (null = no blocks): `frameBar` (bar thickness), `elbowOuter` and `elbowInner`
(elbow radii), `segmentGap`, `referenceNumbers: Boolean`. A non-null `blocks` also means flat
chrome: no gradient, glass or glow in chrome, and buttons, chips and tabs filled with block colours.

`ConsoleStyle` values (first proposal):

- `shapes`: button 100 dp (pill), chip 100 dp (pill), card 6 dp, dialog 24 dp.
- `type`: `fontFamily` Inter (body stays readable, D5), `titleFontFamily` and `labelFontFamily`
  Antonio, `titleWeight` 600, `weightRegular` 400, `weightEmphasis` 600, `trackingTitleSp` 0.6
  (capitals need air), `trackingBodySp` 0.
- `dialog`: corner 24 dp, centred, scrim 0.7.
- `grid`: spacing 6 dp, `focusScale` 1.0, `focusUsesOutline` false, `focusUsesShadow` false,
  `focusFill` true.
- `icon`: `FILLED`.
- `blocks`: `frameBar` 16 dp, `elbowOuter` 40 dp, `elbowInner` 16 dp, `segmentGap` 4 dp,
  `referenceNumbers` per D10.

### 3. Text

- `CinemaText` (both overloads) shows the text in capitals when the role's caps token is on
  (`uppercase(locale)` with the app's language; `AnnotatedString.toUpperCase(LocaleList)` for styled
  text). The semantics keep the original text, so a screen reader does not spell words out.
- Text fields never change what the user types. Body and caption stay mixed case (D5).
- Both `Type.kt` files apply `labelFontFamily` to the label slots, as they apply `titleFontFamily`
  to the title slots today. No size changes.
- `UiFonts.Antonio`: Antonio is a variable font with weights 100–700, so it gets its own weight list
  (400–700), not the shared 400–800 one.

### 4. Element kinds

| Kind | File (style class or component) | Console look |
|---|---|---|
| Ground | `CinemaGround.kt` | `blocks != null`: solid `background`, no wash, no glow |
| Text | `CinemaText.kt`, both `Type.kt` | Antonio capitals for titles and labels; Inter body |
| Button (shared) | `ButtonStyle.kt` | Main: accent (orange) pill; Other: lavender pill, **no outline**; Danger: red pill; label black (`contentOn`) |
| Button (TV) | `TvButtonStyle.kt` | focused: `focusFill` with dark label (danger label on a deleting one); no scale, no ring |
| Icon button | `TvButtonStyle.kt`, `TvIconAction.kt`, `PhoneButtonStyle.kt` | lavender pill at rest; on TV it turns `focusFill` and widens to show its capital label (today's rule); phone label in the long-press tooltip as today |
| TV focus tokens | `TvFocusTokens.kt` | `focusFill` on: `focusedGlow`, `restingGlow`, `cardFocusGlow` = `Glow.None`; scale 1.0 |
| Content row (TV) | `TvListRowDefaults.kt` | focused: `surfaceLight` (dark lavender block) and a bright leading cap in `focusFill` in place of the white outline; text stays light (D9); current bar unchanged |
| Settings / input row (TV) | `TvInputDefaults.kt` | same as content rows |
| Picture card (TV) | `TvCardLooks` in `TvCard.kt` | no glow, no scale; a thick solid `focusFill` ring (the `cardFocusBorder` path) (D8) |
| Placeholder tile | `AccentBlock.kt` | solid block colour (`blocks.at(i)`), no gradient |
| Panels and glass | `GlassPanel.kt`, `TvGlassPanel.kt`, `CinemaPanel` in `CinemaControls.kt`, `CategoryList.kt` border | opaque `surface`, no border shine |
| Chips and tabs | TV `TvCardLooks.chip()`, `TvSectionTabs.kt`; phone `CinemaChip.kt`, `CinemaTabRow.kt` | pills; selected = block colour; TV focus = `focusFill` |
| Dialogs | `CinemaAlertDialog.kt` (both apps use it) | black panel; a block-colour bar across the top with a small elbow at its left end; title in capitals in the bar (D13) |
| Dock (phone) | `MobileDockStyle.kt`, `MobileBottomBar.kt` | a bar of pill blocks, not floating; the tab in use in `focusFill` with dark content; others in block colours; elbow at its left end (D7) |
| Rail (TV) | `TvNavRail.kt`, `TvNavRailDefaults.kt` | the left bar of the frame, cut into segments (one per item, `blocks.at(i)`); current segment in accent; focused segment `focusFill`; slid out, segments widen into pill blocks with the label right-aligned at the bottom; solid scrim, no gradient |
| Frame (TV) | new `TvFrame` (`tv/.../ui/components/frame/`) | L-shaped elbow: rail bar + top bar in the safe margins (D6) |
| Frame (phone) | new `PhoneFrame` (`mobile/.../ui/components/frame/`) | elbow caps on the tab roots (D7) |
| Elbow shape | new `ElbowShape.kt` (core/ui theme) | a `Shape` built from `UiBlockTokens`; lives in the theme folder so the gate allows its path code |
| Reference number | new `ReferenceNumber` (core/ui components) | caption role, `textTertiary`, digits from a stable seed, format in a string resource, hidden from screen readers (D10) |
| Block tile label | Home section tiles (`ContentTypeSelectionScreen.kt`), Kids' Home tiles, TV Settings groups | label right-aligned at the bottom of the block (D12) |

Where `blocks` is null, every row of this table reads as today.

### 5. The TV frame

- The frame lives **only in the safe margins**: the left bar in the 56 dp left margin (the rail's
  rest width), the top bar in the 32 dp top margin, joined at the top-left by an elbow. The content
  area does not move or shrink, so no screen's layout or focus order changes.
- The rail is the left bar. Its items are the bar's segments. Left still opens the rail; the rail
  still slides to 200 dp. Focus order in the rail does not change.
- The top bar is not focusable. It ends at the right in a rounded cap with a reference number
  (D10). `TvScreenHeader` stays where it is, under the bar (D6).
- `TvFrame` draws in `TvSections.kt` beside `TvNavRail`, and hides where `HideTvNavRail()` hides
  the rail: the player, the Live TV preview, the profile picker, Add / Edit Source, safe mode.
- Static drawing (`drawBehind` with `ElbowShape`), no animation of its own, so mid-range chipsets
  (Bravia) pay one draw per frame.

### 6. The phone

- The tab roots (Home, Live TV, Movies, TV Shows): `MobileSourceTopBar` gets a top bar with an elbow
  turning down at its left end; the dock gets a bottom bar with an elbow turning up at its left
  end. No full-height left bar, so the list keeps its width (D7).
- Detail, Settings, Search, guide and player screens: colours, pills, capitals and focus only; no
  frame. Their raw `TopAppBar`s take colours from the Material mapping as today.
- `PhoneButtonStyle` follows `ButtonStyle`: pills, Other filled lavender with no outline.

### 7. TV buttons (the look the user dislikes today)

In Console, a TV button is a flat pill in a block colour with its label in black Antonio capitals,
centred. Main is orange, Other lavender, Danger red. Focus turns the whole pill pale cream
(`focusFill`) with a black label: no white slab, no ring, no growth. Icon buttons are lavender
pills that widen into a labelled cream pill on focus. This look lives in `TvButtonStyle` under
`blocks != null`; the other Looks keep today's white focus. If the user likes it, a later decision
can bring flat block buttons to Modern too. This plan does not change Modern.

### 8. Tests (JVM, Robolectric where Compose)

- `StyleByIdTest`, `PaletteByIdTest`: `console` resolves; `AllUiStyles` / `AllPalettes` list it.
- New palette test: every palette states `blocks` and `focusFill`; for every palette, `focusFill`
  against each resting pill fill (accent, highlight, danger) has a luminance contrast of 1.8 or
  more; `onFocusFill` on `focusFill` and `contentOn` on each block meet 4.5:1.
- New `CinemaText` test: capitals for Title and Label under Console, not for Body; semantics keep
  the original text; a non-Console Look shows the text unchanged.
- `ElbowShape` test: the path stays inside its bounds at the radii given.
- `AppSettingsLookTest`: a `console` id survives export and import.

## Decisions (2026-10-21)

The user asked to implement after the mockup review (canvas "Fijerena Console look"). Where the
review said something, it wins over the first recommendation:

| # | Decision |
|---|---|
| D1 | Name **Console** (fr "Console", mg "Konsoly"); ids `console`. |
| D2 | Two free entries (the Console theme with any Look, the Console Look with any theme), **plus** a packaged UI theme "Console" that sets both (docs/plans/20261021_ui-themes-plan.md). |
| D3 | The Look owns the frame's shape and presence; the theme owns its colours. |
| D4 | Antonio (OFL 1.1, variable, 74 KB), unmodified. |
| D5 | **Changed by the review:** capitals only for labels (buttons, tabs, chips, badges). Section names and titles of films, shows and channels are in Antonio, normal case (the first build put card titles, which use the Heading role, in capitals). Body in Inter. Content shown as a label keeps its case: search terms, category names, other copies of a title, the phone Up next title and the TV guide programme category (`CinemaText(keepCase = true)`, found in the phone pass). |
| D6 | TV frame: an L elbow in the safe margins, **thinner than the first mockup** (the review). No screen layout or focus order changes. |
| D7 | **Changed by the review:** no frame on the phone. Colours, pills, capitals and a dock of block colours only. |
| D8 | Picture cards: a solid ring in `focusFill`, no scale, no glow. |
| D9 | Rows: the row turns `surfaceHigh`, text stays light. |
| D10 | **Changed by the review:** no reference numbers. |
| D11 | No sounds. |
| D12 | No bottom-right block labels for now (they need the frame's tiles; a later plan). |
| D13 | Dialogs: a plain black panel with pill buttons (no top bar) for now. |
| D14 | Artwork stays. The poster background is the profile's own switch (UI themes plan, U3). |

## Phases

One commit per phase on `main`. Each phase updates this plan's Progress table and the docs it
affects (FEATURES, RELEASE_NOTES, CONTRIBUTING, design.md, NAVIGATION_GUIDE, README as named). Each phase
runs ktlint, the touched modules' unit tests, `assembleDebug` for both apps, and the gates
(`check-design-system.sh`, `check-button-text-tone.py`, `check-focus-retry.sh`). Emulator steps
are for the lead: switch the profile's theme and Look only; never clear, install or uninstall
without asking. Test sources: jellyxtream for films and shows, iptv for live.

| Phase | Work | Status |
|---|---|---|
| 1 | **Console colour theme.** `ConsolePalette` (black ground, orange accent, lavender highlight, pale blue and tan text); `CinemaThemePalette.focusFill` (null: `textPrimary`, so no other theme changes) and `scenery` (`Scenery.Flat`: a plain ground, no wash); roles `focusFill`, `onFocusFill`; `TvFocusTokens.focusFill` and the card ring read them; `theme_console` (en/fr/mg); tests | |
| 2 | **Antonio and the Console Look.** Font, OFL text, licences-screen entries; `UiFonts.Antonio`; `UiTypeTokens.labelFontFamily` and `capsLabels`; `UiGridTokens.flat` (no card glow, no scale); `ConsoleStyle` (pills, Antonio headings and labels, Inter body); `CinemaText` capitals for the Label role (semantics keep the text); both `Type.kt` read `labelFontFamily`; `ui_style_console`; tests | |
| 3 | **TV frame.** `TvFrame`: the rail's left bar and a top bar joined by an elbow, in the safe margins, in the theme's highlight and accent; drawn only under a Look with a frame (`UiStyle.frame`), hidden with the rail | |
| 4 | **Phone dock.** Under a Look with a frame, the dock's tabs are block colours, the tab in use `focusFill` | |
| 5 | **Packaged UI theme "Console"**, walks and screenshots on the TV and phone emulators; docs | |

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. Console colour theme | Done (one commit with the other look's Phase 1: they share `Scenery` and the palette file) | ad314a1c |
| 2. Antonio and the Console Look | Done (one commit with the other look's Phase 2) | (this commit) |
| 3. TV frame | Done: `UiFrameTokens` / `ConsoleFrame` and `drawConsoleFrame` (core/ui theme), drawn by `TvNavRail` over its edge shade, so it hides with the rail; the items move right of the 10 dp bar inside the 56 dp margin | (this commit) |
| 4. Phone dock | Done: under a Look with a frame, the tab in use is a solid accent block with dark content (`MobileDockStyle`); checked on the phone emulator | (this commit) |
| 5. UI theme, walks | Done 2026-10-21: UI theme 5bcdc7ed; under Console on the TV emulator `parental-controls.txt` (39 steps) and `settings-guide.txt` (18) matched, and a rail walk (avatar, Home → Settings, back to the card) matched; phone emulator Home and dock checked. Phone pass 2026-10-21 (Home, lists, film and series pages, player, Live TV, Search, Settings, profile page, picker, Parental controls) under Console, Meadow dusk, Meadow and the four other UI themes: content no longer in capitals; status bar icons and wrapped facts lines fixed for all looks | (this commit) |

## Risks

- **Trademark.** The look copies a well-known studio design. Neutral names (D1), no studio logos,
  symbols or fan fonts, and no "LCARS" or "Star Trek" in strings, store text or screenshot captions
  keep the risk low. Code comments may say "starship-console look".
- **Font licence.** Antonio must ship unmodified with its OFL text, as the other fonts do. Check
  the copyright line and any Reserved Font Name in its `OFL.txt` at download. OFL fonts are already
  accepted for the F-Droid build (release plan D1); nothing is downloaded at run time.
- **Readability.** Condensed capitals read slower. D5 keeps body text in Inter. Capitals take more
  width than lower case, so labels and titles can truncate: Phase 7 checks each screen's text.
- **Focus visibility.** A fill change is the only focus cue. The contrast test in Phase 1 and the
  ten-foot walk in Phase 7 check it. Light frame colours never fill a resting button, so a
  focused one never looks like a resting one.
- **Focus contract.** The frame takes no content space and is not focusable, and the rail keeps its
  items and order. Focus walks must pass unchanged; a walk that changes is a bug in this work.
- **Overscan.** The frame sits in the safe margins. A TV with overscan can crop its outer edge. It
  is decorative, so a crop loses nothing; the elbow keeps the rail's edge inset.
- **Every palette states two more colours.** Nine existing palettes get `blocks` and `focusFill`.
  `focusFill` = today's `textPrimary` keeps them as they are; their `blocks` show only under the
  Console Look.
- **Screen readers.** Capitals and reference numbers must not reach TalkBack as noise: semantics
  keep the original text, and reference numbers are hidden.
- **Performance.** The frame is one static draw; no new animation. The rail's slide is unchanged.
- **Scope creep on the phone.** 19 raw `TopAppBar`s get no frame (D7). A framed top bar on every
  phone screen would first need one top-bar component and style class: a separate plan.
