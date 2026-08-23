# Multi-device Xtream Sync + "Who's Playing" Presence/Kick Plan

## Context

Fijerena runs on mobile phones and Android TV devices (e.g. NVIDIA Shield, Chromecast with Google TV), all pointed at the same Xtream subscription. Xtream favorites, watch history, and resume positions are currently **local-only**, stored per-device in `SharedPreferences` via `MediaRepository` (backing `"media_cache_$providerId"`) — nothing syncs between devices. Jellyfin already handles favorites, watch history, resume positions, and active sessions natively on its server side, so **this plan is strictly Xtream-only**; Jellyfin-backed providers remain untouched.

Three related problems to solve:
1. **Data sync** — favorites, watch history, and resume positions should follow the user across mobile and TV devices.
2. **Connection visibility & kick** — Xtream subscriptions cap concurrent connections (`active_cons` / `max_connections`). The Xtream API only exposes connection *counts*, never *what* is playing *where*. When connections are maxed out, there is currently no way to see which device is playing or remotely stop errant streams.
3. **User profiles** — households share TV devices (e.g., Alice and Bob both use the living room Shield), so sync cannot be single-namespace. Alice's favorites and watch history should follow *Alice* across devices, while Bob maintains his own separate profile. Underneath, a single Xtream subscription/catalog is shared — profiles personalize user data without requiring multiple Xtream accounts.

