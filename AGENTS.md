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
| Language | Kotlin (compiler/plugin; stdlib resolves 2.4.0) | 2.3.0 |
| Build System | Gradle | 9.6.0 |
| Build System | Android Gradle Plugin (AGP) | 9.4.1 |
| UI Framework | Jetpack Compose | 2026.03.01 BOM (ui/foundation/runtime/animation 1.10.6) |
| Material Design | Material 3 | 1.4.0 |
| TV Components | androidx.tv.material3 (tv-foundation only as its transitive dep; the app uses plain LazyColumn/LazyRow) | 1.0.0-alpha10 |
| Video Player | Media3 (ExoPlayer) | 1.7.1 |
| Networking | Ktor (OkHttp engine) | 3.5.2 |
| Serialization | kotlinx.serialization | 1.11.0 |
| Coroutines | kotlinx.coroutines | 1.11.0 |
| Database | Room (with FTS4) | 2.8.4 |
| SQLite | Bundled (FTS5 capable) | 3.49.0 |
| Image Loading | Coil | 3.5.0 |
| Navigation | Navigation Compose | 2.8.5 |
| SMB Client | smbj | 0.15.0 |
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
├── server/          # Live sync server (TypeScript; Cloudflare Worker or workerd in Docker) — see server/README.md
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
6. **Long-lived scopes:** Any scope that outlives a screen (singletons, services, repositories) comes from `AppScopes.create(name, dispatcher)` (`core:player/diagnostics`), never a bare `CoroutineScope(...)` — an exception escaping a bare scope kills the process. `AppScopes` logs it and records it in `CrashLog`.
7. **Don't swallow cancellation:** In suspend code, a `catch (e: Exception)` needs a `catch (e: CancellationException) { throw e }` before it, and `runCatching` around a suspend call is `suspendRunCatching` (`core:network`) — otherwise a cancelled job carries on and publishes stale state. `scripts/check-cancellation.sh` (run by CI) enforces it; mark a deliberate or non-suspend case with `// cancellation-ok: <reason>`; its file allow-list (legacy files) only shrinks.
8. **Service from Compose:** In a `LaunchedEffect`/composition-scoped coroutine use `StreamingPlaybackService.awaitInstanceOrNull()`, never bare `awaitInstance()` — its `ServiceDestroyedException` escaping a composition coroutine crashes the app.
9. **`xtream_v2.db` holds user data:** every version bump needs a real `Migration`; the destructive fallback covers only pre-v7 files. See `docs/DATABASE_SCHEMA.md` §3.
10. **Secrets never fall back to plaintext:** an encrypted store that can't be opened is reset (`CredentialStoreHealth.markLost`) and, if it still can't be created, replaced by `CredentialStoreHealth.InMemoryPrefs` — never `getSharedPreferences`.

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
- **Lists are plain `LazyColumn`/`LazyRow`.** Never `TvLazyColumn`/`TvLazyRow`: `tv-foundation` 1.0.0-alpha10 calls a prefetch API removed in Compose 1.9 and crashes on scroll.
- **Back on TV:** where a focused `Button`/`Surface` exists, intercept Back in `onPreviewKeyEvent` on the root — `BackHandler` misses the first press. See `docs/NAVIGATION_GUIDE.md` → "TV Back on Detail Screens".
- **Every screen/panel lands focus somewhere visible on open and returns it to where the user was on Back** — see `docs/NAVIGATION_GUIDE.md` → "D-Pad Focus Handling".

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
  ktlint-gradle 14.2.0 with ktlint 1.8.0 (ktlint-gradle older than 14.1 lints nothing under AGP 9). Rules are in `.editorconfig`; put a `// cancellation-ok: <reason>` marker on the catch line or the line after it.

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
- **Pause:** Explicit via pause button or `KEYCODE_MEDIA_PLAY_PAUSE`. Mobile double-tap no longer pauses — see Mobile Gestures below.
- **Seeking / Navigation:** 
  - **VOD:** Use `PlaybackViewModel.seekRelative(offsetMs)` for relative position changes (FF/Rewind).
  - **TV Shows:** D-pad Left/Right (TV) or Swipe (Mobile) to skip between episodes in-player.
