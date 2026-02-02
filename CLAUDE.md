# Xtream IPTV Client - Project Context
A native Android application built with Kotlin and Jetpack Compose.
Targeting: Android Mobile, NVIDIA Shield, Chromecast with Google TV, and Sony Bravia (Android TV).

## 🛠 Tech Stack
- **UI:** 100% Jetpack Compose. Use `androidx.tv.material3` for TV-specific screens.
- **Networking:** Ktor with kotlinx.serialization (JSON).
- **Video Player:** Media3 (ExoPlayer). Optimize for 4K/HDR hardware acceleration.
- **Navigation:** Adaptive Navigation Suite (handles Mobile and TV D-Pad logic).

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

**Content Type Detection:** Automatically applied in `TvPlayerScreen` based on `contentType` parameter.

### Codec Prioritization Strategy
Hardware-accelerated codec selection based on device capabilities:
- **NVIDIA Shield:** AV1 → HEVC → AVC (prioritizes AV1/HEVC for 4K/HDR)
- **Sony Bravia:** HEVC → AVC (prioritizes HEVC for 4K)
- **Generic devices:** AVC (fallback to H.264)

### StreamingMediaSourceFactory Usage
Always use `StreamingMediaSourceFactory.createMediaSource()` for stream playback:
- Automatically detects stream type (HLS/DASH/MPEG-TS)
- Configures HTTP timeouts (30s connect, 60s read)
- **Supports custom headers for authentication** (auth tokens, CDN headers, user-agents)
- Enables cross-protocol redirects

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

## 📋 Coding Standards
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
- **Install on Shield/Sony:** `adb connect [TV_IP] && ./gradlew installDebug`
- **Lint Check:** `./gradlew ktlintCheck`

## 📱 App Navigation & Features

### Navigation Flow
The app follows this streamlined navigation structure:
1. **App Startup:**
   - **No Provider Configured:** Opens directly to Settings screen
   - **Provider Configured:** Auto-restores session → Content Type Selection
2. **Content Type Selection** (main landing page) - Choose Live TV, Movies, or TV Shows
3. **Category Grid** - Browse categories and streams/episodes
4. **Player Screen** - Video playback
5. **Settings** - Accessible from Content Type Selection via gear icon

**Note:** The login screen has been removed. Authentication happens automatically on startup or after configuring provider URL in Settings.

### Settings Screen
Accessible from the ContentTypeSelection screen via the gear icon (bottom left):
- **Provider URL Management:** Enter or change the Xtream provider URL with automatic authentication
- **Credentials Entry:** Username and password stored securely (encrypted SharedPreferences)
- **Last Watched Queue Size:** Configure the number of items to keep in the "Last Watched" virtual category (range: 1-100, default: 25)
- **Developer Mode:** Enable debug features including:
  - Stats for nerds (payload size tracking for API responses)
  - Payload size metrics displayed in category grid
  - Payload size tracking works even when loading from cache
  - Debug information for troubleshooting

### Watch History Tracking
Content-type specific watch history system:
- Tracks recently watched streams across all content types (Live TV, Movies, TV Shows)
- Configurable queue size via Settings (1-100 items)
- Maintains watch history per content type (separate tracking for each type)
- Enables "Last Watched" virtual category for quick access to recently viewed content

### Developer Mode Features
When enabled, provides debugging and performance insights:
- **Payload Size Tracking:** Monitor API response sizes in bytes (works with both network and cache)
- **Network Statistics:** View request/response metrics
- **Debug Info:** Additional diagnostics for network operations

### Content Types
The app supports three primary content types:
- **Live TV:** Live television channels and streams
- **Movies (VOD):** On-demand movie content
- **TV Shows:** Series and episodes with episode selection support

### Last Watched Virtual Category
A dynamically generated category that displays:
- Most recently watched streams across all content types
- Ordered chronologically (newest first)
- Automatically updated when streams are played
- Size configurable via Settings (default: 25 items)

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
- Max 50 channels displayed at once
- Requires EPG data from IPTV provider
- 30-minute cache refresh interval

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
**Available Controls:**
- **Pause/Resume:** Toggle playback
- **Audio Track:** Open audio track selector
- **Subtitle:** Enable/disable subtitles
- **Quality:** Select video quality
- **Back:** Exit to category grid
- **D-pad Up/Down:** Previous/Next channel (Live TV only)
- **Double-tap OK:** Toggle stats overlay

## ⚠️ Workflow Rules
- Read this file at the start of every session.
- Before coding a UI feature, ask: "Is this D-pad friendly?"
- Use Haiku model for metadata, manifest updates, and documentation.