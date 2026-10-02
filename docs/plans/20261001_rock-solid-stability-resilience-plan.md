# Rock-Solid Stability & Resilience Plan

**Status:** 🚧 **IN PROGRESS** — Phases 0 and 1 done 2026-10-01 (F-24, F-30, F-31; F-20, F-21, F-03, F-17, F-01). Phase 2 in progress: F-08, F-09, F-23, F-22 done. Phases 3-6 not started.
**Date:** 2026-10-01
**Scope:** `core:player`, `core:network`, `core:ui`, `core:navigation`, `tv`, `mobile`, `server`, CI
**Goal:** No crash loops, no silent data loss, no playback dead-ends, no silently stalled sync — and the tooling (crash capture, CI gates, tests) to *prove* it stays that way.

---

## 1. What this revision changed

The first draft of this plan listed 21 findings. Each was re-traced against `HEAD` (`70dcccfc`) before being kept. Results:

| Outcome | Count | Notes |
|---|:---:|---|
| Confirmed as written | 5 | F-01, F-03, F-06 (connection race), F-09, F-19 |
| Confirmed, but mechanism/fix corrected or scope widened | 9 | Sync poison pill, AAD binding, reconnect storm, mobile dock leak, TV resume, TV back stack, profile-switch write loss, EPG tombstone echo, non-atomic deletes |
| Downgraded | 4 | `runBlocking` favourites (P0→P2, deliberate + pre-warmed), Jellyfin pool leak (P0→P3, threads idle out; Jellyfin out of scope per `20260922` plan), `LiveTvSplitLayout` "10 s hang + crash" (P0→folded into F-03), live recycle `BehindLiveWindowException` (P0→P2, HLS-only and recoverable once F-01 is fixed) |
| **Refuted — dropped** | 3 | See §6. VOD resume-to-0:00, transient audio focus kills Live TV, `FocusRequester` crash |
| **New findings** | 18 | Marked 🆕 below. Includes the single highest-impact item in the plan (F-20, destructive migration on the DB that holds watch history and favourites) |

The draft's roadmap also used a different F-numbering from its own catalog (e.g. catalog F-07 = AAD, roadmap F-07 = poison pill). This revision uses **one numbering** everywhere.

**Verification legend:** **CONFIRMED** = traced end-to-end in source. **PLAUSIBLE** = mechanism is real in source, trigger needs on-device reproduction before fixing.

**Baseline:** `./gradlew testDebugUnitTest` → 285 tests, 0 failures (2026-10-01).

---

## 2. Ground rules

1. **One finding (or one tight cluster) per commit.** Never mix sync-protocol, player and Compose changes.
2. **Reproduce before fixing** every PLAUSIBLE item; downgrade or drop it if it doesn't reproduce.
3. **Device safety.** Emulators first. **Never install, uninstall, `pm clear` or run `connectedAndroidTest` on any device — emulator or hardware — without asking first.** Back up `shared_prefs/*` and `databases/*` with `run-as tar` before any device work (`docs/RUN_GUIDE.md`). Deploy with `scripts/deploy-*.sh`, not hand-rolled gradle + adb.
4. **Schema/protocol docs travel with the change.** Any Room migration updates `docs/DATABASE_SCHEMA.md` in the same commit. Any sync wire/crypto change updates `server/README.md` and `docs/plans/20260929_live-sync-plan.md` → Security.
5. **New code follows the single-return rule.** Existing early returns are *not* rewritten as part of this plan (that is `20260914_codebase-robustness-plan.md` Phases 2-3) — it is churn, not stability.
6. **Stable dependencies only.** No RC/alpha bumps proposed here.

---

## 3. Severity matrix

| Sev | Findings |
|---|---|
| **P0** — data loss, crash loop, dead player | F-01 recycle error freezes player · F-03 `ServiceDestroyedException` crashes Compose · F-08 sync poison pill (pull) · F-09 deferred records lost across pages · F-17 mobile dock keeps playing after leaving · 🆕 F-20 destructive migration wipes watch history/favourites · 🆕 F-21 sync credential store crash loop |
| **P1** — wrong state, silent failure, lost writes | F-06 `PlaybackServiceConnection` race · F-07 unauthenticated `updatedAt`/`deleted` · F-12 socket reconnect storm · F-15 closed repo drops writes · F-16 TV Live TV never resumes · F-18 TV provider switch leaves stale back stack · 🆕 F-22 push-side poison pill · 🆕 F-23 non-API sync errors stall silently · 🆕 F-24 app-wide scopes without exception handlers · 🆕 F-30 no crash/ANR capture · 🆕 F-31 CI doesn't run tests |
| **P2** — hardening | F-02 live HLS recycle position · F-11 EPG tombstone echo · F-13 `runBlocking` favourite snapshot · F-14 non-atomic deletes · F-19 mobile player Back skips overlays · 🆕 F-25 HLC poisoning by bad clocks · 🆕 F-26 half-open WebSocket undetected · 🆕 F-27 cancellation swallowed in suspend code · 🆕 F-28 credential store silently wiped / plaintext fallback · 🆕 F-29 `stopPlayback` may never start · 🆕 F-32 Compose artifact version skew · 🆕 F-33 no Room schema export / migration CI · 🆕 F-34 EPG worker `setForeground` outside error handling · 🆕 F-35 `VACUUM` on the hot DB |
| **P3** | F-10 Jellyfin client never closed · 🆕 F-36 manual `ProviderRepository(...)` instantiation (37 sites) · 🆕 F-37 hard `context as ComponentActivity` casts |

---

## 4. Findings

### A. Player engine & playback service

