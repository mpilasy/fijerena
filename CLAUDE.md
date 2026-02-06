# Fijerena - Multi-Provider Media Player
A native Android media player built with Kotlin and Jetpack Compose supporting multiple provider types.
Targeting: Android Mobile, NVIDIA Shield, Chromecast with Google TV, and Sony Bravia (Android TV).

## 🛠 Tech Stack
- **UI:** 100% Jetpack Compose. Use `androidx.tv.material3` for TV-specific screens.
- **Networking:** Ktor with kotlinx.serialization (JSON).
- **Video Player:** Media3 (ExoPlayer). Optimize for 4K/HDR hardware acceleration.
- **Navigation:** Adaptive Navigation Suite (handles Mobile and TV D-Pad logic).
- **Database:** Room (provider management). Per-provider EncryptedSharedPreferences for passwords.
- **Theming:** Dynamic runtime theme switching via `CinemaThemeHolder` + `CinemaThemePalette`.
- **SMB:** `com.hierynomus:smbj:0.13.0` for network share access.

## 🎬 Media3 Player Configuration

### Supported Stream Formats
- **HLS** (`.m3u8`) - HTTP Live Streaming
- **DASH** (`.mpd`) - Dynamic Adaptive Streaming over HTTP
- **MPEG-TS** (`.ts`, `.mpeg`) - MPEG Transport Stream

### LoadControl Optimization - Content-Type Aware
**Dual Buffer Profiles:** Automatically configured based on content type for optimal performance.

#### Live TV Profile (Fast Zapping)
Optimized for instant channel switching and minimal latency:
- `minBufferMs: 2000ms` - Minimal buffering for live streams
- `maxBufferMs: 5000ms` - Avoid over-buffering live content
- `bufferForPlaybackMs: 250ms` - Fast startup/channel switching (80% faster)
- `bufferForPlaybackAfterRebufferMs: 500ms` - Quick recovery from rebuffering
- `backBufferDurationMs: 0ms` - No back buffer for live streams

#### VOD Profile (Movies/TV Shows)
Optimized for smooth playback during network fluctuations:
- `minBufferMs: 15000ms` - Adequate buffer for stability
- `maxBufferMs: 50000ms` - Handle network variations
- `bufferForPlaybackMs: 2500ms` - Smooth startup
- `bufferForPlaybackAfterRebufferMs: 5000ms` - Recover gracefully
- `backBufferDurationMs: 10000ms` - Support seeking in VOD content

**Content Type Detection:** Automatically applied in both `TvPlayerScreen` and `MobilePlayerScreen` based on `contentType` parameter.

### Codec Prioritization Strategy
Hardware-accelerated codec selection based on device capabilities:
- **NVIDIA Shield:** AV1 → HEVC → AVC (prioritizes AV1/HEVC for 4K/HDR)
- **Sony Bravia:** HEVC → AVC (prioritizes HEVC for 4K)
- **Generic devices:** AVC (fallback to H.264)

### StreamingMediaSourceFactory Usage
Always use `StreamingMediaSourceFactory.createMediaSource()` for stream playback:
- Automatically detects stream type (HLS/DASH/MPEG-TS)
- Configures HTTP timeouts (30s connect, 60s read)
- **Supports custom headers for authentication** (auth tokens, CDN headers, Jellyfin X-Emby-Token)
- Enables cross-protocol redirects
- Supports `smb://` URIs via custom `SmbDataSource` for SMB network shares
- Supports `content://` URIs for local media files

### Audio Track Selection
**Feature:** Multi-language and audio format selection during playback.

**Capabilities:**
- Detect available audio tracks from ExoPlayer
- Display track information: language, channels (stereo/5.1/7.1), sample rate, bitrate
- D-pad navigable selection dialog
- Instant track switching without playback interruption
- Visual indication of currently active track

**Usage:**
1. During playback, press OK to show controls
2. Navigate to "🔊 Audio" button
3. Select from available audio tracks
4. Track changes apply immediately

