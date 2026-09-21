# Systemic Concurrency, Memory Pressure & Stability Deep-Dive Plan

**Status:** Phases 1-3 complete (2026-09-20); Phases 4-5 outstanding. Findings 2, 5, and 11 dropped from the roadmap after verification — see notes below and per-phase entries.  
**Date:** 2026-09-19  
**Scope:** `core:player`, `core:network`, `core:ui`, `tv`, `mobile`  

**Verification note (2026-09-20):** every finding below was spot-checked against HEAD before implementation, not trusted as written — two corrections came out of that pass:
- **Finding 2** was already fixed by a separate, unrelated commit (`f1341a99`) before Phase 1 of this plan started; no action needed here.
- **Finding 5** is a false positive: `MediaRepository.loadFavoriteSnapshotLocked()`'s `runBlocking(Dispatchers.IO)` under `favoriteLock` is deliberate, documented behavior (Compose calls `isFavorite()` synchronously; Room throws without it), not an oversight. Its own kdoc explains a prior outage this exact code fixed. Left untouched.
- **Finding 11**'s suggested remediation (`SoftReference`) would be a downgrade from what an earlier plan (`docs/plans/concurrency-memory-stability-round2-plan.md`) already shipped — `EpgChannelMatcher.clearCache()` wired into `trimMemory()`/provider switch/delete. The "never cleared" problem this finding describes is already solved; no further action.
- **Finding 1** required a correction to an earlier (wrong) claim made mid-conversation that it was already fixed — it was not; see Phase 1.

---

## 1. Executive Summary

A first-principles, ground-up audit of concurrency primitives, memory management, coroutine hierarchies, and lifecycle boundaries was conducted across all modules of Fijerena without reliance on prior assumptions or existing plans.

The investigation uncovered **16 critical, high, and medium-severity stability and memory defects**:
1. **Permanent Playback Lockout via `serviceStartRequested` Race & Leak** (`PlaybackViewModel`): Static companion flag remains `true` when a service is stopped or destroyed, causing subsequent calls to `startService()` to silently no-op and `StreamingPlaybackService.awaitInstance()` to time out and fail video playback permanently.
2. **Unbounded Coroutine Collectors & Memory Leak** (`PlaybackViewModel.observeServiceState`): Launches state flow collectors directly in `viewModelScope` rather than the caller's coroutine scope, detaching them from `observeStateJob` and causing coroutines and listeners to leak and compound on every reconnection.
3. **Fragile `!!` in Service Teardown** (`StreamingPlaybackService.releasePlayerAndSession`): Force-unwrapping `playerListener!!` and `analyticsListener!!` throws `NullPointerException` if null, aborting teardown and permanently leaking the Android `WakeLock` and `serviceScope`.
4. **Missing Write-Ahead Logging (WAL) Mode** (`XtreamDatabase`, `SettingsDatabase`): Databases default to rollback journal mode where batch write transactions take an exclusive lock on the DB file, stalling concurrent reader coroutines and freezing the UI.
5. **`runBlocking(Dispatchers.IO)` Under Monitor Lock** (`MediaRepository.loadFavoriteSnapshotLocked`): Synchronous `runBlocking` under `synchronized(favoriteLock)` executed from the main thread blocks UI rendering and risks thread starvation / ANR.
6. **Swallowed `CancellationException` in Search Pipelines** (`XmltvSearchService`, `SearchViewModel`): Broad `catch (e: Exception)` blocks swallow coroutine cancellation, forcing cancelled searches to execute expensive fallback FTS queries on SQLite.
7. **Unbounded Synchronized Socket Mutex** (`SmbClient`): Suspending `connect()` holds `synchronized(this@SmbClient)` across blocking network socket I/O (up to 30-60s); UI thread calling `disconnect()` locks on the same monitor and triggers an ANR.
8. **`NetworkOnMainThreadException` and Main-Thread Disk I/O** (`RemoteM3uMediaProvider`, `LocalMediaProvider`): Suspending `connect()` methods execute raw `HttpURLConnection` and directory scanning without `withContext(Dispatchers.IO)`, crashing with `NetworkOnMainThreadException` or dropping UI frames.
9. **Missing `@Volatile` on Cross-Thread Mutable Fields** (`EpgBrowserViewModel`, `SearchViewModel`): Internal references written on `Dispatchers.IO` and read from query/UI threads lack volatile semantics, risking stale memory reads.
10. **Non-Atomic Provider Creation Race** (`MediaProviderFactory.create`): Check-then-act pattern allows concurrent callers to instantiate duplicate provider instances and session states.
11. **Static Lifetime Retention of Massive Channel Datasets** (`EpgChannelMatcher.cachedInstance`): Holds tens of thousands of channel entities in 4 HashMaps and 2 typed arrays in a static singleton indefinitely.
12. **Unshared OkHttpClient Engines** (`TmdbApiService`): Builds isolated Ktor `HttpClient(OkHttp)` engines without configuring the shared `NetworkModule.okHttpClient`, creating duplicate thread pools and connection pools.
13. **Activity Context Leaks in ViewModel Factories** (`CategoryViewModelFactory`, `StreamLoaderViewModelFactory`, etc.): Factories declare `private val context: Context` as constructor properties, retaining the hosting `Activity` instance.
14. **Unbounded In-Memory Playlist Retention** (`BaseM3uMediaProvider`): Large IPTV M3U files (50k–100k items) retain full object graphs in memory, taking 40MB–100MB of heap permanently.
15. **`UninitializedPropertyAccessException` Crash Vector** (`CategoryViewModel`, `EpgViewModel`): Asynchronous `lateinit var repository` initialization allows synchronous UI calls (`isFavorite`, `toggleFavoriteCategory`) to crash immediately if invoked during initial composition.
16. **EPG Staleness Threshold Disparity** (`EpgFileManager.getStaleSources`): Ignores configured `staleThresholdMs` and uses hardcoded 1-hour `SCHEDULED_REFRESH_AGE_MS`, forcing frequent unnecessary re-downloads and re-ingestions.

