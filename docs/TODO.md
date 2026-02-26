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

- **Subcategory Search Fix**: Resolved the infinite spinning issue during subcategory searches by ensuring asynchronous repository initialization.
- **Dependency Injection**: Refactored all ViewModel factories (`CategoryViewModelFactory`, `SearchViewModelFactory`, `EpgViewModelFactory`, etc.) to use `AppContainer` for providing the `MediaRepository` singleton.
- **Thread Safety**: Marked the `provider` field in `MediaRepository` as `@Volatile` and added Mutex synchronization in `AppContainer` to prevent race conditions during initialization.
- **Module Synchronization**: Aligned `versionCode` (4) between the TV and Mobile modules to prevent deployment conflicts.
- **Database Resilience**: Enabled `fallbackToDestructiveMigration()` in `XtreamDatabase` to prevent schema mismatch crashes during active development.
- **Architectural Refactoring**: Extracted all business logic from Composable layers into unified ViewModels (`StreamLoaderViewModel`, `MovieDetailsViewModel`, `SeriesDetailsViewModel`).
- **UI Performance**: Eliminated `runBlocking` from UI thread; all repository initializations now happen asynchronously in background dispatchers.
- **Repository Singletons**: Introduced `AppContainer` to manage repository instances, reducing redundant instantiations and ensuring consistent state.
- **Global Search**: Unified "ALL" content type search across Live TV, Movies, and TV Shows.
- **Collapsible Grouping**: Search results grouped by source with interactive expand/collapse headers.
- **Lint & Build Warning Fixes**: Resolved all "Unnecessary safe call", deprecated icon, and manifest warning issues.
- **Missing Permissions**: Declared required network state and internet permissions in library modules.
- **Progress Indicator Migration**: Updated to modern lambda-based LinearProgressIndicator.
- Player overlays: category (left) + last-watched (right) with slide animations, semi-transparent GlassPanel
- OK/tap never pauses — pause is explicit (button, media key, double-tap)
- Mobile: double-tap to pause/resume VOD; swipe left/right for Live TV overlays
- VOD seek buttons: Rewind −30s, FF +1min; TV media remote keys wired
- Jellyfin Quick Connect passwordless auth (6-digit code flow)
- Bug fix: Jellyfin session token cleared when provider credentials updated
- GlassPanel `backgroundAlpha` parameter
- `PlaybackViewModel.seekRelative(offsetMs)`
- Favorites export/import + selective import dialog (#9)
- Jellyfin PlaybackInfo negotiation + DeviceProfile (#8)
- Jellyfin auth fix (OkHttp engine + HttpSend interceptor) (#6, #7)
- Jellyfin catalog 401 crash fix (#5)
- Settings export/import, EPG search/auto-refresh fixes (#4)
- Multi-source EPG management
- Cellular buffer tuning (dev mode)
- User-selectable themes (4 dark variants)
- Multiple provider management (Room database)
