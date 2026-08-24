# Codebase Audit Fix Plan

**Date:** 2026-08-24
**Scope:** Full codebase audit (~367 Kotlin files, ~80K LOC)
**Status:** T1 (7 fixes), T3 (9 fixes), and T4 (7 items) applied and build-verified ✅ — T2 remains

---

## Priority Matrix

Findings are grouped into 4 tiers by crossing **risk** (data loss, crash, corruption, UX degradation) with **complexity** (LOC changed, number of files touched, testing surface).

| Tier | Risk | Complexity | Action |
|------|------|------------|--------|
| **T1** | High risk | Low complexity | Fix immediately — high ROI |
| **T2** | High risk | Medium–High complexity | Fix this week — needs design |
| **T3** | Medium risk | Low complexity | Fix next sprint |
| **T4** | Low risk | Any | Backlog |

---

## T1 — High Risk / Low Complexity (fix immediately)

These are the highest-value fixes: dangerous bugs that require minimal code changes.

### T1-1. EPG Database Corruption on Pipeline Cancellation

**Risk:** Database left with dropped FTS triggers, dropped indexes, `PRAGMA synchronous = OFF`
**Files:** `core/network/.../xmltv/EpgFileManager.kt` (L693–697, L879–883)
**Complexity:** ~4 lines changed

When the EPG sync coroutine is cancelled, the `catch (e: Exception)` block catches `CancellationException` and calls the suspend function `endBulkIngestion()`. Inside a cancelled coroutine, this immediately re-throws without executing cleanup. Zero uses of `NonCancellable` exist in the file.

```kotlin
// Current — endBulkIngestion() silently aborts
} catch (e: Exception) {
    EpgIndexer.getInstance(context).endBulkIngestion()
}

// Fix — wrap in NonCancellable
} catch (e: Exception) {
    withContext(NonCancellable) {
        EpgIndexer.getInstance(context).endBulkIngestion()
    }
    if (e is CancellationException) throw e
}
```

Both catch blocks (L695 and L881) need the same fix.

---

### T1-2. `serviceStartRequested` Never Resets — Blank Screen

**Risk:** Player screen goes permanently blank after Activity recreation within the same process
**Files:** `core/player/.../viewmodel/PlaybackViewModel.kt` (L106, L462)
**Complexity:** ~1 line changed

Static `AtomicBoolean` is set to `true` once and never reset. After Activity destruction + recreation (config change, system kill), `startService()` no-ops forever → `awaitInstance()` hangs.

```kotlin
// Fix: reset in onCleared() or when service dies
override fun onCleared() {
    super.onCleared()
    serviceStartRequested.set(false)
    // ... existing cleanup
}
```

---

### T1-3. `_streamRetryCount.value++` Is Not Atomic

**Risk:** Lost retry count increments → incorrect retry state, misleading stats overlay
**Files:** `core/player/.../service/StreamingPlaybackService.kt` (L181, L484, L528, L553)
**Complexity:** 4 one-line changes

`MutableStateFlow.value++` is a non-atomic read-modify-write. Concurrent renderer errors lose updates.

```kotlin
// Current
_streamRetryCount.value++

// Fix
_streamRetryCount.update { it + 1 }
```

---

### T1-4. Watch History / Favorites Lost to Concurrent Writes

**Risk:** User loses watch history or favorites after rapid playback events
**Files:** `core/network/.../xtream/manager/XtreamUserDataManager.kt` (L143–171, L213–228, L369–381)
**Complexity:** ~10 lines — add a Mutex around read-modify-write blocks

`addToWatchHistory()`, `addFavorite()`, `clearPlaybackPosition()` all perform unsynchronized read → modify → write on SharedPreferences JSON. Rapid concurrent calls from ExoPlayer position callbacks overwrite each other.

---

### T1-5. `favoriteIdSet` / `favoriteCategoryIdSet` Unsynchronized

**Risk:** `ConcurrentModificationException` or silent favorite toggle failures
**Files:** `core/network/.../MediaRepository.kt` (L137–138, L624+)
**Complexity:** ~6 lines — protect with existing `watchHistoryLock` or dedicated lock

Lazy `HashSet` caches mutated from concurrent contexts with no locking (unlike `watchHistoryLock` which protects watch history).

---

### T1-6. `EncryptedSharedPreferences.create()` Race

**Risk:** `SecurityException` or MasterKey corruption on concurrent provider access
**Files:** `core/network/.../provider/ProviderRepository.kt` (L322–330)
**Complexity:** 1 line — replace `getOrPut` with `computeIfAbsent`

`ConcurrentHashMap.getOrPut()` does not lock during the lambda. Multiple threads creating encrypted prefs for a new provider simultaneously will call `EncryptedSharedPreferences.create()` concurrently.

```kotlin
// Current
encryptedPrefsCache.getOrPut(providerId) { EncryptedSharedPreferences.create(...) }

// Fix
encryptedPrefsCache.computeIfAbsent(providerId) { EncryptedSharedPreferences.create(...) }
```

---

### T1-7. RefreshQueue Deduplication Gap

