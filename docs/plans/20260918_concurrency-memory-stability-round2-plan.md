# Concurrency, Memory Pressure & Stability Remediation Plan (Round 2)

**Status:** Done — all 4 phases implemented and committed  
**Date:** 2026-09-18  
**Scope:** `core:ui`, `core:network`, `core:player`, `tv`, `mobile`

---

## 1. Executive Summary

Following the completion of the Round 1 stability audit (Phases 1–5 in `docs/plans/20260918_concurrency-memory-stability-plan.md`), a forensic re-verification against HEAD was performed across concurrency primitives, coroutine hierarchies, SQLite transaction boundaries, and memory allocations.

Spot-checks against the current codebase confirmed that two earlier hypotheses were already resolved by prior commits:
- Former Finding 1 (`CategoryViewModel` stream clobbering) was resolved in `9486396d` by `loadStreamsJob`/`nowPlayingJob` cancellation and category match guards.
- Former Finding 3 (`PlaybackViewModel` deadlock) is handled by `onCleared()` reset in `0974b94c`.

The remaining items were verified line-by-line against HEAD, yielding **7 verified defect items** and **2 defensive hardening items**:
- **1 P0 (Critical)**: Non-atomic state updates in `RefreshQueue` causing permanently stuck task states.
- **5 P1 (High)**: Thread-unsafe collections across concurrent coroutines in `MediaRepository` and `JellyfinMediaProvider`, swallowed `CancellationException` turning worker pauses into permanent database failure states, unbounded memory retention in `EpgChannelMatcher`, premature teardown of the application-wide `NetworkMonitor`, and blanket staging deletion corrupting multi-source EPG ingestion.
- **1 P2 (Medium)**: Permanent SQLite native heap bloat from un-reverted connection PRAGMAs in `EpgIndexer`.
- **2 P3 (Low / Hardening)**: Missing composite index on `(source_id, end_epoch)` for EPG end-time queries, and unguarded `lateinit` access in `CategoryViewModel` dev helpers.

---

## 2. Verified Findings & Root Cause Analysis

### Finding 1 (Former Finding 2): Non-Atomic State Update Race in `RefreshQueue`
* **Severity:** **P0 - Critical**
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/queue/RefreshQueue.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/src/main/java/org/njarasoa/fijerena/core/network/queue/RefreshQueue.kt#L121-L124)
* **Status:** **Verified Real on HEAD**
* **Mechanism:**
  1. In `RefreshQueue`, concurrent tasks execute governed by `Semaphore(concurrencyLimit)`.
  2. In the task completion `finally` block:
     ```kotlin
     queueMutex.withLock { activeTasks.remove(queuedTask.task.id) }
     _activeTaskIds.value = _activeTaskIds.value - queuedTask.task.id
     _isProcessing.value = _activeTaskIds.value.isNotEmpty()
     ```
  3. The read-modify-write on `_activeTaskIds.value` sits outside `queueMutex`.
  4. When two tasks finish concurrently on separate coroutines:
     - Task 1 reads `{T1, T2}`, computes `{T2}`.
     - Task 2 reads `{T1, T2}`, computes `{T1}`.
     - Task 1 writes `{T2}`.
     - Task 2 writes `{T1}`, overwriting Task 1's update.
  5. **Consequence:** `T1` is stranded indefinitely in `_activeTaskIds.value`. `_isProcessing.value` remains `true` permanently. The UI retains a perpetual sync spinner, and future submissions of `T1` are rejected by deduplication checks.
* **Remediation:**
  - Move `_activeTaskIds` and `_isProcessing` updates inside `queueMutex.withLock`, or use atomic StateFlow CAS updates:
    ```kotlin
    _activeTaskIds.update { it - queuedTask.task.id }
    _isProcessing.value = _activeTaskIds.value.isNotEmpty()
    ```

---

