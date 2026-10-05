# Device Info Screen Plan

**Status:** In progress (2026-10-04). P1, P2 and P3 built in parallel: P2 and P3 on their own branches against a shared row model, wired into the screen when P1 lands.

## Decisions (2026-10-04)

1. **Separate screen.** "Device info" is its own screen, not a section of Diagnostics and not a
   tab. Diagnostics stays a list of logs only.
2. **Visible to everyone.** The entry sits in Settings → About on TV and mobile for every profile,
   not only with developer mode on. Diagnostics stays behind developer mode.
3. **All three sections built:** P1 core, P2 media, P3 network and power, one commit each.
4. **Its own Share on mobile.** The Diagnostics Share is unchanged. TV has no Share (as today).

Today a log entry in Diagnostics has no context: the same exception reads differently on a 3 GB
Shield than on a 2 GB Bravia. The only device facts in the app are `Build.MODEL` and the API level
in the player stats overlays, and `DeviceDetector` (device type, HEVC/AV1 yes/no).

## Rules for every phase

- **Local APIs only.** No network call (no public IP lookup).
- **Nothing secret.** No passwords, usernames, server addresses, IP addresses or Wi-Fi names. The
  screen is visible to everyone and Share sends it out; every value goes through `Redact.text`
  anyway, as the Diagnostics Share does.
- **Labels translated, values raw.** Row labels and section titles are string resources in
  `values`, `values-fr` and `values-mg`. Values stay as the system gives them (`OMX.Nvidia.h265.decode`,
  `arm64-v8a`), with sizes formatted like the existing database stats.
- **Read on `Dispatchers.IO`, once per open and on Refresh.** A missing value (API too old,
  `SecurityException`) shows "—" rather than failing the screen.

## Shape

- `core/ui/.../deviceinfo/DeviceInfoCollector.kt`: one function per section, each returning a list
  of `Row(labelRes, value)`. Android calls stay here.
- `core/ui/.../viewmodels/DeviceInfoViewModel.kt`: `StateFlow<List<Section>?>` (null while
  loading), `reload()`, `asText()` for Share. Built through `SettingsViewModelFactory`, like
  `DiagnosticsViewModel`.
- `tv/.../feature/settings/DeviceInfoScreen.kt`: title, Refresh, then one focusable surface per
  section (the D-pad scrolls section by section, like the Diagnostics entries).
- `mobile/.../feature/settings/MobileDeviceInfoScreen.kt`: top bar with Back, Refresh and Share;
  one `GlassPanel` per section.
- `Screen.DeviceInfo` route in both nav hosts. Entry: a "Device info" row in `AboutSettingsCard`
  (TV) and under the About group in the mobile `SettingsScreen`.

## Phases

### P1 — Screen and core sections

- **App:** version name and code, git hash, build time, debug or release, first install and last
  update time, active source type (Xtream, Jellyfin, M3U…), number of sources and profiles.
- **Device:** manufacturer, model, device and product codename, Android version and API level,
  security patch, supported ABIs, TV or mobile build, `DeviceDetector` device type.
- **Memory:** total and available RAM, low-memory flag and threshold, `isLowRamDevice`, heap limit
  (`memoryClass` / `largeMemoryClass`), the app's current Java heap use and native heap.
- **Storage:** free and total on internal storage (`StatFs` on `filesDir`); the app's own usage:
  each database file in `databases/` with its `-wal`, the image cache, `cacheDir` total, `filesDir`
  total.
- **Display:** current resolution and density, supported modes (resolution × refresh rate), HDR
  types (`Display.hdrCapabilities`, or `Display.Mode` on API 34+).

- **Test:** unit tests of the pure parts: byte formatting, `asText` (sections in order, labels
  and values, `Redact` applied). Device check on darcy (Shield, Android 11) and the Xperia
  (Android 15): every row filled or "—", no crash, D-pad reaches every section on TV.
- **Docs:** FEATURES (Device info), NAVIGATION_GUIDE (Settings → About → Device info), RELEASE_NOTES.

**Done (2026-10-04):** `core/ui/.../deviceinfo/CoreInfo.kt` (`coreSections`), `DeviceInfoModel.kt`
(the row model P2 and P3 build on), `DeviceInfoViewModel`, `DeviceInfoScreen` (TV: one focus stop per
section) and `MobileDeviceInfoScreen` (Refresh, Share), `Screen.DeviceInfo`. The entry is a row under
the version row in About & advanced on TV (focus walk `settings.txt` updated by hand, not re-recorded)
and a row in the About group on mobile; Back returns focus to it on TV. The git hash and build time
come from each app's `BuildConfig`, so the view model is built by the screen, not
`SettingsViewModelFactory`. Unit tests: `CoreInfoTest` (mode text, HDR names, database sizes with
their WAL, free-of-total, yes/no). Device check: not yet done.

### P2 — Media

- **Video decoders** for AVC, HEVC, AV1 and VP9: each decoder's name, hardware or software
  (`MediaCodecInfo.isHardwareAccelerated`, API 29+; name prefix below that), max resolution and
  frame rate at that resolution, HDR profiles it lists (HEVC Main10 HDR10, Dolby Vision profiles).
  Dolby Vision listed on its own (`video/dolby-vision`) since the playback capability errors work
  showed it decides what plays.
- **Audio passthrough:** what the current output accepts (`AudioCapabilities` from Media3, or
  `AudioManager` encodings): AC3, EAC3, EAC3-JOC, DTS, DTS-HD, TrueHD; max channel count.

- **Test:** unit test of the decoder summary from a fake codec list (hardware/software split,
  resolution text, Dolby Vision row). Device check: darcy shows `OMX.Nvidia.h265.decode` as
  hardware and no Dolby Vision decoder; mdarcy shows `OMX.Nvidia.DOVI.decode`.

**Done (2026-10-04):** `core/ui/.../deviceinfo/MediaInfo.kt` (`mediaSections`), built on its own
branch and cherry-picked. Decoders come from `MediaCodecList(REGULAR_CODECS)`, aliases left out; a
decoder that reports the same upper bound for width and height (it takes portrait video too) shows
the tallest height at its widest width. Audio uses Media3's `AudioCapabilities` (what the player
uses to pick passthrough), so a phone with no HDMI output shows Media3's default. Unit tests:
`MediaInfoTest` (9). Device check: not yet done.

### P3 — Network and power

- **Network:** active transport (Wi-Fi, Ethernet, cellular), validated or not, metered, VPN on,
  Private DNS mode, Wi-Fi link speed and band where the API gives them without a location
  permission (no SSID, no IP).
- **Power and background:** exempt from battery optimisation (`isIgnoringBatteryOptimizations`),
  standby bucket, background restricted, charging or not, device idle (Doze) right now.
- **Sync health:** per source, last catalog sync time, duration and last error (from the
  provider sync stats); last guide refresh time and result. This is where a sync failing in the
  background shows without adb.

- **Test:** unit test of the standby bucket and transport names. Device check on darcy and the
  Xperia, plus one emulator with Wi-Fi off (Ethernet/cellular transport shown).

## Progress

| Phase | Status | Commit |
|---|---|---|
| P1 Screen and core sections | Done; device check pending | P1 commit |
| P2 Media | Done; device check pending | P2 commit |
| P3 Network and power | In progress | |