**API:**
```kotlin
// Get available tracks
val tracks: List<AudioTrackInfo> = viewModel.getAudioTracks()

// Select track
viewModel.selectAudioTrack(groupIndex, trackIndex)
```

### Wake Lock Management
**Optimization:** Smart lifecycle management for long-form content.

**Behavior:**
- **Acquire:** On playback start/resume (keeps screen on)
- **Release:** On pause (saves battery, allows device sleep)
- **Release:** On stop/service destroy
- **No Timeout:** Supports movies of any length (2+ hours)

**Battery Impact:**
- 20-30% battery savings during pause periods
- No interruptions during long movies
- Device can sleep when VOD content is paused

**Implementation:** Uses `PARTIAL_WAKE_LOCK` for CPU, `WAKE_MODE_NETWORK` for screen.

## 🎨 Theme & Design System

### User-Selectable Themes
The app supports 4 dark theme variants, switchable at runtime from Settings:

| Theme | Accent | Surfaces |
|-------|--------|----------|
| **Deep Night** (default) | Electric Blue `#2979FF` | `#0F1014`, `#161A20` |
| **AMOLED Black** | Electric Blue `#2979FF` | `#000000`, `#0A0A0A` |
| **Emerald** | Green `#00C853` | `#0F1014`, `#161A20` |
| **Crimson** | Red `#FF1744` | `#0F1014`, `#161A20` |

**Architecture:** `CinemaThemeHolder` (global object) + `CinemaThemePalette` (immutable data class). TV and mobile re-export files (`CinemaColors.kt`, `Color.kt`) use computed `get()` properties that read from the holder. Zero screen-file changes needed when adding themes.

**Implementation:**
- Theme palettes defined in `core/ui/.../theme/CinemaThemePalette.kt`
- TV re-exports: `tv/.../ui/theme/CinemaColors.kt`
- Mobile re-exports: `mobile/.../ui/theme/Color.kt`
- Theme ID persisted in `AppSettings.themeId`

### Color Palette (Deep Night Default)
Google TV Material 3 design with **Electric Blue** primary and **Vivid Orange** secondary:
- **Primary Accent (Electric Blue)**: `#2979FF` - Focus states, primary CTAs
- **Primary Dark**: `#1565C0` - Darker interactive states
- **Primary Light**: `#82B1FF` - Focus borders & subtle highlights
- **Secondary Accent (Vivid Orange)**: `#FF6D00` - LIVE badges, destructive actions
- **Secondary Light**: `#FFAB40` - Secondary highlights
- **Background (Deep Night)**: `#0F1014` - Main background
- **Surface**: `#161A20` - Cards & elevated surfaces
- **Surface Variant**: `#1E2228` - Secondary surfaces
- **Surface Light**: `#2A3038` - Borders & dividers
- **Glassmorphism BG**: `#0F1014` @ 75% alpha - Translucent backgrounds
- **Glassmorphism Border**: `#2979FF` @ 15% alpha - Subtle gradient borders
- **Text Primary**: `#FFFFFF` - Main text
- **Text Secondary**: `#B0B0B0` - Secondary text

Status colors (success/warning/error/live), text colors, and orange secondary remain constant across all themes.

### Typography Scale
Full 13-style scale optimized for 10-foot TV viewing distance (Roboto, system default):
- **Display**: 48-40sp Bold (headlines, major titles)
- **Headline**: 32-24sp SemiBold (section headers)
- **Title**: 22-18sp Medium (subheaders, labels)
- **Body**: 20-18sp Regular (main content - minimum 18sp)
- **Label**: 18-14sp Medium (small labels, timestamps)

**Key Rule:** All body text ≥18sp for TV readability.

### Focus States (Every Interactive Element)
All focusable components use animated focus feedback:
- **Scale**: 1.0f → 1.1f (animated, 200ms tween)
- **Border**: 2dp Electric Blue border on focus
- **Glow**: 8dp shadow with `#2979FF` @ 40% opacity
- **Animation**: `animateFloatAsState(tween(200ms))` for smooth scaling