### Finding 2 (Former Finding 4): Thread-Unsafe Plain Collections Across Dispatchers
* **Severity:** **P1 - High**
* **Location:**
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/MediaRepository.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/MediaRepository.kt#L691) (`cachedRecentCategories`)
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/jellyfin/JellyfinMediaProvider.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/jellyfin/JellyfinMediaProvider.kt#L49-L51) (`playSessionIds`, `mediaSourceIds`, `playMethods`)
* **Status:** **Verified Real on HEAD**
* **Mechanism:**
  1. `MediaRepository.cachedRecentCategories`: Plain `mutableMapOf<String, List<RecentCategory>>()`. Mutated in `addToCategoryHistory` on `Dispatchers.IO`, read synchronously in `getRecentlyViewedCategories`, and cleared in `clearCache()`.
  2. `JellyfinMediaProvider`: `playSessionIds`, `mediaSourceIds`, and `playMethods` are plain `mutableMapOf<String, String>()`. Mutated in `createMediaSource` / `reportPlaybackStarted`, read during periodic progress reports (`reportPlaybackProgress`), and removed during `stopPlayback` across different coroutines.
  3. **Risk:** Unsynchronized access on Android runtimes causes `ConcurrentModificationException` or internal bucket corruption.
* **Remediation:**
  - Replace `cachedRecentCategories` with `ConcurrentHashMap<String, List<RecentCategory>>`.
  - Replace `playSessionIds`, `mediaSourceIds`, and `playMethods` with `ConcurrentHashMap<String, String>`.

---

### Finding 3 (Former Finding 5): Swallowed `CancellationException` in Sync Runner & Queue
* **Severity:** **P1 - High**
* **Location:**
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/ProviderSyncRunner.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/xtream/ProviderSyncRunner.kt#L85-L97)
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/queue/RefreshQueue.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/queue/RefreshQueue.kt#L117-L120)
* **Status:** **Verified Real on HEAD**
* **Mechanism:**
  1. `ProviderSyncRunner.syncProvider()` catches `catch (e: Exception)`.
  2. When WorkManager cancels the worker or a timeout occurs, coroutines throw `CancellationException` (which extends `IllegalStateException` -> `Exception`).
  3. `isTransient(e)` evaluates `CancellationException` to `false` (only handles `IOException`, `SocketTimeoutException`, `UnknownHostException`).
  4. It returns `Outcome.Permanent(message)`, and `ProviderSyncManager.kt:117-120` writes this error to `providers.db` via `providerRepo.updateSyncStats(...)`.
  5. The provider is flagged in the UI as having a persistent sync error despite merely being cancelled.
  6. In `RefreshQueue.kt:117-120`, catching generic `Exception` logs `CancellationException` with `Log.e`.
* **Remediation:**
  - In `ProviderSyncRunner.kt`: `if (e is CancellationException) throw e` before transient/permanent error branching.
  - In `RefreshQueue.kt`: Do not log `CancellationException` as an error; propagate cancellation.

---

### Finding 4 (Former Finding 6): Unbounded Heap Retention in `EpgChannelMatcher`
* **Severity:** **P1 - High**
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgChannelMatcher.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/xmltv/EpgChannelMatcher.kt#L15-L54)
* **Status:** **Verified Real on HEAD**
* **Mechanism:**
  1. `EpgChannelMatcher` holds a static companion singleton: `private var cachedInstance: EpgChannelMatcher? = null`.
  2. Each instance populates 4 maps and 2 arrays indexing all live stream entities (`byEpgId`, `byEpgIdLower`, `byName`, `byNormalized`, `normalizedNames`, `normalizedStreams`).
  3. For large Xtream providers (30,000–60,000 live channels), this occupies 30–60MB of native/JVM heap.
  4. `ProviderSyncRunner` proactively warms this cache in background workers via `warmCache()`.
  5. `clearCache()` is defined at line 39 but is **never called anywhere in the codebase**. It is never invoked during `onTrimMemory`, provider deletion, or provider switching.
* **Remediation:**
  - Call `EpgChannelMatcher.clearCache()` from `FijerenaApplication.onTrimMemory`.
  - Clear cache when providers are switched or deleted.

---

