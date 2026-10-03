# Next-Level Rock-Solid Resilience & Professionalism Plan

**Status:** In progress. Phase 0 done 2026-10-02 (R-07, R-19, R-10); R-10 verified on the TV emulator. Phase 1 done 2026-10-02 (R-02, R-03, R-17, R-01); R-01 verified on the TV emulator. Phase 0's GitHub Actions run passed (JDK 21, Lint). Phase 2 done 2026-10-02 (R-09, R-25, R-08, R-11). Phase 3 done 2026-10-02 (R-13, R-12, R-04, R-06 steps 1-3; step 4 deferred); 473 unit tests; R-04 and R-06 verified on emulators. Phase 4 code done 2026-10-02 (R-05, R-15, R-20, R-22); 481 unit tests. Every finding was traced in source at `33ffd673`; none of Phases 1-6 has been reproduced on a device yet.
**Date:** 2026-10-02
**Scope:** `core:player`, `core:network`, `core:ui`, `tv`, `mobile`, manifests, CI. The sync server only where the client depends on it.
**Goal:** Close the remaining crash loops, silent data loss and silent failures; make focus and error recovery on TV dependable; and add the guardrails (exception boundaries, crash-loop safe mode, CI gates) that keep it that way.
**Follows:** `20261001_rock-solid-stability-resilience-plan.md` (complete). Its findings are not repeated here; where a finding below builds on one of them, it says so.

---

## 1. What this revision changed

The first draft of this plan (13 findings, F-01 to F-13) was checked against `HEAD` line by line, and then the codebase was reviewed again from four angles: architect, senior dev lead, UI/UX, and performance. Result:

| Outcome | Count | Notes |
|---|:---:|---|
| Draft finding confirmed, kept | 4 | Draft F-01 (now R-01), F-05 (folded into R-06), F-11 and F-12 (R-22) |
| Draft finding kept, but its mechanism or impact corrected | 4 | F-02 (R-12: a crash, not a freeze), F-03 (R-13: the root cause is a cross-thread race), F-06 (R-05: the exception it catches is never thrown, which affects 14 files, not one), F-10 (R-19) |
| Draft finding downgraded | 2 | F-04 (R-25: track calls are already bounds-checked), F-13 (stays deferred as before) |
| **Draft finding refuted, dropped** | 3 | F-07, F-08 and F-09; see §6 |
| **New findings** | 20 | Marked 🆕 below |

**The draft's remediation for F-01 would have caused data loss.** It proposed copying `XtreamDatabase.setAsideIfNewer` onto `providers.db`, which means rebuilding an empty provider table. The launch-time orphan sweep (R-02) deletes every `watch_state` and `favorite_state` row whose `providerId` is not in `providers.db`. So once the user added a provider back, it would wipe all watch history and favourites. R-01 and R-02 must be fixed together.

**Complexity / Risk legend:** *Complexity* is the size of the change (Low: one file or a few lines; Medium: several files or a new component; High: cross-module ownership change). *Risk* is the chance the fix itself breaks something users rely on (Low: isolated, easy to test; Medium: on a hot path or changes behaviour across screens; High: touches data ownership or many call sites at once).

**Verification legend:** **CONFIRMED** means the failure path was traced end to end in source. **PLAUSIBLE** means the mechanism is in source, but the trigger needs reproducing on a device before anything is fixed.

**Baseline (2026-10-02, `33ffd673`):** `./gradlew testDebugUnitTest ktlintCheck` passed, with 418 tests and 0 failures. `scripts/check-cancellation.sh` passed.

---

## 2. Ground rules

These carry over from the 2026-10-01 plan, plus one new rule (rule 7).

1. **One finding, or one tight cluster, per commit.** Never mix player, sync and Compose changes. Commit per phase on `main`.
2. **Reproduce every PLAUSIBLE item before fixing it.** If it doesn't reproduce, downgrade or drop it, and record why here.
3. **Device safety.** Emulators first. Never install, uninstall, `pm clear` or run `connectedAndroidTest` on any device, emulator or hardware, without asking first. Back up with `scripts/backup-app-data.sh` before touching real hardware, and deploy only with `scripts/deploy-*.sh`. Test with the jellyxtream provider for VOD and iptv-org for live, never bears/bearstv.
4. **Docs travel with the change.** That covers this plan's Done notes and Status, plus whichever of `docs/FEATURES.md`, `NAVIGATION_GUIDE.md`, `RUN_GUIDE.md`, `RELEASE_NOTES.md` and the AGENTS.md rules a change affects. A Room change also updates `docs/DATABASE_SCHEMA.md` and its schema JSON in the same commit.
5. **New code follows the single-return rule.** Existing early returns are not rewritten here.
6. **Stable dependencies only.**
7. 🆕 **No destructive self-healing.** Code that runs automatically (startup, workers, sync) must never delete user data (`watch_state`, `favorite_state`, `sync_*`, `providers`, `profiles`) based on an inference. Only an explicit user action, or a received tombstone, may delete user data.

---

## 3. Severity matrix

| Sev | Findings |
|---|---|
| **P0**: crash loop, data loss | R-01 `providers.db` downgrade crash loop · 🆕 R-02 automatic sweep deletes watch history and favourites by inference · 🆕 R-03 startup sweep failure crashes every launch |
| **P1**: wrong state, silent failure, security | 🆕 R-04 watch progress stops saving after the playback service restarts · 🆕 R-05 TV focus retries are dead code (14 files) · R-06 provider instances go stale after sync (incl. draft F-05) · 🆕 R-07 exported debug receivers allow sync-account takeover · 🆕 R-08 catalogue sync failures are reported as success · 🆕 R-09 no exception boundary in ViewModels or composition coroutines · 🆕 R-10 crash-loop safe mode |
| **P2**: hardening, UX, performance | 🆕 R-11 two workers still call `setForeground` unguarded · R-12 playback teardown not exception-safe · R-13 channel list / index race · 🆕 R-14 network calls have no overall deadline, and cancellation is not honoured · 🆕 R-15 TV error screens land no focus · 🆕 R-16 credentials in logs, CrashLog and shared diagnostics · 🆕 R-17 full-table orphan scan on every cold start · 🆕 R-18 watch-progress sync churn · R-19 CI gaps · 🆕 R-20 double-click `!!` crashes on cleared dialog state · 🆕 R-21 `installLocation="preferExternal"` · R-22 SMB and Local sources are offered but can't work |
| **P3** | 🆕 R-23 tracked personal export and root clutter · 🆕 R-24 small hygiene items · R-25 `launchServiceAction` catch-all (draft F-04) · 🆕 R-26 no ViewModel tests in `core:ui` |

---

## 4. Findings

### A. Data integrity & startup (architect)

