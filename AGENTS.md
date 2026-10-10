# AGENTS.md - AI Agent Guide for Fijerena

This is the single source of truth for AI agents working on this codebase. All LLM tools (Claude, Gemini, Codex, Copilot, Cursor, Jules, etc.) should read this file. The vendor entry points (`CLAUDE.md`, `GEMINI.md`, `CODEX.md`, `.cursorrules`, `.github/copilot-instructions.md`) all redirect here; `.jules/bolt.md` is Jules' own performance journal.

---

## Project Overview

Fijerena is a native Android media player built with Kotlin and Jetpack Compose. It plays content from several kinds of source (Xtream IPTV, Jellyfin, Remote M3U, and in developer mode SMB and Local files) with one app for TV (10-foot UI, D-pad) and one for mobile (touch, portrait-locked outside the player).

**Target Devices:** NVIDIA Shield, Chromecast with Google TV, Sony Bravia (Android TV), and Android phones/tablets. `minSdk` 30 (Android 11), `targetSdk` 35, `compileSdk` 36.

**App Icon:** Blue Marble (Earth) with red/cyan 3D glasses. Adaptive icon (foreground PNGs in `mobile/src/main/res/drawable-*/ic_launcher_foreground.png`, black background XML) + legacy webp mipmaps.

---

## Tech Stack

`gradle/libs.versions.toml` is authoritative; `README.md` has the summary table. What the versions imply:

- Kotlin 2.3.0 compiler/plugin (the stdlib resolves to 2.4.0 through dependencies), AGP 9.4.1, Gradle 9.6.0, Java 21.
- Compose BOM 2026.03.01 (ui/foundation/runtime/animation 1.10.6). Compose 1.11+ needs `compileSdk` 37.
- Material 3 is pinned `strictly` 1.4.0 so the compile and runtime classpaths agree.
- `androidx.tv:tv-material` 1.0.0-alpha10 is used; `tv-foundation` only arrives as its transitive dependency. Its `TvLazyColumn`/`TvLazyRow` crash on Compose 1.9+ (see Focus below).
- Room 2.8.4 on a bundled requery SQLite 3.49.0 (FTS5 capable; the app's indexes use FTS4). Media3 1.7.1, Ktor 3.5.2 (OkHttp engine), Coil 3.5.0, Navigation Compose 2.8.5, smbj 0.15.0, WorkManager 2.11.2.
- Theming: `CinemaThemeHolder` + `CinemaThemePalette` (colour); `UiStyleHolder` + `UiStyle` (look and feel).

---

## Module Architecture

```
fijerena/
├── mobile/          # Phone app, touch UI
├── tv/              # TV app, 10-foot UI, D-pad
├── core/
│   ├── player/      # Media3, playback service, domain models, diagnostics (CrashLog, SafeMode, AppScopes)
│   ├── network/     # Source implementations, API clients, EPG pipeline, Room DBs, live sync client
│   ├── navigation/  # Type-safe Screen definitions (shared)
│   ├── ui/          # Shared ViewModels, design tokens, components, AppContainer
│   └── data/        # AuthViewModel only
├── server/          # Live sync server (TypeScript; Cloudflare Worker or workerd in Docker) — see server/README.md
├── scripts/         # Build, deploy, backup/restore, CI checks, TV focus walks
├── tools/           # jellyfin-xtream test bridge
├── docs/            # Technical documentation (see below)
└── gradle/          # Version catalog (libs.versions.toml)
```

### Critical Architectural Constraints

1. **No Circular Dependencies:** `core:player` **must not** depend on `core:network`. If the player needs network settings, it reads directly from `SharedPreferences`.
2. **Unified Domain:** All provider-specific data must be mapped to unified domain models in `core:player/domain/` before reaching the UI.
3. **String IDs:** All media and category IDs must be `String` (not `Int`) to support diverse provider formats (UUIDs, paths, numeric IDs).
4. **Dependency Injection:** Always use `AppContainer` (in `core:ui`) to obtain repository singletons (`MediaRepository`, `ProviderRepository`). Never manually instantiate repositories in ViewModels.
5. **Async Initialization:** ViewModels must initialize repository dependencies asynchronously to prevent UI thread blocking during screen composition.
6. **Long-lived scopes:** Any scope that outlives a screen (singletons, services, repositories) comes from `AppScopes.create(name, dispatcher)` (`core:player/diagnostics`), never a bare `CoroutineScope(...)` — an exception escaping a bare scope kills the process. `AppScopes` logs it and records it in `CrashLog`.
7. **Don't swallow cancellation:** In suspend code, a `catch (e: Exception)` needs a `catch (e: CancellationException) { throw e }` before it, and `runCatching` around a suspend call is `suspendRunCatching` (`core:network`) — otherwise a cancelled job carries on and publishes stale state. `scripts/check-cancellation.sh` (CI) enforces it; mark a deliberate or non-suspend case with `// cancellation-ok: <reason>` on the catch line or the line after it (where ktlint moves it); its file allow-list (legacy files) only shrinks.
8. **Service from Compose:** In a `LaunchedEffect`/composition-scoped coroutine use `StreamingPlaybackService.awaitInstanceOrNull()`, never bare `awaitInstance()` — its `ServiceDestroyedException` escaping a composition coroutine crashes the app.
9. **`xtream_v2.db` holds user data:** every version bump needs a real `Migration`; the destructive fallback covers only pre-v7 files. See `docs/DATABASE_SCHEMA.md` §3 and "Schema changes" below.
10. **Secrets never fall back to plaintext:** an encrypted store that can't be opened is reset (`CredentialStoreHealth.markLost`) and, if it still can't be created, replaced by `CredentialStoreHealth.InMemoryPrefs` — never `getSharedPreferences`.
11. **Startup work that could crash sits behind `SafeMode.isActive`** (`core:player/diagnostics`): `FijerenaApplication.startBackgroundWork()` and the nav hosts' `initializeStartup()` don't run in crash-loop safe mode, nor when `ProvidersDbGuard.isBlocked` (a `providers.db` from a newer build, which nothing may open). New startup work goes inside them, not around them. See `docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md` → R-10.
12. **No destructive self-healing:** code that runs by itself (startup, workers, sync) never deletes user data (`watch_state`, `favorite_state`, `sync_*`, providers, profiles, credentials) by inference. Only an explicit user action or a received tombstone may. See `docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md` → R-02.
13. **ViewModel coroutines use `launchGuarded`** (`core:ui/utils/LaunchGuarded.kt`), never a bare `viewModelScope.launch`: it rethrows cancellation, records the failure in `CrashLog` and passes it to `onError`, which sets the screen's error state. An init that builds a repository surfaces its failure and is retryable — never a crash or a deferred left hanging. `scripts/check-viewmodel-launch.sh` (CI) enforces it; mark a deliberate bare launch `// launch-ok: <reason>`; its per-file allow-list only shrinks. Values received from live sync (`AppSettings.applyRemoteSetting`) are validated before they're stored.

---

## UI & Coding Standards

### 1. STRICT: No Hardcoded UI Values

Every color, dimension, spacing, and animation duration **must** come from design token constants. Never use raw literals like `16.dp` or `Color.White`.

- **Shared Tokens (core/ui):** the `Cinema*` color values in `CinemaColors.kt` (`CinemaAccent`, `CinemaSurface`, … read from `CinemaThemeHolder`), `CinemaAlpha`, `CinemaAnimation`, `CinemaCornerRadius`, `CinemaSpacing`; look-and-feel tokens from `LocalUiStyle` (`UiStyle.kt`).
- **TV Tokens:** `TvDimensions`, `TvFocusTokens`.
- **Mobile Tokens:** `MobileDimensions`.
- **Platform re-exports:** TV `CinemaColors.kt`/`Spacing.kt`, mobile `Color.kt`/`Spacing.kt`.
- **Colors:** Prefer `MaterialTheme.colorScheme.*` or platform re-exports.

### 2. D-Pad & Focus (TV)

Every interactive `@Composable` must be D-pad navigable, and every TV screen follows the focus contract (UX overhaul plan, Part I Part B and Part II; the per-screen behaviour is in `docs/NAVIGATION_GUIDE.md` → "TV Navigation"):

1. **One focus stop per row**; OK does the row's one job. Rows that need several visible actions (Settings' Sources, guide sources) use labelled trailing buttons in fixed-width slots, so Up/Down keep the column.
2. **No 2-D grids in Settings**; options are vertical lists.
3. **Up/Down never skip content:** a block that shows information is focusable or sits inside a focusable row.
4. **Left goes back one level** (pane → rail, picker → row); Right only reaches a row's trailing slots. Exception: a tabbed panel (the Live TV channel panel) uses Left/Right for its tabs, and Back leaves it.
5. **Entry focus is the useful item:** the current value in a picker, the active source on Sources, the first action (never Cancel) in a menu.
6. **Back restores the exact control** that opened the screen, at the same scroll position.
7. **Destructive actions are never the first or default focus**, and always confirm.

