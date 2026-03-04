# AGENTS.md - AI Agent Guide for Fijerena

Welcome to the Fijerena project. This document serves as the master guide for AI agents working on this codebase. It consolidates critical architectural rules, coding standards, development workflows, and detailed feature implementations.

---

## 📖 Project Overview
Fijerena is a premium, native Android media player built with Kotlin and Jetpack Compose. It supports multiple content providers (Xtream IPTV, Jellyfin, SMB, Local files, Remote M3U) and provides a unified, "10-foot UI" experience for TV devices and a touch-optimized UI for mobile.

**Target Devices:** NVIDIA Shield, Chromecast with Google TV, Sony Bravia (Android TV), and Android Mobile (Android 11+).

**App Icon:** Blue Marble (Earth) with red/cyan 3D glasses. Adaptive icon (foreground PNGs in `mobile/src/main/res/drawable-*/ic_launcher_foreground.png`, black background XML) + legacy webp mipmaps.

---

## 🛠️ Tech Stack & Dependencies
Refer to `gradle/libs.versions.toml` for the authoritative versions.
- **Language:** Kotlin 2.3.0
- **Build System:** Gradle 9.2.1
- **UI:** 100% Jetpack Compose (2024.12.01 BOM). `androidx.tv.material3` for TV screens.
- **Media Player:** Media3 ExoPlayer (1.7.1). Optimized for 4K/HDR hardware acceleration.
- **Networking:** Ktor (3.4.0) with OkHttp engine & kotlinx.serialization (JSON).
- **Navigation:** Adaptive Navigation Suite / Navigation Compose with `kotlinx.serialization`.
- **Database:** Room (2.8.4) with FTS4 search.
- **Image Loading:** Coil (3.1.0).
- **SMB Support:** `smbj` (0.13.0).
- **Theming:** Dynamic runtime switching via `CinemaThemeHolder` + `CinemaThemePalette`.

---

## 🏗️ Module Architecture
```
fijerena/
├── mobile/          # Portrait-locked, touch-optimized app
├── tv/              # 10-foot UI, D-pad optimized app
├── core/
│   ├── player/      # Media3 implementation, playback service, domain models
│   ├── network/     # Provider implementations, API clients, EPG pipeline, Room DBs
│   ├── navigation/  # Type-safe Screen definitions (shared)
│   ├── ui/          # Shared ViewModels, design tokens, and components
│   └── data/        # Shared session and auth data
```

### ⚠️ Critical Architectural Constraints
1. **No Circular Dependencies:** `core:player` **must not** depend on `core:network`. If the player needs network settings, it reads directly from `SharedPreferences`.
2. **Unified Domain:** All provider-specific data must be mapped to unified domain models in `core:player/domain/` before reaching the UI.
3. **String IDs:** All media and category IDs must be `String` (not `Int`) to support diverse provider formats (UUIDs, paths, etc.).

---

## 🎨 UI & Coding Standards

### 1. STRICT: No Hardcoded UI Values
Every color, dimension, spacing, and animation duration **must** come from design token constants. Never use raw literals like `16.dp` or `Color.White`.
- **Shared Tokens (core/ui):** `CinemaColors`, `CinemaAlpha`, `CinemaAnimation`, `CinemaCornerRadius`, `CinemaSpacing`.
- **TV Tokens:** `TvDimensions`, `TvFocusTokens`.
- **Mobile Tokens:** `MobileDimensions`.
- **Platform re-exports:** TV `CinemaColors.kt`/`Spacing.kt`, mobile `Color.kt`/`Spacing.kt`.
- **Colors:** Prefer `MaterialTheme.colorScheme.*` or platform re-exports.

### 2. D-Pad & Focus Management (TV)
Every interactive `@Composable` must be D-pad navigable.
- Use `focusRestorer()` and `focusable()`.
- Implement clear focus indicators: Scale 1.0 -> 1.1 (200ms tween), 2dp blue border, 8dp glow. See `FocusModifiers.kt`.
- Avoid complex animations on mid-range TV chipsets (e.g., Sony Bravia).

### 3. Safe Margins (TV Overscan)
Apply TV-safe margins to all root containers (56dp horizontal / 32dp vertical):
- `Spacing.tvSafeMarginHorizontal`, `Spacing.tvSafeMarginVertical`.
- UI should remain 5% away from screen edges.

### 4. Typography
- 13-style Roboto scale (48-14sp).
- **Rule:** All body text **>=18sp** for TV readability.
- UI Scaling (0.4f - 1.0f) is applied globally via `LocalDensity` in `MainActivity.kt`.

---

## 🎬 Player Implementation

### Configuration & Source
- **Source Creation:** Always use `StreamingMediaSourceFactory.createMediaSource()`.
- **Formats:** HLS (`.m3u8`), DASH (`.mpd`), MPEG-TS (`.ts`, `.mpeg`).
- **Buffer Strategy:** `AdaptiveLoadControl` dynamically swaps buffer profiles (Live TV vs VOD, WiFi vs Cellular) at runtime.
- **Codec Priority:** Optimized per device (Shield: AV1 -> HEVC -> AVC; Sony: HEVC -> AVC).

