# Consistency round 2: one build for each job

**Status:** In progress 2026-11-01 (Phases 1 and 3).

## Goal

The style layout tokens plan and the consistency plan made the look come from the theme and the
Look and Feel, and the words and behaviour match. After them, the phone's long-press menu still
mixed three kinds of button in its body, because the design-system gate only blocks the patterns
it names. The user: "Make sure everything is consistent everywhere."

This plan finds every place where one job is built in more than one way, gives each job one
component per app (the same on TV and phone where the job is the same), and adds a gate rule for
each class, so it cannot come back.

## Audit (2026-11-01)

A code audit of both apps, and a walk of every screen, dialog, menu, sheet and picker on both
emulators (findings of the walks are added as they come).

| # | Finding | Where |
|---|---|---|
| 1 | Buttons drawn in a dialog's body | phone PIN options (`ProfileEditScreen.kt:488`), phone guide maintenance (`GuideSettingsRows.kt:76`); editor forms (`ProviderDialogs.kt:222`, `MobileCategoryFilterDialog.kt:149`) |
| 2 | TV action menus hand-built from rows, with Cancel as a row; each sets its own icon tone, label role and dialog colours | `FavoriteMenuDialog.kt`, `DetailsActions.kt`, `BrowseSectionsDialog.kt`, `EpisodeSelectionScreen.kt:2398`; ~20 callers set `containerColor`/`titleContentColor`/`textContentColor` |
| 3 | Phone action menus built three ways: `ItemActionsDialog`, `CinemaChoiceRow` with own padding, raw `DropdownMenu` | `MobileCategoryListScreen.kt:1900`, `EpisodeSelectionScreen.kt:1016`, `ProviderSelectionScreen.kt:285`, `SearchScreen.kt:636`, `MovieDetailsScreen.kt:766`, TV `MovieDetailsScreen.kt:1038`, TV `EpisodeSelectionScreen.kt:1863`, provider type drop-downs |
| 4 | Single-choice and toggle rows: four looks for "selected"; phone check, radio and switch rows hand-built, some with only the box tappable | `SettingsPickerDialog.kt:66`, `ProviderCopyDialogs.kt:85,95`, `ImportDialogs.kt:47`, `MobileCategoryFilterDialog.kt:315`, `ProfileSwitchRow`; TV `TvOptionRow` vs `TvRadioRow` in one dialog |
| 5 | Snackbars, toasts and undo bars: several patterns per app | phone `UndoSnackbarHost` (2), raw `SnackbarHost` (4), `Toast` (6 phone, 2 TV); `TvUndoBar` placed by each caller |
| 6 | Favourite removal undo on TV (Search, Guide) but not on the phone (Search menu, category chip menu) | TV `SearchScreen.kt:362`, `TvGuideGrid.kt:415` |
| 7 | "No results" empty states built by hand; only TV offers "Search everything" | phone and TV Search, People, Teams panes; phone Diagnostics |
| 8 | Text roles differ for the same element (dialog titles, Search section headers, filter group headings, in-dialog section labels, stats header) | 73 Title vs 9 Heading dialog titles; `SearchScreen.kt:1228` vs TV `:1419` |
| 9 | Spacing: dialog body gaps (5 values on the phone, 3 on TV); sheet callers add their own side and bottom padding | `CinemaSheet` callers, `ProfileSheet.kt:61`, `MobileEpgGuideScreen.kt:475` |
| 10 | Raw Material the gate misses: `Icon(` (37), `DropdownMenu` (8 files), `Slider` (1), `PullToRefreshBox` (4), `TooltipBox` (3); hand-made clickable rows (38 phone, 5 TV) | |
| 11 | Phone walk: in one Look, dialogs lay out two or more buttons differently (Delete source: a row; Leave sync group: full-width stack; TMDB key: label-width stack) | `src_del`, `leave`, `tmdb` shots |
| 12 | Delete icons red in some lists (sync devices, guide sources, source menu), white in others (parental keywords, Logs) | |
| 13 | Row actions differ per list: Sources a ⋮ menu; guide sources an icon row; sync devices pencil and bin; keywords bin only | |
| 14 | Search field: outline only on the Guide tab, filled on the other tabs | phone Search |
| 15 | Sync page buttons: a 2×2 grid of unequal buttons; guide sources page: two big Main buttons, unlike every other Settings page, and two refresh icons per card | phone Settings |
| 16 | Settings: "Fitantanana" row without a summary; Admin repeats "Parental controls" as heading and row | phone Settings |
| 17 | Text not in the app language: Search the Guide status line ("0 programs (0 airings) — 3.1s [indexed]"), Device Info labels, "(Settings › API)" in the TMDB dialog | |
| 18 | Phone Search filter pop-up ends on an empty "Type" heading and covers the header | phone Search |
| 19 | Guide source card: one stat wraps to five lines in a narrow column | phone guide sources |
| 20 | Lists end under the bottom bar on some pages (Parental controls, Search's "Not in your sources") | |
| 21 | TV source menu: Delete is a full-width red button and there is no Cancel; other menus have a removal row and Cancel | TV Sources |
| 22 | Clearing history: Search and Search the Guide bins clear at once (no question, no Undo) and focus is lost; Clear Recent asks; bins grey here, red elsewhere | TV, phone Search |
| 23 | "Add" actions in four forms: + icon (Sources), text buttons (Add guide source, Add a device, Add login), a "+ Add Profile" row, an inline field (Add a word) | TV and phone Settings |
| 24 | The same title's menu differs by entry point (Mark show as watched in TV Shows Recent, not in Home Continue Watching) | TV |
| 25 | Dialog footers: Close vs Cancel; first focus on a text box in Your Sources; tall dialogs touch the top edge | TV |
| 26 | Date headers: serif white in Search Teams, grey sans in Sports | TV, phone |
| 27 | Source badge left of the title on Home cards and the category pane, right of it in rows | TV |
| 28 | Category rows: no tile in the left pane, a letter tile in Recent Categories | TV |
| 29 | Film and show pages order the same buttons differently; Play orange on the hero, grey outline in the show's sticky header | TV |
| 30 | Language label: "en" plain in Films, "EN" chip elsewhere | TV |
| 31 | Tabs and chips: serif page and Live TV tabs, sans Search and season chips; selected marker underline vs left bar | TV |
| 32 | Text fields: floating-label outlined (Name) vs icon + filled + accent border (Search, Add a word) | TV |
| 33 | Empty, loading and prompt states built several ways ("No guide", "Couldn't load the guide", Search prompt, "No matched results", Search the Guide prompt, "Searching.." orange serif) | TV |
| 34 | Refresh label on focus in full-screen Live TV, none in the preview | TV |
| 35 | Long-press on a game's channel row opens the channel; the hint says "Hold OK Options" | TV game sheet |
| 36 | Sources row focus ring covers part of the row | TV Settings |
| 37 | Status line "Updated 1d ago• 1 stale" without a space; "1 categories" | TV, phone |
| 38 | Titles cut without an ellipsis in the Live TV preview; cards cut titles to a few letters with room for two lines; team tiles cut | TV |
| 39 | Metadata and clock hard to read over bright artwork; Details "Stream Name" drop-down in another font and misaligned | TV film page |
| 40 | Team page header scrolls half off screen on first focus; the rail keeps Home lit on Browse | TV |

## Design

- **Action menu, one per app, the same shape:** `CinemaActionMenu` (phone) and `TvActionMenu` (TV):
  a title and a list of `MenuAction(icon, label, kind)` — removals last, just above Cancel; Cancel
  in the dialog's `cancel` action. Every long-press menu, More menu, season menu and drop-down of
  actions uses it.
- **Choice and toggle rows as data:** phone `CinemaCheckRow`, `CinemaRadioRow`, `CinemaSwitchRow`
  (whole row tappable), mirroring TV's toggle rows; one TV single-choice row. `CinemaChoiceRow`
  takes label, icon and supporting text as data.
- **Dialogs:** the title is a string and the dialog picks its role; no caller sets dialog colours;
  one body gap token.
- **Feedback:** one undo host per app (phone snackbar host, `TvUndoBar` that places itself); no
  `Toast`; favourite removal has Undo on both apps.
- **Empty states:** every "nothing here" through `MobileEmptyState` / `TvEmptyState`, with an
  optional action.
- **Gate:** a rule for each class above (buttons in a dialog body, Cancel rows, dialog colour
  overrides, raw `Icon(`, `DropdownMenu`, `Slider`, `SnackbarHost`, `Toast`, `PullToRefreshBox`,
  `TooltipBox`, hand-made clickable rows, hand-built toggle rows, caller-picked title roles).

## Phases

| # | Phase | State |
|---|---|---|
| 1 | Menus and dialog bodies (findings 1, 2, 3, 8 for dialogs, 9 for dialogs): action menu components, every menu and drop-down of actions moved, PIN options and guide maintenance as dialog actions or a page, dialog titles and colours from the component, gate | In progress |
| 2 | Choice and toggle rows (finding 4): row components, every picker and filter moved (phone Search filter as a dialog with radio and check rows, as on TV), gate | Not started |
| 3 | Feedback and empty states (findings 5, 6, 7): one undo host per app, no Toast, favourite Undo on the phone, empty states through the components, gate | In progress |
| 4 | Leftovers (findings 8, 9, 10 to 20): text roles, sheet and dialog spacing, one dialog button layout per Look, raw `Icon(`/`Slider`/`PullToRefreshBox`/`TooltipBox`, clickable rows, row actions and delete icons, search field, Settings pages, text not in the app language, lists under the bar, gate; then the TV walk's findings | Not started |
| 4b | TV walk findings (22, 23, 24, 26 to 40) on both apps where the same job exists, gate where a class allows it | Not started |
| 5 | Walk every screen, dialog, menu, sheet and picker on both emulators again; docs | Not started |
