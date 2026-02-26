# Fijerena

<div align="center">

![App Icon](mobile/src/main/res/mipmap-xxxhdpi/ic_launcher.webp)

**A feature-rich multi-provider media player for Android**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-11+-green.svg)](https://developer.android.com)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-100%25-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)]()

*Supporting Android Mobile, NVIDIA Shield, Chromecast with Google TV, and Sony Bravia (Android TV)*

</div>

---

## 📖 Overview

Fijerena is a native Android media player built entirely with Kotlin and Jetpack Compose, designed to deliver an exceptional viewing experience across both mobile and TV platforms. With support for multiple media provider types, advanced playback features, and a modern Material 3 design, Fijerena provides a unified interface for accessing your media library from various sources.

The app features the iconic Blue Marble (Earth) with red/cyan 3D glasses as its adaptive icon, symbolizing a world of content at your fingertips.

## ✨ Key Features

### 🎬 Multi-Provider Support
- **Xtream Codes API** - Full IPTV support with Live TV, Movies, TV Shows, and EPG
- **Jellyfin** - Self-hosted media server integration with playback progress sync and Quick Connect auth
- **SMB/CIFS** - Direct access to network shares (SMB2/3)
- **Local Storage** - Local media files and M3U playlist support
- Seamlessly switch between multiple providers
- Per-provider encrypted credential storage
- Automatic session restoration

### 📺 Content Types
- **Live TV** - Live television channels with fast channel switching
- **Movies (VOD)** - On-demand movie content with resume support
- **TV Shows** - Series browsing with season/episode selection
- **EPG Guide** - 24-hour Electronic Program Guide with grid layout

### 🎮 Playback Features
- **Media3 (ExoPlayer)** - Industry-leading video playback engine
- **4K/HDR Support** - Hardware-accelerated rendering on compatible devices
- **Multi-Audio Tracks** - Language and format selection (Stereo, 5.1, 7.1)
- **Subtitles/Captions** - Support for SRT, VTT, TTML, CEA-608/708
- **Adaptive Quality** - Manual and automatic bitrate selection
- **Content-Type Aware Buffering**:
  - Live TV: Fast zapping profile (2-5s buffer)
  - VOD: Smooth playback profile (15-50s buffer)
- **Codec Prioritization**:
  - NVIDIA Shield: AV1 → HEVC → AVC
  - Sony Bravia: HEVC → AVC
  - Generic: AVC (H.264)

### 🎨 Modern UI/UX
- **100% Jetpack Compose** - Fully declarative UI
- **4 Dark Themes** - Deep Night (default), AMOLED Black, Emerald, Crimson
- **Material 3 Design** - Google TV optimized with Electric Blue accents
- **D-Pad Navigation** - Full remote control support for TV devices
- **Focus Indicators** - Animated scale, border, and glow effects
- **Safe Margins** - TV overscan compensation (56dp horizontal, 32dp vertical)
- **10-Foot UI** - Typography optimized for TV viewing distance (≥18sp body text)

### 🔧 Advanced Features
- **Virtual Categories**:
  - Favorites - User-curated collection (configurable size: 10-500 items)
  - Last Watched - Recent viewing history (added after 5s of viewing, configurable size: 1-100 items)
- **Playback Resume** - Automatic position restore for VOD content (2-95% range)
- **Stats for Nerds** - Real-time playback metrics overlay
- **Channel Switching** - D-pad up/down for live TV channel navigation
- **Channel Overlays** - Category and last-watched side panels (D-pad Left/Right on TV, swipe on mobile)
- **VOD Seek Controls** - Rewind −30s and Fast-forward +1min via buttons or remote media keys
- **Pause via Double-Tap** - Mobile double-tap pauses/resumes VOD content
- **VOD Time Display** - Current position, remaining time, estimated end time
- **Cross-Type Search** - Unified "ALL" search across Live TV, Movies, and TV Shows from content type selection
- **Developer Mode** - Payload size tracking and debug information
- **Cache Management** - Per-content-type cache with statistics
- **UI Scale Adjustment** - 70%-100% sizing options
- **Cellular Buffer Tuning** - Adjustable cellular buffer multipliers (0.5x-3.0x) in dev mode

### 📱 Platform-Specific
- **Mobile**:
  - Portrait-locked UI (except player)
  - Touch-optimized controls
  - Sensor-based orientation in player
  - Horizontally scrollable control row
- **TV**:
  - D-pad/remote navigation throughout
  - Focus-based interaction model
  - Glassmorphism effects (translucent backgrounds)
  - No on-screen back buttons (uses remote back button)

## 🖥️ Supported Devices

### ✅ Tested & Optimized
- **Android Mobile** - Phones & tablets (Android 11+)
- **NVIDIA Shield** - Shield TV & Shield TV Pro (prioritizes AV1/HEVC codecs)
- **Chromecast with Google TV** - 4K & HD models
- **Sony Bravia** - Android TV models (HEVC optimization)

### 🎯 Target Platform
- **Minimum SDK**: 30 (Android 11)
- **Target SDK**: 36 (Android 16)
- **Architectures**: ARM64, ARMv7, x86_64, x86

## 🛠️ Tech Stack

### Core Technologies
| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 2.3.0 |
| Build System | Gradle | 9.2.1 |
| UI Framework | Jetpack Compose | 2024.12.01 BOM |
| Material Design | Material 3 | 1.4.0 |
| TV Components | androidx.tv.material3 | 1.0.0-alpha10 |
| Video Player | Media3 (ExoPlayer) | 1.7.1 |
| Networking | Ktor | 3.4.0 |
| Serialization | kotlinx.serialization | 1.8.0 |
| Database | Room | 2.8.4 |
| Image Loading | Coil | 3.1.0 |
| Navigation | Navigation Compose | 2.8.5 |
| Coroutines | kotlinx.coroutines | 1.7.3 |
| SMB Client | smbj (Hierynomus) | 0.13.0 |

### Architecture
- **Multi-Module**: Separate modules for mobile, TV, and core functionality
- **MVVM Pattern**: ViewModel + StateFlow for reactive UI
- **Clean Architecture**: Domain models abstract provider-specific types
- **Repository Pattern**: Unified `MediaRepository` delegates to active provider
- **Dependency Injection**: Manual DI via factory pattern
- **Type Safety**: Navigation uses String IDs (supports non-numeric identifiers)

## 📂 Project Structure

```
fijerena/
├── mobile/                    # Mobile app module (portrait UI)
├── tv/                        # Android TV app module (10-foot UI)
├── core/
│   ├── player/               # Media3 player configuration & domain models
│   ├── network/              # Multi-provider API implementations
│   │   ├── XtreamMediaProvider.kt    # Xtream Codes API
│   │   ├── jellyfin/                 # Jellyfin REST API client
│   │   ├── smb/                      # SMB network share client
│   │   └── local/                    # Local media & M3U parser
│   ├── data/                 # Room database & encrypted storage
│   ├── ui/                   # Shared Compose components & design tokens
│   └── navigation/           # Type-safe navigation definitions
├── docs/                     # Additional documentation
├── CLAUDE.md                 # Technical specification & coding standards
└── README.md                 # This file
```

## 🚀 Getting Started

### Prerequisites
- **Java Development Kit (JDK)** - Version 17 or higher
- **Android Studio** - Ladybug (2024.2.1) or newer
- **Android SDK** - API Level 36 (Android 16)
- **Git** - For version control

### Clone the Repository
```bash
git clone https://github.com/yourusername/fijerena.git
cd fijerena
```

### Open in Android Studio
1. Launch Android Studio
2. Select **File → Open**
3. Navigate to the cloned `fijerena` directory
4. Click **OK** and wait for Gradle sync to complete

### Configuration
No additional configuration is required for the initial build. The app will prompt for provider setup on first launch.

## 🔨 Building

### Debug Builds

**Build All Modules:**
```bash
./gradlew assembleDebug
```

**Build Mobile Only:**
```bash
./gradlew :mobile:assembleDebug
```

**Build TV Only:**
```bash
./gradlew :tv:assembleDebug
```

**Output Locations:**
- Mobile: `mobile/build/outputs/apk/debug/mobile-debug.apk`
- TV: `tv/build/outputs/apk/debug/tv-debug.apk`

### Release Builds

**Build Release APKs:**
```bash
# Mobile
./gradlew :mobile:assembleRelease

# TV
./gradlew :tv:assembleRelease
```

**Note:** Release builds require signing configuration in `~/.gradle/gradle.properties` or via command-line arguments.

## 📲 Installation

### Install on Android Device/Emulator

**Via Android Studio:**
1. Connect your device or start an emulator
2. Click **Run** (Shift+F10) and select the target device

**Via ADB:**
```bash
# Mobile (phone/tablet)
adb install -r mobile/build/outputs/apk/debug/mobile-debug.apk

# TV (Android TV device)
adb install -r tv/build/outputs/apk/debug/tv-debug.apk

# Specific device (when multiple connected)
adb -s <device-id> install -r <apk-path>
```

### Install on NVIDIA Shield / Sony Bravia

**Connect via Network:**
```bash
# Enable ADB over network on TV (Settings → Developer Options)
adb connect <TV_IP_ADDRESS>:5555

# Verify connection
adb devices

# Install TV APK
./gradlew :tv:installDebug
```

**Alternative:** Use a USB drive and sideload via file manager on the TV.

## 🔍 Development

### Code Style & Linting
```bash
# Check code style
./gradlew ktlintCheck

# Auto-format code
./gradlew ktlintFormat
```

### Running Tests
```bash
# Unit tests
./gradlew test

# Instrumentation tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

### Design Token Standards
All UI values (colors, spacing, dimensions, animations) **must** come from design token files:

**Shared Tokens (core/ui):**
- `CinemaColors.kt` - Color palette
- `CinemaSpacing.kt` - Padding/margins
- `CinemaAlpha.kt` - Opacity values
- `CinemaAnimation.kt` - Animation durations
- `CinemaCornerRadius.kt` - Border radii

**Platform-Specific:**
- `TvDimensions.kt` / `MobileDimensions.kt` - Sizes
- `TvFocusTokens.kt` - Focus scale/border/glow

**Theme System:**
- `CinemaThemePalette.kt` - Per-theme color definitions
- `CinemaThemeHolder.current` - Runtime theme state

### Adding a New Provider
1. Implement `MediaProvider` interface in `core/network/`
2. Create provider-specific models (API responses)
3. Implement mapper to convert to domain models (`MediaCategory`, `MediaItem`)
4. Add provider type to `ProviderType` enum
5. Register in `MediaProviderFactory`
6. Add UI form fields in `AddProviderScreen`
7. Update `ProviderCapabilities` for feature support

## 📚 Documentation

Comprehensive documentation is available in the following files:

- **[CLAUDE.md](CLAUDE.md)** - Complete technical specification, coding standards, and architectural guidelines
- **[docs/FEATURES.md](docs/FEATURES.md)** - Detailed feature documentation with API references
- **[docs/NAVIGATION_GUIDE.md](docs/NAVIGATION_GUIDE.md)** - App navigation flow and screen hierarchy
- **[docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md)** - Version history and changelog
- **[docs/design.md](docs/design.md)** - System design and architecture
- **[docs/ui-theme-options.md](docs/ui-theme-options.md)** - Theme system documentation

## 🎨 Theme System

Fijerena supports 4 dark theme variants, selectable at runtime from Settings:

| Theme | Primary Accent | Surfaces | Use Case |
|-------|---------------|----------|----------|
| **Deep Night** (default) | Electric Blue `#2979FF` | `#0F1014`, `#161A20` | Balanced contrast |
| **AMOLED Black** | Electric Blue `#2979FF` | `#000000`, `#0A0A0A` | Battery saving on OLED |
| **Emerald** | Green `#00C853` | `#0F1014`, `#161A20` | Nature-inspired |
| **Crimson** | Red `#FF1744` | `#0F1014`, `#161A20` | Bold & dramatic |

All themes feature:
- **Electric Blue** primary for focus states and CTAs
- **Vivid Orange** `#FF6D00` for LIVE badges and destructive actions
- Consistent status colors (success/warning/error) across themes
- Dynamic theme switching without app restart

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. **Read the Documentation** - Familiarize yourself with [CLAUDE.md](CLAUDE.md) for coding standards
2. **Create a Feature Branch** - `git checkout -b feature/your-feature-name`
3. **Follow Design Tokens** - Never use hardcoded UI values (colors, spacing, etc.)
4. **Maintain D-Pad Navigation** - All UI must be remote-navigable on TV
5. **Respect Safe Margins** - Apply overscan margins to all TV screens
6. **Test on Multiple Devices** - Verify on both mobile and TV platforms
7. **Run Linting** - `./gradlew ktlintCheck` must pass
8. **Write Clear Commit Messages** - Describe the "why" not just the "what"
9. **Submit a Pull Request** - Include screenshots/recordings for UI changes

### Code Review Checklist
- [ ] No hardcoded colors (use `CinemaColors` or `MaterialTheme.colorScheme`)
- [ ] No hardcoded dimensions (use `CinemaSpacing`, `TvDimensions`, etc.)
- [ ] All interactive elements have focus indicators
- [ ] TV screens respect safe margins (56dp horizontal, 32dp vertical)
- [ ] Typography uses predefined scales (minimum 18sp for body text)
- [ ] Navigation works with D-pad on TV
- [ ] No security vulnerabilities (XSS, SQL injection, command injection)
- [ ] No unnecessary abstractions or over-engineering

## 📄 License

This project is proprietary software. All rights reserved.

Unauthorized copying, modification, distribution, or use of this software,
via any medium, is strictly prohibited without explicit permission from the
copyright holder.

## 🙏 Acknowledgments

- **Jetpack Compose Team** - For the modern declarative UI toolkit
- **Media3 Team** - For the robust ExoPlayer foundation
- **Ktor Team** - For the elegant networking library
- **Hierynomus** - For the smbj SMB client library
- **Open Source Community** - For the countless libraries that make this possible

## 📞 Support

For issues, questions, or feature requests:
- Open an issue on GitHub
- Check existing documentation in the `docs/` directory
- Review [CLAUDE.md](CLAUDE.md) for technical details

---

<div align="center">

**Built with ❤️ using Kotlin and Jetpack Compose**

*Designed for the big screen, optimized for every screen*

</div>
