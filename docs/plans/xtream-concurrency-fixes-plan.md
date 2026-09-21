# Xtream Concurrency & Resource Fixes Plan

**Status:** Complete (2026-09-21) — both phases landed
**Date:** 2026-09-20
**Scope:** Xtream-only — `core/network/xtream/*`, `core/network/XtreamMediaProvider.kt`, `core/player/api/XtreamApiService.kt`, `core/ui/viewmodels/ProviderViewModel.kt`

This is a focused follow-up to the systemic audits already landed (`docs/plans/systemic-concurrency-memory-deep-dive-plan.md` and predecessors), which covered `core:player`, `core:network`, `core:ui`, `tv`, `mobile` broadly but did not scope `XtreamApiService`'s HTTP client lifecycle or `XtreamSyncWorker` specifically. All four findings below are new — verified against HEAD, not carried over from prior plans.

---

## Findings

### 1. `XtreamApiService` is never closed — leaks a full HttpClient + thread pool per use
**Severity:** P1 — High
**Location:** [`core/player/src/main/java/org/njarasoa/fijerena/core/player/api/XtreamApiService.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/src/main/java/org/njarasoa/fijerena/core/player/api/XtreamApiService.kt#L447-L449)

`XtreamApiService.close()` exists (calls `client.close()`) but has zero call sites anywhere in the repo. Every one of these constructs a fresh instance, uses it, and drops it:
- `XtreamSessionManager.login()` — line 39
- `XtreamSessionManager.restoreSession()` — line 75
- `XtreamSessionManager.updateProviderUrl()` — line 134
- `XtreamSessionManager.reinitialize()` — line 210
- `ProviderViewModel.testConnection()` — line 459 (fires on every "Test Connection" tap in the Add/Edit Provider screen)

Verified against `ktor-client-okhttp-jvm:3.4.0` sources (`OkHttpEngine.kt`): the engine's `init` block does
```kotlin
GlobalScope.launch(super.coroutineContext, start = CoroutineStart.ATOMIC) {
    try { requestsJob.job.join() }
    finally { clientCache.forEach { (_, client) -> client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() } }
}
```
That `GlobalScope` coroutine only completes when `close()` completes `requestsJob`. Since `close()` is never called, every abandoned `XtreamApiService` leaves behind a live `GlobalScope` coroutine plus its own dedicated `Dispatcher` (thread pool) and client cache, for the remainder of the process lifetime.

**Impact:** unbounded thread growth over a long-running session — every login attempt, every provider URL edit, every credentials test compounds it. On a TV device left running for days, this is slow but real GC/thread pressure, trending toward thread-starvation ANRs.

**Remediation:** hold one long-lived `XtreamApiService` per provider (or reuse the session's existing instance) instead of constructing throwaway ones for auth-only calls, and call `close()` on any instance that's genuinely being replaced. See Finding 2 first — closing naively as written today is unsafe.

---

### 2. The "shared OkHttpClient" doesn't actually share the thread pool
**Severity:** P2 — Medium (root cause amplifying Finding 1)
**Location:** [`core/player/src/main/java/org/njarasoa/fijerena/core/player/network/NetworkModule.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/src/main/java/org/njarasoa/fijerena/core/player/network/NetworkModule.kt#L30-L34) and [`XtreamApiService.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/player/src/main/java/org/njarasoa/fijerena/core/player/api/XtreamApiService.kt#L67-L74)

`NetworkModule.okHttpClient`'s own doc comment claims: *"Sharing this ensures that we reuse the same connection pool and thread pool (Dispatcher), which is critical for avoiding OutOfMemoryErrors."* This is false for any Ktor client built via `engine { preconfigured = NetworkModule.okHttpClient }` (which is exactly what `XtreamApiService` does).

Verified in `OkHttpEngine.createOkHttpClient()`:
```kotlin
val builder = (config.preconfigured ?: okHttpClientPrototype).newBuilder()
builder.dispatcher(Dispatcher())   // <-- always overrides the inherited dispatcher
```
Ktor unconditionally replaces the dispatcher with a brand new one. Only the `ConnectionPool` and other builder state (TLS config, interceptors, timeouts) are actually inherited via `newBuilder()`. So "reuse `NetworkModule.okHttpClient`" — the exact fix already prescribed for `TmdbApiService` in the still-open Phase 4 of `systemic-concurrency-memory-deep-dive-plan.md` — only prevents duplicate connection pools, not duplicate thread pools, for any Ktor-based service (Xtream, Jellyfin, and Tmdb once "fixed" the same way).

**Impact:** every `XtreamApiService` instance carries its own live thread pool no matter what, so Finding 1's leak is a full thread-pool leak per instance, not just a lightweight object leak.

**Remediation:** no action needed as a standalone item — informs how Finding 1 must be fixed (see Finding 3).

---

### 3. Landmine: naively closing `XtreamApiService` would evict the app-wide shared connection pool
**Severity:** P2 — Medium, latent
**Location:** same as Findings 1-2

Because the `ConnectionPool` genuinely *is* shared (not overridden by `createOkHttpClient`), calling `client.close()` on any `XtreamApiService` runs `client.connectionPool.evictAll()` against the same `ConnectionPool(5, 5, 5min)` instance that `NetworkModule.okHttpClient` shares with Jellyfin, TMDB, EPG downloads, and playback streaming clients. This is currently masked only because nobody calls `close()` (Finding 1). A naive fix that adds `service.close()` after each use would trade a thread leak for random cross-provider connection resets — an Xtream login test could sever an in-progress Jellyfin poster fetch or a live playback connection.

**Impact:** none yet (latent) — but blocks a careless fix of Finding 1.

**Remediation:** when fixing Finding 1, either (a) give `XtreamApiService` its own non-shared `Dispatcher`/`ConnectionPool` so `close()` is safe to call, or (b) stop constructing throwaway instances at all and reuse one long-lived instance per session/provider so `close()` is rarely needed. Do not add bare `.close()` calls without addressing this first.

---

### 4. `XtreamSyncWorker` runs a full catalog sync with no `setForeground()` — same Doze failure class as the already-fixed EPG worker
**Severity:** P1 — High
**Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/XtreamSyncWorker.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/XtreamSyncWorker.kt)