#### R-01: `providers.db` written by a newer build crash-loops an older one [P0, CONFIRMED] (draft F-01, remediation corrected)
- **Complexity:** Medium · **Risk:** Medium — new pre-open check on the launch path; a bug here is itself a launch crash. Needs the instrumented upgrade test.
- **Where:** `core/network/.../provider/SettingsDatabase.kt:313-353` (`getInstance`, no downgrade handling). The crash surfaces in the first query outside an `AppScopes` scope, the nav host's `LaunchedEffect(Unit)`: `tv/.../navigation/TvNavHost.kt:117-149` and `mobile/.../navigation/MobileNavHost.kt:104-124` (`providerRepo.getProviderCount()`).
- **Mechanism:** with no migration path from a higher version, Room throws `IllegalStateException` ("A migration from N to M was required but not found") when it opens a file whose version is newer than `DB_VERSION`. The exception escapes the composition coroutine, so the app crashes on every launch.
- **Trigger:** installing an older debug build over a newer one, which happens whenever a worktree or branch build is deployed to a household TV; or `scripts/restore-app-data.sh` putting back a newer `providers.db`.
- **Impact:** the app can't open. The only way out is `pm clear`, which deletes everything.
- **Fix:** do not rebuild an empty `providers.db`; see R-02. Before Room opens the file, read its header (`PRAGMA user_version` through a raw `SQLiteDatabase`, as `XtreamDatabase.setAsideIfNewer` does). If it is newer than this build, keep the file and show a full-screen, D-pad-focusable "This data belongs to a newer version of Fijerena. Install the newer version." screen. That screen offers an explicit, confirmed "Reset sources" action that sets the file aside as `.v<N>.bak`. Test it alongside `XtreamDatabaseUpgradeTest` (instrumented, not run without asking).
- **Done 2026-10-02 (Phase 1):** `ProvidersDbGuard` (`core:network/provider`) reads `providers.db`'s header read-only in `Application.onCreate` (after `SafeMode.init`) against `SettingsDatabase.DB_VERSION` (now a constant the `@Database` annotation uses). When newer, `startBackgroundWork()` and the nav hosts' `initializeStartup()` don't run and the start destination is `Screen.NewerData`: TV `NewerDataScreen` (focus on Close) / mobile `MobileNewerDataScreen`, with Close (finish) and Reset sources (confirmed: `providers.db{,-wal,-shm}` → `providers.db.v<N>.bak`, latest kept, then `restartProcess`, shared with `SafeMode.leave`). Background workers in a blocked process still fail on open, but WorkManager catches that. Strings en/fr/mg (fr/mg not reviewed). The instrumented test was not added. **Verified 2026-10-02 on the TV emulator** (data backed up first): the emulator's `providers.db` pulled, `user_version` set to 99 on the host, pushed back → "Data from a newer version" screen naming v99 vs v15, focus on Close, no crash, entry in the crash log; Reset sources opens the confirmation (focus on Cancel), Cancel returns focus to Reset sources; Close leaves to the launcher; the file was still v99 with its 5 providers afterwards (the guard's read-only open leaves an empty `-wal`). The original files were then pushed back byte for byte and the app started normally. Reset itself was not pressed on the emulator's real data.

#### 🆕 R-02: The automatic orphan sweep deletes watch history and favourites by inference [P0, CONFIRMED mechanism / PLAUSIBLE trigger]
- **Complexity:** Medium · **Risk:** Medium — changes deletion rules and adds a lock shared with sync; too loose and orphans pile up, too tight and it deadlocks a sync pass.
- **Where:** `core/network/.../provider/ProviderRepository.kt:285-300` (`pruneOrphanedCatalogData`: `(CATALOG_TABLES + "watch_state")`, where `CATALOG_TABLES` includes `favorite_state`, line 47-48). It runs on every cold start (`TvNavHost.kt:146`, `MobileNavHost.kt:121`) and in `EpgSyncWorker.kt:103`.
- **Mechanism:** the sweep deletes every row whose `providerId` is not in a snapshot of `providers.db`. The only guard is "the snapshot is not empty". Every time the two databases disagree, user data goes:
  1. `providers.db` rebuilt or reset (R-01 fixed naively, a corrupt file deleted by the open helper, a partial restore). The first provider the user adds afterwards unlocks the sweep, and every favourite and every history row is deleted.
  2. A race on first sync link. The sweep snapshots provider ids, then live sync inserts a new provider and applies its favourites and history, then the sweep's `NOT IN` deletes them. `sync_version` already marks them applied, so they are never pulled again. Launch is exactly when both run.
  3. Deletes go through raw SQL, so no tombstone is written and the user sees no trace.
- **Impact:** permanent, silent loss of the data `xtream_v2.db` exists to protect (AGENTS.md constraint 9).
- **Fix:** the automatic sweep removes only catalogue caches (`xtream_streams`, `xtream_series`, `xtream_episodes`, `xtream_categories`, `xtream_epg_cache`). It never removes `watch_state` or `favorite_state`. Those go only in `deleteProvider` (an explicit user action or a received tombstone), and the sweep may remove them only for provider ids that have a provider tombstone in `providers.db`. Hold the snapshot and the delete under one lock that `SyncApplier.applyProvider` also takes. Add a regression test: an empty-then-repopulated `providers.db` keeps every `watch_state` row.
- **Done 2026-10-02 (Phase 1), simpler than the fix above:** the sweep never touches `favorite_state` or `watch_state` at all (`ProviderRepository.ORPHAN_SWEEP_TABLES` = the five catalogue tables), instead of matching provider tombstones — `deleteProvider` already removes both, so anything left over is harmless and small. The same inference also deleted credential files (a password lost for good) and orphaned EPG sources **with sync tombstones**, which pushed those deletions to every linked device; now credential files and EPG sources (no tombstones) are removed only by the user's Shrink Database (`userRequested`, formerly `forceVacuum`). No shared lock with `SyncApplier`: the provider list is read again before each step, and with user data out of reach the worst a race does is drop a few catalogue rows the new provider fetches again. Tests: `OrphanSweepRulesTest` (4) locks in the table list and the credential rule — Room isn't available to JVM tests here, so the "repopulated providers.db" test became these rule tests. Not yet run on a device.

#### 🆕 R-03: A failing startup sweep crashes every launch [P0, PLAUSIBLE]
- **Complexity:** Low · **Risk:** Low — moves one call and wraps it.
- **Where:** `TvNavHost.kt:146-148`, `MobileNavHost.kt:121-123`, via `rememberCoroutineScope().launch(Dispatchers.IO) { pruneOrphanedCatalogData() }` with no try/catch. Only the `VACUUM` branch inside is guarded.
- **Mechanism:** an exception that escapes a composition-scoped coroutine goes to the thread's uncaught-exception handler. The sweep's batched `DELETE`s need WAL space, so on a TV with little free storage (an existing concern: `EpgFileManager.shouldUseStaging`) they can throw `SQLiteFullException`, or `SQLiteDatabaseLockedException` while a worker is writing. The sweep runs on every launch, so a persistent cause gives a persistent crash loop.
- **Fix:** move the sweep off the composition into `AppScopes` (or into the worker only, see R-17), wrapped so that a storage error is recorded and skipped. Repro: fill the emulator's `/data` (`fallocate`) and cold-start.
- **Done 2026-10-02 (Phase 1, with R-17):** the launch sweep left the nav hosts. `ProviderRepository.sweepOrphanedCatalogData(onlyIfPending)` never throws (records to `CrashLog`) and is called from its own coroutine in `FijerenaApplication.startBackgroundWork()` (so it is skipped in safe mode and when `providers.db` is blocked) and from `EpgSyncWorker`, where a failure used to fail the guide refresh too. Not reproduced with a full `/data`: skipped by the user's choice (the fix is a catch-and-log, and the start-up sweep now runs only after an interrupted deletion).

#### 🆕 R-10: No crash-loop safe mode [P1, design]
- **Complexity:** Medium · **Risk:** Medium — new startup branch on both apps; a miscounted launch could drop a healthy app into safe mode.
- **Where:** `core:player/diagnostics/CrashLog.kt` records crashes, but nothing acts on them.
- **Gap:** R-01 and R-03 are two separate ways to brick the app at launch. The next one will be a third, and the only recovery today is `pm clear` over `adb`.
- **Fix:** keep a launch counter in a plain file under `filesDir`. Increment it in `Application.onCreate`, and reset it once the home screen has rendered and 30 s have passed. After 3 unfinished launches in 10 minutes, start in **safe mode**: skip the startup sweep, live sync start, EPG initialisation and auto-refresh, and show a focusable screen with "Continue", "Clear caches (keeps sources, favourites and history)" and "Send diagnostics" (redacted, see R-16).
- **Done 2026-10-02 (Phase 0), one change from the fix above:** "healthy" is 30 s of **process** life, not 30 s after the home screen. `Application.onCreate` also runs for processes started only for WorkManager or the playback service, which never show a screen; counting them as unfinished would have put a healthy app into safe mode. `LaunchCounter` + `SafeMode` in `core:player/diagnostics` (`files/safemode/launches`, one timestamp per line, never throws; 7 unit tests). `FijerenaApplication.startBackgroundWork()` and the nav hosts' `initializeStartup()` are skipped in safe mode, so `providers.db` is never opened there (covers R-01's crash path). TV `SafeModeScreen` (focus on Continue) and mobile `MobileSafeModeScreen`; Continue resets and restarts the process; Clear caches = `EpgIndexer.clearAll`, `clearAllCacheForProvider` per source, Coil caches; Show diagnostics opens the existing screen (not yet the redacted share, which is R-16). Strings in en/fr/mg (fr/mg not reviewed by a native speaker). AGENTS.md constraint 11 added. **Verified 2026-10-02 on the TV emulator** (`emulator-5554`, data backed up first), with the trigger in `docs/RUN_GUIDE.md` → Crash-loop safe mode: three faked launches → safe-mode screen, D-pad focus on Continue; Show diagnostics opens the crash log and Back returns with focus on it; Clear caches confirms (focus on Cancel), then empties the EPG index (106 MB → 12 KB), the catalogue (471,796 streams and 95,124 series → 0) and the poster cache, while `watch_state` (117), `favorite_state` (61), providers (5) and profiles (2) are unchanged, and focus returns to Clear caches; Continue starts a new process into the normal start (profile picker); the counter file is deleted 30 s later; three sessions of 34 s each, then a relaunch, start normally. **Mobile safe mode not verified** (user's choice: TV only).

### B. Playback & lifecycle (senior dev lead)

#### 🆕 R-04: Watch progress and track choices stop saving after the playback service is recreated [P1, CONFIRMED code path / PLAUSIBLE frequency]
- **Complexity:** Medium · **Risk:** Medium — changes the player-screen contract on both apps; a double-attached listener would double-write history.
- **Where:** the position-save callback is one mutable field on the service instance (`StreamingPlaybackService.kt:563-567`). The TV player sets it once, from `LaunchedEffect(Unit)` (`TvPlayerScreen.kt:217-222`). Mobile sets it once, from `DisposableEffect(loaderViewModel)` with the non-suspending `getInstance()` (`MobilePlayerScreen.kt:477-485`).
- **Mechanism:**
  - **TV:** `MainActivity.onStop` calls `stopAndRelease()` (`tv/.../MainActivity.kt:100-104`), which destroys the service. Back in the app, `TvPlayerScreen`'s `ON_RESUME` sees `Idle` and calls `playStream()`, which starts a **new** service instance. `LaunchedEffect(Unit)` does not run again, so the new instance has no listener.
  - **Mobile:** on the first playback after a cold start (Movies or TV Shows; no dock has created the ViewModel yet), `PlaybackViewModel.init` starts the service asynchronously. The `DisposableEffect` runs on the first composition, `getInstance()` is still `null`, and no listener is ever set for that session.
- **Impact:** no 10-second progress saves. A crash or kill mid-film loses progress (only the final `finalizeSession` write survives). Audio and subtitle picks are not persisted (`selectAudioTrack` saves through this same callback). On TV, the Recent row and resume point go stale after every Home → return.
- **Fix:** make the callback independent of the instance. Either the service publishes position events on a process-wide `SharedFlow` (the same pattern as `nowPlaying`) that the screen collects, or `PlaybackViewModel` re-attaches the listener whenever `awaitInstance()` returns a new instance. Repro (TV emulator): play a VOD title, press HOME, return, play for 30 s, then `run-as` and query `watch_state.positionMs`.
- **Done 2026-10-02 (Phase 3, `43ef5c91`):** the listener field and `setPositionSaveListener` are gone. The service companion publishes `positionSaves: SharedFlow<PositionSave>` (no replay, buffer 16, drop-oldest, `tryEmit` from Main) from every former listener call: the 10 s loop, state changes, track choices, `disableSubtitles` (-1) and the teardown save. TV collects it in `LaunchedEffect(currentStreamId)` (keyed on the stream: the old `LaunchedEffect(Unit)` kept writing `lastKnownPositionMs` for the first stream after a channel change), mobile in `LaunchedEffect(loaderViewModel)` inside `MobilePlayerContent` (composition-scoped, so PiP keeps saving; the dock shares it, as before). A save with no collector is dropped, not replayed; TV leaving the player still finishes with `finalizeSession`. Tests: `StreamingPlaybackServiceTeardownTest` (5, incl. the publishing side). **Verified 2026-10-02 on the TV emulator** (backed up first): played Aladdin (jellyxtream) → HOME → back via the launcher; logcat shows a second service instance created on return (18:07:32 and 18:08:28 — the bug path), and `watch_state.positionMs` kept advancing afterwards (59.6 s → 79.6 s in 20 s, live position 81 s). Mobile cold-start and PiP not run.

#### R-12: Playback teardown isn't exception-safe [P2, CONFIRMED] (draft F-02, impact corrected)
- **Complexity:** Low · **Risk:** Low — mechanical try-per-stage in one function.
- **Where:** `StreamingPlaybackService.kt:1128-1194` (`releasePlayerAndSession`).
- **Correction to the draft:** a throw here does not "freeze `awaitInstance()` forever". That call is bounded by the 10 s `AWAIT_INSTANCE_TIMEOUT_MS` (`:1668-1671`), and an exception escaping `onDestroy()` kills the process, which also releases the wake lock. The real impact is a crash on exit or on TV `onStop`. When `stopAndRelease()` is called directly (from `MainActivity.onStop` or `TvPlayerScreen`'s dispose), the throw reaches the caller on the main thread, with the same result.
- **Trigger:** the save listener runs first (`:1133-1137`) and does the ViewModel and database work. `player.release()` touches native code.
- **Fix:** run each stage (save, listener removal, `player.release()`, `session.release()`, wake lock, scope cancel, singleton reset) in its own `try`. Record failures with `CrashLog.record`, then continue to the next stage, and keep the `synchronized(instanceLock)` block last and unconditional.
- **Done 2026-10-02 (Phase 3, `43ef5c91`):** each stage runs in `releaseStage(name) { }`, which logs and records `StreamingPlaybackService.release.<stage>` in `CrashLog` and moves on; the singleton reset stays last and unconditional. Instance publication moved to `internal fun publishInstance()` for the test. Tests: `player.release()` or `session.release()` throwing still resets the instance.

#### R-13: A cross-thread race on the channel list and index can index out of bounds [P2, CONFIRMED race / PLAUSIBLE trigger] (draft F-03, root cause corrected)
- **Complexity:** Low · **Risk:** Low — one ViewModel, two fields become one snapshot.
- **Where:** `StreamLoaderViewModel.kt:107-108` (plain `var`s), written on `Dispatchers.IO` (`:174-176`, `:488-490`, `:497`), and read on Main in `nextChannel`/`prevChannel` (`:556-566`).
- **Mechanism:** `streamList` and `currentStreamIndex` are two unsynchronised fields written from IO coroutines, while D-pad handlers read them on Main. A read between `streamList = items` and the index update sees the new, shorter list with the old index, and `prevChannel`'s `streamList[index - 1]` throws `IndexOutOfBoundsException`. The draft's clamp alone narrows the window without fixing visibility.
- **Fix:** keep one immutable `(list, index)` snapshot in a `@Volatile` field (or a `StateFlow`), replace it atomically, and clamp on read.
- **Done 2026-10-02 (Phase 3, `43ef5c91`):** one `MutableStateFlow<ChannelCursor>` (list + index), replaced or `update`d atomically; `next()`/`previous()` clamp to the list they carry. Tests: `ChannelCursorTest` (5, incl. a stale index beyond a shorter list).

#### 🆕 R-11: The `setForeground` fix (prior F-34) reached only one of three workers [P2, CONFIRMED]
- **Complexity:** Low · **Risk:** Low — one shared helper, three call sites; same fix already proven in EpgSyncWorker.
- **Where:** `XtreamSyncWorker.kt:57` and `EpgFtsRebuildWorker.kt:57` call `setForeground(getForegroundInfo())` unguarded. `EpgSyncWorker.kt:68-75` has the guarded version.
- **Impact:** on Google TV and Android 12+, a background start refused with `ForegroundServiceStartNotAllowedException` fails the whole run. The catalogue then isn't refreshed until the next period, and the FTS rebuild left by an interrupted run isn't retried with backoff.
- **Fix:** extract `trySetForeground()` (log it, record it in `CrashLog`, then continue) and use it in all three workers. A unit test asserts that each worker's `doWork` reaches its body when `setForeground` throws.
- **Done 2026-10-02 (Phase 2):** `CoroutineWorker.trySetForeground(tag)` (`core:network/TrySetForeground.kt`) in all three workers; `EpgSyncWorker`'s inline copy removed. Tests: `TrySetForegroundTest` (3, including `EpgFtsRebuildWorker` still rebuilding when `setForeground` throws); the other two workers use the same helper but have no worker-level test (their bodies need the network).

#### R-25: `PlaybackViewModel.launchServiceAction` has no catch-all [P3, downgraded from the draft's P1] (draft F-04)
- **Complexity:** Low · **Risk:** Low — one catch clause.
- **Where:** `PlaybackViewModel.kt:238-250`.
- **Correction:** the track, quality and subtitle calls already bounds-check group and track indices and return early when the session is null (`StreamingPlaybackService.kt:936-1075`). A runtime exception from Media3 here is possible, but no trigger was found.
- **Fix (defence in depth, folded into R-09):** a catch-all that records to `CrashLog`, after the `CancellationException` rethrow.
- **Done 2026-10-02 (Phase 2):** as above.

### C. Provider & sync state (architect)

#### R-06: Provider instances, settings and the active provider go stale after sync and edits [P1, CONFIRMED] (absorbs draft F-05; root cause of prior F-10's "not clean")
- **Complexity:** Medium (steps 1-3) · **Risk:** Medium — split into four steps, each its own commit verified on two linked emulators before the next; steps 1-3 fix every symptom without changing who owns what, and step 4 (the 58-site singleton sweep, the part that was High/High) is optional cleanup, deferred.
- **Where:**
  - `AppContainer.mediaRepositories` (`core/ui/.../di/AppContainer.kt:36-104`) caches a `MediaRepository` bound to a provider instance.
  - `MediaProviderFactory.clearCache` (`MediaProviderFactory.kt:98-109`) evicts and **disconnects** the factory's copy, but leaves the repository's reference alone.
  - Sync calls `clearCache` from `ProviderRepository.applyRemoteProvider/applyRemoteLogin/applyRemoteCategoryFilters` (`:569-651`), but nothing evicts `AppContainer`. `SyncManager`'s listener only handles user data and profile deletion (`SyncManager.kt:86-108`).
  - `ProviderRepository.settingsCache` is per instance (`:88`), and the code constructs 58 separate `ProviderRepository(...)` instances. `SyncApplier` and `SettingsViewModel` each clear only their own cache, so `AppContainer.providerRepository` keeps serving the old settings.
  - A remote delete of the active provider (`SyncApplier.applyProvider` → `deleteProvider(fromRemote = true)`) leaves no active provider. Only the UI path in `ProviderViewModel.deleteProvider` (`:237-251`) promotes another one. `setActiveProvider` (`:402-407`) runs `deactivateAll` and `activateProvider` outside a transaction.
- **Impact:** a password, URL, Jellyfin login or category filter changed on another device does not take effect here until the app restarts. The repository reconnects its old, disconnected instance with the old credentials, and on Jellyfin two sessions with different logins can now coexist. Deleting the active provider on another device leaves this device showing "No provider set" on Home and in browse.
- **Fix:** four steps, one commit each, each verified on two emulators linked through the local sync server before the next starts:
  1. **One settings cache.** Move `ProviderRepository.settingsCache` to a process-wide object shared by every instance, so a write through any instance (sync, Settings, import) clears it for all. Fixes stale category filters and provider settings. Complexity Low · Risk Low.
  2. **A "provider changed" hook.** `AppContainer.onProviderChanged(id)` evicts the cached `MediaRepository` and the factory's provider together; `SyncEngine.Listener` gains `onProvidersChanged(ids)`, called after a pass that applied provider, login or category-filter records, and every `MediaProviderFactory.clearCache` call site in the app calls the container hook instead. A screen that still holds the old repository gets a fresh one on its next `getMediaRepository()`; playback in progress keeps its stream URL and is not interrupted. Fixes stale credentials, URLs and Jellyfin logins after a change on another device. Complexity Medium · Risk Medium (Jellyfin session teardown order; must not cut a playing stream).
  3. **Active-provider promotion on delete.** Move "if the active provider was deleted, activate the first remaining one" from `ProviderViewModel.deleteProvider` into `ProviderRepository.deleteProvider`, and make `setActiveProvider` (`deactivateAll` + `activateProvider`) one transaction. The remote-delete path then lands on another provider and fires the step 2 hook. Fixes "No provider set" after another device deletes the active one. Complexity Low · Risk Low-Medium (navigation must follow the new active provider, as a profile switch already does).
  4. **Optional: `ProviderRepository` as a singleton** via `AppContainer`, replacing the 58 `ProviderRepository(...)` constructions (the prior F-36 sweep). After steps 1-3 it is no longer needed for correctness; deferred unless a step shows per-instance state still matters. Complexity High · Risk Medium (wide mechanical change).
- **Done 2026-10-02 (Phase 3), steps 1-3, one commit each; step 4 not done:**
  - **Step 1 (`42ca0baa`):** `settingsCache` lives in `ProviderRepository`'s companion. Test: `ProviderSettingsCacheTest` (fails without the fix).
  - **Step 2 (`4b257540`):** `AppContainer.onProvidersChanged(ids)` (replaces `evictMediaRepository`) drops the cached repository and the factory's provider together under the mutex. Sync: `SyncApplier.Result.providerChangedIds` (provider, login, category-filter records) → `SyncEngine.Listener.onProvidersChanged` after each committed page (not inside SyncApplier's transaction, which could rebuild from uncommitted rows) → `SyncManager` → container. In-app changes: `MediaProviderFactory.providerChanged(id)` (= `clearCache` + a listener `AppContainer` sets) from `updateProvider`, `deleteProvider`, `saveQuickConnectLogin`, `clearDefaultJellyfinLogins`, `updateProviderSettings` — which also fixes local filter/setting changes not reaching an already-built `MediaRepository`. Tests: `SyncApplierProviderChangesTest` (3), `ProviderChangedListenerTest` (2), `SyncManagerProviderChangesTest` (1); the container's eviction itself isn't unit-tested.
  - **Step 3 (`559265f6`):** `deleteProvider` (local and remote) promotes the first remaining provider when the deleted one was active; `setActiveProvider` runs in one transaction; `ProviderViewModel` no longer promotes. A remote deletion of the active provider sets `SyncApplier.Result.activeProviderDeleted` → `Listener.onActiveProviderDeleted()` → `AppContainer.activeProviderChangedExternally()` (clear caches, emit). `externalProfileSwitches: SharedFlow<String>` became `externalSwitches: SharedFlow<Unit>`, which both nav hosts already collect to rebuild from Home. Tests: `ProviderDeletePromotionTest` (4) + sync tests (3). Untested: whether that rebuild stops a stream playing on the device when another device deletes its source (it rebuilds the back stack like a remote profile switch does).
  - Verification note: the debug `DEBUG_SYNC cmd now` builds its own `SyncEngine` with no listener, so it applies records without the eviction or navigation; verify with both apps in the foreground and let the WebSocket head trigger the pass.
  - **Verified 2026-10-02 on two emulators** (TV `emulator-5554` + Pixel_10 `emulator-5556`, same sync account on the local server, both backed up first, both apps in the foreground):
    - Step 1: on the phone (profile atr, jellyxtream) added an exclude-prefix `4k` filter → TV (switched to atr) pulled it and its Movies list dropped `4k` without a restart. The TV Home card's category count followed only once the source was rebuilt (the step 2 test), not at once.
    - Step 2: a wrong password can't be saved (the edit screen validates the login: "Connection Failed"), so the URL was changed instead, `http://10.0.2.2:8080` → `.../` (same server): the TV pulled it, its playing stream (The Matrix) kept advancing, and on reopening Movies the rebuilt provider authenticated with the new URL (`XtreamSession: Attempting to authenticate with http://10.0.2.2:8080/`) without a restart. A changed Jellyfin login was not tested.
    - Step 3: a throwaway `iptv (copy)` source was made the TV's active one and deleted on the phone; within ~10 s the TV pulled the deletion and rebuilt from its Movies screen to Home on the next source (bears), not "No provider set", no crash. (A real source wasn't deleted: the server keeps deletions, so restoring a backup can't undo one.)
    - Reverted through the app afterwards (filter removed, URL restored, TV back on profile Kid + jellyxtream), since restoring the backups would have been overridden by the server's newer records; both devices' source lists checked equal to before.
    - Seen in passing: while the TV played, it pushed a sync record every ~10 s — R-18 as described.

#### 🆕 R-08: Catalogue sync failures are reported as success [P1, CONFIRMED]
- **Complexity:** Medium · **Risk:** Medium — changes what counts as a failed sync; wrong classification could flag a healthy provider as broken or cause worker retry storms.
- **Where:** `XtreamContentManager.syncStreams`/`syncSeries`/`syncCategories` catch every exception and only log it (`:719-722`, `:832-835`). `XtreamMediaProvider.syncAll` (`:1007-1020`) awaits the results and returns a delta. `ProviderSyncRunner.syncProvider` (`ProviderSyncRunner.kt:59-107`) then returns `Outcome.Success`.
- **Impact:** a timeout or cut connection halfway through `get_vod_streams`, or an expired account (HTTP 403 with `expectSuccess`), leaves `lastSyncError` null. Settings then shows "No changes since last sync" over a failed run, `XtreamSyncWorker` doesn't retry a transient error, and the 4 h freshness stamp blocks the next attempt.
- **Fix:** each task records its failure (rethrow from `execute()` so `RefreshQueue` completes the deferred exceptionally, keeping `CancellationException` separate). `syncAll` collects the per-task failures, and the runner classifies them as `Transient` or `Permanent` as it already does for `connect()`. Partial success still commits what arrived. Repro: jellyxtream on the host with `tc qdisc` or a kill during `get_vod_streams`.
- **Done 2026-10-02 (Phase 2):** the three sync tasks rethrow after logging, so `RefreshQueue` completes their deferreds exceptionally; the 4 h freshness stamp is written only at the end of a successful task. `syncAll` waits for all six (`awaitCatalogTasks`, cancellation rethrown), still runs the exclusion pass, and throws `CatalogSyncException(failures)`; `ProviderSyncRunner` counts it transient if any failure is (IOException family → in-run retries, then `XtreamSyncWorker` `Result.retry()`), permanent otherwise (401/403), message "Catalog sync failed. <reason>" (`error_catalog_sync_failed`, en/fr/mg). Every caller already goes through `syncProvider`, which never throws, and records `updateSyncStats`. A failed task keeps the batches it flushed and never runs its delete phase; the partial delta is dropped (Settings shows the error, not a delta). Each in-run retry downloads the whole catalogue again — up to 3 downloads before the error shows on a flaky line. Tests: `CatalogSyncFailureTest` (8). Not yet reproduced on a device.

#### 🆕 R-18: Watch progress is pushed to the sync server every ~13 s while playing [P2, CONFIRMED mechanism]
- **Complexity:** Low · **Risk:** Medium — small change in SyncManager, but cross-device resume lags by up to 60 s and pause/stop must still flush.
- **Where:** every position save (`StreamingPlaybackService` every 10 s → `recordHistory` → `savePlaybackPosition`) fires the `watch_state` trigger (`XtreamSyncTriggers.kt:55-68`). `SyncManager.onLocalChange` → `requestSync(3 s)` (`SyncManager.kt:263-269`) then runs a full pull and push.
- **Impact:** about 280 passes an hour per playing device. Every other linked device that's in the foreground gets a `head`, pulls, and runs `reloadAfterRemoteChange`, which refills the Recent rows mid-browse on, for example, the TV while someone watches on the phone. That is battery on mobile, Durable Object writes, and needless recomposition.
- **Fix:** coalesce `WATCH` pushes. Push immediately on pause, stop or completion, and otherwise at most once every 60 s. Keep pushing other kinds as they are now.

#### 🆕 R-09: ViewModels and composition coroutines have no exception boundary [P1, CONFIRMED pattern]
- **Complexity:** Medium · **Risk:** Low — wide but mechanical sweep; each site is low risk, and init-error states need UI to show them.
- **Where:** 110 `viewModelScope.launch` sites and 2 `CoroutineExceptionHandler`s in the whole codebase. Examples:
  - `CategoryViewModel.init` (`:191-213`): if `getMediaRepository()` throws (Keystore or credential store, `providers.db` open), the app crashes, and before that `repositoryDeferred` never completes, so every `awaitRepository()` waits forever.
  - `EpgManagementViewModel.nextRefreshAtMs` (`:129-133`) runs `calculateNextRefreshTime`, whose `parts[0].toInt()` (`:563-575`) is unguarded, inside `stateIn(viewModelScope)`. `AppSettings.applyRemoteSetting` (`AppSettings.kt:160-175`) writes `epg_refresh_time` and `epg_refresh_interval` straight to prefs, bypassing the setters' validation. So a value such as `"4:00 AM"` from another app version or a corrupted record crashes the app every time EPG management opens, on every linked device.
  - `StreamLoaderViewModel.stopPlayback` and `recordHistory` run suspend database reads with no guard.
- **Fix:**
  1. A `launchGuarded {}` extension for `viewModelScope` (and `rememberCoroutineScope`) that rethrows cancellation, records to `CrashLog` and turns a failure into the screen's `Error` state. Adopt it in the `init` blocks first.
  2. A CI grep gate (alongside `check-cancellation.sh`) that rejects new bare `viewModelScope.launch` in `core/ui/viewmodels`, with an allow-list that only shrinks.
  3. `applyRemoteSetting` goes through the same clamps and parsers as the setters, and drops a value it can't parse.
- **Done 2026-10-02 (Phase 2):** `CoroutineScope.launchGuarded(name, context, start, onError, block)` in `core:ui/utils` runs the block in `coroutineScope` (so a failing child is caught too), rethrows cancellation, logs, records `coroutine <name>` in `CrashLog` and calls `onError`. Converted: all 12 sites of `CategoryViewModel` (init moved to a retryable `startRepository()` that shows `UiState.Error`; `repositoryDeferred` is deliberately not failed, since the Live TV preview panels await it from composition and would crash), `StreamLoaderViewModel` (init, `recordHistory`, `toggleFavorite`, history/enrich jobs, `stopPlayback` keeping ATOMIC + NonCancellable; `stopPlaybackAwaited` got its own try), `SettingsViewModel.pruneDatabase`, `EpgManagementViewModel`, `EpgViewModel`, `EpgBrowserViewModel`, `SearchViewModel` init, `DiagnosticsViewModel`. The setters turned out to validate nothing, so `applyRemoteSetting` gained its own checks: `AppSettings.parseRefreshTime` (HH:mm, 0-23/0-59), the interval must be one of `EPG_REFRESH_INTERVAL_OPTIONS` (0 would schedule every 15 minutes, so dropped rather than clamped), theme non-blank; anything else is dropped and the local value stays. `calculateNextRefreshTime` uses the same parser (malformed → no schedule). Gate: `scripts/check-viewmodel-launch.sh` + allow-list (98 → 60 bare launches in 14 files; `ProviderViewModel`/`SettingsViewModel` init still bare), in CI after the cancellation gate. Tests: `LaunchGuardedTest` (5), `AppSettingsRemoteSettingTest` (5). Composition-scope sites in tv/mobile not swept. **Verified 2026-10-02 on the TV emulator** (backed up first): `epg_refresh_time` set to `4:00 AM` in `app_settings.xml` → EPG management opens, no crash (the old parse threw on `"00 AM".toInt()`). That run showed a display bug: "no next run" (0) was formatted as the epoch, "Daily. Next at 6:00 PM" in CDT — also briefly visible to everyone before the value loads; TV and mobile now show "Daily" alone when there is no next run, rechecked on the emulator. Original prefs restored byte for byte; normal display "Daily. Next at 2:00 AM". The sync-side validation is covered by the unit tests only; R-08's emulator repro (stopping jellyxtream mid-sync) was skipped by the user's choice.

#### 🆕 R-07: Exported debug receivers allow account takeover [P1, CONFIRMED, security]
- **Complexity:** Low · **Risk:** Low — manifest only; check the adb debug commands still work (shell holds DUMP).
- **Where:**
  - `tv/src/main/AndroidManifest.xml:53-60`: `EpgSyncDebugReceiver` is `exported="true"` with no permission, **in the main manifest**. Its kdoc says "excluded from release builds via BuildConfig.DEBUG guard in the manifest", but no such guard exists.
  - `core/ui/src/debug/AndroidManifest.xml`: `SyncDebugReceiver`, also exported with no permission.
- **Mechanism:** the household devices run **debug** builds (R8 and release are parked). Any app installed on the TV can broadcast `DEBUG_SYNC` with `cmd=setup --es url <attacker server>`. This device then links to the attacker's account and pushes every provider record, **passwords included**, sealed with a key the attacker holds. Alternatively, `cmd=scan --es qr <attacker handoff code>` hands the existing account (key and all) to the attacker's device. `DEBUG_EPG_SYNC` lets any app force full EPG downloads (`ExistingWorkPolicy.REPLACE`) at will.
- **Fix:** add `android:permission="android.permission.DUMP"` to both receivers. The adb shell holds `DUMP`; third-party apps can't. Move `EpgSyncDebugReceiver` into a `debug` source set. Fix its kdoc.
- **Done 2026-10-02 (Phase 0):** `EpgSyncDebugReceiver` moved to `core/network/src/debug` with its own debug manifest; both receivers require `DUMP`. Merged manifests checked: both debug APKs carry both receivers with the permission, neither release APK carries either. Side effect: mobile debug builds now get the EPG receiver too (harmless, mobile runs the same worker). **Checked 2026-10-02 on the TV emulator:** `adb shell am broadcast` still reaches both (`DEBUG_SYNC status` logged the link; `DEBUG_EPG_SYNC` ran `EpgSyncWorker`), and the installed debug APK's manifest carries `permission="android.permission.DUMP"` on both. Refusal of a different app's broadcast is not tested end to end: the emulator is a `user` build (no `su`) with no other debuggable app, so it would take installing a throwaway sender app. Note for anyone retrying with `run-as org.njarasoa.fijerena am broadcast …`: it is delivered, because Android always lets an app's own uid through its own permission, so it proves nothing. `docs/epg_guide.md` and `docs/RUN_GUIDE.md` updated.

### D. TV focus & UX (UI/UX)

#### 🆕 R-05: TV focus restore and retries rely on an exception Compose no longer throws [P1, CONFIRMED in Compose source] (supersedes draft F-06)
- **Complexity:** Medium · **Risk:** Medium — helper is small, but 24 sites change TV focus behaviour everywhere; needs the full D-pad smoke pass.
- **Where:** 24 `try { requester.requestFocus() } catch (_: IllegalStateException) { retry or "not handled" }` sites in 14 files:
  - `TvChannelListOverlay.kt:63-75`
  - `StreamList.kt:248-255, 360-364, 540-543`
  - `CategoryList.kt`, `EpisodeSelectionScreen.kt` (5 sites), `MovieDetailsScreen.kt` (2), `TvEpgBrowserScreen.kt`, `EpgGridLayout.kt`, `ProviderSelectionScreen.kt`, `ImportDialogs.kt` (2)
  - shared components: `FocusReturn.kt`, `ReadOnlyFieldWithEdit.kt`, `TvSearchTextField.kt`, `TvSelectorDialog.kt`, `CinemaAlertDialog.kt`
- **Mechanism:** in the resolved Compose UI (1.10.x; checked in the `ui-android` 1.10.0 sources, `FocusRequester.findFocusTarget`), a requester with no attached node prints a warning and returns `false`. The no-argument `requestFocus()` returns `Unit`, so nothing throws. The retry branches never run. `StreamList.kt:248-255` goes further: it marks the restore as done (`lastFocusedItemId = focusTargetId`) on what its own comment calls "success", so a failed restore is never retried.
- **Impact:** when layout is slow (Bravia, or a long list after Back), focus doesn't land. Remote presses go to the background player or the window root, focus restore after Back is lost, and editors give focus back to the top of the form. This is the AGENTS.md "focus lands somewhere visible and returns on Back" rule, failing intermittently.
- **Fix:** one helper in `tv/ui/components/input`: `suspend fun FocusRequester.requestFocusWithRetry(frames: Int = 30): Boolean`, which loops `requestFocus(FocusDirection.Enter)` (the overload that returns `Boolean`) across `withFrameNanos` until it returns true, with an optional fallback requester. Replace all 24 sites, and add a ktlint or grep gate against `catch (_: IllegalStateException)` next to `requestFocus`. Verify with the D-pad smoke pass from the prior plan's F-32 on the TV emulator (with animations off so it is fast, then with `adb shell setprop debug.hwui.overdraw` load to make layout slow).
- **Done 2026-10-02 (Phase 4):** `requestFocusWithRetry(maxFrames = 30, fallback)` in `tv/ui/components/input/FocusRetry.kt`; all 24 sites plus 6 more that hid the same thing behind `runCatching`/`catch (Exception)` (safe mode, newer data, profile picker, search, player controls) converted; `StreamList` marks a restore handled only when it succeeded; fallbacks where obvious (channel overlay → the list, resume card → Play, episode row → tab row). `CinemaAlertDialog` (core:ui, can't see the tv helper) loops inline. Gate: `scripts/check-focus-retry.sh` in CI (flags all 24 originals when run on the old sources). **Correction:** the no-argument `requestFocus()` is `@Deprecated(HIDDEN)` in 1.10 and source calls already compiled to `requestFocus(Enter): Boolean`, so the result was there and ignored — same defect. Side effects to check: Back from deep in the episode list now really leaves when the tab row can't take focus within ~30 frames (the old give-up branch was dead); CategoryList retries for ~0.5 s and could in theory land after StreamList's Back-restore. Not yet run on a device.

#### 🆕 R-15: TV error screens land no focus and are implemented five times [P2, PLAUSIBLE]
- **Complexity:** Medium · **Risk:** Low — new shared component replacing five; auto-retry needs a connectivity signal.
- **Where:** five private `ErrorScreen`s with no `FocusRequester`:
  - `feature/category/components/CategoryStates.kt:47-77`
  - `episode/EpisodeSelectionScreen.kt:2294`
  - `epg/TvEpgGuideScreen.kt:137`
  - `player/TvPlayerScreen.kt:397`
  - `movie/MovieDetailsScreen.kt:692`
- **Impact:** the most common TV failure is starting while the network is still coming up after wake. It lands on an error with no visible focus. The first D-pad press only moves focus onto Retry, and nothing retries by itself once the network is back.
- **Fix:** add one shared `TvErrorState(message, onRetry, onBack)` that focuses Retry on entry (via R-05's helper). Have it retry once automatically when `NetworkMonitor` reports connectivity regained. Mobile gets the same auto-retry.
- **Done 2026-10-02 (Phase 4):** `TvErrorState(message, onRetry, modifier, title, retryLabel, onBack, backLabel)` replaces the five (each keeps its texts; movie and series errors gained Retry; the player's buttons are now a row with Retry primary); focus lands on Retry via `requestFocusWithRetry`; Back in `onPreviewKeyEvent` when given. `RetryWhenOnline(onRetry)` (core:ui) watches the default network with its own callback (not `NetworkMonitor`, which only the playback service starts): online = `INTERNET` + `VALIDATED`, `onLost` = offline; retries once per offline→online change while the error is shown, never for an error shown while online; a network that never validates (captive portal) never auto-retries. Mobile: one `RetryWhenOnline` line in each of 7 error branches, layouts unchanged. TV search and EPG browser errors weren't among the five and are unchanged. `ACCESS_NETWORK_STATE` declared in core:ui's manifest (lint). Tests: `RetryWhenOnlineTest`. Not yet run on a device.

#### 🆕 R-20: A double click on a dialog crashes with `!!` on state the first click cleared [P2, PLAUSIBLE]
- **Complexity:** Low · **Risk:** Low — local captures in click lambdas.
- **Where:**
  - `mobile/.../settings/SettingsScreen.kt:112-157` and `tv/.../settings/SettingsScreen.kt:346-374` (`pendingParsedImport!!` inside `onConfirm`, `onOverwrite`, `onDuplicate` and `onSkip`)
  - `TvEpgBrowserScreen.kt:967, 1123` and `MobileEpgBrowserScreen.kt:653, 755` (`matchedStream!!`)
- **Mechanism:** the first click sets the state to `null`. A second click (a double tap, or OK auto-repeat on a remote) is dispatched before recomposition removes the dialog, and its lambda hits `!!`.
- **Fix:** capture the value when composing (`val parsed = pendingParsedImport ?: return`) and pass it into the dialog. Grep the rest of the 50 `!!` sites in Compose lambdas for the same shape.
- **Done 2026-10-02 (Phase 4):** import dialogs (TV and mobile `SettingsScreen`) capture their data at composition but the buttons read the live state with `pendingParsedImport?.let { }`, so a second click does nothing rather than importing twice (better than a captured copy, which would have stopped the crash but imported twice); mobile's three conflict lambdas share `resolveImport`. EPG "Watch now" (TV and mobile): `matchedStream` is an immutable `val`, so its `!!` could never fail — the real double-click defect was opening two players; the button now navigates only while `pendingConfirmAiring` is still set. The other `!!` sites are null-checked composition reads, none in click lambdas. Not yet run on a device.

#### R-22: SMB and Local are offered in Add Source but can't work [P2, CONFIRMED] (draft F-11 + F-12)
- **Complexity:** Low · **Risk:** Low — UI gating behind dev mode plus JSON builder.
- **Where:**
  - SMB: `SmbMediaProvider.kt:117` emits `smb://`, and `StreamingMediaSourceFactory` (`DefaultDataSource`) has no SMB scheme, so every play fails.
  - Local: `MobileAddProviderScreen.kt:454-458` saves an empty config (`TvAddProviderScreen.kt:313`: "picker will be added later"), so the source has nothing to list.
  - SMB config is built by string interpolation (`MobileAddProviderScreen.kt:456, 509`), so a `"` or `\` in the host or share produces invalid JSON.
- **UX verdict:** shipping a source type that can't play is worse than not offering it.
- **Fix (now):** show SMB and Local in Add Source only in developer mode. Build the config with `buildJsonObject`.
- **Fix (later, own plan):** an SMB `DataSource` and a SAF folder picker with `takePersistableUriPermission`.
- **Done 2026-10-02 (Phase 4, "now" part):** `addSourceTypes(isDevMode, editedType)` (core:ui) drives both Add Source pickers: Xtream, Jellyfin, Remote M3U, plus SMB and Local in developer mode; an existing SMB/Local source keeps its type when edited. `smbSourceConfig(host, share)` (core:network, `buildJsonObject`) replaces the four string-built configs (tests with `"` and `\`). Not yet run on a device.

### E. Performance & network (performance)

#### 🆕 R-14: No end-to-end network deadline, and cancellation isn't honoured mid-transfer [P2, CONFIRMED]
- **Complexity:** Medium · **Risk:** Medium — timeouts set too tight break slow but working panels; must exclude bulk catalogue and EPG downloads.
- **Where:**
  - `NetworkModule.okHttpClient` (`core/player/.../network/NetworkModule.kt`) has connect, read and write timeouts but no `callTimeout`. No `HttpTimeout` plugin or `withTimeout` wraps any API call; the codebase has one `withTimeout` call, the service wait.
  - `Call.await()` (`core/network/.../utils/OkHttpExt.kt:15-43`) resumes with the `Response` even when the continuation was already cancelled, so the body is never closed and a pooled connection leaks.
  - `EpgFileManager.downloadSource` (`:1210-1240`) reads the whole body in a blocking loop with no `ensureActive()`, so Cancel or Clear-all-data keeps downloading a 100+ MB XMLTV file to the end.
- **Impact:** a panel that trickles bytes (one per 29 s is enough) holds Login, category load or detail on a spinner indefinitely, with no way out but Back. A cancelled EPG refresh keeps the network, storage and wake lock busy.
- **Fix:**
  - Add `callTimeout` (or a Ktor `HttpTimeout` with a request timeout) to the request/response API clients (Xtream metadata, Jellyfin, TMDB, sync), around 30-60 s. Bulk catalogue and EPG downloads keep only their per-read timeouts.
  - Change `Call.await` to `continuation.resume(response) { response.close() }`.
  - Add `coroutineContext.ensureActive()` to the EPG download loop every buffer.

#### 🆕 R-17: Every cold start scans the whole catalogue for orphans [P2, CONFIRMED]
- **Complexity:** Low · **Risk:** Low — a persisted flag around an existing call.
- **Where:** `pruneOrphanedCatalogData` is called on every launch (`TvNavHost.kt:146`, `MobileNavHost.kt:121`). It runs `DELETE … WHERE rowid IN (SELECT rowid FROM t WHERE providerId NOT IN (…) LIMIT 1000)` (`ProviderRepository.kt:265-276`) over six tables, each a full scan, because `NOT IN` can't use the `providerId` index. That is a 250k+ row scan of `xtream_streams` and `xtream_episodes` on the writer connection, while Home makes its first queries.
- **Fix:** the sweep is needed only after a deletion. Persist a `needs_orphan_sweep` flag in `deleteProvider` and in sync's provider tombstone handling, run the sweep only when the flag is set, and keep the worker's run as the safety net. Folds into R-02 and R-03.
- **Done 2026-10-02 (Phase 1):** `AppSettings.orphanSweepPending` (`orphan_sweep_pending`, documented in `docs/DATABASE_SCHEMA.md` §4) is set when `deleteProvider` starts and cleared when it finishes; app start sweeps only while it is set. Sync's remote deletion goes through `deleteProvider`, so it is covered.

#### Deferred, unchanged
- Prior F-13, the `runBlocking` favourite snapshot. Still deferred: revisit only if Diagnostics shows main-thread stalls. **Next level:** route StrictMode violations (debug) into `CrashLog`, so main-thread disk regressions show up in Diagnostics rather than only in logcat.

### F. Security, privacy & release hygiene (senior dev lead)

#### 🆕 R-16: Credentials leak into logcat, CrashLog and shared diagnostics [P2, CONFIRMED]
- **Complexity:** Medium · **Risk:** Low — redaction helper plus call sites; risk is only over- or under-masking diagnostics.
- **Where:**
  - `StreamingPlaybackService.kt:667, 679` log `dataSpec.uri`. Xtream URIs carry `/<user>/<pass>/` in the path.
  - `XtreamSessionManager.kt:106, 113` log the panel URL.
  - ExoPlayer, OkHttp and Ktor exception messages embed the full URL, including the query `password=…`. `CrashLog.record` stores them, and `MobileDiagnosticsScreen.kt:85` shares the log through a chooser.
- **Fix:** add one `Redact.url()` (masking path segments and query values that match the stored username and password, plus `password`, `token` and `api_key` parameters). Apply it in `CrashLog.record` (the message and every cause) and at the logging sites. Remove the "DIAGNOSTIC (temporary)" `StartupTiming` logs or redact them.

#### 🆕 R-21: Both apps ask to be installed on external storage [P2, CONFIRMED]
- **Complexity:** Low · **Risk:** Medium — one attribute, but an install already moved to external storage may need a manual move back; test an update over an existing install.
- **Where:** `android:installLocation="preferExternal"` in `tv/src/main/AndroidManifest.xml:4` and `mobile/src/main/AndroidManifest.xml:4`.
- **Impact:** on a Shield with adopted USB storage, or a phone with an adoptable SD card, the app can be placed on removable media. Unplugging that media kills the app, and WorkManager jobs and the media session lose their component. Google's guidance is that apps with services, sync or widgets should not use external install.
- **Fix:** use `internalOnly` (or remove the attribute). This is a manifest change only, but an already-moved install needs a manual move back.

#### R-19: CI gaps [P2, PLAUSIBLE / CONFIRMED] (draft F-10 widened)
- **Complexity:** Low · **Risk:** Low — CI only; Android Lint needs a baseline so existing warnings do not block.
- CI pins **JDK 17** (`.github/workflows/android-build.yml:21`), while every module compiles with `JavaVersion.VERSION_21` and README asks for 21 (`1a127ea9`). Each run should fail at `compileDebugJavaWithJavac` (`invalid source release: 21`) unless something provisions a toolchain. Confirm with one manual run and fix the workflow to use 21.
- No Android Lint step, although AGENTS.md says to run `lintDebug` after changes. Add it, with a baseline file so existing warnings don't block.
- `check-cancellation.sh` scans only files containing `suspend fun`. Ten files with `catch (e: Exception)` inside `LaunchedEffect`, `launch` or `collect` lambdas are skipped (for example `TvAddProviderScreen.kt`, `MobileAddProviderScreen.kt`, `PlaybackServiceConnection.kt`, `SettingsViewModel.kt`, `SearchScreen.kt`, `TvEpgBrowserScreen.kt`). Widen the file filter to any file using a coroutine builder.
- Add the R-09 and R-05 grep gates here.
- **Done 2026-10-02 (Phase 0 part: JDK 21, Lint, wider cancellation gate):** workflow on JDK 21 and runs `lintDebug` after ktlint (still `workflow_dispatch` only). Every module has `lint { baseline = file("lint-baseline.xml") }` and a committed baseline; it carries one real error, the missing Malagasy `error_saved_login_lost` in `core:network`. `check-cancellation.sh` now scans any file using a coroutine builder: 18 new sites in 9 files — 4 real fixes (`MediaProviderFactory` ×3 disconnects, `SettingsSyncQueue`), 14 marked `cancellation-ok` (non-suspend functions, or a try with no suspension point). The R-05/R-09 gates go in with those phases. Not run on GitHub Actions yet.

#### 🆕 R-23: Personal export and scratch files are tracked in git [P3, CONFIRMED]
- **Complexity:** Low · **Risk:** Low — file removal only; `fijerena_settings.json` history rewrite already done, see Done note.
- `fijerena_settings.json` (tracked; last commit `2b7f1c2e`) holds real provider names, server URLs, usernames, favourites and watch history. It has no passwords. The repo has a GitHub remote. Also tracked: `patch.diff`, `run_test.sh`, `test_plan.sh`, `guides.json`, `epg_browser.png`. Untracked but present: `classes_extract/`.
- **Fix:** `git rm --cached` the rest, add them to `.gitignore`, and move anything worth keeping into `docs/` or `scripts/`.
- **Done 2026-10-02 (`fijerena_settings.json` only, as the user asked):** removed from every commit of `main`, `docs/cast-to-tv-plan` and the agent worktree branch with `git filter-branch`, and ignored in `.gitignore`. A local copy is in `backups/fijerena_settings.json`, and a pre-rewrite bundle is in `backups/pre-history-rewrite-20261002.bundle` (both gitignored). GitHub keeps the old history until a force-push of the rewritten branches. Other files untouched.

#### 🆕 R-24: Small hygiene items [P3, CONFIRMED]
- **Complexity:** Low · **Risk:** Low — independent one-file fixes.
- `SettingsSyncQueue.kt:19` uses a bare `CoroutineScope(SupervisorJob() + IO)`, which breaks AGENTS.md constraint 6. Switch it to `AppScopes`.
- `QrScanner.kt:121, 129`:
  - `providerFuture.get()` in the listener can throw (CameraX init failure), uncaught on Main.
  - In `onDispose`, it blocks Main if the future isn't done yet.
  - Fix: guard the listener; in `onDispose`, unbind only if the future is done.
- `MobileCategoryListScreen.kt:252, 263`: `ViewModelProvider(activity)[PlaybackViewModel]` creates the ViewModel, which starts the playback service, just to call `stop()`. Use the existing instance if there is one.
- `EpgIndexer.kt:445, 526, 640`: `PRAGMA synchronous = OFF` during the swap and rebuild transactions. A power cut mid-swap (common on TV boxes) can corrupt `epg_index.db`. Requery's default handler then deletes it, so the guide is rebuilt rather than bricked. Use `NORMAL` unless measurements show OFF matters.
- `RemoteM3uMediaProvider` uses one temp-file name per provider, so two concurrent `connect()`s overwrite each other's download. Use `File.createTempFile`.
- `FijerenaApplication.onCreate` runs the startup steps in one coroutine, so an early failure (for example `pruneSyncTombstones`) skips every later step, credential warm-up included. Run each step in its own guarded block.

#### 🆕 R-26: The ViewModel layer has no unit tests [P3, CONFIRMED]
- **Complexity:** Medium · **Risk:** Low — test harness for core:ui ViewModels (fake repository, main dispatcher rule); written alongside each fix.
- `core:ui` has 11 unit test files, and none covers `CategoryViewModel`, `StreamLoaderViewModel`, `ProviderViewModel`, `EpgManagementViewModel` or `PlaybackViewModel`, which hold most of the state machines above. Add a test with each fix in R-04, R-06, R-09 and R-13 rather than as a separate sweep.

---

## 5. Phased roadmap

Order: first stop data loss and launch crashes, then make failures visible and recoverable, then fix stale state and focus, then hardening. One commit per phase on `main`; R-23's file removal can go on its own branch.

### Phase 0: Guardrails (small, first) — ✅ done 2026-10-02 (R-10 verified on the TV emulator; R-07 adb path verified, third-party refusal checked in the manifest only; GitHub Actions run passed 2026-10-02; mobile safe mode unverified by choice)
**Complexity:** Medium · **Risk:** Low — receivers and CI are trivial; safe mode is the one real piece of work.
1. **R-07** protect both debug receivers and move the EPG one into a debug source set.
2. **R-19** CI: JDK 21, `lintDebug` with a baseline, widen the cancellation gate.
3. **R-10** crash-loop counter and safe-mode screen (TV and mobile).

### Phase 1: Data loss & launch crashes (P0) — ✅ done 2026-10-02 (R-01 verified on the TV emulator; R-02 by unit tests; R-03 full-disk repro skipped by choice)
**Complexity:** Medium · **Risk:** Medium — all on the launch and deletion paths; R-02 must land before R-01.
1. **R-02** the sweep never touches user tables, sweeps by tombstone only, and is locked against sync; regression test.
2. **R-03 + R-17** sweep moved off the composition, flag-driven, guarded.
3. **R-01** refuse-to-open screen for a newer `providers.db`, with explicit reset. Lands only after R-02.
- **Acceptance:** emulator install of an older build over a newer `providers.db` → "newer version" screen, no crash, `xtream_v2.db` rows intact. Full `/data` → app starts, sweep skipped and logged. Unit test: empty, then repopulated, `providers.db` → `watch_state` and `favorite_state` row counts unchanged.

### Phase 2: Silent failures become visible (P1) — ✅ done 2026-10-02 (R-09 verified on the TV emulator; R-08 emulator repro skipped by choice)
**Complexity:** Medium · **Risk:** Medium — changes error semantics; failures that were hidden will now show, so expect new user-visible errors.
1. **R-09** `launchGuarded` + `init` blocks + validated `applyRemoteSetting` + grep gate.
2. **R-08** catalogue sync failures propagate, with transient/permanent classification.
3. **R-11** `trySetForeground` in all three workers.
- **Acceptance:** a malformed `epg_refresh_time` record from a debug sync server → EPG management opens. A connection killed during `get_vod_streams` → Settings shows the error, and the worker retries.

### Phase 3: Playback & provider state (P1) — ✅ done 2026-10-02 (R-04 verified on the TV emulator; R-06 steps 1-3 on two linked emulators; step 4 deferred)
**Complexity:** Medium · **Risk:** Medium — lowered from High/High (2026-10-02) by splitting R-06 into steps and deferring its singleton sweep; R-04 is the other medium-risk item. Several commits instead of one: R-06's steps each land and get verified on their own, the rest go in one commit.
1. **R-13** atomic channel snapshot, and **R-12** exception-safe teardown (low risk, first).
2. **R-04** position events no longer tied to one service instance.
3. **R-06 step 1** one settings cache. Verify: change a category filter on A → B's lists follow without a restart.
4. **R-06 step 2** "provider changed" hook. Verify: change a provider's password (and a Jellyfin login) on A → B plays with the new one without a restart; a stream playing on B is not cut.
5. **R-06 step 3** active-provider promotion on delete. Verify: delete the active provider on A → B lands on another provider's Home.
6. **R-06 step 4** deferred (optional cleanup; see R-06).
- **Acceptance:** TV emulator: play VOD → HOME → return → 30 s → `watch_state` updated; plus each R-06 step's check above, on two emulators linked through the local sync server.

### Phase 4: TV focus & error UX — ✅ done 2026-10-02 (D-pad smoke pass on the TV emulator: 10 pass, 2 skipped, 0 fail; offline auto-retry not yet run)
- **Smoke pass 2026-10-02** (TV emulator, profile Kid / jellyxtream, Sonnet tester, text checks only, ~75 steps): profile picker, Home, Movies list + Back to the opened row, movie details (Play focused), player OSD (Pause focused) + subtitle picker + Back, episode screen (Play focused, panel Next/Previous keeps focus, Back to the stepped-to row, Back → Episodes tab → out), global search (field focused; after Enter focus lands on the results' header row rather than the first result), Settings → Manage Sources, Diagnostics, Switch Source dialog (Back returns to the chip) — all PASS; EPG (no live TV on this source) and the edit-pencil fields (none found without changing settings) SKIPPED; no FATAL in logcat; no screen ever without focus. Seen, not defects of this phase: Settings doesn't restore its scroll position or focus after Back from Sources or Diagnostics (focus lands on its first item — short of the AGENTS.md "returns it to where the user was" rule; candidate for a later fix); Back from Movies to Home focuses the Switch Source chip, not the Movies card; on TV Shows with an empty Recent, focus lands on the right pane's Refresh button. Tester side effects on the emulator's synced data: a few seconds of an episode played by mistake (a small Continue Watching entry for Kid), and a category favourite toggled on and back off.
**Complexity:** Medium · **Risk:** Medium — focus changes on every TV screen; gated by the full D-pad smoke pass.
1. **R-05** `requestFocusWithRetry` helper, 24 sites, gate.
2. **R-15** shared `TvErrorState` with focused Retry and auto-retry on reconnect (TV and mobile).
3. **R-20** captured dialog state, no `!!` in click lambdas.
4. **R-22** SMB and Local behind developer mode; JSON config built properly.
- **Acceptance:** the prior plan's D-pad smoke pass, plus starting with the emulator's network off → Retry focused → network on → screen recovers on its own.

### Phase 5: Performance, network & privacy
**Complexity:** Medium · **Risk:** Medium — timeouts and sync coalescing change runtime behaviour; the rest is low risk.
1. **R-14** call deadlines, the `Call.await` close-on-cancel, cancellable EPG download.
2. **R-18** coalesced `WATCH` pushes.
3. **R-16** redaction in `CrashLog` and logs.
4. **R-21** `installLocation`.
5. **R-24** hygiene list.

### Phase 6: Lock it in
**Complexity:** Medium · **Risk:** Low — tests only, plus repo hygiene.
- Tests that fail with each fix reverted: R-02 (the sweep keeps user data), R-04 (the listener survives service recreation; `core:player` fake-player harness from prior Phase 6), R-06 (`AppContainer` evicts on a provider change), R-08 (a failing task gives `Transient`), R-09 (a malformed synced setting doesn't throw), R-13 (concurrent list swap and `prevChannel`).
- **R-23** repo hygiene (ask about history).

---

## 6. Dropped from the first draft (refuted)

| Draft item | Why dropped |
|---|---|
| F-07 `JellyfinApiService` never closes its `HttpClient` | Already assessed as prior F-10 and skipped with reason. Closing the client would make the still-referenced instance throw `ClientEngineClosedException`, and the leak is bounded (threads idle out after 60 s, the pool after 5 min). The real defect is that ownership is split between `AppContainer` and the factory, which is now R-06. |
| F-08 `RemoteM3uMediaProvider` leaks sockets | Refuted a second time. `downloadWithRetries` calls `connection?.disconnect()` in `finally` and deletes the temp file on every path (`RemoteM3uMediaProvider.kt:87-145`). The only real issue is the shared temp-file name (R-24). |
| F-09 `SmbClient.disconnect` blocks the main thread | `SmbMediaProvider.disconnect()` already wraps it in `withContext(Dispatchers.IO)` (`:60-66`), and the factory disconnects evicted providers on its own IO scope. |
| F-02 "freeze all future `awaitInstance()` callers" | `awaitInstance()` is bounded by a 10 s timeout. Teardown failure is a crash, not a freeze, and is kept as R-12 with that impact. |
| F-06 "both `requestFocus` attempts throw `IllegalStateException`" | Neither attempt throws (see R-05). The mechanism is wrong, but the symptom is real and wider. |
| Draft baseline "153 actionable tasks" | Replaced by the measured baseline in §1: 418 tests. |

---

## 7. Out of scope

- R8/minify and baseline profiles: parked until public release.
- Replacing `EncryptedSharedPreferences`: deferred (`20260828_secret-store-migration-plan.md`).
- Building SMB playback and the Local folder picker: their own plan (R-22 only hides them).
- Prior F-13 `runBlocking` favourites: deferred, unchanged.
- Rewriting existing early returns: `20260914_codebase-robustness-plan.md`.
- Splitting the very large files (`tv/.../EpisodeSelectionScreen.kt` 2,341 lines, `MediaRepository.kt` 1,939, `StreamingPlaybackService.kt` 1,693, `EpgFileManager.kt` 1,698). This is a maintainability risk, not a stability defect. Split only when a fix above already has the file open, and only along the seam the fix touches.

---

## 8. Verification protocol

- **Every commit:** `./gradlew compileDebugKotlin testDebugUnitTest ktlintCheck` and `scripts/check-cancellation.sh`, plus `cd server && npm test` for anything touching sync.
- **Emulators first**, via `scripts/deploy-tv-emulator.sh` and the mobile equivalent, **after asking**. Check `adb mdns services` and the device notes before asking for an IP. Use jellyxtream for VOD and iptv-org for live.
- **Before any real device:** ask, then back up with `scripts/backup-app-data.sh`. Never `pm clear`, uninstall, or run instrumented tests on hardware.
- **PLAUSIBLE items** (R-03, R-04 frequency, R-15, R-20): reproduce first, and record the repro command in the finding's Done note.
