# Night Audio

**Status:** Done 2026-11-09 on the emulators, released in v0.9.20261109201957. The check by ear and with passthrough on a Shield was skipped for now by the user (2026-11-09). The user took every recommended answer on 2026-11-09 (see Answers).

## Goal

One switch, **Night Audio**, on TV and phone. When it is on, quiet speech is louder and loud sounds
(explosions, music, adverts) are quieter, so the user can watch late at night at a low volume. It
applies to films, episodes, live TV and catch-up, from every source.

## What exists (survey 2026-11-09)

- **One player for everything.** `core/player/.../service/StreamingPlaybackService.kt`
  (`MediaSessionService`) builds the one `ExoPlayer` in `initializePlayer()`: a plain
  `DefaultRenderersFactory(this)` with `EXTENSION_RENDERER_MODE_ON` (MediaCodec first, then the
  Jellyfin FFmpeg decoder `org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1` for AC-3, E-AC-3,
  DTS, TrueHD), `PlayerConfigFactory.createTrackSelector()`, `AudioAttributes` movie/media with focus,
  `setHandleAudioBecomingNoisy(true)`. Media3 1.9.0. No custom audio sink, no `AudioProcessor`, no
  audio effect, no tunnelling, no offload setting. The TV Live TV preview, full screen, catch-up and
  the phone player (also picture-in-picture) all use this player.
- **Passthrough today.** The app does not control it. `DefaultAudioSink` bitstreams AC-3, E-AC-3,
  E-AC-3 JOC (Atmos), DTS and TrueHD when the output says it takes them (a Shield or Bravia on HDMI
  or eARC to a receiver). Device Info → Audio output lists those formats
  (`core/ui/.../deviceinfo/MediaInfo.kt`, `PASSTHROUGH_ENCODINGS`, read from
  `AudioCapabilities.getCapabilities`). No PCM processing can touch a bitstream.
- **Jellyfin.** The direct-play profile (`JellyfinApiService.kt`, `AudioCodec` lists) accepts AC-3,
  E-AC-3, DTS and TrueHD, so these reach the device unchanged; the same passthrough rule applies.
- **Player menu.** TV: `TvPlayerControlsOverlay.kt` shows the Audio `OsdButton` (`CinemaIcons.VolumeUp`)
  only when `audioTrackCount > 1`; it opens `AudioTrackSelectorDialog.kt` on the shared
  `TvSelectorDialog.kt` (radio rows). Phone: `MobileControlsOverlay.kt` (same `> 1` rule) opens
  `AudioTrackSelectorDialog` in `MobilePlayerDialogs.kt`. Both call
  `PlaybackViewModel.selectAudioTrack()` → `StreamingPlaybackService.selectAudioTrack()`.
- **Per-item audio choice.** `WatchStateEntity.audioTrackIndex` / `subtitleTrackIndex` in
  `xtream_v2.db` (`WatchStateDao` upserts, `PositionSave`); a new episode takes the series' last
  choice. Not related to Night Audio, but it shows the pattern for a per-item choice.
- **Settings.** `core/network/.../AppSettings.kt`: device keys, plus `SYNCED_SETTING_KEYS` and
  `PER_PROFILE_SETTING_KEYS` (for example `KEY_AUTOPLAY_NEXT_EPISODE`, shown in Your Profile →
  Playback). Admin → This Device holds the device-wide rows.
- **Stats for Nerds.** The AUDIO row shows codec, sample rate, channels, bitrate. It does not say
  if the output is PCM or a bitstream.
- **minSdk** 30 for `:tv`, `:mobile` and `core/*`, so `DynamicsProcessing` (API 28) is available.

## Decisions (proposed)

