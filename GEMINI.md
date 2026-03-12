# GEMINI.md - Fijerena Project Context

This file serves as the foundational mandate for Gemini CLI interactions within the Fijerena project. It outlines the project's architecture, technologies, engineering standards, and development workflows.

## 📖 Project Overview
**Fijerena** is a premium, native Android media player built with Kotlin and Jetpack Compose. It is designed as a multi-provider media player supporting various sources (Xtream IPTV, Jellyfin, SMB, Local files, Remote M3U). It provides a tailored experience for both touch-based mobile devices and D-pad-driven TV platforms.

**Target Devices:**
- NVIDIA Shield (prioritizing AV1/HEVC)
- Chromecast with Google TV
- Sony Bravia (Android TV)
- Android Mobile (API 30+)

## 🏗️ Architecture & Modules
The project follows a modular, Clean Architecture pattern with MVVM.

- **`:mobile`**: Mobile-specific application module (Portrait-locked, touch UI).
- **`:tv`**: Android TV application module (10-foot UI, D-pad optimized).
- **`:core:player`**: Media3 (ExoPlayer) implementation, playback service, and unified domain models.
- **`:core:network`**: Provider implementations (Xtream, Jellyfin, SMB, Local), API clients, and EPG indexing.
- **`:core:ui`**: Shared UI components, ViewModels, and Design Tokens.
- **`:core:navigation`**: Type-safe navigation definitions shared across platforms.
- **`:core:data`**: Shared session, encrypted storage, and authentication data.

### Architectural Rules
- **Circular Dependencies:** `core:player` MUST NOT depend on `core:network`.
- **Dependency Injection:** Always use the `AppContainer` (in `core:ui`) to obtain repository singletons (`MediaRepository`, `ProviderRepository`). Never manually instantiate repositories in ViewModels to prevent redundant database connections and ensure consistent state.
- **Asynchronous Initialization:** ViewModels must initialize their repository dependencies asynchronously to prevent UI thread blocking during screen composition.
- **Unified Domain:** All provider-specific models must be mapped to unified domain models in `core:player/domain/`.
- **Type Safety:** All media and category IDs must be `String` to ensure compatibility across different provider types.

## 🛠️ Tech Stack
- **Language:** Kotlin 2.3.0
- **Build System:** Gradle 9.2.1 (with Version Catalogs)
- **UI Framework:** Jetpack Compose (BOM 2024.12.01)
- **TV Material:** `androidx.tv.material3` (1.0.0-alpha10)
- **Media Player:** Media3 ExoPlayer (1.7.1)
- **Networking:** Ktor (3.4.0) with OkHttp engine
- **Database:** Room (2.8.4) with FTS4 for EPG search
- **Navigation:** Navigation Compose with `kotlinx.serialization`
- **Image Loading:** Coil (3.1.0)
- **SMB Support:** `smbj` (0.13.0)

## 🔨 Development Workflows

### Build and Run
- **Build All:** `./gradlew assembleDebug`
- **Install Mobile:** `./gradlew :mobile:installDebug`
- **Install TV:** `./gradlew :tv:installDebug` (Ensure `adb connect [TV_IP]` is active)
- **Check Style:** `./gradlew ktlintCheck`
- **Format Code:** `./gradlew ktlintFormat`

### Deployment
- TV and Mobile share the same `applicationId`. Use `adb -s <device_id>` when multiple devices are connected.
- **APK Outputs:** Always build and place all four APK variants in the root `build/outputs/apk/` directory with clear naming:
    - `fijerena-mobile-full-debug.apk`
    - `fijerena-mobile-slim-debug.apk`
    - `fijerena-tv-full-debug.apk`
    - `fijerena-tv-slim-debug.apk`
- **Deployment Strategy:**
    - **NVIDIA Shield:** Always deploy the **Slim** variant (`fijerena-tv-slim-debug.apk`).
    - **OnePlus 12R:** Always deploy the **Full** variant (`fijerena-mobile-full-debug.apk`).
    - **Emulators:** Always deploy the **Slim** variants.

## 🎨 Engineering Standards & Conventions

### 1. UI Development (STRICT)
- **No Hardcoded Values:** Never use raw literals for colors, dimensions, or spacing (e.g., `16.dp`, `Color.White`).
- **Design Tokens:**
  - Shared: `CinemaColors`, `CinemaSpacing`, `CinemaAlpha`, `CinemaCornerRadius`.
  - TV: `TvDimensions`, `TvFocusTokens`.
  - Mobile: `MobileDimensions`.
- **TV Safe Margins:** Root containers MUST apply `Spacing.tvSafeMarginHorizontal` (56dp) and `Spacing.tvSafeMarginVertical` (32dp).
- **Typography:** Body text on TV MUST be at least **18sp**.

### 2. Focus & Navigation (TV)
- Every interactive component must be D-pad navigable.
- Use `focusRestorer()` and clear focus indicators (Scale 1.1, 2dp border, 8dp glow).
- Avoid complex UI animations on mid-range TV processors (e.g., Sony Bravia).

### 3. Media Playback
- **Source Creation:** Always use `StreamingMediaSourceFactory.createMediaSource()`.
- **Buffer Management:** `AdaptiveLoadControl` handles dynamic swapping of buffer profiles.
- **Player Controls:**
  - OK/Center key ONLY shows controls; it does not pause playback.
  - D-pad Left/Right on Live TV opens channel overlays (does not seek).

### 4. EPG & Data
- **EPG Indexing:** XMLTV data is indexed into a Room SQLite DB with FTS4 support.
- **Search:** Supports multi-word queries. Primary search uses `FTS4 MATCH` (for EPG) or client-side word-matching (for Xtream VOD), with `LIKE` as a fallback.
- **Multi-Source EPG:** Managed via `EpgFileManager` singleton with a pipeline architecture:
  - **Download phase:** Sources download concurrently (Semaphore-gated: 3 on mobile, 2 on TV).
  - **Ingestion phase:** Downloads feed into a `Channel<DownloadedSource>` consumed in parallel (2 consumers; SQLite handles locking).
  - **Clear:** Saves source configs, destroys the DB file, recreates via Room schema, restores sources with stats reset.
  - **Cancel:** `cancelProcessing()` cancels the coroutine job and calls `RefreshQueue.cancelAll()` to stop queued tasks.
  - **Progress:** Per-source `ActiveSourceProgress` tracking (phase, percent, bytes, channels, programmes) aggregated into `MultiSourceState.Processing`.
  - **States:** `MultiSourceState` sealed interface — `Idle`, `Processing`, `Completed`, `Error`, `Clearing`.
  - **Task queue:** All refresh work is submitted to `RefreshQueue` (priority-ordered, sequential execution) to prevent overlapping ingestion.

## 🔍 Investigation Strategy
When investigating issues:
1. Check `CLAUDE.md` and `AGENTS.md` for specific technical deep-dives.
2. Verify module dependencies in `build.gradle.kts` files.
3. Use `./gradlew ktlintCheck` to ensure style compliance before suggesting changes.
4. Reproduction of bugs on a connected device via `adb` logs is preferred.