**Risk:** Duplicate concurrent EPG sync tasks for the same source
**Files:** `core/network/.../queue/RefreshQueue.kt` (L98–131)
**Complexity:** ~5 lines — keep mutex locked across poll + activeTask insertion

Between polling from `queue` (under mutex) and inserting into `activeTasks` (separate mutex acquisition), a concurrent `submit()` won't find the task in either collection, bypassing deduplication.

---

## T2 — High Risk / Medium–High Complexity (fix this week)

These require more design thought or touch multiple files.

### T2-1. MediaRepository Leaks OS Threads on Provider Switch

**Risk:** Orphaned `HandlerThread` + `CoroutineScope` per provider switch — unbounded thread growth
**Files:** `core/network/.../MediaRepository.kt` (L141, L153) + `core/ui/.../di/AppContainer.kt`
**Complexity:** Medium — add `Closeable` interface, call from AppContainer eviction path

`MediaRepository` starts a `HandlerThread("WatchHistoryWriter")` and creates a `CoroutineScope(SupervisorJob())` on construction but has no `close()` method. AppContainer evicts repositories without cleanup.

**Approach:**
1. Add `fun close()` to `MediaRepository` that calls `watchHistoryWriteThread.quitSafely()` and `writeScope.cancel()`
2. Call `close()` from `AppContainer` before evicting a cached repository

---

### T2-2. EPG Temp Files Leaked on Pipeline Cancellation

**Risk:** Disk space leak on every cancelled EPG sync
**Files:** `core/network/.../xmltv/EpgFileManager.kt` (L542–626)
**Complexity:** Medium — needs channel drain in finally block

Downloaded files sent to `ingestionQueue` are deleted by consumer jobs in `finally`. If the parent scope is cancelled, consumer jobs die immediately, leaving `DownloadedSource.tmpFile` on disk.

**Approach:**
Add a `finally` block that drains remaining items from the channel and deletes their temp files.

---

### T2-3. `AppContainer.getMediaRepository()` Holds Global Lock During Network Call

**Risk:** ANR / UI freeze — one slow provider blocks all repository access
**Files:** `core/ui/.../di/AppContainer.kt` (L78)
**Complexity:** Medium — needs per-provider locking or lock-free connect pattern

The class-wide `Mutex` is held while calling `repo.connect()` (network IO). A stalled connection blocks all other coroutines.

**Approach:**
Option A: Per-provider `Mutex` map.
Option B: Release the global mutex before `connect()`, re-acquire to store result. Handle the double-init race.

---

### T2-4. `CinemaColors` Not Compose-Reactive

**Risk:** Stale UI colors after dynamic theme change — gradient brushes, default modifiers show old theme
**Files:** `core/ui/.../theme/CinemaColors.kt`, `CinemaThemeHolder.kt` + all consumers
**Complexity:** Medium-High — need to introduce `CompositionLocal` or `State<CinemaThemePalette>` and update all callsites

Global `CinemaColors` properties delegate to `CinemaThemeHolder.current` (`@Volatile var`), which is invisible to Compose's snapshot system.

---

### T2-5. Stats Overlay Full Recomposition Every Second

**Risk:** Dropped frames on mid-range TV chipsets (Sony Bravia) when stats overlay is active
**Files:** `tv/.../ui/player/components/overlays/TvStatsOverlay.kt` (L140–285)
**Complexity:** Medium — needs state scoping refactor

A `while(true)` loop writes ~15 top-level `var`s every 1000ms. All are read at root layout scope → entire ~600-line overlay recomposes every tick.

**Approach:**
Group related stats into nested composable functions that isolate state reads, or use a single `data class StatsSnapshot` with `derivedStateOf`.

---

### T2-6. Position Save Listener Leaked from Docked Mini-Player

**Risk:** Leaked `loaderViewModel` reference in singleton service after dock demotion
**Files:** `mobile/.../feature/player/MobilePlayerScreen.kt` (L312–315)
**Complexity:** Medium — listener lifecycle needs to move into `MobilePlayerContent` itself

`MobilePlayerContent` registers a `setPositionSaveListener` in a `LaunchedEffect` but only the standalone wrapper has the `onDispose` cleanup. The docked path uses `MobilePlayerContent` directly.

**Approach:**
Move the listener registration + cleanup into a `DisposableEffect` inside `MobilePlayerContent`.

---

## T3 — Medium Risk / Low Complexity (fix next sprint)

### T3-1. `StreamingMediaSourceFactory` Shared Instance Mutated Concurrently

**Files:** `core/player/.../source/StreamingMediaSourceFactory.kt` (L53–60)
**Complexity:** Small — create a new factory per call or synchronize

The cached `DefaultMediaSourceFactory` has its data source factory and error policy mutated before each `createMediaSource()`. Concurrent calls (preview + main) cross-contaminate settings.

---

### T3-2. Gesture Coroutine Killed on Every Playback State Change

**Files:** `mobile/.../feature/player/MobilePlayerScreen.kt` (L415–420)
**Complexity:** Trivial — remove `playbackState::class` from `pointerInput` key, use `rememberUpdatedState`

---

