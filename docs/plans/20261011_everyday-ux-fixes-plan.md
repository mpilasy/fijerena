# Everyday UX fixes

**Status:** In progress, 2026-10-11. Seven independent fixes taken from the on-hold
[public release plan](./20261011_public-release-fdroid-readiness-plan.md) because they help
everyday use, public or not. Built in parallel, one commit each on main.

Decided 2026-10-11: PiP stays live-only (the release plan's "revisit it" note is closed).

## Phases

| # | Fix | Source | What done looks like |
|---|---|---|---|
| 1 | **TV VOD seeking** | U-M7 | Left/Right tap jumps ±10 s at once; holding scrubs with the existing cursor and seeks on release or OK. Live TV unchanged |
| 2 | **TV Undo without a Menu key** | U-H2 | Undo also works with a long-press on OK (remotes without Menu, e.g. Chromecast with Google TV); the hint copy names both keys |
| 3 | **Login errors** | U-H4 | Add-source and guide errors are string resources in en/fr/mg, go through `friendlyErrorMessage`, never show "null" or a bare HTTP code (raw text beneath only in developer mode); "Save anyway" is secondary |
| 4 | **Phone channel swipe** | U-H5 | Swipe thresholds in dp (48–64 dp), gestures starting in the outer 10 % of the screen ignored |
| 5 | **Guide time offset** | U-M2 | ±hh:mm offset (15-minute steps, Auto default) on TV and phone; invalid input can't silently become 0; stored format migrates if it changes |
| 6 | **First-sync progress** | U-M5 | The home header shows the sync phase and running counts during a catalogue sync; empty sections say the library is still loading |
| 7 | **Cleanups** | Low items | The unused strings removed in all three languages; the fixed 1 s minimum splash removed |

## Progress

| # | Status | Commit |
|---|---|---|
| 1 | In progress | |
| 2 | In progress | |
| 3 | In progress | |
| 4 | Done (2026-10-11); needs a phone check | fc5825d3 |
| 5 | Queued (max 4 agents at once) | |
| 6 | Queued | |
| 7 | In progress | |
