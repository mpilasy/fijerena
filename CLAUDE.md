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

### LoadControl Optimization for IPTV Live Streaming
Configured for fast channel switching (zapping speed) and minimal latency:
- `minBufferMs: 2000ms` - Minimal buffering for live streams
- `maxBufferMs: 5000ms` - Avoid over-buffering live content
- `bufferForPlaybackMs: 250ms` - Fast startup/channel switching
- `bufferForPlaybackAfterRebufferMs: 500ms` - Quick recovery from rebuffering
- `backBufferDurationMs: 0ms` - No back buffer for live streams

### Codec Prioritization Strategy
Hardware-accelerated codec selection based on device capabilities:
- **NVIDIA Shield:** AV1 → HEVC → AVC (prioritizes AV1/HEVC for 4K/HDR)
- **Sony Bravia:** HEVC → AVC (prioritizes HEVC for 4K)
- **Generic devices:** AVC (fallback to H.264)

### StreamingMediaSourceFactory Usage
Always use `StreamingMediaSourceFactory.createMediaSource()` for stream playback:
- Automatically detects stream type (HLS/DASH/MPEG-TS)
- Configures HTTP timeouts (8s connect/read)
- Supports custom headers for authentication
- Enables cross-protocol redirects

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

## 📱 App Features

### Settings Screen (`AppSettings`)
The Settings screen provides user configuration options:
- **Provider URL Management:** Change the Xtream provider URL without re-entering credentials
- **Last Watched Queue Size:** Configure the number of items to keep in the "Last Watched" virtual category (range: 1-100, default: 25)
- **Developer Mode:** Enable debug features including:
  - Stats for nerds (payload size tracking for API responses)
  - Payload size metrics displayed in category grid
  - Debug information for troubleshooting

### Watch History Tracking
Content-type specific watch history system:
- Tracks recently watched streams across all content types (Live TV, Movies, TV Shows)
- Configurable queue size via Settings (1-100 items)
- Maintains watch history per content type (separate tracking for each type)
- Enables "Last Watched" virtual category for quick access to recently viewed content

### Developer Mode Features
When enabled, provides debugging and performance insights:
- **Payload Size Tracking:** Monitor API response sizes in bytes
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

## ⚠️ Workflow Rules
- Read this file at the start of every session.
- Before coding a UI feature, ask: "Is this D-pad friendly?"
- Use Haiku model for metadata, manifest updates, and documentation.