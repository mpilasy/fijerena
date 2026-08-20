# TV Focus & Input Overhaul

Audit of every D-pad-reachable surface in `:tv`, and a delivery plan to fix two systemic defects:

1. **Focus is not visible.** On dialogs and settings cards you cannot tell which item the D-pad is
   on. Several surfaces have *no* focus visual at all.
2. **Focus is not retained.** Committing a field, or picking an option, destroys the focused node
   and drops focus to the window root — the next D-pad press restarts from the top of the screen.

Both trace to a small number of shared patterns, not to 40 independent bugs. Fixing the patterns
fixes every call site at once.

**Scope:** `:tv` only. `:mobile` behaviour must not change.
**Status:** steps 1-6 of 8 landed; steps 7-8 pending.
**Audited:** 2026-08-20, against `c99ffbd3`.

---

## 1. Root causes

### R1 — Focused container is *darker* than resting container

`CinemaSecondaryButton` (`ui/components/buttons/CinemaButton.kt:130-133`):

| state | container | palette "cinema" | palette "midnight" |
|---|---|---|---|
| resting | `CinemaSurfaceVariant` | `#1E2228` | `#121212` |
| **focused** | `CinemaSurface` | `#161A20` | `#0A0A0A` |

Focus makes the button *recede*. The only additive cues are a 1.5 dp accent border and a 1.04×
scale (`TvFocusTokens.focusBorderWidth`, `MaterialStyle.grid.focusScale` in
`core/ui/.../UiStyle.kt:80`). Under `RokuStyle` the scale is `1.0f` — border only. At 10 feet on a
55" panel neither reads.

This is the single highest-impact finding: `CinemaSecondaryButton` is the default control on every
settings card and most dialogs.

### R2 — Bare Material3 selection controls, which have no TV focus state

Material3 `Checkbox` / `RadioButton` / `Switch` render focus through `LocalIndication` (a ripple),
which is invisible without a pointer. Call sites:

| file:line | control |
|---|---|
| `feature/epgbrowser/TvEpgBrowserScreen.kt:354` | `RadioButton` — EPG search-mode picker |
| `feature/epgbrowser/TvEpgBrowserScreen.kt:386` | `Checkbox` — "matched only" |
| `feature/provider/components/ProviderDialogs.kt:360` | `Checkbox` — **script exclusion list** |
| `feature/settings/components/ImportDialogs.kt:222` | `Checkbox` — import option rows (shared row helper) |
| `feature/epg/TvEpgManagementScreen.kt:244` | `Switch` — per-source enable |
| `feature/provider/components/ProviderSettingsSection.kt:104, 349` | `Switch` — auto-resume, caching |
| `feature/settings/components/DeveloperSettingsCard.kt:43` | `Switch` — dev mode |

### R3 — `tvFocusableNoScale()` wrapped around an already-focusable child

`Modifier.focusable()` on a `Row` that contains a `Checkbox`/`Switch`/`Button` creates a **second**
focus target. The result is two D-pad stops per item, and the outer one has no `onClick` — pressing
OK on it does nothing.

- `ProviderDialogs.kt:358` (script rows) — Row + `Checkbox`
- `ProviderDialogs.kt:148` (`MatchTypeChipRow`) — modifier on a `CinemaButton`
- `ProviderSettingsSection.kt:86, 335` — Row + `Switch`
- `DeveloperSettingsCard.kt:43` — Row + `Switch`

`tvFocusableNoScale()` also draws border-only feedback (`FocusModifiers.kt:80-91`), so even the
stop that *is* focusable is barely marked.

### R4 — Selection toggles swap the composable type, destroying the focused node

The pattern `if (isSelected) CinemaPrimaryButton(…) else CinemaSecondaryButton(…)` puts two
*different* composables in one slot. Selecting an option makes Compose remove the focused node and
insert a new one; focus falls to the root, and the next D-pad press lands on the first focusable in
the screen. This is the "focus jumps to the top" report.

| file:line | picker |
|---|---|
| `feature/settings/components/ThemeSettingsCard.kt:71, 126` | palette, UI style |
| `feature/settings/components/UiScaleSettingsCard.kt:72` | UI scale |
| `feature/settings/components/PlaybackSettingsCard.kt:65` | watch delay |
| `feature/settings/components/LanguageSettingsCard.kt:67` | language |
| `feature/provider/components/ProviderSettingsSection.kt:202, 239` | stream format, playlist type |

Two aggravating factors: the selected variant is wired to `onClick = { }`, so OK on the current
option gives no feedback at all; and `UiScaleSettingsCard` / `ThemeSettingsCard` additionally
remeasure the whole tree via `LocalUiScale` / `CinemaThemeHolder`.