---

## 2. Forensic Findings & Root Cause Analysis

### Domain A: Playback & Lifecycle Concurrency

#### Finding 1: Permanent Playback Lockout via `serviceStartRequested` Race & Leak
* **Severity:** **P0 - Critical (Showstopper)**
* **Location:** [`core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt#L120-L126)
* **Mechanism:**
  1. `serviceStartRequested` is declared in `PlaybackViewModel.companion object` as a static `AtomicBoolean(false)`.
  2. `startService()` guards `context.startService(intent)` with `serviceStartRequested.compareAndSet(false, true)`.
  3. `serviceStartRequested` is only reset to `false` in `PlaybackViewModel.onCleared()`.
  4. If `StreamingPlaybackService.stopAndRelease()` is called (e.g. from `MainActivity.onStop()` on TV or when switching screens) while the `PlaybackViewModel` is retained in the backstack or activity scope, `StreamingPlaybackService.instance` is destroyed and set to `null`.
  5. When playback is requested again, `ensureServiceRunning()` checks `if (StreamingPlaybackService.getInstance() != null) return` and calls `startService()`.
  6. Because `serviceStartRequested` is already `true`, `compareAndSet(false, true)` returns `false`. `startService()` silently returns without starting the service!
  7. `StreamingPlaybackService.awaitInstance()` then suspends on `instanceReady.await()` until it hits the 10-second timeout, failing playback permanently until the process is killed.
* **Impact:** Black screen / infinite spinner on playback resume after backgrounding or exiting player.
* **Remediation:**
  1. In `ensureServiceRunning()`, if `StreamingPlaybackService.getInstance() == null`, atomically reset `serviceStartRequested.set(false)` before invoking `startService()`.
  2. In `StreamingPlaybackService.releasePlayerAndSession()`, ensure instance readiness deferreds and service start flags are synchronized.

---

#### Finding 2: Unbounded Coroutine Collectors & Memory Leak in `observeServiceState`
* **Severity:** **P1 - High**
* **Location:** [`core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt#L103-L118)
* **Mechanism:**
  1. `observeServiceState()` launches collectors using `viewModelScope.launch`:
     ```kotlin
     private suspend fun observeServiceState() {
         val service = StreamingPlaybackService.awaitInstance()
         viewModelScope.launch {
             service.playbackState.collect { ... }
         }
         viewModelScope.launch {
             service.currentMetadata.collect { ... }
         }
     }
     ```
  2. Because the collectors are launched on `viewModelScope` rather than the suspending function's own coroutine context, `observeServiceState()` finishes immediately after firing the launches.
  3. In `ensureServiceRunning()`, `observeStateJob?.cancel()` is called. But `observeStateJob` has already completed! Cancelling a completed job is a no-op.
  4. The two launched collectors remain active indefinitely in `viewModelScope`.
  5. Every time `ensureServiceRunning()` is called upon stream restart, two new collector coroutines are added to `viewModelScope`.
* **Impact:** Coroutine leaks, duplicate state processing, and increased memory pressure over extended sessions.
* **Remediation:**
  Wrap the collections in structured concurrency using `coroutineScope`:
  ```kotlin
  private suspend fun observeServiceState() = coroutineScope {
      val service = StreamingPlaybackService.awaitInstance()
      launch {
          service.playbackState.collect { state -> _playbackState.value = state }
      }
      launch {
          service.currentMetadata.collect { metadata -> _currentMetadata.value = metadata }
      }
  }
  ```

---

#### Finding 3: Fragile `!!` in Service Teardown Causing WakeLock & Teardown Leak
* **Severity:** **P1 - High**
* **Location:** [`core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt#L1001-L1018)
* **Mechanism:**
  1. `releasePlayerAndSession()` contains:
     ```kotlin
     mediaSession?.run {
         player.removeListener(playerListener!!)
         (player as? androidx.media3.exoplayer.ExoPlayer)?.removeAnalyticsListener(analyticsListener!!)
         player.release()
         release()
     }
     ```
  2. If `playerListener` or `analyticsListener` is null (e.g. if `releasePlayerAndSession()` is called during partial initialization or called more than once), the `!!` operator throws `NullPointerException`.
  3. This crash prevents subsequent cleanup lines from running:
     ```kotlin
     releaseWakeLock()
     wakeLock = null
     serviceScope?.cancel()
     serviceScope = null
     adaptiveLoadControl = null
     ```
* **Impact:** The system `WakeLock` is held indefinitely (draining battery / keeping TV awake), and the `serviceScope` coroutines are never cancelled.
* **Remediation:** Replace `!!` with safe-calls:
  ```kotlin
  playerListener?.let { player.removeListener(it) }
  analyticsListener?.let { (player as? androidx.media3.exoplayer.ExoPlayer)?.removeAnalyticsListener(it) }
  ```

---

### Domain B: Database Concurrency & Lock Contention

#### Finding 4: Missing Write-Ahead Logging (WAL) in `XtreamDatabase` and `SettingsDatabase`
* **Severity:** **P1 - High**
* **Location:**
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/db/XtreamDatabase.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/xtream/db/XtreamDatabase.kt#L195-L207)
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/provider/SettingsDatabase.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/provider/SettingsDatabase.kt#L180-L197)
* **Mechanism:**
  1. Neither database builder configures `.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)`.
  2. SQLite defaults to rollback journal mode (DELETE / TRUNCATE).
  3. In rollback journal mode, writing to the database requires an exclusive lock on the entire database file. Any concurrent read query is completely blocked until the write transaction commits.
  4. During large catalog syncs (`XtreamContentManager.syncStreams` inserting tens of thousands of rows) or provider settings updates, readers querying `streamDao`, `watchStateDao`, or `favoriteStateDao` for UI rendering are blocked.
* **Impact:** UI freezes, dropped frames during background sync, and potential SQLite database lock timeouts.
* **Remediation:** Configure `.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)` on both Room database builders.

---

#### Finding 5: `runBlocking(Dispatchers.IO)` Under Monitor Lock in `MediaRepository`
* **Severity:** **P1 - High**
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/MediaRepository.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/MediaRepository.kt#L870-L885)
* **Mechanism:**
  1. In `loadFavoriteSnapshotLocked()`:
     ```kotlin
     private fun loadFavoriteSnapshotLocked() {
         if (cachedFavorites != null && cachedFavoriteCategories != null) return
         val rows = runBlocking(Dispatchers.IO) { favoriteStateDao.getAll(providerId) }
         ...
     }
     ```
  2. When synchronous Compose methods or ViewModels call `isFavorite()` or `getFavoritesForContentType()`, `synchronized(favoriteLock)` is acquired on the Main thread.
  3. If the cache is unprimed, it executes `runBlocking(Dispatchers.IO)`.
  4. The Main thread is completely blocked waiting for an IO worker thread to complete the query.
  5. Furthermore, the early `return` violates single-return style rules.
* **Impact:** High risk of UI stuttering and ANR crashes if `Dispatchers.IO` is under load or waiting on SQLite disk locks.
* **Remediation:**
  Refactor `loadFavoriteSnapshotLocked()` to eliminate early returns, and asynchronously warm `cachedFavorites` and `cachedFavoriteCategories` on repository initialization or provider switch so cold main-thread queries never stall.

---

### Domain C: Coroutine Cancellation & Threading Safety

#### Finding 6: Swallowed `CancellationException` in Search Pipelines
* **Severity:** **P1 - High**
* **Location:**
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/XmltvSearchService.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/xmltv/XmltvSearchService.kt#L170-L173)
  - [`core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/SearchViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/viewmodels/SearchViewModel.kt#L148-L150)
