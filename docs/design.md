# Fijerena - System Design Document

## Overview

Fijerena is a native Android media player supporting multiple content providers (Xtream IPTV, Jellyfin, SMB, Local files, Remote M3U). It ships two app targets — **mobile** (phone/tablet) and **tv** (NVIDIA Shield, Chromecast with Google TV, Sony Bravia) — sharing a common core layer.

**Target devices:** Android phones, tablets, NVIDIA Shield, Chromecast with Google TV, Sony Bravia (Android TV).

---

## Module Architecture

```
fijerena/
  mobile/          Android app target (phone/tablet)
  tv/              Android app target (Android TV / set-top boxes)
  core/
  player/        Media3 player, domain models, playback service
  network/       Provider implementations, API clients, EPG, Room databases
  navigation/    Type-safe navigation routes (Screen sealed interface)
  ui/            Shared theme tokens, components, ViewModels
  data/          Auth ViewModel (legacy)```

### Dependency Graph

```
mobile ──┬── core:ui ──┬── core:player
         │             └── core:network
tv ──────┘
core:ui ────── core:player
core:ui ────── core:network
core:network ── core:player (domain models only)
```

**Critical constraint:** `core:player` cannot depend on `core:network` (circular dependency). When the player needs network settings (e.g., cellular buffer multipliers), it reads directly from `SharedPreferences` via `context.getSharedPreferences("app_settings")`.

---

## Multi-Provider Architecture

### Provider Type Matrix

| Provider | Live TV | Movies | TV Shows | EPG | Search | Auth | Progress Sync |
|----------|---------|--------|----------|-----|--------|------|---------------|
| Xtream | Yes | Yes | Yes | Yes | Yes | Yes | No |
| Jellyfin | No | Yes | Yes | No | Yes | Yes | Yes |
| SMB | No | Yes | No | No | Yes | Optional | No |
| Local | M3U only | Yes | No | No | Yes | No | No |
| Remote M3U | Yes | No | No | No | No | No | No |

### Domain Model Layer (`core:player/domain/`)

All provider-specific data is mapped to unified domain types before reaching the UI. Screens never see provider-specific types.

```
MediaProvider (interface)        -- connect/disconnect, category/item/stream resolution
  MediaCategory                  -- id: String, name, itemCount, thumbnailUrl
  MediaItem                      -- id: String, name, mediaType, categoryId, metadata, providerData
  MediaMetadata                  -- plot, cast, director, genre, rating, year
  SeriesDetail                   -- seasons with episodes
  MovieDetail                    -- extended movie metadata
  PlayableStream                 -- uri: String, headers: Map
  ProviderCapabilities           -- feature flags per provider type
  ProviderType (enum)            -- XTREAM, JELLYFIN, SMB, LOCAL, REMOTE_M3U
  MediaType (enum)               -- LIVE_CHANNEL, MOVIE, SERIES, EPISODE, VIDEO_FILE
```

Navigation IDs are `String` (not `Int`) throughout the domain layer for compatibility with Jellyfin UUIDs, SMB paths, and local file URIs.

### Provider Implementations (`core:network/`)

```
MediaProviderFactory             -- creates provider instance from ProviderEntity + password
MediaRepository                  -- unified facade with favorites, watch history, caching
XtreamMediaProvider              -- Xtream Codes API client
XtreamMapper                     -- maps Xtream JSON responses to domain models
XtreamRepository                 -- low-level Xtream API calls via Ktor
JellyfinMediaProvider            -- Jellyfin REST API client
JellyfinApiService               -- Ktor-based Jellyfin API calls
SmbMediaProvider                 -- SMB network shares via smbj
SmbClient                        -- SMB connection management
LocalMediaProvider               -- local file system scanning
LocalFileScanner                 -- directory walking, media detection
M3uParser                        -- M3U/M3U8 playlist parsing
RemoteM3uMediaProvider           -- remote M3U URL fetching + parsing
```

### Storage