#### F-01: Error during a seamless recycle freezes the player forever [P0, CONFIRMED]
- **Where:** `core/player/.../service/StreamingPlaybackService.kt:184-219` (`performSeamlessRecycle`), `:656-675` (`attemptStreamRetry`), `:725-735` (`handleStreamEndedOrError`), `:410-413` (only reset on `Playing`).
- **Mechanism:** `performSeamlessRecycle()` sets `isRecycling = true`. If the fresh source fails to prepare, `onPlayerError → handleStreamEndedOrError → attemptStreamRetry` hits `if (isRecycling()) return`. `isRecycling` is only cleared on reaching `Playing`, which never happens. `PlayerListener.isInErrorState` also stays `true`, so `updatePlaybackState()` publishes nothing, and the health loop only feeds metrics in `READY`/`BUFFERING`, so it never recycles again.
- **Trigger:** Health monitor recycles a live channel (stall/low buffer) and the reconnect gets a 4xx/5xx, DNS failure or timeout — the exact conditions that cause recycles.
- **Impact:** Black/frozen frame, no spinner (non-`Playing` states were suppressed during the recycle grace), no error, no retry, until the user leaves the channel.
- **Fix:** In `handleStreamEndedOrError()` (error path), if `isRecycling()` clear it and let the hard retry proceed. Keep the guard in `attemptStreamRetry` only for the *no-error* case it was written for. Unit-test the state machine with a fake player (see Phase 6).
- **Done 2026-10-01:** `attemptStreamRetry()`'s only caller is the fault path, so the guard had no good case to keep — removed. **Not reproduced on a device:** the emulators' provider refused every stream (HTTP 511) that day, so no live recycle could be triggered. Still needs the Phase 6 fake-player test and an on-device check on a working live channel.

#### F-02: Live HLS recycle keeps the old playhead [P2, PLAUSIBLE]
- **Where:** `StreamingPlaybackService.kt:216` — `player.setMediaSource(mediaSource, false)`.
- **Mechanism:** Recycles only run for live (`startHealthMonitorLoop` gates on `metadata.isLive`). For HLS live with a sliding window, keeping the old position can land behind the new window → `BehindLiveWindowException`. Most Xtream live is MPEG-TS (progressive, unseekable), where this is harmless.
- **Impact today:** the error routes into F-01 and freezes. Once F-01 is fixed, the generic retry (`setMediaSource(source)` = reset position) recovers it — so this is a seamlessness issue, not a lockup.
- **Fix (after reproducing on an HLS live channel):** `setMediaSource(mediaSource, /* resetPosition = */ metadata.isLive)` and, for `ERROR_CODE_BEHIND_LIVE_WINDOW`, `seekToDefaultPosition(); prepare()` instead of a counted retry.

