# Known Bugs — Prioritized Plan

Merged from three sweeps: a "watching experience" sweep (EPG subsystem,
content-browsing/favorites/history, mobile gesture/rotation), a broader
audit of `core/player`, `core/network`, and `core/ui`/`mobile`/`tv` (DI,
ViewModels, navigation, EPG pipeline), and a third follow-up sweep (#12-#21,
2026-06-22) covering provider sync/network correctness, remaining player
service-instance races, ViewModel lifecycle issues, and mobile/TV UI screens
not covered by the first two passes. All three sweeps used agents to
generate candidates, then personally verified every lead against the actual
source before recording it — agent output is a hypothesis, not ground truth,
and a substantial fraction of raw candidates from every sweep turned out to
be false positives on inspection (see "Investigated, ruled out" at the
bottom).

One bug from the original watching-experience sweep — `StreamingPlaybackService`
never re-arming `instanceReady` after `onDestroy()`, which caused live TV to
get permanently stuck after the device spent hours in standby — has since
been fixed (commit `9c3c7a8`, verified still in place: `instanceReady` is
reassigned to a fresh `CompletableDeferred()` in `onDestroy()`). It's omitted
from the active list below.

Ordered by impact (High → Moderate → Low), then by ease of fix within each
tier. Each entry below also has a **Complexity** rating; at a glance:

| # | Bug | Complexity |
|---|-----|-----------|
| 1 | [FIXED] EPG Now Playing/Up Next stale 12h | Easy |
| 2 | [FIXED] AppContainer zombie repo at provider ID 0 | Moderate |
| 3 | [FIXED] RefreshQueue duplicate concurrent execution | Moderate |
| 4 | [SKIPPED — not reachable] ProviderViewModel sync-status overwrite | Moderate–Involved |
| 5 | [FIXED, manual verify pending] Mobile gesture (self-mutation + missing guard) | Easy–Moderate |
| 6 | [FIXED] First ~10s of playback not saved | Trivial |
| 7 | [SKIPPED — split-screen only, mobile only] EpgBrowserViewModel stale pager after Clear All Data | Involved |
| 8 | [FIXED] Live-retry drops bandwidth telemetry | Trivial |
| 9 | [FIXED] PlaybackViewModel metadata-before-service race | Trivial |
| 10 | [FIXED] EpgIndexDatabase cursor leak | Trivial |
| 11 | [FIXED] onDestroy() listener cleanup asymmetry | Trivial |
| 12 | [FIXED] Xtream sync mass-deletes local library on empty/partial server response | Easy–Moderate |
| 13 | [FIXED] Player getInstance()/awaitInstance() race beyond playStream() | Easy |
| 14 | [FIXED] AppContainer caches stale MediaRepository after credential edit | Moderate |
| 15 | JellyfinMediaProvider.resolvePlayableStream() has no auto-reconnect-on-401 | Easy–Moderate |
| 16 | [FIXED] Saved audio/subtitle track index uses wrong indexing scheme on restore | Moderate |
| 17 | [SKIPPED — deprioritized] Favorites/favorite-categories silently evict oldest entry at cap | Easy–Moderate |
| 18 | [FIXED] Episode season auto-expand clobbers manual accordion toggle | Easy |
| 19 | [FIXED] EpgViewModel.forceRefresh()'s isRefreshing flag decoupled from reload | Trivial |
| 20 | [FIXED] XtreamSyncWorker never updates provider sync stats | Trivial |
| 21 | [FIXED] TV EPG grid's "now" highlight is wrong on any non-today date | Trivial–Easy |

Quick-win batch (one small PR, low review risk): #6, #8, #9, #10, #11 — all
fixed.
#4 and #7 were investigated and skipped — both have documented triggers that
turned out not to be reachable via the app's actual navigation/ViewModel
architecture (see their entries below for the reachability analysis, so
neither needs re-investigating cold later).
#12, #13, #14, #16, #18, #19, #20, #21 fixed (2026-06-22) — see their entries
below for the resolution notes. #17 was deprioritized, not invalidated —
unlike #4/#7, the bug itself is still confirmed real. #15 from the same
sweep remains open.

## High impact

### 1. [FIXED] Live TV's "Now Playing" / "Up Next" info can be stale for up to 12 hours after an EPG sync
**Root cause, traced precisely:**
- `StreamLoaderViewModel.loadStreamInternal()` (`core/ui/.../viewmodels/StreamLoaderViewModel.kt:186`) calls `repo.getEpgBulkForItems(listOf(currentItem))` to populate `currentEpgProgram`/`nextEpgProgram` — the data shown in the player's live-TV info bar.
- That delegates to `MediaRepository.getEpgBulkForItems()` (`core/network/.../MediaRepository.kt:241-256`), which tries `xmltvEpgService.getEpgForChannels(items)` first.
- `XmltvEpgService.getEpgForChannels()` (`core/network/.../xmltv/XmltvEpgService.kt:133-207`) checks an in-memory/SharedPreferences cache via `getCachedEpg()` (lines 270-290) before ever touching the SQLite EPG index. That cache is considered valid as long as: (a) the wall-clock TTL (`PARSED_CACHE_TTL_MS = 12h`, line 25) hasn't elapsed, and (b) every requested channel ID is already a key in the cached map (line 145, `allPresent`).
- Nothing in this check considers whether the underlying SQLite index (`epg_index.db`) has been updated more recently than the cached snapshot. `EpgSyncWorker`/`EpgIndexer` write fresh programme data straight into SQLite on every background sync, but `clearXmltvCache()` (the only thing that nulls this cache) was never called from the sync path — only from explicit user actions elsewhere (e.g. switching providers).
- Net effect: once a channel's EPG has been fetched once, the player can keep showing that exact snapshot — wrong current-program title, wrong end time, wrong "up next" — for up to 12 hours after a sync corrects or updates it, with no way for the user to tell.

**Trigger:** happens passively, every live-TV session after every background EPG sync — the broadest-reach bug in this list.
**Impact:** wrong now/next info shown silently, on essentially every live TV viewing session at some point.
**Fix:** have `EpgSyncWorker`/`EpgFileManager.processAllSources()` call `XmltvEpgService.clearCache()` (already exists, `XmltvEpgService.kt:264-268`) after a successful ingest, so a completed sync invalidates the stale snapshot. Minimal — uses an existing method, no new invalidation mechanism needed.
**Complexity:** Easy — the invalidation method already exists; the only wrinkle is that `XmltvEpgService` is keyed per-provider (`xmltv_cache_$providerId`), so the call site needs to know which provider(s) just synced rather than clearing one global cache.
**Resolved:** added `EpgFileManager.invalidateXmltvCache()`, called from both `processAllSourcesInternal()` and `processSingleSourceInternal()` right after a successful ingest (post-swap, so the SQLite index is already visible). It resolves affected provider IDs from each ingested source's `providerId` — a `null` providerId ("applies to all providers") expands to every provider via `ProviderRepository.getAllProvidersList()` — then calls `XmltvEpgService(context, providerId).clearCache()` for each.
**Verify:** force an EPG sync (`EpgSyncDebugReceiver`) while a live channel with stale cached EPG is playing; confirm the player's now/next info updates promptly after the sync completes instead of holding the old snapshot.