| Store | Purpose | Location |
|-------|---------|----------|
| `providers.db` (Room v10) | Provider configurations (name, URL, type, config JSON, active flag, sync stats + last-sync delta), EPG sources (incl. change-detection validators), pipeline stats | `ProviderEntity`, `EpgSourceEntity`, `EpgPipelineStatsEntity` |
| `xtream_v2.db` (Room v15) | Xtream catalog cache (categories, streams, series, episodes, per-stream EPG payloads, FTS4) **and** the provider-agnostic `watch_state` table | `Xtream*Entity`, `WatchStateEntity` |
| `epg_index.db` (Room v16) | EPG programme index with FTS4 search | See EPG section |
| EncryptedSharedPreferences | Per-provider passwords (keyed by provider ID) | `provider_creds_{id}`, `xtream_secure_credentials` |
| `xtream_cache_{id}` SharedPreferences | Per-provider Xtream category/item cache | JSON blobs |
| `media_cache_{id}` SharedPreferences | Per-provider favorites, favorite categories, recent categories, last-browsed position | JSON blobs + scalars |
| `app_settings` SharedPreferences | Global settings (theme, dev mode, buffer multipliers) | `AppSettings` |

Watch position and completion are **not** in SharedPreferences — the `watch_history_v3` blob was
retired in favour of `watch_state`, which is never truncated. See `docs/DATABASE_SCHEMA.md` §3.

---

## Player System

### Core Components (`core:player/`)

```
service/
  StreamingPlaybackService       -- MediaSessionService, ExoPlayer lifecycle, wake locks
  PlaybackServiceConnection      -- binds activity to service

config/
  AdaptiveLoadControl            -- network-aware buffer management (WiFi vs Cellular, Live vs VOD)
  NetworkBufferProfile           -- buffer constant definitions (design tokens)
  PlayerConfigFactory            -- track selector, content type enum

network/
  NetworkMonitor                 -- ConnectivityManager.NetworkCallback singleton, StateFlow<NetworkType>

source/
  StreamingMediaSourceFactory    -- auto-detects HLS/DASH/MPEG-TS, network-aware timeouts, auth headers
  AdaptiveLoadErrorPolicy        -- retry policy (WiFi: 5 retries, Cellular: 8, exponential backoff)

viewmodel/
  PlaybackViewModel              -- UI-facing playback control, track selection queries

model/
  PlaybackState (sealed class)   -- Idle | Buffering | Playing | Paused | Ended | Error
  PlayerMetadata                 -- title, channelName, streamUrl, isLive, headers
  AudioTrackInfo / SubtitleTrackInfo / VideoQualityInfo -- track selection models
  EpgModels                      -- EpgProgram, EpgResponse for in-player EPG
```

### Buffer Strategy

Buffers swap dynamically at runtime via `AdaptiveLoadControl` without restarting the player. `NetworkMonitor` emits `StateFlow<NetworkType>`, collected by `StreamingPlaybackService`.

| Profile | Min Buffer | Max Buffer | Playback Buffer | Rebuffer |
|---------|-----------|-----------|----------------|---------|
| WiFi Live TV | 2s | 8s | 500ms | 500ms |
| WiFi VOD | 5s | 50s | 1s | 2s |
| Cellular Live TV | 50s | 50s | 2.5s | 5s |
| Cellular VOD | 40s | 100s | 8s | 10s |

Cellular buffers support a user-configurable multiplier (0.5x - 3.0x) persisted in SharedPreferences.

### Performance Analytics

`PerformanceAnalyticsListener` (inner class of `StreamingPlaybackService`) tracks:

| Metric | API | Exposure |
|--------|-----|----------|
| Dropped frames / total frames | `onDroppedVideoFrames`, `onVideoFrameProcessingOffset` | `StateFlow<Long>` |
| Rebuffer count | `onPlaybackStateChanged` (READY->BUFFERING transitions) | `StateFlow<Int>` |
| Total rebuffer time | Accumulated time in BUFFERING state | `StateFlow<Long>` |
| Measured bandwidth | `onBandwidthEstimate` | `StateFlow<Long>` |
| ABR quality switches | `onDownstreamFormatChanged` (video height changes) | `StateFlow<Int>` |
| Stream retries | `AdaptiveLoadErrorPolicy` callback + live retry counter | `StateFlow<Int>` |
| Stream uptime | `SystemClock.elapsedRealtime()` delta from stream start | `StateFlow<Long>` |