### T3-3. Search `focusRequesters` Map Mutated During Composition

**Files:** `tv/.../feature/search/SearchScreen.kt` (L559)
**Complexity:** Small — build map outside composition or use per-item `remember`

`mutableMapOf` mutated via `getOrPut` inside `TvLazyColumn` composition. Risk of `ConcurrentModificationException`.

---

### T3-4. Channel List Overlay Uses `onKeyEvent` Instead of `BackHandler`

**Files:** `tv/.../ui/player/components/overlays/TvChannelListOverlay.kt` (L81–88)
**Complexity:** Trivial — replace with `BackHandler(enabled = isVisible) { onDismiss() }`

Raw `onKeyEvent` for Back consumes the event before the system `OnBackPressedDispatcher`, bypassing global back handlers.

---

### T3-5. Focus Request With Hardcoded `delay(100)` — Fails on Slow Devices

**Files:** `tv/.../ui/player/components/overlays/TvChannelListOverlay.kt` (L65), TvPlayerControlsOverlay
**Complexity:** Small — use `awaitFrame()` or `Modifier.onGloballyPositioned`

On slow devices (Sony Bravia), layout may take >100ms. The focus request fires into an unplaced node, the exception is swallowed, and the user is stranded without D-pad controls.

---

### T3-6. Playback State `first()` Hangs Forever on Error

**Files:** `mobile/.../feature/player/MobilePlayerScreen.kt` (L377–379)
**Complexity:** Trivial — add `PlaybackState.Error` to the filter

```kotlin
// Current — hangs if error occurs before Playing/Paused
.filter { it is PlaybackState.Playing || it is PlaybackState.Paused }
.first()

// Fix
.filter { it is PlaybackState.Playing || it is PlaybackState.Paused || it is PlaybackState.Error }
.first()
```

---

### T3-7. SmbClient Unsynchronized Connect/Disconnect

**Files:** `core/network/.../smb/SmbClient.kt` (L32–61)
**Complexity:** Small — add `synchronized(this)` to `connect()` and `disconnect()`

---

### T3-8. Navigation Creates Duplicate ContentTypeSelection

**Files:** `mobile/.../navigation/MobileNavHost.kt` (L360–362, L414–416)
**Complexity:** Small — fix `popUpTo` target

Repeated provider switches grow the backstack unboundedly.

```kotlin
// Fix
navController.navigate(Screen.ContentTypeSelection) {
    popUpTo(Screen.ContentTypeSelection) { inclusive = true }
}
```

---

### T3-9. `onDispose` Launches Into Cancelled Scope

**Files:** `mobile/.../feature/category/MobileCategoryListScreen.kt` (L424–430)
**Complexity:** Small — move work to ViewModel scope

`rememberCoroutineScope` is cancelled when the composable leaves composition. The `launch` in `onDispose` always fails silently.

---

## T4 — Low Risk (backlog)

### T4-1. `recycleHandler` Not Removed in `onDestroy()`

**Files:** `core/player/.../service/StreamingPlaybackService.kt` (L864–894)
**Fix:** Add `mainHandler.removeCallbacks(recycleHandler)` in `onDestroy()`.

### T4-2. `ExhaustionToastWatcher` Polling Loop

**Files:** `core/player/.../service/ExhaustionToastWatcher.kt` (L42–50)
**Note:** Safe if caller uses structured concurrency. Add a defensive check or document the requirement.

### T4-3. Movie Refresh Spinner Never Shown

**Files:** `tv/.../feature/movie/MovieDetailsScreen.kt` (L267–270)
**Fix:** Make `isRefreshing` track the ViewModel's loading state instead of toggling synchronously.

### T4-4. Buffering Detection via Polling Instead of Listener

**Files:** `tv/.../ui/player/PlayerScreen.kt` (L223)
**Fix:** Use `Player.Listener.onPlaybackStateChanged()` instead of `while(true) { delay(1000) }`.

### T4-5. `LaunchedEffect` Key Self-Mutation in Import/Export

**Files:** `mobile/.../feature/settings/MobileSettingsScreen.kt` (L80–117)
**Note:** Functionally harmless but causes wasteful recomposition. Refactor to event-based pattern.

### T4-6. Toast Collection Not Lifecycle-Aware

**Files:** `mobile/.../feature/epg/MobileEpgManagementScreen.kt` (L65–71)
**Fix:** Use `collectAsStateWithLifecycle()` or `repeatOnLifecycle`.

### T4-7. `writeScope` CoroutineScope Never Cancelled

**Files:** `core/network/.../MediaRepository.kt` (L153)
**Note:** Subsumed by T2-1 — same root cause. Will be fixed when `close()` is added.

---

## Summary

| Tier | Count | Effort | Timeline |
|------|-------|--------|----------|
| T1 — High risk / Low complexity | 7 | ~30 lines total | Immediate |
| T2 — High risk / Medium+ complexity | 6 | Design needed | This week |
| T3 — Medium risk / Low complexity | 9 | ~50 lines total | Next sprint |
| T4 — Low risk | 7 | Trivial each | Backlog |
| **Total** | **29** | | |
