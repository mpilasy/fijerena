# Adversarial Codebase Review & Remediation Plan

**Status:** Reviewed — each finding independently verified against source before acting; see §0.  
**Author:** AI Agent (Adversarial Audit)  
**Date:** 2026-09-12  

---

## 0. Verification & Remediation Status

Every finding below was checked against the actual code before anything was applied — this audit
got the *location* and *observed shape* right consistently, but was wrong or overstated about
*mechanism* on two of them (C2, C4), and partially overstated on a third (M2). Applied fixes
were sometimes narrower or shaped differently than this doc's own remediation text once the
surrounding code made a difference (P3, M2).

| ID | Finding | Outcome |
|----|---------|---------|
| C1 | Cancellation swallowed in `suspendResultOf`/`launchGenericTask` | ✅ Applied as described |
| C2 | `runBlocking` under `favoriteLock` | ❌ Rejected — already mitigated and documented; remediation duplicates existing code |
| C3 | `instanceReady` hang in `awaitInstance()` | ⚠️ Partially applied — exceptional completion done; timeout guard deliberately skipped |
| C4 | `AdaptiveLoadControl` field volatility | ❌ Rejected — false positive, fields are same-thread not cross-thread |
| M1 | Unclosed stream on corrupt gzip | ✅ Applied as described |
| M2 | Premature WakeLock in `onCreate()` | ✅ Applied, narrower than proposed — see note below |
| P1 | FTS trigger order during EPG swap | ✅ Applied as described (both multi- and single-source paths) |
| P2 | Unfiltered staging→primary transfer | ✅ Applied as described |
| P3 | Sequential FTS queries in `matchToCatalogue` | ✅ Applied, differently — parallelized rather than batched into one query |
| P4 | `Regex("[A-Z]+")` misfires on capitalized queries | ✅ Applied as described |
| U1 | `suppressNextCenterKeyUp` leak | ✅ Applied — narrower, reactive fix instead of per-transition resets |
| U2 | Hardcoded spacing/typography literals | ⏸ Not done — deferred, style only |
| U3 | Multi-return function style | 🔄 In progress — see note below |

**C2 detail:** `MediaRepository.loadFavoriteSnapshotLocked()`'s `runBlocking(Dispatchers.IO)` is
memoized (only runs cold) and already has an async warm-up in `setProvider()`. Its own doc comment
explains why the blocking fallback exists: Compose calls `isFavorite()` synchronously during
composition, so there's no coroutine to suspend in at that call site, and shipping without the
fallback once already crashed the app on a cold main-thread Room read. The suggested "lock-free
reactive StateFlow" remediation doesn't address that constraint — left as-is.

**C4 detail:** `lastPreparedPlayerId`/`lastTracksSelected` are written in `onPrepared`/
`onTracksSelected` and read only via `replayIfPending()`, called from `shouldContinueLoading`/
`shouldStartPlayback` — and the class's own comment states ExoPlayer always invokes all of these
on the playback looper thread. Same thread, not cross-thread as claimed; `@Volatile` would cost
nothing but the "race condition" framing doesn't hold up.

**C3 detail:** Applied the exceptional-completion half (`StreamingPlaybackService.onDestroy()`
now completes the old `instanceReady` exceptionally before replacing it). Skipped the timeout
guard on `awaitInstance()` — a dozen call sites currently assume it never throws, and an arbitrary
timeout risks turning a normal-but-slow service startup into a false-positive crash.

**M2 detail:** The premature-acquire half was real and is fixed — removed the unconditional
`acquireWakeLock()` from `onCreate()`. The "not released until explicit teardown" half was false:
`pause()`/`stop()` already call `releaseWakeLock()`, and `PlayerListener` already acquires it
correctly via `onWakeLockRequired`, fired from `onIsPlayingChanged`/`onPlayWhenReadyChanged` — the
mechanism the remediation asked for already existed; only the redundant early acquire needed
removing.