* **Mechanism:**
  1. In `XmltvSearchService.search()`:
     ```kotlin
     val rawRows = try {
         withTimeoutOrNull(FTS_TIMEOUT_MS) {
             dao.searchByTitleFts(rawFtsQuery, sourceIds, windowStart, windowEnd)
         }
     } catch (e: Exception) {
         null
     }
     ```
  2. When the user types a new character in the search box, the previous coroutine job is cancelled.
  3. `withTimeoutOrNull` or the suspend call throws `CancellationException`.
  4. The generic `catch (e: Exception)` catches `CancellationException`, suppressing cancellation and returning `null`.
  5. Because `rawRows` is `null`, execution continues directly into the fallback FTS safe search (`dao.searchByTitleFts(safeFtsQuery, ...)`), executing an expensive SQLite query on an already-cancelled job!
  6. Similarly, in `SearchViewModel.kt`, category search coroutines catch `Exception` without rethrowing `CancellationException`.
* **Impact:** Waste of SQLite query resources and CPU threads executing stale searches while new searches are queued.
* **Remediation:** Check and rethrow `CancellationException` in all try-catch blocks across search services.

---

#### Finding 7: Unbounded Synchronized Socket Mutex in `SmbClient`
* **Severity:** **P1 - High**
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/smb/SmbClient.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/smb/SmbClient.kt#L34-L65)
* **Mechanism:**
  1. `connect()` is a suspending function running on `Dispatchers.IO`:
     ```kotlin
     suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
         synchronized(this@SmbClient) {
             client = SMBClient()
             connection = client!!.connect(host) // Blocking network socket I/O
             ...
         }
     }
     ```
  2. Connecting to an unreachable or slow SMB server blocks the thread inside `synchronized(this@SmbClient)` for 30–60 seconds.
  3. `disconnect()` is a synchronous method:
     ```kotlin
     fun disconnect() = synchronized(this) { ... }
     ```
  4. When the user exits the screen, `disconnect()` is called on the Main thread (e.g. from Compose `onDispose` or ViewModel cleanup).
  5. The Main thread blocks on `synchronized(this)` waiting for the network socket timeout in `connect()`, triggering an ANR.
