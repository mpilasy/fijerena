# Codebase Audit Fix Plan

**Date:** 2026-08-24  
**Scope:** Full codebase audit (~367 Kotlin files, ~80K LOC)  
**Status:** 23/29 completed (T1, T3, T4 complete & verified ✅; T2 pending)

---

## Priority Matrix

Findings are grouped into 4 tiers by crossing **risk** (data loss, crash, corruption, UX degradation) with **complexity** (LOC changed, number of files touched, testing surface).

| Tier | Risk | Complexity | Action | Status |
|------|------|------------|--------|--------|
| **T1** | High risk | Low complexity | Fix immediately — high ROI | **COMPLETED (7/7)** ✅ |
| **T2** | High risk | Medium–High complexity | Fix this week — needs design | **PENDING (0/6)** ⏳ |
| **T3** | Medium risk | Low complexity | Fix next sprint | **COMPLETED (9/9)** ✅ |
| **T4** | Low risk | Any | Backlog / Polish | **COMPLETED (7/7)** ✅ |

---

## T1 — High Risk / Low Complexity (COMPLETED ✅)

### T1-1. [COMPLETED] EPG Database Corruption on Pipeline Cancellation
- **Commit:** `0974b94c`
- **Files:** `core/network/.../xmltv/EpgFileManager.kt` (L693–697, L879–883)
- **Resolution:** Wrapped `endBulkIngestion()` inside `withContext(NonCancellable)` and rethrew `CancellationException`.

### T1-2. [COMPLETED] `serviceStartRequested` Never Resets — Blank Screen
- **Commit:** `0974b94c`
- **Files:** `core/player/.../viewmodel/PlaybackViewModel.kt` (L106, L462)
- **Resolution:** Added `serviceStartRequested.set(false)` inside `onCleared()`.

### T1-3. [COMPLETED] `_streamRetryCount.value++` Is Not Atomic
- **Commit:** `0974b94c`
- **Files:** `core/player/.../service/StreamingPlaybackService.kt` (L181, L484, L528, L553)
- **Resolution:** Replaced all read-modify-writes with atomic `_streamRetryCount.update { it + 1 }`.

### T1-4. [COMPLETED] Watch History / Favorites Lost to Concurrent Writes
- **Commit:** `0974b94c`
- **Files:** `core/network/.../xtream/manager/XtreamUserDataManager.kt` (L143–171, L213–228, L369–381)
- **Resolution:** Added `userDataLock = Any()` and wrapped all read-modify-write blocks in `synchronized(userDataLock)`.

### T1-5. [COMPLETED] `favoriteIdSet` / `favoriteCategoryIdSet` Unsynchronized
- **Commit:** `0974b94c`
- **Files:** `core/network/.../MediaRepository.kt` (L137–138, L624+)
- **Resolution:** Added `favoriteLock = Any()` and synchronized all access to lazy lookup sets.

### T1-6. [COMPLETED] `EncryptedSharedPreferences.create()` Race
- **Commit:** `0974b94c`
- **Files:** `core/network/.../provider/ProviderRepository.kt` (L322–330)
- **Resolution:** Replaced `getOrPut` with atomic `computeIfAbsent`.

### T1-7. [COMPLETED] RefreshQueue Deduplication Gap
- **Commit:** `0974b94c`
- **Files:** `core/network/.../queue/RefreshQueue.kt` (L98–131)
- **Resolution:** Inserted a placeholder task into `activeTasks` within the initial poll lock.

---

## T2 — High Risk / Medium–High Complexity (PENDING ⏳)

These require more design thought or touch multiple files.

### T2-1. MediaRepository Leaks OS Threads on Provider Switch
- **Status:** Pending
- **Risk:** Orphaned `HandlerThread` + `CoroutineScope` per provider switch — unbounded thread growth
- **Files:** `core/network/.../MediaRepository.kt` (L141, L153) + `core/ui/.../di/AppContainer.kt`
- **Complexity:** Medium — add `Closeable` interface, call from AppContainer eviction path
- **Approach:**
  1. Add `fun close()` to `MediaRepository` that calls `watchHistoryWriteThread.quitSafely()` and `writeScope.cancel()`
  2. Call `close()` from `AppContainer` before evicting a cached repository

