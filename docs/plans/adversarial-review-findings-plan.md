# Adversarial Review Findings & Stability Plan

**Status:** Phase 2 complete; Phases 1, 3, 4, 5 outstanding
**Scope:** `core:player`, `core:network`, `core:ui`, `scripts`

---

## 1. Summary

Nine defects across Xtream session lifecycle, playback teardown, network client lifecycle, movie-detail latency, and deploy tooling:

1. **Xtream Client Leak on Failed Auth / Exceptions** (`XtreamSessionManager`): `replaceApiService()` only runs on success; exceptions or invalid credentials abandon the constructed `XtreamApiService` and its `HttpClient` + thread pool unclosed.
2. **Provider Cache Eviction Disconnect Gap** (`MediaProviderFactory`): `clearCache(id)` and `clearAllCaches()` remove `MediaProvider` instances without calling `disconnect()`, leaving underlying sockets and API services active.
3. **Playback Service Teardown Publication Race** (`StreamingPlaybackService`): teardown resets `serviceStartRequested` and nulls `instance` before completing or reassigning `instanceReady`, letting a racing `awaitInstance()` call catch the old deferred and fail with `CancellationException`.
4. **Uncaught `CancellationException` in Playback Startup** (`PlaybackViewModel.playStream`): `catch (e: TimeoutCancellationException)` doesn't catch the plain `CancellationException` thrown on service death, silently aborting playback without setting `PlaybackState.Error`.
5. **10-Second Hang in Teardown on Dead Service** (`PlaybackViewModel.stopAndRelease`): calls `StreamingPlaybackService.awaitInstance().stopAndRelease()`, paying an unnecessary 10-second timeout if the service is already dead or stopped.
6. **Isolated Dispatcher Leak in TMDB Client** (`TmdbApiService`): uses `preconfigured` Ktor engine, which still builds a dedicated `Dispatcher` per instance, with no `close()` lifecycle method exposed.
7. **Missing `trimMemory()` in M3U Provider** (`BaseM3uMediaProvider`): the `itemsByCategory` index has no eviction hook under memory pressure. (Narrower than it sounds — see Finding 7 below.)
8. **Hardware Safety Rules Omitted in Deploy Script** (`scripts/deploy-tv-ip.sh`): no device-type check before installing the TV APK, no data backup before `adb install -r`.
9. **Sequential TMDB Enrichment Calls in `getMovieDetail`** (`XtreamMediaProvider`): `fetchMovieCertification` and `tmdb.getMovieDetails` run one after another though they're independent, stacking two full network round-trips on top of `get_vod_info` on every uncached movie-detail load.

---

## 2. Findings & Root Cause

### Domain A: Xtream Lifecycle & Provider Teardown

#### Finding 1: `XtreamSessionManager` leaks `HttpClient` on failed authentication or exceptions
* **Severity:** P1 — High
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/manager/XtreamSessionManager.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/manager/XtreamSessionManager.kt)
* **Mechanism:** `login()`, `restoreSession()`, `updateProviderUrl()`, and `reinitialize()` each construct `XtreamApiService` (which eagerly builds a Ktor `HttpClient(OkHttp)` with its own `Dispatcher` in its constructor), then call `service.authenticate()`, and only reach `replaceApiService(service)` — which closes whatever it replaces — on the success path. If `authenticate()` throws (`IOException`, timeout, serialization error) or the response is invalid (`auth != 1`, inactive status), the function throws before `replaceApiService(service)` runs. The already-constructed `service` is abandoned unclosed.
* **Remediation:** Wrap each construction site so `service.close()` runs on any exit that doesn't end in `replaceApiService(service)` — track assignment success and close in a `finally`, or use `.also`/`runCatching` with an explicit close-on-failure branch.

#### Finding 2: `MediaProviderFactory` evicts providers without calling `disconnect()`
* **Severity:** P2 — Medium
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/MediaProviderFactory.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/MediaProviderFactory.kt)
* **Mechanism:** `clearCache(providerId)` and `clearAllCaches()` remove instances from `providerCache` without invoking `disconnect()`. For `XtreamMediaProvider` this bypasses `repository.logout()` → `sessionManager.logout()`, leaking `apiService`. For `SmbMediaProvider`, the SMB socket connection stays open.
* **Remediation:** Dispatch removed provider instances to `Dispatchers.IO` to call `disconnect()` on eviction. Also clear `tmdbImagesCache` in `XtreamMediaProvider.trimMemory()` and hook `trimMemory()` into `disconnect()`.