### 2. [FIXED] AppContainer permanently caches a provider-less repository under ID 0
**File:** `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/di/AppContainer.kt` (`getMediaRepository()`, lines 37–66)

When `providerId` is the default `0L` and `providerRepository.getActiveProvider()` returns null (no active provider yet), `resolvedId` falls back to `0L`. The function then builds a `MediaRepository` keyed at `0L`, but since `entity` is also null in that branch, `newRepo.setProvider(...)` is never called — the repo has no backing provider implementation. It still gets cached at `mediaRepositories[0L]` (line 64) before `repo.connect()` is attempted and fails. Every later call to `getMediaRepository()` with the default arg will hit this cached, permanently-broken instance — even after a real active provider exists — because the cache is keyed by ID and `0L` is never evicted.

**Trigger:** any code path that calls `getMediaRepository()` with no explicit ID before an active provider is persisted to disk (e.g. a ViewModel's async init racing app startup, or a brief window right after deleting the active provider).
**Impact:** silent, sticky connection failure for the affected user that survives until `clearAllCaches()` is called or the app is restarted — narrow trigger window, but total breakage when hit.
**Fix:** don't cache the result when `resolvedId <= 0` or `entity == null`; return/throw a clear "no active provider" signal instead.
**Complexity:** Moderate — contained to one function, but the `?: run { }` cache-miss block needs careful restructuring so a provider-less repo is built-and-returned-but-not-cached, without breaking the mutex invariant or causing repeated recreation churn on every call until a provider exists.
**Resolved:** moved `mediaRepositories[resolvedId] = newRepo` inside the `if (entity != null)` branch, so a provider-less repo is still built and returned (preserving the existing `MediaRepository` non-nullable contract and the connect-and-fail behavior callers already handle) but is never cached. The next call after a real active provider exists resolves to that provider's real ID and builds/caches a proper repo, unaffected by any earlier provider-less attempt.

### 3. [FIXED] RefreshQueue lets the same task ID run twice concurrently
**File:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/queue/RefreshQueue.kt` (`submit()` lines 67–83, `activeJobs`/`processAvailable()` lines 42, 96–122)

`submit()` only de-duplicates against tasks still sitting in the pending `queue` (line 73: `queue.find { it.task.id == task.id }`). It never checks `activeJobs`, the map of tasks already polled off the queue and currently executing. If a refresh for source/provider X is submitted, starts executing, and a second refresh for the same X is submitted before the first finishes, the de-dup check finds nothing in `queue` and enqueues a second run — which then executes *concurrently* with the first.

**Trigger:** double-tap "Refresh Now" for the same EPG source, or let a manual refresh overlap with the coroutine-based auto-refresh for the same source.
**Impact:** two concurrent pipeline runs writing into the same DB rows/staging tables — wasted work at best, corrupted/interleaved EPG data at worst.
**Fix:** check `activeJobs` (or a combined in-flight ID set) in `submit()` before accepting a new submission with the same ID; coalesce into the existing `Deferred` instead of starting a parallel run.
**Complexity:** Moderate — `queue` (guarded by `queueMutex`) and `activeJobs` (a separate `ConcurrentHashMap`) aren't under one lock today; de-duplicating across both needs either a unified lock or careful TOCTOU reasoning, plus a decision on semantics (coalesce vs. queue-behind). Small code footprint (~15-20 lines), but the concurrency design needs to be right.
**Resolved:** replaced the separate `ConcurrentHashMap`-backed `activeJobs` with `activeTasks`, guarded by the same `queueMutex` as `queue`. A task is moved from `queue` into `activeTasks` in the same dequeue step in `processAvailable()` — before the semaphore permit is even acquired — so it's never absent from both at once. `submit()` now checks `activeTasks` first and coalesces into the running task's existing `Deferred` instead of enqueueing a duplicate. (A sub-microsecond gap remains between `scope.launch` returning a `Job` and that job being registered into `activeTasks`, both still inside `processAvailable()`; closing that fully would need pre-registering a placeholder before launch, which wasn't judged worth the extra complexity given the bug's realistic triggers — UI double-taps, overlapping manual/auto refresh — operate on millisecond-or-slower timescales.)

### 4. [SKIPPED — trigger not reachable] ProviderViewModel's sync-status tracking is overwritten by a second concurrent sync
**File:** `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/ProviderViewModel.kt` (`syncProvider()` lines 347–355, completion check in `loadProviders()` lines 151–174)

`syncingProviderId` is a single nullable `var` with no guard in `syncProvider()` against a sync already being in flight. If the user starts syncing provider A (`syncingProviderId = A`), then starts syncing provider B before A finishes, `syncingProviderId` is silently reassigned to B. When A's sync later completes and updates its DB row, the `providers` Flow re-emits and the completion-check in `loadProviders()` looks up `syncingProviderId` (now B) — so A's completion is never reported, and B can get a stale/spurious "completed" status reported off an old `lastSyncedAtMs` that has nothing to do with the in-flight sync.

**Trigger:** sync two different providers in close succession (multi-provider users only).
**Impact:** wrong or missing sync success/error feedback shown to the user.
**Fix:** track sync state per provider ID (`Map<Long, SyncState>` instead of a single `var`), or disable starting a new sync while one is already in progress.
**Complexity:** Moderate–Involved — the ViewModel-side change is mechanical, but it's unverified whether `_syncState` is consumed as a single global value by UI (e.g. one spinner for "the" sync) — if so, every consumer needs updating too. Needs a quick check of UI call sites before sizing precisely.
**Skipped:** confirmed `_syncState` IS a single global value, consumed identically by `MobileAddProviderScreen.kt`/`TvAddProviderScreen.kt`/`DataManagementSection.kt`/`CacheManagementSection.kt` — so the "proper" fix would mean reworking 4 UI files across both platforms. But the documented trigger isn't reachable at all: `ProviderViewModel` is scoped per-screen via Compose's `viewModel()` (tied to that screen's `NavBackStackEntry`), `syncProvider()` is only ever called with a fixed `editId` from the single Add/Edit Provider screen, and no call site invokes it with a second, different provider ID from the same instance. "Sync two different providers in close succession" would require two separate screen visits, each getting its own isolated `syncingProviderId`/`_syncState` — they can't collide. Decided not worth the 4-file UI rework for a scenario that can't occur via any current call site. (Separately found a real, structurally-similar issue: `ProviderSyncManager.startManualSync()` has zero de-duplication — a double-tap on the same provider's "Sync Now," or a manual sync racing the periodic `performFullSync()` auto-refresh, launches fully concurrent `syncAll()` runs with no coordination. Not fixed here; would be the same class of fix as #3 if picked up later.)

### 12. [FIXED] Xtream sync mass-deletes the local library on any empty/partial server response
**File:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/manager/XtreamContentManager.kt` (`syncCategories()` lines 339-409, `syncStreams()` lines 411-505, `syncSeries()` lines 507-598 — all three share the identical pattern)

All three sync functions fetch fresh data from the server, track which IDs were seen this round (`seenIds`), then compute `allIds.filter { it !in seenIds }` and delete everything not seen — this is how server-side deletions get mirrored locally. Confirmed precisely: `syncCategories()` (line 376-377: `val seenIds = entities.map { it.categoryId }.toSet(); val toDeleteIds = currentHashes.keys.filter { it !in seenIds }`), `syncStreams()` (line 487-488: `val allIds = streamDao.getStreamIds(...); val toDelete = allIds.filter { it !in seenIds }`), and `syncSeries()` (line 580-581, identical shape) all have zero guard against the case where the server call succeeds (no exception thrown) but returns an empty or truncated list — which real IPTV providers do under rate-limiting, transient overload, or auth hiccups, returning HTTP 200 with an empty/partial body rather than an error. When that happens, `seenIds` is empty or far smaller than reality, and the cleanup step interprets the user's entire existing Live TV channel list, VOD catalog, or series library as server-side deletions and wipes it locally — with no minimum-count or "suspiciously-empty-response" sanity check anywhere in any of the three functions.

**Trigger:** a single transient server hiccup (rate-limit, brief outage, auth blip) that returns 200 with zero/few items instead of an HTTP error, during any scheduled or manual sync.
**Impact:** real data loss — a user's whole Live TV and/or VOD/Series library can vanish after one bad sync, silently, with no warning and no rollback path short of a full re-sync (which itself depends on the next sync actually returning real data).
**Fix:** before running the delete step, compare `seenIds.size` against the source's previous channel/stream/series count (or just skip the delete-by-absence step entirely when `seenIds` is empty while `currentHashes` is non-empty) — treat a suspiciously-empty response as a failed sync, not a confirmed mass-deletion.
**Complexity:** Easy–Moderate — the guard logic itself is a few lines, but it needs to be applied consistently across all three functions (categories/streams/series), and the right threshold ("how empty is suspicious") needs a judgment call rather than a hard rule.
**Resolved:** added an identical guard to all three functions — `if (seenIds.isEmpty() && currentHashes.isNotEmpty())`, logs a warning, and bails out before the delete/insert/timestamp-update step (`return` in `syncCategories()`, `return@coroutineScope` in `syncStreams()`/`syncSeries()` since those are wrapped in a non-inline `coroutineScope { }`). Bailing out also skips `rebuildFts()` and the per-type timestamp write, so a suspiciously-empty sync is correctly treated as not-yet-synced rather than marked fresh.

### 13. [FIXED] Player control methods beyond playStream() still race service initialization via getInstance()
**Files:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt:195-201` (the comment documenting this exact gap), `core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt` (`pause()` line 138, `resume()` line 145, `stop()` lines 154 and 446, `seekTo()` line 193, `setPlaybackSpeed()` line 224, `selectAudioTrack()` line 271, `selectSubtitleTrack()` line 317, `disableSubtitles()` line 327, `selectVideoQuality()` line 418, `enableAutoQuality()` line 428), `tv/src/main/java/org/njarasoa/fijerena/feature/player/TvPlayerScreen.kt:156,161`, `mobile/src/main/java/org/njarasoa/fijerena/feature/player/MobilePlayerScreen.kt:253,274`

`playStream()` was already fixed (commits afa3c71/b082e57) to use `StreamingPlaybackService.awaitInstance()` instead of `getInstance()`, closing a race where the service singleton isn't published yet when the player screen mounts. That fix was never applied to any of its ~12 sibling call sites — confirmed every one of them still calls `getInstance()?.xxx()`. The gap is even self-documented: the comment at `StreamingPlaybackService.kt:195-201` explicitly names `TvPlayerScreen`'s `setContentType()`/`setPositionSaveListener()` calls as having this exact problem ("fired from a LaunchedEffect right as the player screen mounts... can't resolve to a service whose mediaSession/adaptiveLoadControl is still null... silently no-op on null with no log"), but the actual call sites (`TvPlayerScreen.kt:156,161`, `MobilePlayerScreen.kt:253,274`) were never updated to match. `setPositionSaveListener()` in particular is wrapped in `LaunchedEffect(Unit)` — a true one-shot effect that never re-fires — so if `getInstance()` is null at that exact moment, the position-save callback is never wired up for the rest of that screen visit at all.
**Trigger:** ordinary cold-start timing — the player screen's `LaunchedEffect`s can run before `StreamingPlaybackService.onCreate()` finishes publishing its singleton, no split-screen or contrived setup needed. For the `PlaybackViewModel` methods, any pause/resume/seek/track-selection call (e.g. a Bluetooth media-button press, or a remote key) landing in that same narrow startup window.
**Impact:** `setPositionSaveListener` lost → total, silent loss of resume-position/watch-history for that entire session, no error surfaced. `setContentType` lost → wrong (VOD vs Live) buffer profile for the whole session. The `PlaybackViewModel` methods losing a call → a single silently-dropped user command (pause/seek/track-switch does nothing), no feedback.
**Fix:** swap `getInstance()` for `awaitInstance()` at all listed call sites, mirroring the already-applied `playStream()` fix exactly.
**Complexity:** Easy — mechanical, same pattern already proven correct in this exact file; the only cost is touching ~14 call sites across 3 files.
**Resolved:** swapped all 10 `PlaybackViewModel` control methods plus the 4 `TvPlayerScreen.kt`/`MobilePlayerScreen.kt` `LaunchedEffect` call sites to `awaitInstance()`. Deliberately left two call sites unchanged: `onPlayerError`'s `getInstance() == null` check (line 70, a non-suspend `Player.Listener` callback used only as a defensive fallback, not a race) and `onCleared()`'s `getInstance()?.stop()` (line 446, synchronous cleanup with no `viewModelScope` available by that point — `awaitInstance()` there could hang forever if the service never initialized, which would be a worse bug than the one being fixed).

### 14. [FIXED] AppContainer caches a stale MediaRepository after a provider's credentials are edited
**Files:** `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/di/AppContainer.kt` (`getMediaRepository()`, lines 37-84), `core/network/src/main/java/org/njarasoa/fijerena/core/network/provider/ProviderRepository.kt` (`updateProvider()`, lines 77-108)

`ProviderRepository.updateProvider()` correctly calls `MediaProviderFactory.clearCache(id)` after saving new credentials (line 107) — but that's a *different*, lower-level cache than `AppContainer.mediaRepositories: MutableMap<Long, MediaRepository>`. `getMediaRepository()`'s cache-hit path (`mediaRepositories[resolvedId] ?: run { ... }`) returns the already-cached `MediaRepository` object directly with no re-check of whether the provider's credentials changed — it never calls `MediaProviderFactory.create()` again on a cache hit, so clearing that lower-level factory cache has zero effect on an `AppContainer`-level repo that's already cached. The `MediaRepository`'s `provider` field was set once, at construction time (`newRepo.setProvider(provider)`), using whatever URL/username/password was current then; nothing re-fetches it later.
**Trigger:** browse/use a provider (so its `MediaRepository` gets cached), then go to Settings and edit that same provider's URL or password (e.g. after a reseller rotates credentials), save — ordinary usage, not an edge case.
**Impact:** the app keeps connecting with the old URL/credentials for that provider — silent auth failures or connections to the wrong server — until the user explicitly switches the active provider (the only call sites that invoke `AppContainer.clearAllCaches()`) or the process restarts. The save itself appears to succeed (DB row and `MediaProviderFactory` are both correctly updated), making this confusing to debug.
**Fix:** give `AppContainer` a targeted single-ID eviction method (mirroring `MediaProviderFactory.clearCache(id)`) and call it from the provider-edit save path (`ProviderViewModel`'s save/update flow), not just on full provider switch via `clearAllCaches()`.
**Complexity:** Moderate — the eviction method itself is trivial, but wiring it into the right save-path call site needs care to avoid evicting/rebuilding more than necessary (e.g. don't evict on every settings save, only on credential-affecting ones).
**Resolved:** added `AppContainer.evictMediaRepository(providerId)` (removes one entry under the existing mutex) and call it from `ProviderViewModel.performSave()`'s `id != null` (edit) branch, right after `providerRepository.updateProvider(...)` succeeds — this is the actual UI-reachable path (`validateAndSave()` → `performSave()`, wired from the Add/Edit Provider screens on both platforms). Note: `ProviderViewModel.updateProvider()` (a separate, differently-named ViewModel method) turned out to be dead code with zero callers, so it wasn't touched. Also found a second real call site with the same exposure — `SettingsExportManager.kt:436`'s import-conflict "Overwrite" path also calls `providerRepository.updateProvider()` — but `SettingsExportManager` is in `core:network`, which can't depend on `AppContainer` (in `core:ui`) without violating the module direction in AGENTS.md's architecture constraints. Left unfixed; would need the eviction call added at whatever `core:ui`-or-above layer invokes the import flow.

## Moderate impact

### 5. [FIXED, manual verify still pending] Mobile swipe gesture for channel-switch/category-panel can flicker, drop input mid-swipe, or double-fire
**File:** `mobile/src/main/java/org/njarasoa/fijerena/feature/player/MobilePlayerScreen.kt:351-396` (re-confirmed still present in current code)

Two compounding issues in the same gesture handler:
- The drag-gesture `Modifier.pointerInput(state.categoryStreams, showCategoryOverlay, showLastWatchedOverlay, showStats) { detectDragGestures(...) }` is keyed on `showCategoryOverlay`/`showLastWatchedOverlay` — but the handler **mutates those exact same two booleans from inside its own `onDrag` callback** (lines 384-390) while the user's finger is still down. The moment the horizontal-swipe branch flips one of them, Compose recomposes with a new `pointerInput` key, tearing down and restarting the `detectDragGestures` coroutine *during* the same physical touch. Local state (`verticalAccumulator`, `horizontalAccumulator`, `hasFiredThisGesture`) is lost, and the rest of that swipe can simply go unrecognized.
- The vertical channel-switch branch has a per-gesture single-fire guard (`hasFiredThisGesture`), but the **horizontal panel-toggle branch has no equivalent guard** at all — it just resets its own accumulator to 0 on fire (line 392) and re-evaluates on the next 80px within the *same* continuous drag. A single longer/diagonal/imprecise swipe can cross both the 100px vertical and 80px horizontal thresholds in one gesture, firing a channel switch *and* an overlay toggle together — or cross the horizontal threshold twice (overshoot-and-correct), toggling a panel open then immediately shut.

**Trigger:** any imprecise, longer-than-threshold, or diagonal swipe on the Live TV player screen — ordinary day-to-day mobile usage, not an edge case.
**Impact:** swiping to switch channels or open the category/last-watched panel feels unreliable — panel flicker, dropped gesture tail, or unintended combined actions. Jarring, mobile-only.
**Fix:** two independent, minimal changes:
1. Don't key `pointerInput` on state the handler itself writes. Drop `showCategoryOverlay`/`showLastWatchedOverlay` from the key list (keep `state.categoryStreams`/`showStats`, which are externally driven) and read current overlay-open state via a `rememberUpdatedState`-wrapped value inside the callback instead.
2. Give the horizontal branch the same single-fire-per-gesture protection the vertical branch already has (reuse `hasFiredThisGesture`, or add a second flag).

**Complexity:** Easy–Moderate — two independent, well-understood Compose fixes, small diff, single file. The real cost isn't the code but the lack of automated test coverage for gestures — correctness has to be confirmed by manual device testing.
**Verify:** on a mobile device/emulator, do one continuous swipe right noticeably longer than the 80px threshold (don't lift between); confirm the category panel opens once and stays open. Repeat for vertical channel-switch with a long swipe to confirm only one channel change fires and the rest of the gesture isn't dropped. Try a diagonal swipe and confirm only one of (channel switch / overlay toggle) fires, not both.
**Resolved:** dropped `showCategoryOverlay`/`showLastWatchedOverlay` from the `pointerInput()` key list (kept `state.categoryStreams`/`showStats`); reads of those two vars inside `onDrag` stay fresh regardless, since they're `mutableStateOf`-backed, not plain captured values — no `rememberUpdatedState` wrapper needed. Added `hasFiredHorizontalThisGesture`, mirroring the existing vertical guard (renamed to `hasFiredVerticalThisGesture` for clarity), reset alongside it in `onDragStart`/`onDragEnd`/`onDragCancel`. Compiles and installed to a real device + emulator, but the on-device swipe-by-swipe manual verification above hasn't been completed yet — got interrupted mid-attempt (building a throwaway Remote M3U test provider to reach the live-TV gesture surface, since no provider was configured on the test devices).

### 6. [FIXED] First ~10 seconds of every playback session are never saved to watch history
**File:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt` (`PlayerListener`, lines 790–819, save gate at lines 798–816)

`lastSavedPosition` starts at `0L`, and the periodic save in `onPlaybackStateChanged()` only fires when `abs(currentPosition - lastSavedPosition) >= saveIntervalMs (10_000) || isPaused`. For a freshly started, uninterrupted playback session, no save happens until either 10 seconds of playback have elapsed or the user pauses.

**Trigger:** play a short clip/trailer under 10 seconds, or have the app/device killed within the first 10 seconds of any stream without pausing first.
**Impact:** watch-history/auto-resume position for that session is lost — falls back to whatever (if anything) was last persisted, defeating resume.
**Fix:** seed `lastSavedPosition` so the very first `STATE_READY` transition (or the `playStream()` call itself) performs an immediate save, not just on the periodic/pause boundary.
**Complexity:** Trivial — one-line fix: seed `lastSavedPosition = -saveIntervalMs` instead of `0L` so the very first `STATE_READY` always clears the save-gate. No structural change.

### 7. [SKIPPED — reachable only via split-screen, narrow] EpgBrowserViewModel's "Now Playing" pager is never rebuilt after Clear All EPG Data
**File:** `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/EpgBrowserViewModel.kt` (`loadNowPlaying()`, lines 314–338; same pattern in the search pager around lines 456–480)

`loadNowPlaying()` calls `EpgIndexDatabase.getInstance(context)` once, captures the resulting `dao` in a `Pager { dao.getPagedNowPlaying(...) }` closure, and caches the flow for the ViewModel's lifetime via `.cachedIn(viewModelScope)`. `EpgManagementViewModel` deliberately guards against exactly this class of bug with its `_dbGeneration` counter (re-subscribing Room flows after a DB destroy+recreate from "Clear All EPG Data") — but `EpgBrowserViewModel` has no equivalent mechanism. If "Clear All EPG Data" destroys and recreates the database while the Now Playing pager is alive, every subsequent page load queries a DAO bound to a closed `SupportSQLiteDatabase`.

**Trigger:** open the EPG browser's Now Playing/search view, then run "Clear All EPG Data" from EPG management while that view is still active or paged — a narrower window than the other bugs in this tier.
**Impact:** paging errors / stuck "Now Playing" list until the screen is fully re-created; no crash observed downstream since Paging3 surfaces load failures as `LoadState.Error`, but the feature breaks until next navigation.
**Fix:** mirror `EpgManagementViewModel`'s generation-counter pattern, or have `EpgIndexDatabase.destroy()` broadcast an invalidation event this ViewModel observes to rebuild its pagers.
**Complexity:** Involved — the most architecturally heavy fix in this list. `EpgManagementViewModel`'s `_dbGeneration` counter is scoped to that ViewModel; fixing this properly means either promoting an invalidation signal up into `EpgIndexDatabase` itself so any consumer can react, or duplicating the generation-counter pattern locally. Also needs verifying that hot-swapping the inner `Flow<PagingData<...>>` inside `_pagedNowPlaying`/`_pagedSearchResults` is actually picked up by whatever composable collects it (`collectAsLazyPagingItems()`) — not yet verified.
**Skipped:** confirmed the "Involved" framing was overstated — `EpgIndexer.state` is already a process-wide signal `EpgBrowserViewModel` already subscribes to forever (`indexer.state.collect{}` in `initPagedNowPlaying()`), and `clearAll()` already publishes `EpgIndexState.NotIndexed` into it after destroying the DB. The actual gap is just that the existing collector's `if (state is Indexed)` ignores every other state — a same-file, few-line fix, not a new generation-counter/broadcast mechanism. However, the documented trigger ("open EPG browser, then clear data while it's still active") isn't reachable in single-window use: EPG Browser and EPG Management are pushed from two different parent hubs with no `saveState`, so leaving EPG Browser always pops it — destroying the ViewModel and its collector — before Clear All Data can run, on both mobile and TV. It IS reachable on mobile via Android split-screen (two instances of the app open at once, sharing the same process-wide `EpgIndexer`/`EpgIndexDatabase` singletons) — confirmed `mobile/AndroidManifest.xml` doesn't set `resizeableActivity="false"`, so multi-window isn't blocked — but that's a deliberate two-window action, not ordinary usage, and TV has no split-screen at all. Decided the narrow, mobile-only, split-screen-only trigger doesn't justify the change right now; `_pagedSearchResults` would also need separate handling since it has no reactive mechanism at all today.

### 15. JellyfinMediaProvider.resolvePlayableStream() has no auto-reconnect-on-401, unlike every other Jellyfin call
**File:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/jellyfin/JellyfinMediaProvider.kt` (`resolvePlayableStream()` lines 286-345+, `withAutoReconnect()` lines 510-524, `ensureConnected()` lines 505-508)

Every other Jellyfin method (`getCategories`, `getItems`, `getMovieDetail`, `search`, etc. — confirmed at lines 84, 111, 131, 175, 185, 281, 458) wraps its API call in `withAutoReconnect { }`, which detects a 401 (`ClientRequestException` with `HttpStatusCode.Unauthorized`), clears the session, re-authenticates, and retries once. `resolvePlayableStream()` — the function that actually starts playback — instead calls `ensureConnected()` (line 293), which only checks the *local* `isConnected()` flag and short-circuits to `true` if a session token is merely present, without validating it against the server. The actual API calls (`api.getPlaybackInfo()`, `api.getItemById()`, lines 300-301) are made directly via `async { }`, completely bypassing `withAutoReconnect`.
**Trigger:** a Jellyfin session token expires or is revoked server-side while the token is still cached locally, then the user tries to play something without having browsed categories/items first (browsing would have silently self-healed the session via the `withAutoReconnect`-wrapped calls elsewhere).
**Impact:** playback fails with a confusing, generic error while every other part of the app continues to "work" — the inconsistency makes this hard to diagnose from a bug report.
**Fix:** route `resolvePlayableStream()`'s `getPlaybackInfo()`/`getItemById()` calls through `withAutoReconnect { }` like every other method, instead of the local-only `ensureConnected()` check.
**Complexity:** Easy–Moderate — mechanical to apply the existing wrapper, but the two calls are currently fired in parallel via `async { }` inside a `coroutineScope { }`, so the wrapper needs to wrap that parallel-fetch block as a unit rather than each call individually.

### 16. [FIXED] Saved audio/subtitle track index is restored using the wrong indexing scheme
**File:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt` (`selectAudioTrack(groupIndex, trackIndex)` lines 612-641, `selectSubtitleTrack(groupIndex, trackIndex)` lines 643-672, `selectAudioTrack(consolidatedIndex)` lines 564-570, `selectSubtitleTrack(consolidatedIndex)` lines 600-610, `getAudioTracks()` lines 534-562), `tv/.../player/TvPlayerScreen.kt:190-196`, `mobile/.../player/MobilePlayerScreen.kt:302-308`

Two different indexing schemes exist for the same concept and the save/restore path mixes them. **Save:** `selectAudioTrack(groupIndex, trackIndex)` and `selectSubtitleTrack(groupIndex, trackIndex)` (the direct two-int overloads used by the interactive track-selector dialogs) persist the raw **in-group** `trackIndex` via `onPositionSaveListener?.invoke(..., trackIndex, ...)` (confirmed at lines 640 and 671) — this flows unchanged through `MediaRepository.savePlaybackPosition()` into `WatchedItem.audioTrackIndex`/`subtitleTrackIndex`. **Restore:** both `TvPlayerScreen.kt` and `MobilePlayerScreen.kt` feed that same saved int into the single-argument `selectAudioTrack(consolidatedIndex)`/`selectSubtitleTrack(consolidatedIndex)` overloads, which treat it as a position in the **flattened, cross-group** list built by `getAudioTracks()` (iterates every track group, then every track within each group, in order). These two schemes only produce the same number when a stream happens to expose exactly one relevant track group — true for many but not all sources; some multi-rendition HLS/DASH streams expose multiple audio or subtitle track groups.
**Trigger:** select a non-default audio or subtitle track (via the in-player selector dialog) on a stream with multiple track groups, then resume that item later.
**Impact:** the wrong track is silently selected on resume (or the restore call no-ops if the saved index falls outside the flattened list's bounds) — no crash, no error, just the wrong language/subtitle track playing.
**Fix:** persist the consolidated index (i.e., the track's position in `getAudioTracks()`/`getSubtitleTracks()`) at save time instead of the raw in-group index, so save and restore agree on one scheme — or have the restore path search by stable track identity (language/label) instead of by position.
**Complexity:** Moderate — touches the save call sites, the `onPositionSaveListener` signature's meaning (not its shape), and needs the same fix applied symmetrically to both audio and subtitle paths.
**Resolved:** at both save call sites, compute `getAudioTracks()`/`getSubtitleTracks()` (the same flattened list the restore path's consolidated selector indexes into) and look up the position of the just-selected `(groupIndex, trackIndex)` pair via `indexOfFirst`, persisting that instead of the raw `trackIndex`. Falls back to `null` (via `.takeIf { it >= 0 }`) in the unreachable case the pair isn't found, rather than reusing `-1` — which already has a distinct meaning ("subtitles explicitly disabled") on the subtitle path.

### 17. [SKIPPED — deprioritized] Favorites and favorite categories silently evict the oldest entry once the configurable cap is hit
**File:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/MediaRepository.kt` (`addFavorite()` lines 577-593, `addFavoriteCategory()` lines 652-667)

Both functions insert the new favorite at the front (`favorites.add(0, ...)`) then `.take(providerSettings.favoritesMaxSize)` — whatever falls past the cap (default 100, configurable 10-500 per provider settings) is gone, with no signal to the caller that an eviction happened. Both functions return a plain `Boolean` meaning only "was this newly added," and every UI call site (`MovieDetailsScreen.kt`, `EpisodeSelectionScreen.kt` on both platforms) is fire-and-forget — none of them check the return value or could show eviction feedback even if they wanted to. (Watch history's identical `.take()` pattern is fine by contrast — it's an LRU activity list by design, not a curated collection the user explicitly built.)
**Trigger:** favorite more than `favoritesMaxSize` items (100 by default) — a real scenario for any engaged long-term user, not an edge case.
**Impact:** the user's oldest explicitly-curated favorite is silently deleted with zero indication anything was removed — surprising, silent data loss of a deliberate user action.
**Fix:** simplest version — after the `.take()`, compare sizes; if an eviction occurred, surface a toast ("X removed from favorites to make room") or block the add entirely with an error instead of silently evicting.
**Complexity:** Easy–Moderate — the detection itself is a one-line size comparison; the UX decision (toast vs. block vs. raise the default cap) needs a call, and threading a signal back to the fire-and-forget UI call sites needs minor plumbing.
**Skipped:** deprioritized at the user's direction — not investigated as unreachable like #4/#7. The bug analysis above still stands as confirmed; this is just not being picked up right now.

### 18. [FIXED] Episode season auto-expand can clobber the user's manual accordion choice
**File:** `tv/src/main/java/org/njarasoa/fijerena/feature/episode/EpisodeSelectionScreen.kt` (`expandedSeasons` state lines 298-302, auto-expand `LaunchedEffect(seriesDetail)` lines 305-332, manual toggle `onToggle` lines 492-499+)

`expandedSeasons` initializes synchronously to the first season. A `LaunchedEffect(seriesDetail)` then runs an async DB lookup (`mediaRepository.getPlaybackPositionsSuspend(allEpisodeIds, ContentType.TV_SHOWS)`, line 316) and, once it resolves, unconditionally overwrites `expandedSeasons` to whichever season has the next incomplete episode (line 327: `expandedSeasons = setOf(season.seasonNumber)`). The manual season-header toggle (`onToggle`, lines 492-499) writes to that exact same state variable with no coordinating flag — there's nothing that marks "the user already manually chose a season" to stop the auto-expand effect from overwriting it once the async lookup finishes.
**Trigger:** open a series with multiple seasons, then tap to expand a different season before the playback-position lookup resolves (plausible on a slower device, or simply a quick double-tap right after the screen loads).
**Impact:** the accordion silently snaps back to the auto-detected season, undoing the user's just-made manual choice — confusing, looks like a UI glitch.
**Fix:** add a "user has manually toggled this session" flag, set by `onToggle`, checked by the `LaunchedEffect` before it overwrites `expandedSeasons`.
**Complexity:** Easy — one boolean flag and a guard check.
**Resolved:** added `hasManuallyToggledSeasons` (reset alongside `expandedSeasons` via `remember(seriesDetail)`), set to `true` in `onToggle`, checked by the auto-expand `LaunchedEffect` right before it would overwrite `expandedSeasons`. Mobile's `EpisodeSelectionScreen.kt` had the identical pattern (confirmed: same accordion state, same async-lookup-then-overwrite shape, just per-episode lookups instead of one batched call) and got the same fix, even though the original write-up only cited TV.

## Low impact / quick wins

### 8. [FIXED] Live-retry path drops bandwidth telemetry
**File:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt` (`attemptLiveRetry()` lines 468–474 vs. `playStream()` lines 414–420)

`playStream()` passes `transferListener = bandwidthMeter` into `createMediaSource()`; the retry path inside `attemptLiveRetry()` rebuilds the media source without that parameter.
**Impact:** bandwidth/quality-adaptation telemetry silently stops being fed during a live retry, until the user/auto-retry eventually calls `playStream()` again.
**Fix:** one-line addition — pass `transferListener = bandwidthMeter` in the retry path too.
**Complexity:** Trivial — one named argument added to an existing call.

### 9. [FIXED] PlaybackViewModel updates UI metadata before the service is told to play it
**File:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt` (`playStream()`, lines 119–134)

`_currentMetadata.value = metadata` (line 126) runs synchronously, before the `viewModelScope.launch { StreamingPlaybackService.awaitInstance().playStream(...) }` block (lines 130–133) even starts. For a brief window, UI state describes a stream the service hasn't been asked to load yet.
**Impact:** mostly cosmetic; on rapid channel-switching, anything keyed off `currentMetadata` could briefly disagree with what's actually playing.
**Fix:** move the `_currentMetadata` assignment inside the launched coroutine, alongside the `service.playStream(...)` call.
**Complexity:** Trivial — move one assignment inside an existing `launch` block.

### 10. [FIXED] Cursor can leak in EpgIndexDatabase.onOpen()
**File:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/epgindex/EpgIndexDatabase.kt` (lines 64–66)

`db.query("PRAGMA auto_vacuum")` is closed manually after `moveToFirst()` rather than via `.use { }`; if `moveToFirst()` throws, the cursor is never closed (the surrounding catch at line 70 swallows the exception and moves on). Narrow/edge-case (only triggers on an actual exception there), but the fix is trivial and the rest of the function already uses `.use{}`-style cleanup for the adjacent PRAGMA queries (lines 61–62).
**Fix:** wrap in `.use { }` like the other PRAGMA queries in the same function.
**Complexity:** Trivial — wrap an existing query in `.use { }`, matching the pattern already used two lines above it.

### 11. [FIXED] Listener cleanup in onDestroy() is asymmetric
**File:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt` (`onDestroy()`, lines 759–788, specifically 767–775)

`player.removeListener(playerListener!!)` is called before release, but there is no matching `player.removeAnalyticsListener(analyticsListener!!)` — `analyticsListener` is just set to `null` (line 775) without ever being detached from the player. `playerListener` itself is also never nulled out (only `analyticsListener` is). No crash results (the analytics callback null-checks the destroyed service singleton before touching it), but it's a latent leak / cleanup-symmetry bug that's easy to get wrong again if this code is touched.
**Fix:** add the missing `removeAnalyticsListener` call and null out both listener fields, matching the existing pattern.
**Complexity:** Trivial — one added call plus nulling two fields, mirroring the existing `playerListener` cleanup.

### 19. [FIXED] EpgViewModel.forceRefresh()'s isRefreshing flag is decoupled from the actual reload
**File:** `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/EpgViewModel.kt` (`forceRefresh()` lines 148-156, `loadEpgData()` lines 78-85)

`loadEpgData()` is fire-and-forget — it does `viewModelScope.launch { ... }` and returns immediately, it does not suspend until the load finishes. `forceRefresh()` calls it as its second-to-last statement: `_isRefreshing.value = true; repository.clearEpgCache(); repository.clearXmltvCache(); loadEpgData(currentDate); _isRefreshing.value = false`. Since `loadEpgData()` only *schedules* a new, separate coroutine, `_isRefreshing.value = false` runs immediately after, not after the reload actually completes.
**Trigger:** tap "force refresh" on the EPG grid — ordinary usage.
**Impact:** the refreshing spinner/indicator disappears almost immediately while the actual cache-clear + network fetch + EPG rebuild is still running in an untracked coroutine — the UI looks done when it isn't, and `loadEpgDataInternal()`'s own `UiState.Loading` transition races against whatever the screen does when `isRefreshing` flips back to false.
**Fix:** have `forceRefresh()` call `loadEpgDataInternal(currentDate)` directly (the private, properly-suspending function) instead of through the fire-and-forget public `loadEpgData()` wrapper, so `_isRefreshing.value = false` only runs after the reload actually completes.
**Complexity:** Trivial — swap which function is called inside the existing coroutine.
**Resolved:** swapped the call to `loadEpgDataInternal(currentDate)`, preserving the same lazy-`repository`-init guard `loadEpgData()` had (`if (!::repository.isInitialized) { ... }`) since `forceRefresh()` no longer goes through that wrapper to get it for free.

### 20. [FIXED] XtreamSyncWorker never updates provider sync stats
**File:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/XtreamSyncWorker.kt` (`doWork()`, full file)

`XtreamSyncWorker` is the class actually scheduled by `ProviderSyncManager.updateWorkManagerSchedule()` for the periodic 24h background sync — confirmed via `PeriodicWorkRequestBuilder<XtreamSyncWorker>(24, TimeUnit.HOURS)`. Its `doWork()` calls `mediaProvider.syncAll()` for every Xtream provider but never calls `providerRepo.updateSyncStats(...)` anywhere — contrast with `ProviderSyncManager.performFullSync()`/`startManualSync()`, both of which call it in a `finally` block.
**Trigger:** rely on background sync (the default/primary path on mobile) rather than manually tapping "Sync Now."
**Impact:** `lastSyncedAtMs`/`lastSyncError` shown in Settings go permanently stale even though background syncs are succeeding — purely a misleading-status bug, no functional/data impact.
**Fix:** wrap the per-provider sync in a `try`/`finally` that calls `providerRepo.updateSyncStats(provider.id, endTime, duration, syncError)`, mirroring `ProviderSyncManager`'s pattern exactly.
**Complexity:** Trivial — copy the existing stats-update pattern from `ProviderSyncManager` into this worker's loop.
**Resolved:** wrapped the per-provider sync in `try`/`finally`, calling `providerRepo.updateSyncStats(provider.id, endTime, endTime - startTime, syncError)` on every path (success, "Failed to connect," and exception) — matches `ProviderSyncManager.startManualSync()`'s pattern exactly.

### 21. [FIXED] TV EPG grid's "now" highlight is wrong on any date other than today
**File:** `tv/src/main/java/org/njarasoa/fijerena/feature/epg/EpgGridLayout.kt:430`, `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/EpgViewModel.kt` (`calculateCurrentTimeSlot()` lines 263-267)

`calculateCurrentTimeSlot()` returns `0` (not a "no match" sentinel like `-1`) whenever real wall-clock "now" doesn't fall inside any of the selected date's 48 time slots — i.e., whenever the selected date isn't today. `EpgGridLayout.kt:430`'s `val isCurrent = index == currentTimeSlot` has no date check, so slot 0 (12:00-12:30 AM) gets visually highlighted as "current" on every non-today date. (The auto-scroll-to-now effect at `EpgGridLayout.kt:155-156` happens to guard on `currentTimeSlot > 0`, so it doesn't also auto-scroll to slot 0 on non-today dates — only the highlight is affected. Confirmed mobile does not share this bug: `MobileEpgTimeline.kt:215` computes its current-slot indicator via an absolute epoch-range comparison, which correctly evaluates false on any non-today date.)
**Trigger:** tap "Next Day" or "Previous Day" once in the TV EPG Guide — ordinary usage.
**Impact:** purely cosmetic — the wrong time slot is bolded/highlighted as "now" on every non-today date, but nothing else is affected (no wrong data is fetched or shown, just a visual indicator).
**Fix:** have `calculateCurrentTimeSlot()` return `-1` when there's no real match, and check `currentTimeSlot >= 0` (not just `index == currentTimeSlot`) before applying the "current" highlight in `EpgGridLayout.kt`.
**Complexity:** Trivial–Easy — two small, localized changes, same file pair already named.
**Resolved:** `calculateCurrentTimeSlot()` now just returns `timeSlots.indexOfFirst { ... }` directly — `indexOfFirst` already returns -1 on no match, so the previous `if (index >= 0) index else 0` wrapper was actively converting "no match" into "slot 0." `EpgGridLayout.kt`'s highlight check is now `currentTimeSlot >= 0 && index == currentTimeSlot`. The existing auto-scroll guard (`currentTimeSlot > 0`) already excluded -1 correctly with no change needed.

---

## Investigated, ruled out

Agent-reported leads from both sweeps that didn't hold up on direct
verification — recorded so they aren't re-investigated later.

- TV's D-pad channel-up/down already correctly gates on `repeatCount == 0` (`PlayerKeyHandler.kt:76,98`) — holding the remote does not spam channel changes.
- Mobile's `PlayerView` is correctly memoized via unkeyed `remember {}` (`MobilePlayerScreen.kt:401`) — not recreated per recomposition.
- TV's `StatsOverlay.kt` already shows the live stream-health row, same as mobile — no parity gap.
- `AdaptiveLoadControl`'s cached `lastPreparedPlayerId`/`lastTracksSelected` fields are only ever touched from the single playback-looper thread — no data race despite not being `@Volatile`.
- `autoRetryAttempted`'s apparent "lockout" never sticks — the very `playStream()` call it triggers resets the flag synchronously before the function that set it returns.
- `episodeId.toInt()` in `XtreamContentManager.buildEpisodeStreamUrl()` can't crash the caller — it's wrapped in `resultOf { }`, which already catches `Exception` (a superclass of `NumberFormatException`).
- `EpgSyncWorker`'s state check after `processAllSources()` isn't a race — that call is a structured suspend call, not fire-and-forget, so the state is already final by the time it's read.
- Both flagged timezone-format issues are non-issues — `TimeZone.getTimeZone("GMT+0500")` is explicitly valid syntax per the `TimeZone` javadoc (`"GMT+0010"` is its own literal example).
- `CategoryViewModel`'s `.filterValues { it != null }.mapValues { it.value!! }` is safe — every entry has already passed the non-null filter.
- `StreamLoaderViewModel.retryLastLoad()`'s `lastLoadRequest!!` and `SearchViewModel.ensureRepo()`'s `repository!!` are both unreachable-while-null in practice (no concurrent mutation, no suspension point between the check and the use).
- `EpgSourceEntity.providerId`'s nullability is intentional (null = "applies to all providers"), and is already handled correctly in the DAO's `IS NULL OR =` query.
- `RemoteM3uMediaProvider`'s `connection.disconnect()` ordering is fine — the stream is fully consumed via `.use { }` before the `finally` block that disconnects ever runs.

## Lower-confidence / informational

- **EPG timezone handling**: `EpgIndexer`/`XmltvParser` apply a per-source `timezoneOffsetHours` at parse time rather than converting at query time. Config-dependent, not a code bug per se — if a source's offset is ever misconfigured, programme times would be wrong app-wide for that source, but no logic error was found in the conversion itself. Worth a spot-check against a real XMLTV source's stated timezone if program times are ever reported as off.

### Leads from the third sweep, reported by agents but not personally re-verified

These came back from the same fork-based sweep that produced #12-#21 above, with reasonable-sounding evidence, but ran out of verification budget this round — recorded so they aren't lost, not yet trusted as confirmed:

- **`MovieDetailsViewModel`/`SeriesDetailsViewModel.toggleFavorite()` lost-update race on rapid double-tap** — both read `_uiState.value` once at coroutine start, do an async repo write, then write back based on that stale snapshot; two taps close together could both see the same starting `isFavorite` and both take the same branch instead of toggling. `CategoryViewModel.toggleFavoriteCategory()`/`toggleFavoriteStream()` re-check the repo fresh right before deciding, which the fork flagged as the correct pattern these two don't follow.
- **`LocalFileScanner`'s `DocumentFile` fallback path generates unstable `MediaItem` IDs** (`local/LocalFileScanner.kt`) — the two main scan paths bake a content-derived hash into the ID; the legacy `DocumentFile` fallback (used when the optimized scan throws) allegedly uses pure scan-position (`"local_file_$index"`), which would reassign IDs — and orphan watch history/favorites — on any rescan that walks files in a different order.
- **`SettingsExportManager` imported favorites ignore each provider's individual conflict-resolution choice** — the providers loop and the favorites loop allegedly run independently, so a provider the user chose to `SKIP` could still gain merged favorites, and a `DUPLICATE`d provider could end up with none (they go to the original instead).
- **`ProviderSyncManager`/`XtreamSyncWorker` TOCTOU on `connect()`** — two concurrent sync callers can both observe "not connected" and both call `connect()`/`restoreSession()`. Mostly superseded by today's RefreshQueue dedup fix at the data-sync level; this is about the connection step specifically, lower severity than originally suspected.
- **`RemoteM3uMediaProvider` concurrent `connect()` calls race on the same temp file path** (`remote/RemoteM3uMediaProvider.kt`) — two callers seeing a stale cache at once would both download to the same fixed temp-file path and race on the rename-over-cache step.
- **`SmbClient.connect()` doesn't close existing resources before overwriting them** (`smb/SmbClient.kt`) — a second `connect()` without an intervening `disconnect()` would leak the old `SMBClient`/`Connection`/`Session`. Current call-site reachability not traced.
- **`AdaptiveLoadControl.onTracksSelected()` skips the `replayIfPending()` call** that the class's other two delegate-forwarding methods have — could invert callback ordering after a LoadControl hot-swap if `onTracksSelected` happens to fire first, but unclear whether `DefaultLoadControl` actually misbehaves from this (vs. being harmlessly idempotent).
- **`LocalUiScale` re-provided via a non-reactive one-time snapshot in 7 TV screens** (`EpisodeSelectionScreen.kt` and 6 others), shadowing the reactive global value from `MainActivity.kt` — same narrow split-screen-only reachability class as already-skipped #4/#7, so likely not worth fixing even if confirmed.
- **Possible one-frame wrong-theme flash on cold start** — speculative, the reporting agent did not trace how `themeId` is actually threaded from `AppSettings` into the theme composable before flagging this, so treat as unconfirmed until someone does.