* **Impact:** Application Not Responding (ANR) crash when navigating away from an SMB share during connection attempts.
* **Remediation:** Replace monitor synchronization with a non-blocking `kotlinx.coroutines.sync.Mutex` or decouple socket teardown from the connection monitor.

---

#### Finding 8: `NetworkOnMainThreadException` and Main-Thread Disk I/O in Providers
* **Severity:** **P1 - High**
* **Location:**
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/remote/RemoteM3uMediaProvider.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/remote/RemoteM3uMediaProvider.kt#L41-L58)
  - [`core/network/src/main/java/org/njarasoa/fijerena/core/network/local/LocalMediaProvider.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/local/LocalMediaProvider.kt#L33-L60)
* **Mechanism:**
  1. `RemoteM3uMediaProvider.connect()` and `LocalMediaProvider.connect()` are suspending functions, but they do not switch dispatchers.
  2. `CategoryViewModel.init` launches on `viewModelScope` (which defaults to `Dispatchers.Main.immediate` on Android) and invokes `repository.connect()`.
  3. For `RemoteM3uMediaProvider`, `HttpURLConnection.connect()` and stream reading execute directly on the Main thread, throwing `android.os.NetworkOnMainThreadException`.
  4. For `LocalMediaProvider`, ContentResolver streams and directory recursion execute on the Main thread, causing frame drops and ANR risks.
* **Impact:** App crash on loading Remote M3U providers; UI stutter on loading Local providers.
* **Remediation:** Wrap connection and parsing routines in `withContext(Dispatchers.IO)`.

---

#### Finding 9: Missing `@Volatile` on Cross-Thread Mutable Fields
* **Severity:** **P2 - Medium**
* **Location:**
  - [`core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/EpgBrowserViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/viewmodels/EpgBrowserViewModel.kt#L158)
  - [`core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/SearchViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/viewmodels/SearchViewModel.kt#L27)
* **Mechanism:**
  1. In `EpgBrowserViewModel.kt`, `channelMatcher` is written on `Dispatchers.IO` and read from other coroutines without `@Volatile` (unlike the adjacent `lastMatcherProviderId`).
  2. In `SearchViewModel.kt`, `repository` is lazily set without `@Volatile` or synchronization.
* **Impact:** Stale cache reads or race conditions under concurrent queries across dispatchers.
* **Remediation:** Mark cross-thread mutable state fields with `@Volatile`.

---

#### Finding 10: Non-Atomic Provider Creation Race in `MediaProviderFactory`
* **Severity:** **P2 - Medium**
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/MediaProviderFactory.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/MediaProviderFactory.kt#L44-L65)
* **Mechanism:**
  1. In `MediaProviderFactory.create()`:
     ```kotlin
     providerCache[entity.id]?.let { return it }
     val provider = when (entity.type) { ... }
     return providerCache.putIfAbsent(entity.id, provider) ?: provider
     ```
  2. Concurrent callers both miss `providerCache[entity.id]` and create two separate `MediaProvider` instances, wasting resources and potentially opening duplicate network sessions before one is discarded.
  3. Also contains an early return violating single-return style rules.
* **Impact:** Duplicate network/session allocations during concurrent provider lookups.
* **Remediation:** Use `providerCache.computeIfAbsent` or atomic synchronization with single-return structure.

---

### Domain D: Memory Pressure, Object Graphs & Leak Vectors

#### Finding 11: Static Lifetime Retention of Massive Channel Datasets in `EpgChannelMatcher`
* **Severity:** **P1 - High**
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgChannelMatcher.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/xmltv/EpgChannelMatcher.kt#L15-L33)
* **Mechanism:**
  1. `EpgChannelMatcher.companion object` maintains `private var cachedInstance: EpgChannelMatcher? = null`.
  2. Each instance stores:
     - `byEpgId: MutableMap<String, XtreamStreamEntity>`
     - `byEpgIdLower: MutableMap<String, XtreamStreamEntity>`
     - `byName: MutableMap<String, XtreamStreamEntity>`
     - `byNormalized: MutableMap<String, XtreamStreamEntity>`
     - `normalizedNames: Array<String>`
     - `normalizedStreams: Array<XtreamStreamEntity>`
     - `memoizedMatches: ConcurrentHashMap<MatchKey, MatchResult>` (up to 5,000 entries)
  3. For large IPTV providers (15,000–30,000 channels), this static reference pins **40MB to 80MB of JVM heap** indefinitely across the entire application lifecycle, even when no EPG or Live TV screen is displayed.
* **Impact:** Severe memory pressure on low-RAM Android TV devices (1.5GB–2GB RAM), precipitating Low Memory Killer (LMK) process termination.
* **Remediation:** Convert `cachedInstance` to a `SoftReference` or implement explicit lifecycle-based cache clearing when navigating away from Live TV / EPG screens.

---

#### Finding 12: Unshared OkHttpClient Engines in `TmdbApiService`
* **Severity:** **P2 - Medium**
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/tmdb/TmdbApiService.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/tmdb/TmdbApiService.kt#L34-L43)
* **Mechanism:**
  1. `TmdbApiService` constructs a Ktor client via `HttpClient(OkHttp)`.
  2. It omits `engine { preconfigured = NetworkModule.okHttpClient }` (unlike `JellyfinApiService`).
  3. Ktor instantiates a standalone `OkHttpClient` with its own `ConnectionPool` and `Dispatcher` thread pool.
* **Impact:** Redundant native socket connections, extra background threads, and wasted memory.
* **Remediation:** Configure `engine { preconfigured = NetworkModule.okHttpClient }` in `TmdbApiService`.

---

#### Finding 13: Activity Context Leaks via ViewModel Factories
* **Severity:** **P2 - Medium**
* **Location:**
  - [`core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/CategoryViewModelFactory.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/viewmodels/CategoryViewModelFactory.kt#L8)
  - [`core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/StreamLoaderViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/viewmodels/StreamLoaderViewModel.kt#L750)
  - [`core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/SearchViewModelFactory.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/viewmodels/SearchViewModelFactory.kt#L8)
* **Mechanism:**
  1. Factories declare constructor parameters as `private val context: Context`.
  2. Even if `context.applicationContext` is passed to the constructed ViewModel, the factory object itself retains a reference to the incoming `Context` (which is typically an `Activity`).
  3. When factory instances are remembered in Compose or retained across configuration changes, the Activity is leaked.
* **Impact:** Activity memory leak during screen rotation or navigation transitions.
* **Remediation:** In factory constructors, declare `context: Context` (without `val`) and store only `private val appContext = context.applicationContext`.

---

#### Finding 14: Unbounded In-Memory Playlist Retention in `BaseM3uMediaProvider`
* **Severity:** **P2 - Medium**
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/BaseM3uMediaProvider.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/BaseM3uMediaProvider.kt#L15)
* **Mechanism:**
  1. `BaseM3uMediaProvider` stores all parsed playlist entries in `protected var items = emptyList<MediaItem>()`.
  2. For large IPTV playlists with 50,000–100,000 channels, this retains tens of thousands of `MediaItem` objects and strings directly on the JVM heap (40MB–100MB).
  3. Operations like `getCategories` and `search` perform repeated linear passes over this collection.
* **Impact:** High heap consumption and GC churn during browsing on large M3U playlists.
* **Remediation:** Index parsed items by category ID in a map or prune unused metadata fields to minimize retained heap per item.

---

### Domain E: State Machine & Stability Vectors

#### Finding 15: `UninitializedPropertyAccessException` Crash Vector on Asynchronous Repository Initialization
* **Severity:** **P1 - High**
* **Location:**
  - [`core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/CategoryViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/viewmodels/CategoryViewModel.kt#L157-L182)
  - [`core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/EpgViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/viewmodels/EpgViewModel.kt#L67-L77)
* **Mechanism:**
  1. `CategoryViewModel` declares `private lateinit var repository: MediaRepository`.
  2. `repository` is initialized asynchronously inside `viewModelScope.launch` in `init`.
  3. Synchronous public methods like `isFavorite(itemId, contentType)` and `isFavoriteCategory(categoryId, contentType)` (lines 479 & 484) access `repository` directly without checking `::repository.isInitialized`.
  4. If a Composable evaluates item favorite states or if the user clicks a favorite toggle before the coroutine in `init` completes, the app crashes with `UninitializedPropertyAccessException: lateinit property repository has not been initialized`.
* **Impact:** Intermittent crash on startup or navigation into categories.
* **Remediation:** Ensure safe nullability / initialization check fallback in all synchronous methods, or adopt a safe `ensureRepo()` pattern.

---

#### Finding 16: EPG Staleness Threshold Disparity in `EpgFileManager`
* **Severity:** **P2 - Medium**
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgFileManager.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/xmltv/EpgFileManager.kt#L1435-L1470)
* **Mechanism:**
  1. `EpgFileManager` defines a dynamic property `staleThresholdMs` based on user settings (defaulting to 24h if disabled or "Never").
  2. However, in `refreshOutdatedSources` (line 1435) and `getStaleSources` (line 1470), it checks:
     ```kotlin
     source.lastIngestedAtMs == 0L || (now - source.lastIngestedAtMs) > SCHEDULED_REFRESH_AGE_MS
     ```
     where `SCHEDULED_REFRESH_AGE_MS` is hardcoded to 1 hour (`3_600_000L`).
  3. WorkManager syncs (`EpgSyncWorker`) and startup auto-refreshes consider any source older than 1 hour as stale, triggering network downloads and database writes even if the user selected a 24-hour or 48-hour sync schedule.
* **Impact:** Unnecessary network data usage, background CPU drain, and flash wear on storage.
* **Remediation:** Replace `SCHEDULED_REFRESH_AGE_MS` with `staleThresholdMs` in `refreshOutdatedSources` and `getStaleSources`.

---

## 3. Prioritized Implementation Roadmap

### Phase 1: Playback Lifecycle, Teardown Safety & Deadlock Immunity (P0 / P1) — Done (`9d20ea62`)
* **Target Files:**
  - `core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt`
  - `core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt`
* **Tasks:**
  1. In `PlaybackViewModel.ensureServiceRunning()`, check if `StreamingPlaybackService.getInstance() == null`; if so, reset `serviceStartRequested.set(false)` before calling `startService()`.
  2. ~~Refactor `PlaybackViewModel.observeServiceState()` to use `coroutineScope`...~~ — **done separately, before this phase started** (`f1341a99`); this is Finding 2, see the verification note above.
  3. In `StreamingPlaybackService.releasePlayerAndSession()`, replace `playerListener!!` and `analyticsListener!!` with null-safe calls (`playerListener?.let { ... }`) to eliminate crash risk during teardown.
  4. Ensure all methods conform to the single-return style rule.
* **As implemented:** task 1 was the important one — `serviceStartRequested` was only ever reset in `onCleared()`, never in `stopAndRelease()`, so a service killed by TV backgrounding or `LiveTvSplitLayout` losing its preview target (ViewModel surviving either way) left the flag permanently `true` and `startService()` silently no-op'd forever after. Fixed in `ensureServiceRunning()`, guarded by the same `getInstance() == null` check already there.

---

### Phase 2: Database Concurrency & Lock Contention (P1) — Done (`a1fdd54e`)
* **Target Files:**
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/db/XtreamDatabase.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/provider/SettingsDatabase.kt`
  - ~~`core/network/src/main/java/org/njarasoa/fijerena/core/network/MediaRepository.kt`~~ — dropped, Finding 5 is a false positive, see verification note above.
* **Tasks:**
  1. Add `.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)` to `XtreamDatabase.getInstance()`.
  2. Add `.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)` to `SettingsDatabase.getInstance()`.
  3. ~~Refactor `loadFavoriteSnapshotLocked()`...~~ — not done, not a bug.
* **As implemented:** Room's `JournalMode.AUTOMATIC` default already resolves to WAL unless `ActivityManager.isLowRamDevice()` is true — not provable from source alone whether this app's actual Shield/Bravia hardware trips that heuristic, but setting WAL explicitly is free insurance either way and matches the convention `EpgIndexDatabase` already uses.

---

### Phase 3: Coroutine Hierarchy, Cancellation & Dispatching (P1) — Done (`15db52a5`)
* **Target Files:**
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/XmltvSearchService.kt`
  - `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/SearchViewModel.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/remote/RemoteM3uMediaProvider.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/local/LocalMediaProvider.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/smb/SmbClient.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/MediaProviderFactory.kt`
* **Tasks:**
  1. Rethrow `CancellationException` in `XmltvSearchService.search()` and `SearchViewModel.kt` category prefetching.
  2. Wrap `RemoteM3uMediaProvider.connect()` and `downloadWithRetries()` in `withContext(Dispatchers.IO)`.
  3. Wrap `LocalMediaProvider.connect()` in `withContext(Dispatchers.IO)`.
  4. In `SmbClient.kt`, ensure `disconnect()` cannot deadlock on the UI thread when `connect()` is running.
  5. Refactor `MediaProviderFactory.create()` to use single-return style and avoid duplicate creations.
  6. Add `@Volatile` to cross-thread mutable state in `EpgBrowserViewModel` and `SearchViewModel`.
* **As implemented:** task 2 wraps all of `connect()` (which calls `downloadWithRetries()` transitively), not just that one function, since the whole call chain needed to move off Main. Task 4's fix goes further than "ensure it cannot deadlock" — `SmbClient` was rewritten so the slow handshake builds independent local objects entirely outside any lock, with only the final field-swap synchronized (a generation counter discards the built connection if a `disconnect()` landed mid-handshake). Task 5 uses `ConcurrentHashMap.computeIfAbsent`, which is both single-return and closes the duplicate-creation race in one move. Confirmed via `sed`/`grep` sweep that only these two files (`RemoteM3uMediaProvider`, `LocalMediaProvider`) have provider-level `connect()` implementations missing a dispatcher switch — no other instances found.

---

### Phase 4: Memory Optimization, Object Lifecycles & Leak Elimination (P1 / P2) — Not started
* **Target Files:**
  - ~~`core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgChannelMatcher.kt`~~ — dropped, Finding 11 is already solved better, see verification note above.
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/tmdb/TmdbApiService.kt`
  - `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/CategoryViewModelFactory.kt`
  - `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/StreamLoaderViewModel.kt`
  - `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/SearchViewModelFactory.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/BaseM3uMediaProvider.kt`
* **Tasks:**
  1. ~~In `EpgChannelMatcher.kt`, convert static `cachedInstance` to a soft reference...~~ — dropped.
  2. In `TmdbApiService.kt`, configure Ktor `HttpClient(OkHttp)` to reuse `NetworkModule.okHttpClient`.
  3. In all ViewModel factories, convert `private val context: Context` constructor properties to non-property parameters and store only `private val appContext = context.applicationContext`.
  4. Optimize memory footprint in `BaseM3uMediaProvider` by indexing items by category ID — task 4 needs a closer look at `getCategories`/`search`'s actual access patterns before committing to "index by category" as the right fix; not yet verified whether the linear scan cost is real in practice.

---

### Phase 5: ViewModel State Robustness & Background Sync Alignment (P1 / P2) — Not started
* **Target Files:**
  - `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/CategoryViewModel.kt`
  - `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/EpgViewModel.kt`
  - `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgFileManager.kt`
* **Tasks:**
  1. In `CategoryViewModel.kt` and `EpgViewModel.kt`, add safe checks and fallbacks to all synchronous methods (`isFavorite`, `isFavoriteCategory`, `toggleFavoriteCategory`, `loadCategories`) to eliminate `UninitializedPropertyAccessException`. Confirmed on HEAD: `isFavorite()`/`isFavoriteCategory()` specifically lack the `::repository.isInitialized` guard that other `CategoryViewModel` helpers already got in an earlier plan (`docs/plans/concurrency-memory-stability-round2-plan.md` Phase 4) — this is a real gap that plan missed, not a duplicate.
  2. In `EpgFileManager.kt`, align `getStaleSources()` and `refreshOutdatedSources()` to use `staleThresholdMs` instead of hardcoded `SCHEDULED_REFRESH_AGE_MS`. Confirmed real and inconsistent (`launchRefreshStale()` right next to it already does this correctly) — but `SCHEDULED_REFRESH_AGE_MS`'s own comment ("scheduled runs refresh if data is older than 1 hour") suggests this might be an intentional background-minimum-cadence floor rather than an oversight. Needs a product decision before touching it, not a blind fix.

---

## 4. Verification & Testing Strategy

**Status: not yet done.** Each phase's touched modules passed `ktlintCheck` + `compileDebugKotlin` (plus downstream `tv`/`mobile` compile checks) before commit. Nothing below has been run on hardware.

1. **Static Analysis & Compilation:** — done per-phase (see above); a full repo-wide `./gradlew ktlintCheck`/`assembleDebug` has not been run.
2. **Playback Resilience Testing:** — outstanding
   - Start playback on TV, press Home (stopping service), resume app, and verify immediate playback resumption without hanging on `awaitInstance()`.
   - Verify service teardown cleans up all listeners and cancels scopes cleanly.
3. **Database Concurrency Testing:** — outstanding
   - Execute a large Xtream stream sync while concurrently querying favorites and watch history on the UI thread to verify WAL mode prevents read stalls.
4. **Memory Footprint & Leak Profiling:** — outstanding, and still applies once Phase 4 lands
   - Profile heap allocations using Android Studio Profiler after parsing large M3U files and running EPG channel matching.
   - Verify ViewModel factories do not retain Activity context across configuration changes.
5. **Sync & Background Worker Validation:** — outstanding, blocked on the Phase 5 product decision above
   - Verify that setting EPG refresh interval to 24 hours prevents `EpgSyncWorker` from running within 1 hour.
6. **Concurrency Regression Testing (new, Phase 3):** — outstanding
   - Exercise an SMB share: navigate into it, then immediately back out while it's still connecting, repeatedly — confirm no ANR and no resurrected connection after an explicit disconnect.
   - Exercise a Remote M3U or Local provider under a flaky/failing network so the `AppContainer`-side auto-connect fails at least once, then trigger a category refresh — confirm no `NetworkOnMainThreadException`.
   - Rapid-fire search keystrokes against a large EPG index — confirm no doubled query load per keystroke (logcat: only one FTS query pair per settled query, not per cancelled one).
