# Known Bugs — Prioritized Plan

Merged from two prior sweeps: a "watching experience" sweep (EPG subsystem,
content-browsing/favorites/history, mobile gesture/rotation) and a broader
audit of `core/player`, `core/network`, and `core/ui`/`mobile`/`tv` (DI,
ViewModels, navigation, EPG pipeline). Both sweeps used Explore agents to
generate candidates, then personally verified every lead against the actual
source before recording it — agent output is a hypothesis, not ground truth,
and a substantial fraction of raw candidates from both sweeps turned out to
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
| 4 | ProviderViewModel sync-status overwrite | Moderate–Involved |
| 5 | Mobile gesture (self-mutation + missing guard) | Easy–Moderate |
| 6 | [FIXED] First ~10s of playback not saved | Trivial |
| 7 | EpgBrowserViewModel stale pager after Clear All Data | Involved |
| 8 | [FIXED] Live-retry drops bandwidth telemetry | Trivial |
| 9 | [FIXED] PlaybackViewModel metadata-before-service race | Trivial |
| 10 | [FIXED] EpgIndexDatabase cursor leak | Trivial |
| 11 | [FIXED] onDestroy() listener cleanup asymmetry | Trivial |

Quick-win batch (one small PR, low review risk): #6, #8, #9, #10, #11 — all
fixed.
Need a bit more investigation before sizing precisely: #4 and #7 — both
depend on UI/consumer call sites not yet traced (see their entries below).

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

### 4. ProviderViewModel's sync-status tracking is overwritten by a second concurrent sync
**File:** `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/ProviderViewModel.kt` (`syncProvider()` lines 347–355, completion check in `loadProviders()` lines 151–174)

`syncingProviderId` is a single nullable `var` with no guard in `syncProvider()` against a sync already being in flight. If the user starts syncing provider A (`syncingProviderId = A`), then starts syncing provider B before A finishes, `syncingProviderId` is silently reassigned to B. When A's sync later completes and updates its DB row, the `providers` Flow re-emits and the completion-check in `loadProviders()` looks up `syncingProviderId` (now B) — so A's completion is never reported, and B can get a stale/spurious "completed" status reported off an old `lastSyncedAtMs` that has nothing to do with the in-flight sync.

**Trigger:** sync two different providers in close succession (multi-provider users only).
**Impact:** wrong or missing sync success/error feedback shown to the user.
**Fix:** track sync state per provider ID (`Map<Long, SyncState>` instead of a single `var`), or disable starting a new sync while one is already in progress.
**Complexity:** Moderate–Involved — the ViewModel-side change is mechanical, but it's unverified whether `_syncState` is consumed as a single global value by UI (e.g. one spinner for "the" sync) — if so, every consumer needs updating too. Needs a quick check of UI call sites before sizing precisely.

## Moderate impact

### 5. Mobile swipe gesture for channel-switch/category-panel can flicker, drop input mid-swipe, or double-fire
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

### 6. [FIXED] First ~10 seconds of every playback session are never saved to watch history
**File:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt` (`PlayerListener`, lines 790–819, save gate at lines 798–816)

`lastSavedPosition` starts at `0L`, and the periodic save in `onPlaybackStateChanged()` only fires when `abs(currentPosition - lastSavedPosition) >= saveIntervalMs (10_000) || isPaused`. For a freshly started, uninterrupted playback session, no save happens until either 10 seconds of playback have elapsed or the user pauses.

**Trigger:** play a short clip/trailer under 10 seconds, or have the app/device killed within the first 10 seconds of any stream without pausing first.
**Impact:** watch-history/auto-resume position for that session is lost — falls back to whatever (if anything) was last persisted, defeating resume.
**Fix:** seed `lastSavedPosition` so the very first `STATE_READY` transition (or the `playStream()` call itself) performs an immediate save, not just on the periodic/pause boundary.
**Complexity:** Trivial — one-line fix: seed `lastSavedPosition = -saveIntervalMs` instead of `0L` so the very first `STATE_READY` always clears the save-gate. No structural change.

### 7. EpgBrowserViewModel's "Now Playing" pager is never rebuilt after Clear All EPG Data
**File:** `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/EpgBrowserViewModel.kt` (`loadNowPlaying()`, lines 314–338; same pattern in the search pager around lines 456–480)

`loadNowPlaying()` calls `EpgIndexDatabase.getInstance(context)` once, captures the resulting `dao` in a `Pager { dao.getPagedNowPlaying(...) }` closure, and caches the flow for the ViewModel's lifetime via `.cachedIn(viewModelScope)`. `EpgManagementViewModel` deliberately guards against exactly this class of bug with its `_dbGeneration` counter (re-subscribing Room flows after a DB destroy+recreate from "Clear All EPG Data") — but `EpgBrowserViewModel` has no equivalent mechanism. If "Clear All EPG Data" destroys and recreates the database while the Now Playing pager is alive, every subsequent page load queries a DAO bound to a closed `SupportSQLiteDatabase`.

**Trigger:** open the EPG browser's Now Playing/search view, then run "Clear All EPG Data" from EPG management while that view is still active or paged — a narrower window than the other bugs in this tier.
**Impact:** paging errors / stuck "Now Playing" list until the screen is fully re-created; no crash observed downstream since Paging3 surfaces load failures as `LoadState.Error`, but the feature breaks until next navigation.
**Fix:** mirror `EpgManagementViewModel`'s generation-counter pattern, or have `EpgIndexDatabase.destroy()` broadcast an invalidation event this ViewModel observes to rebuild its pagers.
**Complexity:** Involved — the most architecturally heavy fix in this list. `EpgManagementViewModel`'s `_dbGeneration` counter is scoped to that ViewModel; fixing this properly means either promoting an invalidation signal up into `EpgIndexDatabase` itself so any consumer can react, or duplicating the generation-counter pattern locally. Also needs verifying that hot-swapping the inner `Flow<PagingData<...>>` inside `_pagedNowPlaying`/`_pagedSearchResults` is actually picked up by whatever composable collects it (`collectAsLazyPagingItems()`) — not yet verified.

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
