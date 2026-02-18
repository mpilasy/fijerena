# TODO - Known Issues

## No Active Blockers

The build environment is fully operational. All recent commits compile and deploy successfully.

---

## ~~Player Error Handling on Emulator~~ MOOT

**Original issue**: TV show episodes with HEVC codec showed "Ready to play" instead of error message on emulator.

**Resolution**: Moot since commit #8. Jellyfin PlaybackInfo negotiation now handles codec incompatibility server-side — if the device can't direct-play HEVC, Jellyfin transcodes to H.264+AAC and sends an HLS stream instead. The HEVC error path on emulator is no longer reachable for Jellyfin content.

For non-Jellyfin providers (Xtream, SMB, Local), HEVC decode is handled by ExoPlayer + the bundled Jellyfin FFmpeg extension. Real devices (NVIDIA Shield, Chromecast, Sony Bravia) all support hardware HEVC decode.

---

## ~~Stream Info Not Updating on Channel Change~~ FIXED

**Status**: RESOLVED

---

## ~~Login Screen Flash on Mobile~~ FIXED

**Status**: RESOLVED — Login screen removed from both TV and mobile navigation.

---

## ~~Mobile Live TV Playback Failures~~ FIXED

**Status**: RESOLVED — `setContentType()` added to MobilePlayerScreen.

---

## ~~Missing JDK / Android SDK~~ FIXED

**Status**: RESOLVED — JDK 21 and Android SDK installed. Builds and deploys successfully.

---

## Testing Notes

Prefer real hardware for validation:
- **NVIDIA Shield**: Best HEVC/4K codec support, AV1 hardware decode
- **Chromecast with Google TV**: General Android TV compatibility
- **Sony Bravia**: TV-specific behaviour, reduced animations

## Recently Completed

- Jellyfin PlaybackInfo negotiation + DeviceProfile (#8)
- Jellyfin auth fix (OkHttp engine + HttpSend interceptor) (#6, #7)
- Jellyfin catalog 401 crash fix (#5)
- Settings export/import, EPG search/auto-refresh fixes (#4)
- Multi-source EPG management
- Cellular buffer tuning (dev mode)
- User-selectable themes (4 dark variants)
- Multiple provider management (Room database)