`doWork()` calls `ProviderSyncRunner.syncProvider()` → `mediaProvider.syncAll()` for every configured Xtream provider — a full network fetch and DB write of the entire live/VOD/series catalog (tens of thousands of rows for large providers). It's a plain `CoroutineWorker`: no wake lock, no `setForeground()`.

This is the exact failure class already found and fixed for `EpgSyncWorker` (see memory: Shield `mCharging=false` → enters Doze → background jobs get DNS blocked → fixed by adding a foreground service via `setForeground()`). `XtreamSyncWorker` runs on the same devices, on the same periodic cadence (every 4 hours per `ProviderSyncManager.REFRESH_INTERVAL_HOURS`), and never got the equivalent fix.

**Impact:** periodic Xtream catalog sync can silently fail or be killed mid-write on Shield/TV devices when idle and on battery/Doze, the same way EPG sync used to.

**Remediation:** apply the same pattern used in `EpgSyncWorker` — wrap the sync in `setForeground()` with a foreground service notification for the duration of `doWork()`.

---

## Roadmap

### Phase 1: `XtreamSyncWorker` foreground fix (P1) — standalone, no dependencies — Done
- Ported the `setForeground()` pattern from `EpgSyncWorker` into `XtreamSyncWorker.doWork()`: new `getForegroundInfo()` override, own notification channel (`xtream_sync`) and id, `setForeground()` called at the top of `doWork()`. New strings (`xtream_sync_notification_channel`/`_title`) added in `values`, `values-fr`, `values-mg`. No manifest changes needed — `FOREGROUND_SERVICE_DATA_SYNC` is already declared app-wide for `EpgSyncWorker`. Verified with `ktlintCheck` + `compileDebugKotlin` for `core:network`, `:tv`, `:mobile`.

### Phase 2: `XtreamApiService` lifecycle fix (P1, depends on resolving Finding 3 first) — Done
- Went with a combined fix rather than picking one option: gave `XtreamApiService` its own `ConnectionPool` (`engine { config { connectionPool(ConnectionPool(5, 5, 5min)) } }`, layered on top of `preconfigured` so DNS/timeout/redirect settings are still inherited from `NetworkModule.okHttpClient`) so `close()` is now safe from any call site — it can never evict the shared pool or affect Jellyfin/TMDB/EPG/playback traffic. Ktor's `OkHttpEngine` already gives every instance its own `Dispatcher` regardless (Finding 2), so this was the only piece actually needing isolation.
- `XtreamSessionManager` now routes every assignment to `apiService` through a new private `replaceApiService()` that closes the outgoing instance first — wired into `login()`, `restoreSession()`, `updateProviderUrl()`, `reinitialize()`, and `logout()` (previously just dropped the reference with no close).
- `ProviderViewModel.testConnection()`'s one-off Xtream probe now closes its `service` in a `finally` block.
- `NetworkModule`'s doc comment was left as-is — it's still accurate for direct `OkHttpClient.newBuilder()` consumers (the streaming clients it defines itself); the inaccuracy is specific to Ktor's `preconfigured` path, which is now called out in `XtreamApiService`'s own comment instead of rewriting a comment on a class this plan didn't otherwise touch.
- Verified: `ktlintCheck` + `compileDebugKotlin` for `core:player`, `core:network`, `core:ui`, `:tv`, `:mobile`, plus existing `*Xtream*` unit tests in `core:player` and `core:network` all green.

## Verification
- Manual: repeatedly hit "Test Connection" with bad Xtream credentials in Settings, watch thread count (`adb shell dumpsys | grep Thread` or Android Studio Profiler) — should not grow unbounded after the fix.
- Manual: run Xtream sync on Shield while idle/screen-off long enough to enter Doze, confirm sync completes and `providers.db` sync stats update instead of silently stalling.
- Confirm no cross-provider connection resets: play a live Xtream stream while a Jellyfin/TMDB fetch is in flight, then trigger a provider test-connection or reconnect — playback should not glitch.
