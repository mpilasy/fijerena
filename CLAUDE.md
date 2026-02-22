# Fijerena - Multi-Provider Media Player
A native Android media player built with Kotlin and Jetpack Compose supporting multiple provider types.
Targeting: Android Mobile, NVIDIA Shield, Chromecast with Google TV, and Sony Bravia (Android TV).

**App Icon:** Blue Marble (Earth) with red/cyan 3D glasses. Adaptive icon (foreground PNGs in `mobile/src/main/res/drawable-*/ic_launcher_foreground.png`, black background XML) + legacy webp mipmaps.

## Tech Stack
- **UI:** 100% Jetpack Compose. `androidx.tv.material3` for TV screens.
- **Networking:** Ktor with kotlinx.serialization (JSON).
- **Video Player:** Media3 (ExoPlayer). Optimize for 4K/HDR hardware acceleration.
- **Navigation:** Adaptive Navigation Suite (handles Mobile and TV D-Pad logic).
- **Database:** Room (provider management, EPG index). Per-provider EncryptedSharedPreferences for passwords.
- **Theming:** Dynamic runtime theme switching via `CinemaThemeHolder` + `CinemaThemePalette`.
- **SMB:** `com.hierynomus:smbj:0.13.0` for network share access.

## Media3 Player Configuration

### Stream Formats & Codecs
Supported: **HLS** (`.m3u8`), **DASH** (`.mpd`), **MPEG-TS** (`.ts`, `.mpeg`)

Codec priority: NVIDIA Shield (AV1→HEVC→AVC), Sony Bravia (HEVC→AVC), Generic (AVC fallback).

### LoadControl — Network & Content-Type Aware
`AdaptiveLoadControl` dynamically selects buffer parameters based on content type (Live TV vs VOD) and network type (WiFi vs Cellular). Buffer thresholds swap at runtime without restarting the player. Architecture: delegates to `@Volatile` inner `DefaultLoadControl` with shared `DefaultAllocator`. `NetworkMonitor` emits `StateFlow<NetworkType>`, collected by `StreamingPlaybackService`.

Buffer constants defined in `core/player/.../config/NetworkBufferProfile.kt`. WiFi Live TV uses aggressive low-latency buffers (2s min/5s max); VOD uses large buffers (15s/50s). Cellular Live TV uses 50s flat buffer (min=max=50s) for stability on variable networks. Cellular VOD profiles use larger buffers with `prioritizeTimeOverSizeThresholds: true`.

### StreamingMediaSourceFactory
Always use `StreamingMediaSourceFactory.createMediaSource()` for playback:
- Auto-detects HLS/DASH/MPEG-TS, network-aware HTTP timeouts, custom auth headers
- `AdaptiveLoadErrorPolicy`: 3 retries WiFi / 6 cellular, exponential backoff (1s base, 10s cap)
- Supports `smb://` (SmbDataSource) and `content://` URIs, cross-protocol redirects
- Cronet engine (QUIC/HTTP/3) managed internally via `initCronet()`/`releaseCronet()` — called from `StreamingPlaybackService` lifecycle. Uses `CronetProviderInstaller` from Play Services. Falls back to `DefaultHttpDataSource` if unavailable.

### NetworkMonitor
Singleton via `ConnectivityManager.NetworkCallback`. `StateFlow<NetworkType>` for coroutines, `@Volatile currentNetworkType` for synchronous reads. Ethernet/WiFi→`WIFI`, Cellular→`CELLULAR`, Unknown→`WIFI` fallback.

**Key Player Files:** `config/NetworkBufferProfile.kt`, `config/AdaptiveLoadControl.kt`, `network/NetworkMonitor.kt`, `source/AdaptiveLoadErrorPolicy.kt`, `source/StreamingMediaSourceFactory.kt` (all in `core/player/`)

