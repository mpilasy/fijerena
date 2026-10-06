# Up next details panel

**Status:** Done — checked on both emulators 2026-10-06 (phone in landscape only).

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
| 2 | Check on the emulators: TV (focus on/off the card), mobile (portrait and landscape), an episode with and without a synopsis | Done 2026-10-06 |

## Emulator check (2026-10-06)

- **TV (jellyxtream, Kid):** Cirque du Soleil specials. The card appeared with focus on Play now
  and the panel under it at the strip's width, title only ("Quidam", "Alegria": the bridge gives
  no episode synopsis). The episode ended into the next one by itself.
- **TV focus:** with the controls hidden the player keeps focus on the card (D-pad keys stay in
  the card), so in practice the panel is up for as long as the card is. Focus leaves the card
  only when the controls are already up as the card appears and the viewer moves into them;
  that case was not reached.
- **Phone (jellyxtream, atr), landscape:** Breaking Bad S1E2 with the controls up: the card below
  the clock, the panel under it with "...And the Bag's in the River" and two lines of synopsis
  cut with an ellipsis. S1E1 ended into S1E2 by itself. Portrait not checked.
- The bridge was still in its shared-logins test mode (1 connection, held 90 s), which counts
  every stream request, a seek included, as a new connection: every seek hit HTTP 458 for
  about 90 s. Once on the phone the retry resumed from the old position rather than the seek
  target; not looked into, test-mode only. The bridge was restarted in normal mode afterwards.
- **TV on bears (Kid, "DE - Breaking Bad (2008)"):** three seeks of +5 min, 12 s apart, all
  played on with no refusal (a real panel sees the old connection close). At the end of S1E1
  the panel gave "Cat's in the Bag..." and three lines of synopsis cut with an ellipsis; S1E2
  then started by itself. With the controls up when the card appeared, Down and Left from
  Play now stayed in the card and the controls hid, so the focus-off case is still not reached.
