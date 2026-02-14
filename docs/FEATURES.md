# Xtream IPTV Client - Features Documentation

A native Android application for streaming IPTV content across Android Mobile, NVIDIA Shield, Chromecast with Google TV, and Sony Bravia (Android TV) devices.

## Core Features

### 1. Authentication & Provider Management
- **No Login Screen:** Authentication happens automatically on startup or after configuring a provider in Settings
- **Session Management:** Automatic session restoration from stored credentials on app restart
- **Multiple Providers:** Room database stores provider metadata; add, edit, delete, and switch between providers
- **Credential Storage:** Per-provider EncryptedSharedPreferences for passwords
- **Provider Migration:** Automatic one-time migration of legacy single-provider credentials to Room on first launch
- **Provider Support:** HTTP/Cleartext support for legacy Xtream providers

### 2. Content Management

#### Content Types
The application supports three primary content types:

| Content Type | Description | Navigation |
|---|---|---|
| **Live TV** | Live television channels and streams | Direct playback from category grid |
| **Movies (VOD)** | Video-on-demand movie content | Select movie → Movie details → Play |
| **TV Shows** | Series with seasons and episodes | Select series → Episode selection → Play |

#### Content Discovery
- **Categories:** Organize content by provider-defined categories
- **Category Grid:** Two-column layout with categories on the left and streams on the right
- **Last Watched:** Virtual category displaying recently viewed streams (configurable size)
- **Multi-Type Support:** Separate category lists for each content type

### 3. Playback Features

#### Stream Format Support
The Media3 player supports multiple stream formats:
- **HLS** (`.m3u8`) - HTTP Live Streaming
- **DASH** (`.mpd`) - Dynamic Adaptive Streaming over HTTP
- **MPEG-TS** (`.ts`, `.mpeg`) - MPEG Transport Stream

#### Playback Optimization
- **Fast Channel Switching:** Optimized for live TV zapping (minimal latency)
- **Hardware Acceleration:** Codec prioritization based on device capabilities:
  - NVIDIA Shield: AV1 → HEVC → AVC
  - Sony Bravia: HEVC → AVC
  - Generic devices: AVC (H.264)
- **4K/HDR Support:** Hardware-accelerated rendering for compatible devices
- **Buffer Configuration:**
  - Minimal buffering: 2000ms
  - Maximum buffering: 5000ms
  - Playback startup: 250ms
  - Rebuffer recovery: 500ms
  - No back buffer for live streams

### 4. Settings & Configuration

#### Settings Screen Features

**Active Provider Display**
- Shows current provider name and URL
- "Manage Providers" button navigates to provider selection screen

**Theme Selection**
- 4 dark theme variants: Deep Night (default), AMOLED Black, Emerald, Crimson
- Visual preview with color dots for each theme
- Persists selection across app restarts via AppSettings
- Dynamic runtime switching without app restart

**Manage Providers**
- View all configured providers in a list
- Select a provider to make it active
- Edit provider details (name, URL, username, password)
- Delete providers with confirmation
- Add new providers via form

**Last Watched Queue Size**
- Adjustable configuration (range: 1-100 items)
- Default: 25 items
- Numeric validation with range enforcement
- Real-time persistence to SharedPreferences

**Developer Mode**
- Toggle switch for enabling debug features
- Real-time enable/disable without app restart
- Persistent state storage
- Stats for nerds: payload size tracking

#### AppSettings Configuration
Configuration stored in `SharedPreferences` with key-value pairs:
```kotlin
- isDevMode: Boolean (default: false)
- watchHistorySize: Int (default: 25, range: 1-100)
- themeId: String (default: "deep_night")
- favoritesMaxSize: Int (default: 100, range: 10-500)
- autoResume: Boolean (default: true)
- uiScale: Float (default: 1.0)
```

### 5. Watch History & Last Watched

#### Watch History Tracking
- **Content-Type Specific:** Separate tracking for Live TV, Movies, and TV Shows
- **Automatic Recording:** Streams tracked when playback begins
- **Chronological Order:** Most recently watched appears first
- **Configurable Size:** User-adjustable queue size (1-100 items)
- **Virtual Category:** "Last Watched" category dynamically generated

#### Data Persistence
- Watch history stored per content type
- Automatic cleanup when queue exceeds configured size
- Survives app restarts

### 6. Navigation

#### Navigation Structure
The application uses type-safe navigation with Jetpack Navigation Compose:

