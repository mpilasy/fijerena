# Systemic Concurrency, Memory Pressure & Stability Remediation Plan

**Status:** Done — Phases 1-3 and most of Phase 4 implemented and committed; one item (Finding 9, `LiveTvSplitLayout`) deliberately skipped, see Phase 4 below  
**Date:** 2026-09-18  
**Scope:** `core:player`, `core:network`, `core:ui`, `tv`, `mobile`

---

## 1. Executive Summary

A comprehensive, ground-up audit of concurrency primitives, memory management, coroutine hierarchies, and lifecycle boundaries was conducted across all modules of Fijerena without reliance on prior plans.

The investigation uncovered **10 critical and high-priority architectural defects**:
1. **Permanent Playback Deadlock & State Black Hole** (`PlaybackViewModel` & `StreamingPlaybackService`): Process-wide static flag prevents service restart after `stopAndRelease()` or Android background stop, permanently hanging `StreamingPlaybackService.awaitInstance()` and killing video playback until process termination.
2. **ExoPlayer Unbounded Memory Buffer & OOM Risk** (`AdaptiveLoadControl` & `NetworkBufferProfile`): 120-second VOD buffer with `setPrioritizeTimeOverSizeThresholds(true)` forces 400MB–1.2GB buffer allocations on 4K/high-bitrate streams, triggering Low Memory Killer (LMK) kills on 1–2GB Android TV devices (Sony Bravia, Chromecast).
3. **SQLite Connection Pool Native RAM Exhaustion** (`EpgIndexDatabase`): Unconditional `PRAGMA cache_size = -64000` (64MB) and `temp_store = MEMORY` on all pooled connections reserves up to 256MB+ of unmanaged native RAM.
4. **`RefreshQueue` Race Condition on Task Dispatch & Cancellation**: Disconnected dummy `Job()` in `activeTasks` allows coroutines to escape cancellation, and unsynchronized task clearing leaves stale IDs in `_activeTaskIds`.
5. **Swallowed Coroutine Cancellations Across Workers & Sync**: Generic `catch (e: Exception)` blocks in `EpgSyncWorker`, `EpgFtsRebuildWorker`, and `XtreamContentManager` swallow `CancellationException`, misclassifying task pauses/timeouts as failures or reporting aborted syncs as successful.
6. **Thread-Unsafe Collections in `XtreamMediaProvider`**: Plain `mutableMapOf` caches (`searchDataSizes`, `tmdbOverviewCache`) mutated across concurrent IO coroutines risk data races and `ConcurrentModificationException`.
7. **Unguarded Database Checkpoint & Vacuum Deadlock** (`EpgIndexer`): `incrementalVacuum()` runs `PRAGMA wal_checkpoint(TRUNCATE)` outside `writeMutex`, risking exclusive SQLite lock contention and database lockups during concurrent ingestion.
8. **Activity Context Leaks via Player Listeners & Factories**: `TvPlayerScreen` captures `StreamLoaderViewModel` and Activity context in `onPositionSaveListener` without clearing it on dispose; `StreamLoaderViewModelFactory` stores raw Activity context.
9. **Conditional Composable Execution & Early Return in `LiveTvSplitLayout`**: Early `if (target == null) return` skips Compose lifecycle observers and ViewModel bindings, violating Compose slot-table stability and single-return principles.
10. **Positional IDs in SMB and M3U Providers**: Items assigned sequential IDs based on list index (`smb_file_${index}`, `${idPrefix}_m3u_$index`), causing watch state and favorites corruption whenever items are added or removed.

---

## 2. Forensic Findings & Root Cause Analysis