### R5 — Field edit-commit destroys the focused node, with nothing to return to

`ui/components/ReadOnlyFieldWithEdit.kt:70-176` swaps an `OutlinedTextField` for a `Row` + pencil
`CinemaIconButton` when `isEditing` flips false. Same node-destruction as R4, and no
`FocusRequester` is aimed back at the pencil. Every provider form is built from this component
(`XtreamForm.kt:33/55/66`, `JellyfinForm.kt:48/68/79`, `SmbForm.kt:32/44/56/67`,
`RemoteM3uForm.kt:25`, `TvAddProviderScreen.kt:235`), and they all live in one
`Column.verticalScroll` (`TvAddProviderScreen.kt:210-214`) — so committing a field also scrolls the
form back to the top.

`ProviderSettingsSection.kt:399-457` and `477-556` (watch-history size, favourites max) repeat the
same if/else by hand.

### R6 — No focus restoration on any lazy container

`focusRestorer()` appears twice in the whole module, both in `ImportDialogs.kt` (`:117`, `:280`).
Eleven files use `TvLazyColumn` / `LazyColumn` / `LazyRow` / `LazyVerticalGrid` without it, so
scrolling a row off-screen and back loses the focused item. `SettingsScreen.kt:189` is the one the
user meets first.

### R7 — Dialogs that never receive focus

`CinemaAlertDialog` (`core/ui/.../CinemaAlertDialog.kt:73-84`) lands initial focus on the dismiss
button, or on the confirm button when there is no dismiss button. Two consequences:

- **`LanguageSettingsCard.kt:86` passes `confirmButton = {}`** and no dismiss button. The requester
  is attached to an empty `Box`; `requestFocus()` cannot land. The dialog opens with focus nowhere,
  and D-pad keys fall through to the screen behind it.
- When the dialog's real content lives in the `text` slot (language options, `CategoryFilterDialog`
  at `ProviderDialogs.kt:160-395`), focus starts on the button row at the *bottom*, so the user
  must D-pad up through the whole panel to reach the first option.

### R10 — A focused text field is a D-pad dead end

On TV a focused `OutlinedTextField` consumes every direction key, so focus can never leave it.
`ReadOnlyFieldWithEdit` exists precisely to avoid that (see its KDoc), but three panels place a
live text field directly in the D-pad path anyway:

- `ProviderDialogs.kt` — "Add rules" in the category filter panel. Everything below it, including
  the whole script filter, was unreachable by remote.
- `TvEpgManagementScreen.kt:756/772/788` — the EPG source edit dialog's three fields.
- `EpgGridLayout.kt`, `TvEpgBrowserScreen.kt`, `SearchScreen.kt` — search fields; less severe,
  since those are the last stop in their row, but the same trap.

Found while verifying step 5; the filter panel is fixed there.

### R9 — Every TV dialog ignores the selected theme

`FirstVideoPlayerTheme` (`tv/ui/theme/Theme.kt`) provides `androidx.tv.material3.MaterialTheme`
only. `CinemaAlertDialog` is built on `androidx.compose.material3.AlertDialog`, and the text fields,
checkboxes, radios and switches inside these screens are M3 too — all of which read the *other*
`MaterialTheme`, which TV never provided. It therefore fell back to Material3's stock **light**
scheme: every TV dialog was a white panel with a purple button whatever palette the user picked,
and its body text was M3's stock 14sp, below the project's 18sp TV floor.

Found while fixing R7; fixed in the same step.

### R8 — Player selector dialogs report the wrong "Active" track

`AudioTrackSelectorDialog`, `SubtitleSelectorDialog`, `QualitySelectorDialog`,
`ChapterSelectorDialog` (1104 lines, near-identical) each do
`onFocusChanged { if (isFocused) selectedIndex = index }`, then style `isSelected` from
`selectedIndex`. Merely *navigating* re-points the selected styling and, for the "Off" row, the
"Active" label. The real current track is only recoverable from `track.isSelected`.

---

## 2. Target design

One rule, applied everywhere: **focus and selection are two independent, simultaneously legible
channels.**

- **Focus** = brighter container (`CinemaSurfaceLight`) + full-weight accent outline + the style's
  scale. Never darker than resting. Never outline-only.
- **Selection** = a leading state glyph (`CinemaIcons.CheckCircle` /
  `RadioButtonChecked` / `RadioButtonUnchecked`) plus an accent left-edge bar. Independent of focus,
  so a focused-but-unselected row and an unfocused-but-selected row both read correctly.

Five new primitives in `tv/ui/components/input/`, each a **single composable with a `selected:
Boolean` parameter** — never a type swap — and each with exactly **one** focus target:

| primitive | built on | replaces |
|---|---|---|
| `TvSelectableButton` | `Surface(checked=…)` (`ToggleableSurfaceDefaults`) | the R4 `if (isSelected) Primary else Secondary` pairs |
| `TvOptionRow` | `ListItem(selected=…)` | option rows in the four player selector dialogs |
| `TvCheckRow` | `ListItem` + inert `tv.material3.Checkbox` | Material3 `Checkbox` + label rows (R2/R3) |
| `TvRadioRow` | `ListItem` + inert `tv.material3.RadioButton` | Material3 `RadioButton` + label rows |
| `TvSwitchRow` | `ListItem` + inert `tv.material3.Switch` | Material3 `Switch` + label/description rows |

`androidx.tv.material3` alpha10 already ships `ListItem(selected, onClick, …)` with a complete
focused × selected state matrix, plus TV flavours of `Checkbox` / `RadioButton` / `Switch` that
accept a `null` callback and render as inert indicators. So the row primitives are thin wrappers,
not hand-drawn controls: the row owns focus and the click, the indicator only draws state. That is
what collapses R3's two D-pad stops per item into one.

Plus two helpers:

- `rememberFocusReturn(active)` — re-aims focus at the trigger control on the `true -> false` edge
  of a transient editor. Used by `ReadOnlyFieldWithEdit` and the hand-rolled edit toggles (R5).
- `CinemaAlertDialog(initialFocus = …)` — an opt-in slot so a dialog can land focus on its first
  content item instead of the button row. Default stays today's behaviour, so `:mobile`'s 11 call
  sites are unaffected.

`TvFocusTokens` gains `restingContainer`, `focusedContainer`, `selectedContainer`, and a
`minFocusBorderWidth` floor so no style can render an unreadable outline.

Every value in the new primitives resolves through the active `CinemaThemePalette` (colour) and
`UiStyle` (corner radius, focus scale, outline weight, focus shadow, emphasis font weight), so the
controls follow the user's Theme and Look-and-Feel settings exactly as the rest of the TV UI does.
`UiGridTokens.focusUsesShadow` was declared but read nowhere before this — `TvFocusTokens.focusedGlow`
is its first consumer.

**Not changing:** palettes, typography, spacing, layout, navigation graph, screen structure, or any
`:mobile` file. Every step is a like-for-like control substitution.

---

## 3. Delivery plan

Eight steps. Each builds, passes `ktlintCheck` + `lintDebug`, deploys via
`scripts/deploy-tv-ip.sh`, has a named on-device check, and lands as one commit. Stopping after any
step leaves the app coherent.

### Step 1 — Tokens and primitives · no call sites yet — ✅ **done**
Added `TvFocusTokens.restingContainer` / `focusedContainer` / `selectedContainer` and the
`minFocusBorderWidth` floor. Added the five primitives, `TvInputDefaults`, `TvInputListItem` and
`rememberFocusReturn()` under `tv/ui/components/input/`. Nothing imports them yet.
**Check:** build + `ktlintCheck` only. One incidental visual change: the border floor raises the
focus outline from 1.5 dp to 2 dp under `CupertinoStyle` (the only style with
`focusUsesOutline = false`).

### Step 2 — R1, at the source — ✅ **done**
Repointed `CinemaSecondaryButton`'s resting/focused containers at
`TvFocusTokens.restingContainer` / `focusedContainer`, and gave the generic `CinemaButton`
passthrough the focus outline and scale its semantic siblings already had (40 call sites had none).
**Verified** on darcy: in Settings, focused "5s" and focused "Amethyst" are visibly lighter than
their unfocused neighbours and carry a full accent outline, while the selected option stays solid
accent — the two states no longer collapse into one another.

### Step 3 — R4: the settings pickers — ✅ **done**
Migrated `ThemeSettingsCard` (×2), `UiScaleSettingsCard`, `PlaybackSettingsCard`,
`LanguageSettingsCard` and `ProviderSettingsSection`'s two format pickers to `TvSelectableButton`.
Added `TvFocusTokens.focusedSelectedContainer` along the way — letting the focus container win when
a row is *both* focused and selected dropped the only container-level selection cue exactly when
the user was looking at it.
**Verified** on darcy against the hardest case, UI scale (a global `LocalDensity` remeasure):
selecting 80% then 60% kept focus on the pressed option both times, where it previously fell to the
top of the screen.

### Step 4 — R7: dialog initial focus, and R9: dialogs ignoring the palette — ✅ **done**
Added the `initialFocus` slot to `CinemaAlertDialog` (opt-in, default unchanged so `:mobile`'s 11
call sites are untouched) and guarded its `requestFocus()`. Gave `LanguageSettingsCard` a real
`confirmButton` and pointed both it and `CategoryFilterDialog` at their first content item.