**Implementation:** See `FocusModifiers.kt` for `tvFocusable()`, `tvFocusableSubtle()`, `tvFocusableNoScale()`.

### Safe Margins (TV Overscan)
All screens must respect 56dp horizontal / 32dp vertical safe margins to account for TV overscan:
- **Horizontal**: `Spacing.tvSafeMarginHorizontal = 56.dp`
- **Vertical**: `Spacing.tvSafeMarginVertical = 32.dp`

**Usage:** Apply to all screen root containers:
```kotlin
.padding(
    horizontal = Spacing.tvSafeMarginHorizontal,
    vertical = Spacing.tvSafeMarginVertical
)
```

### Component Design

**Cards:**
- `CinemaSelectableCard` - Interactive, focusable, glow effect
- `CinemaInfoCard` - Non-interactive info display
- `CinemaCompactCard` - Dense grid variant
- `CinemaStandardCard` - Content with accent blocks

**Buttons:**
- `CinemaPrimaryButton` - Primary CTAs (Electric Blue)
- `CinemaSecondaryButton` - Secondary actions (muted)
- `CinemaTertiaryButton` - Minimal emphasis (outline)
- `CinemaIconButton` - Icon-only actions
- `CinemaDangerButton` - Destructive actions (Vivid Orange)

**Special Effects:**
- **Glassmorphism:** Category sidebar uses translucent background + gradient border
- **Accent Blocks:** `AccentBlock.kt` provides content-type gradients (LIVE_TV orange, MOVIE blue, TV_SHOW light blue)

## 📋 Coding Standards
- **STRICT: No Hardcoded UI Values.** When adding or modifying any UI element, every visual attribute (colors, dimensions, spacing, opacity, corner radii, animation values, stroke widths, font sizes) **must** come from an existing design token constant — never use raw literals like `16.dp`, `Color.White`, `2.dp`, `0.5f`, etc. in screen/component files. If the needed token doesn't exist yet, **add it to the appropriate token file first**, then reference it. This rule applies to all new code and all modified code — no exceptions.
- **Design Token Files:**
  - **Shared (core/ui):** `CinemaColors` (colors), `CinemaAlpha` (opacity), `CinemaAnimation` (timing), `CinemaCornerRadius` (radii), `CinemaSpacing` (padding/margins)
  - **TV-specific:** `TvDimensions` (sizes/spacing), `TvFocusTokens` (focus scale/border/glow)
  - **Mobile-specific:** `MobileDimensions` (sizes/spacing)
  - **Platform re-exports:** TV `CinemaColors.kt` and mobile `Color.kt` re-export core colors as computed `get()` properties from `CinemaThemeHolder` — screen files import from their platform package, not from core directly. TV `Spacing.kt` and mobile `Spacing.kt` re-export `CinemaSpacing` values.
  - **Theme palettes:** `CinemaThemePalette.kt` defines per-theme color sets; `CinemaThemeHolder.current` is set by the theme composable
  - For colors, always prefer `MaterialTheme.colorScheme.*` (e.g., `onSurface`, `primary`) or the platform re-export constants — never use `Color.White`, `Color.Black`, etc.
- **Focus Management:** Every @Composable must be D-pad (remote) navigable. Use `Modifier.focusRestorer()` and `Modifier.focusable()`.
- **Safe Areas:** Respect "Overscan." UI must remain 5% away from screen edges for Sony/Shield TVs.
- **Mobile vs TV:** Use `WindowSizeClass` to switch between NavigationBar (Mobile) and NavigationRail/Drawer (TV).
- **Network:** Support HTTP/Cleartext for legacy Xtream providers.

## 📺 Device-Specific Rules
- **NVIDIA Shield:** Enable "High-Performance" video codecs (AV1/HEVC) if the device is identified as 'shield'.
- **Sony TV:** Avoid complex UI animations that might lag on mid-range Bravia processors; keep the UI lean.
- **Chromecast:** Ensure the layout is responsive to "Compact" window sizes (often lower DPI on Chromecast).