### Finding 1: Permanent Playback Deadlock & Stale State Flow in `PlaybackViewModel`
* **Severity:** **P0 - Critical (Showstopper)**
* **Location:**
  - [`core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt#L81-L109)
  - [`core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt#L702-L705)
  - [`tv/src/main/java/org/njarasoa/fijerena/MainActivity.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/MainActivity.kt#L101-L106)
* **Mechanism:**
  1. `PlaybackViewModel` guards service startup with a static companion `AtomicBoolean`:
     ```kotlin
     private val serviceStartRequested = java.util.concurrent.atomic.AtomicBoolean(false)
     ```
     `startService()` only executes if `serviceStartRequested.compareAndSet(false, true)` succeeds.
  2. When TV playback stops or the TV app is backgrounded to the home launcher, `MainActivity.onStop()` calls `StreamingPlaybackService.getInstance()?.stopAndRelease()`.
  3. `stopAndRelease()` calls `releasePlayerAndSession()` followed by `stopSelf()`. In `releasePlayerAndSession()`:
     ```kotlin
     instance = null
     instanceReady.completeExceptionally(CancellationException("StreamingPlaybackService destroyed"))
     instanceReady = CompletableDeferred()
     ```
  4. The Android Service process component is terminated. However, `PlaybackViewModel` remains alive in the `ViewModelStore` of the activity or screen backstack. `serviceStartRequested` remains `true` permanently.
  5. When the user resumes the app and attempts to play any video or live stream, `playStream()` executes:
     ```kotlin
     viewModelScope.launch {
         val service = StreamingPlaybackService.awaitInstance() // Awaits instanceReady.await()
         service.playStream(metadata, resumeFromPosition)
     }
     ```
  6. Because the service is stopped, `instance` is `null`. `awaitInstance()` suspends on the new `CompletableDeferred` `instanceReady`.
  7. No component calls `startService()` (it was only called in `PlaybackViewModel.init`). Even if `startService()` were called, `serviceStartRequested` is already `true`, so it immediately returns without starting the service.
  8. Furthermore, `observeServiceState()` in `PlaybackViewModel` was only invoked once during `init`. When the old service died, its coroutine scopes were cancelled. Even if a service were restarted, `PlaybackViewModel` would never re-subscribe to `service.playbackState`.
* **Impact:** The app permanently hangs on `awaitInstance()`. The user sees an infinite spinner or black screen. Playback is completely broken until the app process is force-killed from Android TV settings.
* **Remediation:**
  1. Introduce `ensureServiceRunning()` in `PlaybackViewModel` that detects if `StreamingPlaybackService.getInstance() == null`, resets `serviceStartRequested`, calls `startService()`, and launches a supervisor coroutine to re-bind `observeServiceState()` and `connectToService()`.
  2. In `StreamingPlaybackService.releasePlayerAndSession()` and `onDestroy()`, reset `serviceStartRequested` state or notify listeners.
  3. Add a safe timeout (e.g. 10 seconds) to `StreamingPlaybackService.awaitInstance()` so coroutines never suspend indefinitely if service startup fails.

---

### Finding 2: ExoPlayer Unbounded Memory Buffer & OOM Crash on Low-RAM Devices
* **Severity:** **P0 - Critical**
* **Location:**
  - [`core/player/src/main/java/org/njarasoa/fijerena/core/player/config/AdaptiveLoadControl.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/src/main/java/org/njarasoa/fijerena/core/player/config/AdaptiveLoadControl.kt#L133-L138)
  - [`core/player/src/main/java/org/njarasoa/fijerena/core/player/config/NetworkBufferProfile.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/src/main/java/org/njarasoa/fijerena/core/player/config/NetworkBufferProfile.kt#L25-L29)
* **Mechanism:**
  1. In `AdaptiveLoadControl.kt`:
     ```kotlin
     return DefaultLoadControl.Builder()
         .setAllocator(sharedAllocator)
         .setBufferDurationsMs(durations.minBufferMs, durations.maxBufferMs, durations.playbackMs, durations.rebufferMs)
         .setBackBuffer(durations.backBufferMs, true)
         .setPrioritizeTimeOverSizeThresholds(true)
         .build()
     ```
  2. In `NetworkBufferProfile.kt`, `WIFI_VOD_MAX_BUFFER_MS = 120_000` (2 minutes).
  3. ExoPlayer's `DefaultLoadControl.shouldContinueLoading` checks `prioritizeTimeOverSizeThresholds`. When set to `true`, ExoPlayer continues buffering data until `maxBufferMs` is satisfied in duration, **completely disregarding any target buffer byte limit**.
  4. On high-bitrate VOD content (such as 4K HEVC streams at 40–80 Mbps), 120 seconds of video requires buffering **600MB to 1.2GB of data into memory**.
  5. Devices like the Sony Bravia (1.5GB RAM) and Chromecast with Google TV have an application heap limit of 192MB–256MB.
  6. In addition, `AdaptiveLoadControl` retains `lastTracksSelected` (holding `TrackGroupArray` and `ExoTrackSelection` object graphs) without clearing it in `onReleased()`.
* **Impact:** High memory pressure, GC thrashing, UI stuttering during playback, and eventual OutOfMemoryError or system Low Memory Killer (LMK) termination.
* **Remediation:**
  1. Change `.setPrioritizeTimeOverSizeThresholds(false)` so ExoPlayer enforces size bounds.
  2. Cap target buffer bytes explicitly (e.g. 64MB for VOD, 16MB for Live TV).
  3. Reduce `WIFI_VOD_MAX_BUFFER_MS` from 120s to 60s.
  4. In `AdaptiveLoadControl.onReleased()`, explicitly null out `lastTracksSelected` and `lastPreparedPlayerId`.

---

### Finding 3: SQLite Connection Pool Native RAM Exhaustion
* **Severity:** **P1 - High**
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/epgindex/EpgIndexDatabase.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/epgindex/EpgIndexDatabase.kt#L89-L97)
* **Mechanism:**
  1. In `EpgIndexDatabase.addCallback`:
     ```kotlin
     override fun onOpen(db: SupportSQLiteDatabase) {
         db.execSQL("PRAGMA synchronous = NORMAL")
         db.execSQL("PRAGMA cache_size = -64000") // 64MB cache
         db.execSQL("PRAGMA temp_store = MEMORY")
     }
     ```
  2. This callback runs on **every connection** opened by Room's connection pool.
  3. Under Room WAL mode, multiple read connections and a write connection coexist. Four active connections lock up to $4 \times 64\text{MB} = 256\text{MB}$ of native memory.
  4. Native memory allocations are not tracked by JVM heap limits and cannot be reclaimed by Java GC, directly inducing low-memory kills on Android TV.
* **Impact:** Severe native memory footprint; process killed by OS during background sync while media playback or browsing is active.
* **Remediation:**
  1. Lower `cache_size` to a safe `-8000` (8MB) or `-16000` (16MB) in `onOpen`.
  2. Use default `temp_store` instead of forcing `MEMORY`.

---

### Finding 4: `RefreshQueue` Race Condition on Task Dispatch & Cancellation
* **Severity:** **P1 - High**
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/queue/RefreshQueue.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/src/main/java/org/njarasoa/fijerena/core/network/queue/RefreshQueue.kt#L104-L147)
* **Mechanism:**
  1. In `processAvailable()`, line 104 creates an entry with a dummy disconnected `Job()`:
     ```kotlin
     activeTasks[task.task.id] = ActiveTask(Job(), task.deferred)
     ```
  2. The coroutine is then launched on line 110: `val job = scope.launch { ... }`.
  3. Outside the launch block at line 146, it updates:
     ```kotlin
     queueMutex.withLock { activeTasks[queuedTask.task.id] = ActiveTask(job, queuedTask.deferred) }
     ```
  4. If `cancelAll()` is invoked between line 104 and line 146, it iterates `activeTasks`, cancels the disconnected dummy `Job()`, and clears the map.
  5. Line 146 then executes and inserts the real `job` back into `activeTasks`, running uncancelled.
  6. Furthermore, `cancelAll()` does not reset `_activeTaskIds` or `_isProcessing`, leaving the UI sync spinner permanently active if tasks were awaiting the semaphore permit.
* **Impact:** Orphaned sync jobs continue running after user cancellation; sync indicator stuck in perpetual spinning state.
* **Remediation:**
  1. Atomically poll from `queue`, launch `scope.launch`, and record `ActiveTask(job, task.deferred)` inside `queueMutex.withLock`.
  2. In `cancelAll()`, reset `_activeTaskIds.value = emptySet()` and `_isProcessing.value = false`.
  3. Replace the `while (true) ... ?: break` loop in `processAvailable()` with a structured condition avoiding `break`.

---

### Finding 5: Swallowed `CancellationException` in Workers and Content Sync
* **Severity:** **P1 - High**
* **Location:**
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgSyncWorker.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgSyncWorker.kt#L107)
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgFtsRebuildWorker.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgFtsRebuildWorker.kt#L59)
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/manager/XtreamContentManager.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/manager/XtreamContentManager.kt#L540-L802)
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgFileManager.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgFileManager.kt#L824-L1032)
* **Mechanism:**
  1. In `EpgSyncWorker.kt` and `EpgFtsRebuildWorker.kt`, `catch (e: Exception)` does not rethrow `CancellationException`. When WorkManager stops or cancels the worker (e.g. constraints lost or timeout), it catches the exception, logs a failure, and schedules a retry.
  2. In `XtreamContentManager.kt`, `syncCategories`, `syncStreams`, and `syncSeries` catch `catch (e: Exception)` inside `RefreshTask.execute()` and only log it. Because `execute()` does not throw, `RefreshQueue` treats the task as successfully completed (`queuedTask.deferred.complete(Unit)`).
  3. In `EpgFileManager.kt` lines 824 and 1031, `_state.value = MultiSourceState.Error(...)` is set **before** `if (e is CancellationException) throw e`. A normal worker cancellation or pause flashes a red error state on the UI.
* **Impact:** Coroutine cancellation trees are corrupted; cancelled operations are reported as successes or logged as permanent sync errors.
* **Remediation:**
  1. Add `if (e is CancellationException) throw e` at the entry of all worker catch blocks.
  2. In `XtreamContentManager`, let cancellations and unhandled exceptions propagate to `RefreshQueue`.
  3. In `EpgFileManager`, guard `_state.value = MultiSourceState.Error(...)` so it only runs when `e !is CancellationException`.

---

### Finding 6: Thread-Unsafe Collections in `XtreamMediaProvider`
* **Severity:** **P1 - High**
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/XtreamMediaProvider.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/XtreamMediaProvider.kt#L41-L45)
* **Mechanism:**
  1. `searchDataSizes` is declared as `private val searchDataSizes = mutableMapOf<String, Long>()`.
  2. `tmdbOverviewCache` is declared as `private val tmdbOverviewCache = mutableMapOf<Int, Map<Pair<Int, Int>, String>>()`.
  3. `XtreamMediaProvider` is a singleton instance accessed concurrently across background sync workers, catalog search coroutines, and UI viewmodels.
  4. Reading and mutating plain `LinkedHashMap` without synchronization across dispatchers leads to non-deterministic data corruption and `ConcurrentModificationException`.
* **Impact:** Random crashes during background TMDB enrichment and catalog search.
* **Remediation:** Replace with `java.util.concurrent.ConcurrentHashMap`.

---

### Finding 7: Unguarded Database Checkpoint & Vacuum Deadlock in `EpgIndexer`
* **Severity:** **P1 - High**
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/epgindex/EpgIndexer.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/epgindex/EpgIndexer.kt#L688-L720)
* **Mechanism:**
  1. All database writes in `EpgIndexer` are guarded by `private val writeMutex = Mutex()`.
  2. `incrementalVacuum()` runs **completely outside `writeMutex`**.
  3. Inside `incrementalVacuum()`, it executes `sdb.execPragma("PRAGMA wal_checkpoint(TRUNCATE)")` and `PRAGMA incremental_vacuum`.
  4. A WAL checkpoint with `TRUNCATE` requires an exclusive database lock. If an ongoing ingest or swap is running on another coroutine holding `writeMutex`, this triggers `SQLiteDatabaseLockedException` or `sqlite3_busy`.
  5. Note that `purgeOldProgrammes()` calls `incrementalVacuum()` while already holding `writeMutex`. Wrapping `incrementalVacuum()` directly in `writeMutex.withLock` would deadlock because Kotlin's `Mutex` is not re-entrant.
  6. Line 710 in `incrementalVacuum()` also contains an explicit `break` keyword.
* **Impact:** Ingestion crashes with SQLite lock errors; long vacuum stalls lock out readers.
* **Remediation:**
  1. Extract core vacuum logic into `private fun incrementalVacuumLocked(sdb: SupportSQLiteDatabase)`.
  2. Have `purgeOldProgrammes()` call `incrementalVacuumLocked()` while holding the lock.
  3. Have external callers call `suspend fun incrementalVacuum() = writeMutex.withLock { ... }`.
  4. Refactor the `while` loop to eliminate `break`.

---

### Finding 8: Activity Context Leaks via Player Listeners & Factories
* **Severity:** **P1 - High**
* **Location:**
  - [`tv/src/main/java/org/njarasoa/fijerena/feature/player/TvPlayerScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/player/TvPlayerScreen.kt#L81-L151)
  - [`core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt#L990-L1015)
  - [`core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/StreamLoaderViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/StreamLoaderViewModel.kt#L749-L765)
* **Mechanism:**
  1. In `TvPlayerScreen.kt:149`:
     ```kotlin
     StreamingPlaybackService.awaitInstance().setPositionSaveListener { position, duration, isPaused, audioIndex, subtitleIndex ->
         loaderViewModel.recordHistory(position, duration, isPaused, audioIndex, subtitleIndex)
     }
     ```
  2. Unlike `MobilePlayerScreen.kt`, `TvPlayerScreen.kt` never clears `setPositionSaveListener(null)` in `DisposableEffect.onDispose`.
  3. In `StreamingPlaybackService.releasePlayerAndSession()`, `onPositionSaveListener` is not nulled out.
  4. The service singleton retains the closure capturing `loaderViewModel`.
  5. `TvPlayerScreen.kt:81` passes `LocalContext.current` (the Activity) directly to `StreamLoaderViewModelFactory`.
* **Impact:** Every playback session leaks the previous Activity instance, its View hierarchy, and its ViewModels into the long-lived Service singleton.
* **Remediation:**
  1. In `TvPlayerScreen.kt`, call `StreamingPlaybackService.getInstance()?.setPositionSaveListener(null)` in `onDispose`.
  2. In `StreamingPlaybackService.releasePlayerAndSession()`, null out `onPositionSaveListener = null`.
  3. Store `context.applicationContext` in `StreamLoaderViewModelFactory`.

---

### Finding 9: Conditional Composable Execution & Early Return in `LiveTvSplitLayout`
* **Severity:** **P2 - Medium**
* **Location:** [`tv/src/main/java/org/njarasoa/fijerena/feature/category/components/LiveTvSplitLayout.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/category/components/LiveTvSplitLayout.kt#L204-L236)
* **Mechanism:**
  1. Lines 204–231:
     ```kotlin
     val target = previewTarget
     if (target == null) {
         AmbientBackdrop(...)
         Row(...) { ... }
         return
     }
     val playback: PlaybackViewModel = viewModel()
     val previewPlaybackState by playback.playbackState.collectAsStateWithLifecycle()
     val lifecycleOwner = LocalLifecycleOwner.current
     DisposableEffect(lifecycleOwner) { ... }
     ```
  2. An early return directly precedes composable instantiations and lifecycle effects.
  3. When `target` transitions from non-null to null (e.g. during category switching when streams are reloading), Compose disposes all subsequent composables.
  4. `DisposableEffect.onDispose` runs `playback.stopAndRelease()`, killing the background service.
  5. When streams load and `target` becomes non-null again, the ViewModel is retrieved from the backstack, but the service is dead and hangs on `awaitInstance()`.
  6. The early return also directly violates the user's single return statement constraint.
* **Impact:** Frame drops, lifecycle churn, and playback crashes when navigating live TV categories.
* **Remediation:** Refactor `LiveTvSplitLayout` so all ViewModels and effects are initialized unconditionally at the top of the composable, and the UI layout branches cleanly with standard `if-else` without any early return.

---

### Finding 10: Positional IDs in SMB and M3U Providers
* **Severity:** **P2 - Medium**
* **Location:**
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/smb/SmbMediaProvider.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/smb/SmbMediaProvider.kt#L175)
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/local/M3uParser.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/local/M3uParser.kt#L114-L153)
* **Mechanism:**
  1. In `SmbMediaProvider.kt`:
     ```kotlin
     id = "smb_file_${itemList.size}"
     ```
  2. In `M3uParser.kt`:
     ```kotlin
     id = "${idPrefix}_m3u_$index"
     ```
  3. The item ID is based on its positional order during directory scanning or playlist parsing.
  4. If a file or stream is added, removed, or reordered, every subsequent item receives a different ID.
  5. Watch state (`watch_state` table) and favorites (`favorite_state` table) are keyed on `(providerId, itemId)`.
* **Impact:** Watch history and favorites silently scramble and attach to completely unrelated media files when the catalog changes.
* **Remediation:** Generate deterministic IDs based on content identity: `id = "smb_${fullPath}"` or `"${idPrefix}_m3u_${streamUri.hashCode()}"`.

---

## 3. Prioritized Implementation Roadmap

### Phase 1: Playback Service Lifecycle & Deadlock Immunity (P0) — Done (`a2d62442`)
* **Target Files:**
  - `core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt`
  - `core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt`
* **Tasks:**
  1. Add `ensureServiceRunning()` in `PlaybackViewModel` before any `awaitInstance()` or playback operation.
  2. If the service was stopped (`getInstance() == null`), restart it, reset `serviceStartRequested`, and re-subscribe `observeServiceState()` and `connectToService()`.
  3. Add a timeout guard (10s) to `StreamingPlaybackService.awaitInstance()`.
  4. In `releasePlayerAndSession()` and `onDestroy()`, reset `serviceStartRequested` and clear `onPositionSaveListener = null`.
* **As implemented:** `serviceStartRequested` reset was already fixed by an earlier, unrelated commit (`9486396d`, credited in the round-2 plan) — verified still true on HEAD rather than re-doing it. `onDestroy()` already calls `releasePlayerAndSession()` (safe to call twice per its own kdoc), so no separate reset was needed there.

### Phase 2: Memory Optimization & Native Heap Protection (P0 / P1) — Done (`78b75ec3`)
* **Target Files:**
  - `core/player/src/main/java/org/njarasoa/fijerena/core/player/config/AdaptiveLoadControl.kt`
  - `core/player/src/main/java/org/njarasoa/fijerena/core/player/config/NetworkBufferProfile.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/epgindex/EpgIndexDatabase.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgChannelMatcher.kt`
* **Tasks:**
  1. In `AdaptiveLoadControl.kt`, set `.setPrioritizeTimeOverSizeThresholds(false)`, set explicit target buffer bytes (64MB VOD, 16MB Live), and clear tracks in `onReleased()`.
  2. Reduce `WIFI_VOD_MAX_BUFFER_MS` from 120s to 60s in `NetworkBufferProfile.kt`.
  3. In `EpgIndexDatabase.kt`, lower `onOpen` PRAGMA `cache_size` from `-64000` (64MB) to `-8000` (8MB) and remove `temp_store = MEMORY`.
  4. In `EpgChannelMatcher.kt`, bound `memoizedMatches` capacity to 5,000 entries and refactor `doMatch` to single-return style.
* **As implemented:** swept the codebase for the same 4 pattern classes (permanent large PRAGMA settings, unbounded `LoadControl`, unbounded memoization caches) before implementing — no other instances found; `EpgIndexer`'s other PRAGMA bumps were already correctly temporary-and-reset.

### Phase 3: Concurrency, Workers & Task Safety (P1) — Done (`264160d4`)
* **Target Files:**
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/queue/RefreshQueue.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgSyncWorker.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgFtsRebuildWorker.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/manager/XtreamContentManager.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgFileManager.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/XtreamMediaProvider.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/epgindex/EpgIndexer.kt`
* **Tasks:**
  1. Fix `RefreshQueue` task registration: poll, coroutine launch, and activeTask registration performed atomically under `queueMutex`. Reset active state in `cancelAll()`. Eliminate `break`.
  2. Rethrow `CancellationException` in `EpgSyncWorker` and `EpgFtsRebuildWorker`.
  3. Propagate exceptions and cancellations in `XtreamContentManager` sync tasks.
  4. Guard `_state.value = MultiSourceState.Error` in `EpgFileManager` so cancellations do not flash UI errors.
  5. Replace plain maps in `XtreamMediaProvider` with `ConcurrentHashMap`.
  6. Guard `incrementalVacuum` in `EpgIndexer` with `writeMutex` (via `incrementalVacuumLocked`) and eliminate `break`.
* **As implemented:** also wrapped `RefreshQueue`'s completion-cleanup `finally` block in `NonCancellable` — a suspending `queueMutex.withLock` inside `finally` on an already-cancelling coroutine can throw immediately and skip cleanup if the lock is contended, a gap in the Phase-1-of-round-2 fix to the same block. `XtreamSyncWorker` (a sibling `CoroutineWorker`, found while sweeping for other instances of task 2/3's pattern) needed no change — it has no local catch block, so it already inherits the round-2 fix to `ProviderSyncRunner`. Also found and fixed the same "relies on informal timing instead of a mutex" shape in `EpgFileManager.launchClearAllData()`, which used `RefreshQueue.cancelAll()` plus a fixed 100ms delay as a guess that a cancelled ingest would finish before `clearAll()` ran; now acquires `ingestMutex` properly instead.

### Phase 4: Lifecycle Leaks, UI Composition & Catalog Determinism (P1 / P2) — Done except Finding 9 (`897acf61`)
* **Target Files:**
  - `tv/src/main/java/org/njarasoa/fijerena/feature/player/TvPlayerScreen.kt`
  - `tv/src/main/java/org/njarasoa/fijerena/feature/category/components/LiveTvSplitLayout.kt`
  - `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/StreamLoaderViewModel.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/smb/SmbMediaProvider.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/local/M3uParser.kt`
* **Tasks:**
  1. In `TvPlayerScreen.kt`, add `StreamingPlaybackService.getInstance()?.setPositionSaveListener(null)` in `onDispose`.
  2. Use `context.applicationContext` in `StreamLoaderViewModelFactory`.
  3. Refactor `LiveTvSplitLayout.kt` to eliminate early `return`, ensuring unconditional composable execution.
  4. Replace positional IDs in SMB and M3U providers with deterministic content-based IDs.
* **As implemented:**
  - Task 3 (`LiveTvSplitLayout`) **deliberately skipped, by decision, not forgotten.** The early return sits inside a ~500-line composable; a correct fix — not tearing down the playback service on a transient null, not just relocating the `return` — needs real Compose lifecycle surgery that can't be verified without a device. The crash-causing consequence (hang after teardown) is already closed by Phase 1 of this plan and by the round-2 plan's `ensureServiceRunning()`/`awaitInstance()` timeout; what's left here is frame drops/lifecycle churn, not breakage. Revisit as its own carefully-tested change if it becomes worth the risk.
  - Task 4 expanded beyond the two named files once the same pattern was swept for repo-wide: `LocalFileScanner.kt` had three more instances (`local_file_$index`, two separate `local_dir_${categories.size}` sites across its plain-`File` and `DocumentFile` scanners) not in the original finding, all fixed the same way (content-derived ids: file path, directory name, or SAF URI hash). Caught a real regression in my own first pass here — `createFileMediaItem` is called for both root-level and nested files sharing one item list, so hashing just `file.name` (dropping the old global running-index component) would have let a root file and a same-named nested file collide; fixed by hashing the full `file.path` instead, which is globally unique across the tree.

---

## 4. Verification & Testing Strategy

**Status: not yet done.** Each phase's touched modules passed `ktlintCheck` + `compileDebugKotlin` (and `core:ui`/`tv`/`mobile` downstream compile checks) before commit, plus the existing `M3uParser`/`LocalFileScanner` unit tests, which still pass. Nothing below has been run on hardware.

1. **Compilation & Style Integrity:** — done per-phase (see above); a full repo-wide `./gradlew ktlintCheck`/`assembleDebug` has not been run.
2. **Lifecycle & Playback Verification:** — outstanding
   - Launch TV playback, press Home button (triggers `MainActivity.onStop()` -> `stopAndRelease()`), resume the app, and select a stream. Confirm playback immediately resumes without hanging on `awaitInstance()`.
   - Rapidly switch Live TV categories in `LiveTvSplitLayout` to verify seamless preview transitions and no orphaned service calls. (Note: the underlying early-return pattern here was left as-is — see Phase 4 above — so this is about confirming there's no regression, not a fixed behavior.)
3. **Memory Pressure Verification:** — outstanding
   - Profile heap and native allocations with Android Studio Profiler during high-bitrate 4K playback. Verify that buffer usage stays capped at 64MB and does not grow to 1GB.
   - Trigger multi-source EPG sync and verify SQLite cache remains within 16MB native RAM.
4. **Cancellation Verification:** — outstanding
   - Cancel an active EPG sync and verify that WorkManager and `RefreshQueue` tear down immediately without recording error states in `providers.db`.
5. **Catalog Determinism Verification:** — outstanding, new given the Phase 4 ID changes
   - Add/remove/reorder files in a local folder, SMB share, or M3U playlist with existing watch progress/favorites; confirm history and favorites stay attached to the correct item instead of shifting to whatever now occupies that position.