| # | Subject | Decision |
|---|---|---|
| NA-D1 | Engine | A Media3 `AudioProcessor`, `NightAudioProcessor` (core/player): a feed-forward compressor with linked channels (one gain for all channels, so the stereo or 5.1 image stays), then a peak limiter at −1 dBFS. Start values: threshold −24 dBFS, ratio 3:1, attack 10 ms, release 300 ms, make-up gain +6 dB. Accepts 16-bit PCM and float PCM; inactive for any other encoding. Pure Kotlin, no native code. |
| NA-D2 | Wiring | `NightAudioRenderersFactory` extends `DefaultRenderersFactory` and overrides `buildAudioSink()` to return a `DefaultAudioSink` with `setAudioProcessors(arrayOf(nightAudioProcessor))`. The MediaCodec and FFmpeg audio renderers both get this sink. The processor is always in the chain; when off it is a bypass. |
| NA-D3 | Switch on and off | No player rebuild. `StreamingPlaybackService.setNightAudio(on)` sets a flag in the processor; the gain moves to the new state over about 200 ms, so there is no click. Off is bit-exact once the ramp ends. |
| NA-D4 | Speech boost | Part of the one preset (NA-Q5): with decoded 5.1, the centre channel +3 dB before the compressor; with stereo, a gentle presence lift (about +3 dB, 1–4 kHz). |
| NA-D5 | Passthrough | Per NA-Q2. Proposed: while Night Audio is on, a `ForwardingAudioSink` around the sink reports every non-PCM encoding as unsupported, so the audio is decoded on the device (MediaCodec or FFmpeg) and goes out as PCM. Turning it on or off during a bitstream re-prepares the stream at its position (the silent recycle path the service has). The menu row says it under the switch on a device with passthrough: "Surround sound is decoded on this device while Night Audio is on". |
| NA-D6 | Menu | The Audio button shows always (not only with more than one track). Its dialog has the **Night Audio** switch first (summary "Quieter loud sounds, clearer speech"), then the tracks when there are more than one. TV: a switch row in `TvSelectorDialog`, entry focus on the selected track as today, Up reaches the switch. Phone: a switch row at the top of the phone dialog. Icon buttons stay icons. |
| NA-D7 | Where it is kept | Per NA-Q3. Proposed: one device setting, `AppSettings.nightAudio`, not synced, not per profile; also a row in Admin → This Device. The player menu switch changes the same value, and anyone can use it (no PIN). |
| NA-D8 | Shown state | When on, the Audio button carries a small dot (TV and phone), and the TV live banner shows a moon icon beside the channel name. |
| NA-D9 | Nerd | Stats for Nerds AUDIO adds "Output: PCM" or "Output: passthrough AC3" (from `AnalyticsListener.onAudioTrackInitialized`, `AudioTrackConfig.encoding`) and, while on, "Night Audio: −4.2 dB" (the gain now). |
| NA-D10 | Not used | `LoudnessEnhancer` (gain only, does not make loud sounds quieter). Codec DRC keys (`KEY_AAC_DRC_EFFECT_TYPE` "late night", `KEY_AAC_DRC_*`): they act only when the stream carries DRC metadata, which most IPTV AAC does not; possible later as an extra, not the engine. AC-3 dialnorm/DRC: no public Android key, and the FFmpeg build has no `drc_scale` setting. |
| NA-D11 | Strings | "Night Audio" (Title Case in English), the summary, the passthrough note, the Nerd lines; en, fr, mg in one change; British spelling; fr and mg in sentence case. |

## Open questions

| # | Question | Options |
|---|---|---|
| NA-Q1 | Engine | (a) **Own `AudioProcessor` (NA-D1).** Recommended: the same on every device, unit-testable, no audio HAL quirks. (b) Android `DynamicsProcessing` on the player's audio session: less code, a multi-band compressor, but the result depends on the device's effect support (some TV HALs skip effects on direct outputs) and it cannot be unit-tested. (c) (a) and (b) both, chosen per device: more work, two paths to test. |
| NA-Q2 | Passthrough (Shield or Bravia with a receiver) | (a) **Decode to PCM while on (NA-D5).** Recommended: the switch always works. Cost: Atmos and DTS:X objects are lost while on; the receiver shows PCM; the device decodes 5.1 (FFmpeg on CPU when no platform decoder). (b) Disable the switch during a bitstream, with "Not available while the receiver decodes the sound". (c) Leave it on but without effect, with a note. |
| NA-Q3 | Where it is kept and if it syncs | (a) **Per device, not synced (NA-D7).** Recommended: night viewing is a room thing (the bedroom TV), and a phone turned on at night must not change the lounge TV. (b) Per profile, synced (as Play Next Episode Automatically), in Your Profile → Playback. (c) Per profile and per device, not synced. |
| NA-Q4 | When it turns off | (a) **It stays on until the user turns it off.** Recommended: simple; NA-D8 shows it. (b) It turns off at the next app start after 06:00. (c) A schedule (for example 22:00 to 07:00) in This Device. |
| NA-Q5 | Strength | (a) **One preset, On or Off.** Recommended: the request is one switch. (b) Off, Light, Strong. (c) As (a), plus a separate Speech Boost switch. |
| NA-Q6 | Where the switch shows | (a) **In the Audio dialog only (NA-D6).** Recommended: next to the tracks, where users look for sound. (b) Its own icon button in the player controls (one more button on a full TV row). (c) Both. |