The primitives (all in `tv/.../ui/components/input/`):

- **Land focus with `requestFocusWithRetry`** (`FocusRetry.kt`) from a `LaunchedEffect`, never `try { requestFocus() } catch (IllegalStateException)` or `runCatching`: since Compose 1.10 an unattached requester returns `false` instead of throwing, so those catches retry nothing. Act on its Boolean result and pass a `fallback` when there is an obvious second target. `scripts/check-focus-retry.sh` (CI) enforces it.
- **Back returns focus (`NavReturnFocus.kt`):** every control that navigates to another destination records itself — `returnFocus.leaveFrom(key, listState)` before navigating, `Modifier.navReturnFocusTarget(returnFocus, key)` on the control, one `NavReturnFocusEffect` per screen (`val returnFocus = rememberNavReturnFocus()`). A screen's own first-open focus effect skips while `returnFocus.key != null` (or `isReturn`).
- **Two-column screens use `tvPane`** (`TvPane.kt`): one `rememberPaneFocus()` per column, `pane.bind(...)` in the list, `Modifier.tvPane(pane, exitLeft/exitRight = neighbour)` on the column, `Modifier.paneItem(pane, key)` on each row. Never leave a column switch to Compose's geometric search, and don't add `focusRestorer` to a pane (its `onEnter` overrides the pane's) — see `docs/NAVIGATION_GUIDE.md` → "Panes (`tvPane`)".
- **Row actions:** a content row (channel, title or category in the lists, the Live TV panel's rows, a TV Guide cell) is one focus stop; **long-press OK or the Menu key** opens its action menu (`FavoriteContextMenuDialog`), and a non-focusable "⋮" on the focused row is the hint; Home's cards too. In a Recent / Favorites list the menu opens on its removal (`RowMenuList`). A removal is immediate, no confirmation: focus goes to the row that took its place (never nothing — `rowAfterChange` / `cardAfterChange`), and `TvUndoBar` offers Undo on the Menu key for 10 s, in its own room under the list. No hidden trailing ★ / ✓ / 🗑 buttons on content rows. Settings' Sources rows keep their visible buttons. See `docs/NAVIGATION_GUIDE.md` → "Row actions".
- **TV text fields reached by D-pad are `TvSearchField`** (`TvSearchField.kt`): an ordinary focus stop at rest, OK opens the keyboard, Back closes it (`onInterceptKeyBeforeSoftKeyboard`). A bare focused text field would take every D-pad key.
- **One "current / selected" style:** `Modifier.currentIndicator(active, …)` (`TvInputDefaults.kt`) — an accent bar on the leading edge (bottom edge for tabs, `CurrentIndicatorEdge.Bottom`) plus `TvFocusTokens.currentText` for the title. No tint or outline on a selected row, so it never looks like a second focused one; a focused current row keeps the bar.
- **Focus look** comes from `TvFocusTokens` and the active look and feel (`LocalUiStyle.current.grid`: scale 1.0-1.09, outline at least `minFocusBorderWidth` 2dp, since the Roku style scales by 1.0 and relies on the outline alone). See `FocusModifiers.kt`. Avoid complex animations on mid-range TV chipsets (e.g., Sony Bravia).
- **Action buttons are icons** (`TvIconAction`; mobile `IconAction`): the label shows only while focused on TV, in a long-press tooltip on mobile, and is the content description. Text stays for dialog buttons, a screen's single main action and form submits (Play, Save, Add, Retry), and setting value rows. Never turn icons into text buttons without the user's decision. See `docs/plans/archive/20261005_icon-buttons-plan.md`.
- **Lists are plain `LazyColumn`/`LazyRow`.** Never `TvLazyColumn`/`TvLazyRow`: `tv-foundation` 1.0.0-alpha10 calls a prefetch API removed in Compose 1.9 and crashes on scroll.
- **Back on TV:** where a focused `Button`/`Surface` exists, intercept Back in `onPreviewKeyEvent` on the root — `BackHandler` misses the first press. See `docs/NAVIGATION_GUIDE.md` → "TV Back on Detail Screens".
- **In-tree overlays trap focus:** a picker drawn in the screen's own layout (not a `Dialog` window) wraps its content in `.focusProperties { onExit = { cancelFocusChange() } }.focusGroup()` (focusProperties directly before focusGroup) and refocuses its opener on close. Don't trap overlays that animate out while focused. `onExit = { FocusRequester.Cancel }` does nothing.
- **Error states:** TV uses `TvErrorState` (focus on Retry, auto-retry when back online) for real failures only; an empty result (no channels, no guide sources, an empty category) is `TvEmptyState` (centred sentence, optional icon and one action). Mobile error branches call `RetryWhenOnline` (`core:ui`).
- **Shared TV pieces (TV UI audit, `docs/plans/archive/20261007_tv-ui-audit-plan.md`):** a screen's header is `TvScreenHeader` (title, short subtitle, icon actions right); content list rows take their colours and focus look from `TvListRowDefaults` (light lift, white outline, text stays white; accent only for the current bar), settings / input rows from `TvInputDefaults` (same look); channel logos go through `CinemaThumbnail` with `ThumbnailContentType.LIVE_TV` (drawn whole, never cropped); provider separator rows (`#### … ####`) are non-focusable headings, never played, zapped to or counted. Text & grid size scales the whole app through `LocalDensity` at the root — don't scale sizes per screen (`.scaled()` is a no-op kept for old code).
- **Focus walks:** a change to a screen's focus order updates its walk in `scripts/focus-walks/` in the same commit (re-record with `scripts/tv-focus-walk.sh -r`; see `docs/RUN_GUIDE.md` → "Focus walks").

### 3. Safe Margins (TV Overscan)

Apply TV-safe margins to all root containers (56dp horizontal / 32dp vertical):
- `Spacing.tvSafeMarginHorizontal`, `Spacing.tvSafeMarginVertical`.
- UI should remain 5% away from screen edges.

### 4. Typography

- TV: 13-style scale (48-14sp) — Manrope for display/headline styles, Roboto (system default) for title/body/label.
- **Rule:** All body text **>=18sp** for TV readability.
- TV UI scale (40/60/80/100 %, default 80 %; `AppSettings.uiScale`) is applied globally via `LocalDensity` in TV `MainActivity.kt`.

### 5. Strings and titles

- **Strings:** every user-visible string is a resource in a `core` module (`core/ui`, `core/network` or `core/player`, under `src/main/res/`), added to `values/`, `values-fr/` and `values-mg/` in the same change — the three files of each module carry the same keys. `tv` and `mobile` hold only `app_name`.
- **Wording:** in the UI content providers are **sources** ("Add Source", "Manage Sources"; French *source*, feminine; Malagasy *loharano*), and EPG/XMLTV feeds are **guide sources** (*source de guide*, *loharanon'ny fitarihana*). Code keeps "provider" (`ProviderEntity`, the `providers` table, `providerId`, string keys such as `provider_add_title`), so new strings use the new words with the old key style. "Provider" stays only where it means the company that sells the IPTV service (`login_footer_text`). See `docs/plans/archive/20261002_provider-to-source-rename-plan.md`.
- **Provider titles:** raw names carry tags (`EN - …`, `4K-NF - …`, `AFR: …`, `… (US)`). Show them through `BadgedTitle` (`core:ui`, `LanguageBadge.kt`) or `parseDisplayTitle` (`core:player/domain/TitleLanguage.kt`): the tag becomes a `LanguageBadge` beside the clean title. Episode names go through `episodeOwnTitle` / `playerEpisodeName` / `episodeTitleWithoutSeries` in the same file. Stats for Nerds shows the raw names on purpose.

### 6. Coding Style

- **Single return statement** per function only.
- **OS:** Ubuntu Linux development environment.
- **Lint:** `./gradlew ktlintCheck` verifies style, `./gradlew ktlintFormat` fixes it. ktlint-gradle 14.2.0 with ktlint 1.8.0 (ktlint-gradle older than 14.1 lints nothing under AGP 9). Rules are in `.editorconfig`.

---

## Player Implementation

### Configuration & Source

- **Source Creation:** Always use `StreamingMediaSourceFactory.createMediaSource()`.
- **Formats:** HLS (`.m3u8`), DASH (`.mpd`), MPEG-TS (`.ts`, `.mpeg`).
- **Buffer Strategy:** `AdaptiveLoadControl` dynamically swaps buffer profiles (Live TV vs VOD, WiFi vs Cellular) at runtime, from the constants in `NetworkBufferProfile` (cellular uses the profile sizes as they are; there is no user multiplier any more). VOD keeps no back buffer (ExoPlayer counts it against the byte cap; on 4K it starved the forward buffer) and its byte cap scales with the heap (`NetworkBufferProfile.vodTargetBufferBytes`, 64-160 MB).
- **Codec Priority:** Optimized per device (`DeviceCapabilities`: Shield and Chromecast with Google TV: AV1 -> HEVC -> AVC; Sony: HEVC -> AVC; others: AVC).

### Controls & Navigation

- **State Management:** `PlaybackViewModel` delegates to `StreamingPlaybackService` (a `MediaSessionService`).
- **OK / Center Key:** **Shows controls only** — it never pauses or resumes playback. On TV it opens the OSD (`TvPlayerControlsOverlay`): one row of icon buttons, each naming itself while focused, focus on Channels on live, Play/Pause on VOD.
- **Double-OK:** Dismisses the stats overlay if visible.
- **Pause:** Explicit via pause button or `KEYCODE_MEDIA_PLAY_PAUSE`. Mobile double-tap seeks, it doesn't pause — see Mobile Gestures below.
- **Seeking / Navigation:**
  - **VOD:** Use `PlaybackViewModel.seekRelative(offsetMs)` for relative position changes (FF/Rewind).
  - **TV VOD:** with controls hidden, D-pad Left/Right and REW/FF move a scrub cursor (`stepScrubCursor`, `scrubStepMs`); OK commits, Back cancels.
  - **TV Shows:** a Next episode button appears in the player controls (TV and mobile) once 80 % of the episode has played, and the "Up next" card offers the next episode at the end. There is no Previous episode button.
- **Channel panel (Live TV):** TV: one `LiveTvChannelPanel` — docked in the preview, and over the video in full screen where D-pad Left or Right (or the OSD's Channels) opens it (tabs Category · Recent · Favorites, focus on the playing channel, Back closes it); the panel's list is the zap order (`ChannelContext`). Mobile: a horizontal swipe opens `MobileChannelListSheet` (category one way, Last Watched the other).
- **Live TV is one nav entry (LT7):** on TV, the Live TV section (`Screen.LiveTvTab`, from the rail) is one entry hosting browse with `showPreviewPane = false`. The preview (`LiveTvSplitLayout`) and its in-place full screen are layers over browse inside that entry, open while `livePreviewChannelId` (saveable) is set — never push a nav entry for them. Back steps full screen → preview → browse → the rail → Home. Entries that open on one channel (Search, EPG Browser, TV Guide) push their own `CategoryList` with `showPreviewPane = true` (preview only, Back returns to the opener). Mobile has one `CategoryList` entry too; its dock and full screen are local state (`dockTarget`, `fullScreen`). Both promote to full screen on the same engine connection (no restart), and Back always has a real stopover before leaving Live TV — see `docs/NAVIGATION_GUIDE.md` → "Live TV Preview / Dock Back-Stack".
- **Mobile Gestures:** `detectTapGestures` (tap=controls; double-tap=10s relative seek, VOD only — left 40% of the width rewinds, right 40% seeks forward, center 20% does nothing). Merged `detectDragGestures` (vertical=channel switch, horizontal=channel sheets).

### Features

- **Stats Overlay:** Opened from Stats in the player controls (on TV behind ⋮ More); double-OK or Back dismisses it. Comprehensive diagnostics (codecs, network speed, dropped frames, build info, the provider's raw names). Non-focusable on TV, so the remote keeps controlling playback.
- **Stream Info:** the resolution/codec line under the title; on TV only in developer mode (`PlayerScreenState.isDeveloperMode`).
- **Auto-resume:** position saved every 10 s while playing; VOD resumes if progress is 2-95%.
- **Position saves:** `StreamingPlaybackService.positionSaves` (process-wide `SharedFlow<PositionSave>`, no replay) carries every periodic, state-change, track-choice and teardown save; the player screen collects it in a composition-scoped `LaunchedEffect` and calls `loaderViewModel.recordHistory`. Never attach a callback to a service instance: it is destroyed and recreated (TV `onStop`, cold start). `releasePlayerAndSession` runs each teardown stage in its own `releaseStage`; keep new stages inside one and the singleton reset last.
- **Network deadlines:** a new request/response API call gets its client's overall deadline (Xtream metadata, Jellyfin, sync 60 s; TMDB 30 s); a bulk download opts out (`timeout { requestTimeoutMillis = INFINITE_TIMEOUT_MS }`). The player's streaming clients, EPG downloads and the sync WebSocket never get a call timeout.
- **Shared logins:** an Xtream playback URL goes through `XtreamLoginPicker.forPlayback` (`XtreamMediaProvider.resolvePlayableStream` does it), so it may carry an extra login, and a refusal can move it to another (`AlternateLogin`). Compare stream URLs with `XtreamStreamUrl.sameStream`, never `==`. See `docs/plans/20261005_shared-logins-plan.md`.
- **Logging secrets:** anything that logs a URL or exception text that may hold credentials goes through `Redact.text` (`core:player/diagnostics`); `CrashLog.record` already redacts.
- **Provider changes:** a change to a provider's settings, URL or login goes through `MediaProviderFactory.providerChanged(id)` (in the app) or `SyncEngine.Listener.onProvidersChanged` (from sync), which `AppContainer.onProvidersChanged` turns into dropping the cached repository and provider together. Never call `MediaProviderFactory.clearCache` alone for a config change: the cached `MediaRepository` keeps the old, disconnected provider.
- **Watch History Rules:**
  - **Live TV:** Added to history after `watchDelaySeconds` of continuous playback (default **10 seconds**, 5-120, Settings → Playback).
  - **VOD (Movies/Series):** Added to history only after reaching a **2% watch threshold**.
  - **Session Finalization:** `loaderViewModel.stopPlayback()` MUST be called when exiting the player or switching streams to ensure final progress is reported and history is flushed to disk.
- **Watch State Storage:** Position and completion live in the durable `watch_state` Room table (`xtream_v2.db`, added in v15), **not** in SharedPreferences — the old `watch_history_v3` blob truncated to `watchHistorySize` on every write and is now migrated and purged on first `setProvider()`. `watchHistorySize` still bounds the *Recent* row's length, never what is stored. Reads go through `MediaRepository`; never re-introduce a blob writer.
  - **Completion is sticky:** progress upserts do `MAX(existing, new)` on `isCompleted`. Only `setWatched(false)` clears it.
  - **Manual marking:** `MediaRepository.setWatched(itemId, contentType, watched)`. No-ops for server-backed providers (Jellyfin owns that state). A manual mark leaves `lastPlayedAt` null so it never pollutes the Recent row.
  - **TMDB dedup:** movies join `xtream_streams` on shared `tmdbId`; episodes join `xtream_series` on **series-level** `tmdbId`, then match `(season, episodeNum)` — episode-level `tmdbId` is effectively never populated by providers and must not be used for this.
  - **Track prefs:** `audioTrackIndex`/`subtitleTrackIndex` persist per row, with a series-level fallback so a new episode inherits the last choice made in that series.

---

## EPG & Indexing System

- **Pipeline:** `EpgFileManager` manages multi-source XMLTV ingestion using a Channel-based producer-consumer architecture. Downloads run concurrently (Semaphore-gated: 3 on mobile, 2 on TV), and ingestion into the DB is parallelized (2 workers) via an `UNLIMITED` Channel queue. User-initiated refreshes and the on-connect `refreshOutdatedSources` are submitted through `RefreshQueue`; `EpgSyncWorker` (the periodic auto-refresh) calls `processAllSources` → `processAllSourcesInternal` directly so the work stays under the worker's wake lock on Shield/Doze.
- **Stale Threshold:** per guide source, half its own `epg_source.refresh_interval_hours` (`EpgRefreshSchedule.staleAfterMs`; null = not set uses the retired device-wide interval). `EpgSyncWorker` runs at the shortest interval among enabled sources and refreshes the active provider's sources older than that; an off source (-1) only while never ingested, and counts as stale after 24h for Refresh stale. See `docs/epg_guide.md` → Refresh scheduling.
- **Indexing:** `EpgIndexer` parses XMLTV into `epg_index.db` (Room, version 17) using FTS4. The `ingest_method` column tracks how each source was ingested.
- **Search:** Two-tier strategy, both via SQLite **FTS4 MATCH** in `XmltvSearchService`: a raw query preserving FTS operators (OR/NEAR/NOT, prefix wildcard), then a sanitized "safe" AND-style retry if the raw query returns nothing. If the index isn't built yet (`EpgIndexState.NotIndexed`), search returns null. If the FTS index is stale (`isFtsStale()` — a low-storage direct-path refresh, or an interrupted rebuild), search falls back to a title-only `LIKE` scan of `epg_programme.title_lowercase` (`EpgSearchPath.LIKE_FALLBACK`, same window/500 cap/10 s timeout; the screens note results may be incomplete); only if that times out or fails does it throw `EpgIndexBusyException`. `EpgBrowserViewModel` shows `UiState.IndexBusy` (why, not "no results") and reruns a busy or `LIKE_FALLBACK` query when `EpgIndexer.state` reaches `Indexed` — so `rebuildFtsAndUpdateState()` clears the stale flag *before* publishing `Indexed`. The staging path never goes stale: `EpgIndexer.swapAndRebuildFts()` does the staging→primary swap and the FTS `'rebuild'` in one transaction, so WAL readers never see new rows with the old FTS — keep them together. No XML-scan fallback exists.
- **Timezone:** Per-source `timezoneOffsetHours` override applied at parse time.
- **State Machine:** `MultiSourceState` sealed interface: `Idle` -> `Processing` -> `Completed`/`Error`, plus `Clearing` state. Per-source progress tracked via `ActiveSourceProgress(label, phase, channels, programmes)`.
- **Change Detection:** `downloadSource` sends `If-None-Match`/`If-Modified-Since` from the source's stored `etag`/`last_modified_header`; a `304` short-circuits with no body read. Otherwise a SHA-256 of the payload is compared to `last_content_sha256` (computed during the download pass for plain sources, after decompression for `.gz` — gzip's mtime header taints the raw bytes). A confirmed-unchanged source skips `ingestFromStream` entirely, is excluded from `swapAndRebuildFts`'s id list (its staging table was never populated), and carries its previous counts forward via `EpgSourceDao.markUnchanged`. A hash match only skips within 24h of the last real ingest — ingestion drops programmes that ended more than 12h ago (wall-clock), so a byte-identical file is still re-ingested daily or long-ended programmes pile up. There is no future limit on ingest: everything ahead the source provides is kept.
- **Download Integrity:** `read()` returning -1 alone can't distinguish a clean EOF from a cut connection. `totalRead` is checked against `Content-Length` when the server sends one; a mismatch is a download error and takes the normal retry path.
- **Persistent Stats:** Pipeline completion triggers an update to `EpgPipelineStatsEntity` in `providers.db` (`SettingsDatabase`, version 16).
- **Clear All Data:** Uses DB `destroy()` + `getInstance()` (recreate) instead of `DELETE FROM` — critical for performance on large databases (4M+ rows). Cancel in-flight work via `RefreshQueue.cancelAll()`.
- **ViewModel Resilience:** `EpgManagementViewModel` uses a `db()` function (always calls `EpgIndexDatabase.getInstance()`) and `_dbGeneration` StateFlow counter. After DB destroy/recreate, bumping the generation causes `flatMapLatest` to re-subscribe all Room Flows to the new DB instance.
- **Management:** Multi-source EPG management in `Screen.EpgManagement`.

---

## App Navigation & Features

### Flow

1. **Startup:** `NewerData` / `SafeMode` first when they apply; otherwise, if a provider is configured, TV's home screen (`ContentTypeSelection`, through `ProfilePicker` when there is more than one profile) and on the phone the last tab used (no Home: a bottom bar of section tabs, Back-Stack Rule 7) — else Settings. See `docs/NAVIGATION_GUIDE.md` → "Navigation Rules".
2. **Selection:** Content Type -> Category Grid -> Details (VOD) -> Player.
3. **Navigation IDs:** Always use `String` for IDs.
4. **TV navigation rail:** every TV screen's leftmost focusable items take `Modifier.leftToRail()` (`tv/.../ui/components/rail/`): Left opens the rail (profile, Home, sections, Search, Settings), Right returns to the same item. Exceptions: the TV Guide (Left in the grid steps back in time; only the channel column reaches the rail) and screens that call `HideTvNavRail()` (player, Live TV preview, profile picker, Add / Edit Source, safe mode). Sections are saved stacks (`Screen.LiveTvTab` / `MoviesTab` / `TvShowsTab`); clear them with `clearSectionStacks()` on any source / profile switch. See `docs/NAVIGATION_GUIDE.md` → Back-Stack Rule 6.

### Features

- **Search:**
  - **Global Search:** Unified "ALL" search across Live TV, Movies, and TV Shows from the home screen (`ContentTypeSelection`).
  - **Collapsible Groups:** Results grouped by source with collapsible headers (saved via `rememberSaveable`).
  - **Xtream:** Local FTS4 prefix search over the synced catalogue (`xtream_streams_fts` / `xtream_series_fts`), no network call; a second FTS query counts matches hidden by category filters.
  - **Jellyfin:** Server-side search.
- **Virtual Categories:** Favorites (configurable 10-500), Last Watched (1-100), Continue Watching (VOD), Recent Categories.
- **Mark Watched/Unwatched:** Manual toggle on movie details (icon beside the favorite toggle), TV content rows and search (the row action menu, `FavoriteContextMenuDialog`), mobile search (`MobileSearchFavoriteDialog`, second action row), TV episode cards (long-press OK toggles it), and the mobile episode watched badge (itself the tap target). Each surface reuses its existing affordance — do not invent a new one.
- **Sync Feedback:** Provider screens show the last sync's catalog delta ("No changes since last sync", or "N added • N updated • N removed"), gated on Xtream and on `lastSyncError` being null. EPG management shows "Unchanged" in place of durations for a source the last run confirmed unchanged.
- **Jellyfin Quick Connect:** Supported for easy auth.
- **Settings:** profiles, sources and guide sources, live sync, playback, color theme, look and feel (Material, Cupertino, Roku, BRAVIA), language (English, French, Malagasy), UI scale (TV), EPG and cache management, database maintenance ("Shrink Database"), export/import (JSON), developer mode (Diagnostics). On TV, Settings is a rail of groups beside the selected group's rows.
- **Database Maintenance & Compaction:** When providers are deleted, `ProviderRepository.deleteProvider` cascades through all catalog tables in `xtream_v2.db` (`xtream_streams`, `xtream_series`, `xtream_episodes`, `xtream_categories`, `favorite_state`, `xtream_epg_cache`), clears orphaned SharedPreferences (`provider_creds_*`, `media_cache_*`, `xtream_cache_*`), deleting catalogue rows 1,000 per commit; no automatic `VACUUM` (`auto_vacuum = FULL` already returns space, and a WAL-mode `VACUUM` needs room for the whole database — see `docs/DATABASE_SCHEMA.md` §3). A background sweep (`sweepOrphanedCatalogData`) runs during `EpgSyncWorker`, and at app start only after an interrupted deletion (`orphan_sweep_pending`); it removes downloaded catalogue only, never favourites or watch history, and never throws. The "Shrink Database" button in Settings is the only place a `VACUUM` runs, and the only path that removes orphaned credential files and EPG sources. A circuit breaker prevents deletions if the valid provider list is empty.

---

## Multi-Provider Support

| Provider | Live TV | Movies | TV Shows | EPG | Search | Progress Sync |
|----------|---------|--------|----------|-----|--------|---------------|
| **Xtream** | Yes | Yes | Yes | Yes | Client-side (FTS) | No |
| **Jellyfin** | No | Yes | Yes | No | Server-side | Yes |
| **Remote M3U** | Yes | Playlist video entries | No | No | Title match | No |
| **SMB** (dev mode) | No | Yes | No | No | Filename | No |
| **Local** (dev mode) | M3U only | Yes | No | No | Title match | No |

SMB and Local are offered in Add Source only in developer mode (`addSourceTypes`, `core:ui`; SMB has no playback data source yet, Local has no folder picker); an existing one stays editable. Capabilities per type are in each provider's `ProviderCapabilities`.

---

## Development Workflow

### Commands

```bash
./gradlew assembleDebug                 # Build both apps
./gradlew testDebugUnitTest             # Unit tests (what CI runs)
./gradlew ktlintCheck                   # Code style (ktlintFormat fixes it)
./gradlew lintDebug                     # Android Lint (per-module lint-baseline.xml; only new issues fail)
scripts/check-cancellation.sh           # CI gates, run them before committing
scripts/check-viewmodel-launch.sh
scripts/check-focus-retry.sh
scripts/deploy-tv-emulator.sh           # Build + install on the TV emulator
scripts/deploy-mobile-emulator.sh       # Build + install on the phone emulator
scripts/deploy-tv-ip.sh <ip> [<ip>...]  # Build + install on network TVs (playback check, backup)
scripts/deploy-mobile-usb.sh [serial]   # Build + install on a USB phone (backup)
```

CI runs on every push (and by hand). `checks.yml`: the destructive-fallback grep and the three `scripts/check-*.sh` gates. `android-build.yml` (skipped for docs-only, server-only and focus-walk changes): ktlint, unit tests, Android Lint, the Room schema check, `assembleDebug`, then compiles the instrumented tests; APKs are uploaded only from `main` or a manual run. `server.yml` (only when `server/` changes): the sync server's tests. `instrumented-tests.yml` runs nightly (and by hand): `connectedDebugAndroidTest` for `core:network` and `mobile` on a phone emulator, `tv` on an Android TV emulator. Build, deploy and debugging details are in `docs/RUN_GUIDE.md`.

### Deployment Rules

- **Use the deploy scripts**, never hand-run `gradlew` + `adb install`. They build incrementally and install with `install -r`. `deploy-tv-emulator.sh` and `deploy-tv-ip.sh` check `dumpsys media_session` for active playback (`state=PlaybackState {state=3}`) and ask before interrupting it; `deploy-tv-ip.sh` skips a device that doesn't report `tv` in `ro.build.characteristics`. TV and mobile share the `applicationId` `org.njarasoa.fijerena`, so installing the wrong APK silently replaces the other app.
- **NEVER install to real hardware without backing up app data first.** `adb install -r` is *not* guaranteed to preserve app data — a signing-key mismatch (or other cause) can make it install fresh with no warning. On 2026-09-08 a deploy to all three household TVs (2 Shields + 1 Bravia) reported "Success" on every device and had wiped every one (`firstInstallTime == lastUpdateTime`); no backup existed. `deploy-tv-ip.sh` and `deploy-mobile-usb.sh` back up each device with `scripts/backup-app-data.sh` (user data only: `shared_prefs`, `providers.db*`, and the `watch_state`, `favorite_state` and `sync_*` tables of `xtream_v2.db`) into `backups/`, deleting backups older than 7 days, and don't install on a device whose backup failed; `scripts/restore-app-data.sh` puts one back. Any other install to a real device runs `scripts/backup-app-data.sh <serial> <out.tar.gz>` first. Permission to deploy is not permission to skip this.
- **Never uninstall or clear app data** (`adb uninstall`, `pm clear`) to fix a deploy, and ask before uninstalling or clearing data on any device, emulators included, and before installing on a real device. On emulators, installing over the existing app (the deploy scripts, `install -r`) is fine. `scripts/uninstall-app.sh` is the only sanctioned uninstall and asks for confirmation. `./gradlew connectedAndroidTest` uninstalls the app on every connected device: run it only when asked, on one emulator (`ANDROID_SERIAL`).
- **No auto-launch on install:** never launch the app (`am start`, monkey) after installing unless the user asks.
- **Incremental deploys:** the deploy scripts don't run `clean`. A `NoClassDefFoundError` after changes across modules (`core:*` consumed by `:tv`/`:mobile`) is a stale DEX shard: `./gradlew clean`, then deploy again.
- **Finding devices:** network TV IPs drift (DHCP, `192.168.68.0/24`). Run `adb mdns services` first; fall back to `arp -a | grep 192.168.68.` only if it finds nothing. Identify a device by `getprop ro.build.characteristics` (`tv` vs `default`/`nosdcard`) or `product:`/`model:` in `adb devices -l`, never by IP or port — emulator serials follow launch order.

---

## Agent Workflow Rules

1. **Read first.** Start every session by reading this file and the relevant docs in `docs/`.
2. **Verify every UI change:** "Is this D-pad friendly?" and does it follow the focus contract above.
3. **Use design tokens** for every visual attribute; never hardcode dimensions or colors.
4. **Only modify when explicitly instructed.** Do not make speculative changes.
5. **Checks:** run `./gradlew ktlintCheck`, `./gradlew lintDebug` and the `scripts/check-*.sh` gates after changes; a change is not done until a build succeeds.

### Investigation Strategy

When investigating issues:
1. Read this file and relevant docs in `docs/` for technical context.
2. Verify module dependencies in `build.gradle.kts` files.
3. Run `./gradlew ktlintCheck` to ensure style compliance before suggesting changes.
4. Prefer reproducing bugs on a connected device via `adb` logs; the on-device crash log and Diagnostics screen are described in `docs/RUN_GUIDE.md`.

---

## In-Depth Documentation

For deep-dives, see the `docs/` directory:

| Document | Contents |
|----------|----------|
| [docs/design.md](docs/design.md) | Full system design: module graph, domain model, player system, EPG architecture, theme system, screen inventory |
| [docs/FEATURES.md](docs/FEATURES.md) | Comprehensive feature reference with API details |
| [docs/DATABASE_SCHEMA.md](docs/DATABASE_SCHEMA.md) | Complete database schema for all Room DBs and SharedPreferences. **Update it in the same commit as any migration** - see below |
| [docs/epg_guide.md](docs/epg_guide.md) | EPG pipeline: ingestion, indexer, workers, TV Guide grid, Search the guide, settings, file inventory |
| [docs/NAVIGATION_GUIDE.md](docs/NAVIGATION_GUIDE.md) | Type-safe navigation, screen definitions, flow diagrams, TV focus handling |
| [docs/EPG_INDEX_STORAGE.md](docs/EPG_INDEX_STORAGE.md) | Why the EPG index grew to 87% dead space, the PRAGMA/Requery traps behind it, and how to read DB state from a file header |
| [docs/RUN_GUIDE.md](docs/RUN_GUIDE.md) | Build, install, deploy, backup/restore, debugging and focus walks for TV and mobile |
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

Multi-phase work is planned in writing before it is built, in `docs/plans/YYYYMMDD_<kebab-case-topic>-plan.md`. Everything else in `docs/` is standing reference material that describes how the app works today; a plan describes work that is proposed, in progress, or deliberately deferred.

Each plan states its own status at the top and keeps it current while the work runs (a Progress table for multi-lane work). [`docs/plans/README.md`](docs/plans/README.md) indexes them: open plans stay in `docs/plans/`; a finished or abandoned plan moves to `docs/plans/archive/` and its index row moves with it. Plans are archived, not deleted: source comments cite them by path and phase (`// Phase 6, docs/plans/archive/20260828_watch-state-durable-storage-plan.md`), and a finished plan can still record live information (the watch-state plan's "Known adjacent problems, deliberately out of scope"). Moving or renaming a plan means updating every reference - `git grep` its filename first.

When asked to produce a plan, write the real file under `docs/plans/` - not only an ephemeral plan-mode scratch file.

---

## Performance & Bug Journal

Hard-won lessons from production debugging. Read these before making changes in related areas.

### SharedPreferences JSON deserialization was the #1 hotspot
**Context:** The favourites getters deserialized JSON from SharedPreferences on every call with no in-memory cache, and were called per-chip inside `LazyRow items {}` — 500+ deserializations per recompose for large providers.
**Fix:** In-memory cache + dirty-flag + debounced write (the pattern `cachedWatchHistory` uses). Since 2026-08-28 favourites and watch state live in Room (`favorite_state`, `watch_state`), read through `MediaRepository`'s in-memory snapshots, so those blobs are gone; the rule still applies to any SharedPreferences JSON read in a hot path.

### Watch history lookups were O(n*m) in refreshPerItemData
**Context:** `MediaRepository.getPlaybackPosition()` did a linear scan of the watch-history blob per call, and `refreshPerItemData()` called it in a loop over every stream.
**Fix (landed):** `getPlaybackPositions(contentType)` now issues one indexed `watch_state` query and returns a Map for O(1) lookups. The lesson stands: always check for linear scans inside loops when profiling data layers.

### contentHash self-referential hash bug in XtreamContentManager
**Context:** `hashCode()` on a data class that includes the `contentHash` field (defaulting to 0). The stored entity has a non-zero `contentHash`, so `hashCode()` never matches — causing spurious DB re-inserts on every sync.
**Fix:** Exclude the hash field itself from `hashCode()` computation.

### Every catalog sync rewrote most rows and wiped their detail cache
**Context:** Xtream's `num` is a position in the provider's list; it shifts for tens of thousands of rows whenever the provider adds one (38,888 of 47,513 series between two bears syncs 3h apart). It was in `contentHash`, and a changed row is rewritten with `@Insert(REPLACE)` from a fresh entity, which reset `episodesFetchedAt`, `detailFetchedAt`, `contentRating`, `posterPath` and `containerExtension`.
**Fix:** `computeHash` leaves `num` out; a stream whose position alone moved gets `updateNums` (lists are ordered by `num`); changed rows go through `insertKeepingDetailCache`, which carries the detail columns over (a changed series still clears `episodesFetchedAt`, the "fetch its episodes again" trigger). **Rule:** a catalog hash covers content only, never list position; never write a catalog row with a bare `insertAll` from sync code. See `docs/plans/archive/20261002_catalog-sync-cache-churn-plan.md`.

### Clear All EPG Data takes 10+ minutes with DELETE FROM
**Context:** 4M+ rows on NVIDIA Shield with low-IOPS flash storage.
**Fix:** Replace row-level deletion with DB `destroy()` + `getInstance()` (recreate). Use `db()` function + `_dbGeneration` counter to re-subscribe Room Flows after recreation.

### EPG pipeline lacked feedback between download and ingestion
**Context:** Channel-based producer-consumer pipeline decouples downloads from ingestion. Large source finishes downloading but sits silently queued.
**Fix:** `EpgFileManager` emits an explicit `"Awaiting Ingestion"` phase (`ActiveSourceProgress.phase`) once a source is sent to the ingestion channel.

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
