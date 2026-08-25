# AGENTS.md - AI Agent Guide for Fijerena

This is the single source of truth for AI agents working on this codebase. All LLM tools (Claude, Gemini, Codex, Copilot, Cursor, etc.) should read this file. Vendor-specific entry points (CLAUDE.md, GEMINI.md, CODEX.md, .cursorrules, .github/copilot-instructions.md) all redirect here.

---

## Project Overview

Fijerena is a premium, native Android media player built with Kotlin and Jetpack Compose. It supports multiple content providers (Xtream IPTV, Jellyfin, SMB, Local files, Remote M3U) and provides a unified experience for TV devices (10-foot UI with D-pad navigation) and mobile (touch-optimized, portrait-locked).

**Target Devices:** NVIDIA Shield, Chromecast with Google TV, Sony Bravia (Android TV), and Android Mobile (Android 11+).

**App Icon:** Blue Marble (Earth) with red/cyan 3D glasses. Adaptive icon (foreground PNGs in `mobile/src/main/res/drawable-*/ic_launcher_foreground.png`, black background XML) + legacy webp mipmaps.

---

## Tech Stack

Refer to `gradle/libs.versions.toml` for authoritative versions.

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 2.3.0 |
| Build System | Gradle | 9.4.1 |
| Build System | Android Gradle Plugin (AGP) | 9.2.1 |
| UI Framework | Jetpack Compose | 2025.06.01 BOM |
| Material Design | Material 3 | 1.4.0 |
| TV Components | androidx.tv.material3 | 1.0.0-alpha10 |
| Video Player | Media3 (ExoPlayer) | 1.7.1 |
| Networking | Ktor (OkHttp engine) | 3.4.0 |
| Serialization | kotlinx.serialization | 1.8.0 |
| Coroutines | kotlinx.coroutines | 1.7.3 |
| Database | Room (with FTS4) | 2.8.4 |
| SQLite | Bundled (FTS5 capable) | 3.49.0 |
| Image Loading | Coil | 3.1.0 |
| Navigation | Navigation Compose | 2.8.5 |
| SMB Client | smbj | 0.13.0 |
| Theming | CinemaThemeHolder + CinemaThemePalette | — |

---