Key decisions (aligned with existing architecture):
- **Backend hosting: undecided, three viable options planned in parallel** — self-hosted Go server (Option A), Firebase (Option B), or Supabase (Option C). Details for all three options are specified in the "Backend Options" section.
- **Presence is self-reported**: each device heartbeats "I am playing X" to the server; other devices query/observe that list.
- **Kick = remote-stop signal**: server flags a device for kick; that device's app stops its own playback upon receiving the signal.
- **Auth = per-device token**: issued out-of-band during setup (no self-service public signup — personal multi-device setup).
- **Profiles**: open picker without PINs. Created in-app (Netflix-style "Add profile" in the picker, synced to server). Each device remembers its active profile across app restarts.
- **Strict String IDs**: all media IDs (`stream_id` / `item_id`) across all models, API endpoints, and schemas are `String` (matching Rule #3 in `AGENTS.md`).

---

## Codebase Audit & Architectural Reality

An empirical review of the existing codebase (`core/network` and `core/ui`) reveals the exact integration points required for this feature:

1. **Central User-Data Authority (`MediaRepository`)**:
   - The UI ViewModels (`StreamLoaderViewModel`, `CategoryViewModel`, `MovieDetailsViewModel`, `SeriesDetailsViewModel`, `SearchViewModel`) do **not** call `XtreamUserDataManager` directly. They interact exclusively with `MediaRepository` (`core/network/MediaRepository.kt`).
   - `MediaRepository` currently manages user data (favorites, watch history, playback position, recent items) for non-Jellyfin providers via local `SharedPreferences` (`"media_cache_$providerId"`).
   - Therefore, local cache re-scoping and sync outbox hooks must attach directly to **`MediaRepository`** (and `XtreamMediaProvider`), not `XtreamUserDataManager`.

2. **Domain Models (`WatchedItem` and `FavoriteItem`)**:
   - `MediaRepository` stores rich watch history via `@Serializable data class WatchedItem`:
     - `itemId: String`
     - `itemName: String`
     - `categoryId: String`
     - `contentType: String` (`LIVE_TV`, `MOVIES`, `TV_SHOWS`)
     - `timestamp: Long`
     - `playbackPosition: Long`
     - `duration: Long`
     - `isCompleted: Boolean`
     - `episodeId: EpisodeId?`
     - `episodeExtension: String?`
     - `seriesId: SeriesId?`
     - `seriesName: String?`
     - `audioTrackIndex: Int?`
     - `subtitleTrackIndex: Int?`
   - Favorites are stored via `@Serializable data class FavoriteItem`:
     - `itemId: String`, `itemName: String`, `categoryId: String`, `contentType: String`, `timestamp: Long`.
   - The sync server schema and API payloads must preserve these fields so episode and series metadata (`seriesId`, `seriesName`, `episodeId`) sync losslessly across devices.

3. **Playback Lifecycle Hooks (`MediaProvider` & `XtreamMediaProvider`)**:
   - `StreamLoaderViewModel` already triggers playback lifecycle hooks on `MediaRepository`:
     - `repo.onPlaybackStarted(streamId)`
     - `repo.onPlaybackProgress(streamId, position, duration, isPaused)`
     - `repo.onPlaybackStopped(streamId, position, duration)`
   - `XtreamMediaProvider` (`core/network/XtreamMediaProvider.kt`) inherits default no-op implementations for these methods from `MediaProvider` (`core/player/domain/MediaProvider.kt`).
   - Presence heartbeats and stop signals will be implemented inside `XtreamMediaProvider`'s overrides of these three methods.

4. **Dependency Architecture Constraints (`AGENTS.md`)**:
   - **No Circular Dependencies**: `core:player` MUST NOT depend on `core:network`. Adding presence metadata (such as optional `seriesName: String? = null`) to `MediaProvider` interface methods in `core:player` keeps the domain interface clean and decoupled.
   - **Repository Injection**: `AppContainer` (`core/ui/di/AppContainer.kt`) provides singletons. Profile switches will interact with `AppContainer` to re-key or evict repository caches cleanly.

---

## Backend Options

Three options for hosting the backend sync and presence services. Data models, app-side profile/kick logic, and implementation phases remain identical across all three options — only the server runtime, storage engine, auth mechanism, and outbox strategy differ.

### Comparison

| Metric / Capability | Option A: Self-hosted (Go + SQLite) | Option B: Firebase (Firestore + Auth) | Option C: Supabase (Postgres + Realtime) |
|---|---|---|---|
| **Ops & Hosting** | Self-hosted container (Go binary + SQLite file) | Fully managed cloud (Google) | Managed cloud or self-hosted Docker stack |
| **Data Engine** | Embedded SQLite (`TEXT` primary keys) | Document Store (NoSQL collections) | Relational Postgres (`TEXT` primary keys) |
| **Offline Sync Outbox** | App-side outbox (`pending_sync_ops` + `SyncPushWorker`) | Built-in Firestore offline persistence | App-side outbox (`pending_sync_ops` + `SyncPushWorker`) |
| **Presence & Kick Latency** | ~20s polling via heartbeats | Realtime listeners (<1s latency) | Postgres Realtime channels (<1s latency) |
| **Stale Cleanup** | Server-side background sweeper thread | Native Firestore Document TTL on timestamp | Scheduled SQL function (`pg_cron` / Edge Function) |
| **Authorization** | Application logic in Go middleware | Firestore Security Rules | Postgres Row-Level Security (RLS) |
| **Vendor Lock-in** | None (100% open, self-contained) | High (proprietary Firestore client/rules) | Low (standard Postgres SQL and open REST APIs) |
| **Development Burden** | Server code + App sync outbox | Minimal server code; Security rules configuration | SQL schema/RLS setup + App sync outbox |

---

## Architecture Details

### Option A — Self-hosted Server (`server/` subfolder, Go + SQLite)

A lightweight Go microservice located in `server/` (excluded from the Gradle build). Single static binary containerized with Docker.

#### Data Model (SQLite)
- `devices(id TEXT PRIMARY KEY, token_hash TEXT NOT NULL, name TEXT NOT NULL, created_at INTEGER NOT NULL)`
- `server_config(key TEXT PRIMARY KEY, value TEXT NOT NULL)` — contains `xtream_server_url` recorded from the first device's sync.
- `profiles(id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at INTEGER NOT NULL)`
- `favorites(profile_id TEXT NOT NULL, item_id TEXT NOT NULL, content_type TEXT NOT NULL, item_name TEXT NOT NULL, category_id TEXT NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER, PRIMARY KEY (profile_id, item_id, content_type))`
- `watch_history(profile_id TEXT NOT NULL, item_id TEXT NOT NULL, content_type TEXT NOT NULL, item_name TEXT NOT NULL, category_id TEXT NOT NULL, position_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL, is_completed INTEGER NOT NULL, episode_id TEXT, episode_extension TEXT, series_id TEXT, series_name TEXT, audio_track_index INTEGER, subtitle_track_index INTEGER, updated_at INTEGER NOT NULL, device_id TEXT NOT NULL, device_name TEXT NOT NULL, PRIMARY KEY (profile_id, item_id, content_type))`
- `presence(device_id TEXT PRIMARY KEY, device_name TEXT NOT NULL, profile_id TEXT NOT NULL, profile_name TEXT NOT NULL, content_type TEXT NOT NULL, stream_id TEXT NOT NULL, stream_name TEXT NOT NULL, series_name TEXT, started_at INTEGER NOT NULL, last_heartbeat_at INTEGER NOT NULL, kick_requested INTEGER NOT NULL DEFAULT 0)`

#### API Endpoints (Bearer Token Auth)
- `GET /profiles` — returns profile list.
- `POST /profiles {name}` — creates a profile.
- `PUT /profiles/{profileId}/favorites/{itemId}` / `DELETE /profiles/{profileId}/favorites/{itemId}` — syncs favorite state.
- `PUT /profiles/{profileId}/history/{itemId}` — upserts watch history / resume position.
- `GET /profiles/{profileId}/state` — returns complete snapshot of active favorites and history for `profileId`.
- `GET /history?profileId=&deviceId=` — query endpoint for server-side diagnostics.
- `POST /presence/heartbeat` `{deviceName, profileId, profileName, contentType, streamId, streamName, seriesName?}` -> `{kick: boolean}`.
- `POST /presence/stop` — explicit playback stop notification.
- `POST /presence/{deviceId}/kick` — flags `kick_requested = 1` for target device.

---

### Option B — Firebase (Firestore + Auth)

#### Data Model (Firestore Document Collections)
- `config/xtreamServerUrl` -> `{ value: string }`
- `devices/{deviceId}` -> `{ deviceName: string, createdAt: timestamp }`
- `profiles/{profileId}` -> `{ name: string, createdAt: timestamp }`
- `profiles/{profileId}/favorites/{itemId_contentType}` -> `{ itemId, itemName, categoryId, contentType, updatedAt, deletedAt }`
- `profiles/{profileId}/history/{itemId_contentType}` -> `{ itemId, itemName, categoryId, contentType, positionMs, durationMs, isCompleted, episodeId, episodeExtension, seriesId, seriesName, audioTrackIndex, subtitleTrackIndex, updatedAt, deviceId, deviceName }`
- `presence/{deviceId}` -> `{ deviceName, profileId, profileName, contentType, streamId, streamName, seriesName, startedAt, lastHeartbeatAt, kickRequested }`

---

### Option C — Supabase (Postgres + Realtime + Auth)

#### Data Model (Postgres Schema)
Mirroring Option A's relational SQL tables directly in Postgres with Row-Level Security (RLS) policies checking `xtream_server_url` against `server_config` and filtering by device token claims.

---

## Cross-Panel Safety Guard

Device stream IDs (`stream_id` / `item_id`) are unique only within a specific Xtream server's catalog. If two devices are configured with different Xtream providers, syncing favorites or progress between them would corrupt user data.

- Every `PUT`/`DELETE` favorite, history update, and presence heartbeat includes the normalized Xtream server URL (`scheme://host[:port]`, derived from `ProviderEntity.url`).
- The backend compares the incoming URL against `server_config['xtream_server_url']`. The first device to sync sets the initial URL.
- Subsequent calls with mismatching URLs are rejected with HTTP `409 Conflict` (`xtream_server_mismatch`).
- The app handles `409 Conflict` gracefully by displaying a non-blocking warning ("Xtream panel URL mismatch — sync paused for this device") in Settings, preserving local functionality.

---

## App Changes & Component Design

### 1. Active Profile Management (`ProfileManager`)
- New singleton `ProfileManager` in `core/network`:
  - Stores `activeProfileId` and `activeProfileName` in per-device `SharedPreferences` (`"fijerena_profile_prefs"`).
  - Exposes `val activeProfile: StateFlow<Profile?>`.
  - Provides `fun switchProfile(profile: Profile)` and `fun createProfile(name: String)`.

### 2. Local Cache Re-Scoping in `MediaRepository`
- `MediaRepository` currently uses `"media_cache_$providerId"`.
- Under the multi-profile architecture, `MediaRepository` re-scopes its user data cache file to `"media_userdata_${providerId}_${profileId}"`.
- When `ProfileManager.switchProfile` is called:
  1. `AppContainer.evictMediaRepository(providerId)` clears the in-memory repository cache.
  2. The next `getMediaRepository()` call instantiates `MediaRepository` bound to the new `profileId`.
  3. `SyncPullWorker` immediately executes a one-shot pull to hydrate the local cache for the newly active profile.

### 3. Outbox & Sync Execution (`SyncClient`, `SyncPushWorker`, `SyncPullWorker`)
- **`SyncClient`** (Ktor API client in `core/network/xtream/sync/`):
  - Encapsulates network operations to the sync backend (Option A, B, or C).
  - Includes device token, active `profileId`, and normalized Xtream URL in headers/payloads.
- **Outbox Queue (`pending_sync_ops`)**:
  - `MediaRepository` maintains a small JSON outbox of pending mutations when offline or when a sync push fails.
  - Operations: `ADD_FAVORITE`, `REMOVE_FAVORITE`, `SAVE_POSITION`, `CLEAR_HISTORY`.
- **`SyncPushWorker`** (`CoroutineWorker`):
  - Drains `pending_sync_ops` periodically or when network connectivity is restored (`Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)`).
- **`SyncPullWorker`** (`CoroutineWorker`):
  - Periodically (and on app foreground / profile switch) fetches `GET /profiles/{profileId}/state`.
  - Merges remote favorites and history items into `MediaRepository`'s local SharedPreferences using `updated_at` timestamps (remote wins if newer, preserving local non-conflicting entries).

### 4. Presence & Kick Wiring
- **Playback Hooks**:
  - `XtreamMediaProvider` overrides `onPlaybackStarted`, `onPlaybackProgress`, and `onPlaybackStopped`.
  - `onPlaybackStarted`: Sends initial `POST /presence/heartbeat`.
  - `onPlaybackProgress`: Sends throttled heartbeats (every ~20s). If the server responds with `{kick: true}`, `XtreamMediaProvider` emits a signal on `val kickEvents: Flow<Unit>`.
  - `onPlaybackStopped`: Sends `POST /presence/stop`.
- **Domain Method Enhancement (`core/player/domain/MediaProvider.kt`)**:
  - Update `onPlaybackStarted` and `onPlaybackProgress` in `MediaProvider` to accept optional `seriesName`:
    ```kotlin
    suspend fun onPlaybackStarted(itemId: String, seriesName: String? = null) {}
    suspend fun onPlaybackProgress(
        itemId: String,
        positionMs: Long,
        durationMs: Long,
        isPaused: Boolean = false,
        seriesName: String? = null,
    ) {}
    ```
  - Thread `seriesName` through `MediaRepository` and `StreamLoaderViewModel`. Default `null` parameter ensures zero breakage for Jellyfin or local providers.
- **Kick Handling**:
  - `MediaRepository` exposes `val kickEvents: SharedFlow<Unit> = provider?.kickEvents ?: emptyFlow()`.
  - `StreamLoaderViewModel` collects `kickEvents` while active. When emitted, it invokes `stopPlayback()`, closes the player UI, and displays a user notification ("Playback stopped from another device").

### 5. Compose UI Screens
- **Profile Picker Screen ("Who's Watching?")**:
  - Displayed on initial setup or via Settings profile switch button.
  - Lists synced profiles with avatar placeholders, plus an "Add Profile" button.
  - D-pad navigable for Android TV (scale & glow focus tokens) and touch-optimized for mobile.
- **Connected Devices & Active Presence Screen**:
  - Located in Settings under "Connected Devices".
  - Shows Server URL, Device Token setup, and reachability indicator.
  - Live "Now Playing" list showing: Device Name, Profile Name, Content Title (with Series Name if applicable), and Start Time.
  - Each active entry includes a "Kick Stream" button (triggers `POST /presence/{deviceId}/kick`).

---

## Implementation Phases

### Phase 0 — Server Skeleton & Device Auth
- Implement initial server infrastructure (Option A Go service, Option B Firebase Auth/Rules, or Option C Supabase setup).
- Define `devices` schema and Bearer token verification.
- Provide device provisioning CLI / admin token generator.
- **Exit Criteria**: Server responds to authenticated health check ping; invalid tokens receive 401 Unauthorized.

### Phase 1 — Profile System & Local Cache Re-Scoping
- Server: Implement `profiles` table and `GET/POST /profiles` endpoints.
- App: Create `ProfileManager` and Profile Picker Compose UI.
- App: Re-scope `MediaRepository` user data storage to `"media_userdata_${providerId}_${profileId}"`.
- App: Wire `AppContainer` cache eviction on profile switch.
- **Exit Criteria**: Switching profiles in-app loads distinct local favorites and watch history for each profile; profile additions sync to server.

### Phase 2 — Favorites, Watch History & Resume Position Sync
- Server: Implement `favorites`, `watch_history`, and `server_config` tables + sync endpoints.
- App: Create `SyncClient`, outbox persistence (`pending_sync_ops`), `SyncPushWorker`, and `SyncPullWorker`.
- App: Integrate push triggers into `MediaRepository` mutation methods (`addFavorite`, `removeFavorite`, `savePlaybackPosition`).
- App: Build "Connected Devices" Settings screen with cross-panel mismatch warning.
- **Exit Criteria**: Favoriting a title on Mobile reflects on TV after pull; offline mutations queue in outbox and push successfully when network returns; mismatching server URLs display warning without crashing.

### Phase 3 — Presence Visibility ("Now Playing")
- Server: Implement `presence` table, `POST /presence/heartbeat`, `POST /presence/stop`, and TTL sweeper.
- App: Update `MediaProvider` interface with optional `seriesName` parameter.
- App: Wire presence heartbeats into `XtreamMediaProvider` (`onPlaybackStarted`, `onPlaybackProgress`, `onPlaybackStopped`).
- App: Add live "Now Playing" section to Connected Devices screen.
- **Exit Criteria**: Starting playback on one device displays active stream details (including series name for TV shows) on another device's Connected Devices screen; stopping playback removes the entry.

### Phase 4 — Kick & Remote-Stop Execution
- Server: Implement `POST /presence/{deviceId}/kick` and `{kick: true}` heartbeat response flag.
- App: Add "Kick Stream" button to active presence items in Connected Devices UI.
- App: Wire `kickEvents` flow from `XtreamMediaProvider` -> `MediaRepository` -> `StreamLoaderViewModel`.
- **Exit Criteria**: Clicking "Kick Stream" on Device A causes Device B to stop playback within ~20 seconds and present a notification.

### Phase 5 — Offline Polish & Intermittent Network Resilience
- App: Implement profile picker offline fallback (cached profile list / local offline profile mode).
- App: Exercise pull/push worker retries under simulated packet loss and server downtime.
- **Exit Criteria**: App operates seamlessly offline; sync recovers cleanly upon reconnect without data duplication or loss.

---

## Verification & Testing Strategy

1. **Unit & Integration Tests**:
   - `MediaRepositorySyncTest`: Verify outbox queuing, push trigger invocation, and pull merge logic.
   - `ProfileManagerTest`: Test profile switching, StateFlow emissions, and SharedPreferences persistence.
   - `SyncClientTest`: Mock server responses (including 409 Conflict URL mismatch and 401 Unauthorized) and verify error handling.
   - Run `./gradlew testDebugUnitTest` to validate test suite execution.

2. **Manual Device Verification**:
   - Install build on Mobile (`./gradlew :mobile:installDebug`) and Android TV (`./gradlew :tv:installDebug`).
   - Confirm D-pad navigation on TV for Profile Picker and Connected Devices UI.
   - Test cross-device sync: Favorite item on Mobile -> Verify arrival on TV.
   - Test kick functionality: Play stream on TV -> Trigger kick from Mobile -> Verify TV stops playback cleanly.
