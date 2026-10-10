# Fijerena

<div align="center">

![App Icon](mobile/src/main/res/mipmap-xxxhdpi/ic_launcher.webp)

**A multi-source media player for Android TV and Android phones**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-11+-green.svg)](https://developer.android.com)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)]()

*Android phones and tablets, NVIDIA Shield, Chromecast with Google TV, and Sony Bravia (Android TV)*

</div>

---

## 📖 Overview

Fijerena is a native Android media player written in Kotlin with Jetpack Compose. One codebase builds two apps: a 10-foot TV app driven by the D-pad, and a touch app for phones. Both play Live TV, movies and TV shows from the sources you add, with a TV guide, profiles, and live sync between a household's devices.

The adaptive icon is the Blue Marble (Earth) wearing red/cyan 3D glasses.

## ✨ Key Features

The full reference is **[docs/FEATURES.md](docs/FEATURES.md)**; this is the short version.

- **Sources:** Xtream Codes (Live TV, Movies, TV Shows, guide), Jellyfin (Movies, TV Shows, progress sync, Quick Connect), and Remote M3U playlists (Live TV and the playlist's video entries). SMB shares and Local files are offered in developer mode only. Several sources can be configured; logins are stored encrypted per source.
- **Live TV:** a channel plays beside the list while you browse; one channel panel (category, Recent, Favourites) in the preview and over the video in full screen; channel switching with Up/Down; a TV Guide grid and guide search over multi-source XMLTV, refreshed in the background with change detection and retries.
- **Movies and TV Shows:** details screens, resume between 2 % and 95 %, Continue Watching and Up next, mark watched/unwatched, watched state shared across language/quality variants of the same title (TMDB ID), and TMDB episode synopses when a TMDB key is configured.
- **Player:** Media3 (HLS, DASH, MPEG-TS), audio, subtitle and quality selection remembered per item and per series, Stats for Nerds, network-aware buffering, and codec preference per device (AV1 → HEVC → AVC on Shield).
- **Profiles and Live Sync:** a "Who's watching?" picker with per-profile favourites, history, filters and Jellyfin login; profiles, favourites, progress and settings kept in step across devices, end-to-end encrypted, through a self-hosted or Cloudflare sync server ([server/](server/README.md)); TVs join by QR code from a phone.
- **Settings:** four dark themes (Deep Night, AMOLED Black, Amethyst, Teal), four looks (Material, Cupertino, Roku, BRAVIA), English, French and Malagasy, UI scale on TV, export/import, developer mode with on-device diagnostics.
- **TV UI:** D-pad navigation with a consistent focus model, TV-safe margins (56dp / 32dp), body text of at least 18sp. Mobile is portrait-locked outside the player.

## 🖥️ Supported Devices

- **Android phones and tablets** (Android 11+).
- **NVIDIA Shield** (Shield TV and Shield TV Pro).
- **Chromecast with Google TV**.
- **Sony Bravia** Android TV models.

| | |
|---|---|
| Minimum SDK | 30 (Android 11) |
| Target SDK | 35 (Android 15) |
| Compile SDK | 36 (Android 16) |
| ABIs | arm64-v8a, armeabi-v7a; debug builds add x86 and x86_64 so emulators run natively |

## 🛠️ Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 2.3.0 |
| Build System | Gradle | 9.6.0 |
| Build System | Android Gradle Plugin (AGP) | 9.4.1 |
| UI Framework | Jetpack Compose | 2026.03.01 BOM (ui/foundation/runtime/animation 1.10.6) |
| Material Design | Material 3 | 1.4.0 (`strictly`) |
| TV Components | androidx.tv.material3 (lists are plain LazyColumn/LazyRow) | 1.0.0-alpha10 |
| Video Player | Media3 (ExoPlayer) | 1.7.1 |
| Networking | Ktor (OkHttp engine) | 3.5.2 |
| Serialization | kotlinx.serialization | 1.11.0 |
| Database | Room (FTS4) | 2.8.4 |
| SQLite | Bundled requery build (FTS5 capable) | 3.49.0 |
| Background Work | WorkManager | 2.11.2 |
| Paging | androidx.paging | 3.5.1 |
| Image Loading | Coil | 3.5.0 |
| Navigation | Navigation Compose | 2.8.5 |
| Coroutines | kotlinx.coroutines | 1.11.0 |
| SMB Client | smbj (Hierynomus) | 0.15.0 |
| Sync server | TypeScript on Cloudflare Workers / workerd | see `server/package.json` |

`gradle/libs.versions.toml` is authoritative. The TV app doesn't use `tv-foundation`'s lazy lists
(its alpha10 calls a prefetch API removed in Compose 1.9), which is what let the Compose BOM move off
2025.06.x. Compose 1.11+ needs `compileSdk` 37.

Architecture in one line: multi-module MVVM (ViewModel + StateFlow), provider types mapped to shared
domain models, one `MediaRepository` over the active source, manual DI through `AppContainer`. See
[docs/design.md](docs/design.md).

## 📂 Project Structure

```
fijerena/
├── mobile/                    # Phone app (touch UI)
├── tv/                        # Android TV app (10-foot UI)
├── core/
│   ├── player/               # Media3 player, playback service, domain models, diagnostics
│   ├── network/              # Source implementations, Room databases, EPG pipeline, live sync client
│   │   ├── XtreamMediaProvider.kt    # Xtream Codes
│   │   ├── jellyfin/                 # Jellyfin REST client
│   │   ├── remote/                   # Remote M3U
│   │   ├── local/                    # Local files and M3U parser
│   │   └── smb/                      # SMB share client
│   ├── ui/                   # Shared ViewModels, Compose components, design tokens, strings
│   ├── navigation/           # Type-safe navigation definitions
│   └── data/                 # AuthViewModel only
├── server/                   # Live sync server (Cloudflare Worker / workerd Docker image)
├── scripts/                  # Build, deploy, backup/restore, CI checks, TV focus walks
├── tools/jellyfin-xtream/    # Test bridge: serves a Jellyfin library as an Xtream panel
├── docs/                     # Technical documentation
├── AGENTS.md                 # Guide for AI coding agents (rules and conventions)
└── README.md                 # This file
```

## 🚀 Getting Started

### Prerequisites
- **JDK 21** (sources target Java 21)
- **Android Studio** — a release that supports AGP 9.4 — or the Android SDK command-line tools
- **Android SDK** — API level 36
- **sqlite3** on the host, for the backup/restore and network-TV deploy scripts

### Clone and open
```bash
git clone https://github.com/mpilasy/fijerena.git
cd fijerena
```
Open the folder in Android Studio (**File → Open**) and let Gradle sync, or use `./gradlew` from the
command line.

### Configuration
Nothing is required to build. For TMDB episode synopses, put a TMDB API key (v3 key or v4 read
token) in the gitignored `local.properties`:

```properties
TMDB_API_KEY=<your key>
```

Without it the build still works and TMDB lookups are skipped. The app asks for a source on first
launch.

## 🔨 Building

```bash
./gradlew assembleDebug            # both apps
./gradlew :tv:assembleDebug        # TV only   (or scripts/build-tv.sh)
./gradlew :mobile:assembleDebug    # mobile only (or scripts/build-mobile.sh)
```

Outputs, one per module:
- `tv/build/outputs/apk/debug/tv-debug.apk`
- `mobile/build/outputs/apk/debug/mobile-debug.apk`

`./gradlew :tv:assembleRelease` and `:mobile:assembleRelease` build release APKs. The build has no
signing configuration, so they come out unsigned and must be signed (e.g. with `apksigner`) before
they can be installed.

## 📲 Installation

Use the deploy scripts; each builds the right APK and installs it as an update:

```bash
scripts/deploy-tv-emulator.sh [serial]       # TV emulator
scripts/deploy-mobile-emulator.sh [serial]   # phone emulator
scripts/deploy-tv-ip.sh <ip>[:port] ...      # network TVs: Shield, Bravia, Chromecast
scripts/deploy-mobile-usb.sh [serial]        # phone over USB
```

The TV and mobile apps share one `applicationId` (`org.njarasoa.fijerena`), so a device gets one or
the other. `adb install -r` can wipe the app's data without warning; the real-device scripts back up
user data first (`scripts/backup-app-data.sh`). Device discovery, backup/restore, logs and debugging
are in **[docs/RUN_GUIDE.md](docs/RUN_GUIDE.md)**.

## 🔍 Development

```bash
./gradlew ktlintCheck          # code style (ktlintFormat fixes it)
./gradlew lintDebug            # Android Lint; each module's lint-baseline.xml holds the old warnings
./gradlew testDebugUnitTest    # unit tests
scripts/check-cancellation.sh && scripts/check-viewmodel-launch.sh && scripts/check-focus-retry.sh
```

GitHub Actions runs these on every push to `main`: `checks.yml` (the gates), `android-build.yml` (tests,
lint, the Room schema check, both debug APKs) and `server.yml` (the sync server's tests, when
`server/` changes).

```bash
# Instrumentation tests (requires a connected device/emulator).
# WARNING: this uninstalls the app and wipes its data on EVERY connected device;
# disconnect real devices first, or target one emulator with ANDROID_SERIAL.
./gradlew connectedAndroidTest
```

Coding rules (design tokens, TV focus, strings in English, French and Malagasy, database migrations)
are in **[AGENTS.md](AGENTS.md)**, which applies to people as much as to agents.

## 📚 Documentation

- **[docs/FEATURES.md](docs/FEATURES.md)** - Feature reference, settings, themes
- **[docs/design.md](docs/design.md)** - System design and architecture
- **[docs/NAVIGATION_GUIDE.md](docs/NAVIGATION_GUIDE.md)** - Screens, navigation flow, TV focus handling
- **[docs/DATABASE_SCHEMA.md](docs/DATABASE_SCHEMA.md)** - Room databases and SharedPreferences
- **[docs/epg_guide.md](docs/epg_guide.md)** - EPG pipeline implementation guide
- **[docs/EPG_INDEX_STORAGE.md](docs/EPG_INDEX_STORAGE.md)** - Why the EPG index grew to 87% dead space, and how to read DB state from a file header
- **[docs/RUN_GUIDE.md](docs/RUN_GUIDE.md)** - Build, install, deploy, backup/restore, debugging, focus walks
- **[docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md)** - Changelog
- **[server/README.md](server/README.md)** - Live sync server: develop, deploy, self-host, API

### AI agent instructions

**[AGENTS.md](AGENTS.md)** is the guide for all AI coding assistants. The vendor entry points
redirect there:

| File | Tool |
|------|------|
| `CLAUDE.md` | Claude Code |
| `GEMINI.md` | Gemini CLI, Jules |
| `CODEX.md` | OpenAI Codex |
| `.cursorrules` | Cursor |
| `.github/copilot-instructions.md` | GitHub Copilot |

`.jules/bolt.md` is Jules' performance journal.

## 🤝 Contributing

1. Read [AGENTS.md](AGENTS.md) for the coding rules.
2. Work on a branch.
3. Keep TV screens D-pad navigable and inside the safe margins; use the design tokens.
4. Make ktlint, Android Lint, the unit tests and the `scripts/check-*.sh` gates pass.
5. Test on both TV and mobile; include screenshots or recordings for UI changes.
6. Write commit messages that say why, not only what.

## 📄 License

This project is proprietary software. All rights reserved.

Unauthorized copying, modification, distribution, or use of this software,
via any medium, is strictly prohibited without explicit permission from the
copyright holder.

## 🙏 Acknowledgments

- **Jetpack Compose** - declarative UI toolkit
- **Media3** - the ExoPlayer playback engine
- **Ktor** - networking
- **Hierynomus** - the smbj SMB client library
- The open-source libraries listed in `gradle/libs.versions.toml`