- **Channel Overlays (Live TV):** D-pad Left/Right (TV) or Swipe (Mobile) open channel overlays. TV: `TvChannelListOverlay(panelAlignment=…)` with `slideInHorizontally` and `GlassPanel(backgroundAlpha=0.5f)`. Mobile: `MobileChannelListSheet`.
- **Preview Pane / Dock (Live TV browse):** Channel plays alongside the list while browsing — TV: focus-driven split (`LiveTvSplitLayout`); Mobile: tap-driven docked mini-player (`MobileCategoryListScreen`). Both promote to full-screen on the same engine connection (no restart). Each platform guarantees Back always has a real stopover before exiting Live TV — see `docs/NAVIGATION_GUIDE.md` → "Live TV Preview / Dock Back-Stack".
- **Mobile Gestures:** `detectTapGestures` (tap=controls; double-tap=10s relative seek, VOD only — left 40% of the width rewinds, right 40% seeks forward, center 20% does nothing). Merged `detectDragGestures` (vertical=channel switch, horizontal=overlays).

### Features

- **Stats Overlay:** Double-tap OK. Comprehensive diagnostics (codecs, network speed, dropped frames, build info). Repositionable to 4 corners via D-pad. Non-focusable on TV.
- **Stream Info Overlay:** Top-left panel showing resolution and codec underneath the title.
- **Auto-resume:** Saves position every 10s (Live) or based on progress (VOD); resumes if 2-95% progress.
- **Watch History Rules:**
  - **Live TV:** Added to history after **10 seconds** of continuous playback.
  - **VOD (Movies/Series):** Added to history only after reaching a **2% watch threshold**.
  - **Session Finalization:** `loaderViewModel.stopPlayback()` MUST be called when exiting the player or switching streams to ensure final progress is reported and history is flushed to disk.
- **Watch State Storage:** Position and completion live in the durable `watch_state` Room table (`xtream_v2.db` v17), **not** in SharedPreferences — the old `watch_history_v3` blob truncated to `watchHistorySize` on every write and is now migrated and purged on first `setProvider()`. `watchHistorySize` still bounds the *Recent* row's length, never what is stored. Reads go through `MediaRepository`; never re-introduce a blob writer.
  - **Completion is sticky:** progress upserts do `MAX(existing, new)` on `isCompleted`. Only `setWatched(false)` clears it.
  - **Manual marking:** `MediaRepository.setWatched(itemId, contentType, watched)`. No-ops for server-backed providers (Jellyfin owns that state). A manual mark leaves `lastPlayedAt` null so it never pollutes the Recent row.
  - **TMDB dedup:** movies join `xtream_streams` on shared `tmdbId`; episodes join `xtream_series` on **series-level** `tmdbId`, then match `(season, episodeNum)` — episode-level `tmdbId` is effectively never populated by providers and must not be used for this.
  - **Track prefs:** `audioTrackIndex`/`subtitleTrackIndex` persist per row, with a series-level fallback so a new episode inherits the last choice made in that series.

---

## EPG & Indexing System

