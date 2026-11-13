# Match Frame Rate (TV)

**Status:** Done 2026-11-11 (Phases 1 to 3): films and Live TV in full screen play at a matching
mode with the This Device switch on. The device checks (Checks, Phases 2 and 3) wait for the user on
the Shields and the Bravia. TV emulator check 2026-11-12: see Progress (FPS "N/A" found and fixed). The user decided MF-Q1 to MF-Q6 on 2026-11-11, each with option (a).

## Goal

The TV app plays full-screen video at a display mode whose refresh rate matches the content's frame
rate: a 24 fps film at 24 Hz, a 50 Hz European channel at 50 Hz. A film at 24 fps on a 60 Hz output
judders today on the Shields (Android 11) and on the Bravia. A switch in This Device
turns the feature on and off, per device.

## What exists (survey 2026-11-11)

- **No app code asks for a display mode.** No `preferredDisplayModeId`, `Surface.setFrameRate`,
  `setVideoChangeFrameRateStrategy` or `Display.getSupportedModes` in core, tv or mobile.
- **Media3 already asks, but only for seamless changes.** Media3 1.9 (`gradle/libs.versions.toml`)
  has the default strategy `C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS`.
  `VideoFrameReleaseHelper` calls the two-argument `Surface.setFrameRate(rate,
  FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)` on API 30 and later (checked in the 1.9.0 bytecode). Media3
  has only two strategies, OFF and ONLY_IF_SEAMLESS; it cannot ask for a non-seamless change. Over
  HDMI, 60 Hz to 24 Hz is never seamless, so this call changes nothing on the user's TVs.
- **One player for the whole app.** `StreamingPlaybackService.initializePlayer`
  (core/player/.../service/StreamingPlaybackService.kt) builds the only `ExoPlayer`, with
  `NightAudioRenderersFactory`. The service has no window, so it cannot set a display mode.
- **One surface composable.** `EmbeddedPlayerSurface` (core/ui/.../components/EmbeddedPlayerSurface.kt)
  binds a `PlayerView` to the service's player:
  - Full screen (`PlayerScreen`, tv/.../ui/player/PlayerScreen.kt) uses a SurfaceView.
  - The Live TV preview (`LiveTvSplitLayout`, tv/.../feature/category/components/LiveTvSplitLayout.kt)
    uses a TextureView (`useTextureView = true`). `Surface.setFrameRate` has no effect on a
    TextureView, so the preview never asks for a mode today.
- **Every full-screen path goes through `PlayerScreen`:**
  - VOD and channels opened from a list: `TvPlayerScreen` (tv/.../feature/player/TvPlayerScreen.kt).
  - Live TV full screen: `LiveTvSplitLayout` shows `PlayerScreen` while `fullScreen` is true. Up
    and Down zap inside the same `PlayerScreen`, so it stays in composition across zaps.
  - `PlayerScreen` knows live from VOD through `currentMetadata.isLive`.
- **Frame rate data:**
  - `PlaybackViewModel` copies `Format.frameRate` into the quality list (`PlaybackState.kt`, `frameRate`).
  - `StreamingPlaybackService.measuredFps` counts rendered frames per wall-clock second
    (`PerformanceAnalyticsListener.onVideoFrameProcessingOffset`). It is too coarse to tell 23.976
    from 24.
  - `Format.frameRate` is set before the first frame for MP4, MKV and HLS with a `FRAME-RATE`
    attribute. For live MPEG-TS it is often `Format.NO_VALUE`.
- **Stats for Nerds.** `TvStatsOverlay` (tv/.../ui/player/components/overlays/TvStatsOverlay.kt) shows
  the content fps (`player_stats_fps`), from `Format.frameRate` or else `measuredFps`. No line shows
  the display mode.
- **Per-device settings.** `AppSettings.nightAudio` (core/network/.../AppSettings.kt) is the model: a
  plain key in `app_settings`, not in `SYNCED_SETTING_KEYS`, off until turned on. Its TV row is a
  `TvSwitchRow` in `SettingsGroup.THIS_DEVICE` (tv/.../feature/settings/SettingsScreen.kt), an Admin
  section.
- **Leaving the app.** `MainActivity.onStop` (tv) calls `stopAndRelease()`: TV has no background
  playback and no PiP.