### Stream Format Support

- **HLS** (`.m3u8`) - primary format for Xtream providers
- **DASH** (`.mpd`) - adaptive streaming
- **MPEG-TS** (`.ts`, `.mpeg`) - raw transport streams
- **SMB** (`smb://`) - via custom `SmbDataSource`
- **Content URIs** (`content://`) - local file access

Codec priority varies by device:
- NVIDIA Shield: AV1 -> HEVC -> AVC
- Sony Bravia: HEVC -> AVC
- Generic: AVC fallback

FFmpeg extension (from Jellyfin pre-built) provides software decoding for AC3, EAC3, DTS, TrueHD, MLP audio codecs.

---

## Navigation

Type-safe navigation using `kotlinx.serialization` with Navigation Compose; routes are defined once in `core:navigation/Screen.kt` and shared by both `tv/navigation/TvNavHost.kt` (D-pad, no on-screen back buttons except error screens) and `mobile/navigation/MobileNavHost.kt` (touch, portrait-locked except player). Startup lands on `ContentTypeSelection` if a provider is configured, otherwise `Settings`.

**See [NAVIGATION_GUIDE.md](NAVIGATION_GUIDE.md) for the full `Screen` definition list and navigation flow diagram** — kept in one place to avoid the two copies drifting out of sync.

---

## EPG System

### Architecture

```
EPG Sources (XMLTV URLs)
  -> EpgFileManager (download/stream + parse)
    -> XmltvParser (streaming XML parse)
      -> EpgIndexer (Room batch INSERT with FTS4)
        -> epg_index.db

epg_index.db
  -> EpgIndexDao (FTS MATCH queries)
    -> XmltvSearchService (search facade)
      -> EpgBrowserViewModel -> EpgBrowserScreen

  -> EpgSourceDao (source CRUD)
    -> EpgManagementViewModel -> EpgManagementScreen
```

Channel-based producer-consumer ingestion: downloads run concurrently (`Semaphore`-bounded, 3 on mobile / 2 on TV) as producers; 2 parallel workers consume and batch-insert into `epg_index.db` via `EpgIndexer`. `RefreshQueue` runs up to 3 tasks concurrently (semaphore-gated, not a single tracked job) and de-dupes by task ID against both queued and already-executing tasks. "Clear All Data" destroys and recreates the Room instance (instant regardless of row count) rather than `DELETE FROM`; `EpgManagementViewModel` re-subscribes its `sources` Flow afterward via a `_dbGeneration` counter. Search is two-tier FTS4 MATCH (raw query, then a sanitized AND-style retry) — no LIKE or XML-scan fallback.

**See [epg_guide.md](epg_guide.md) for the full pipeline walkthrough, state machine, and search strategy, and [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) for the canonical table/column reference** — kept in one place to avoid copies drifting out of sync.

---

## Theme & Design System

### Theme Architecture

```
CinemaThemePalette (data class)   -- complete color set per theme
CinemaThemeHolder (singleton)     -- @Volatile current palette for non-composable access
LocalCinemaTheme (CompositionLocal) -- composable access

TV re-exports:    tv/.../theme/CinemaColors.kt    (computed get() properties)
Mobile re-exports: mobile/.../theme/Color.kt       (computed get() properties)
```

### UI Scaling System

The app supports user-selectable UI scaling (0.4x - 1.0x).
- **Implementation:** `MainActivity.kt` overrides `LocalDensity` globally.
- **Mechanism:** `scaledDensity = Density(density = original * uiScale, fontScale = originalFontScale)`.
- **Result:** All `dp` and `sp` values are automatically adjusted. `.scaled()` extensions in `UiScale.kt` are no-ops to prevent double scaling.

### Palettes