- **Pipeline:** `EpgFileManager` manages multi-source XMLTV ingestion using a Channel-based producer-consumer architecture. Downloads run concurrently (Semaphore-gated: 3 on mobile, 2 on TV), and ingestion into the DB is parallelized (2 workers) via an `UNLIMITED` Channel queue. User-initiated refreshes are submitted through `RefreshQueue`; the coroutine-based auto-refresh (`awaitRefreshOutdatedSources`) and `EpgSyncWorker` call `processAllSourcesInternal` directly to avoid releasing the wake lock on Shield/Doze.
- **Stale Threshold:** `staleThresholdMs` = user refresh interval / 2. Sources older than this are picked up by auto-refresh and `EpgSyncWorker`. Defaults to 24h when the interval is "Never" (≤ 0).
- **Indexing:** `EpgIndexer` parses XMLTV into `epg_index.db` (Room, version 16) using FTS4. The `ingest_method` column tracks how each source was ingested.
- **Search:** Two-tier strategy, both via SQLite **FTS4 MATCH** in `XmltvSearchService`: a raw query preserving FTS operators (OR/NEAR/NOT, prefix wildcard), then a sanitized "safe" AND-style retry if the raw query returns nothing. If the index isn't built yet (`EpgIndexState.NotIndexed`), search returns null. If the FTS index is stale (`isFtsStale()` — a low-storage direct-path refresh, or an interrupted rebuild), search falls back to a title-only `LIKE` scan of `epg_programme.title_lowercase` (`EpgSearchPath.LIKE_FALLBACK`, same window/500 cap/10 s timeout; the screens note results may be incomplete); only if that times out or fails does it throw `EpgIndexBusyException`. `EpgBrowserViewModel` shows `UiState.IndexBusy` (why, not "no results") and reruns a busy or `LIKE_FALLBACK` query when `EpgIndexer.state` reaches `Indexed` — so `rebuildFtsAndUpdateState()` clears the stale flag *before* publishing `Indexed`. The staging path never goes stale: `EpgIndexer.swapAndRebuildFts()` does the staging→primary swap and the FTS `'rebuild'` in one transaction, so WAL readers never see new rows with the old FTS — keep them together. No XML-scan fallback exists.
- **Timezone:** Per-source `timezoneOffsetHours` override applied at parse time.
- **State Machine:** `MultiSourceState` sealed interface: `Idle` -> `Processing` -> `Completed`/`Error`, plus `Clearing` state. Per-source progress tracked via `ActiveSourceProgress(label, phase, channels, programmes)`.
- **Change Detection:** `downloadSource` sends `If-None-Match`/`If-Modified-Since` from the source's stored `etag`/`last_modified_header`; a `304` short-circuits with no body read. Otherwise a SHA-256 of the payload is compared to `last_content_sha256` (computed during the download pass for plain sources, after decompression for `.gz` — gzip's mtime header taints the raw bytes). A confirmed-unchanged source skips `ingestFromStream` entirely, is excluded from `swapAndRebuildFts`'s id list (its staging table was never populated), and carries its previous counts forward via `EpgSourceDao.markUnchanged`. A hash match only skips within 24h of the last real ingest — ingestion drops programmes that ended more than 12h ago (wall-clock), so a byte-identical file is still re-ingested daily or long-ended programmes pile up. There is no future limit on ingest: everything ahead the source provides is kept.
- **Download Integrity:** `read()` returning -1 alone can't distinguish a clean EOF from a cut connection. `totalRead` is checked against `Content-Length` when the server sends one; a mismatch is a download error and takes the normal retry path.
- **Persistent Stats:** Pipeline completion triggers an update to `EpgPipelineStatsEntity` in `providers.db` (version 10).
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
  - **Xtream:** Local FTS4 prefix search over the synced catalogue (`xtream_streams_fts` / `xtream_series_fts`), no network call; a second FTS query counts matches hidden by category filters.
  - **Jellyfin:** Server-side search.
- **Virtual Categories:** Favorites (configurable 10-500), Last Watched (1-100), Continue Watching (VOD), Recent Categories.
- **Mark Watched/Unwatched:** Manual toggle on movie details (icon beside the favorite toggle), TV content lists and search (`FavoriteContextMenuDialog`/`SearchFavoriteDialog` second action row), TV episode cards (long-press), and the mobile episode watched badge (itself the tap target). Each surface reuses its existing affordance — do not invent a new one.
- **Sync Feedback:** Provider screens show the last sync's catalog delta ("No changes since last sync", or "N added • N updated • N removed"), gated on Xtream and on `lastSyncError` being null. EPG management shows "Unchanged" in place of durations for a source the last run confirmed unchanged.
- **Jellyfin Quick Connect:** Supported for easy auth.
- **Settings:** Provider management, theme selection, EPG management, cache management, database maintenance ("Shrink Database"), UI scale, export/import (JSON).
- **Database Maintenance & Compaction:** When providers are deleted, `ProviderRepository.deleteProvider` cascades through all catalog tables in `xtream_v2.db` (`xtream_streams`, `xtream_series`, `xtream_episodes`, `xtream_categories`, `favorite_state`, `xtream_epg_cache`), clears orphaned SharedPreferences (`provider_creds_*`, `media_cache_*`, `xtream_cache_*`), deleting catalogue rows 1,000 per commit; no automatic `VACUUM` (`auto_vacuum = FULL` already returns space, and a WAL-mode `VACUUM` needs room for the whole database — see `docs/DATABASE_SCHEMA.md` §3). A background self-healing sweep (`pruneOrphanedCatalogData`) runs on app launch and during `EpgSyncWorker` to clean up past orphaned rows. The "Shrink Database" button in Settings is the only place a `VACUUM` runs. A circuit breaker prevents deletions if the valid provider list is empty.

---

## Multi-Provider Support