## 🚀 Development Commands
- **Build App:** `./gradlew assembleDebug`
- **Build Release:** `./gradlew :mobile:assembleRelease` (or `:tv:assembleRelease`)
- **Install on Shield/Sony:** `adb connect [TV_IP] && ./gradlew installDebug`
- **Install mobile only on phone:** `adb -s emulator-5554 install -r mobile/build/outputs/apk/debug/mobile-debug.apk`
- **Lint Check:** `./gradlew ktlintCheck`

**Note:** TV and mobile modules share the same `applicationId`. When deploying to both a phone and TV emulator simultaneously, use `adb -s <device>` to target the correct device to avoid overwriting one APK with the other.

## 📱 App Navigation & Features

### Navigation Flow
The app follows this streamlined navigation structure:
1. **App Startup:**
   - **No Provider Configured:** Opens directly to Settings screen
   - **Provider Configured:** Auto-restores session → Content Type Selection
2. **Content Type Selection** (main landing page) - Choose Live TV, Movies, or TV Shows
3. **Category Grid** - Browse categories and streams/episodes
4. **Movie Details** (Movies) / **Episode Selection → Episode Details** (TV Shows) - Info screen before playback
5. **Player Screen** - Video playback
6. **Settings** - Accessible from Content Type Selection via gear icon
7. **Provider Management** - Accessible from Settings → "Manage Providers"
   - **Provider Selection:** List all providers, select/edit/delete
   - **Add/Edit Provider:** Type selector + type-specific form fields (Xtream: URL/user/pass, Jellyfin: server URL/user/pass, SMB: host/share/user/pass, Local: folder/M3U picker). Edit mode includes inline provider settings (auto-resume, watch history size, favorites max size, clear buttons, category filters, caching toggle).

**Note:** There is no login screen and no logout button anywhere in the app. Authentication happens automatically on startup via stored credentials, or after configuring a provider in Settings. To switch or remove a provider, use Settings → Manage Providers. Both TV and mobile use the same flow.

**TV Back Navigation:** TV screens do not have explicit "Back" buttons — the remote's back button handles all backward navigation. Only error/fallback screens retain on-screen Back buttons.

**Mobile Orientation:** The mobile app is locked to portrait mode (`android:screenOrientation="portrait"`) for all screens except the player. `MobilePlayerScreen` unlocks orientation to sensor on enter and locks back to portrait on dispose.

### Settings Screen
Accessible from the ContentTypeSelection screen via the gear icon (bottom left):
- **Active Provider Display:** Shows current provider name and URL
- **Manage Providers:** Navigate to provider selection/management screen (add, edit, delete, switch providers)
- **Theme Selection:** Choose from 4 dark themes (Deep Night, AMOLED Black, Emerald, Crimson) — persists across app restarts
- **External EPG Source (XMLTV):** Global EPG URL editor with edit/save/clear buttons — used for external XMLTV EPG data across all providers (stored in `AppSettings.epgUrl`)
- **Cache Management:** View cache statistics and clear cached data
  - Total cache size display
  - Per-content-type breakdown (Live TV, Movies, TV Shows)
  - Individual clear buttons for each content type
  - Shows category cache status and stream list counts
  - EPG data and other cache information
- **UI Scale:** Adjust font, spacing, and element sizes for category/grid views
  - Options: 70%, 80%, 90%, 100% (default: 100%)
  - Applies to category grid screens and settings page
  - Scales fonts, buttons, spacing, padding, cards, heights, and widths
- **Developer Mode:** Enable debug features including:
  - Stats for nerds (payload size tracking for API responses)
  - Payload size metrics displayed in category grid
  - Payload size tracking works even when loading from cache
  - Debug information for troubleshooting