## Module Architecture

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
├── docs/            # In-depth technical documentation (see below)
├── gradle/          # Version catalog (libs.versions.toml)
└── build/           # APK outputs collected here after assembleDebug
```

### Critical Architectural Constraints

1. **No Circular Dependencies:** `core:player` **must not** depend on `core:network`. If the player needs network settings, it reads directly from `SharedPreferences`.
2. **Unified Domain:** All provider-specific data must be mapped to unified domain models in `core:player/domain/` before reaching the UI.
3. **String IDs:** All media and category IDs must be `String` (not `Int`) to support diverse provider formats (UUIDs, paths, numeric IDs).
4. **Dependency Injection:** Always use `AppContainer` (in `core:ui`) to obtain repository singletons (`MediaRepository`, `ProviderRepository`). Never manually instantiate repositories in ViewModels.
5. **Async Initialization:** ViewModels must initialize repository dependencies asynchronously to prevent UI thread blocking during screen composition.

---

## UI & Coding Standards

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

### 5. Coding Style

- **Single return statement** per function only.
- **OS:** Ubuntu Linux development environment.
- **Lint:** Run `./gradlew ktlintCheck` to verify style. Use `./gradlew ktlintFormat` to auto-fix.

---

## Player Implementation

### Configuration & Source

- **Source Creation:** Always use `StreamingMediaSourceFactory.createMediaSource()`.
- **Formats:** HLS (`.m3u8`), DASH (`.mpd`), MPEG-TS (`.ts`, `.mpeg`).
- **Buffer Strategy:** `AdaptiveLoadControl` dynamically swaps buffer profiles (Live TV vs VOD, WiFi vs Cellular) at runtime.
- **Codec Priority:** Optimized per device (Shield: AV1 -> HEVC -> AVC; Sony: HEVC -> AVC).

### Controls & Navigation

- **State Management:** `PlaybackViewModel` delegates to `StreamingPlaybackService` (a `MediaSessionService`).
- **OK / Center Key:** **Shows controls only** — it never pauses or resumes playback.
- **Double-OK:** Dismisses the stats overlay if visible.
- **Pause:** Explicit via pause button, `KEYCODE_MEDIA_PLAY_PAUSE`, or mobile double-tap (VOD only).
- **Seeking / Navigation:** 
  - **VOD:** Use `PlaybackViewModel.seekRelative(offsetMs)` for relative position changes (FF/Rewind).
  - **TV Shows:** D-pad Left/Right (TV) or Swipe (Mobile) to skip between episodes in-player.
- **Channel Overlays (Live TV):** D-pad Left/Right (TV) or Swipe (Mobile) open channel overlays. TV: `TvChannelListOverlay(panelAlignment=…)` with `slideInHorizontally` and `GlassPanel(backgroundAlpha=0.5f)`. Mobile: `MobileChannelListSheet`.
- **Preview Pane / Dock (Live TV browse):** Channel plays alongside the list while browsing — TV: focus-driven split (`LiveTvSplitLayout`); Mobile: tap-driven docked mini-player (`MobileCategoryListScreen`). Both promote to full-screen on the same engine connection (no restart). Each platform guarantees Back always has a real stopover before exiting Live TV — see `docs/NAVIGATION_GUIDE.md` → "Live TV Preview / Dock Back-Stack".
- **Mobile Gestures:** `detectTapGestures` (tap=controls, double-tap=pause/resume VOD). Merged `detectDragGestures` (vertical=channel switch, horizontal=overlays).

### Features

- **Stats Overlay:** Double-tap OK. Comprehensive diagnostics (codecs, network speed, dropped frames, build info). Repositionable to 4 corners via D-pad. Non-focusable on TV.
- **Stream Info Overlay:** Top-left panel showing resolution and codec underneath the title.
- **Auto-resume:** Saves position every 10s (Live) or based on progress (VOD); resumes if 2-95% progress.
- **Watch History Rules:**
  - **Live TV:** Added to history after **10 seconds** of continuous playback.
  - **VOD (Movies/Series):** Added to history only after reaching a **2% watch threshold**.
  - **Session Finalization:** `loaderViewModel.stopPlayback()` MUST be called when exiting the player or switching streams to ensure final progress is reported and history is flushed to disk.

---

## EPG & Indexing System

- **Pipeline:** `EpgFileManager` manages multi-source XMLTV ingestion using a Channel-based producer-consumer architecture. Downloads run concurrently (Semaphore-gated: 3 on mobile, 2 on TV), and ingestion into the DB is parallelized (2 workers) via an `UNLIMITED` Channel queue. User-initiated refreshes are submitted through `RefreshQueue`; the coroutine-based auto-refresh (`awaitRefreshOutdatedSources`) and `EpgSyncWorker` call `processAllSourcesInternal` directly to avoid releasing the wake lock on Shield/Doze.
- **Stale Threshold:** `staleThresholdMs` = user refresh interval / 2. Sources older than this are picked up by auto-refresh and `EpgSyncWorker`. Defaults to 24h when the interval is "Never" (≤ 0).
- **Indexing:** `EpgIndexer` parses XMLTV into `epg_index.db` (Room, version 16) using FTS4. The `ingest_method` column tracks how each source was ingested.
- **Search:** Two-tier strategy, both via SQLite **FTS4 MATCH** in `XmltvSearchService`: a raw query preserving FTS operators (OR/NEAR/NOT, prefix wildcard), then a sanitized "safe" AND-style retry if the raw query returns nothing. No LIKE or XML-scan fallback exists — if the index isn't built yet (`EpgIndexState.NotIndexed`), search returns null; if the FTS index is mid-rebuild (`isFtsStale()`), search throws rather than degrading to a full scan. The old FTS index stays usable until `rebuildFtsAndUpdateState()` actually starts (stale is marked at entry, not at dispatch), so this only affects the actual rebuild window, not the scheduling gap.
- **Timezone:** Per-source `timezoneOffsetHours` override applied at parse time.
- **State Machine:** `MultiSourceState` sealed interface: `Idle` -> `Processing` -> `Completed`/`Error`, plus `Clearing` state. Per-source progress tracked via `ActiveSourceProgress(label, phase, channels, programmes)`.
- **Persistent Stats:** Pipeline completion triggers an update to `EpgPipelineStatsEntity` in `providers.db` (version 8).
- **Clear All Data:** Uses DB `destroy()` + `getInstance()` (recreate) instead of `DELETE FROM` — critical for performance on large databases (4M+ rows). Cancel in-flight work via `RefreshQueue.cancelAll()`.
- **ViewModel Resilience:** `EpgManagementViewModel` uses a `db()` function (always calls `EpgIndexDatabase.getInstance()`) and `_dbGeneration` StateFlow counter. After DB destroy/recreate, bumping the generation causes `flatMapLatest` to re-subscribe all Room Flows to the new DB instance.
- **Management:** Multi-source EPG management in `Screen.EpgManagement`.

---

## App Navigation & Features

### Flow

1. **Startup:** Always lands on the Content Type Selection screen if a provider is configured; otherwise, navigates to Settings.
2. **Selection:** Content Type -> Category Grid -> Details (VOD) -> Player.
3. **Navigation IDs:** Always use `String` for IDs.

### Features

- **Search:**
  - **Global Search:** Unified "ALL" search across Live TV, Movies, and TV Shows from the Content Type Selection screen.
  - **Collapsible Groups:** Results grouped by source with collapsible headers (saved via `rememberSaveable`).
  - **Xtream:** Two-phase parallel search with multi-word matching.
  - **Jellyfin:** Server-side search.
- **Virtual Categories:** Favorites (configurable 10-500), Last Watched (1-100), Continue Watching (VOD), Recent Categories.
- **Jellyfin Quick Connect:** Supported for easy auth.
- **Settings:** Provider management, theme selection, EPG management, cache management, UI scale, export/import (JSON).

---

## Multi-Provider Support

| Provider | Live TV | Movies | TV Shows | EPG | Search | Progress Sync |
|----------|---------|--------|----------|-----|--------|---------------|
| **Xtream** | Yes | Yes | Yes | Yes | Client-side | No |
| **Jellyfin** | No | Yes | Yes | No | Server-side | Yes |
| **SMB** | No | Yes | No | No | Filename | No |
| **Local** | M3U only | Yes | No | No | Filename | No |
| **Remote M3U** | Yes | No | No | No | Yes | No |

---

## Development Workflow

### Build & Install

```bash
./gradlew assembleDebug              # Build both targets
./gradlew :tv:installDebug            # Install to TV (requires adb connect)
./gradlew :mobile:installDebug        # Install to Mobile
```

### Quality Control

```bash
./gradlew ktlintCheck                 # Check code style
./gradlew ktlintFormat                # Auto-format code
./gradlew lintDebug                   # Run Android Lint
./gradlew check                       # Run all tests and lint
```

### APK Outputs

Standard AGP outputs generated per module:
- `tv/build/outputs/apk/debug/tv-debug.apk`
- `mobile/build/outputs/apk/debug/mobile-debug.apk`

### Deployment

TV and Mobile share the same `applicationId` (`org.njarasoa.fijerena`). Use `adb -s <device_id>` when multiple devices are connected.

**Strict Deployment Rules:**
- **Pre-Deployment Clean Build:** Whenever changes span multiple modules (e.g. modifying `core:*` libraries consumed by `:tv` or `:mobile`), never deploy from an incremental build. Always build via `./gradlew clean assembleDebug` to prevent stale intermediate DEX shards (`NoClassDefFoundError`).
- **No Auto-Launch on Install:** Never automatically launch the app or inject monkey/activity launch intents after installing via `adb install -r` unless explicitly instructed by the user. Let the user launch the app manually when ready.

### Device-Specific Tips

- **NVIDIA Shield:** Best for 4K/HDR and AV1 testing.
- **Sony Bravia:** Test for UI performance and overscan compliance.
- **Emulator:** HEVC testing is limited; Jellyfin content will trigger transcoding.

---

## Agent Workflow Rules

1. **Read first.** Start every session by reading this file and relevant docs.
2. **Verify every UI change:** "Is this D-pad friendly?"
3. **Never** hardcode dimensions or colors.
4. **Use design tokens** for all visual attributes.
5. **Only modify when explicitly instructed.** Do not make speculative changes.
6. **Lint check:** Run `./gradlew lintDebug` after changes to verify no regressions.
7. **Build verification:** Changes are not done until a build succeeds.

### Investigation Strategy

When investigating issues:
1. Read this file and relevant docs in `docs/` for technical context.
2. Verify module dependencies in `build.gradle.kts` files.
3. Run `./gradlew ktlintCheck` to ensure style compliance before suggesting changes.
4. Prefer reproducing bugs on a connected device via `adb` logs.

---

## In-Depth Documentation

For deep-dives, see the `docs/` directory:

| Document | Contents |
|----------|----------|
| [docs/design.md](docs/design.md) | Full system design: module graph, domain model, player system, EPG architecture, theme system, screen inventory |
| [docs/FEATURES.md](docs/FEATURES.md) | Comprehensive feature reference with API details |
| [docs/DATABASE_SCHEMA.md](docs/DATABASE_SCHEMA.md) | Complete database schema for all Room DBs and SharedPreferences |
| [docs/epg_guide.md](docs/epg_guide.md) | EPG pipeline implementation guide with data models and file inventory |
| [docs/NAVIGATION_GUIDE.md](docs/NAVIGATION_GUIDE.md) | Type-safe navigation system, screen definitions, and flow diagrams |
| [docs/EPG_INDEX_STORAGE.md](docs/EPG_INDEX_STORAGE.md) | Why the EPG index grew to 87% dead space, the PRAGMA/Requery traps behind it, and how to read DB state from a file header |
| [docs/ui-theme-options.md](docs/ui-theme-options.md) | Theme system design decisions and options |
| [docs/MOBILE_RUN_GUIDE.md](docs/MOBILE_RUN_GUIDE.md) | Mobile build, install, and run guide |
| [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md) | Version history and changelog |
| [TODO.md](TODO.md) | Known issues, open investigations, and optimization status |

---

## Performance & Bug Journal

Hard-won lessons from production debugging. Read these before making changes in related areas.

### SharedPreferences JSON deserialization is the #1 hotspot
**Context:** `getFavoriteCategoryItems()`, `getFavoriteItems()`, and `getFavoriteShowItems()` all deserialize JSON from SharedPreferences on every call with no in-memory cache. Called per-chip inside `LazyRow items {}` — 500+ deserializations per recompose for large providers.
**Fix:** Apply in-memory cache + dirty-flag + debounced-write pattern (same as `cachedWatchHistory`).

### Watch history lookups are O(n*m) in refreshPerItemData
**Context:** `MediaRepository.getPlaybackPosition()` does a linear scan of watch history per call. `refreshPerItemData()` calls it in a loop over every stream.
**Fix:** Build a Map index for O(1) lookups. Always check for linear scans inside loops when profiling data layers.

### contentHash self-referential hash bug in XtreamContentManager
**Context:** `hashCode()` on a data class that includes the `contentHash` field (defaulting to 0). The stored entity has a non-zero `contentHash`, so `hashCode()` never matches — causing spurious DB re-inserts on every sync.
**Fix:** Exclude the hash field itself from `hashCode()` computation.

### Clear All EPG Data takes 10+ minutes with DELETE FROM
**Context:** 4M+ rows on NVIDIA Shield with low-IOPS flash storage.
**Fix:** Replace row-level deletion with DB `destroy()` + `getInstance()` (recreate). Use `db()` function + `_dbGeneration` counter to re-subscribe Room Flows after recreation.

### EPG pipeline lacked feedback between download and ingestion
**Context:** Channel-based producer-consumer pipeline decouples downloads from ingestion. Large source finishes downloading but sits silently queued.
**Fix:** Add explicit `AwaitingIngestion` phase emitted after source is sent to the ingestion channel.

### Compose recomposition hotspots from un-hoisted allocations
**Patterns to avoid:**
1. `collectAsState()` instead of `collectAsStateWithLifecycle()` — keeps flows active when backgrounded.
2. `Color.copy()` called inside composables — allocates every frame.
3. `System.currentTimeMillis()` / `Date()` inside composable bodies without `remember {}`.
4. `Brush.verticalGradient()`, `ButtonDefaults.colors()`, `FilterChipDefaults.filterChipColors()` allocated inside composables instead of hoisted.
5. `AppSettings` deserialized inside tight loops (e.g., `while(true)` in `EpgFileManager`).

**Rule:** Hoist allocations that don't depend on recomposition state to `remember {}` or outer scope. Prefer `collectAsStateWithLifecycle()`. Treat SharedPreferences deserialization as expensive.

### Category references treated as streams on long-press
**Context:** Virtual categories render entries as `MediaItem` with `providerData["isCategoryRef"] = "true"`. Long-press handlers were creating `Stream` favorite targets for ALL items.
**Fix:** Always check `providerData["isCategoryRef"]` before deciding the favorite target type.