| Theme | ID | Accent | Background | Surface |
|-------|----|--------|-----------|---------|
| Deep Night (default) | `deep_night` | `#2979FF` Electric Blue | `#0F1014` | `#161A20` |
| AMOLED Black | `amoled_black` | `#2979FF` | `#000000` | `#0A0A0A` |
| Emerald | `emerald` | `#00C853` Green | `#0F1014` | `#161A20` |
| Crimson | `crimson` | `#FF1744` Red | `#0F1014` | `#161A20` |

Secondary accent (Vivid Orange `#FF6D00`) is constant across all themes.

### Design Token Files

| Token File | Module | Contents |
|-----------|--------|----------|
| `CinemaColors` | core:ui | Base color palette definition |
| `CinemaAlpha` | core:ui | Opacity constants (glass, scrim, tint, text levels) |
| `CinemaAnimation` | core:ui | Duration constants (stats update, controls auto-hide, toast) |
| `CinemaCornerRadius` | core:ui | Border radius constants |
| `CinemaSpacing` | core:ui | Spacing scale (xxs through xxl) |
| `CinemaThemePalette` | core:ui | Theme palette data class + 4 predefined palettes |
| `TvDimensions` | tv | TV-specific sizes (dialog widths, progress bars, dot sizes) |
| `TvFocusTokens` | tv | Focus state parameters (scale, border, glow) |
| `MobileDimensions` | mobile | Mobile-specific sizes (icon sizes, overlay widths) |

### Typography

13-style Roboto scale (48sp - 14sp). All body text >= 18sp for TV readability.

### Focus System (TV)

- Scale: 1.0 -> 1.1 (200ms tween)
- Border: 2dp blue border
- Glow: 8dp glow effect
- Implementation: `FocusModifiers.kt`
- Every `@Composable` must be D-pad navigable using `focusRestorer()` and `focusable()`

### Safe Margins (TV Overscan)

56dp horizontal / 32dp vertical on all screen root containers.

### Shared Components (`core:ui/components/`)

- `GlassPanel` - glassmorphism container
- `CinemaThumbnail` - image loading with placeholder
- `GradientOverlay` - gradient overlay effects

### TV Components (`tv/ui/components/`)

- **Cards:** `CinemaSelectableCard`, `CinemaInfoCard`, `CinemaCompactCard`, `CinemaStandardCard`
- **Buttons:** `CinemaPrimaryButton`, `CinemaSecondaryButton`, `CinemaTertiaryButton`, `CinemaIconButton`, `CinemaDangerButton`
- **Effects:** `AccentBlock` (content-type gradients)
- **Modifiers:** `FocusModifiers` (D-pad focus states)

---

## Shared ViewModels (`core:ui/viewmodels/`)

ViewModels live in `core:ui` so both TV and mobile share identical business logic. Each has a corresponding `ViewModelFactory` for manual dependency injection.

| ViewModel | Purpose |
|-----------|---------|
| `CategoryViewModel` | Category listing, item loading, search pre-fetching |
| `SearchViewModel` | Two-phase search (cache sweep + network), result streaming |
| `EpgViewModel` | EPG guide grid data, channel/programme resolution |
| `EpgBrowserViewModel` | Programme FTS search, result grouping |
| `EpgManagementViewModel` | Multi-source EPG CRUD, ingestion trigger |
| `ProviderViewModel` | Provider CRUD, active provider switching |
| `LoginViewModel` | Credential validation, provider creation |
| `PlaybackViewModel` | Playback control (delegates to `StreamingPlaybackService`) |

---

## Player UI Features

### TV Player (`tv/ui/player/PlayerScreen.kt`)

- D-pad key handling: OK = show controls, Double-OK = dismiss stats overlay (if visible), Back = dismiss stats or exit
- Channel switching: D-pad up/down (Live TV only, disabled for VOD)
- Controls overlay: TvLazyRow of buttons (Play/Pause, Audio, Subtitle, Quality, Stats, Favorite)
- Stream info display: title, **resolution/codec info**, EPG current/next programme, progress bar. Uses `basicMarquee()` for long titles.
- Channel Overlays: Slide-in panels (Category/Last Watched) are 25% screen width. Channel names use `basicMarquee()`.
- Stats overlay: static at top-right corner, non-focusable (allows background stream control)
- Auto-hide: controls after 15s, stream info after 3s