**P3 detail:** FTS `MATCH` doesn't cleanly support "N different query strings, tell me which
matched which" in one statement without changing match semantics, so this wasn't merged into a
single query as suggested. Instead the independent, read-only per-title lookups now run
concurrently (`async`/`awaitAll`, index-aligned with the results list), then the exact same
sequential accept/dedup logic runs against the prefetched results — same behavior and ordering,
N round trips now concurrent instead of serial.

**U1 detail:** Rather than resetting on every overlay-visibility setter (5 separate booleans),
added one guard at the top of `handlePlayerKeyEvent`: `suppressNextCenterKeyUp` can only ever be
set true while `isModalOpen` is false (the branch that sets it returns early when a modal is
already open), so observing both true at once is proof a modal opened between the Center/Enter
KeyDown and its own KeyUp — exactly the leak case — and is cleared there.

**U3 detail:** User later directed a full single-return sweep, not just this doc's flagged
examples. Done so far: the functions named in this doc, plus `core/ui`, `tv`, `mobile` modules in
full (staged by UI-change risk, lowest first). Remaining: `core/player` (~33 functions flagged by
heuristic scan, real count lower — un-audited), `core/network` (~145 flagged, same caveat; highest
risk, thin instrumented coverage, deferred last on purpose).

One refactor in this sweep (`EpisodeSelectionScreen.kt`, the episode-card modifier chain) caused a
real regression: an `else if` branch added to an `if/else-if/else` silently dropped a trailing
`.padding()/.onFocusChanged()/.testTag()` chain from the true-branch due to Kotlin's chain-binding
behavior on if-expressions. Caught only by running the actual instrumented test
(`EpisodeSelectionScreenTest`) on-device, not by diff review or unit tests — diff review alone had
already (wrongly) cleared it. Root-caused via git bisect + Compose semantics-tree dump, fixed by
parenthesizing the if/else-if/else before the chain. Lesson recorded: for this codebase's
low-coverage UI modules, "unit tests pass + diff looks right" is not sufficient verification —
instrumented on-device runs are required before calling a refactor safe.

