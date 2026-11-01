# Style layout tokens: layout from the style, not from the screen

**Status:** In progress 2026-11-01: Phases 1 to 6 done; 7 (walk and docs) in progress.

## Goal

The design system plan (2026-10-16) moved every colour, size, corner and font into the theme and the
Look and Feel (`UiStyle`). Screens still decide layout and some looks themselves:

- **Dialog buttons:** each screen arranges them (row or stack, width, order, where Cancel goes) and
  picks the kind of each button.
- **Sheet or dialog:** some screens open a sheet or a dialog window directly.
- **Phone top bars:** each phone screen builds its own.
- **Icons:** each screen styles its own icon buttons and icon colours.
- **Panels and rows:** raw panels and list rows set their own look, and some screens pick their own
  focus fills.

So one style cannot look different from another in these places. A screen gives content
and what each action does. The style decides how it looks. Nobody asks the user how a new
dialog should look: each style answers it.

## Inventory (audit, 2026-11-01)

`scripts/check-design-system.sh` (CI) is at 0 for literals, raw buttons, cards, dialogs, text and
switches. It does not see these:

| What | Sites | Examples |
|---|---|---|
| Dialog buttons laid out by the caller (`confirmButton =` / `dismissButton =`) | 81 | `GameSheetOverlay.kt`, `TvGuideGrid.kt:1649`, `ProfilesSettingsCard.kt:807`, `ImportDialogs.kt:118` |
| `CinemaAlertDialog`: one end-aligned Row in all three positions | 1 | `CinemaAlertDialog.kt:152,204,246` |
| `ModalBottomSheet` called directly | 4 | `MobileGameSheet.kt:83`, `ProfileSheet.kt:72`, `ShowAppearancesSheet.kt:44`, `MobileEpgGuideScreen.kt:538` |
| Raw `Dialog(` windows | 4 | `tv/.../ImportDialogs.kt:86,252`, `ProviderDialogs.kt:175`, `DataManagementSection.kt:227` |
| Home-made overlay for the TV player selectors | 1 (4 users) | `TvSelectorDialog.kt:102` |
| Phone `TopAppBar` / `Scaffold` built per screen | 22 / 46 lines | `MovieDetailsScreen.kt:149`, `MobilePersonScreen.kt:119`, `MobileSourceTopBar.kt:119` |
| Raw `IconButton` in feature code | 35 | `MobileEpgBrowserScreen.kt` (7), `SearchScreen.kt` (4) |
| `Icon(tint = …)` in feature code | 71 | `MobileCategoryListScreen.kt` (14) |
| Raw `Surface` / `ListItem` | 10 / 5 | `TvNavHost.kt:624`, `ProfilePickerScreen.kt:156`, `ProfileSheet.kt:83` |
| Focus and row fills picked in screens (`.background(` / `.border(`) | 73 | `MovieDetailsScreen.kt:1024`, `EpisodeSelectionScreen.kt:1844,2324` |
| Alpha and type values the gate misses | ~6 / ~23 | `TvPlayerStates.kt:117`, `ProfilePickerScreen.kt:135` |
| Spacing: one fixed scale in every style | 1277 uses | `CinemaSpacing`, TV `Spacing` |

## Design

1. **Dialog actions.** `UiDialogTokens` gets an action layout: stacked or in a row, full width or
   label width, and where Cancel goes. `CinemaAlertDialog` takes actions as data: one main action,
   other actions, a Danger action and Cancel. It lays them out from the style and gives each one its
   button kind. Callers stop passing button slots. On TV every dialog stays centred
   (design system D3), and its stack follows `TvButtonStyle` (`CinemaButtonArea`).
2. **Sheets.** One sheet component per app (phone `CinemaSheet`, TV overlay) that reads the style:
   a bottom sheet, a centred panel or full screen. The TV player selectors use the TV overlay.
3. **Phone top bar.** `MobileScreenHeader`, the counterpart of `TvScreenHeader`: title, back,
   actions. The style decides its look (`UiStyle.topBar`: height, title role, fill or none,
   centred or start).
4. **Icons.** Every icon button is `CinemaIconButton` / `IconAction` (phone) or `TvIconAction` (TV).
   An icon's colour comes from a tone (`TextTone`-like `IconTone`), never `tint =`.
5. **Panels and rows.** `CinemaPanel` for a raw surface. List rows use the existing row components
   (`TvCard(look = TvCardLooks.listRow())`, the phone list row). Focus fills come from the card
   looks, never from the screen.
6. **Spacing per style.** `UiStyle.spacing`: a density factor that `CinemaSpacing` and TV `Spacing`
   read, so a style can be denser or roomier.
7. **Gate.** `check-design-system.sh` also counts these outside the components: `confirmButton =` /
   `dismissButton =`, `ModalBottomSheet`, `Dialog(`, `TopAppBar`, `IconButton`, `tint =`,
   `Surface(`, `ListItem(`, `.background(`/`.border(` with a colour, `.copy(alpha =` with a
   literal, `fontSize =`, `FontFamily.`. Each phase adds its own checks and brings them to 0.

## Phases

| # | Phase | State |
|---|---|---|
| 1 | Dialog actions: the token, the `CinemaAlertDialog` actions API, all 81 dialogs moved, gate | Done 2026-11-01 (e7a593f51); TV focus opens on the main action, Cancel with a Danger action |
| 2 | Sheets and dialog windows: phone sheet and TV overlay components, the 4 sheets, 4 raw dialogs and the TV player selectors moved, gate | Done 2026-11-01 (b3e901487): `CinemaSheet` (phone, `UiDialogTokens.sheet`), `TvOverlayPanel` (TV) |
| 3 | Phone top bar: `MobileScreenHeader` and `UiStyle.topBar`, 22 screens moved, gate | Done 2026-11-01 (d99d74a4c) |
| 4 | Icons: icon buttons and icon tones, 35 + 71 sites moved, gate | Done 2026-11-01 (5010ee606); icon colour is a `TextTone` (`CinemaIcon`, `TvIcon`) |
| 5 | Panels, list rows and focus fills: `CinemaPanel`, row components, 10 + 5 + 73 sites moved, gate | Done 2026-11-01 (3c4d1d71b): `CinemaPanel` (`PanelFill`, `PanelCorner`, `PanelOutline`; stops touches like a Surface), `TvPanel`, new `TvCardLooks`, `CinemaChoiceRow` |
| 6 | Leftovers: alpha and type values, spacing per style, gate | Done 2026-11-01 (9bf0dc137): `UiStyle.spacing` (phone 0.9 to 1.1; TV stays 1.0), `CinemaCodeText` in core/ui |
| 7 | Walk both emulators in every Look and Feel (dialogs, sheets, top bars, icons, rows); docs (CONTRIBUTING rules, FEATURES, RELEASE_NOTES, design system plan) | In progress |

Each phase is one commit on main, with its tests, its gate checks at 0, and its emulator check.
Phases 3 and 4 touch different files from 1 and 2 and can run beside them; 5 and 6 come after.