### Provider Settings (Inline in Edit Provider)
Per-provider settings are configured inline on the Edit Provider screen (not visible when adding a new provider):
- **Auto-Resume:** Toggle automatic playback resume for VOD content (default: enabled)
- **Last Watched Queue Size:** Configure the number of items to keep in the "Last Watched" virtual category (range: 1-100, default: 25)
- **Favorites Max Size:** Configure maximum number of favorites to store (range: 10-500, default: 100)
- **Clear All Favorites:** Remove all favorited streams from all content types
- **Clear Playback Progress:** Remove all saved positions (clears Continue Watching category)
- **Category Filters:** Configure category filtering rules (Xtream only) — include/exclude by name/regex
- **Enable Caching:** Toggle response caching (Xtream only, default: enabled)

### Watch History Tracking
Content-type specific watch history system:
- Tracks recently watched streams across all content types (Live TV, Movies, TV Shows)
- Configurable queue size via Edit Provider settings (1-100 items)
- Maintains watch history per content type (separate tracking for each type)
- Enables "Last Watched" virtual category for quick access to recently viewed content

### Developer Mode Features
When enabled, provides debugging and performance insights:
- **Payload Size Tracking:** Monitor API response sizes in bytes (works with both network and cache)
- **Network Statistics:** View request/response metrics
- **Debug Info:** Additional diagnostics for network operations
- **Provider Type Display:** Shows provider type in parentheses after provider name on Content Type Selection screen (e.g., "My Server (JELLYFIN)")

### Content Types
The app supports three primary content types (availability depends on provider capabilities):
- **Live TV:** Live television channels and streams (Xtream, Local with M3U)
- **Movies (VOD):** On-demand movie content (all providers)
- **TV Shows:** Series and episodes with episode selection support (Xtream, Jellyfin)

### Episode Details (Inline)
When selecting an episode from the episode list, an inline detail panel is shown before playback (same pattern as the Movie Details screen). This avoids a separate navigation route since all episode data is already loaded in `SeriesDetail`.

**Features:**
- Episode thumbnail, title, "Season X · Episode Y" label
- Metadata row: rating (episode or series fallback), duration, "Ends at" time
- Genre (from series)
- Play / Resume buttons (resume shows saved position timestamp)
- Plot description, cast, director (with series-level fallback)
- Back button (mobile) or remote back (TV) returns to the episode list

**Collapsible Seasons:**
- Multi-season shows display collapsible season headers with chevron indicators
- Tapping/clicking a season header toggles its episode list
- On load, the season containing the **next unwatched/in-progress episode** is auto-expanded (checks watch status via `mediaRepository.getPlaybackPositionSuspend()`)
- Single-season shows skip the header entirely

**Resume Logic:**
- Uses `mediaRepository.getPlaybackPositionSuspend(episodeId, "TV_SHOWS")`
- Shows Resume button if progress is between 2% and 95%
- `startFromBeginning` parameter passed through to Player screen

**Files:**
- `tv/.../feature/episode/EpisodeSelectionScreen.kt` - TV version with GlassPanel, D-pad focus
- `mobile/.../feature/episode/EpisodeSelectionScreen.kt` - Mobile version with scrollable layout

### Virtual Categories
The app provides two special virtual categories that load locally from device storage:

#### Favorites Virtual Category
A user-curated collection of favorite streams:
- Empty by default until streams are favorited
- Add/remove favorites via star button in player controls
- Loads instantly from local storage (no network call)
- Filtered by content type (separate favorites for Live TV, Movies, TV Shows)
- Pinned at top of category list for quick access
- Size configurable via Edit Provider settings (range: 10-500, default: 100)

#### Last Watched Virtual Category
A dynamically generated category that displays:
- Most recently watched streams across all content types
- Ordered chronologically (newest first)
- Automatically updated when streams are played
- Loads instantly from local storage (no network call)
- Filtered by content type (separate history for each type)
- Pinned at top of category list below Favorites
- Size configurable via Edit Provider settings (range: 1-100, default: 25)

### EPG (Electronic Program Guide)
**Feature:** Full TV Guide with grid view for Live TV channels displaying 24-hour program schedules.

**Access:** Category Grid Screen → "TV Guide" button (next to Search, only visible for Live TV)