### Player UI Features
- **Audio/Subtitle/Quality selection:** In-playback track switching dialogs (D-pad navigable)
- **Channel switching:** D-pad up/down for Live TV only (disabled for VOD to prevent accidents). Toast notification at top-center, auto-dismiss 3s.
- **Channel overlays (Live TV):** Category-channel panel (left edge) and last-watched panel (right edge). TV: D-pad Left/Right. Mobile: swipe right/left. Animated `slideInHorizontally`/`slideOutHorizontally`. Semi-transparent `GlassPanel` (`backgroundAlpha=0.5f`), 30% scrim. `ChannelListOverlay` has `panelAlignment` param.
- **VOD seek:** Rewind −30s and FF +1min buttons in controls bar (shown only when `!isLive && duration > 0`). TV remote: `KEYCODE_MEDIA_REWIND`/`KEYCODE_MEDIA_FAST_FORWARD`. `PlaybackViewModel.seekRelative(offsetMs)` handles both.
- **VOD pause via remote/gesture:** TV: `KEYCODE_MEDIA_PLAY_PAUSE` key. Mobile: double-tap screen (Live TV ignores double-tap).
- **VOD time display:** Progress bar, remaining time, "Ends at" with timezone-aware calculation
- **EPG in player (Live TV):** Shows current programme title + time range, programme progress bar, and "Up Next" in stream info overlay (TV `StreamInfoDisplay`) and mobile `ControlsOverlay`/`ChannelToast`. Fetched via `getEpgBulkForItems()` on stream start and channel switch. Graceful degradation if no EPG data.
- **Stats overlay:** Double-tap OK. Video/audio codec info, network stats (measured bandwidth, rebuffer count/duration, ABR quality switches), dropped frames (color-coded), stream retries, uptime, repositionable (4 corners). Mobile: dismissible only via X button.
- **Control hints:** First-playback overlay listing all controls, auto-dismiss 7s, "Don't show again" option
- **Wake lock:** Acquired on play, released on pause/stop. `PARTIAL_WAKE_LOCK` + `WAKE_MODE_NETWORK`.
- **Auto-resume:** Saved position every 5s, resume if 2-95% progress
- **Mobile:** `detectTapGestures` on player surface — single tap = toggle controls, double-tap = pause/resume VOD. `detectDragGestures` merges vertical (channel switch, threshold 100f) and horizontal (overlay panels, threshold 80f) swipe.

**Controls:** OK=show controls (never pauses), Double-OK=stats, Back=exit, D-pad Up/Down=channel (Live TV), D-pad Left/Right=overlays (Live TV) or seek (VOD), Media keys=VOD pause/seek, Audio/Subtitle/Quality/Favorite buttons. Mobile: tap=controls, double-tap=pause/resume (VOD), swipe up/down=channel, swipe left/right=overlays (Live TV).

## Theme & Design System

### Themes
4 dark variants switchable at runtime. Architecture: `CinemaThemeHolder` + `CinemaThemePalette`. TV/mobile re-export files use computed `get()` properties.

| Theme | Accent | Surfaces |
|-------|--------|----------|
| **Deep Night** (default) | `#2979FF` Electric Blue | `#0F1014`, `#161A20` |
| **AMOLED Black** | `#2979FF` | `#000000`, `#0A0A0A` |
| **Emerald** | `#00C853` Green | `#0F1014`, `#161A20` |
| **Crimson** | `#FF1744` Red | `#0F1014`, `#161A20` |

Secondary accent (Vivid Orange `#FF6D00`) constant across themes. Status colors, text colors, glassmorphism tokens also constant. All colors defined in `core/ui/.../theme/CinemaThemePalette.kt`, re-exported by `tv/.../ui/theme/CinemaColors.kt` and `mobile/.../ui/theme/Color.kt`.

### Typography & Focus
- 13-style Roboto scale (48-14sp). **Key rule:** All body text >=18sp for TV readability.
- **UI Scaling:** Applied globally via `LocalDensity` in `MainActivity.kt`. Scales all `dp` and `sp` values (0.4f - 1.0f). `UiScale.kt` extension functions are now no-ops to avoid double scaling.
- Focus states: scale 1.0→1.1 (200ms tween), 2dp blue border, 8dp glow. See `FocusModifiers.kt`.
- **Corner Radius:** Restored to 8dp-20dp for UI elements (`CornerRadius.kt`). App container (`Surface` in `TvNavHost`) uses `RectangleShape` for sharp screen edges.

### Safe Margins (TV Overscan)
56dp horizontal / 32dp vertical on all screen root containers: `Spacing.tvSafeMarginHorizontal`, `Spacing.tvSafeMarginVertical`.