```
App Startup
    ├─→ No provider → Settings
    └─→ Provider exists → Auto-restore session
                              ↓
Content Type Selection (with Settings gear icon)
    ├─→ Live TV Categories
    │     ├─→ Live TV Stream Player
    │     ├─→ Search
    │     └─→ EPG Guide (TV Guide)
    │
    ├─→ Movie Categories
    │     ├─→ Movie Details Screen → Movie Player
    │     └─→ Search
    │
    └─→ TV Show Categories
          ├─→ Episode Selection (by Season) → Episode Player
          └─→ Search

Settings
    └─→ Manage Providers
          ├─→ Provider Selection (list, select, delete)
          └─→ Add/Edit Provider (form)
```

#### Navigation Destinations
- **ContentTypeSelection:** Choose between Live TV, Movies, or TV Shows (main landing page)
- **CategoryList:** Browse categories for selected content type
- **MovieDetails:** View movie information and play options
- **EpisodeSelection:** Browse TV show seasons and episodes
- **Player:** Video playback screen with playback controls
- **Search:** Search across categories for a content type
- **EpgGuide:** TV Guide with 24-hour program grid (Live TV only)
- **Settings:** App configuration, theme selection, provider management
- **ProviderSelection:** List, select, edit, and delete providers
- **AddProvider:** Form for adding or editing a provider

#### Navigation Features
- **Type-Safe Navigation:** kotlinx.serialization for compile-time safety
- **D-Pad Focus Management:** Full remote control support
- **Back Stack Management:** Intelligent navigation back button handling
- **Session Restoration:** Auto-navigate to last content type on app launch

### 7. Developer Mode Features

When enabled via Settings, Developer Mode provides:

#### Payload Size Tracking
- **API Response Monitoring:** Tracks size of network responses in bytes
- **Performance Metrics:** Monitor bandwidth usage
- **Debug Display:** Payload sizes shown in category grid and when loading from cache
- **Network Analysis:** Identify bandwidth-heavy operations

#### Debug Information
- Network request/response statistics
- Provider connectivity diagnostics
- Content loading metrics

### 8. User Interface

#### TV-Optimized Design
- **D-Pad Navigation:** Full remote control support without touch
- **Focus Management:** Visual focus indicators for all interactive elements
- **Overscan Safety:** 5% padding from screen edges for TV display safety
- **Material Design 3 (TV):** Using `androidx.tv.material3` components

#### Multi-Device Support
- **Responsive Layouts:** Adapts to different screen sizes
- **Device-Specific Optimization:**
  - NVIDIA Shield: High-performance codec rendering
  - Sony Bravia: Lean UI avoiding complex animations
  - Chromecast: Responsive to compact window sizes
- **WindowSizeClass:** Dynamic UI adaptation (Mobile vs TV)

#### Two-Column Layout
- **Left Column:** Category list with vertical scrolling
- **Right Column:** Stream/episode list for selected category
- **Focus Navigation:** D-pad movement between columns
- **Dynamic Scaling:** Content area adjusts based on selection

### 9. Authentication & Credentials

#### Credential Management
- **Room Database:** Provider metadata (name, URL, username, active flag) stored in Room
- **Encrypted Passwords:** Per-provider EncryptedSharedPreferences (AES256-GCM)
- **Session Persistence:** Automatic session restoration on app restart
- **Multiple Providers:** Add, edit, delete, and switch between IPTV providers
- **Migration:** Automatic one-time migration from legacy single-provider storage to Room

#### Provider URL Handling
- **HTTP/Cleartext Support:** Compatibility with legacy providers
- **Custom Headers:** Authentication header support
- **Cross-Protocol Redirects:** Automatic redirect handling
- **Connection Timeouts:** 8-second connect/read timeouts

### 10. Error Handling & Recovery

#### Network Error Handling
- User-friendly error messages
- Retry mechanisms for failed operations
- Graceful degradation
- Provider URL validation

#### Playback Error Handling
- Stream availability checks
- Format compatibility detection
- Fallback options for unsupported content
- Clear error reporting

## Technical Architecture

### Dependency Injection
- **ViewModel Factory:** Custom factories for screen-specific ViewModels
- **Repository Pattern:** XtreamRepository as data layer, ProviderRepository for provider management
- **Service Layer:** AccountManager for legacy credential management, ProviderRepository for multi-provider
- **Database:** Room for provider metadata, EncryptedSharedPreferences for passwords

### State Management
- **Jetpack Compose State:** Local state for UI
- **ViewModel State:** Screen-level state management
- **SharedFlow/StateFlow:** Reactive data streams
- **Coroutines:** Asynchronous operations

### Networking
- **Ktor Client:** HTTP networking library
- **kotlinx.serialization:** JSON deserialization
- **Custom Interceptors:** Authentication and error handling
- **Connection Pooling:** Efficient connection management

