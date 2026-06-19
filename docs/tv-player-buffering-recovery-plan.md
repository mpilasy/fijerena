# TV Player UX Hardening: Spotty-Connection Buffering, Crash/Stall Recovery, Channel-Zapping

## Context

The TV live-TV playback stack just went through a concentrated string of fixes (`488b2e3` through `0635c05`) that introduced `StreamHealthMonitor` (autonomous recovery), seamless silent-recycle, a tiered fast/degraded recovery budget, and finally made `AdaptiveLoadControl`'s network-aware buffer swapping actually work (it had been a no-op stub). That work closed the big, structural gaps. This deep-dive went looking for what's left — specifically for the three pain points named: **buffering on spotty connections**, **recovery from stream stalls/crashes**, and **frequent channel-changing**.

What it found is not "the system is broken" — it's a handful of concrete seams where the last round of fixes didn't quite connect end-to-end, plus two pre-existing gaps (HTTP stack mismatch, no job-cancellation on channel change) that predate that work entirely. Every finding below was confirmed by reading the actual code (not inferred), and a second independent pass (a Plan-mode review agent) pressure-tested all five original proposals against the codebase and validated them, while surfacing one additional finding (P0-3) that turned out to be more impactful than originally scoped.

The goal of this plan is targeted, minimal-diff fixes that build on the existing patterns (`AdaptiveLoadControl`'s volatile-delegate swap, `StreamHealthMonitor`'s tiered-counter pattern, `NetworkMonitor`'s `StateFlow` collection) rather than new abstractions — consistent with AGENTS.md's anti-speculation guidance.

> **Addendum (found during the mobile-side pass, see [`docs/mobile-player-buffering-recovery-plan.md`](./mobile-player-buffering-recovery-plan.md)):** `StreamLoaderViewModel.StreamState.Error` — the screen shown when resolving a stream's playable URL fails, rendered by this plan's `TvPlayerScreen.kt::ErrorScreen` — has no retry option on either platform, only Back. This is shared code, not mobile-specific; the fix (P0-M1 in the mobile plan) adds an `onRetry` callback to both platforms' `ErrorScreen` composables and a small `lastLoadRequest`/`retryLastLoad()` addition to `StreamLoaderViewModel`. Tracked there, not duplicated here.

---

## Verified Findings

1. **HTTP stack mismatch.** `StreamingMediaSourceFactory.kt` streams every piece of content (Live, VOD, all providers — confirmed the only `createMediaSource` call site in the app) through `DefaultHttpDataSource` (`java.net.HttpURLConnection`-based). Meanwhile `NetworkModule.kt` maintains a separate `okhttp3.OkHttpClient` with a custom `AndroidAwareDns` (retries DNS resolution 3x with backoff) — used for other API calls, never for streaming. `StreamingPlaybackService`'s silent-recycle path calls `NetworkModule.evictConnectionPool()` believing it forces fresh sockets to "bypass ISP/CDN shaping" — but since streaming never used that pool, **the call is a no-op for its stated purpose.** `media3-datasource-okhttp` is already a declared dependency (pulled into `core:player` via the `media` bundle) but `OkHttpDataSource` is never referenced anywhere in the codebase — dead weight sitting unused.

2. **Buffering spinner is suppressed for the entire recovery window, contradicting the backend's own stated intent.** `StreamingPlaybackService.kt` has an explicit comment that the 7s `SEAMLESS_RECYCLE_GRACE_MS` window exists so that, past it, "the real state (almost always Buffering) through so a slow recovery shows a spinner instead of leaving the screen black." But `PlayerScreen.kt`'s render gate is `if (!isRecycling && !isActuallyMoving) BufferingContent()` — and `isRecycling` stays `true` for the *entire* recovery (fast tier + degraded tier, up to ~3.5 minutes), only flipping to `false` when `Playing` arrives. So even after the service starts forwarding real `Buffering` states past the grace window specifically to enable a spinner, the UI's own redundant guard blocks it. Net effect: on a degraded connection, the user can stare at a frozen frame for minutes with zero feedback. Confirmed no race/threading issue causes this — it's a straightforward logic gap, most likely because the `isRecycling` UI guard (`9e18864`) predates the 7s-grace-window commit (`d6e7358`) and was never reconciled with it.

3. **Channel changes today fully unmount the player and black out to a spinner — for every change, not just races.** `TvPlayerScreen.kt`'s top-level `when (streamState)` renders `LoadingScreen()` (a full-screen spinner, replacing `PlayerScreen` entirely) for *every* `StreamLoaderViewModel.StreamState.Loading` emission — and `loadStream()` sets state to `Loading` synchronously before doing any work. This means a single channel change blacks out the live video and shows a spinner until the new stream resolves, today, regardless of network speed. Compounding this: `loadStream()` (`StreamLoaderViewModel.kt`) launches every call via `viewModelScope.launch(Dispatchers.IO)` with **no `Job` tracking** — concurrent calls race to write `_state.value`, with no guarantee the last-requested channel wins. `PlayerKeyHandler.kt`'s D-pad Up/Down handler fires `onNextChannel()`/`onPreviousChannel()` on every `KeyDown` including Android's auto-repeat ticks while the key is held, with no `repeatCount` gating (unlike the VOD scrub-cursor logic a few lines below, which does check `repeatCount`).

4. **No auto-retry once autonomous recovery gives up.** After `StreamHealthMonitor.onRecoveryExhausted` fires, the service calls `stop()` and emits `PlaybackState.Error`; the user must press the on-screen Retry button manually. There's no listener watching for connectivity to return and resuming automatically. The plumbing (`NetworkMonitor.networkType: StateFlow<NetworkType>`) is already collected elsewhere in the same service, so this is a same-pattern addition, not new infrastructure.

5. **No escalation of buffer conservatism on repeated failure** — every recycle attempt rebuilds the identical buffer profile that just failed. Investigated as a possible fix, but `cellularLiveMultiplier`/`cellularVodMultiplier` are read once at player-init and passed as `val`s into `AdaptiveLoadControl` — they're not part of the existing hot-swap mechanism (`updateForNetwork`/`updateContentType`). Making this work needs a new swap method across three files, for a benefit with no field evidence it's needed (the fast→degraded tiering already buys recovery time without changing buffer math). **Deferred to P2**, pre-scoped below.

Two additional things surfaced during review, also pre-scoped for later rather than bundled in:
- `StreamHealthMonitor.updateMetrics(hasReadTimeout=...)` is **always called with `false`** from both of its call sites — the "immediate recycle on read timeout" fast path is currently dead code. No live signal feeds it.
- `media3-datasource-cronet` is also in the version catalog but unused anywhere — not a live alternative to the OkHttp swap below, just an unused catalog entry worth knowing about so it isn't mistaken for a half-finished migration.
- Repo-wide grep confirmed no ICY-metadata handling exists anywhere in the codebase, so the HTTP datasource swap (item 1) has no ICY-related edge case to worry about.

---

## P0 — Do First (independent, low-risk, land 1/2/3 before 4)

### P0-1. Remove the redundant `isRecycling` spinner guard
**File:** `tv/src/main/java/org/njarasoa/fijerena/ui/player/PlayerScreen.kt` (~line 199)

Change:
```kotlin
PlaybackState.Buffering -> {
    if (!isRecycling && !isActuallyMoving) {   // before
        BufferingContent()
    }
}
```
to:
```kotlin
PlaybackState.Buffering -> {
    if (!isActuallyMoving) {                   // after
        BufferingContent()
    }
}
```
Leave the `LaunchedEffect(currentPs, isRecycling)` failsafe poll above it (lines 179-194) untouched — it's correctly scoped already, since during a *true* sub-7s seamless recycle there's nothing to show regardless (state is suppressed server-side in `StreamingPlaybackService`).

**Risk:** very low — removes a redundant client-side guard; the server already owns the real suppression window.

**Verify:** Force a connection degraded enough that recycle exceeds 7s (throttle Wi-Fi via router QoS, or toggle airplane mode briefly mid-stream) and confirm a spinner now appears instead of a frozen frame. Then confirm normal sub-7s recycles on a healthy connection remain fully seamless (no spinner flash) — this is a regression check on the common case, not just the bug case.

### P0-2. Cancel the in-flight channel-load job before starting a new one
**File:** `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/StreamLoaderViewModel.kt`

Add a tracked `private var loadJob: Job? = null`; in `loadStream(item)`, call `loadJob?.cancel()` before assigning `loadJob = viewModelScope.launch(Dispatchers.IO) { ... }`. Kotlin's cooperative cancellation means a cancelled job simply won't resume past its next suspension point (the `resolvePlayableStream` network call), so no `_state.value` write happens from a stale request — no extra guard logic needed beyond tracking the `Job`.

**Risk:** low.

**Verify:** Mash channel-up 10+ times rapidly on real hardware (Shield/Bravia); confirm the channel that ends up on screen is the last one actually pressed, not whichever network call happened to finish first. Check Logcat for any unexpected behavior from cancelled-but-still-running coroutines (there shouldn't be any).

### P0-3. Keep `PlayerScreen` mounted during channel-change `Loading`, not just on first entry
**File:** `tv/src/main/java/org/njarasoa/fijerena/feature/player/TvPlayerScreen.kt`

Currently the top-level `when (streamState)` swaps to a full-screen `LoadingScreen()` (unmounting `PlayerScreen`, including the live `PlayerView`) on *every* `Loading` emission, including ordinary channel changes. Change this so `LoadingScreen()` only renders on true first entry (no prior `Success` yet); on subsequent channel changes, keep rendering `PlayerScreen` against the last-known `Success` state while the new stream resolves, letting the now-fixed (P0-1) `Buffering` spinner inside `PlayerScreen` communicate progress instead of a full takeover.

This is the one item in P0 with real design ambiguity, flagged explicitly by the review pass: `TvPlayerScreen.kt`'s `currentStreamId` (derived from `(streamState as? Success)?.streamId`) drives a `LaunchedEffect(currentStreamId)` that calls `playbackViewModel.playStream(...)`. Under the current code, that key goes to `null` during the `Loading` window — previously masked by the full-screen takeover, but once `PlayerScreen` stays mounted through `Loading`, this becomes observable. Trace this interaction carefully before implementing; it likely means holding onto the *previous* successful `streamId` as the displayed/effect key rather than letting it null out, while still branching the inner loading/success rendering on the real `streamState`.

**Risk:** medium — the only P0 item touching control flow, not just a render condition. Sequence it after P0-1/P0-2/P0-4 if it needs a second design pass; it doesn't block the others.

**Verify:** Single-tap channel change — confirm the previous channel's video keeps showing (with a buffering spinner overlay, not a screen takeover) until the new channel is ready, then cuts over. Confirm this applies both to D-pad zapping and to selecting from the category/last-watched overlays. Confirm the `Error` path still fully takes over the screen (only `Loading` should fall back to previous content, not `Error`).

### P0-4. Swap streaming HTTP layer from `DefaultHttpDataSource` to `OkHttpDataSource`
**Files:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/network/NetworkModule.kt`, `core/player/src/main/java/org/njarasoa/fijerena/core/player/source/StreamingMediaSourceFactory.kt`

In `NetworkModule.kt`, add a function that derives a per-network-type streaming client from the shared `okHttpClient` via `.newBuilder()` (cheap shallow copy — shares the connection pool, dispatcher, and `AndroidAwareDns`), explicitly setting:
- `.retryOnConnectionFailure(false)` — `AdaptiveLoadErrorPolicy` already owns the full retry/backoff decision tree (`getRetryDelayMsFor`, returns `C.TIME_UNSET` to stop); letting OkHttp silently retry underneath it would corrupt Media3's `errorCount` bookkeeping and drift the backoff timing away from the `NetworkBufferProfile` constants.
- `.connectTimeout(...)` / `.readTimeout(...)` matching the existing `NetworkBufferProfile.WIFI_CONNECT_TIMEOUT_MS` / `CELLULAR_CONNECT_TIMEOUT_MS` (etc.) values currently applied to `DefaultHttpDataSource.Factory` — OkHttp timeouts are client-level, not per-call, confirmed via the actual `OkHttpDataSource.Factory` source (media3 1.7.1): constructor takes a `Call.Factory`, plus `setDefaultRequestProperties`/`setUserAgent`/`setCacheControl`/`setContentTypePredicate`/`setTransferListener` — no per-call timeout setters exist.

In `StreamingMediaSourceFactory.kt`, replace the `DefaultHttpDataSource.Factory()` construction with `OkHttpDataSource.Factory(callFactory)`, passing the derived per-network-type client, and carry over the existing header/user-agent configuration via the equivalent setters confirmed above.

This makes `evictConnectionPool()` finally do something real for its stated purpose, and gives every video/audio stream the same DNS-retry resilience already built for the rest of the app's networking.

**Risk:** highest in this plan — it's the data path for every stream, every provider (Xtream, Jellyfin, SMB, local, remote M3U all funnel through this one factory; confirmed it's HTTP-only today with no scheme branching, so the swap is uniform and isolated, no edge-case scheme risk). Two specific things to verify, not just assume:
- **HTTP status code parsing must still work.** `StreamingPlaybackService.parsePlaybackError()` regex-matches `"Response code: (\d{3})"` out of `DefaultHttpDataSource`'s exception cause chain to produce the user-facing 401/403/404/458/5xx messages. `OkHttpDataSource` throws a different exception type — confirm its message still contains a parseable status code, or these specific error messages will silently degrade to a generic fallback.
- **Cross-protocol redirects.** `DefaultHttpDataSource.Factory.setAllowCrossProtocolRedirects(true)` is currently set explicitly; confirm `OkHttpClient.followRedirects(true)`/`followSslRedirects(true)` (already set in `NetworkModule.kt`) covers the same HTTP↔HTTPS redirect cases some IPTV CDNs rely on.

**Verify:** Play one stream from each provider type (Xtream live, Xtream VOD, Jellyfin VOD, SMB, local, remote M3U) on both Wi-Fi and cellular, confirm normal playback. Force a 403/404 (expired credential or bad URL) and confirm the error banner still shows the specific HTTP-code message, not the generic fallback. Force a mid-stream drop (10s airplane-mode toggle) and confirm both the hard-retry and recycle recovery paths still work over the new stack.

---

## P1 — Real, Lower Urgency, Isolated

### P1-1. Debounce held/rapid D-pad channel-up/down
**Files:** `tv/src/main/java/org/njarasoa/fijerena/ui/player/PlayerKeyHandler.kt` / `PlayerScreenState.kt`

Android's D-pad auto-repeat means holding Up/Down fires a `KeyDown` on every repeat tick, each one currently triggering an immediate `onNextChannel()`/`onPreviousChannel()` call. Add a short debounce (recommend **300ms** quiet period — round, defensible, and short enough that a single deliberate tap-and-release never sees a second `KeyDown` before key-up, so single presses are unaffected; only multi-press bursts or held repeats get coalesced into one call). Keep this purely as a call-debounce (delay invoking `nextChannel()`/`prevChannel()` until input quiets down) rather than building a running channel-number preview — a precise live-updating preview would need exposing internal ViewModel list/index state to the UI layer, which is more abstraction than this warrants per AGENTS.md's minimal-scope guidance.

**Risk:** low-medium. Interacts with P0-3 — once that lands, the combined feel should be: press repeatedly → 300ms after release → one `loadStream()` call → video keeps showing the old channel with a spinner until the new one resolves. Verify the combination, not just each fix alone.

**Verify:** Single short press changes channel with no perceptible lag (check on both the Shield's own remote and a Bluetooth TV remote — repeat timing varies by device). Holding the button for 2+ seconds results in exactly one `loadStream()` call (check Logcat), landing on the channel N presses away from the start. Rapid distinct taps (not held) coalesce the same way.

### P1-2. Auto-retry once when connectivity returns after giving up
**File:** `core/player/src/main/java/org/njarasoa/fijerena/core/player/service/StreamingPlaybackService.kt`

In `observeNetworkChanges()`, track the previous `NetworkMonitor.networkType` emission alongside the existing collector. On a transition from `UNKNOWN` to a real type (not just "is currently a real type" — that would also fire for fatal HTTP errors like 404, where the network was never actually down) — and only while `_currentMetadata.value.isLive && _playbackState.value is PlaybackState.Error` **at the moment the transition is observed** (not just at arm-time, since the user may have already pressed Back) — call the same retry path the manual Retry button uses. Guard with a one-shot `autoRetryAttempted` flag, reset inside `playStream()` alongside the other per-stream counter resets (`liveRetryCount = 0`, etc.) so it's consistent with the existing reset pattern.

**Risk:** medium — the live state-check at fire-time (not arm-time) is the one correctness detail that matters; `serviceScope` isn't cancelled by `stop()`, so a stale collector emission must be a no-op rather than relying on cancellation to suppress it.

**Verify:** Drive a live channel into `Error` (e.g. exhaust recovery via airplane mode), then restore connectivity — confirm playback resumes automatically, exactly once, with no manual Retry press. Then repeat the failure a second time within the same session and confirm it does *not* auto-retry again without an intervening successful play. Separately: enter `Error`, press Back to exit, then restore connectivity — confirm no auto-retry fires after the user has already left.

---

## P2 — Deferred, Pre-Scoped (not built now, recorded so it isn't re-designed cold later)

- **Session-level buffer escalation on repeated failure** (finding 5): add `AdaptiveLoadControl.updateMultiplier(scaledMultiplier: Float)` mirroring the existing `updateForNetwork`/`updateContentType` swap pattern; call it from `StreamingPlaybackService` once `StreamHealthMonitor`'s attempt counter crosses into degraded tier, resetting on `notifyStablePlayback()`. Deferred — no field evidence the current fast/degraded tiering under-recovers, and it needs a small new accessor on `StreamHealthMonitor` to expose attempt count.
- **Wire a real `hasReadTimeout` signal into `StreamHealthMonitor.updateMetrics`**: currently always `false` from both call sites, so the monitor's "immediate recycle on read timeout" path is dead code. Would need new error-classification plumbing from `AdaptiveLoadErrorPolicy`/`PlaybackException.errorCode` through to the health monitor — more than a one-file change, not a reported pain point today.
- **Cellular-specific faster degradation detection / jittered degraded-tier cadence**: considered, no observed evidence it's needed, would risk false-positive recycles on bursty-but-fine cellular connections. Recorded as considered-and-declined rather than silently dropped.

---

## Verification Summary

All of these are player/runtime-behavior changes that can't be meaningfully verified by unit tests alone — per AGENTS.md, validate on real hardware (Shield for general behavior + HEVC/AV1, Bravia for TV-specific remote/overscan quirks, Chromecast with Google TV as a third data point). After each P0/P1 item, also run `./gradlew ktlintCheck` and `./gradlew lintDebug`, and do a full `./gradlew :tv:installDebug` + manual smoke pass before moving to the next item, since several of these changes touch the same files in sequence (`StreamingPlaybackService.kt` is touched by P0-1's verification context and P1-2's implementation; `PlayerScreen.kt`/`TvPlayerScreen.kt` are touched by P0-1 and P0-3 together).