### Components
**Cards:** `CinemaSelectableCard` (interactive), `CinemaInfoCard`, `CinemaCompactCard`, `CinemaStandardCard`
**Buttons:** `CinemaPrimaryButton`, `CinemaSecondaryButton`, `CinemaTertiaryButton`, `CinemaIconButton`, `CinemaDangerButton`
**Player Overlays:** Slide-in panels are 25% screen width. Channel names and titles use `basicMarquee()` for overflow.
**Effects:** Glassmorphism (category sidebar), `AccentBlock.kt` (content-type gradients)

## Coding Standards
- **STRICT: No Hardcoded UI Values.** Every visual attribute must come from design token constants — never raw literals (`16.dp`, `Color.White`, etc.). Add missing tokens to token files first.
- **Design Token Files:**
  - **Shared (core/ui):** `CinemaColors`, `CinemaAlpha`, `CinemaAnimation`, `CinemaCornerRadius`, `CinemaSpacing`
  - **TV:** `TvDimensions`, `TvFocusTokens` | **Mobile:** `MobileDimensions`
  - **Platform re-exports:** TV `CinemaColors.kt`/`Spacing.kt`, mobile `Color.kt`/`Spacing.kt` — screen files import from platform package
  - Colors: prefer `MaterialTheme.colorScheme.*` or platform re-exports, never `Color.White`/`Color.Black`
- **Focus Management:** Every @Composable must be D-pad navigable. Use `focusRestorer()` and `focusable()`.
- **Safe Areas:** UI must remain 5% away from screen edges for Sony/Shield TVs.
- **Mobile vs TV:** Use `WindowSizeClass` for layout switching.
- **Network:** Support HTTP/Cleartext for legacy Xtream providers.

## Device-Specific Rules
- **NVIDIA Shield:** Enable AV1/HEVC codecs if device is 'shield'.
- **Sony TV:** Avoid complex UI animations on mid-range Bravia processors.
- **Chromecast:** Responsive to "Compact" window sizes (lower DPI).

## Development Commands
- **Build:** `./gradlew assembleDebug` | **Release:** `./gradlew :mobile:assembleRelease` (or `:tv:assembleRelease`)
- **Install TV:** `adb connect [TV_IP] && ./gradlew installDebug`
- **Install mobile:** `adb -s emulator-5554 install -r mobile/build/outputs/apk/debug/mobile-debug.apk`
- **Lint:** `./gradlew ktlintCheck`

TV and mobile share `applicationId` — use `adb -s <device>` when deploying to both simultaneously.

## App Navigation & Features

### Navigation Flow
1. **Startup:** No provider → Settings. Provider + saved content type → auto-navigate to last Category Grid. Otherwise → Content Type Selection.
2. **Content Type Selection** → **Category Grid** → **Details** (Movie/Episode) → **Player**
3. **Settings** via gear icon, **EPG Browser** via book icon (visible when EPG data is indexed), **EPG Management** via Settings → "Manage EPG Data"
4. **Provider Management** via Settings → "Manage Providers" (select/edit/delete/add with type-specific forms)

**TV Back Navigation:** Remote back button only, no on-screen Back buttons (except error screens).
**Mobile Orientation:** Portrait locked except player (sensor-based).
**No login/logout screens.** Auth via stored credentials or provider configuration.

### Settings Screen
- **Active Provider:** Name and URL display
- **Manage Providers:** Navigate to provider CRUD screen
- **Theme Selection:** 4 dark themes, persisted
- **Manage EPG Data:** Navigate to EPG Management screen. Shows summary ("N sources, X programmes" or "No sources configured")
- **Cache Management:** Total size, per-content-type breakdown with clear buttons
- **UI Scale:** 70-100% for category/grid views
- **Developer Mode:** Payload size tracking, debug info, provider type display
- **Export Settings:** Saves providers + EPG sources + global settings to a JSON file via SAF. Passwords excluded.
- **Import Settings:** Reads JSON file via SAF. On provider name conflict, dialog prompts Overwrite / Duplicate / Skip. EPG sources merged by URL (duplicates skipped). `SettingsExportManager` in `core/network/`.

### Provider Settings (Inline in Edit Provider)
Auto-resume (default: on), Last Watched queue size (1-100, default: 25), Favorites max (10-500, default: 100), clear favorites/progress, category filters (Xtream only), caching toggle (Xtream only).

### Content Types
- **Live TV:** Live channels (Xtream, Local with M3U)
- **Movies (VOD):** On-demand (all providers)
- **TV Shows:** Series with episode selection (Xtream, Jellyfin)