### Video Playback
- **Media3 (ExoPlayer):** Modern video playback engine
- **StreamingMediaSourceFactory:** Automatic stream type detection
- **LoadControl:** Optimized buffer management
- **Codec Selection:** Hardware-accelerated rendering

## Navigation Flow Details

### Live TV Flow
1. User selects "Live TV" from ContentTypeSelection
2. CategoryList displays Live TV categories
3. User selects category → streams load
4. User selects stream → Player starts playback
5. Watch history automatically updated

### Movie Flow
1. User selects "Movies" from ContentTypeSelection
2. CategoryList displays movie categories
3. User selects movie → MovieDetails screen shows
4. User presses Play → Player starts playback
5. Watch history automatically updated

### TV Shows Flow
1. User selects "TV Shows" from ContentTypeSelection
2. CategoryList displays series categories
3. User selects series → EpisodeSelection screen shows
4. User selects episode → Player starts playback
5. Watch history automatically updated with episode info

### Settings Access
- From ContentTypeSelection: Press gear icon button (bottom left)
- Settings Screen options:
  - Active Provider: View name and URL of current provider
  - Manage Providers: Add, edit, delete, switch between providers
  - Theme: Select from 4 dark themes
  - Auto-Resume: Toggle VOD auto-resume
  - Watch History Size: Configure queue length
  - Favorites Max Size: Configure max favorites
  - UI Scale: Adjust font and element sizes
  - Cache Management: View and clear cached data
  - Developer Mode: Toggle debug features

## File Structure

```
core/
├── network/          # Networking and repository layer
│   ├── AppSettings.kt       # Settings configuration (incl. themeId)
│   ├── AccountManager.kt    # Legacy credential management
│   ├── XtreamRepository.kt  # Data layer
│   └── provider/             # Multi-provider support
│       ├── ProviderEntity.kt     # Room entity
│       ├── ProviderDao.kt        # Data access object
│       ├── ProviderDatabase.kt   # Room database singleton
│       └── ProviderRepository.kt # Repository (DAO + encrypted prefs)
├── ui/               # Shared UI components
│   ├── theme/
│   │   └── CinemaThemePalette.kt  # Theme palettes + CinemaThemeHolder
│   └── viewmodels/
│       ├── ProviderViewModel.kt   # Provider management + migration
│       └── ProviderViewModelFactory.kt
├── player/           # Video playback
│   └── StreamingMediaSourceFactory.kt  # Stream type detection
└── navigation/       # Navigation types
    ├── ContentType.kt       # Content type enum
    └── Screen.kt            # Navigation destinations

tv/
└── src/main/java/
    ├── feature/
    │   ├── category/           # Category browsing
    │   ├── contentselection/   # Content type selection
    │   ├── episode/            # TV show episodes
    │   ├── movie/              # Movie details
    │   ├── player/             # Video playback
    │   ├── provider/           # Provider management
    │   │   ├── TvProviderSelectionScreen.kt
    │   │   └── TvAddProviderScreen.kt
    │   ├── search/             # Search
    │   ├── settings/           # App settings + theme picker
    │   └── epg/                # Electronic Program Guide
    ├── ui/theme/               # TV theme (CinemaColors re-exports, Theme.kt)
    └── navigation/
        └── TvNavHost.kt

mobile/
└── src/main/java/
    ├── feature/
    │   ├── category/           # Category browsing
    │   ├── contentselection/   # Content type selection
    │   ├── episode/            # TV show episodes
    │   ├── movie/              # Movie details
    │   ├── player/             # Video playback
    │   ├── provider/           # Provider management
    │   │   ├── MobileProviderSelectionScreen.kt
    │   │   └── MobileAddProviderScreen.kt
    │   ├── search/             # Search
    │   └── settings/           # App settings + theme picker
    ├── ui/theme/               # Mobile theme (Color re-exports, Theme.kt)
    └── navigation/
        └── MobileNavHost.kt
```

## Performance Considerations

### Network Optimization
- Connection pooling for efficient requests
- 8-second timeouts for provider reliability
- Payload size tracking in developer mode
- Minimal bandwidth usage in live streaming

### UI Performance
- Efficient focus management
- Lazy loading of content
- Memory management for video playback
- Responsive D-pad navigation

### Playback Optimization
- Hardware-accelerated codec selection
- Minimal buffering for live streams
- Fast channel switching (250ms startup)
- 4K/HDR hardware support

## Future Enhancement Opportunities

- Cloud sync for watch history and favorites
- Recommendations based on watch history
- Content filtering and sorting
- Parental controls
- Multiple profile support per provider
- Scheduled recordings (if applicable)
- Playback speed control for VOD (0.5x, 1.25x, 1.5x, 2x)
- Picture-in-Picture mode (mobile only)
- Audio/subtitle language persistence per stream