### Finding 5 (Former Finding 7): Premature Teardown of Application-Wide `NetworkMonitor`
* **Severity:** **P1 - High**
* **Location:** [`core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/service/StreamingPlaybackService.kt#L1015)
* **Status:** **Verified Real on HEAD**
* **Mechanism:**
  1. `NetworkMonitor` is an application-wide singleton that registers a `ConnectivityManager.NetworkCallback`.
  2. In `StreamingPlaybackService.releasePlayerAndSession()`, line 1015 calls:
     ```kotlin
     NetworkMonitor.release()
     ```
  3. When playback stops or transitions via `stopAndRelease()`, this unregisters the system network callback globally.
  4. Any other component relying on `NetworkMonitor` (such as EPG sync workers or connection recovery) ceases receiving network callbacks until a new player is created.
* **Remediation:**
  - Remove `NetworkMonitor.release()` from `StreamingPlaybackService.releasePlayerAndSession()`. `NetworkMonitor` should remain active for the process lifetime.

---

### Finding 6 (Former Finding 8): Indiscriminate Staging Deletion During Partial EPG Swap
* **Severity:** **P1 - High**
* **Location:**
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/epgindex/EpgIndexDao.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/xmltv/epgindex/EpgIndexDao.kt#L69-L74)
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgFileManager.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/xmltv/EpgFileManager.kt#L884-L886)
* **Status:** **Verified Real on HEAD**
* **Mechanism:**
  1. `executeSwap(sourceIds: List<Long>)` selectively transfers channels and programmes for the specified `sourceIds`.
  2. At line 73, `executeSwap` calls `clearStaging()`, which runs unconditional `DELETE FROM epg_programme_staging` and `DELETE FROM epg_channel_staging`.
  3. If another source was staging in parallel, or if single-source refreshes overlap, this deletes staging data for all other sources.
  4. In `EpgFileManager.kt:884-886`, `processSingleSourceInternal(sourceId)` also invokes blanket `indexer.clearStaging()`.
* **Remediation:**
  - Introduce `clearStagingForSources(sourceIds: List<Long>)` that deletes only where `source_id IN (:sourceIds)`.
  - Replace `clearStaging()` calls in `executeSwap` and single-source flows with source-scoped clearing.

---

### Finding 7 (Former Finding 9): SQLite Connection Pool PRAGMA RAM Retention
* **Severity:** **P2 - Medium**
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/epgindex/EpgIndexer.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/xmltv/epgindex/EpgIndexer.kt#L423-L434)
* **Status:** **Verified Real on HEAD**
* **Mechanism:**
  1. During FTS index rebuild, `EpgIndexer` executes:
     ```kotlin
     sdb.execSQL("PRAGMA temp_store = MEMORY")
     sdb.execSQL("PRAGMA cache_size = -16000") // 16MB page cache
     ```
  2. Upon completion, it resets `PRAGMA synchronous = NORMAL`, but never resets `cache_size` or `temp_store`.
  3. Because Room pools connections, that connection retains its 16MB page cache and in-memory temporary store permanently, consuming RAM on 1-2GB Android TV devices.
* **Remediation:**
  - In a `finally` block, reset `PRAGMA cache_size = -2000` (SQLite 2MB default) and `PRAGMA temp_store = DEFAULT`.

---

### Finding 8 (Former Finding 10): Missing Composite Index on `(source_id, end_epoch)`
* **Severity:** **P3 - Low / Optimization**
* **Location:**
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/epgindex/EpgProgrammeEntity.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/xmltv/epgindex/EpgProgrammeEntity.kt#L12-L21)
  - [`core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/EpgManagementViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/EpgManagementViewModel.kt#L91-L102)
* **Status:** **Verified Real on HEAD (Downgraded from storm to query optimization)**
* **Mechanism:**
  1. `EpgManagementViewModel.latestProgrammeTimes` queries `SELECT MAX(end_epoch) FROM epg_programme WHERE source_id = :sourceId`.
  2. `epg_programme` has an index on `source_id` and an index on `end_epoch`, but no composite index on `(source_id, end_epoch)`.
  3. Finding `MAX(end_epoch)` for a source requires scanning all index entries for that source.
* **Remediation:**
  - Add composite index `(source_id, end_epoch)` on `epg_programme` if schema migration is performed, or cache the result.

---

### Finding 9 (Former Finding 11): Unguarded `lateinit` Access in `CategoryViewModel` Dev Helpers
* **Severity:** **P3 - Low / Hardening**
* **Location:** [`core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/CategoryViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/CategoryViewModel.kt#L78-L116)
* **Status:** **Verified Real on HEAD (Low Risk)**
* **Mechanism:**
  1. `getPayloadSize()` and `getFetchTime()` dereference `repository` directly without checking `::repository.isInitialized`.
  2. While normal UI flows reach these only after `UiState.Success` is emitted, adding defensive guards prevents crashes if composables invoke them during early composition.
* **Remediation:**
  - Add `if (!::repository.isInitialized) return null` guards at the top of these helper methods.

---

## 3. Prioritized Remediation Roadmap

### Phase 1: Critical Concurrency State Hazards (P0) — Done (`51aeb2b9`)
- **Scope:** `RefreshQueue.kt`
- **Actions:**
  1. **RefreshQueue**: Move `_activeTaskIds` and `_isProcessing` state mutations inside `queueMutex.withLock` or use atomic `.update {}` to eliminate race conditions stranding task IDs and keeping `_isProcessing` stuck.
- **As implemented:** wrapped both the add-side and remove-side transitions in `queueMutex.withLock` (the add-side had the same unguarded race; the original finding only named the remove-side).

### Phase 2: Memory & Lifecycle Leaks (P1) — Done (`ddf3601b`)
- **Scope:** `EpgChannelMatcher.kt`, `StreamingPlaybackService.kt`, `EpgIndexer.kt`
- **Actions:**
  1. **EpgChannelMatcher**: Hook `clearCache()` into memory trim events (`FijerenaApplication.onTrimMemory`) and provider switching.
  2. **StreamingPlaybackService**: Remove `NetworkMonitor.release()` from `releasePlayerAndSession()` so global network monitoring is not severed on playback exit.
  3. **EpgIndexer**: Reset `cache_size = -2000` and `temp_store = DEFAULT` in a `finally` block after FTS rebuild.
- **As implemented:** hooked into `MediaProviderFactory.trimMemory()`/`clearCache()`/`clearAllCaches()`, which are the actual call sites reached from `onTrimMemory` and provider delete/switch. PRAGMA reset also covers `synchronous`, which wasn't being reset on a failed rebuild either.

### Phase 3: Data Safety & Thread Safety Hazards (P1) — Done (`a96d0da0`)
- **Scope:** `MediaRepository.kt`, `JellyfinMediaProvider.kt`, `ProviderSyncRunner.kt`, `EpgIndexDao.kt`, `EpgFileManager.kt`
- **Actions:**
  1. **MediaRepository**: Migrate `cachedRecentCategories` to `ConcurrentHashMap`.
  2. **JellyfinMediaProvider**: Migrate `playSessionIds`, `mediaSourceIds`, and `playMethods` to `ConcurrentHashMap`.
  3. **ProviderSyncRunner**: Rethrow `CancellationException` cleanly so cancellations are not saved as permanent errors in `providers.db`.
  4. **EpgIndexDao & EpgFileManager**: Scope staging clearing to the specific `sourceIds` being swapped.
- **As implemented:** also fixed `RefreshQueue`'s own swallowed-`CancellationException` catch (same root cause, same file already touched in Phase 1). `EpgIndexDao.executeSwap()` now uses a new `clearStagingForSources()`; `EpgFileManager`'s two top-level `clearStaging()` calls were left as-is — they run under `ingestMutex`, which already serializes top-level ingestion runs, so a blanket clear there is a legitimate clean slate, not the bug.

### Phase 4: Defensive Hardening (P3) — Done (`6b9c7f35`)
- **Scope:** `CategoryViewModel.kt`, `EpgManagementViewModel.kt`
- **Actions:**
  1. **CategoryViewModel**: Add `::repository.isInitialized` guards to Dev Mode helper methods.
  2. **EpgManagementViewModel** (Finding 8): resolved without a schema migration — `latestProgrammeTimes` now restarts only on source add/remove (`distinctUntilChangedBy { ids }`) and on a `_dbGeneration` bump, which now also fires on `MultiSourceState.Completed`, not just a full DB wipe. Avoids the query storm without the risk of a `(source_id, end_epoch)` index migration.

---

## 4. Verification Strategy

**Status: not yet done.** Each phase's touched modules passed `ktlintCheck` + `compileDebugKotlin` before commit, but nothing below has been run.

1. **Unit & Concurrency Tests:** — outstanding
   - Add concurrency tests for `RefreshQueue` validating that simultaneous task completions never leave dangling IDs in `activeTaskIds`.
   - Add unit tests for `ProviderSyncRunner` ensuring `CancellationException` is rethrown without writing permanent database errors.
2. **Build & Style Integrity:** — outstanding
   - Execute `./gradlew ktlintCheck` and `./gradlew ktlintFormat` (repo-wide, not just touched modules).
   - Compile both TV and Mobile targets via `./gradlew assembleDebug`.
3. **Hardware / Emulation Validation:** — outstanding
   - Test `RefreshQueue` under rapid parallel submissions.
   - Profile memory usage before and after EPG sync to verify SQLite PRAGMA reset and `EpgChannelMatcher` clearing under memory pressure.
   - Rapidly switch categories on TV and Mobile; stop/restart playback repeatedly; run a multi-source EPG sync and confirm `latestProgrammeTimes` still updates after completion.