### Virtual Categories
- **Continue Watching:** In-progress VOD items (not for Live TV)
- **Favorites:** User-curated, star button in player, per-content-type, configurable max size
- **Last Watched:** Chronological history, auto-updated after 5s of viewing, per-content-type, configurable queue size
- **Recent Categories:** Shows recently browsed categories (max 20, per content type, deduplicated). Clicking navigates to that category. Only visible when history exists. Tracked automatically on non-virtual category loads. Data stored as JSON in per-provider SharedPreferences.

### Episode Details (Inline)
Inline detail panel before playback (no separate route). Thumbnail, metadata, Play/Resume buttons, plot/cast/director with series fallback. Collapsible season accordion (one expanded at a time, auto-expands next unwatched). Resume if 2-95% progress.

### Search
**Access:** Category Grid → magnifying glass icon. Minimum 2 chars, explicit trigger.

**Xtream (client-side):** Two-phase parallel search. Phase 1: instant cache sweep. Phase 2: network fetch of uncached categories (semaphore=20, streaming results, 200 max). Background pre-fetching warms cache on init.
**Jellyfin:** Native server-side search.

**Key Files:** `core/ui/.../viewmodels/SearchViewModel.kt`, `tv/.../feature/search/SearchScreen.kt`, `mobile/.../feature/search/SearchScreen.kt`

### EPG (Electronic Program Guide)
Full TV Guide grid for Live TV. Access: Category Grid → "TV Guide" button (Live TV only).

Grid: channel list (20%) + time grid (80%), 48×30min slots, auto-scroll to now, date navigation (prev/next day, jump to now). Click channel/program to play. Max 50 channels, 30-min cache TTL.

**API:** `get_simple_data_table` (primary), `get_short_epg` (fallback). Parallel bulk fetching.
**Files:** `EpgModels.kt`, `EpgViewModel.kt`, `EpgGuideScreen.kt`, `EpgGridLayout.kt`

### EPG Browser
Standalone programme search screen. Access: Content Type Selection → book icon (visible when `EpgIndexer.state` is `Indexed`).

Results grouped by title, sorted by airing count. Time window: -1 to +6 days, max 500 results. TV: GlassPanel/TvLazyColumn. Mobile: expandable cards/LazyColumn. Programme titles and channel names use `basicMarquee()` for overflow scrolling. LazyColumn keys use `"${title}::${description}"` (not title alone) to avoid duplicates.

**Search:** SQLite FTS4 MATCH query (<100ms). Falls back to LIKE if FTS returns empty.

**Indexing:** `EpgIndexer` singleton parses XMLTV into Room DB with FTS4. Streaming parse, 500-row batch INSERTs wrapped in Room `withTransaction` for atomicity. States: `NotIndexed`, `Indexing(progress)`, `Indexed(counts)`, `Failed(reason)`. Triggered by `EpgFileManager` after source ingestion. Incremental auto_vacuum reclaims dead pages after clear/purge/refresh operations. On network drop or parse error, transaction rolls back — DB stays consistent.

**Timezone Override:** Per-source `timezoneOffsetHours` overrides XMLTV timestamps during parsing via `XmltvParser.timezoneOverrideHours`.

**Key Files:** `XmltvSearchService.kt` (SQLite-only search), `EpgBrowserModels.kt`, `XmltvParser.kt`, `epgindex/EpgIndexer.kt`, `epgindex/EpgIndexDatabase.kt` (Room v4), `epgindex/EpgIndexDao.kt` (FTS MATCH + LIKE), `epgindex/EpgProgrammeEntity.kt` (FTS4), `epgindex/EpgChannelEntity.kt`, `EpgBrowserViewModel.kt`

### EPG Management (Multi-Source)
Dedicated screen (`Screen.EpgManagement`) for managing multiple XMLTV EPG sources. Replaces the old single-URL EPG configuration in Settings.

**Architecture:** Download-ingest-delete pipeline per source. Each source URL is downloaded to a temp file, parsed into Room SQLite database, and the file is immediately deleted. No permanent XML files on disk. First source clears existing data (full rebuild), subsequent sources append (REPLACE handles channel ID overlaps).

**Data Model:** `EpgSourceEntity` in `epg_index.db` (Room v6) — stores URL, label, per-source timezone override, enabled flag, last ingested timestamp, last error. Database uses `auto_vacuum=INCREMENTAL` with one-time full VACUUM on upgrade from older mode.