- **SDK.** `minSdk = 30` and `targetSdk = 35` in tv and mobile. `Display.getSupportedModes` and
  `WindowManager.LayoutParams.preferredDisplayModeId` exist from API 23. The three-argument
  `Surface.setFrameRate(rate, compatibility, CHANGE_FRAME_RATE_ALWAYS)` and
  `DisplayManager.getMatchContentFrameRateUserPreference` exist from API 31 (Android 12).
- **Out of scope:** the phone app (phone panels change rate seamlessly, and Media3's default already
  asks), and YouTube trailers (`TrailerPlayer`, its own player).

## The Options

| | (A) `preferredDisplayModeId` | (B) `Surface.setFrameRate(..., CHANGE_FRAME_RATE_ALWAYS)` |
|---|---|---|
| API | 23 and later: works on the Shields (Android 11) | 31 and later: not on the Shields |
| Who asks | The activity window: the app picks the exact mode from `Display.getSupportedModes` | The video surface: the system picks the mode |
| Seamless | No: the HDMI link blanks for about 2 to 5 seconds (TV and AVR dependent) | No, the same blank |
| System setting | The app must read it itself (API 31: `getMatchContentFrameRateUserPreference`) | The system applies it |
| Preview | Only when the full-screen composable sets it | A TextureView preview has no effect |
| Tests | The mode choice is a pure function | Little app logic to test |

Other players that match frame rate on the Shield use (A). (B) does not help the Shields.

**Shield's system setting.** Shield Experience has a Match Content Frame Rate setting
(Display & Sound, Advanced). Its options and whether it gates an app's mode request must be checked
on a Shield by the user (Phase 1 check). The Stats for Nerds lines of Phase 1 show the result.

**When the frame rate is known:**
- VOD (MP4, MKV, HLS with `FRAME-RATE`): from `Format.frameRate` at `onTracksChanged`, before or
  near the first frame. MKV can round 23.976 to 24.
- Live MPEG-TS and HLS without `FRAME-RATE`: only from the frames. The plan measures the median
  step between frame presentation times (`ExoPlayer.setVideoFrameMetadataListener`) over about 60
  frames, about 2 seconds.
- Interlaced 25i/50i and 29.97i/59.94i: the decoder gives 25 or 50 (30 or 60) frames per second.
  Both fit a 50 Hz (59.94 Hz) mode, so the family rule below covers them.
- An HLS stream can have variants at 25 and 50 fps. They are in the same family: no switch.

## Questions (decided 2026-11-11)

The user took the recommended option (a) for each question.

| # | Question | Options |
|---|----------|---------|
| MF-Q1 | Which mechanism? **Decided: (a).** | (a) **`preferredDisplayModeId` on every API level.** One path, works on the Shields, the mode choice is unit-tested. Keep Media3's default ONLY_IF_SEAMLESS strategy (harmless). Recommended. (b) (A) on Android 11, (B) on Android 12 and later. Two paths to test, and the Bravia's Android version decides which one runs. (c) (B) only. The Shields get nothing. |
| MF-Q2 | Default of the switch? **Decided: (a).** | (a) **Off until turned on**, like Night Audio. The blank screen at the start of a film surprises a user who did not ask for it. Recommended. (b) On. |
| MF-Q3 | Live TV in full screen? **Decided: (a).** | (a) **Switch only when the family changes** (24, 50 or 60 Hz; 59.94 and 60 count as one, 23.976 and 24 as one). Zapping from one 50 Hz channel to another does not blank. The mode stays across zaps. Recommended. (b) VOD only; live stays at the system mode. (c) Switch on every channel whose exact rate differs. Many blanks. |
| MF-Q4 | Back from Live TV full screen to the preview? **Decided: (a).** | (a) **Back to the system mode when `PlayerScreen` closes.** Simple, and the lists never run at 24 Hz. Each preview to full screen round trip on a 50 Hz channel blanks twice. Recommended. (b) Keep the mode until the Live TV screen closes. Fewer blanks; the preview and lists run at the content's rate. |
| MF-Q5 | Hold a film during the switch? **Decided: (a).** | (a) **Yes, VOD only.** The film waits (paused in the app) until the display reports the new mode plus a short settle, at most 6 seconds, then plays. The user misses nothing. Recommended. (b) No. The first 2 to 5 seconds play on a blank screen. Live never waits in either case. |
| MF-Q6 | Android 12 and later: the system's Match Content Frame Rate (Never, Seamless only, Always)? **Decided: (a).** | (a) **Never: no switch. Seamless only or Always: switch.** The app switch is an explicit opt-in, so it goes past the "Seamless only" default, but the user's "Never" wins. Recommended. (b) Switch only with "Always". On most devices the feature then needs a second setting. (c) Ignore it. Only the Bravia can be on Android 12 or later. |

