# Fijerena — System Design

Fijerena is a native Android media player for several content providers (Xtream IPTV, Jellyfin, SMB, local files, remote M3U). Two app targets — **mobile** (phones, tablets) and **tv** (NVIDIA Shield, Chromecast with Google TV, Sony Bravia) — share a common core. Both apps use `applicationId` `org.njarasoa.fijerena`.

What the user sees: [FEATURES.md](FEATURES.md). Routes, back stack and the TV focus contract: [NAVIGATION_GUIDE.md](NAVIGATION_GUIDE.md). Databases: [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md). Guide pipeline: [epg_guide.md](epg_guide.md).

Source paths below are relative to each module's `src/main/java/org/njarasoa/fijerena/` (core modules: `.../core/<module>/`).

---

## Module Architecture

```
fijerena/
  mobile/            app: phone/tablet
  tv/                app: Android TV
  core/
    player/          Media3 player and playback service, domain models, Xtream API client,
                     device detection, diagnostics (crash log, safe mode)
    network/         provider implementations, repositories, Room databases, EPG pipeline,
                     catalogue sync, profiles, live-sync engine, settings export
    ui/              shared ViewModels, theme tokens, shared components, AppContainer,
                     live-sync manager, TV Guide layout engine
    navigation/      Screen routes, navigateOnce, ContentType
    data/            AuthViewModel (restored Xtream session)
  server/            live-sync server (Cloudflare Worker / Docker)
```

### Dependency Graph

```
tv, mobile ──→ core:ui, core:network, core:player, core:navigation, core:data
core:ui ─────→ core:network, core:player
core:network ─→ core:player   (api)
core:data ───→ core:player    (api)
core:player, core:navigation  → no project dependencies
```

`core:player` cannot depend on `core:network` (that would be circular): what the player needs from settings or the network layer is passed in by its callers.

### Key Classes

| Class | Module | Role |
|-------|--------|------|
| `AppContainer` (`di/`) | core:ui | Manual DI: one `ProviderRepository` and one `MediaRepository` per source, resolved behind a `Mutex`; `clearAllCaches()` on a source switch; `externalSwitches` when live sync moves the device off a profile or source |
| `FijerenaApplication` | core:ui | Start-up: crash-loop check, EPG initialisation, migrations, sync |
| `MediaRepository` | core:network | Per-source, per-profile facade: catalogue, favourites, watch state, Recent, guide pages |
| `ProviderRepository` (`provider/`) | core:network | Sources, per-source settings, credentials, database shrink |
| `ProfileRepository` (`profile/`) | core:network | Profiles and the active profile |
| `MediaProviderFactory` | core:network | Builds and caches one `MediaProvider` per source id |
| `ProviderSyncManager`, `XtreamSyncWorker` (`xtream/`) | core:network | Xtream catalogue sync into `xtream_v2.db` |
| `EpgFileManager`, `EpgIndexer`, `XmltvSearchService`, `XmltvEpgService` (`xmltv/`) | core:network | Guide download, index, search, TV Guide pages |
| `SyncEngine` (`sync/`) and `SyncManager` (core:ui `sync/`) | core:network, core:ui | Live sync: records, crypto, transport; app wiring, now-playing, Remote Stop |
| `SettingsExportManager` | core:network | Settings export / import |
| `AppSettings` | core:network | `app_settings` preferences (device, per-profile and synced keys) |
| `StreamingPlaybackService` | core:player | The one ExoPlayer engine, as a `MediaSessionService` |
| `GuideLayout` (`guide/`) | core:ui | TV Guide time-to-pixel engine shared by `TvGuideGrid` and `MobileGuideGrid` |

ViewModels start their repositories asynchronously (no `runBlocking` during composition).

---

## Multi-Provider Architecture