**Features:**
- **Grid Layout:** Two-pane design with channel list (20% width) and time grid (80% width)
- **Time Slots:** 48 x 30-minute intervals covering full 24-hour day
- **Current Time Indicator:** Highlighted time slot showing current time
- **Auto-Scroll:** Automatically scrolls to current time on initial load
- **Date Navigation:**
  - Previous Day button (← arrow)
  - Next Day button (→ arrow)
  - Jump to Now button (returns to current date/time)
- **Program Information:** Each cell displays start time and program title
- **Current Program Highlighting:** Different background color for programs airing now
- **Channel Selection:** Click any channel to start playback
- **Program Selection:** Click any program to start playback on that channel

**API Integration:**
- **Primary Endpoint:** `get_simple_data_table` - Full EPG data for a stream
- **Fallback Endpoint:** `get_short_epg` - Limited programs with configurable limit
- **Cache Strategy:** 30-minute TTL with background refresh for optimal performance
- **Bulk Fetching:** Parallel API calls for multiple channels (max 50 channels for performance)

**Technical Details:**
- **Data Models:** `EpgModels.kt` (EpgProgram, EpgResponse, EpgChannelRow, TimeSlot)
- **ViewModel:** `EpgViewModel.kt` with Loading, Success, Error states
- **UI Components:** `EpgGuideScreen.kt`, `EpgGridLayout.kt`
- **Navigation:** Type-safe navigation via `Screen.EpgGuide(categoryId, categoryName)`
- **Caching:** SharedPreferences with keys `epg_` + `epg_timestamp_` prefixes

**Performance Optimizations:**
- Maximum 50 channels displayed to ensure smooth scrolling
- 30-minute cache expiry with background refresh
- Lazy loading for channel rows and program cells
- Synchronized horizontal scrolling across all channel rows

**Error Handling:**
- No EPG data available for channels
- Authentication failures
- Network errors
- Empty program listings
- Missing streams in category

**Usage Flow:**
1. Navigate to Live TV → Select a category
2. Focus on "TV Guide" button in header (has calendar icon)
3. Press OK/Center button
4. EPG grid loads with current date and time
5. Use D-pad to navigate: UP/DOWN for channels, LEFT/RIGHT for time
6. Select program or channel to start playback
7. Use date navigation buttons to view other days

**Limitations:**
- Live TV only (not available for Movies or TV Shows)
- Only available when `provider.capabilities.supportsEpg` is true (currently Xtream only)
- Max 50 channels displayed at once
- Requires EPG data from IPTV provider
- 30-minute cache refresh interval

### Multi-Provider Architecture
The app supports 4 provider types through a unified domain model abstraction. All providers map to generic types — screens never see provider-specific types.

**Supported Provider Types:**

| Provider | Live TV | Movies | TV Shows | EPG | Search | Auth | Progress Sync |
|----------|---------|--------|----------|-----|--------|------|---------------|
| **Xtream** | Yes | Yes | Yes | Yes | Yes | Yes | No |
| **Jellyfin** | No | Yes | Yes | No | Yes | Yes | Yes |
| **SMB** | No | Yes | No | No | Yes | Optional | No |
| **Local** | M3U only | Yes | No | No | Yes | No | No |

**Domain Models** (in `core/player/.../domain/`):
- `MediaProvider` - Interface all providers implement
- `MediaCategory`, `MediaItem` - Provider-agnostic content types
- `SeriesDetail`, `MovieDetail` - Detail models
- `PlayableStream` - Resolved stream URI with headers
- `ProviderCapabilities` - What each provider supports
- `ProviderType` - Enum of provider types