## Design

### The setting (This Device)

- `AppSettings.matchFrameRate`: a plain Boolean key `match_frame_rate` in `app_settings`, per device,
  not synced, not exported, off by default (MF-Q2). Not in `SYNCED_SETTING_KEYS`.
- TV only: a `TvSwitchRow` **Match Frame Rate** in `SettingsGroup.THIS_DEVICE`, under Night Audio.
  Description: the screen goes black for a few seconds when the rate changes. en, fr and mg strings,
  British spelling, Title Case in English.
- Not in the player menu: it is set once per TV.

### The content frame rate (core:player)

- `StreamingPlaybackService.contentFrameRate: StateFlow<Float?>`, null while unknown.
  - VOD: `Format.frameRate` of the selected video format, when it is set.
  - Live, or VOD without a rate: `FrameRateEstimator` (new, pure Kotlin) takes frame presentation
    times from `setVideoFrameMetadataListener` (playback thread). It gives the median step over 60
    frames, snapped to a known rate (23.976, 24, 25, 29.97, 30, 50, 59.94, 60) within 0.3 %, or null.
  - Reset on every new stream and on a seek (the `newStreamStarting` and `playStream` paths).

### The mode choice (pure, unit-tested)

- `pickDisplayMode(modes, current, fps): ModeSpec?` in core/player/.../display/. `ModeSpec` is a
  small data class (id, width, height, refresh), so the tests do not need `Display.Mode`.
- Rules:
  1. Only modes with the current mode's width and height. The plan does not change resolution or HDR.
  2. A mode whose refresh is a whole multiple of the rate (1× to 5×) within 0.05 %, so 23.976 and
     24 stay apart.
  3. Order: 25 fps prefers 50 Hz, 29.97 and 30 prefer 59.94 and 60 Hz (25 Hz and 30 Hz modes flicker
     on some TVs and slow the UI). Other rates prefer 1×, then 2×, then higher multiples.
  4. No match at 0.05 %: accept 0.2 % (23.976 content on a 24 Hz mode: one repeated frame every
     42 seconds).
  5. No match: null, no switch.
- Live (MF-Q3 a): `sameFamily(currentRefresh, targetRefresh)` is true when both are in the same
  family: 24 (23.976, 24, 48), 50 (25, 50) or 60 (29.97, 30, 59.94, 60). Then there is no switch.

### The switch (tv)

- `DisplayModeMatcher` (tv/.../ui/player/), started by a `DisposableEffect` in `PlayerScreen`. The
  Live TV preview never runs it, because it does not compose `PlayerScreen`.
- It runs only when `AppSettings.matchFrameRate` is on and (MF-Q6 a) the API 31 user preference is
  not Never.
- On a known `contentFrameRate`: `pickDisplayMode` with `display.supportedModes` and `display.mode`.
  If the result differs from the mode in place, set `window.attributes.preferredDisplayModeId`.
- VOD (MF-Q5 a): pause in the app before the switch (so the live and paused-film rules do not fire),
  wait for `DisplayManager.DisplayListener.onDisplayChanged` with the new mode plus a settle time
  (start at 1.5 s, tune on device), at most 6 s, then play.
- Live: switch when the family changes (MF-Q3 a), without a pause.
- Leaving (MF-Q4 a): `onDispose` sets `preferredDisplayModeId = 0` (no preference), so the system
  mode comes back. In the background the window is not visible, and the system drops its request;
  `MainActivity.onStop` stops playback anyway.

### Stats for Nerds

- Two new rows in `TvStatsOverlay`, in the video section:
  - **Display**: the current mode, such as `3840×2160 · 23.976 Hz`, with "(matched)" when the app
    asked for it.
  - **Modes**: the refresh rates at the current resolution, such as `23.976, 24, 50, 59.94, 60 Hz`.
- The fps row shows the measured rate with two decimals when `Format.frameRate` is not set.
- Read from `LocalView.current.display`, refreshed on `onDisplayChanged`.