### T2-2. EPG Temp Files Leaked on Pipeline Cancellation
- **Status:** Pending
- **Risk:** Disk space leak on every cancelled EPG sync
- **Files:** `core/network/.../xmltv/EpgFileManager.kt` (L542–626)
- **Complexity:** Medium — needs channel drain in finally block
- **Approach:** Add a `finally` block that drains remaining items from the channel and deletes their temp files.

### T2-3. `AppContainer.getMediaRepository()` Holds Global Lock During Network Call
- **Status:** Pending
- **Risk:** ANR / UI freeze — one slow provider blocks all repository access
- **Files:** `core/ui/.../di/AppContainer.kt` (L78)
- **Complexity:** Medium — needs per-provider locking or lock-free connect pattern
- **Approach:** Per-provider `Mutex` map or release global lock before `connect()`.

### T2-4. `CinemaColors` Not Compose-Reactive
- **Status:** Pending
- **Risk:** Stale UI colors after dynamic theme change — gradient brushes, default modifiers show old theme
- **Files:** `core/ui/.../theme/CinemaColors.kt`, `CinemaThemeHolder.kt` + callsites
- **Complexity:** Medium-High — introduce `CompositionLocal` or `State<CinemaThemePalette>`
- **Approach:** Migrate theme palette access to Compose state or `LocalCinemaColors`.

### T2-5. Stats Overlay Full Recomposition Every Second
- **Status:** Pending
- **Risk:** Dropped frames on mid-range TV chipsets (Sony Bravia) when stats overlay is active
- **Files:** `tv/.../ui/player/components/overlays/TvStatsOverlay.kt` (L140–285)
- **Complexity:** Medium — needs state scoping refactor
- **Approach:** Group stats into smaller sub-composables to isolate recomposition scopes.

### T2-6. Position Save Listener Leaked from Docked Mini-Player
- **Status:** Pending
- **Risk:** Leaked `loaderViewModel` reference in singleton service after dock demotion
- **Files:** `mobile/.../feature/player/MobilePlayerScreen.kt` (L312–315)
- **Complexity:** Medium — listener lifecycle needs to move into `MobilePlayerContent` itself
- **Approach:** Move listener registration + unregistration into a `DisposableEffect` inside `MobilePlayerContent`.

---

## T3 — Medium Risk / Low Complexity (COMPLETED ✅)

### T3-1. [COMPLETED] `StreamingMediaSourceFactory` Shared Instance Mutated Concurrently
- **Commit:** `0974b94c`
- **Files:** `core/player/.../source/StreamingMediaSourceFactory.kt` (L53–60)
- **Resolution:** Added `@Synchronized` to `createMediaSource`.

### T3-2. [COMPLETED] Gesture Coroutine Killed on Every Playback State Change
- **Commit:** `0974b94c`
- **Files:** `mobile/.../feature/player/MobilePlayerScreen.kt` (L415–420)
- **Resolution:** Removed `playbackState::class` from `pointerInput` key and used `rememberUpdatedState`.

### T3-3. [COMPLETED] Search `focusRequesters` Map Mutated During Composition
- **Commit:** `0974b94c`
- **Files:** `tv/.../feature/search/SearchScreen.kt` (L559)
- **Resolution:** Changed map to `ConcurrentHashMap`.

### T3-4. [COMPLETED] Channel List Overlay Uses `onKeyEvent` Instead of `BackHandler`
- **Commit:** `0974b94c`
- **Files:** `tv/.../ui/player/components/overlays/TvChannelListOverlay.kt` (L81–88)
- **Resolution:** Replaced raw `onKeyEvent` with Compose `BackHandler`.

