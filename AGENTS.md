# AGENTS.md - AI Agent Guide for Fijerena

Welcome to the Fijerena project. This document serves as the master guide for AI agents working on this codebase. It consolidates critical architectural rules, coding standards, and development workflows.

---

## 📖 Project Overview
Fijerena is a premium, native Android media player built with Kotlin and Jetpack Compose. It supports multiple content providers (Xtream IPTV, Jellyfin, SMB, Local files, Remote M3U) and provides a unified, "10-foot UI" experience for TV devices and a touch-optimized UI for mobile.

**Target Devices:** NVIDIA Shield, Chromecast with Google TV, Sony Bravia (Android TV), and Android Mobile (5.0+).

---

## 🛠️ Tech Stack & Dependencies
Refer to `gradle/libs.versions.toml` for the authoritative versions.
- **Language:** Kotlin 2.3.0
- **UI:** Jetpack Compose (2024.12.01 BOM)
- **TV UI:** `androidx.tv.material3` (1.0.0-alpha10)
- **Media Player:** Media3 ExoPlayer (1.9.1)
- **Networking:** Ktor (3.4.0) with OkHttp engine
- **Database:** Room (2.8.4) with FTS4 search
- **Navigation:** Navigation Compose with `kotlinx.serialization`
- **Image Loading:** Coil (3.1.0)
- **SMB Support:** `smbj` (0.13.0)

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
- **Colors:** Use `CinemaColors` or `MaterialTheme.colorScheme`.
- **Spacing:** Use `CinemaSpacing` (xxs to xxl).
- **Typography:** Minimum **18sp** for body text on TV.

### 2. D-Pad & Focus Management (TV)
Every interactive `@Composable` must be D-pad navigable.
- Use `focusRestorer()` and `focusable()`.
- Implement clear focus indicators (Scale 1.0 -> 1.1, blue border, glow).
- Avoid complex animations on mid-range TV chipsets (e.g., Sony Bravia).

### 3. Safe Margins (TV Overscan)
Apply TV-safe margins to all root containers to compensate for overscan:
- **Horizontal:** 56dp (`Spacing.tvSafeMarginHorizontal`)
- **Vertical:** 32dp (`Spacing.tvSafeMarginVertical`)
- UI should remain 5% away from screen edges.

---

## 🎬 Player Implementation
- **Source Creation:** Always use `StreamingMediaSourceFactory.createMediaSource()`.
- **Buffer Strategy:** `AdaptiveLoadControl` dynamically swaps buffer profiles (Live TV vs VOD, WiFi vs Cellular) at runtime.
- **Codec Priority:** Optimized per device (Shield: AV1 -> HEVC -> AVC; Sony: HEVC -> AVC).
- **State Management:** `PlaybackViewModel` delegates to `StreamingPlaybackService` (a `MediaSessionService`).
- **Seeking:** Use `PlaybackViewModel.seekRelative(offsetMs)` for relative position changes (FF/Rewind).
- **Controls — critical rules:**
  - OK / center key **shows controls only** — it never pauses or resumes playback.
  - Pause is explicit: pause button, `KEYCODE_MEDIA_PLAY_PAUSE`, or mobile double-tap (VOD only).
  - D-pad Left/Right on Live TV open channel overlay panels (never seek on live).
  - Channel overlays use `ChannelListOverlay(panelAlignment=…)` with `slideInHorizontally` animations and `GlassPanel(backgroundAlpha=0.5f)`.
  - Mobile uses `detectTapGestures` (not `.clickable`) and merged `detectDragGestures` (vertical=channel switch, horizontal=overlays).

---

## 📡 EPG & Indexing System
- **Pipeline:** `EpgFileManager` manages multi-source XMLTV ingestion (streaming on TV, temp-file on mobile).
- **Indexing:** `EpgIndexer` parses XMLTV into `epg_index.db` using Room batch transactions.
- **Search:** Two-tier strategy: SQLite **FTS4 MATCH** (primary) -> **LIKE** (fallback).
- **Timezone:** Per-source `timezoneOffsetHours` override is applied at parse time.

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
./gradlew ktlintCheck                 # Check code style
./gradlew ktlintFormat                # Auto-fix code style
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