Which provider supports what: [FEATURES.md → Sources](FEATURES.md#sources). Each provider declares it in `ProviderCapabilities` (content types, EPG, search, auth, progress sync, server-side user data, autoplay next episode).

### Domain Model (`core:player` `domain/`)

Every provider's data is mapped to these types before it reaches a screen.

```
MediaProvider (interface)   connect/disconnect, categories, items, series/movie detail,
                            resolvePlayableStream, search, EPG, related titles, TMDB art
MediaCategory               id, name, parentId, iconUrl, isVirtual
MediaItem                   id, name, mediaType, categoryId, thumbnailUrl, metadata, providerData, target
MediaMetadata               plot, cast, director, genre, rating, year, duration, tmdbId, trailer, …
SeriesDetail / MovieDetail  seasons with episodes / extended movie metadata
PlayableStream              uri, headers, isLive, title, mimeType
BrowseTarget                what a row opens: Series, Episode, Movie, Channel, CategoryRef
ProviderCapabilities        feature flags per provider
ProviderType (enum)         XTREAM, JELLYFIN, SMB, LOCAL, REMOTE_M3U
MediaType (enum)            LIVE_CHANNEL, MOVIE, SERIES, EPISODE, VIDEO_FILE
TitleLanguage.kt            parseDisplayTitle, episodeOwnTitle, playerEpisodeName
```

IDs are `String` throughout (Jellyfin UUIDs, SMB paths, file URIs).

### Provider Implementations (`core:network`)

| File | Role |
|------|------|
| `XtreamMediaProvider.kt`, `XtreamMapper.kt`, `XtreamRepository.kt`, `xtream/` | Xtream: API via `XtreamApiService` (core:player `api/`, Ktor), mapping, cached catalogue and sync |
| `jellyfin/JellyfinMediaProvider.kt`, `JellyfinApiService.kt`, `JellyfinModels.kt` | Jellyfin REST (Ktor + OkHttp engine) |
| `smb/SmbMediaProvider.kt`, `SmbClient.kt` | SMB shares (smbj) |
| `local/LocalMediaProvider.kt`, `LocalFileScanner.kt`, `M3uParser.kt` | Local files and M3U playlists |
| `remote/RemoteM3uMediaProvider.kt`, `BaseM3uMediaProvider.kt` | Remote M3U (fetched, parsed, cached) |
| `tmdb/` | TMDB synopses, logos, backdrops, title matching |

### Storage

| Store | Contents | Classes |
|-------|----------|---------|
| `providers.db` (`SettingsDatabase`, Room v15) | Sources (config, active flag, sync stats and last-sync delta), guide sources (with change-detection validators), pipeline stats, profiles, live-sync bookkeeping | `ProviderEntity`, `EpgSourceEntity`, `EpgPipelineStatsEntity`, `ProfileEntity`, settings-sync entities |
| `xtream_v2.db` (`XtreamDatabase`, Room v24) | Xtream catalogue (categories, streams, series, episodes, per-stream EPG payloads, FTS4), and the provider-agnostic `watch_state` and `favorite_state` tables | `Xtream*Entity`, `WatchStateEntity`, `FavoriteStateEntity` |
| `epg_index.db` (`EpgIndexDatabase`, Room v17) | Guide programme index with FTS4 | `epgindex/` |
| EncryptedSharedPreferences | Passwords and sessions per source (and per profile for Jellyfin logins) | `provider_creds_<id>`, `xtream_secure_credentials` |
| `xtream_cache_<id>` SharedPreferences | Per-source Xtream response cache | JSON blobs |
| `media_cache_<id>` SharedPreferences | Recent categories (up to 20 per content type), last-browsed position | JSON blobs + scalars |
| `app_settings` SharedPreferences | Device, per-profile and synced settings | `AppSettings` |

Watch position, completion and favourites are durable Room rows that are never truncated; see [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) §3; the SharedPreferences stores are §4–§6.

---

## Player System

### Components (`core:player`)

```
service/
  StreamingPlaybackService     MediaSessionService: ExoPlayer lifecycle, wake locks, analytics, position saves
  PlaybackServiceConnection    binds the UI to the service
  ExhaustionToastWatcher       decides when rebuffers are frequent enough for the "excessive buffering" toast
config/
  AdaptiveLoadControl          network- and content-aware buffers, swapped at runtime
  NetworkBufferProfile         buffer, retry, timeout and byte-cap constants
  PlayerConfigFactory          track selector (device codec preference), ContentType
network/
  NetworkMonitor               ConnectivityManager callback → StateFlow<NetworkType>
  StreamHealthMonitor          live stream health (healthy / unstable / degraded)
source/
  StreamingMediaSourceFactory  HLS / DASH / progressive detection, timeouts, auth headers
  AdaptiveLoadErrorPolicy      retries: Wi-Fi 5, cellular 8, exponential backoff 0.5–5 s
device/DeviceCapabilities.kt   DeviceDetector: device type, HEVC/AV1/4K support, preferred codecs
viewmodel/PlaybackViewModel    UI-facing playback control and track queries
model/                         PlaybackState, PlayerMetadata, track info, EpgModels, PositionSave, NowPlayingSnapshot
api/XtreamApiService           Xtream API client (Ktor)
diagnostics/                   CrashLog, ProcessExits, LaunchCounter, SafeMode, Redact
```

### Buffer Strategy

`AdaptiveLoadControl` swaps buffer parameters without restarting the player: `NetworkMonitor`'s `NetworkType` (Wi-Fi / cellular) and the content type (live / VOD) pick the profile. Values from `NetworkBufferProfile`:

| Profile | Min | Max | To start | After rebuffer |
|---------|-----|-----|----------|----------------|
| Wi-Fi Live TV | 15 s | 30 s | 0.5 s | 1 s |
| Wi-Fi VOD | 30 s | 60 s | 2.5 s | 10 s |
| Cellular Live TV | 50 s | 50 s | 2.5 s | 5 s |
| Cellular VOD | 40 s | 100 s | 8 s | 10 s |

No back buffer (a seek back re-downloads). Byte caps bound memory: live 32 MB; VOD a quarter of the large-heap memory class, between 64 and 160 MB (`vodTargetBufferBytes`). Cellular buffers are used as they are: the old multiplier keys (`cellular_live_multiplier`, `cellular_vod_multiplier`) are only carried through settings export / import, never applied.

### Performance Analytics

`PerformanceAnalyticsListener` (inner class of `StreamingPlaybackService`) feeds Stats for Nerds:

| Metric | Source | Exposure |
|--------|--------|----------|
| Dropped / total frames, measured FPS, recent drop rate | `onDroppedVideoFrames`, `onVideoFrameProcessingOffset` | `StateFlow` |
| Rebuffer count and time | `onPlaybackStateChanged` (READY → BUFFERING) | `StateFlow<Int>`, `StateFlow<Long>` |
| Measured bandwidth | `onBandwidthEstimate` | `StateFlow<Long>` |
| ABR quality switches | `onDownstreamFormatChanged` (video height changes) | `StateFlow<Int>` |
| Stream retries | `AdaptiveLoadErrorPolicy` + live retry counter | `StateFlow<Int>` |
| Uptime | stream start time (`streamStartTimeMs`) | `StateFlow<Long>` |
| Stream health | `StreamHealthMonitor` | `StateFlow` |

### Formats and Codecs

- HLS (`.m3u8`, the usual Xtream format), DASH (`.mpd`), MPEG-TS, MP4, MKV, WebM; `content://` for local files. SMB hands the player an `smb://` URI, for which there is no data source yet.
- Jellyfin's pre-built FFmpeg decoder (`org.jellyfin.media3:media3-ffmpeg-decoder`) adds AC3, EAC3, DTS, TrueHD and MLP audio.
- `DeviceDetector` orders preferred video codecs for the track selector (each only when the device decodes it):

| Device | Codec order |
|--------|-------------|
| NVIDIA Shield, Chromecast with Google TV | AV1 → HEVC → AVC |
| Sony Bravia | HEVC → AVC |
| Other TVs, phones | AVC |

### Jellyfin Playback

**Auth:** `JellyfinApiService` adds `Authorization: MediaBrowser …` and, once signed in, `X-Emby-Token` to every request through an `HttpSend` interceptor. Sign-in body: `{"Username": "...", "Pw": "..."}`. Quick Connect stores only the token.

Before each playback the app negotiates the stream with `POST /Items/{id}/PlaybackInfo`:

1. **DeviceProfile** (`buildDeviceProfile()`, built once): direct-play containers MP4/M4V, MKV, WebM, TS with H.264, HEVC, VP9, AV1, AC3, EAC3, DTS, TrueHD, FLAC, Opus; a transcode profile of HLS/TS, H.264 + AAC/MP3, `BreakOnNonKeyFrames=true`; codec profiles H.264 up to High@L5.2 and HEVC Main/Main10 up to L6; `MaxStreamingBitrate` 140 Mbps.
2. **Response** (`JellyfinPlaybackInfoResponse`): the first media source decides the URL — `supportsDirectPlay` → `buildStreamUrl(itemId, container, mediaSourceId)` (`/Videos/{id}/stream…?Static=true`); `transcodingUrl` → `$serverUrl$transcodingUrl` (HLS); `supportsDirectStream` → the stream URL; otherwise, or with no source, the plain static URL.
3. **Session:** `playSessionId` and `mediaSourceId` are kept in `JellyfinMediaProvider` and sent with every progress / stop report so the server can manage the transcode.
4. **Fallback:** if `PlaybackInfo` fails, the static direct-play URL. Requests time out after 60 s, enough for a transcode to start.

### Search Implementation

- **Xtream:** FTS4 over the synced catalogue, no network call. Each word becomes a prefix term (`the*`) matched against `xtream_streams_fts` / `xtream_series_fts`, up to 200 results per content type. A second FTS query per type counts matches in categories hidden by the content filters (the "N hidden" note); it uses `+s.categoryId` so SQLite drives the lookup from the FTS matches rather than the `categoryId` index (see the AGENTS.md Performance & Bug Journal).
- **Jellyfin:** the server's search endpoint.
- **Local / Remote M3U:** `BaseM3uMediaProvider.search`, a title match over the loaded playlist.
- **SMB:** no provider search; `SearchViewModel` scans items already loaded.
- **Guide:** two-tier FTS4 MATCH on `epg_index.db` (raw query, then a sanitised AND-style retry), with a title-only `LIKE` scan while the index is stale. See [epg_guide.md](epg_guide.md).

---

## Navigation

Type-safe routes (`kotlinx.serialization`, Navigation Compose) defined once in `core:navigation` `Screen.kt` and used by `tv/navigation/TvNavHost.kt` and `mobile/navigation/MobileNavHost.kt`. The `Screen` list, the navigation tree, back-stack rules and TV focus rules are kept only in [NAVIGATION_GUIDE.md](NAVIGATION_GUIDE.md).

## EPG System

```
Guide sources (XMLTV URLs)
  → EpgFileManager (download or stream, RefreshQueue, WorkManager EpgSyncWorker)
    → XmltvParser (streaming parse)
      → EpgIndexer (batched Room inserts, FTS4) → epg_index.db

epg_index.db
  → EpgIndexDao
    → XmltvSearchService → EpgBrowserViewModel → Search the guide
    → XmltvEpgService (one page of channels per day) → MediaRepository → EpgViewModel → TV Guide
  EpgSourceDao (providers.db) → EpgManagementViewModel → Guide sources
```

Downloads run concurrently (`Semaphore`, 3 on mobile, 2 on TV) as producers; 2 workers batch-insert into `epg_index.db`. `RefreshQueue` runs up to 3 tasks at once and de-duplicates by task id against queued and running tasks. Clear All Data destroys and recreates the Room instance rather than `DELETE FROM`; `EpgManagementViewModel` re-subscribes its `sources` Flow through a `_dbGeneration` counter. The TV Guide falls back to the source's own EPG (`getEpgBulk`) when the index has nothing for the channels. Full pipeline, state machine and search strategy: [epg_guide.md](epg_guide.md); tables: [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md).

---

## Theme & Design System

### Style

- **Content first:** category grids lead with poster art, with gradient text overlays rather than heavy borders or elevation; items without art get a dark card with an accent icon and initials.
- **Glass panels:** translucent surfaces (`GlassPanel`, `CinemaAlpha.glassPanel`) for player controls, channel panels and dialogs.
- **Focus (TV):** scale, outline and shadow come from the selected look and feel (below).

### Theme Architecture

```
CinemaThemePalette (data class)     complete colour set per theme
CinemaThemeHolder (object)          current palette for non-composable code
LocalCinemaTheme (CompositionLocal) composable access
UiStyle / LocalUiStyle / UiStyleHolder   look and feel, the same way

core/ui/.../theme/CinemaColors.kt   Cinema* colours read from the active palette
tv/.../ui/theme/CinemaColors.kt     re-exports (computed get() properties)
mobile/.../ui/theme/Color.kt        re-exports (computed get() properties)
```

### Palettes

| Theme | ID | Accent | Background | Surface |
|-------|----|--------|-----------|---------|
| Deep Night (default) | `deep_night` | `#2979FF` electric blue | `#0F1014` | `#161A20` |
| AMOLED Black | `amoled_black` | `#E0E0E0` near-white | `#000000` | `#0A0A0A` |
| Amethyst | `amethyst` | `#9C6BFF` purple | `#0F1014` | `#161A20` |
| Teal | `teal` | `#26C6DA` teal | `#0F1014` | `#161A20` |

The live / secondary orange `#FF6D00` is the same in every theme.

### Look and Feel (`UiStyle`)

`core/ui/.../theme/UiStyle.kt` carries shape, type weight and tracking, dialog position and scrim, grid spacing and focus effect, and icon style. Four presets — **Material** (default), **Cupertino**, **Roku** and **BRAVIA** — chosen in Settings (`AppSettings.uiStyleId`). Any palette combines with any style.

### UI Scale (TV)

Settings → Display → Text & grid size (0.4–1.0, default 0.8). TV `MainActivity` replaces `LocalDensity` with `Density(density = original * uiScale, fontScale = original)`, so every `dp` and `sp` scales; the `.scaled()` extensions in `tv/.../ui/theme/UiScale.kt` are no-ops to avoid double scaling. `LocalUiScale` (core:ui) still carries the factor. Mobile has no UI scale.

### Design Token Files

| Token file | Module | Contents |
|-----------|--------|----------|
| `CinemaColors.kt` | core:ui | `Cinema*` colours from the active palette |
| `CinemaAlpha` | core:ui | Opacity constants (glass, scrim, tint, text levels) |
| `CinemaAnimation` | core:ui | Durations: focus, nav transition (300 ms), controls auto-hide (TV 15 s, mobile 5 s), toast (3 s), stats update (1 s), up-next lead (90 s) |
| `CinemaCornerRadius` | core:ui | Corner radii |
| `CinemaSpacing` | core:ui | Spacing scale (none, xxxs through xxl) |
| `CinemaThemePalette` | core:ui | Palette data class and the 4 palettes |
| `UiStyle` | core:ui | Look-and-feel presets |
| `TvDimensions` | tv | TV sizes (safe margins, dialog widths, progress bars, guide column) |
| `TvFocusTokens` | tv | Focus scale, border, containers, driven by `LocalUiStyle` |
| `MobileDimensions` | mobile | Mobile sizes (icons, overlays) |

### Typography (TV)

A 15-style scale (48 sp down to 14 sp): display and headline styles in Manrope, title, body and label in Roboto. Body text is at least 18 sp.

### Focus System (TV)

- Scale, outline and shadow come from `LocalUiStyle.current.grid` through `TvFocusTokens`: focus scale 1.0 (Roku, outline only) to 1.09 (Cupertino); outline 3 dp when the style uses one, never below `minFocusBorderWidth` (2 dp).
- Modifiers: `tv/ui/components/modifiers/FocusModifiers.kt`. Focus behaviour (panes, return focus, retries) is the contract in [NAVIGATION_GUIDE.md](NAVIGATION_GUIDE.md#tv-focus).

### Safe Margins (TV Overscan)

56 dp horizontal / 32 dp vertical on every TV screen root (`Spacing.tvSafeMarginHorizontal`, `Spacing.tvSafeMarginVertical`; `TvDimensions.safeMarginHorizontal` / `safeMarginVertical`). Full-screen video is the exception.

### Shared Components (`core:ui` `components/`)

`GlassPanel`, `CinemaThumbnail`, `GradientOverlay`, `CinemaAlertDialog`, `BadgedTitle` / `LanguageBadge` / `CinemaBadge` / `RatingBadge`, `WatchedBadge`, `ProfileAvatar`, `EmbeddedPlayerSurface`, `RetryWhenOnline`, `AppLoadingScreen`, `QrCode`, `TitleLogoOrText`, up-next timing (`UpNext.kt`).

### TV Components (`tv/ui/components/`)

- **Buttons** (`buttons/CinemaButton.kt`): `CinemaButton`, `CinemaPrimaryButton`, `CinemaSecondaryButton`, `CinemaIconButton`, `CinemaDangerButton`, `CinemaDangerIconButton`
- **Detail screens:** `TvDetailHero`, `AmbientBackdrop`, `RelatedTitlesRow`, `TvSectionTabs`, `DetailsActions`
- **Input and focus** (`input/`): `TvPane`, `NavReturnFocus`, `FocusRetry`, `FocusReturn`, `TvSearchField`, `TvOptionRow`, `TvToggleRows`, `TvSelectableButton`, `TvInputListItem`
- **Other:** `TvGlassPanel`, `TvErrorState`, `ReadOnlyFieldWithEdit`, `AccentBlock` (`cards/`), `modifiers/FocusModifiers`, `modifiers/TvDpadEscape`

---

## Shared ViewModels (`core:ui` `viewmodels/`)

Both apps share these. Several have a `…ViewModelFactory` for manual injection.

| ViewModel | Purpose |
|-----------|---------|
| `CategoryViewModel` | Categories (incl. virtual ones), items, Recent, last-played item |
| `SearchViewModel` | Search across content types, hidden-match counts, search history |
| `EpgViewModel` | TV Guide: channel rows, paged listings per day, row actions |
| `EpgBrowserViewModel` | Search the guide: FTS search, grouping, stale refresh |
| `EpgManagementViewModel` | Guide sources CRUD, refresh, maintenance |
| `ProviderViewModel` | Sources CRUD, active source, sync |
| `MovieDetailsViewModel` / `SeriesDetailsViewModel` | Detail screens, TMDB, episode lists |
| `StreamLoaderViewModel` | Resolves what to play and starts it (zaps, next episode, history) |
| `SettingsViewModel` | Settings state |
| `ProfilesViewModel` | Profiles and the "Who's watching?" picker |
| `SyncSettingsViewModel` | Live sync setup, pairing, devices |
| `DiagnosticsViewModel` | Crash log and process-exit history |
| `SafeModeViewModel` | Safe mode's Clear caches |
| `PlaybackViewModel` (core:player) | Playback control over `StreamingPlaybackService` |

`LoginViewModel` (like `Screen.Login`) remains but no screen uses it.

---

## Screen Inventory

### TV (`tv/`)

| Screen | File | Description |
|--------|------|-------------|
| Home | `feature/contentselection/ContentTypeSelectionScreen.kt` (+ `components/TvContinueWatchingShelf.kt`) | Content types, header buttons, Continue Watching |
| Category screen | `feature/category/TvCategoryGridScreen.kt` | Categories and items in two panes; Live TV preview layer |
| Movie details | `feature/movie/MovieDetailsScreen.kt` | Hero, Play / Resume, tabs, related titles |
| Episodes | `feature/episode/EpisodeSelectionScreen.kt` | Season tabs, episode list, episode detail panel |
| Player | `feature/player/TvPlayerScreen.kt` → `ui/player/PlayerScreen.kt` | The `Player` route (movies, episodes); `PlayerScreen` also renders Live TV full screen inside the preview layer |
| Search | `feature/search/SearchScreen.kt` | Query field, grouped results |
| Settings | `feature/settings/SettingsScreen.kt` (+ `components/`) | Group rail and pane |
| Sources | `feature/provider/ProviderSelectionScreen.kt` | Source list: use, guide, add, edit, copy, delete |
| Add / Edit Source | `feature/provider/TvAddProviderScreen.kt` (+ `components/`) | Connection form; per-source settings, filters, library data |
| TV Guide | `feature/epg/TvEpgGuideScreen.kt` + `feature/epg/TvGuideGrid.kt` | Time grid |
| Guide sources | `feature/epg/TvEpgManagementScreen.kt` | XMLTV sources of one source |
| Search the guide | `feature/epgbrowser/TvEpgBrowserScreen.kt` | Programme search |
| Profile picker | `feature/profile/ProfilePickerScreen.kt` | "Who's watching?" |
| Live sync | `feature/settings/SyncSettingsScreen.kt` | Sync group setup, pairing, devices |
| Diagnostics | `feature/settings/DiagnosticsScreen.kt` | Crash log and exit history |
| Safe mode | `feature/safemode/SafeModeScreen.kt` | Continue, Clear caches, Show diagnostics |
| Newer data | `feature/safemode/NewerDataScreen.kt` | Close, Reset sources |

Live TV and category parts (`feature/category/components/`): `TwoColumnLayout`, `CategoryList`, `StreamList`, `LiveTvSplitLayout` (preview and full screen), `LiveTvChannelPanel`, `ChannelContext`, `FavoriteMenuDialog` (row actions), `CategoryStates`, `CategoryGridUtils`.

Player parts (`ui/player/`): `PlayerScreenState`, `PlayerKeyHandler`, `PlayerEffects`; `components/overlays/` `TvPlayerControlsOverlay` (OSD), `TvStatsOverlay`, `TvTuningOverlay`, `TvUpNextOverlay`; `components/dialogs/` audio, subtitle, quality and chapter pickers.

### Mobile (`mobile/`)

| Screen | File | Description |
|--------|------|-------------|
| Home | `feature/contentselection/ContentTypeSelectionScreen.kt` (+ `components/MobileContinueWatchingShelf.kt`) | Content types, header buttons, Continue Watching |
| Category list | `feature/category/MobileCategoryListScreen.kt` | Categories and items; Live TV dock and full screen |
| Movie details | `feature/movie/MovieDetailsScreen.kt` | Hero, Play / Resume, related titles |
| Episodes | `feature/episode/EpisodeSelectionScreen.kt` | Season tabs, episode list |
| Player | `feature/player/MobilePlayerScreen.kt` (+ `components/`) | Touch controls, channel lists, stats, up next |
| Search | `feature/search/SearchScreen.kt` | Query field, grouped results |
| Settings | `feature/settings/SettingsScreen.kt` (+ `components/`) | Grouped list |
| Sources | `feature/provider/ProviderSelectionScreen.kt` | Source list |
| Add / Edit Source | `feature/provider/MobileAddProviderScreen.kt` (+ `components/`) | Connection form, Quick Connect, per-source settings, data management |
| TV Guide | `feature/epg/MobileEpgGuideScreen.kt` + `feature/epg/MobileGuideGrid.kt` | Date tabs, time grid, programme sheet |
| Guide sources | `feature/epg/MobileEpgManagementScreen.kt` | XMLTV sources of one source |
| Search the guide | `feature/epgbrowser/MobileEpgBrowserScreen.kt` | Programme search |
| Profile picker | `feature/profile/ProfilePickerScreen.kt` | "Who's watching?" |
| Live sync | `feature/settings/MobileSyncSettingsScreen.kt` (+ `components/QrScanner.kt`) | Sync group setup, pairing, devices, Remote Stop |
| Diagnostics | `feature/settings/MobileDiagnosticsScreen.kt` | Crash log and exit history, Share |
| Safe mode | `feature/safemode/MobileSafeModeScreen.kt` | Continue, Clear caches, Show diagnostics |
| Newer data | `feature/safemode/MobileNewerDataScreen.kt` | Close, Reset sources |

Player parts (`feature/player/components/`): `MobileControlsOverlay`, `MobileChannelListSheet`, `MobileChannelToast`, `MobileStatsOverlay`, `MobileUpNextOverlay`, `MobilePlayerDialogs`, `MobilePlayerStates`.

---

## Settings Export / Import

`SettingsExportManager` (`core:network`) serialises sources, guide sources, favourites, favourite categories, watch state and a few global settings to JSON through the Storage Access Framework; what is included and how conflicts resolve is in [FEATURES.md](FEATURES.md#export--import). On TV the file-picker callbacks only store the URI, and the export / import runs in a `LaunchedEffect` keyed on it, so the work survives recomposition; mobile runs it from the callback in the screen's coroutine scope. The import MIME filter includes `*/*` for older file managers.

---

## Build & Dependencies

Building, installing and device discovery: [RUN_GUIDE.md](RUN_GUIDE.md).

- **UI:** Jetpack Compose, `androidx.tv.material3` (TV), Coil
- **Networking:** Ktor (OkHttp engine), kotlinx.serialization
- **Player:** Media3 (ExoPlayer, HLS, DASH, session), Jellyfin's FFmpeg decoder
- **Database:** Room with KSP
- **Navigation:** Navigation Compose with kotlinx.serialization routes
- **SMB:** `com.hierynomus:smbj`
- **Encryption:** `androidx.security:security-crypto` (EncryptedSharedPreferences)
- **Background:** WorkManager (guide refresh, FTS rebuild, catalogue sync)
- **Lint:** ktlint (`./gradlew ktlintCheck`)