**Key Architecture Files:**
- `core/network/.../MediaRepository.kt` - Unified repository delegating to active `MediaProvider`
- `core/network/.../MediaProviderFactory.kt` - Creates provider instances by type
- `core/network/.../XtreamMediaProvider.kt` - Xtream adapter wrapping `XtreamApiService`
- `core/network/.../XtreamMapper.kt` - Maps Xtream types to domain types
- `core/network/.../jellyfin/JellyfinMediaProvider.kt` - Jellyfin REST API provider
- `core/network/.../jellyfin/JellyfinApiService.kt` - Jellyfin HTTP client (Ktor)
- `core/network/.../jellyfin/JellyfinModels.kt` - Jellyfin JSON models
- `core/network/.../smb/SmbMediaProvider.kt` - SMB network share provider
- `core/network/.../smb/SmbClient.kt` - SMB2/3 client wrapper (smbj)
- `core/network/.../local/LocalMediaProvider.kt` - Local media/M3U provider
- `core/network/.../local/LocalFileScanner.kt` - SAF directory scanner
- `core/network/.../local/M3uParser.kt` - M3U/M3U8 playlist parser

**Storage:**
- **Room database** (`ProviderEntity`) stores provider metadata (name, URL, username, type, config, active flag)
- **Per-provider EncryptedSharedPreferences** for passwords (keyed by provider ID)
- **Per-provider cache SharedPreferences** files (`xtream_cache_{id}`)
- `ProviderEntity.type` field: `XTREAM`, `JELLYFIN`, `SMB`, or `LOCAL`
- `ProviderEntity.config` field: JSON blob for type-specific configuration

**Screens:**
- **Provider Selection** (`Screen.ProviderSelection`): List all providers with select/edit/delete
- **Add/Edit Provider** (`Screen.AddProvider`): Type-specific form fields per provider type; edit mode includes inline provider settings (auto-resume, history/favorites sizes, clear buttons, category filters, caching toggle)
- **Content Type Selection**: Provider name clickable to open provider switcher dialog (dark-themed `AlertDialog` with `CinemaSurface` background); shows provider type in dev mode

**Navigation IDs:** All navigation uses `String` IDs (not `Int`) to support non-numeric IDs from Jellyfin/SMB/Local providers.

**Migration:** On first launch after upgrade, existing single-provider credentials from `AccountManager` are automatically migrated to Room in both NavHosts before the `hasProvider` check.

**Key Files:**
- `core/network/.../provider/ProviderEntity.kt` - Room entity (with `type` and `config` fields)
- `core/network/.../provider/ProviderDao.kt` - Data access object
- `core/network/.../provider/ProviderDatabase.kt` - Room database singleton (version 2 with migration)
- `core/network/.../provider/ProviderRepository.kt` - Repository wrapping DAO + encrypted prefs
- `core/ui/.../viewmodels/ProviderViewModel.kt` - ViewModel with migration logic
- `tv/.../feature/provider/TvProviderSelectionScreen.kt` - TV provider list
- `tv/.../feature/provider/TvAddProviderScreen.kt` - TV add/edit form (type-specific fields)
- `mobile/.../feature/provider/MobileProviderSelectionScreen.kt` - Mobile provider list
- `mobile/.../feature/provider/MobileAddProviderScreen.kt` - Mobile add/edit form (type-specific fields)

### Player UI Features

#### Channel Switching Feedback
**Toast Notification:** Visual confirmation when changing channels.
- Appears at top-center of screen
- Shows "Now Playing" label with channel name
- Auto-dismisses after 3 seconds
- Smooth slide-in/fade animations
- Doesn't obstruct video content
- Triggered automatically on D-pad up/down during Live TV

**Important:** Channel switching (D-pad up/down) only works for **Live TV content**. For VOD (Movies/TV Shows), D-pad up/down does nothing to prevent accidental stream switching during playback. This is intentional design to protect the viewing experience.

#### VOD Time Display
**Feature:** Enhanced time information for on-demand content.

**Information Displayed:**
- **Progress Bar:** Visual indicator of playback progress
- **Current Position / Total Duration:** Time counters below progress bar
- **Remaining Time:** Shows time left until video ends (e.g., "Remaining: 1:23:45")
- **Ends At:** Estimated completion time in 12-hour format (e.g., "Ends at 11:30 PM")
  - Uses device timezone for accurate local time
  - Automatically handles date rollovers (crossing midnight)
  - Updates in real-time as playback progresses