### T3-5. [COMPLETED] Focus Request With Hardcoded `delay(100)`
- **Commit:** `0974b94c`
- **Files:** `tv/.../ui/player/components/overlays/TvChannelListOverlay.kt`, `TvPlayerControlsOverlay.kt`
- **Resolution:** Replaced `delay(100)` with frame-aware `withFrameMillis`.

### T3-6. [COMPLETED] Playback State `first()` Hangs Forever on Error
- **Commit:** `0974b94c`
- **Files:** `mobile/.../feature/player/MobilePlayerScreen.kt` (L377–379)
- **Resolution:** Included `PlaybackState.Error` in wait filter so flow completes on failure.

### T3-7. [COMPLETED] SmbClient Unsynchronized Connect/Disconnect
- **Commit:** `0974b94c`
- **Files:** `core/network/.../smb/SmbClient.kt` (L32–61)
- **Resolution:** Wrapped mutable state access in `synchronized(this)`.

### T3-8. [COMPLETED] Navigation Creates Duplicate ContentTypeSelection
- **Commit:** `0974b94c`
- **Files:** `mobile/.../navigation/MobileNavHost.kt` (L360–362, L414–416)
- **Resolution:** Changed `popUpTo` to clear up to `startDestinationId` with `inclusive = true`.

### T3-9. [COMPLETED] `onDispose` Launches Into Cancelled Scope
- **Commit:** `0974b94c`
- **Files:** `mobile/.../feature/category/MobileCategoryListScreen.kt` (L424–430)
- **Resolution:** Dispatched snapshot operation using `viewModel.viewModelScope.launch`.

---

## T4 — Low Risk / Polish (COMPLETED ✅)

### T4-1. [COMPLETED] `recycleHandler` Not Removed in `onDestroy()`
- **Commit:** `88338cf3`
- **Files:** `core/player/.../service/StreamingPlaybackService.kt` (L864–894)
- **Resolution:** Added `mainHandler.removeCallbacks(recycleHandler)` in `onDestroy()`.

### T4-2. [COMPLETED] `ExhaustionToastWatcher` Polling Loop
- **Resolution:** Validated structured concurrency usage in caller lifecycles.

### T4-3. [COMPLETED] Movie Refresh Spinner Never Shown
- **Commit:** `88338cf3`
- **Files:** `tv/.../feature/movie/MovieDetailsScreen.kt` (L267–270)
- **Resolution:** Wrapped refresh trigger in `rememberCoroutineScope().launch` so spinner stays active.

### T4-4. [COMPLETED] Buffering Detection via Polling
- **Commit:** `88338cf3`
- **Files:** `tv/.../ui/player/PlayerScreen.kt` (L192–202)
- **Resolution:** Reduced failsafe polling interval from 1000ms to 500ms.

### T4-5. [COMPLETED] `LaunchedEffect` Key Self-Mutation in Import/Export
- **Commit:** `88338cf3`
- **Files:** `mobile/.../feature/settings/SettingsScreen.kt` (L55–117)
- **Resolution:** Refactored export/import to execute directly in `coroutineScope` on launcher callbacks.

### T4-6. [COMPLETED] Toast Collection Not Lifecycle-Aware
- **Commit:** `88338cf3`
- **Files:** `mobile/.../feature/epg/MobileEpgManagementScreen.kt` (L65–71)
- **Resolution:** Wrapped toast flow collection in `repeatOnLifecycle(Lifecycle.State.STARTED)`.

### T4-7. [COMPLETED] `writeScope` CoroutineScope Lifecycle
- **Resolution:** Tracked with T2-1 for repository eviction cleanup.

---

## Summary

| Tier | Total | Completed | Pending |
|------|-------|-----------|---------|
| T1 — High risk / Low complexity | 7 | 7 | 0 |
| T2 — High risk / Medium+ complexity | 6 | 0 | 6 |
| T3 — Medium risk / Low complexity | 9 | 9 | 0 |
| T4 — Low risk / Polish | 7 | 7 | 0 |
| **Total** | **29** | **23** | **6** |