### Controls & Navigation
- **State Management:** `PlaybackViewModel` delegates to `StreamingPlaybackService` (a `MediaSessionService`).
- **OK / Center Key:** **Shows controls only** — it never pauses or resumes playback.
- **Pause:** Explicit via pause button, `KEYCODE_MEDIA_PLAY_PAUSE`, or mobile double-tap (VOD only).
- **Seeking:** Use `PlaybackViewModel.seekRelative(offsetMs)` for relative position changes (FF/Rewind).
- **Channel Overlays (Live TV):** D-pad Left/Right (TV) or Swipe (Mobile) open channel overlays. Use `ChannelListOverlay(panelAlignment=…)` with `slideInHorizontally` and `GlassPanel(backgroundAlpha=0.5f)`.
- **Mobile Gestures:** `detectTapGestures` (tap=controls, double-tap=pause/resume VOD). Merged `detectDragGestures` (vertical=channel switch, horizontal=overlays).

### Features
- **Audio/Subtitle/Quality:** In-playback track switching dialogs.
- **EPG in Player:** Shows current/next programme. Fetched via `getEpgBulkForItems()`.
- **Stats Overlay:** Double-tap OK. Shows codecs, network stats, dropped frames, etc.
- **Auto-resume:** Saves position every 5s; resume if 2-95% progress.

---

## 📡 EPG & Indexing System
- **Pipeline:** `EpgFileManager` manages multi-source XMLTV ingestion using a Channel-based producer-consumer architecture. Downloads run concurrently (coroutine per source), but ingestion into the DB is sequential via an `UNLIMITED` Channel queue. Tasks are submitted through `RefreshQueue`.
- **Indexing:** `EpgIndexer` parses XMLTV into `epg_index.db` (Room, version 8) using FTS4. The `ingest_method` column tracks how each source was ingested.
- **Search:** Two-tier strategy: SQLite **FTS4 MATCH** (primary) -> **LIKE** (fallback).
- **Timezone:** Per-source `timezoneOffsetHours` override applied at parse time.
- **State Machine:** `MultiSourceState` sealed interface: `Idle` -> `Processing` -> `Completed`/`Error`, plus `Clearing` state for clear-all operations. Per-source progress tracked via `ActiveSourceProgress(label, phase, channels, programmes)`.
- **Clear All Data:** Uses DB `destroy()` + `getInstance()` (recreate) instead of `DELETE FROM` — critical for performance on large databases (4M+ rows). Cancel in-flight work via `RefreshQueue.cancelAll()`.
- **ViewModel Resilience:** `EpgManagementViewModel` uses a `db()` function (always calls `EpgIndexDatabase.getInstance()`) and `_dbGeneration` StateFlow counter. After DB destroy/recreate, bumping the generation causes `flatMapLatest` to re-subscribe all Room Flows to the new DB instance.
- **Management:** Multi-source EPG management in `Screen.EpgManagement`.

---

## 🔄 App Navigation & Features

### Flow
1. **Startup:** Auto-navigates based on provider/saved state.
2. **Selection:** Content Type -> Category Grid -> Details (VOD) -> Player.
3. **Navigation IDs:** Always use `String` for IDs.

### Features
- **Search:**
  - **Global Search:** Unified "ALL" search across Live TV, Movies, and TV Shows. Accessible via search button on the Content Type Selection screen.
  - **Collapsible Groups:** Results grouped by source with collapsible headers (saved via `rememberSaveable`).
  - **Xtream:** Two-phase parallel search with multi-word matching.
  - **Jellyfin:** Server-side search.
- **Virtual Categories:** Favorites (configurable 10-500), Last Watched (1-100), Continue Watching (VOD), Recent Categories.
- **Jellyfin Quick Connect:** Supported for easy auth.
- **Settings:** Provider management, theme selection, EPG management, cache management, UI scale, export/import (JSON).

---

## 🔄 Development Workflow

### Build & Install
```bash
./gradlew assembleDebug              # Build both targets
./gradlew :tv:installDebug            # Install to TV (requires adb connect)
./gradlew :mobile:installDebug        # Install to Mobile
```

### Quality Control
```bash
./gradlew lintDebug                   # Run Android Lint
./gradlew check                       # Run all tests and lint
```

### Device-Specific Tips
- **NVIDIA Shield:** Best for 4K/HDR and AV1 testing.
- **Sony Bravia:** Test for UI performance and overscan compliance.
- **Emulator:** HEVC testing is limited; Jellyfin content will trigger transcoding.

---

## 🤖 Agent Workflow Rules
1. **Start** every session by reading project documentation and this file.
2. **Verify** every UI change: "Is this D-pad friendly?"
3. **Never** hardcode dimensions or colors.
4. **Use design tokens** for visual attributes.
5. **Fulfill the Directive:** Only perform modifications when explicitly instructed.
6. **Lint Check:** Run `./gradlew lintDebug` after changes to verify no regressions.
7. **Build Verification:** Jules' jobs should not be considered done unless a build was run and succeeded.

# Agent Instructions

