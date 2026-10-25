# TV clock and Recent on full screen

**Status:** In progress 2026-10-25.

## Goal

1. **Clock:** every TV screen except full-screen playback shows the time at the top right, at the
   same place as the player's controls clock, the Live TV preview included.
2. **Recent:** a live channel opened full screen from the preview goes into Recent at once, not
   after the watch delay. The delay still applies to the preview and to channel changes.

## Decisions (user, 2026-10-25)

- **CL-D1 Apps:** the clock on TV only. The phone's status bar already shows the time.
- **CL-D2 Place:** one clock drawn over every screen at the controls clock's place. Each screen
  keeps that corner free: its top-right items move left or down by the clock's size.
- **CL-D3 Home's clock:** Home's clock next to the app name goes; there is one clock.
- **CL-D4 Recent at once:** TV and phone.
- **Not asked:** a child profile's Home has had no clock since the kids' Home plan; the clock
  stays off for a child profile.

## Phases

1. **Recent on full screen:** `StreamLoaderViewModel.addToRecentNow`, called by the TV preview's
   OK on the playing row and by a tap on the phone's docked video; `RecentWriteTest`.
2. **TV clock:** one clock in `TvNavHost` over every screen, hidden on the player route and in Live
   TV full screen; screens with something at the top right keep the corner free; Home's clock
   goes; focus tests and walks on the TV emulator.
3. **Docs:** FEATURES, NAVIGATION_GUIDE, RELEASE_NOTES.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. Recent on full screen | Done | |
| 2. TV clock | In progress | |
| 3. Docs | Not started | |