### Mobile Player (`mobile/feature/player/MobilePlayerScreen.kt`)

- Touch to show/hide controls
- Swipe up/down for channel switching (Live TV)
- Slider-based seek bar for VOD
- GlassPanel-based controls overlay with scrollable button row; **respects status bar padding**
- Stream info display: title and **resolution/codec info** (top-left)
- Stats overlay: dismissible only via X button (not background tap); **respects status bar padding**
- Orientation: unlocked to sensor during playback, portrait on exit

### Stats for Nerds Overlay

Both platforms display identical metrics:

**VIDEO:** Codec, Resolution, Frame Rate, Bitrate
**AUDIO:** Codec, Sample Rate, Channels, Bitrate
**NETWORK:** Speed (format bitrate), Bandwidth (measured), Buffer health, Buffered position, Rebuffer count/duration (color-coded), ABR quality switches
**PLAYBACK:** Position, Duration
**PERFORMANCE:** Dropped frames (color-coded: green < 0.5%, yellow < 2%, red >= 2%)
**STREAM:** Type (Live/VOD), Retries, Uptime, URL (truncated)
**DEVICE:** Model, API level

Updates every ~500ms via polling loop.

---

## Screen Inventory

### TV Screens (`tv/feature/`)

| Screen | File | Description |
|--------|------|-------------|
| Content Type Selection | `contentselection/ContentTypeSelectionScreen.kt` | Live TV / Movies / TV Shows picker |
| Category Grid | `category/TvCategoryGridScreen.kt` | Category sidebar + item grid |
| Movie Details | `movie/MovieDetailsScreen.kt` | Movie info, play/resume buttons |
| Episode Selection | `episode/EpisodeSelectionScreen.kt` | Season accordion, episode list |
| Player | `player/TvPlayerScreen.kt` + `ui/player/PlayerScreen.kt` | Video playback with D-pad controls |
| Search | `search/SearchScreen.kt` | Search input + results grid |
| Settings | `settings/SettingsScreen.kt` | App configuration |
| Edit Provider | `settings/EditProviderScreen.kt` | Provider URL/settings editor |
| Provider Selection | `provider/ProviderSelectionScreen.kt` | Provider list with CRUD |
| Add Provider | `provider/TvAddProviderScreen.kt` | New provider form |
| EPG Guide | `epg/TvEpgGuideScreen.kt` + `epg/EpgGridLayout.kt` | TV guide time grid |
| EPG Management | `epg/TvEpgManagementScreen.kt` | Multi-source EPG configuration |
| EPG Search | `epgbrowser/TvEpgBrowserScreen.kt` | Programme search |
| Stats Overlay (generic) | `common/StatsOverlay.kt` | Reusable stats component for non-player screens |

`login/TvLoginScreen.kt` still exists on disk but `Screen.Login` is not wired into `TvNavHost.kt` — dead code left over from the login-screen removal (see RELEASE_NOTES.md, Phase 4).

### Mobile Screens (`mobile/feature/`)

| Screen | File | Description |
|--------|------|-------------|
| Content Type Selection | `contentselection/ContentTypeSelectionScreen.kt` | Content type picker |
| Category List | `category/MobileCategoryListScreen.kt` | Category list + item grid |
| Movie Details | `movie/MovieDetailsScreen.kt` | Movie info, play/resume buttons |
| Episode Selection | `episode/EpisodeSelectionScreen.kt` | Season/episode picker |
| Player | `player/MobilePlayerScreen.kt` | Touch-based playback controls |
| Search | `search/SearchScreen.kt` | Search input + results |
| Settings | `settings/SettingsScreen.kt` | App configuration |
| Edit Provider | `settings/EditProviderScreen.kt` | Provider editor |
| Cellular Buffer Settings | `settings/MobileCellularBufferSettingsScreen.kt` | Buffer multiplier sliders (dev mode) |
| Provider Selection | `provider/ProviderSelectionScreen.kt` | Provider list |
| Add Provider | `provider/MobileAddProviderScreen.kt` | New provider form |
| EPG Guide | `epg/MobileEpgGuideScreen.kt` + `epg/MobileEpgTimeline.kt` | TV guide |
| EPG Management | `epg/MobileEpgManagementScreen.kt` | EPG source management |
| EPG Browser | `epgbrowser/MobileEpgBrowserScreen.kt` | Programme search |