| Provider | Live TV | Movies | TV Shows | EPG | Search | Progress Sync |
|----------|---------|--------|----------|-----|--------|---------------|
| **Xtream** | Yes | Yes | Yes | Yes | Client-side | No |
| **Jellyfin** | No | Yes | Yes | No | Server-side | Yes |
| **SMB** | No | Yes | No | No | Filename | No |
| **Local** | M3U only | Yes | No | No | Filename | No |
| **Remote M3U** | Yes | No | No | No | Yes | No |

**Wording:** in the UI these are **sources** ("Add Source", "Manage Sources"; French *source*, feminine; Malagasy *loharano*), and EPG/XMLTV feeds are **guide sources** (*source de guide*, *loharanon'ny fitarihana*). Code keeps "provider" (`ProviderEntity`, the `providers` table, `providerId`, string keys such as `provider_add_title`), so new strings use the new words with the old key style. "Provider" stays only where it means the company that sells the IPTV service (`login_footer_text`). See `docs/plans/20261002_provider-to-source-rename-plan.md`.

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
- **Always Use Official Deploy Scripts:** Whenever deploying to network TVs or USB hardware, always use `scripts/deploy-tv-ip.sh <ip>` or `scripts/deploy-mobile-usb.sh`. Never run raw `adb install` commands manually. The deploy scripts check `dumpsys media_session` for active playback (`state=PlaybackState {state=3}`) to prevent interrupting someone streaming in the household, confirm with the user before deploying, and automatically manage backups.
- **Incremental deploys:** the deploy scripts no longer run `clean` (2026-10-02). If an install crashes with `NoClassDefFoundError` after changes across modules (`core:*` consumed by `:tv`/`:mobile`), that is a stale DEX shard: run `./gradlew clean` and deploy again.
- **No Auto-Launch on Install:** Never automatically launch the app or inject monkey/activity launch intents after installing via `adb install -r` unless explicitly instructed by the user. Let the user launch the app manually when ready.
- **NEVER install to real hardware without backing up app data first.** `adb install -r` is *not* guaranteed to preserve app data — a signing-key mismatch (or other cause) can make it install-fresh with no warning, silently wiping providers/favorites/watch history. This happened for real on 2026-09-08: a deploy to all three household TVs (2 Shields + 1 Bravia, all daily-use, real config) came back "Success" on every device but had actually wiped every one (`firstInstallTime == lastUpdateTime == the deploy timestamp` — a fresh install, not an update). No backup existed. Nothing was recoverable. Before *any* `adb install`/`gradle install*` targeting a device that isn't a disposable emulator, back up first — `shared_prefs/*` + `providers.db*` via `run-as tar` (see `docs/RUN_GUIDE.md` or ask if unsure of the exact command). Explicit permission to deploy is not permission to skip this — do it every time, unprompted, or say why you can't and ask before installing.

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
| [docs/DATABASE_SCHEMA.md](docs/DATABASE_SCHEMA.md) | Complete database schema for all Room DBs and SharedPreferences. **Update it in the same commit as any migration** - see below |
| [docs/epg_guide.md](docs/epg_guide.md) | EPG pipeline implementation guide with data models and file inventory |
| [docs/NAVIGATION_GUIDE.md](docs/NAVIGATION_GUIDE.md) | Type-safe navigation system, screen definitions, and flow diagrams |
| [docs/EPG_INDEX_STORAGE.md](docs/EPG_INDEX_STORAGE.md) | Why the EPG index grew to 87% dead space, the PRAGMA/Requery traps behind it, and how to read DB state from a file header |
| [docs/RUN_GUIDE.md](docs/RUN_GUIDE.md) | Unified build, install, and deployment guide for TV and mobile |
| [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md) | Version history and changelog |

### Schema changes

**Every Room migration updates `docs/DATABASE_SCHEMA.md` in the same commit.** Not as a follow-up. That doc is the only prose description of the schema, so a migration that lands without it leaves the file stating a version number the code no longer uses - which is exactly how it drifted before (`providers.db` documented as v8 when it was v10, `xtream_v2.db` as v14 when it was v15).

A migration means any of: a new `MIGRATION_n_n+1`, a version bump, a new entity, a new column, a new index. For each, update:

- the database's `**Version:**` line;
- the parenthetical migration history in that section's intro ("v15 added `watch_state`…"), appending the new entry;
- a full table section for a new table - every column with type and description, the primary key, and each index;
- any new SharedPreferences key the migration introduces, especially one-time backfill/purge flags (`watch_state_migrated_v1`, `favorites_migrated_v1`), in the scalar-keys table of §4;
- when a migration replaces a SharedPreferences blob: remove that key from the §4 table and add a **Retired:** note naming the table that now owns it and the backfill hook that copies it.

**Commit the generated Room schema too.** `xtream_v2.db` and `providers.db` export their schema (`exportSchema = true`, KSP `room.schemaLocation`) to `core/network/schemas/<database class>/<version>.json`. The build writes the new version's file; commit it with the migration. CI fails if a build leaves an uncommitted change there (an entity changed without a version bump) or finds a blanket `fallbackToDestructiveMigration(` in either database. History starts at `xtream_v2.db` v24 / `providers.db` v15 — older versions have no JSON.

---

### Plans

Multi-phase work is planned in writing before it is built, in `docs/plans/<kebab-case-topic>-plan.md`. Everything else in `docs/` is standing reference material that describes how the app works today; a plan describes work that is proposed, in progress, or deliberately deferred.

Each plan states its own status at the top - trust that over any summary here.

| Plan | Status |
|------|--------|
| [docs/plans/20260824_codebase-audit-fix-plan.md](docs/plans/20260824_codebase-audit-fix-plan.md) | 29/29 complete (T1-T4) |
| [docs/plans/20260826_tv-ui-performance-plan.md](docs/plans/20260826_tv-ui-performance-plan.md) | Partially landed; baseline measured on hardware 2026-08-26. Open: Live TV back-out animation tail (6b), mobile `RelatedTitlesRow`, Xtream EPG cache sizing |
| [docs/plans/20260827_refresh-change-detection-plan.md](docs/plans/20260827_refresh-change-detection-plan.md) | Phases 0-3 and 5 landed; Phase 4 (optional) not started |
| [docs/plans/20260828_favorites-durable-storage-plan.md](docs/plans/20260828_favorites-durable-storage-plan.md) | **Complete** - all four phases landed |
| [docs/plans/20260828_secret-store-migration-plan.md](docs/plans/20260828_secret-store-migration-plan.md) | Not started, deferred deliberately |
| [docs/plans/20260828_watch-state-durable-storage-plan.md](docs/plans/20260828_watch-state-durable-storage-plan.md) | **Complete** - all six phases landed; kept, see below |
| [docs/plans/20260829_mobile-ui-polish-plan.md](docs/plans/20260829_mobile-ui-polish-plan.md) | **Complete** (2026-08-29) |
| [docs/plans/20260829_ui-look-feel-uplift-plan.md](docs/plans/20260829_ui-look-feel-uplift-plan.md) | **Complete** - all four phases landed (2026-08-29) |
| [docs/plans/20260902_tv-detail-hero-ui-plan.md](docs/plans/20260902_tv-detail-hero-ui-plan.md) | **Complete** - all five phases landed (2026-09-11) |
| [docs/plans/20260908_episode-selection-fragility-plan.md](docs/plans/20260908_episode-selection-fragility-plan.md) | **Complete** - all three phases landed (2026-09-08) |
| [docs/plans/20260911_provider-copy-duplicate-plan.md](docs/plans/20260911_provider-copy-duplicate-plan.md) | **Landed** (2026-09-11); planned unit tests not written |
| [docs/plans/20260912_adversarial-codebase-remediation-plan.md](docs/plans/20260912_adversarial-codebase-remediation-plan.md) | **Resolved** - applied, rejected or deferred per finding (U2 spacing literals and C3 timeout guard deferred) |
| [docs/plans/20260912_adversarial-codebase-review-round2-plan.md](docs/plans/20260912_adversarial-codebase-review-round2-plan.md) | Phases 1-3 landed (2026-09-12); Phase 4 single-return cleanup not started |
| [docs/plans/20260914_codebase-robustness-plan.md](docs/plans/20260914_codebase-robustness-plan.md) | Phase 1 landed (2026-09-29); Phases 2-5 not started; secret-store part of Phase 6 deferred |
| [docs/plans/20260918_concurrency-memory-stability-plan.md](docs/plans/20260918_concurrency-memory-stability-plan.md) | **Complete** - all five phases landed (2026-09-18) |
| [docs/plans/20260918_concurrency-memory-stability-round2-plan.md](docs/plans/20260918_concurrency-memory-stability-round2-plan.md) | **Complete** - all four phases landed (2026-09-18); hardware/unit-test verification outstanding |
| [docs/plans/20260918_systemic-concurrency-memory-stability-plan.md](docs/plans/20260918_systemic-concurrency-memory-stability-plan.md) | **Mostly complete** - Phases 1-3 and 3/4 of Phase 4 landed (2026-09-18); `LiveTvSplitLayout` early-return item deliberately skipped, hardware/unit-test verification outstanding |
| [docs/plans/20260919_systemic-concurrency-memory-deep-dive-plan.md](docs/plans/20260919_systemic-concurrency-memory-deep-dive-plan.md) | **Complete** - all five phases landed (2026-09-21) |
| [docs/plans/20260920_xtream-concurrency-fixes-plan.md](docs/plans/20260920_xtream-concurrency-fixes-plan.md) | **Complete** - both phases landed (2026-09-21) |
| [docs/plans/20260921_adversarial-review-findings-plan.md](docs/plans/20260921_adversarial-review-findings-plan.md) | **Complete** - all five phases landed (2026-09-22) |
| [docs/plans/20260922_codebase-stability-resilience-plan.md](docs/plans/20260922_codebase-stability-resilience-plan.md) | **Complete** - all findings landed except intentionally out-of-scope ones (2026-09-23) |
| [docs/plans/20260923_ui-ux-transitions-flow-uplift-plan.md](docs/plans/20260923_ui-ux-transitions-flow-uplift-plan.md) | In Progress (Phases 1-5 complete, Phase 6 item 2a complete 2026-09-23; 6a/2c held back by user) |
| [docs/plans/20260925_epg-add-to-calendar-plan.md](docs/plans/20260925_epg-add-to-calendar-plan.md) | **Complete** (2026-09-25) |
| [docs/plans/20260929_live-sync-plan.md](docs/plans/20260929_live-sync-plan.md) | **Complete** - all nine phases landed (2026-10-01) |
| [docs/plans/20260930_profile-architecture-adversarial-review-plan.md](docs/plans/20260930_profile-architecture-adversarial-review-plan.md) | **Resolved** - findings 3, 5, 6, 7 fixed; rest not defects, by design or deferred (2026-09-30) |
| [docs/plans/20260930_profile-scoped-settings-plan.md](docs/plans/20260930_profile-scoped-settings-plan.md) | **Complete** - filters and dev mode per profile, verified on emulators (2026-09-30) |
| [docs/plans/20261001_fast-profile-switch-plan.md](docs/plans/20261001_fast-profile-switch-plan.md) | **Complete** (2026-10-01) |
| [docs/plans/20261001_rock-solid-stability-resilience-plan.md](docs/plans/20261001_rock-solid-stability-resilience-plan.md) | **Complete** - all six phases landed, verified on emulators (2026-10-02); F-13 deferred, F-02 not reproduced, `XtreamDatabaseUpgradeTest` (instrumented) not yet run |
| [docs/plans/20261001_live-sync-now-playing-plan.md](docs/plans/20261001_live-sync-now-playing-plan.md) | **Complete** - Phases 0-4 landed, verified on emulators (2026-10-01) |
| [docs/plans/20261002_profile-last-provider-plan.md](docs/plans/20261002_profile-last-provider-plan.md) | **Complete** - each profile returns to the provider it last picked (synced), verified on emulators (2026-10-02) |
| [docs/plans/20261002_epg-search-during-refresh-plan.md](docs/plans/20261002_epg-search-during-refresh-plan.md) | **Complete** - all three phases landed (2026-10-02); emulator verification outstanding |
| [docs/plans/20261002_catalog-sync-cache-churn-plan.md](docs/plans/20261002_catalog-sync-cache-churn-plan.md) | **Complete** - all four phases landed (2026-10-02); Phase 1 verified on bears, Phase 4 on jellyxtream |
| [docs/plans/20261002_provider-to-source-rename-plan.md](docs/plans/20261002_provider-to-source-rename-plan.md) | **Complete** - both phases landed (2026-10-02); French and Malagasy reviewed as text only |

Source comments cite plans by path and phase (`// Phase 6, docs/plans/20260828_watch-state-durable-storage-plan.md`), so **moving or renaming a plan means updating every reference** - the watch-state plan is cited from 24 source files, tv-ui-performance from 2, secret-store-migration from 3.

**A complete plan is not automatically deletable.** `23d2ced3` set the precedent of dropping finished plans rather than archiving them, and `docs/RELEASE_NOTES.md` is the durable record of what shipped. But a plan that source comments cite is load-bearing documentation: the comments say *which* phase a piece of code implements and the plan says *why* that design was chosen, so deleting it turns those references into dead paths and strands the reasoning.

So before deleting a finished plan, grep for citations of its filename. If any exist, keep the file - the watch-state plan is complete and deliberately retained on exactly these grounds. Prune only plans nothing cites.

A complete plan may also still carry live information. The watch-state plan's "Known adjacent problems, deliberately out of scope" section records four defects found while building it and consciously not fixed. Three were resolved on 2026-08-28 - the `getSeriesWatchProgress()` TMDB dedup gap, `XtreamUserDataManager`'s parallel blob, and the duplicated `last_*` navigation keys that went with it - and are struck through rather than deleted, so the reasoning that deferred them stays legible. All four are now closed - Favorites carried the identical truncation defect and were ported to a `favorite_state` table on 2026-08-28. The last capped blob left anywhere is `recent_categories_<contentType>` (20 entries), kept deliberately: it is a convenience list nobody curates, so eviction is the intended behaviour there rather than data loss.

When asked to produce a plan, write the real file under `docs/plans/` - not only an ephemeral plan-mode scratch file.

---

## Performance & Bug Journal

Hard-won lessons from production debugging. Read these before making changes in related areas.

### SharedPreferences JSON deserialization is the #1 hotspot
**Context:** `getFavoriteCategoryItems()`, `getFavoriteItems()`, and `getFavoriteShowItems()` all deserialize JSON from SharedPreferences on every call with no in-memory cache. Called per-chip inside `LazyRow items {}` — 500+ deserializations per recompose for large providers.
**Fix:** Apply in-memory cache + dirty-flag + debounced-write pattern (same as `cachedWatchHistory`).

### Watch history lookups were O(n*m) in refreshPerItemData
**Context:** `MediaRepository.getPlaybackPosition()` did a linear scan of the watch-history blob per call, and `refreshPerItemData()` called it in a loop over every stream.
**Fix (landed):** `getPlaybackPositions(contentType)` now issues one indexed `watch_state` query and returns a Map for O(1) lookups. The lesson stands: always check for linear scans inside loops when profiling data layers.

### contentHash self-referential hash bug in XtreamContentManager
**Context:** `hashCode()` on a data class that includes the `contentHash` field (defaulting to 0). The stored entity has a non-zero `contentHash`, so `hashCode()` never matches — causing spurious DB re-inserts on every sync.
**Fix:** Exclude the hash field itself from `hashCode()` computation.

### Every catalog sync rewrote most rows and wiped their detail cache
**Context:** Xtream's `num` is a position in the provider's list; it shifts for tens of thousands of rows whenever the provider adds one (38,888 of 47,513 series between two bears syncs 3h apart). It was in `contentHash`, and a changed row is rewritten with `@Insert(REPLACE)` from a fresh entity, which reset `episodesFetchedAt`, `detailFetchedAt`, `contentRating`, `posterPath` and `containerExtension`.
**Fix:** `computeHash` leaves `num` out; a stream whose position alone moved gets `updateNums` (lists are ordered by `num`); changed rows go through `insertKeepingDetailCache`, which carries the detail columns over (a changed series still clears `episodesFetchedAt`, the "fetch its episodes again" trigger). **Rule:** a catalog hash covers content only, never list position; never write a catalog row with a bare `insertAll` from sync code. See `docs/plans/20261002_catalog-sync-cache-churn-plan.md`.

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

### FTS count crossed with an indexed IN-list took seconds per query
**Context:** Search showed "Loading categories…" (the default `UiState.Loading()` label) for ~45 s for "the" on a 250k-row Xtream provider. Not the category load and not the search: `countExcludedByFts` (`s.rowid IN (fts MATCH) AND s.categoryId IN (excluded categories)`) let SQLite pick the `(providerId, type, categoryId)` index and probe every excluded category × every FTS docid. Host test DB: 4.95 s (VOD) + 5.6 s (Live) for "the*" vs ~20 ms for the LIMIT 200 search itself.
**Fix:** `+s.categoryId` (unary plus disables that index) so the FTS docids drive the lookup: 0.05 s / 0.02 s, same counts. **Rule:** when a query combines an FTS `rowid IN (… MATCH …)` with another `IN` list on an indexed column, run `EXPLAIN QUERY PLAN` on a full-size catalogue with a short common prefix before shipping.