## Phases

| Phase | What | Visible change |
|---|---|---|
| 1 | `FrameRateEstimator`, `contentFrameRate`, the Display and Modes rows in Stats for Nerds | Two Nerd rows; no switch |
| 2 | `matchFrameRate` setting and row, `pickDisplayMode`, `DisplayModeMatcher` for VOD (hold, reset on leave, MF-Q6) | Films play at a matching mode with the switch on |
| 3 | Live TV full screen: family rule, no switch on a same-family zap, back to the system mode on leave (MF-Q3, MF-Q4) | Live channels in full screen at a matching mode |

Each phase is one commit. Docs ride with each commit: FEATURES.md (This Device, Stats for Nerds),
NAVIGATION_GUIDE.md (the This Device row, Phase 2) and RELEASE_NOTES.md.

**Checks:**
- Phase 1: unit tests for `FrameRateEstimator`: steps for 23.976, 24, 25, 29.97, 50 and 59.94;
  jitter; a gap; 25i given as 50 steps; reset. ktlint, core:player and tv tests, compile. User on
  the Shields and the Bravia: the Modes row lists the modes; a film, a 50 Hz and a 60 Hz channel
  show the right rate; the Shield's Match Content Frame Rate options are noted in this plan.
- Phase 2: unit tests for `pickDisplayMode`: each rule above, a TV with only 60 Hz (null), a 4K
  mode list, 23.976 with and without a 23.976 mode. A Robolectric test for the This Device row
  (on, off, saved). The TV emulator: the switch, and no crash when only one mode exists (it may not
  expose several modes). User on the Shields and the Bravia: a 24 fps film starts at 24 Hz after the
  hold, the Display row says so, and Back returns to the system mode.
- Phase 3: unit tests for the live decision (same family, other family, unknown rate, switch off).
  User: zap between two 50 Hz channels (no blank), from a 50 Hz to a 60 Hz channel (one blank), full
  screen to preview (MF-Q4).

## Progress

| Phase | Status |
|---|---|
| 1 | Done 2026-11-11 |
| 2 | Done 2026-11-11 |
| 3 | Done 2026-11-11 |

**TV emulator check (2026-11-12, debug 0.9.20261112184527):** the This Device switch shows, is saved on and
off, and a 25 fps film plays with no crash with the switch on (the emulator has one 60 Hz mode, so
no switch happens). Finding: Stats for Nerds shows FPS "N/A" for the 25 fps test films, where the
Phase 1 check showed "25.00 fps (measured)"; with no content rate, the switch has nothing to match.
Cause, found and fixed on 2026-11-12: after Home and back during a film, the player screen stays and
a new `StreamingPlaybackService` instance plays it. `EmbeddedPlayerSurface` bound the player only
once (its `LaunchedEffect(Unit)`), so the video view kept the released player and the new player had
no surface: sound and position went on, the picture stayed black, and no frame reached the frame
metadata listener, so `contentFrameRate` stayed null. Now the service counts each publish
(`StreamingPlaybackService.publishCount`) and the view binds again on each new count. Two more
faults found on the way, also fixed: `TvStatsOverlay` (and `MobileStatsOverlay`) read the
measured rate, the content rate, the dropped frames and the stream time through states collected
at its first composition, which stayed with the old instance (Home and back showed the old
"25.00 fps" over a black picture); now its update loop reads the service of now at each tick. And
the "measured" fallback never filled during playback: Media3 1.9 reports frame processing offsets
only when the renderer stops; now `RenderedFrameRate` counts the frames that the frame metadata
listener gets in 2-second windows, so a 10 fps film reads "10.0 fps (measured)". Emulator check
after the fix: a 25 fps film reads "25.00 fps (measured)" before and after Home and back, with the
picture, switch on and off; The Little Robot (10 fps) reads "10.0 fps (measured)".

**Phase 1 done 2026-11-11.** `FrameRateEstimator` and `ContentFrameRate` (core/player/.../display/),
`StreamingPlaybackService.contentFrameRate`, and the **Display** and **Modes** rows in `TvStatsOverlay`
(`displayModeText`, `refreshRatesText`, `ModeSpec` in `DisplayModes.kt`). Decisions taken in the code:
- The median step alone cannot tell 23.976 from 24 when the times have jitter (MKV stores times in
  whole milliseconds: 41 and 42 ms steps). The median step finds the nominal step, each step counts
  as its nearest number of nominal steps (a missing frame counts as two), and a least-squares line
  through the frame counts and the times gives the rate.