`login/LoginScreen.kt` still exists on disk but `Screen.Login` is not wired into `MobileNavHost.kt` — dead code left over from the login-screen removal (see RELEASE_NOTES.md, "Mobile Login Screen Removal").

---

## Virtual Categories

Virtual categories appear alongside provider categories in the category list:

| Category | Content Types | Description |
|----------|--------------|-------------|
| Continue Watching | Movies, TV Shows | In-progress VOD items (2-95% watched) |
| Favorites | All | User-curated via star button in player |
| Last Watched | All | Chronological history, auto-updated on play |
| Recent Categories | All | Recently browsed categories (max 20, per content type) |

Favorites and Recent Categories are stored in per-provider SharedPreferences, with configurable max sizes per provider settings. Continue Watching and Last Watched are derived from the `watch_state` table — the size setting bounds the rendered row, not what is stored.

---

## Search Architecture

### Xtream (Client-Side)

Two-phase parallel search:
1. **Phase 1 (instant):** Sweep cached categories for matches
2. **Phase 2 (network):** Fetch uncached categories with semaphore=20, streaming results, 200 max

Background pre-fetching warms cache on category screen init.

### Cross-Type Search ("ALL")

Global search accessible from the Content Type Selection screen via search button. Searches across Live TV, Movies, and TV Shows simultaneously. Results are grouped by content type with collapsible headers (state saved via `rememberSaveable`). Navigation from results is dynamically routed based on content type: Live TV → Player, Movies → MovieDetails, TV Shows → EpisodeSelection.

### Jellyfin (Server-Side)

Native server-side search via Jellyfin REST API.

**Auth:** `JellyfinApiService` uses an `HttpSend` interceptor to inject both `Authorization: MediaBrowser ...` and `X-Emby-Authorization: MediaBrowser ...` on every request. Jellyfin 10.10+ requires `Authorization`; older versions used `X-Emby-Authorization`. The interceptor ensures compatibility with both. Body: `{"Username": "...", "Pw": "..."}` as required by the Jellyfin 10.9+ OpenAPI spec (`additionalProperties: false`).

### Jellyfin Playback — DeviceProfile & PlaybackInfo Negotiation

Before every Jellyfin playback, the app negotiates the stream format via `POST /Items/{id}/PlaybackInfo`:

1. **DeviceProfile** built lazily by `JellyfinApiService.buildDeviceProfile()` using `buildJsonObject` DSL. Declares:
   - Direct play containers: MP4/M4V, MKV, WebM, TS — covering H.264, HEVC, VP9, AV1, AC3, EAC3, DTS, TrueHD, FLAC, Opus
   - Transcode profile: HLS/TS container, H.264 video + AAC/MP3 audio, `BreakOnNonKeyFrames=true`
   - CodecProfiles: H.264 up to High@L5.2, HEVC Main/Main10 up to L6
   - `MaxStreamingBitrate`: 140 Mbps

2. **PlaybackInfo response** (`JellyfinPlaybackInfoResponse`) contains `mediaSources` and a `PlaySessionId`. The app picks the first `JellyfinPlaybackMediaSource` and resolves the URL:
   - `supportsDirectPlay=true` → `buildStreamUrl(itemId, container, mediaSourceId)`
   - `transcodingUrl` present → `$serverUrl${transcodingUrl}` (HLS m3u8)
   - `supportsDirectStream=true` → direct stream URL
   - Fallback → legacy `?static=true` URL

3. **Session tracking:** `playSessionId` and `mediaSourceId` stored in `JellyfinMediaProvider` maps and included in all subsequent progress/stop reports so the server can manage the transcoding session lifecycle.