**EpgFileManager** (`core/network/.../xmltv/EpgFileManager.kt`): Singleton managing multi-source ingestion lifecycle. Uses Ktor `HttpClient(OkHttp)` for HTTP requests. Dual-mode architecture via `isFixedDevice()` (uses `DeviceDetector`): TV/fixed devices stream directly from network to database (zero disk I/O); mobile devices download to `cacheDir` first, then ingest from file. Both paths use 128KB buffers, 3 retries with exponential backoff. WiFi-only enforcement. `MultiSourceState`: Idle/Processing(source,index,total,phase)/Completed(count,errors)/Error. Auto-migrates legacy `AppSettings.epgUrl` to `EpgSourceEntity` on first init. Mobile background sync via WorkManager `EpgSyncWorker` (24h periodic).

**UI:** TV (`TvEpgManagementScreen`) and Mobile (`MobileEpgManagementScreen`). Source list with status dots (green=recent, yellow=>24h, red=error, gray=disabled), add/edit dialog (URL, label, timezone cycle), actions (Refresh All, Cleanup Files, Purge >7d, Clear All Data with confirmation).

**Key Files:** `epgindex/EpgSourceEntity.kt`, `epgindex/EpgSourceDao.kt`, `EpgFileManager.kt`, `EpgSyncWorker.kt`, `EpgManagementViewModel.kt`, `EpgManagementViewModelFactory.kt`

### Multi-Provider Architecture
4 provider types through unified domain model. Screens never see provider-specific types.

| Provider | Live TV | Movies | TV Shows | EPG | Search | Auth | Progress Sync |
|----------|---------|--------|----------|-----|--------|------|---------------|
| **Xtream** | Yes | Yes | Yes | Yes | Yes | Yes | No |
| **Jellyfin** | No | Yes | Yes | No | Yes | Yes | Yes |
| **SMB** | No | Yes | No | No | Yes | Optional | No |
| **Local** | M3U only | Yes | No | No | Yes | No | No |

**Domain Models** (`core/player/.../domain/`): `MediaProvider`, `MediaCategory`, `MediaItem`, `SeriesDetail`, `MovieDetail`, `PlayableStream`, `ProviderCapabilities`, `ProviderType`

**Key Files:** `MediaRepository.kt` (unified repository), `MediaProviderFactory.kt`, `XtreamMediaProvider.kt`/`XtreamMapper.kt`, `jellyfin/JellyfinMediaProvider.kt`/`JellyfinApiService.kt`, `smb/SmbMediaProvider.kt`/`SmbClient.kt`, `local/LocalMediaProvider.kt`/`LocalFileScanner.kt`/`M3uParser.kt`

**Storage:** Room `ProviderEntity` (name, URL, username, type, config JSON, active flag), per-provider EncryptedSharedPreferences for passwords (`provider_creds_{id}`), per-provider cache SharedPreferences (`xtream_cache_{id}`).

**Jellyfin session tokens** stored in `provider_creds_{id}` prefs under keys `jellyfin_token` and `jellyfin_user_id`. `ProviderRepository.saveJellyfinSession(id, token, userId)` writes them. `updateProvider()` clears them for JELLYFIN providers to force re-auth on credential change.

**Jellyfin Quick Connect:** `POST /QuickConnect/Initiate` → display 6-digit code → poll `GET /QuickConnect/Connect?secret=…` every 3s (up to 2min) → `POST /Users/AuthenticateWithQuickConnect` → `saveJellyfinSession()`. UI in `TvAddProviderScreen` and `MobileAddProviderScreen` (add mode only).

**Navigation IDs:** All `String` (not `Int`) for Jellyfin/SMB/Local compatibility.
**Migration:** First launch auto-migrates single-provider `AccountManager` credentials to Room.

**Provider Files:** `provider/ProviderEntity.kt`, `provider/ProviderDao.kt`, `provider/ProviderDatabase.kt` (v2), `provider/ProviderRepository.kt`, `ProviderViewModel.kt`, `Tv/MobileProviderSelectionScreen.kt`, `Tv/MobileAddProviderScreen.kt`

## Workflow Rules
- Read this file at the start of every session.
- Before coding a UI feature, ask: "Is this D-pad friendly?"
- Use Haiku model for metadata, manifest updates, and documentation.
