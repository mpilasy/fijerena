# Playback Capability Errors Plan

**Status:** Complete (2026-10-04). All four phases built and checked on darcy and the Xperia XZ2 Compact.

## Decisions (2026-10-04)

1. **Slow connection: warn, don't stop.** A banner says the connection is too slow; playback goes
   on and the user stops it if they want. **A stalled one (no data for 60 s) stops** with Retry —
   added after the test showed a 13-minute spinner.
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

### P4 — Slow connection and stalls

**Tested 2026-10-04** (darcy and Xperia, same results on both): the 4K 60 Mbps clip through a
throttling proxy on the host.

| Link | What happens today |
|---|---|
| Capped at 45 Mbps | Plays 9.5 s, buffers 13 s, plays to the end: 30 s of video in 46 s. No message. |
| Capped at 20 Mbps | Buffers about 30 s twice: 30 s of video in 96 s. No message — the "Excessive buffering" toast needs 3 rebuffers within 30 s; there were 2, 44 s apart. |
| Stalled (no bytes) | Spinner for **13 min**, then "Video unavailable after 3 retries. Network connection failed." Each attempt waits out Media3's own read-timeout retries (about 3.3 min), times the app's 4 attempts. |

Live wasn't tested (the test source has no live channels); from the code, `StreamHealthMonitor`
recycles a live stream with a low buffer — 3 fast + 5 slow attempts, about 4+ min — before
giving up.

**Decisions:** warn about a slow link without stopping; stop a stalled one after 60 s (both
2026-10-04).

**Change:**

1. **Stall limit (VOD and live).** A `TransferListener` in front of the bandwidth meter notes when
   data last arrived. The service's 5 s loop asks a `StallWatchdog`: player buffering with
   `playWhenReady`, and no data for 60 s since buffering began → stop and show "Connection lost:
   no data for a minute. Check your connection and try again." with Retry; no automatic retry
   (each takes minutes). Any byte resets the minute, so a recycle or retry that gets data keeps
   going. Paused, idle (between retries) and account-busy waits don't count.
2. **Slow-connection banner.** On a rebuffer that isn't a seek, when the stream's bitrate is known
   and the bandwidth estimate is below it, show a banner over the video: "Connection too slow for
   this video (needs ~60 Mbps, getting ~45 Mbps)". When the bitrate is unknown (most TS/MKV), the
   same banner without numbers on the second rebuffer within 2 minutes. It hides after 60 s of
   playback without a rebuffer. It replaces the "Excessive buffering" toast. Not focusable on TV.
   *Built with a `ThroughputMeter` (bytes received over the last 10 s) instead of
   `DefaultBandwidthMeter`'s estimate: that only updates when a transfer ends, and a progressive
   stream is one long transfer — on a 20 Mbit/s link it said 2 Mbit/s and nothing at the first
   rebuffer.*

- **Test:** unit tests for `StallWatchdog` and the banner decision; device check with the
  throttling proxy on darcy and the Xperia (cap 20 → banner with numbers; stall → error about a
  minute after the buffer runs dry).

## Device check (2026-10-04)

| Check | darcy | Xperia (French UI) |
|---|---|---|
| P1: DV profile 5 | "…not supported on this device: Dolby Vision profile 5" in 0.4 s, no retry | same, in French |
| P2+P3: 8K HEVC | "…: HEVC 7680×4320" in 0.55 s, no retry, dev details shown | same, in French, raw error beneath |
| P4: cap 20 Mbit/s | Banner "needs ~60 Mbps, getting ~20 Mbps" at the first rebuffer | same, in French |
| P4: stall | "Connection lost…" 62 s after the buffer ran dry (80 s after the cut) | same, 63 s, in French |

## Progress

| Phase | Status | Commit |
|---|---|---|
| P1 Unplayable video track | Done | `47da9f12` |
| P2 Codec errors final | Done | `5830a0dd` |
| P3 Error text | Done | `8f67be1a` |
| P4 Slow connection and stalls | Done | (this commit) |