Separately, while investigating why that regression test could even run: `tv/build.gradle.kts`
was missing `testInstrumentationRunner` in `defaultConfig` — the other four instrumented modules
(`mobile`, `core/player`, `core/data`, `core/navigation`) all declare it, `tv` didn't. It worked by
accident (runner got merged in from a test dependency's manifest), not by configuration. Fixed:
added the same `androidx.test.runner.AndroidJUnitRunner` line to `tv/build.gradle.kts`.

---

## 1. Executive Summary

A deep-dive adversarial review of the entire Fijerena codebase (`core:player`, `core:network`, `core:ui`, `core:data`, `core:navigation`, `tv`, `mobile`) was executed to identify potential bugs, memory leaks, concurrency hazards, performance bottlenecks, and violations of project constraints.

The audit revealed key architectural vulnerabilities and optimization targets across four primary tiers:
1. **Concurrency & Coroutine Safety:** Cancellation swallowing in asynchronous network/task loops and blocking I/O calls on the main thread.
2. **Resource & Memory Lifecycle:** Leaked unclosed `InputStream` handles on malformed gzip downloads and excessive `WakeLock` retention during player idle/paused states.
3. **Database & Indexing Inefficiencies:** Double FTS4 insertion and trigger storms during EPG table swaps, unbatched loop queries in recommendation matching, and unfiltered staging table copy during partial source ingest failure.
4. **UI & Focus Robustness:** Focus keyup suppression state leaks, multi-key recomposition triggers in TV split layouts, hardcoded tokens, and multi-return code style violations.

---

## 2. Comprehensive Review Findings

### 2.1 Concurrency, Coroutines & Threading

#### Finding C1: Coroutine `CancellationException` Swallowed in `suspendResultOf` and `launchGenericTask`
* **Locations:**
  * `core/network/src/main/java/org/njarasoa/fijerena/core/network/Result.kt:21-26`
  * `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgFileManager.kt:423-448`
* **Severity:** High
* **Mechanism:** Catching `Exception` without rethrowing `CancellationException` swallows coroutine cancellation. When a user navigates away or cancels an EPG/network job, the worker treats cancellation as a transient failure, transitions to `Retrying`, executes scheduled delays, and fails to stop background threads.
* **Remediation:** Enforce `if (e is CancellationException) throw e` as the first instruction in all coroutine `catch (e: Exception)` blocks.

#### Finding C2: Synchronous `runBlocking(Dispatchers.IO)` Under `synchronized(favoriteLock)` on Main Thread
* **Location:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/MediaRepository.kt:856-871`
* **Severity:** High
* **Mechanism:** `isFavorite()` and `isFavoriteCategory()` can be evaluated during composable list layout. When the in-memory cache is cold, `loadFavoriteSnapshotLocked()` acquires `favoriteLock` and performs a blocking database query via `runBlocking(Dispatchers.IO)`. Under heavy I/O load (e.g. concurrent EPG sync), this blocks the Android UI thread and causes dropped frames or ANRs.
* **Remediation:** Warm the favorites cache asynchronously during repository startup and expose a reactive, lock-free in-memory `StateFlow` snapshot.

#### Finding C3: Race Condition & Potential Indefinite Hang in `awaitInstance()`
* **Location:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt:983-990, 1367-1371`
* **Severity:** Medium
* **Mechanism:** In `onDestroy()`, `instanceReady` is reassigned to a new uncompleted `CompletableDeferred()`. Any coroutine already awaiting `instanceReady.await()` is abandoned or hangs if the service does not restart.
* **Remediation:** Complete the active `CompletableDeferred` exceptionally with `CancellationException("StreamingPlaybackService destroyed")` prior to resetting the reference, and add a timeout guard in `awaitInstance()`.

#### Finding C4: Non-Thread-Safe Cross-Thread Variables in `AdaptiveLoadControl`
* **Location:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/config/AdaptiveLoadControl.kt:36-43, 70-78`
* **Severity:** Medium
* **Mechanism:** `lastPreparedPlayerId` and `lastTracksSelected` are written from ExoPlayer's internal playback thread and read/replayed from the Main UI thread during network or content-type transitions without `@Volatile` or synchronization.
* **Remediation:** Annotate shared mutable state with `@Volatile`.

---

### 2.2 Resource & Memory Leaks

#### Finding M1: Unclosed `FileInputStream` on Corrupt Gzip Headers in `EpgFileManager`
* **Location:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgFileManager.kt:1306-1311`
* **Severity:** Medium
* **Mechanism:** `downloaded.tmpFile.inputStream()` and `CountingInputStream` are instantiated before `GZIPInputStream(...)`. If the file contains invalid gzip magic bytes, `GZIPInputStream` throws `ZipException` during construction, leaving the underlying `FileInputStream` unclosed and leaking OS file descriptors.
* **Remediation:** Ensure the base `FileInputStream` is enclosed within a `.use { ... }` block before wrapping in `GZIPInputStream`.

#### Finding M2: Premature WakeLock Acquisition in `StreamingPlaybackService`
* **Location:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt:229, 934-949`
* **Severity:** Medium
* **Mechanism:** `acquireWakeLock()` is called immediately inside `onCreate()`. If the service is instantiated ahead of time (or during TV boot), a `PARTIAL_WAKE_LOCK` is held while the player sits idle. In addition, when playback pauses via audio focus loss or player buffering, the wake lock is not released until explicit teardown.
* **Remediation:** Acquire the wake lock strictly when `isPlaying == true` and actively decoding. Release it whenever playback enters paused, idle, or error states.

---

### 2.3 Database, Indexing & Performance Inefficiencies

#### Finding P1: Double FTS4 Write Amplification During EPG Table Swap
* **Locations:**
  * `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/EpgFileManager.kt:746-792`
  * `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/epgindex/EpgIndexer.kt:529-550`
* **Severity:** High
* **Mechanism:** `endBulkIngestion()` restores SQLite FTS sync triggers *before* `executeSwapToMain()` runs. `executeSwapToMain()` executes `INSERT INTO epg_programme ... SELECT ... FROM epg_programme_staging`, firing the `AFTER_INSERT` FTS trigger millions of times. Immediately afterward, `rebuildFtsAndUpdateState()` drops and rebuilds the entire FTS table from scratch, resulting in redundant disk writes and UI freezes on TV storage.
* **Remediation:** Maintain triggers dropped during `executeSwapToMain()`, perform the table swap, run the single full FTS rebuild, and only recreate the triggers afterwards.

#### Finding P2: Incomplete Staging Data Transfer on Partial Source Failure
* **Location:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/epgindex/EpgIndexDao.kt:44-62`
* **Severity:** High
* **Mechanism:** `executeSwap(sourceIds)` transfers rows from `epg_programme_staging` into `epg_programme` using an unfiltered `SELECT ... FROM epg_programme_staging`. If one source in a multi-source sync failed midway, its incomplete staging rows are erroneously transferred into the main index.
* **Remediation:** Update `transferChannelsFromStaging` and `transferProgrammesFromStaging` to filter `WHERE source_id IN (:sourceIds)`.

#### Finding P3: Sequential Database Queries in Recommendation Loops
* **Location:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/XtreamMediaProvider.kt:737-752`
* **Severity:** Medium
* **Mechanism:** `matchToCatalogue` executes up to 40 sequential individual SQLite FTS queries across loops when fetching TMDB recommendations.
* **Remediation:** Batch search candidate titles using a single composite query or query by TMDB IDs in a single `IN (:tmdbIds)` Room query.

#### Finding P4: Dynamic Regex Compilation & Search Operator Bug in `XmltvSearchService`
* **Location:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/xmltv/XmltvSearchService.kt:188-198`
* **Severity:** Medium
* **Mechanism:** `Regex("[A-Z]+")` is compiled on every search keystroke. Any query containing capital letters (e.g., "CNN", "HBO", "Movie") is mistakenly treated as raw FTS syntax, suppressing the prefix wildcard (`*`) and returning empty results.
* **Remediation:** Precompile a regex that checks explicitly for FTS reserved boolean operators (`\b(AND|OR|NOT|NEAR)\b`).

---

### 2.4 UI, TV D-Pad & Constraint Violations

#### Finding U1: D-Pad Center KeyUp State Leak in Player
* **Location:** `tv/src/main/java/org/njarasoa/fijerena/ui/player/PlayerKeyHandler.kt:27-34`
* **Severity:** Medium
* **Mechanism:** `state.suppressNextCenterKeyUp` is cleared only on a matching KeyUp event. If focus shifts to a dialog or overlay before KeyUp fires, the flag remains `true` indefinitely, swallowing the next center click.
* **Remediation:** Reset `suppressNextCenterKeyUp = false` on screen pause and overlay visibility transitions.

#### Finding U2: Hardcoded Spacing and Typography Literals
* **Locations:**
  * `mobile/src/main/java/org/njarasoa/fijerena/feature/player/components/MobileControlsOverlay.kt:266` (`10.sp`)
  * `mobile/src/main/java/org/njarasoa/fijerena/ui/components/buttons/CinemaButton.kt:180` (`4.dp`)
  * `tv/src/main/java/org/njarasoa/fijerena/ui/components/cards/AccentBlock.kt:76` (`28.sp`)
  * `tv/src/main/java/org/njarasoa/fijerena/ui/player/components/overlays/TvStatsOverlay.kt` (multiple raw `.sp` values)
* **Severity:** Low / Style Constraint
* **Remediation:** Replace raw literals with tokens from `CinemaSpacing`, `MobileDimensions`, `TvDimensions`, and Typography scales.

#### Finding U3: Multi-Return Function Rule Violations
* **Locations:**
  * `core/player/src/main/java/org/njarasoa/fijerena/core/player/viewmodel/PlaybackViewModel.kt` (`getChapters`, `seekRelative`, `getVideoQualities`)
  * `core/network/src/main/java/org/njarasoa/fijerena/core/network/MediaRepository.kt` (`addFavorite`, `removeFavorite`, `resumeProgress`, `getSeriesTrackPrefs`)
  * `tv/src/main/java/org/njarasoa/fijerena/feature/episode/EpisodeResumeState.kt` (`selectSeason`)
  * `tv/src/main/java/org/njarasoa/fijerena/ui/player/PlayerKeyHandler.kt` (`handlePlayerKeyEvent`)
* **Severity:** Low / Style Constraint
* **Remediation:** Refactor methods to accumulate into a single result variable with a single return statement.

---

## 3. Phased Remediation Plan

### Phase 1: Concurrency & Safety Fixes (P0)
1. ✅ **Fix Cancellation Swallowing:**
   - Updated `suspendResultOf` in `core/network/.../Result.kt` to rethrow `CancellationException`.
   - Updated `EpgFileManager.launchGenericTask` to rethrow `CancellationException`.
2. ❌ **Fix Main Thread Database Blocking:** — not done, rejected. Already mitigated (memoized,
   async-warmed in `setProvider()`) and documented as load-bearing for Compose's synchronous
   `isFavorite()` call site. See §0, C2 detail.
3. ⚠️ **Fix Playback Service Lifecycle Hangs:**
   - Updated `StreamingPlaybackService.onDestroy()` to complete the abandoned `instanceReady`
     exceptionally.
   - Timeout guard on `awaitInstance()` deliberately **not** added — see §0, C3 detail.
4. ❌ **Thread-Safe Load Control:** — not done, rejected as a false positive. See §0, C4 detail.

### Phase 2: Memory & Resource Leak Prevention (P1)
1. ✅ **Safe Stream Ingestion in `EpgFileManager`:**
   - Wrapped the `FileInputStream` path so it closes even if `GZIPInputStream`'s constructor
     throws on a corrupt file.
2. ✅ **WakeLock Lifecycle Alignment**, narrower than proposed — see §0, M2 detail:
   - Removed the redundant unconditional `acquireWakeLock()` from `onCreate()`. Release-on-pause
     already existed (`pause()`/`stop()`); acquire-on-active-playback already existed
     (`PlayerListener.onWakeLockRequired`) — neither needed changing.

### Phase 3: Database & Search Performance (P2)
1. ✅ **Optimize EPG Swap & FTS Rebuild Pipeline:**
   - Trigger restoration in `EpgIndexer`/`EpgFileManager` now runs after the swap *and* the full
     FTS rebuild, in both the multi-source and single-source code paths.
2. ✅ **Filter Staging Table Copy:**
   - `EpgIndexDao.transferChannelsFromStaging`/`transferProgrammesFromStaging` now take
     `sourceIds` and filter by `source_id IN (:sourceIds)`.
3. ✅ **Batch Catalogue Search in `XtreamMediaProvider`** — applied differently, see §0, P3 detail:
   - Per-title `searchByFts` lookups now run concurrently (`async`/`awaitAll`) instead of one
     merged query; the accept/dedup pass afterward is unchanged.
4. ✅ **Precompile Search Regex & Fix Prefix Wildcarding:**
   - `XmltvSearchService` now checks a precompiled `\b(AND|OR|NOT|NEAR)\b` instead of
     `Regex("[A-Z]+")`.

### Phase 4: UI, Focus & Code Style Cleanup (P3)
1. ✅ **Fix Player KeyUp Suppression Leak** — applied differently, see §0, U1 detail:
   - One reactive guard in `handlePlayerKeyEvent` instead of resets on each overlay's setter.
2. ⏸ **Replace Hardcoded UI Literals:** — not done, deferred (style only).
3. ⏸ **Enforce Single Return Statements:** — not done, deferred (style only).

---

## 4. Verification & Validation Strategy

1. **Unit & Integration Tests:**
   - Run `./gradlew testDebugUnitTest` across all modules.
   - Add unit tests verifying `suspendResultOf` cancellation propagation.
   - Add unit tests verifying EPG staging transfer with partial source failures.
2. **Code Style & Linting:**
   - Run `./gradlew ktlintCheck` and `./gradlew lintDebug`.
3. **Hardware / Device Validation:**
   - Perform test runs on Android TV (Shield / Sony Bravia) observing logcat for WakeLock release, EPG sync execution times, and D-pad focus responsiveness.