## Model Routing Rules
- [Claude] For boilerplate/docs: Use `haiku`.
- [Gemini] For boilerplate/docs: Use `flash`.
- [Universal] For architectural changes: Use `pro` / `sonnet`.

## Shared Coding Standards (Universal)
- **Style:** Single return statement only.
- **OS:** Ubuntu Linux.

---

## 📓 Performance & Bug Journal

### 2026-02-27 - SharedPreferences JSON deserialization is the #1 hotspot
**Learning:** `getFavoriteCategoryItems()`, `getFavoriteItems()`, and `getFavoriteShowItems()` all deserialize JSON from SharedPreferences on every call with no in-memory cache. Watch history already has this pattern (`cachedWatchHistory`). The favorite category check is called per-chip inside `LazyRow items {}` in `MobileCategoryListScreen`, meaning 500+ deserializations per recompose for large providers.
**Action:** Apply the same in-memory cache + dirty-flag + debounced-write pattern used for watch history to all favorites lists.

### 2026-02-27 - Category reference items treated as streams on long-press
**Learning:** Virtual categories ("Favorite Categories", "Recent Categories") render their entries as `MediaItem` objects with `providerData["isCategoryRef"] = "true"`. The `onItemLongPress` / `onStreamLongPress` handlers in both mobile (`MobileCategoryListScreen`) and TV (`TwoColumnLayout`) were blindly creating `Stream` favorite targets for ALL items, including these category references. This caused long-pressing a category in these lists to call `addFavorite` (stream) instead of `addFavoriteCategory`.
**Action:** Always check `providerData["isCategoryRef"]` before deciding the favorite target type in any item long-press handler.

### 2026-02-27 - Watch history lookups are O(n×m) in refreshPerItemData
**Learning:** `MediaRepository.getPlaybackPosition()` does a linear scan of watch history per call. `refreshPerItemData()` calls it in a loop over every stream (hundreds), making it O(n×m) on the main thread with synchronized locks.
**Action:** Always check for linear scans inside loops first when profiling data layers. Build a Map index for O(1) lookups.

### 2026-02-27 - contentHash self-referential hash bug in XtreamContentManager
**Learning:** `XtreamContentManager` computes `base.hashCode()` on a data class that includes the `contentHash` field (defaulting to 0). The stored entity has a non-zero `contentHash`, so `hashCode()` never matches on re-fetch — causing spurious DB re-inserts on every sync.
**Action:** When using `hashCode()` for change detection on data classes, exclude the hash field itself from computation.

### 2026-03-01 - Clear All EPG Data takes 10+ minutes on Shield TV with DELETE FROM
**Learning:** The EPG index database can grow to 4M+ rows (channels + programmes across multiple sources). Using `DELETE FROM` to clear all data on an NVIDIA Shield TV took over 10 minutes due to SQLite journaling overhead on the low-IOPS flash storage. The UI appeared frozen with no feedback.
**Action:** Replace row-level deletion with DB `destroy()` + `getInstance()` (recreate). This deletes the database file and creates a fresh empty one — completing in under a second regardless of database size. The ViewModel must handle the DB instance changing: use a `db()` function that always calls `getInstance()` and a `_dbGeneration` counter to re-subscribe Room Flows after recreation. Always prefer file-level operations over row-level bulk deletes for large databases on constrained hardware.

### 2026-03-01 - EPG pipeline lacked feedback between download completion and ingestion start
**Learning:** The Channel-based producer-consumer pipeline decouples downloads from ingestion. A large source could finish downloading (progress reaches 100%) but then sit silently in the queue waiting for the single ingestion consumer to drain earlier sources. Users saw the progress jump to 100% and then nothing — appearing frozen.
**Action:** Add an explicit `AwaitingIngestion` phase emitted immediately after a source is sent to the ingestion channel and before the consumer picks it up. Display downloaded bytes in the UI during both the `Downloading` and `AwaitingIngestion` phases so the user understands the source is queued and not stalled.

### 2026-03-01 - Compose recomposition hotspots from un-hoisted allocations
**Learning:** Several recurring patterns caused unnecessary allocations and recompositions:
1. `collectAsState()` instead of `collectAsStateWithLifecycle()` kept flows active when the app was backgrounded, causing redundant recompositions on return.
2. `Color.copy()` called inside `GlassPanel` on every recompose allocated a new Color object each frame.
3. Unnecessary `.toList()` call in `EpgIndexer` batch insert converted a sequence that was already iterable.
4. `System.currentTimeMillis()` and `Date()` called inside composable bodies (not remembered) recalculated on every recompose.
5. `Brush.verticalGradient(...)`, `ButtonDefaults.colors()`, and `FilterChipDefaults.filterChipColors()` allocated inside composables instead of being hoisted outside.
6. `AppSettings` deserialized inside the `while(true)` loop in `EpgFileManager`, causing repeated SharedPreferences JSON parsing on every EPG refresh cycle.
**Action:** Always hoist allocations that don't depend on recomposition-variable state to `remember {}` blocks or to the composable's outer scope. Prefer `collectAsStateWithLifecycle()` for all Flow collection in Composables. Move SharedPreferences reads outside tight loops — treat deserialization as expensive even for small payloads.