#### F-03: Uncaught `ServiceDestroyedException` in `LaunchedEffect`s crashes the app [P0, CONFIRMED]
- **Where:** `core/ui/.../components/EmbeddedPlayerSurface.kt:42-45`, `tv/.../feature/player/TvPlayerScreen.kt:181-197` (two effects), `mobile/.../feature/player/MobilePlayerScreen.kt:401-409`, `tv/.../feature/category/components/LiveTvSplitLayout.kt:174-178`.
- **Mechanism:** `releasePlayerAndSession()` fails the pending `instanceReady` with `ServiceDestroyedException` (a plain `Exception`, by design). These five call sites await it bare inside `LaunchedEffect`; an uncaught non-cancellation exception in a composition-scoped coroutine crashes the process. `PlaybackViewModel` already handles both exceptions correctly (`PlaybackViewModel.kt:150-160, 209-229, 238-248`).
- **Correction to draft:** `withTimeout`'s `TimeoutCancellationException` is a `CancellationException` — it ends the effect silently, it does **not** crash. The draft's "10 s hang then crash" in `LiveTvSplitLayout` (old F-16) is therefore only this `ServiceDestroyedException` path; there is no UI freeze (the effect suspends, it doesn't block).
- **Trigger:** Leaving the TV player (`stopAndRelease`) while another surface's effect is still awaiting; `stopAndRelease` → re-entry races.
- **Fix:** One helper, e.g. `suspend fun StreamingPlaybackService.Companion.awaitInstanceOrNull(): StreamingPlaybackService?` returning null on timeout/destroyed (rethrowing real cancellation). Use it at all five sites. Drop `LiveTvSplitLayout`'s `setContentType(LIVE_TV)` effect entirely — `StreamingPlaybackService.playStream()` already sets the profile from `metadata.isLive` (`:562-568`).
- **Done 2026-10-01:** `StreamingPlaybackService.awaitInstanceOrNull()`; the four remaining effects use it and `LiveTvSplitLayout`'s `setContentType` effect is gone. Smoke-tested on the TV emulator: no-target split state, preview start, rapid Back/re-enter ×3 — no crash.

#### F-06: `PlaybackServiceConnection` shares mutable state across flow collections [P1, CONFIRMED]
- **Where:** `core/player/.../service/PlaybackServiceConnection.kt:16-17, 26-49`.
- **Mechanism:** `controllerFuture`/`controller` are instance fields. `PlaybackViewModel.ensureServiceRunning()` cancels the old `connectToService()` job and starts a new collection; the old `awaitClose` runs afterwards and reads the field — now the *new* future — then **releases it** and nulls both fields. The old future's listener can likewise read the new future.
- **Impact:** The ViewModel's `_controller` points at a released `MediaController` → audio/subtitle track lists and chapters silently empty after a service restart.
- **Fix:** Keep the future local to each `callbackFlow` block; release only that one. Drop the fields (or set them only via identity-checked compare).

### B. Live sync (`core:network/sync`, `core:ui/sync`, `server`)

#### F-07: `updatedAt` and `deleted` are not authenticated [P1, CONFIRMED]
- **Where:** `core/network/.../sync/SyncEngine.kt:150` (`seal(..., aad = keyId)`), `:160` (`open(..., aad = wire.key)`), `:165` (trusts `wire.updatedAt`, `wire.deleted`).
- **Mechanism:** AAD binds only the key id. A compromised or buggy server can flip `deleted=false→true` on any record (remote wipe of a favourite/provider/profile), or rewrite `updatedAt` to win/lose LWW at will.
- **Fix:** AAD v2 = `"v2|$keyId|$updatedAt|$deleted"`. Seal with v2. Open with v2, fall back to v1 (`keyId` only) for records already on the server; re-push v1 records on next local change. The server is unchanged (it never decrypts) — the draft's `server/src/account.ts` edit is not needed. Document in `server/README.md` + live-sync plan → Security.

#### F-08: One unreadable record stalls pulling forever, silently [P0, CONFIRMED]
- **Where:** `sync/SyncApplier.kt:78-99` (no per-record guard); throwing sites `:185-186, 188, 238, 290, 371-372, 448-457` (`SyncPayloads.decode`, `requireNotNull`, `!!` after a presence check); `core/ui/.../sync/SyncManager.kt:197-202`.
- **Correction to draft:** envelope JSON errors are already caught (`SyncEngine.decode` uses `runCatching`). The throwing paths are the *payload* decodes inside `applyX` (a shape from a newer/older app), `requireNotNull(record.payload)`, and `!!` races with a concurrent local provider deletion.
- **Mechanism:** The exception escapes `SyncEngine.syncNow` (only `SyncApiException` is handled), so the page's cursor isn't advanced. `SyncManager.runSync` lands in the generic `catch (e: Exception)` which only logs: no `lastError`, no retry scheduled. Every later pass refetches and re-throws on the same record.
- **Impact:** Sync permanently stuck on that device; settings screen shows no error.
- **Fix:** In `apply()`, wrap each `applyOne` in `try/catch` (rethrow `CancellationException`), count as `skipped`, log key kind + exception class. Replace `!!` with a re-lookup returning `Outcome.Deferred`. Surface `skipped > 0` in `Status` (dev mode shows detail).
- **Reproduced 2026-10-01** end to end: TV emulator linked to a local `workerd` server; a host-side test device pushed a hand-sealed `setting` record with payload `{}`. Old build: every pass threw, cursor stuck at 152, `lastError` null.
- **Done 2026-10-01** (`4f93fffc`), one change from the fix above: a record that throws is **deferred, not skipped** — it waits with the other deferred records and is retried every pass, so a newer app version can still apply it. Recorded in Diagnostics once per kind and exception type; the `Status` counter was not added (Diagnostics covers it). `!!` after presence checks now return `Deferred`. Verified on the same rig: pass completes, cursor moves to 154, the record waits.

#### F-09: Deferred records from earlier pages are lost if a later page fails [P0, CONFIRMED]
- **Where:** `sync/SyncEngine.kt:80-103`.
- **Mechanism:** `store.cursor` is persisted per page; `store.deferred` only after the loop. A failure (network, process death, F-08) on page N drops records deferred on pages < N while the cursor has already moved past them.
- **Fix:** Persist `store.deferred` together with `store.cursor` on every page. (Both live in the same prefs file — write them in one `edit {}`.)
- **Done 2026-10-01** (`59906bf5`): `SyncAccountStore.savePullProgress(cursor, deferred)` writes both in one commit, every page. Not reproduced on a device (needs a >500-record pull failing mid-way).

#### F-12: Socket reconnect storm when HTTP works but WebSocket doesn't [P1, CONFIRMED — mechanism corrected]
- **Where:** `SyncManager.kt:284-306` (`onSocketGone`), `:194` (success resets `retryDelayMs`).
- **Correction to draft:** when the server is fully down, each reconnect also triggers a failing pass that doubles `retryDelayMs`, so there *is* backoff. The storm is the other case: a reverse proxy that doesn't upgrade WebSockets (common self-hosting mistake), or one that drops idle sockets. Then the socket fails, the pass **succeeds**, `retryDelayMs` resets to 5 s, and the device does a full sync pass every 5 s forever. `openSocket()`'s `if (socket != null)` check is also unsynchronised (main thread vs IO).
- **Fix:** Separate `socketRetryDelayMs` (exponential, max 5 min, reset only in `onOpen` after the socket has stayed up for > 1 min). Make the reconnect only `openSocket()` — do not `requestSync(0)` on every attempt, only on `onOpen`. Guard socket open/close with one lock.

#### F-11: Remote provider deletion echoes EPG-source tombstones back [P2, CONFIRMED — downgraded]
- **Where:** `SyncApplier.kt:301-303` → `provider/ProviderRepository.kt:193-208` (`deleteProviderEpgSources`) → `SettingsSyncTriggers` `epg_source` delete trigger.
- **Mechanism:** The applier calls `deleteProvider()` without `applying = 1`, so each EPG source row deletion queues a pending tombstone that is pushed back. Redundant traffic, not a feedback loop (the server accepts once, other devices skip as already-deleted).
- **Fix:** **Not** the draft's "wrap in `inSettingsApply`" — `deleteProvider` also clears `xtream_v2.db`, prefs and runs `VACUUM`; holding a `providers.db` write transaction across all that blocks every settings write for seconds. Instead add `deleteProvider(id, fromRemote = true)` that deletes the EPG sources inside a short `inSettingsApply` and skips re-tombstoning.

#### 🆕 F-21: Sync credential store has no failure handling → crash loop at startup [P0, CONFIRMED]
- **Where:** `sync/SyncAccountStore.kt:19-33` (`EncryptedSharedPreferences.create` in a bare `lazy`), `SyncManager.kt:49` (scope without handler), `:107` (`scope.launch { refreshStatus() }` on every start).
- **Mechanism:** If the Keystore master key is lost or the file can't be decrypted (memory note: already observed after `pm clear`; also OEM Keystore resets), `prefs` throws on first access. That happens inside `SyncManager.scope`, which has no `CoroutineExceptionHandler` → uncaught → process dies — on every launch, before any UI. `AccountManager` and `ProviderRepository` already guard the same call; this store doesn't.
- **Fix:** Same recovery pattern: catch, delete the file, recreate; report `link = null` with a "Sync link lost — pair again" status. Add a handler to `SyncManager.scope` (F-24).
- **Reproduced 2026-10-01** on the TV emulator by zeroing the Tink keysets in `sync_account.xml`: `InvalidProtocolBufferException` on the main thread at launch (`SyncManager.onForeground` → `engine.isLinked`), every launch — F-24's scope handler alone didn't cover it.
- **Done 2026-10-01:** the store resets itself (delete + recreate) and is recorded in Diagnostics; the device shows as unlinked and can pair again. If even the reset fails, the store is absent: unlinked, writes ignored, pairing fails with an error — never a plaintext fallback. Verified on the emulator, original link restored afterwards.

#### 🆕 F-22: One invalid record blocks pushing forever [P1, PLAUSIBLE]
- **Where:** `server/src/account.ts:224-229` (whole batch → 400 if any record fails `invalid()`), `MAX_PAYLOAD = 64 KiB` (`:10`); `SyncEngine.kt:107-124` (always retries the same oldest-first batch).
- **Mechanism:** `api.push` throws `SyncApiException(400)`; nothing is marked sent; the next pass picks the same `pending(200)` batch first and fails again. Likely trigger: `CATEGORY_FILTERS` for a large provider (thousands of hidden category ids → base64 + GCM overhead can pass 64 KiB).
- **Fix:** Server: reject per record (`rejected: [{key, reason: "invalid"}]`) instead of failing the batch. Client: pre-check sealed size, log + mark oversized records as sent with a visible `skipped` count; consider compressing filters. Add a server test for a mixed valid/invalid batch.
- **Done 2026-10-01**: server (`99c57454`) rejects invalid records one by one as `invalid: <why>` and applies the rest; only a malformed batch is still `400` (new server test: a mixed batch). Client (`2f541d8b`) drops records whose sealed payload is over 64 KiB before sending, logging them and recording them in Diagnostics, and logs non-stale rejections. Compressing filters not done. Old apps already treat any rejection as done.

#### 🆕 F-23: Non-HTTP sync failures stop retrying and show no error [P1, CONFIRMED]
- **Where:** `sync/SyncApi.kt:153-157` (`json.decodeFromString` outside the `SyncApiException` wrapping), `SyncManager.kt:197-202`.
- **Mechanism:** A captive portal / proxy returning `200 text/html`, or any `SerializationException`, escapes as a non-`SyncApiException` → logged as "Sync pass crashed", no `lastError`, no backoff retry. Same sink as F-08.
- **Fix:** Wrap decode failures as `SyncApiException(status, "Unexpected response")`. Make the generic catch set `store.lastError` and schedule the normal backoff.
- **Done 2026-10-01** (`acb8749d`): non-JSON responses become `SyncApiException`; any other failure of a pass sets `lastError`, is recorded in Diagnostics and backs off like an HTTP failure. Not reproduced on a device (no captive portal on the rig).

#### 🆕 F-25: A device with a wrong clock poisons every device's HLC [P2, CONFIRMED]
- **Where:** `SyncApplier.kt:96-99` (`receive(max hlc)`), `SettingsSyncDao.kt:97`, `SyncVersionDao.kt:36`; tick = `max(now, hlc + 1)`.
- **Mechanism:** Android TV boxes often boot with a wrong RTC until NTP syncs. Records stamped years ahead (or `Long.MAX_VALUE` from a bug or F-07 tampering) drag every device's clock forward permanently; at `Long.MAX_VALUE`, `hlc + 1` overflows negative and every later local edit loses LWW.
- **Fix:** Don't take records with `hlc > now + 1 day` into the clock (apply-or-skip them and log), and saturate instead of overflowing. Unit-test in `SyncMergeTest`.

#### 🆕 F-26: Half-open sync socket is never detected [P2, PLAUSIBLE]
- **Where:** `SyncManager.kt:240-251` — app-level `"ping"` text every 30 s, nobody checks for the `"pong"`.
- **Mechanism:** After a NAT/Wi-Fi drop without FIN, `send()` succeeds into the buffer; no read ever fails. Live updates stop until the app is backgrounded/foregrounded.
- **Fix:** Build the socket client with `NetworkModule.okHttpClient.newBuilder().pingInterval(30, SECONDS)` (OkHttp fails the socket on a missed pong → `onFailure` → reconnect via F-12's backoff). Keep the server's text auto-response for old clients or drop it.

### C. Data layer & storage

#### 🆕 F-20: `xtream_v2.db` — home of watch history and favourites — is wiped on any unmigrated version change [P0, CONFIRMED]
- **Where:** `core/network/.../xtream/db/XtreamDatabase.kt:396` (`fallbackToDestructiveMigration(dropAllTables = true)`), `:28` (`exportSchema = false`).
- **Mechanism:** Since `watch_state` (v17) and `favorite_state` moved into this DB, it holds the only durable copy of user data. Room's destructive fallback fires on any missing migration path **including downgrades** — e.g. installing an older debug APK on a device that had a newer one (routine in this multi-branch, multi-device workflow), or a future version bump that forgets a `Migration`. All history, favourites, sync versions and tombstones vanish silently, and live sync doesn't bring them back: the pull cursor lives in prefs, so the device never re-pulls what it had already received.
- **Fix:**
  1. Replace with `fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6)` (pre-`MIGRATION_7_8` catalog-only versions).
  2. Downgrade: before building Room, read `PRAGMA user_version`; if it's newer than `version`, move the file set to `xtream_v2.db.v<N>.bak` (keep the last one), log loudly, start fresh. User data is recoverable by reinstalling the newer build.
  3. Turn on `exportSchema = true` with `room { schemaDirectory(...) }`, commit schemas, and convert `XtreamDatabaseMigrationTest` to `MigrationTestHelper` (F-33).
  - **Done 2026-10-01** (1 + 2; 3 is F-33): verified on the TV emulator — a v99 file was set aside as `xtream_v2.db.v99.bak`, the app started on an empty v24 DB without crashing, and the event showed in Diagnostics; original data restored afterwards.
  - Longer term: split rebuildable catalog tables from user-data tables into separate DB files so catalog schema churn can never touch user data.

#### F-13: Favourite snapshot can `runBlocking` the main thread [P2, CONFIRMED — downgraded]
- **Where:** `core/network/.../MediaRepository.kt:946-961`.
- **Context:** Deliberate and documented; `setProvider()` pre-warms on IO so the main-thread path is the cold-cache exception. Remaining risk: cold path after `reloadAfterRemoteChange()`/`clearCache()` lands a DB read on Main inside `synchronized(favoriteLock)` — while IO threads block on that same lock.
- **Fix:** Hold favourites in a `StateFlow<FavoriteSnapshot>` loaded on the write dispatcher; synchronous readers read the last snapshot (empty until loaded) and Compose collects the flow. Delete the `runBlocking`.

#### F-14: Multi-statement deletions are not atomic [P2, CONFIRMED — fix corrected]
- **Where:** `profile/ProfileRepository.kt:66-88`, `provider/ProviderRepository.kt:193-208, 220-245`.
- **Correction to draft:** these span two databases plus SharedPreferences, so one `withTransaction` cannot make them atomic. Orphans from a killed provider deletion are already swept by `pruneOrphanedCatalogData()` (run by `EpgSyncWorker`), so the real gap is profile deletion.
- **Fix:** Order for crash-safety: (1) write the profile tombstone + mark the profile `deleting` first, (2) `xtreamDb.withTransaction { watch/favorite/tombstone/version deletes }`, (3) prefs cleanup, (4) delete the row. On startup, resume any profile left `deleting`. Same shape for providers: catalog deletes in one `xtreamDb.withTransaction`.

#### F-15: A closed `MediaRepository` silently drops writes [P1, CONFIRMED — scope widened]
- **Where:** `MediaRepository.kt:258, 1875-1877` (`close()` = `writeScope.cancel()`); callers `AppContainer.kt` `clearAllCaches()` (provider switch), `switchProfile()`, `evictMediaRepository()`.
- **Mechanism:** (a) Writes queued on `writeDispatcher` but not started are cancelled — a favourite toggle or the final progress save from just before the switch is lost. (b) Any ViewModel still holding the old instance (TV back stack, F-18) keeps calling `writeScope.launch {}` on a cancelled scope — every call is a silent no-op.
- **Fix:** `close()` drains first (`awaitPendingWrites()` then cancel) and is made `suspend`. After close, writes log an error in debug builds (`check(!closed)`), so (b) shows up in testing instead of losing data.

#### 🆕 F-35: Full `VACUUM` on the hot database [P2, CONFIRMED]
- **Where:** `ProviderRepository.kt:233-243, 285-292`.
- **Mechanism:** A full `VACUUM` of `xtream_v2.db` (hundreds of MB with a large catalogue) holds the write lock for its whole duration — every `watch_state` save queues behind it — and needs up to 2× the DB size free, which the low-storage TVs don't have (memory: 5556 already needed `pm clear` for space).
- **Fix:** Reuse `EpgIndexer`'s approach: `auto_vacuum = INCREMENTAL` (needs one migration with a single full `VACUUM` at upgrade, guarded by a free-space check) then chunked `PRAGMA incremental_vacuum(N)` with `wal_checkpoint(PASSIVE)` between chunks; skip when free space < DB size.

### D. Lifecycle, navigation & UI

#### F-16: TV Live TV never resumes after the app loses focus [P1, CONFIRMED — widened]
- **Where:** `tv/.../category/components/LiveTvSplitLayout.kt:343-351`.
- **Mechanism:** `ON_PAUSE` → `onFocusLost()` pauses and starts a 30 s stop timer. `ON_RESUME` only cancels the timer — it never calls `resume()` and never re-plays. Short absence (Home, a notification panel, a dialog activity): preview stays **paused on a stale frame**. Long absence (screensaver, HDMI input switch): stopped → **black, Idle**. `TvPlayerScreen` handles the Idle case (`TvPlayerScreen.kt:131-155`); the split layout doesn't handle either.
- **Fix:** On `ON_RESUME`: if `Paused` and live → `playStream(current metadata)` (resuming a stale live buffer is wrong for live — same rule as the service's external-pause handler); if `Idle` and a resolved stream exists → `playStream(...)`. Apply the same live-`Paused` rule to `TvPlayerScreen`.

#### F-17: Mobile docked Live TV keeps playing after leaving the screen [P0, CONFIRMED — widened]
- **Where:** `mobile/.../category/MobileCategoryListScreen.kt:240-247` (system Back is handled), `:486` (toolbar Back → `onBack` with no stop), `:503-511` (EPG/Search actions navigate away with the dock playing), `:399-412` (lifecycle observer removed on dispose).
- **Mechanism:** The dock's `PlaybackViewModel` is Activity-scoped. Only system Back stops it. Toolbar Back pops the screen; Search/EPG push over it. In all three, audio continues, and since the screen's `ON_STOP` observer is gone once disposed, it even keeps playing after the app is backgrounded.
- **Fix:** Route toolbar Back through the same stop-then-pop path as the `BackHandler`. Stop (or pause) the dock before `onSearchClick`/`onEpgClick`. Add `DisposableEffect(isLiveTv) { onDispose { if (!isChangingConfigurations) dock.stop() } }` as the backstop.
- **Done 2026-10-01:** toolbar Back, Search and TV Guide stop the dock before leaving. The dispose-time backstop was **not** added: `MobilePlayerScreen` uses the same Activity-scoped `PlaybackViewModel` (and the service is global), so a stop on dispose could kill the stream the next screen just started. Remaining gap: a back-stack rebuild with the dock playing (profile switched by live sync, provider switch) still leaves it running. Verified on the phone emulator: dock buffering → Search / toolbar Back → `Idle` immediately.

#### F-18: TV provider switch stacks a second Home on top of the old one [P1, CONFIRMED]
- **Where:** `tv/.../navigation/TvNavHost.kt:689-691` (`popUpTo(ProviderSelection) { inclusive = true }`), `:768-770` (`popUpTo(Settings) { inclusive = false }`).
- **Mechanism:** Home → Settings → (Providers →) switch ⇒ back stack `[Home(old provider), Settings, Home(new)]`. Back walks through Settings into the **old** Home, whose ViewModels hold a `MediaRepository` that `clearAllCaches()` just closed — every favourite/progress write from there is dropped (F-15b). Mobile already does it right (`MobileNavHost.kt:479, 549` pop to the graph start).
- **Fix:** `popUpTo(navController.graph.id) { inclusive = true }`, as mobile does.

#### F-19: Mobile player Back exits the player instead of closing an open panel [P2, CONFIRMED]
- **Where:** `mobile/.../player/MobilePlayerScreen.kt:261-262, 735-778` — no `BackHandler` while `showCategoryOverlay`/`showLastWatchedOverlay` are open (only the stats overlay has one).
- **Fix:** `BackHandler(enabled = showCategoryOverlay || showLastWatchedOverlay) { close both }`.

#### 🆕 F-29: Final watch-progress write can be skipped on exit [P2, PLAUSIBLE]
- **Where:** `core/ui/.../viewmodels/StreamLoaderViewModel.kt:584-600`.
- **Mechanism:** `viewModelScope.launch(IO) { withContext(NonCancellable) { … } }` — `NonCancellable` only protects the body once it starts. If the ViewModel is cleared before the IO dispatch picks the coroutine up, a `DEFAULT`-start launch is cancelled before running and the final position is never written.
- **Fix:** `launch(Dispatchers.IO, start = CoroutineStart.ATOMIC)`, or hand the write to the repository's own write scope (which F-15 now drains on close).

### E. Systemic hardening

#### 🆕 F-24: Process-wide coroutine scopes have no exception handler [P1, CONFIRMED]
- **Where:** `SyncManager.kt:49`, `ProviderSyncManager.kt:19` (on **Main**), `RefreshQueue.kt:25`, `EpgFileManager.kt:217`, `XmltvSearchService.kt:33`, `MediaRepository.kt:258`, `XtreamContentManager.kt:73`, `XtreamEpgManager.kt:42`, `MediaProviderFactory.kt:98, 116, 135` (bare `CoroutineScope(Dispatchers.IO)`), `StreamingPlaybackService.kt:265`. Only `FijerenaApplication.kt:70` has one.
- **Mechanism:** Any exception that escapes a `launch` in these scopes goes to the thread's uncaught handler and kills the process — F-21 is one concrete instance.
- **Fix:** One `AppScopes.create(name, dispatcher)` helper = `SupervisorJob() + dispatcher + CoroutineExceptionHandler { log + record to crash log (F-30) }`. Replace all of the above. The handler never rethrows, debug builds included: every build shipped today is a debug build, so "debug rethrows" would change nothing on real devices. Failures surface through Settings → Diagnostics instead.
- **Done 2026-10-01:** `core/player/.../diagnostics/AppScopes.kt`; all 12 scopes above plus `FijerenaApplication`'s startup scope converted (`SettingsSyncQueue` already catches per launch — left as is).

#### 🆕 F-27: Cancellation is swallowed across suspend code [P2, CONFIRMED]
- **Where:** 194 `catch (e: Exception)` sites; files with many and no `CancellationException` handling: `ProviderViewModel.kt` (13), `EpgIndexer.kt` (11), `ProviderRepository.kt` (9), `XtreamMediaProvider.kt` (8), `SettingsExportManager.kt` (7), `MediaRepository.kt` (6); `runCatching` wrapping suspend calls in `MovieDetailsViewModel`, `SeriesDetailsViewModel`, `XtreamMediaProvider` (TMDB).
- **Mechanism:** A cancelled job (screen left, newer load started) catches its own `CancellationException`, carries on, and publishes stale state or writes after its owner is gone — a common root of "wrong item shown after fast navigation" bugs.
- **Fix:** Add `suspendRunCatching {}` (rethrows `CancellationException`) to `core:network`; convert the listed files first; add a grep gate to CI that flags `catch (e: Exception)` in files containing `suspend fun` without a preceding `CancellationException` catch (allow-list for intentional cases).

#### 🆕 F-28: Credential store silently wipes or downgrades to plaintext [P2, CONFIRMED]
- **Where:** `ProviderRepository.kt:816-839` (delete on failure, then **plain `SharedPreferences` fallback**), `AccountManager.kt:62-80` (delete on failure), `MediaProviderFactory.kt:228-248` (delete, return null).
- **Mechanism:** A Keystore hiccup deletes the user's saved logins with no message (they just see auth failures); a second failure in `ProviderRepository` stores passwords **unencrypted**.
- **Fix:** Remove the plaintext fallback. On recovery-by-delete, set a per-provider "credentials lost" flag the UI turns into "Sign in again" instead of a generic error. (The store replacement itself stays deferred — `20260828_secret-store-migration-plan.md`.)

#### 🆕 F-30: No crash or ANR capture [P1]
- **Where:** No `Thread.setDefaultUncaughtExceptionHandler`, no `ApplicationExitInfo` use anywhere; no crash reporter.
- **Impact:** Field crashes on the Shields/Bravia/CCwGTV are invisible unless someone has `adb logcat` running at the time; every finding above was found by reading code.
- **Fix (no third-party SDK):** On start, read `ActivityManager.getHistoricalProcessExitReasons()` (API 30 = `minSdk`) for `REASON_CRASH`, `REASON_ANR`, `REASON_LOW_MEMORY`, `REASON_CRASH_NATIVE`; chain an uncaught handler that appends the stack to a capped ring file (`files/crashlog/`). Show both in a dev-mode "Diagnostics" screen with copy/share. Feeds F-24's handler too.
- **Done 2026-10-01:** `CrashLog` (256 KiB cap, installed first in `FijerenaApplication.onCreate`) and `ProcessExits` in `core/player/.../diagnostics/`; `DiagnosticsViewModel`; `Screen.Diagnostics`; TV `DiagnosticsScreen` and mobile `MobileDiagnosticsScreen` (with Share), opened from the developer-mode card. Off-device: `adb shell run-as org.njarasoa.fijerena cat files/crashlog/crashes.log`. Tests: `core/network/src/test/.../diagnostics/AppScopesCrashLogTest.kt`.

#### 🆕 F-31: CI is manual-only and runs no tests [P1]
- **Where:** `.github/workflows/android-build.yml` — `workflow_dispatch` only; runs `assembleDebug` only.
- **Fix:** Run `testDebugUnitTest`, `ktlintCheck`, and `server` `npm test` (it already has `sync.test.ts`) before the APK build. Add the F-27 grep gate and a "no `fallbackToDestructiveMigration(` without `From`" grep gate when those phases land. Instrumented tests stay manual (emulators only, never real devices).
- **Decision 2026-10-01:** the workflow stays `workflow_dispatch`-only (manual by choice since `3944975e`); it just runs the gates now. **Done** in `.github/workflows/android-build.yml`.

#### 🆕 F-32: Compose artifacts resolve to mismatched versions [P2, CONFIRMED]
- **Where:** `gradle/libs.versions.toml:36` (`composeBom = "2025.06.01"` → foundation 1.8.3), but transitive deps pull `ui`/`runtime` to **1.10.0** (`:tv:dependencyInsight`). Foundation 1.8.3 runs against UI 1.10.0.
- **Impact:** Unsupported combination; behaviour already differs from what the code assumes (e.g. `FocusRequester.requestFocus()` on an unattached node *throws* in 1.8.x and only logs in 1.9+ — which is why the draft's F-18 doesn't crash today). AGENTS.md's version table is also stale (coroutines resolve to 1.11.0, not 1.7.3).
- **Fix:** Move to the latest **stable** BOM that ships UI 1.10.x so every Compose artifact is aligned; re-run focus/D-pad smoke tests on emulator after. Update AGENTS.md table.

#### 🆕 F-33: No exported Room schemas; migration test not in any gate [P2]
- **Where:** `exportSchema = false` in `XtreamDatabase.kt:28`, `SettingsDatabase.kt:24`, `EpgIndexDatabase.kt:22`; `XtreamDatabaseMigrationTest` is `androidTest` and never run by CI.
- **Mechanism:** Hand-written migration SQL that drifts from the entity fails Room's validation at open → `IllegalStateException` → crash loop on upgrade (or, for `xtream_v2.db` today, F-20's wipe).
- **Fix:** Export schemas for `XtreamDatabase` and `SettingsDatabase`, commit them, and migrate tests to `MigrationTestHelper` + Robolectric so they run in the JVM unit-test gate. (`EpgIndexDatabase` is a rebuildable cache — destructive fallback is fine there.)

#### 🆕 F-34: `EpgSyncWorker` calls `setForeground()` outside its error handling [P2, PLAUSIBLE]
- **Where:** `core/network/.../xmltv/EpgSyncWorker.kt:57-58`.
- **Mechanism:** On Android 12+ a worker that starts while the app is in the background can get `ForegroundServiceStartNotAllowedException` from `setForeground`. It's outside the `try`, so every background run fails before syncing (Shield is Android 11 — unaffected; Chromecast with Google TV is 12/14 — likely affected). `pruneOrphanedCatalogData()` (`:83`) also runs outside the `try`.
- **Fix:** Reproduce on a Google TV emulator first. Then wrap `setForeground` (continue without foreground on failure; the inline FTS rule from memory still applies) and move the prune inside the `try`.

#### F-10: Jellyfin HTTP client never closed [P3, CONFIRMED — downgraded]
- **Where:** `jellyfin/JellyfinApiService.kt:56-73, 642-646`.
- **Correction to draft:** OkHttp dispatcher threads idle out after 60 s and pooled connections after 5 min; this is not an unbounded thread leak / `pthread_create` OOM. Jellyfin is out of scope per `20260922_codebase-stability-resilience-plan.md` scope note.
- **Fix (optional):** mirror `XtreamApiService.close()`; call it from `JellyfinMediaProvider.disconnect()`. Same for `TmdbApiService`.

#### 🆕 F-36 / F-37: DI and cast hygiene [P3]
- `ProviderRepository(...)` is constructed directly at 37 sites (e.g. `TvNavHost.kt:177, 670, 752`) despite AGENTS.md rule 4 — each instance builds its own `MasterKey`/encrypted-prefs cache. Route through `AppContainer.providerRepository` when touching those files; no sweep.
- `tv/.../ui/components/modifiers/FocusModifiers.kt` `tvFocusable*()`: the focus-event node is chained *after* `focusable()`, so it never receives focus events and never draws its ring. No screen uses it (found when Diagnostics tried to, 2026-10-01). Fix the order or delete the helpers.
- `MobileCategoryListScreen.kt:245, 274, 284` hard-cast `context as ComponentActivity`. Use `LocalActivity.current` / a `findActivity()` helper. Low risk today (always hosted in `MainActivity`).

---

## 5. Phased roadmap

Order: first the safety net that lets us see failures, then data loss and crash loops, then sync, then playback/lifecycle, then systemic hardening and test gates.

### Phase 0 — Safety net (small, lands first)
1. **F-30** crash/ANR capture + dev-mode Diagnostics screen.
2. **F-31** CI on push/PR: unit tests, ktlint, server tests.
3. **F-24** `AppScopes` helper; replace the 12 handler-less scopes. *(Fixes the crash half of F-21 by itself.)*

### Phase 1 — Data loss & crash loops (P0)
1. **F-20** stop the destructive fallback on `xtream_v2.db`; downgrade backup path. *(Schema doc updated in the same commit.)*
2. **F-21** recoverable `SyncAccountStore`.
3. **F-03** `awaitInstanceOrNull()` at the five Compose call sites; drop the redundant `setContentType` effect.
4. **F-17** mobile dock stops on toolbar Back / Search / EPG / dispose.
5. **F-01** clear recycling on error so the hard retry runs.

### Phase 2 — Sync integrity
1. **F-08** per-record guard in `SyncApplier`; no `!!`.
2. **F-09** persist `deferred` with `cursor` per page.
3. **F-23** wrap decode errors; generic failures set `lastError` and back off.
4. **F-22** per-record server rejection + client size check *(server and client in separate commits; server first, it's backward compatible)*.
5. **F-12** + **F-26** socket backoff and OkHttp `pingInterval`.
6. **F-07** AAD v2 with v1 fallback *(docs: `server/README.md`, live-sync plan)*.
7. **F-25** HLC future-clock guard.
8. **F-11** `deleteProvider(fromRemote = true)`.

### Phase 3 — Playback & lifecycle
1. **F-16** TV Live TV resume (split layout + player live-paused rule).
2. **F-18** TV provider switch pops to graph root.
3. **F-15** drain-then-close repositories; debug assertion on post-close writes.
4. **F-06** local future per `callbackFlow`.
5. **F-29** atomic final progress write.
6. **F-02** after HLS live reproduction only.
7. **F-19** mobile player Back closes panels.

### Phase 4 — Storage hardening
1. **F-33** export schemas; `MigrationTestHelper` in JVM tests.
2. **F-14** crash-safe profile deletion order + startup resume.
3. **F-13** favourite `StateFlow` snapshot; remove `runBlocking`.
4. **F-35** incremental vacuum with free-space guard.
5. **F-28** no plaintext fallback; "sign in again" state.

### Phase 5 — Systemic hygiene
1. **F-27** `suspendRunCatching` + convert the listed files + CI grep gate.
2. **F-32** align Compose BOM (stable) + emulator D-pad smoke pass; refresh AGENTS.md version table.
3. **F-34** after Google TV emulator reproduction.
4. **F-10**, **F-36**, **F-37** opportunistically when those files are touched.

### Phase 6 — Regression tests that lock it in (written alongside each phase, listed here as the gate)
- `StreamingPlaybackService` retry/recycle state machine behind a fake `Player` (F-01, F-02).
- `SyncEngine` pagination with an injected failing page (F-09); `SyncApplier` with an undecodable payload and a concurrently deleted provider (F-08).
- `SyncManager` socket backoff with a fake socket factory (F-12, F-26).
- `server` test: mixed valid/oversized batch (F-22); AAD v1/v2 interop in `SyncCryptoTest` (F-07).
- `XtreamDatabase` downgrade and every-version upgrade with data preserved (F-20, F-33).
- `MediaRepository.close()` drains a queued favourite write (F-15).

---

## 6. Dropped from the first draft (refuted)

| Draft item | Why dropped |
|---|---|
| VOD retry resumes at 0:00 (draft F-04) | ExoPlayer keeps the playback position on error (`stopInternal(resetPosition = false)`), so `currentPosition` in `handleStreamEndedOrError()` is the real position, not 0. |
| Transient audio-focus loss kills Live TV (draft F-05) | Transient loss doesn't change `playWhenReady` — ExoPlayer uses a playback *suppression* reason. Only permanent loss (another media app takes over) reaches the `stop()` branch, and stopping live in that case is the intended design (`StreamingPlaybackService.kt:445-466`). |
| `FocusRequester.requestFocus()` crashes on detached nodes (draft F-18) | Resolved `compose-ui` is 1.10.0, where an unattached requester logs a warning and returns `false` instead of throwing (verified in the 1.10/1.9 vs 1.8.3 bytecode). The real issue is the version skew — see F-32. |
| `LiveTvSplitLayout` "Compose lifecycle violation, 10 s freeze, crash" (draft F-16) | Conditional `viewModel()` after an early return is legal; the timeout is a `CancellationException` (no crash) and the effect suspends rather than blocking. The only crash path is `ServiceDestroyedException`, covered by F-03. |
| "Eliminate existing early returns in Compose layouts" (draft principle 2) | Style churn with no stability effect; tracked separately in `20260914_codebase-robustness-plan.md`. |
| Draft's `RemoteM3uMediaProvider` temp-file/connection leak (matrix only) | Code already uses connect/read timeouts, `disconnect()` in `finally`, and deletes the temp file on every failure path (`RemoteM3uMediaProvider.kt:85-145`). |

---

## 7. Out of scope

- R8/minify and baseline profiles — parked until public release.
- Replacing `EncryptedSharedPreferences` — deferred (`20260828_secret-store-migration-plan.md`); F-21/F-28 only harden its failure handling.
- Jellyfin/SMB/M3U-specific defects beyond F-10 — per the 2026-09-22 scope note.

---

## 8. Verification protocol

- **Every commit:** `./gradlew compileDebugKotlin testDebugUnitTest ktlintCheck`; `cd server && npm test` for server/sync changes.
- **Emulators first** via `scripts/deploy-*-emulator.sh` — after asking. Check `adb mdns services` and the device memory notes before asking for an IP.
- **Before any real device:** ask, then back up with `run-as tar`. Never `pm clear`, uninstall or run instrumented tests on hardware.
- **Per-phase acceptance:**
  - Phase 1: install an older build over a newer one on an emulator → history/favourites intact (or `.bak` present); corrupt `sync_account` prefs → app starts, sync shows "pair again".
  - Phase 2: two emulators linked to a local `workerd` server; inject an undecodable record and an oversized record → sync keeps flowing, status shows skipped count; kill the app mid-pagination → deferred records survive.
  - Phase 3: TV emulator — Home press and 35 s `adb shell input keyevent KEYCODE_SLEEP`/wake on the Live TV split → playback resumes live; provider switch then Back → no second Home.
  - Phase 0 check: force a crash in a debug build → appears in Diagnostics after restart.
