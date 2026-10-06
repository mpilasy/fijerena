# Up next details panel

**Status:** Phase 1 done (unit tests and builds); not yet checked on an emulator.

## Goal

The "Up next" card (autoplay next episode) is a one-line strip in the top-right corner since
`8745f087`, which dropped the episode title so the card covers less of the picture. Bring the
title back, with the synopsis, in a second semi-opaque panel directly under the strip — without
giving back the coverage the strip saved.

## Decision (user, 2026-10-05)

- **TV:** the details panel shows only while focus is inside the card. Focus lands on Play now
  as the card appears, so it shows at first; moving focus off the card (or Back, which hides the
  card) removes it, leaving the strip.
- **Mobile:** the details panel shows whenever the card does.
- Same width as the strip, same `GlassPanel` at `CinemaAlpha.scrim`.
- Title: the episode's own title (`playerEpisodeName`, so "EN - Show - S01E02 - Title" gives
  "Title"); the strip already carries the S:E code. Synopsis: `EpisodeItem.metadata.plot`,
  3 lines on TV, 2 on mobile. Either may be missing; with neither, no panel.
- No new fetch: the next episode already carries its title and plot (plot only when the panel or
  TMDB gave one).

Rejected: always showing the panel (gives back the coverage `8745f087` removed); fading it after a
few seconds.

## Progress

| Phase | What | Status |
|---|---|---|
| 1 | `upNextTitle` / `upNextPlot` in `UpNext.kt` with tests; details panel in `TvUpNextOverlay` and `MobileUpNextOverlay`; FEATURES and RELEASE_NOTES | Done (`3bc48b18`) |
| 2 | Check on the emulators: TV (focus on/off the card), mobile (portrait and landscape), an episode with and without a synopsis | Not started — needs the user's go-ahead to install on the emulators |