- The window is at least 60 steps and at least 2 seconds: 60 steps of a 59.94 fps stream cover 1
  second only, too short to tell 59.94 from 60 under 1 ms of jitter. So 50 and 60 fps keep 100 and
  120 steps.
- VOD takes `Format.frameRate` from the frame metadata listener, so the rate is known from the first
  frame, not at `onTracksChanged`. Phase 2's hold can read it there if it must know before.
- A seek resets the estimate (null until 2 seconds of frames); a VOD container rate stays.
- The fps row now shows a container rate with its decimals (23.976 showed as 23), and the frames'
  rate with two decimals when the format has none.
- Display reads `3840×2160 · 23.976 Hz` (no "(matched)" until Phase 2 asks for a mode); Modes lists
  the refresh rates at the current resolution only, as the design says.

**Phase 2 done 2026-11-11.** `AppSettings.matchFrameRate` (`match_frame_rate`) and its TV row
**Match Frame Rate** in This Device under Night Audio (`SettingsViewModel.updateMatchFrameRate`, Admin
only, as Show Clock); `pickDisplayMode` and `displayModeToRequest` (core/player/.../display/
`DisplayModeChoice.kt`); `DisplayModeMatcher` and `MatchFrameRateEffect` (tv/.../ui/player/), called by
`PlayerScreen`; "(matched)" on the Stats Display row. Decisions taken in the code:
- Among modes that fit equally well, the mode in place comes first. 25, 29.97 and 30 fps try the
  multiples 2×, 1×, 3×, 4×, 5×; the other rates 1× to 5×.
- `StreamingPlaybackService.stop()` now also resets the rate (one line). Without it, a new player
  screen read the rate of the stream before until the new stream started.
- The matcher reads live or VOD from the service's metadata, which the service sets where it resets
  the rate, not from the screen.
- The hold uses `service.pause()` and `service.resume()`. Only a film that plays waits, and it plays
  again only when it is still paused (the user can press Play during the wait). The 1.5 s settle is
  inside the 6 s.
- A film shows its first frames before the pause: the container rate comes with the first frame
  (Phase 1).
- "(matched)" reads the window's `preferredDisplayModeId` at each display change: no shared state.
- A new rate during a film (only from the frames, when the format has none) asks again; a rate whose
  mode is in place asks nothing.
- The CI focus walk (`ci-settings.txt`) stops at Night Audio, so it did not change; the Robolectric
  This Device test walks the new row.

**Phase 3 done 2026-11-11.** `displayModeToRequest` now takes live: it asks for a mode only when
`sameRateFamily(current, target)` is false (`RateFamily`: 60 Hz with base 30, 50 Hz with base 25,
24 Hz with base 24). The matcher did not change: live never waited, Live TV's full screen keeps one
`PlayerScreen` across zaps (so the request stays), and its dispose already gives the system mode back
(MF-Q4). Decisions taken in the code:
- A rate is in a family when it is a whole multiple of the family's base within 0.5 %. 60 Hz is
  checked first, so 120 Hz counts as 60 Hz. A rate in no family (56 Hz) is never the same family,
  so a mode for the content is asked.
- A zap resets the rate, so the mode in place stays until the new channel's rate is known (about
  2 s from its frames), then changes only for another family.
- A film still changes the mode inside a family (60 to 59.94): the family rule is for live only, as
  MF-Q3 says.

## Difficulty

Medium. The mode choice and the estimator are small, pure and testable. The risk is in device
behaviour that the emulator cannot show: the blank time, the hold, and how each TV and AVR reacts.

## Risks

- **HDMI audio.** The link re-syncs, so audio can drop for the same seconds. With passthrough (AC3,
  E-AC3) some AVRs can make the audio track fail. Check on a Shield with the AVR; the service's recovery
  must not read it as a stream error.
- **The Bravia may list one mode only.** Then the feature does nothing there; the Modes row shows it.
- **Wrong container rates.** An MKV can say 24 for 23.976 content. The 0.2 % rule keeps the
  judder small; the measured rate can replace a container rate later if the user sees it.
- **Live channels that change rate mid-stream** (an advert at another rate): the family rule
  switches only on a real family change.