## Answers (user, 2026-11-09)

| # | Answer |
|---|---|
| NA-Q1 | (a) Own `AudioProcessor`. |
| NA-Q2 | (a) Decode to PCM while Night Audio is on. |
| NA-Q3 | (a) Per device, not synced. |
| NA-Q4 | (a) It stays on until the user turns it off. |
| NA-Q5 | (a) One preset, On or Off. |
| NA-Q6 | (a) In the Audio dialog only. |

## Phases

| # | Work | Done when |
|---|---|---|
| 1 | `NightAudioProcessor` and its tests; `NightAudioRenderersFactory`; `StreamingPlaybackService.setNightAudio()`; `AppSettings.nightAudio` per NA-Q3 | Processor tests pass; the service plays with the processor in bypass and output is unchanged |
| 2 | Passthrough per NA-Q2 (`ForwardingAudioSink`, re-prepare on change); Nerd output and gain lines | Sink test passes; Stats shows PCM or passthrough |
| 3 | TV: Audio button always, switch row, banner moon, This Device row | TV focus test and focus walk pass |
| 4 | Phone: Audio button always, switch row, dot, This Device row | Phone Compose tests pass |
| W | Merge, full suite, lint, gates, emulator walks, Shield check; FEATURES (Player → Shared, Settings → This Device), NAVIGATION_GUIDE (Audio dialog focus), RELEASE_NOTES | All checks below pass |

Phases 3 and 4 run in parallel after Phase 2.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1 | Done | b11ac7031 |
| 2 | Done | 5150e1ddd |
| 3 | Done | 614f8346d |
| 4 | Done | db0404a4e |
| W | Done | a9120f9b3 |

## Checks

- **Unit tests** (`NightAudioProcessorTest`, JVM, generated tones): silence stays silence; a −40 dBFS
  tone comes out at least 6 dB louder; a −3 dBFS burst comes out quieter and never above −1 dBFS;
  equal gain on all channels (balance kept); 16-bit and float give the same result; `flush()` resets
  the envelope; the off ramp ends bit-exact; inactive for a non-PCM format. `NightAudioSinkTest`:
  AC-3 and E-AC-3 unsupported while on, supported while off.
- **Gates:** `./gradlew ktlintCheck`, compile of `:tv` and `:mobile`, the touched modules' tests,
  `./gradlew lintDebug` with 0 errors, `scripts/check-*.sh`.
- **Emulator walk, TV and phone** (jellyxtream for a film and an episode, iptv for live TV, a
  catch-up programme): turn on and off during play (no click, no pause, no re-buffer); quiet speech
  louder and a loud scene quieter by ear and in the Nerd gain line; the setting kept after a restart
  and in This Device; Audio button on a one-track stream; TV focus walk of the dialog (English for
  the walk, then Malagasy text checked).
- **Real device: a Shield with passthrough to a receiver or eARC TV.** An AC-3 or E-AC-3 stream:
  before, Stats says "passthrough"; Night Audio on → stream re-prepares at its position, Stats says
  "PCM", the receiver shows PCM, the effect is heard; off → passthrough again. Also a stereo AAC live
  channel. Check CPU and dropped frames with FFmpeg decoding of 5.1 on 4K video.

## Risks

1. **Lost surround formats.** Decoding drops Atmos and DTS:X objects while on. The menu note says it.
2. **Multichannel PCM.** Some outputs take only stereo PCM; the decoded 5.1 then needs a downmix.
   The Shield check covers it; if Media3 does not downmix, the processor adds a stereo downmix.
3. **CPU on old TVs.** FFmpeg 5.1 decode plus the processor costs CPU next to 4K video. Measure on
   the Shield and the Bravia.
4. **Pumping.** A fast release makes the background breathe. Tune the release by ear on real films.
5. **Re-prepare during live TV.** It must not count as a stall, a retry or a new Recent entry. Use
   the silent recycle path and test it.