Uncovered and fixed **R9** in the process: `FirstVideoPlayerTheme` only themed
`androidx.tv.material3.MaterialTheme`, but `CinemaAlertDialog`, `OutlinedTextField`, `Checkbox`,
`RadioButton` and `Switch` all read `androidx.compose.material3.MaterialTheme`, which was never
provided on TV and therefore resolved to Material3's stock **light** scheme. Every TV dialog
rendered as a white panel with a purple button regardless of the selected theme, and its body text
sat at M3's stock 14sp, under the 18sp TV floor. The theme root now provides a palette-derived M3
scheme, style-derived M3 shapes, and an M3 mirror of `cinemaTypography`.

**Verified** on darcy: Settings → Language opens on-palette (dark panel, accent Cancel), with focus
on the active language rather than nowhere at all.

### Step 5 — R2 + R3 + R10: the filter panel — ✅ **done**
`CategoryFilterDialog`'s script list → `TvCheckRow`, `MatchTypeChipRow` and the Exclude/Include
pair → `TvSelectableButton`, and the redundant `tvFocusableNoScale()` wrappers dropped.

Found **R10** while verifying, and it is the more serious half: the "Add rules" `OutlinedTextField`
sat directly in the D-pad path, and a focused text field on TV swallows every direction key. Focus
entered it and never came out, so the Add button and **the entire script filter below it were
unreachable by remote** — the exact thing the user reported as broken. Making it `singleLine` was
not enough; the fix is to keep a live text field out of the navigation path at all, using the
`ReadOnlyFieldWithEdit` pattern the project already has for this.

**Verified** on darcy: the script rows are reachable, one D-pad stop each, and the three states are
legible at once — Greek checked-unfocused (accent tint + filled box), Cyrillic focused-unchecked
(lifted grey + accent outline), Latin/Arabic resting.

### Step 6 — R2 + R3 + the rest of R10 — ✅ **done**
`ProviderSettingsSection` (×2) and `DeveloperSettingsCard` → `TvSwitchRow`. The EPG browser's
search-mode radios and "matched only" checkbox → `TvSelectableButton` (they are a 2-way and a 1-way
picker; the full-width row primitives are the wrong shape for a toolbar).

`TvEpgManagementScreen:228` and `ImportDialogs:200` were **left alone on purpose** — they already
wrap an inert control in a TV `Surface` that owns the click, which is the shape everything else is
being moved to.

Added `Modifier.tvDpadEscape()` and applied it to the six remaining single-line fields
(`TvSearchTextField`, the EPG grid search, the three EPG source-edit fields, the edit-provider URL,
the three login fields). It forwards D-pad Up/Down to the focus manager so a focused field stops
being a dead end, without changing how the field behaves otherwise — the right fix where the field
should stay in the navigation path, as opposed to the filter panel's, where it should not.

**Verified** on darcy: from the EPG browser search field, D-pad Up now lands on "What's on", and
the three toolbar states (Programme selected, What's on focused, Matched only selected) are all
legible where the bare radios and checkbox previously showed no focus at all.

### Step 7 — R5: field editing
`rememberFocusReturn()` into `ReadOnlyFieldWithEdit` (fixed in place — no new component, the
existing one already has the right shape) and into the two hand-rolled editors in
`ProviderSettingsSection`.
**Check:** Add Provider → edit the URL, press Enter. Focus returns to that field's pencil and the
form does not scroll away. Repeat with Back (cancel).

### Step 8 — R6 + R8: restoration and the player dialogs
`focusRestorer()` on the eleven lazy containers. Collapse the four player selector dialogs onto one
`TvOptionRow`-based `TvSelectorDialog`, with `selected` driven by the track's real state, not by
focus.
**Check:** Settings, scroll to the bottom card, scroll back — focus returns to where it was. In the
player, open Subtitles and arrow through: "Active" stays on the real current track.

---

## 4. Risks

- **Step 2 is a shared-component change.** `CinemaSecondaryButton` lives in `:tv`, so `:mobile` is
  safe, but it is used on 22 TV files — the whole point, though it means step 2 needs a wider
  visual sweep than its own check implies.
- **`CinemaAlertDialog` is shared with `:mobile`** (11 call sites). Step 4 must add an opt-in
  parameter with today's behaviour as the default, and must not touch the existing focus logic.
- **`androidx.tv.foundation`'s `TvLazyColumn` is deprecated alpha.** Step 8's `focusRestorer()`
  works on both it and `LazyColumn`; migrating off `TvLazyColumn` is explicitly out of scope here.
- **`FlowRow` is unusable in this project** — see the comment at `ProviderDialogs.kt:138-141`. The
  new primitives must keep the manual `chunked(2)` wrapping.