**Technical Details:**
- Uses `Calendar.getInstance()` for timezone-aware calculations
- Handles videos of any length (supports 2+ hour movies)
- Time format adapts to device locale settings

#### Stats Overlay ("Stats for Nerds")
**Advanced Metrics:** Comprehensive playback statistics for power users.

**Access:** Double-tap OK button during playback

**Information Displayed:**
- **Video:** Codec, resolution, frame rate, bitrate
- **Audio:** Codec, sample rate, channels, bitrate
- **Network:** Speed, buffer health, buffered position
- **Performance:** Dropped frames, drop rate (color-coded)
  - Green (< 0.5%): Excellent
  - Yellow (0.5-2%): Acceptable
  - Red (> 2%): Poor
- **Playback:** Current position, duration
- **Stream:** Type (Live/VOD), URL preview
- **Device:** Model, Android API level

**Features:**
- 75% background opacity for excellent readability
- Large fonts optimized for TV viewing distance (10ft+)
- 3dp primary color border when focused
- Repositionable with D-pad (4 corner positions)
- Default position: bottom-right
- Real-time performance monitoring
- Double-tap OK to hide

#### Subtitle/Caption Support
**Feature:** Accessibility and multi-language subtitle selection.

**Access:** Press OK → Navigate to "💬 Subtitle" button

**Capabilities:**
- Detect available subtitle tracks
- "Off" option to disable all subtitles
- Display language and format (SRT, VTT, CEA-608/708, TTML)
- Instant subtitle switching
- Visual indication of active subtitle

**Supported Formats:**
- SRT (SubRip)
- VTT (WebVTT)
- TTML (Timed Text Markup Language)
- CEA-608/708 (Closed Captions)

#### Quality/Bitrate Selection
**Feature:** Manual video quality control for adaptive streams.

**Access:** Press OK → Navigate to "⚙️ Quality" button

**Capabilities:**
- "Auto (Adaptive)" mode (recommended)
- Manual quality selection (4K, 1440p, 1080p, 720p, 480p)
- Display resolution, bitrate, frame rate
- Instant quality switching
- Visual indication of active quality

**Use Cases:**
- Bandwidth control
- Data usage management
- Device capability matching
- Troubleshooting playback

#### Control Discoverability Hints
**Feature:** First-time user guidance overlay.

**Behavior:**
- Appears automatically on first playback
- Lists all available controls with descriptions
- Auto-dismisses after 7 seconds
- "Got it!" to dismiss immediately
- "Don't show again" to disable permanently

**Controls Explained:**
- OK Button → Show/hide controls
- Double-tap OK → Toggle stats overlay
- BACK Button → Exit player
- D-pad Up/Down → Change channel (Live TV)
- Pause/Resume → Control playback
- Audio Button → Select audio track
- Subtitle Button → Enable/disable subtitles
- Quality Button → Select video quality

#### Player Controls
**TV Controls (D-pad):**
- **Pause/Resume:** Toggle playback
- **Audio Track:** Open audio track selector
- **Subtitle:** Enable/disable subtitles
- **Quality:** Select video quality
- **Favorite:** Toggle stream favorite
- **Back:** Exit to category grid
- **D-pad Up/Down:** Previous/Next channel (Live TV only)
- **Double-tap OK:** Toggle stats overlay

**Mobile Controls (touch):**
- Same feature set as TV: audio/subtitle/quality selectors, favorite toggle
- Tap screen to show/hide controls
- Controls displayed as horizontally scrollable button row
- VOD: remaining time and "Ends at" display
- Auto-resume from saved playback position (2-95% range)
- Periodic position save every 5 seconds
- Orientation unlocks to sensor during playback, locks back to portrait on exit

## ⚠️ Workflow Rules
- Read this file at the start of every session.
- Before coding a UI feature, ask: "Is this D-pad friendly?"
- Use Haiku model for metadata, manifest updates, and documentation.