### Domain B: Playback Lifecycle & Coroutine Cancellation

#### Finding 3: `StreamingPlaybackService` teardown publication race
* **Severity:** P1 — High
* **Location:** [`core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt#L1028-L1045)
* **Mechanism:** in `releasePlayerAndSession()`:
  ```kotlin
  serviceStartRequested.set(false)
  instance = null
  instanceReady.completeExceptionally(CancellationException("StreamingPlaybackService destroyed"))
  instanceReady = CompletableDeferred()
  ```
  These four operations are unsynchronized. A concurrent `PlaybackViewModel.ensureServiceRunning()` call observing `instance == null` can call `startService()` and `awaitInstance()` before the last two lines run. `awaitInstance()` reads the *old* `instanceReady` reference right before `completeExceptionally()` lands on it, so the caller fails with `CancellationException` even though a new service is starting.
* **Remediation:** synchronize teardown state updates and deferred recreation, or have `awaitInstance()` re-check `instance` nullability and retry against the fresh deferred.

#### Finding 4: Uncaught `CancellationException` in `PlaybackViewModel.playStream`
* **Severity:** P1 — High
* **Location:** [`core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt#L180-L186)
* **Mechanism:**
  ```kotlin
  try {
      val service = StreamingPlaybackService.awaitInstance()
      _currentMetadata.value = metadata
      service.playStream(metadata, resumeFromPosition)
  } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
      _playbackState.value = PlaybackState.Error(context.getString(R.string.player_error_occurred))
  }
  ```
  `awaitInstance()` throws a plain `CancellationException("StreamingPlaybackService destroyed")` when the service is torn down mid-await — not a `TimeoutCancellationException`, so it isn't caught. Playback silently fails to start with no `PlaybackState.Error` and no user-visible signal.
* **Remediation:** catch `CancellationException` from service destruction explicitly (distinguish it from a caller-initiated cancel), or wrap service resolution in retry logic before failing visibly.

#### Finding 5: 10-second hang in `PlaybackViewModel.stopAndRelease()` on a dead service
* **Severity:** P2 — Medium
* **Location:** [`core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt#L223-L229)
* **Mechanism:**
  ```kotlin
  fun stopAndRelease() {
      viewModelScope.launch {
          StreamingPlaybackService.awaitInstance().stopAndRelease()
      }
  }
  ```
  If the service is already null/destroyed, `awaitInstance()` blocks for the full `AWAIT_INSTANCE_TIMEOUT_MS` (10s) awaiting an instance that will never appear, just to call `stopAndRelease()` on it.
* **Remediation:** check `StreamingPlaybackService.getInstance()?.stopAndRelease()` directly instead of awaiting a service that isn't coming.

### Domain C: Network Client Lifecycle & Memory

#### Finding 6: Isolated dispatcher leak in `TmdbApiService`
* **Severity:** P2 — Medium
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/tmdb/TmdbApiService.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/src/main/java/org/njarasoa/fijerena/core/network/tmdb/TmdbApiService.kt#L34-L49)
* **Mechanism:** `TmdbApiService` configures `engine { preconfigured = NetworkModule.okHttpClient }`. Ktor's `OkHttpEngine` always builds its own `Dispatcher` per instance regardless of `preconfigured` (see `docs/plans/xtream-concurrency-fixes-plan.md`, Finding 2) — only the `ConnectionPool` is actually inherited. `TmdbApiService` has no `close()` method, and one instance is held per `XtreamMediaProvider`.
* **Remediation:** make `TmdbApiService` a process-wide singleton — it takes only a fixed `BuildConfig.TMDB_API_KEY` and holds no provider state, so nothing about it needs to be per-provider — or give it its own `ConnectionPool` (as `XtreamApiService` now has) plus an explicit `close()`.

#### Finding 7: Missing `trimMemory()` in `BaseM3uMediaProvider`
* **Severity:** P2 — Medium
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/BaseM3uMediaProvider.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/src/main/java/org/njarasoa/fijerena/core/network/BaseM3uMediaProvider.kt#L15-L27)
* **Mechanism:** `items`'s setter maintains an `itemsByCategory: Map<String, List<MediaItem>>` index (built via `groupBy`) to avoid a linear scan on every category open. That index wraps the same `MediaItem` references already held in `items` — no object duplication — but it has no eviction hook: `BaseM3uMediaProvider` implements no `trimMemory()`, so the index (and `items`/`categories`) stay resident under memory pressure even when nothing else on screen needs them.
* **Remediation:** implement `trimMemory()` to drop `items`/`categories`/`itemsByCategory` when the provider isn't the active one, re-populating on next `connect()`/`getCategories()`. Don't restructure the index itself — it isn't the problem.

### Domain D: Movie Detail Latency

#### Finding 9: Sequential, independent TMDB calls in `XtreamMediaProvider.getMovieDetail`
* **Severity:** P1 — High (user-reported)
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/XtreamMediaProvider.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/src/main/java/org/njarasoa/fijerena/core/network/XtreamMediaProvider.kt#L414-L426)
* **Mechanism:**
  ```kotlin
  return when (val result = repository.getVodInfo(id)) {          // line 414
      is XtreamResponse.Ok -> {
          ...
          if (tmdb.hasApiKey() && tmdbMovieId != null) {
              val certification = fetchMovieCertification(tmdbMovieId)                // line 421
              ...
              val tmdbDetails = runCatching { tmdb.getMovieDetails(tmdbMovieId) }.getOrNull()  // line 425
  ```
  `get_vod_info` has to run first — it's what produces `tmdbMovieId` — but `fetchMovieCertification` and `tmdb.getMovieDetails` are independent of each other (both take only `tmdbMovieId`, neither reads the other's result) and still run strictly one after another. Each hop carries `NetworkModule`'s 30s connect/read timeout with no retry, so a slow or unresponsive endpoint stacks up to three full timeouts back to back on a single detail load.
  This is the traced cause of a real freeze report: switching between alternate stream versions on a movie's detail screen on an NVIDIA Shield froze the UI, including D-pad input, for about a minute, with queued input replaying once it recovered. Every alternate version has a different `vodId`, so switching versions always misses `movieDetailCache`/the persisted detail cache and re-triggers this exact chain — consistent with two ~30s network hops landing back to back on a cold cache miss. Whether the freeze itself traces fully to this latency stack, or is compounded by main-thread contention elsewhere (`MediaRepository`'s `synchronized(favoriteLock)`, which Compose calls synchronously — `MediaRepository.kt:908`), isn't confirmed without an ANR trace; fixing this removes a guaranteed multi-timeout stall regardless.
* **Remediation:** run `fetchMovieCertification` and `tmdb.getMovieDetails` concurrently with `coroutineScope { async { ... } }` / `awaitAll`, since neither depends on the other's result.

### Domain E: Deploy Tooling Hardware Safety

#### Finding 8: Safety rules omitted in parallel deploy script
* **Severity:** P1 — High (data-loss risk)
* **Location:** [`scripts/deploy-tv-ip.sh`](file:///home/tahiry/data/code/mpilasy/fijerena/scripts/deploy-tv-ip.sh)
* **Mechanism:** the script installs to every reachable target with `adb install -r` and:
  1. never verifies device type (e.g. `getprop ro.build.characteristics`) before installing the TV APK;
  2. never backs up `shared_prefs/*` and `providers.db*` (`run-as tar`) before `adb install -r`.

  If a signing-key mismatch or a failed in-place update makes the install go fresh instead of updating, every targeted device's data is gone with nothing to restore — this is the exact rule the project's agent instructions already carry as a hard-won lesson from a real incident that wiped three TVs this way with no backup, nothing recoverable. Of all nine findings here, this is the only one with a real prior incident behind it, which is why it outranks the other P2s despite touching tooling rather than app code.
* **Remediation:** add the device-type check and a mandatory pre-install backup routine to `scripts/deploy-tv-ip.sh`, matching the same rule the rest of the deploy tooling already follows.

---

## 3. Plan of Attack

Ordered for implementation — do these in sequence, not in parallel; each phase is scoped to compile and verify independently before starting the next.

### Phase 1 — Deploy script safety (Finding 8)
Zero code dependency, highest real-world risk, blocks nothing else. Add the device-type check and mandatory `run-as tar` backup to `scripts/deploy-tv-ip.sh` before any other phase touches app code that might need a test deploy.
* **Files:** `scripts/deploy-tv-ip.sh`

### Phase 2 — Xtream session & provider-cache cleanup (Findings 1, 2) — Done
Same area as the lifecycle work already landed on `XtreamSessionManager`/`XtreamApiService` — closed the exception-path gap left open there, and extended the same close-on-teardown discipline to `MediaProviderFactory`'s eviction paths.
* **Files:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/manager/XtreamSessionManager.kt`, `core/network/src/main/java/org/njarasoa/fijerena/core/network/MediaProviderFactory.kt`, `core/network/src/main/java/org/njarasoa/fijerena/core/network/XtreamMediaProvider.kt`
* **Tasks:**
  1. Close `XtreamApiService` on every non-success exit from `login()`, `restoreSession()`, `updateProviderUrl()`, `reinitialize()`.
  2. Dispatch `disconnect()` on `Dispatchers.IO` for providers removed by `clearCache()`/`clearAllCaches()`.
  3. Clear `tmdbImagesCache` in `XtreamMediaProvider.trimMemory()`; hook `trimMemory()` into `disconnect()`.
* **As implemented:** each of the four `XtreamSessionManager` methods wraps its body in try/finally with a `serviceAssigned` flag, closing the constructed `XtreamApiService` on any exit that doesn't reach `replaceApiService()`. `MediaProviderFactory` captures the evicted instance(s) before clearing the map and dispatches `disconnect()` on IO with exceptions swallowed. Verified: `ktlintCheck` + `compileDebugKotlin` for `core:network`, `:tv`, `:mobile`, plus existing Xtream/`MediaProviderFactory` unit tests, all green. Two accepted trade-offs, not blocking: the eviction dispatch uses an untracked `CoroutineScope(Dispatchers.IO)` rather than a shared scope, and disconnecting an evicted provider can surface a caught "Not authenticated" error on a request that was already in flight against it — every call site already null-checks and friendly-error-maps this, so it degrades to a toast, not a crash.

### Phase 3 — Playback teardown & cancellation robustness (Findings 3, 4, 5)
Independent domain from Phase 2 — playback service lifecycle and its ViewModel's cancellation handling.
* **Files:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt`, `core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt`
* **Tasks:**
  1. Synchronize `releasePlayerAndSession()`'s teardown-state update and deferred recreation so a racing `awaitInstance()` can't catch the stale deferred.
  2. Catch or recover from service-destruction `CancellationException` in `playStream()`.
  3. Skip the 10-second `awaitInstance()` wait in `stopAndRelease()` when `getInstance()` is already null.

### Phase 4 — Movie detail latency (Finding 9)
Standalone, user-reported, no dependency on the earlier phases.
* **Files:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/XtreamMediaProvider.kt`
* **Tasks:**
  1. Parallelize `fetchMovieCertification` and `tmdb.getMovieDetails` in `getMovieDetail()` via `coroutineScope { async { ... } }`.

### Phase 5 — TMDB client lifecycle & M3U memory trim (Findings 6, 7)
Lowest severity, self-contained cleanup — do last.
* **Files:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/tmdb/TmdbApiService.kt`, `core/network/src/main/java/org/njarasoa/fijerena/core/network/BaseM3uMediaProvider.kt`
* **Tasks:**
  1. Make `TmdbApiService` a process-wide singleton (or give it its own `ConnectionPool` + `close()`).
  2. Implement `trimMemory()` in `BaseM3uMediaProvider` to drop `items`/`categories`/`itemsByCategory` under memory pressure.

## 4. Verification

Each phase: `ktlintCheck` + `compileDebugKotlin` for every touched module plus `:tv`/`:mobile`, and existing unit tests for touched files, before moving to the next phase. Hardware verification (ANR traces, actual deploy dry-run, playback teardown under real Doze/backgrounding) stays outstanding until phases land — none of this has been run on a device yet.
