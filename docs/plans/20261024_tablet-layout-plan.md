# Tablet layout

**Status:** Shelved 2026-10-24 (user). Decisions taken; not started.

## Goal

The phone app runs on Android tablets (Android 11 and later, Fire OS 8 included: no Google Play
services needed), but shows the phone layout: locked to portrait, one narrow column stretched to the
tablet's width. Make it a good tablet app: both orientations, and layouts that use the width (a list
with its details beside it), without a third APK and without changing the phone.

## Today

- `mobile/src/main/AndroidManifest.xml`: `MainActivity` has `android:screenOrientation="portrait"`.
  `RequestOrientation` turns the player to `SENSOR_LANDSCAPE` and the category list to `SENSOR`.
- No window size classes or tablet checks anywhere in `mobile/` or `core/ui`.
- `targetSdk` 35: Android 16 ignores orientation locks on large screens only for apps that target
  36, so today's lock still applies on tablets.
- The bottom bar (`MobileBottomBar`: Home, Live TV, Movies, TV Shows, the profile slot) is the only
  navigation.
- Phone screens (`mobile/.../feature/`): home, contentselection, category (Live TV and the film and
  series lists), movie, episode, epg and epgbrowser (TV Guide), search, wanted, settings, provider
  (Sources, Add/Edit Source), profile, player, safemode.

## Design

1. **Size classes.** One `WindowSizeClass` (Material 3 adaptive) read once at the top of the phone
   app and passed down through a CompositionLocal. Compact = today's phone layout, unchanged.
   Medium and Expanded (most tablets, and large phones held sideways) get the tablet layouts below.
2. **Orientation.** The portrait lock applies only to Compact width; Medium and Expanded follow the
   device. The player keeps its landscape request on phones; on a tablet it follows the device too.
3. **Navigation.** On Medium and Expanded, a navigation rail on the left in place of the bottom bar
   (same entries, same profile slot with the gear), as Material's adaptive navigation suggests.
4. **List and details side by side** (Expanded; Medium where it fits):
   - Movies and TV Shows: categories, list and the selected title's details in two panes
     (list | details); OK or a tap opens the details in the right pane, not a new screen.
   - Live TV: channel list | a preview with the now/next guide, like the TV's Live TV.
   - Settings: groups | the selected group's rows, like the TV's rail and pane.
   - Search: results | details.
   - The TV Guide uses the full width for more hours.
5. **Grids and rows.** Home rows and card sizes scale with the width (more cards per row, not
   bigger cards). No change to the lists as lists (the poster grid stays rejected).
6. **Dialogs and sheets.** On Medium and Expanded, the profile sheet and pickers open as centred
   dialogs of a fixed maximum width, not full-width sheets.
7. **Design system.** New tokens for the pane widths and the rail in `MobileDimensions`; no raw
   sizes (the design-system gate).

## Decisions for the user

- **TB-D1 Scope of the first release.** (a) Orientation, navigation rail and wider grids only
   (Design 1–3, 5–7); the two-pane layouts later. (b) Everything, two panes included. Recommend (a):
   it makes tablets usable quickly; the panes are most of the work.
- **TB-D2 Two-pane screens, if (b) or later:** which first? Recommend Settings and Movies/TV Shows.
- **TB-D3 Test device.** A tablet AVD (Pixel Tablet, Android 15) on this computer; a real tablet
   (which one?) before a release.
- **TB-D4 Fire tablets.** Fire OS 8 is Android 11 without Google services; check on a real Fire
   tablet, or leave it untested.

**Decided (user, 2026-10-24):** TB-D1 (b) everything in the first tablet release, two panes
included; TB-D2 all four two-pane screens, in this order: Settings, Movies and TV Shows, Live TV,
Search; TB-D3 the tablet emulator only (a Pixel Tablet AVD); TB-D4 Fire tablets left untested.

## Phases

1. **Size classes and orientation:** the CompositionLocal, the portrait lock for Compact only, the
   player rule; a tablet AVD set up; every screen walked in both orientations to list what breaks.
2. **Navigation rail** for Medium and Expanded; the profile slot and its gear in the rail.
3. **Wider grids and dialogs:** Home rows, card counts, sheets as dialogs.
4. **Two panes** (TB-D2): one screen per commit: Settings, Movies and TV Shows, Live TV, Search.
5. **Docs and walks:** FEATURES, NAVIGATION_GUIDE (a Tablet section), RUN_GUIDE (the tablet AVD; Fire tablets not tested),
   RELEASE_NOTES; screenshots of every screen on the tablet AVD in both orientations.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. Size classes and orientation | Not started | |
| 2. Navigation rail | Not started | |
| 3. Wider grids and dialogs | Not started | |
| 4. Two panes | Not started | |
| 5. Docs and walks | Not started | |
