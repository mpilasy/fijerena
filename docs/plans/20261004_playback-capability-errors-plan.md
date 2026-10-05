# Playback Capability Errors Plan

**Status:** In progress (2026-10-04). P1–P3 done (device check pending); P4 waits on its slow-network test.

## Decisions (2026-10-04)

1. **Slow connection: warn, don't stop.** A banner says the connection is too slow; playback goes
   on and the user stops it if they want.
2. **Slow-network test before building P4**, on darcy and the Xperia.
3. **P1–P3 built now**, one commit each.

A stream the device can't decode should stop at once with a clear message, not play a black
screen or retry for 19 seconds. A slow connection should say so instead of stop-starting in
silence.

## Test results (2026-10-04)

Five 30 s, 60 fps, video-only clips from repo.jellyfin.org/test-videos, played from a local M3U
source with the Media3 `EventLogger` attached (debug builds, commit `15f18682`).

| Clip | darcy (Shield 2017, Android 11) | Xperia XZ2 Compact (LineageOS, Android 15) |
|---|---|---|
| Dolby Vision profile 5, 4K | `NO_UNSUPPORTED_TYPE`, track not selected, **plays nothing, no error** | same |
| Dolby Vision profile 8.1, 4K | Falls back to `OMX.Nvidia.h265.decode`, plays, 29 frames dropped | Falls back to `OMX.qcom.video.decoder.hevc`, plays, 39 dropped |
| HEVC HDR10 4K, 60 Mbps | `NO_EXCEEDS_CAPABILITIES`, plays, 0 dropped | `YES`, plays, 0 dropped |
| HEVC HDR10 4K, 150 Mbps | `NO_EXCEEDS_CAPABILITIES`, plays, 0 dropped | `NO_EXCEEDS_CAPABILITIES`, plays, 0 dropped |
| HEVC HDR10 8K, 150 Mbps | `DECODING_FAILED`, 3 retries, error after ~19 s | `DECODER_INIT_FAILED`, 3 retries, error after ~19 s |

Neither device has a Dolby Vision decoder (mdarcy does: `OMX.Nvidia.DOVI.decode`).

What this rules out: `FORMAT_EXCEEDS_CAPABILITIES` before playback is **not** a usable signal —
both devices played 4K60 at 150 Mbps cleanly with that flag set. Not covered by the test: MKV/TS
containers (may not label Dolby Vision at all, so profile 5 would decode as plain HEVC with wrong
colours and nothing to detect), and clips with audio.

## Phases

### P1 — Unplayable video track stops playback

**Today:** a stream whose only video track has no decoder (Dolby Vision profile 5 on darcy and
the Xperia) is never selected; Media3 reaches READY and plays audio only (or nothing). No error.

**Change:** in the service's `onTracksChanged`, when the tracks contain a video group, no video
track is selected, and no video track is supported (`isTrackSupported` false for all), stop the
player and show an error naming the format. Audio-only streams (no video group) are untouched.

- Format name from the track's `codecs`: `dvhe.05`/`dvh1.05` → "Dolby Vision profile 5",
  otherwise the MIME type short name (HEVC, AV1, …) and resolution.
- Final: no retry (a retry gets the same tracks).
- **Test:** unit test of the decision as a pure function over a tracks summary (video group
  unsupported / supported / absent / selected); device check with the DV P5 clip on darcy.

### P2 — Codec errors are final

**Today:** decoder errors go through `onStreamEndedOrError` → `attemptStreamRetry`, so an 8K
stream fails 4 times over ~19 s before the error shows.

**Change:** in `onPlayerError`, skip the retry and show the error at once when all three hold:

- the error is a codec one (`DECODER_INIT_FAILED`, `DECODING_FAILED`,
  `DECODING_FORMAT_UNSUPPORTED`, `DECODING_FORMAT_EXCEEDS_CAPABILITIES`);
- no frame was rendered since the stream (or its last retry) started — a failure after frames
  played is a corrupt packet mid-stream, mostly live, which can recover;
- the renderer did not report the format as fully supported
  (`ExoPlaybackException.rendererFormatSupport != C.FORMAT_HANDLED`). A decoder that fails to start
  on a format it claims to handle is more likely busy (a second player holding the hardware
  decoder) than incapable, so that case keeps today's retry.

- **Test:** unit test of the retry decision (error code × first-frame-rendered); device check
  with the 8K clip on darcy: error in under 2 s, no "Stream retry" log lines.

### P3 — Error text

1. **"FORMAT=FORMAT" bug.** `CODEC_REGEX` (`video/(\w+)|format=(\w+)`) matches
   `format=Format(` in the exception message. Replace the regex with the renderer format:
   `(error as? ExoPlaybackException)?.rendererFormat` → MIME short name + resolution, e.g.
   "HEVC 7680×4320". Shared with P1's naming.
2. **Wrong language.** The service builds error strings with its own context, which never gets
   the in-app language (`LocaleManager.wrap` runs only in `MainActivity.attachBaseContext`). On the
   Xperia (device en-US, app French) the error showed in English. Give the service a locale-wrapped
   context. `LocaleManager` lives in `core:ui`; the service is in `core:player`, so the wrap (or the
   language lookup) moves down to `core:player`.
3. **Developer mode:** friendly message first, raw `error.message` beneath it when developer mode
   is on (project rule).

- **Test:** unit test for the format-name helper (DV profile 5, HEVC 8K, null format); device
  check of the French text on the Xperia.

### P4 — Slow connection (needs a test and a decision)

**Today, from reading the code (not yet tested):**

| Situation | VOD (Xtream progressive, one bitrate) | Live |
|---|---|---|
| Link slower than the stream, still flowing | Stop-start: plays until the buffer runs out, waits for 10 s of buffer (`WIFI_VOD_REBUFFER_MS`), repeats. Toast "Excessive buffering is happening" only if 3 rebuffers fall within 30 s, which long stop-start cycles may never reach. No error, no quality step-down (nothing to step down to). | `StreamHealthMonitor` sees buffer < 8 s for 20 s and recycles the connection: 3 fast + 5 slow (30 s apart), then "unavailable after repeated recovery attempts", about 4+ min in. Recycling doesn't help a link that's simply too slow. |
| Link stalled (no bytes) | 30 s read timeout → error → 3 retries, each waiting out the timeout → about 2 min to the error | Read timeout → immediate recycle |

**Proposal:** the stream's bitrate (when the container gives it) and `DefaultBandwidthMeter`'s
estimate are both already known. When a rebuffer happens and the estimate stays under the
bitrate, show a persistent banner — "Connection too slow for this video (needs ~60 Mbps, getting
~17 Mbps)" — instead of the 30 s toast heuristic. Don't stop: connections recover, and the user
can stop themselves. For live, show the same banner and skip recycles while the cause is plain
bandwidth (keep them for read timeouts and stalls).

**Before building:**

1. **Test:** the 4K 60 Mbps clip over a throttled link (host `tc` shaping on the test server, or
   the Xperia's ~17 Mbps Wi-Fi path) and a stalled one (server pauses mid-file), on darcy and the
   Xperia, to confirm the table above.
2. ~~Decision: warn only, or stop?~~ Warn only (2026-10-04).

## Progress

| Phase | Status | Commit |
|---|---|---|
| P1 Unplayable video track | Done | `47da9f12` |
| P2 Codec errors final | Done | `5830a0dd` |
| P3 Error text | Done | (this commit) |
| P4 Slow connection | Needs test (decision: warn only) | |