4. **Fallback:** If `getPlaybackInfo()` fails (network error, non-200), falls back to `buildStreamUrl(itemId)` with `?static=true`. Jellyfin's `readTimeout` extended to 60s to handle transcoding startup.

**Key files:** `JellyfinApiService.kt` (`buildDeviceProfile`, `getPlaybackInfo`, `postCapabilities`), `JellyfinMediaProvider.kt` (`resolvePlayableStream`, `playSessionIds`, `mediaSourceIds`), `JellyfinModels.kt` (`JellyfinPlaybackInfoRequest`, `JellyfinPlaybackInfoResponse`, `JellyfinPlaybackMediaSource`)

### Local / SMB

Client-side filename matching against scanned file list.

---

## Settings Export / Import

`SettingsExportManager` (`core/network/`) serializes all app configuration to a JSON file via the Storage Access Framework.

**Exported data:**
- Global `AppSettings`: theme, UI scale, dev mode, EPG auto-refresh, cellular buffer multipliers
- All provider configurations: name, URL, username, type, config JSON, per-provider settings, active flag
- All EPG sources: URL, label, timezone offset, enabled state

**NOT exported** (security): passwords (EncryptedSharedPreferences), cache data, EPG programme data, timestamps.

**Import conflict resolution:** When an imported provider name matches an existing one, a dialog prompts the user to choose:
- **Overwrite** — updates URL, username, type, config, and per-provider settings of the existing entry
- **Duplicate** — adds as a new provider with `(imported)` suffix
- **Skip** — leaves the existing entry unchanged

**SAF pattern:** File picker callbacks only set URI state; actual import/export work runs in `LaunchedEffect` to survive composable recomposition (`ForgottenCoroutineScopeException` prevention). Import MIME type filter includes `*/*` for compatibility with older Android file managers.

**Key file:** `core/network/.../SettingsExportManager.kt`

---

## Build & Deployment

### Build Commands

```bash
./gradlew assembleDebug                    # Build both targets
./gradlew :mobile:assembleRelease          # Release mobile APK
./gradlew :tv:assembleRelease              # Release TV APK
./gradlew ktlintCheck                      # Lint
```

### Deployment

```bash
# Emulator (check `adb devices -l` product/model first — port numbers are
# assigned by launch order, not by device type)
adb -s emulator-5554 install -r mobile/build/outputs/apk/debug/mobile-debug.apk

# TV devices (network) — IP changes with DHCP; use `adb mdns services` to find
# a device that moved rather than assuming a fixed address
adb connect <TV_IP>:5555
adb -s <TV_IP>:5555 install -r tv/build/outputs/apk/debug/tv-debug.apk
```

TV and mobile share `applicationId` -- use `adb -s <device>` when deploying to both simultaneously.

### Key Dependencies

- **UI:** Jetpack Compose, `androidx.tv.material3` (TV)
- **Networking:** Ktor + OkHttp engine, kotlinx.serialization
- **Player:** Media3 (ExoPlayer), Jellyfin pre-built FFmpeg decoder
- **Database:** Room with KSP
- **Navigation:** Navigation Compose with kotlinx.serialization routes
- **SMB:** `com.hierynomus:smbj:0.13.0`
- **Encryption:** EncryptedSharedPreferences (per-provider passwords)
- **Background:** WorkManager (EPG sync)

---

## Device-Specific Considerations

| Device | Considerations |
|--------|---------------|
| NVIDIA Shield | Enable AV1/HEVC codecs, full hardware acceleration |
| Sony Bravia | Avoid complex UI animations (mid-range processors), HEVC->AVC codec priority |
| Chromecast w/ Google TV | Responsive to compact window sizes, lower DPI |
| Mobile phones | Portrait locked (except player), touch controls, cellular buffer profiles |
| Tablets | `WindowSizeClass` for adaptive layout switching |

### TV Overscan Safety

All TV screen root containers apply 56dp horizontal / 32dp vertical safe margins (`Spacing.tvSafeMarginHorizontal`, `Spacing.tvSafeMarginVertical`). UI stays 5% away from screen edges for Sony/Shield TVs.
