# Mobile Player UX Hardening: Buffering Feedback, Initial-Load Retry, Swipe-Zapping

## Context

This is the mobile-side counterpart to [`docs/tv-player-buffering-recovery-plan.md`](./tv-player-buffering-recovery-plan.md), which covered the same three themes — **buffering on spotty connections**, **recovery from stream stalls**, and **frequent channel-changing** — for the TV app. The backend engine (`core/player/`) and the channel-loading ViewModel (`core/ui/.../StreamLoaderViewModel.kt`) are shared between TV and mobile, so most of the TV plan's P0/P1 fixes (the `DefaultHttpDataSource`→`OkHttpDataSource` swap, the `StreamLoaderViewModel` job-cancellation fix, the auto-retry-on-reconnect addition) live in shared code and will benefit mobile automatically once landed — they are **not** repeated here, only cross-referenced where mobile needs its own verification pass.

What this deep-dive found instead is a distinct set of mobile-UI-specific issues — same themes, different root causes, because mobile's player screen (`MobilePlayerScreen.kt`) is a single file hosting both the loader and the playback UI (unlike TV's split between `TvPlayerScreen.kt` and `PlayerScreen.kt`), uses touch gestures instead of D-pad, and has its own independent buffering-spinner logic that never references the service's `isRecyclingFlow` at all. Every finding below was confirmed by direct reading of the current code, then pressure-tested by an independent Plan-mode review pass.

---

## Verified Findings

### A. The buffering spinner is permanently disabled after the first successful play
`MobilePlayerScreen.kt:139` declares `var hasStartedPlaying by remember { mutableStateOf(false) }`. A `LaunchedEffect(currentPs)` at lines 224-229 sets it to `true` the first time `PlaybackState.Playing` is observed — with the comment "Track when video first starts playing so we stop showing the center spinner" — and it is **never reset**. The spinner's render condition (lines 416-424) is `PlaybackState.Buffering -> { if (!hasStartedPlaying) { MitohanaLoading(...) } }`. Net effect: once a stream has played successfully even once, *every* subsequent `Buffering` state for the rest of that session — including a long autonomous-recovery cycle from the shared `StreamHealthMonitor`, which can silently run up to ~3.5 minutes before giving up — renders nothing. The user sees a frozen frame with zero indication anything is happening, for as long as the stall lasts.

This is a different root cause from TV's analogous bug (TV double-guards on a service `isRecyclingFlow` that outlives its own grace window; mobile has no reference to `isRecyclingFlow` at all — confirmed via grep, zero matches in this file) but the same user-visible symptom.

Mobile already has a *complementary*, non-overlapping mechanism worth preserving as-is: a `LaunchedEffect` (lines 158-186) polls `StreamingPlaybackService.exhaustionRebufferCount` every second and fires a generic Android `Toast` ("buffering excessive") if 3+ rebuffer-exhaustion events occur within a rolling 30-second window. This is retrospective and transient (a few seconds on screen), not a persistent in-place indicator synced to current state — it does not make this finding moot, but the fix below is designed to coexist with it rather than replace it.

### B. A single fast swipe can fire `nextChannel()`/`prevChannel()` multiple times before the gesture even ends
`MobilePlayerScreen.kt:329-367` implements vertical-swipe channel switching via `detectDragGestures`, accumulating drag distance into `verticalAccumulator` and firing `nextChannel()`/`prevChannel()` whenever the accumulator crosses 100px — then resetting the accumulator to `0f` and continuing to accumulate within the *same* gesture. `onDrag` fires on every pointer-move event (many times during one continuous fling), and nothing caps firing to once per gesture or imposes any time-based cooldown. A single deliberate fast swipe of 300-500px can cross the 100px threshold two or three times within milliseconds, firing the same shared-ViewModel channel-change path repeatedly before the user has even lifted their finger — a more aggressive trigger of `StreamLoaderViewModel`'s known race (already being fixed at the ViewModel layer per the TV plan's job-cancellation fix) than TV's D-pad auto-repeat, which is at least rate-limited by Android's OS-level key-repeat interval.

### C. The initial stream-load error screen has no retry option — confirmed shared with TV, not mobile-only
Both `MobilePlayerStates.kt`'s `ErrorScreen(message, onBack)` and TV's private `ErrorScreen(message, onBack)` in `TvPlayerScreen.kt` (lines 284+) take only a message and a Back callback — no retry. This is the screen shown when `StreamLoaderViewModel.StreamState.Error` occurs, i.e. when *resolving* a stream's playable URL fails (a provider-API call, before the player ever mounts) — distinct from each platform's *other* error screen (mobile's `ErrorOverlay`, TV's `ErrorContent`), which already has Retry but only covers failures from `PlaybackState.Error` (after the stream was already handed to ExoPlayer). Today, a single transient API hiccup while resolving a channel's URL leaves the user with no option but to back out of the player entirely and re-navigate from the channel list. `StreamLoaderViewModel` does not currently retain enough state to support a generic retry — `loadStream(item: MediaItem)` takes a transient parameter that's never stored on the class.

This was discovered during the mobile pass but is unambiguously shared code (confirmed identical usage on both platforms) — the TV plan doc has been given a short addendum pointing back here.

---

## P0 — Do First

### P0-M1. Add an `onRetry` path to both `ErrorScreen` composables
**Files:** `core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/StreamLoaderViewModel.kt`, `mobile/src/main/java/org/njarasoa/fijerena/feature/player/components/MobilePlayerStates.kt`, `mobile/src/main/java/org/njarasoa/fijerena/feature/player/MobilePlayerScreen.kt`, `tv/src/main/java/org/njarasoa/fijerena/feature/player/TvPlayerScreen.kt`

Why P0 despite touching the most files: this is the only finding here with a hard usability dead-end today (forced full exit-and-renavigate) rather than a degraded-but-recoverable experience, and the fix is small and additive.

Add `private var lastLoadRequest: MediaItem? = null` to `StreamLoaderViewModel`, set at the top of `loadStream(item)` before `_state.value = StreamState.Loading`. Add a public `fun retryLastLoad()` that re-invokes `loadStream(lastLoadRequest)` if set, or falls back to re-running `initializeAndLoad()` if the failure happened on the very first load (before any explicit `loadStream` call ever ran, so `lastLoadRequest` is still null). This one method+field covers both failure points. Add an `onRetry: () -> Unit` parameter to both `ErrorScreen` composables, wired at each call site to `loaderViewModel.retryLastLoad()`.

**Risk:** low — purely additive API surface (new optional callback param, new VM method); no change to existing success-path behavior.

**Verify (manual, on a phone — both platforms need this; TV verification per its own plan's device list, mobile on a mid-range and a high-end phone):** Force a transient `resolvePlayableStream()` failure (toggle airplane mode for ~2s right as you tap a channel) and confirm the Error screen now shows a Retry button that re-resolves and proceeds to playback without leaving the player. Confirm Retry works both for a failure on the very first stream entered (no prior `loadStream` call — exercises the `initializeAndLoad()` fallback) and for a failure on a mid-session channel change (exercises the `lastLoadRequest` path). Confirm `onBack` still works as before.

---

## P1 — Real, Isolated

### P1-M1. Time-bounded buffering spinner past first play (Finding A)
**File:** `mobile/src/main/java/org/njarasoa/fijerena/feature/player/MobilePlayerScreen.kt`

Add a file-scoped `private const val POST_FIRST_PLAY_BUFFERING_SPINNER_DELAY_MS = 3_000L` and a `var showRecoverySpinner by remember { mutableStateOf(false) }` alongside the existing `hasStartedPlaying`. Add a `LaunchedEffect(currentPs)`: while `currentPs is PlaybackState.Buffering`, `delay(POST_FIRST_PLAY_BUFFERING_SPINNER_DELAY_MS)` then set `showRecoverySpinner = true`; on any other state, reset it to `false` immediately. Change the spinner's render condition from `if (!hasStartedPlaying)` to `if (!hasStartedPlaying || showRecoverySpinner)`. This is a pure addition — `hasStartedPlaying` and its existing effect are untouched, so the "never flash a spinner before first play" guarantee is preserved exactly as today.

Confirmed safe to key the effect on `currentPs` directly: `PlaybackState.Buffering` is a `data object` (singleton, in `core/player/.../model/PlaybackState.kt`), so repeated `Buffering` emissions during one continuous stall are the same value to Compose's `LaunchedEffect` — the delay runs once per continuous buffering span, not restarted on every emission within it.

3 seconds was chosen deliberately distinct from TV's 7-second `SEAMLESS_RECYCLE_GRACE_MS`: TV's number calibrates a *backend* question (how long can a silent service-level recycle hide a state transition before it's suspicious); mobile's is a pure *presentational* question (how long can the UI look frozen before that's worse than a spinner flash) with no backend coupling to honor, so it's free to be shorter while still comfortably exceeding ordinary sub-second ABR/rebuffer blips.

**Risk:** low — purely additive UI-state logic; doesn't touch the ViewModel or the underlying `PlaybackState` stream.

**Verify (manual, phone, mid-range + high-end):** Confirm first-play behavior is unchanged (no spinner before the first frame ever renders). Force a brief sub-3s rebuffer post-first-play (minor Wi-Fi blip) and confirm no spinner flashes — this is the regression check on the case the original latch protected. Force a stall exceeding 3s post-first-play (airplane-mode toggle for 10-15s, or degrade Wi-Fi enough to trigger the shared `StreamHealthMonitor` recovery path) and confirm the spinner now appears and persists through the stall, disappearing once `Playing` resumes — including through a full fast-tier-then-degraded-tier recovery cycle (up to ~3.5 minutes), not just the first few seconds. Confirm it doesn't visually collide with the existing 30s-rolling-window "buffering excessive" toast when both are triggered together.

### P1-M2. Cap channel-switch swipe to one fire per gesture (Finding B)
**File:** `mobile/src/main/java/org/njarasoa/fijerena/feature/player/MobilePlayerScreen.kt` (lines 329-367)

Add a local `var hasFiredThisGesture = false` inside the `pointerInput` block, alongside the existing accumulators. Change the vertical-fire condition to `if (!hasFiredThisGesture && abs(verticalAccumulator) > 100f)`, setting `hasFiredThisGesture = true` right after the `nextChannel()`/`prevChannel()` call. Add `onDragEnd = { hasFiredThisGesture = false }` and `onDragCancel = { hasFiredThisGesture = false }` to the `detectDragGestures(...)` call (currently only `onDragStart`/`onDrag` are wired — both extra callbacks default to no-ops today, so this is additive). Leave the horizontal overlay-toggle logic untouched; it doesn't call into the shared ViewModel and isn't part of this finding.

**Design choice, made deliberately:** cap to exactly one channel-change per discrete gesture, full stop — not a cooldown that still permits multi-step zapping on a long swipe. There's no existing precedent anywhere in this screen (or in AGENTS.md's documented mobile gesture model) for "swipe further to skip further" — the horizontal overlay-toggle right next to this code is already a single discrete action per swipe, and TV's analogous control (D-pad hold, fixed by the TV plan's P1-1 debounce) is being coalesced down to one change per input burst, not enhanced to skip multiple channels. A cooldown that still allows 2-3 fires per long swipe would just be a rate-limited version of the same race rather than a structurally different fix, and would still burn redundant `resolvePlayableStream()` network calls for a single intended channel-change even after those calls are made cancellable.

**Risk:** low — additive guard plus two new lambda bodies for previously-default-no-op callbacks; doesn't touch the distance-threshold math or `StreamLoaderViewModel`.

**Verify (manual, on more than one phone — touch-sampling rate varies by hardware and affects how many `onDrag` events a fast flick generates):** Perform one deliberate fast/long swipe (300-500px in a single continuous motion) and confirm exactly one channel change occurs — check Logcat for the call count, not just the visually-resolved channel, since a fast double-fire can coincidentally land on the right-looking result. Perform a series of separate short swipes (lift-and-reswipe) and confirm each fires its own change — the cap must be scoped to one gesture, not suppress legitimate subsequent ones. Confirm single short swipes feel exactly as responsive as before (the cap is a pure event-count guard, no added delay, but verify empirically). **Also use this pass to re-confirm the TV plan's P0-2 (`StreamLoaderViewModel` job-cancellation fix) specifically via touch gestures**: even with the one-fire cap, separate rapid swipes in quick succession (not one continuous gesture) can still fire multiple `loadStream()` calls close together, so confirm the final swiped-to channel wins here too, not just under the D-pad-mash test already specified in the TV plan.

---

## Sequencing Notes

- P0-M1 is independent of P1-M1/P1-M2 and touches `StreamLoaderViewModel.kt` (shared) plus *both* platforms' Error screens — it's the one item here with a required TV-side code change and TV-side verification, even though it surfaced during the mobile pass.
- P1-M1 and P1-M2 both touch only `MobilePlayerScreen.kt`, in disjoint line ranges, and are otherwise independent (render-gate timing vs. gesture-firing count) — land in either order, but do a full manual smoke pass between them since they're in the same file.
- Once P0-M1 lands, add a short note to `docs/tv-player-buffering-recovery-plan.md`'s findings list pointing back here, since that doc predates this discovery.
- No mobile-specific P2 items — the deferred/pre-scoped items in the TV plan's P2 section (session-level buffer escalation, the dead `hasReadTimeout` signal, cellular-specific tuning) live entirely in the shared `core/player` engine and already cover mobile once addressed there.

## Verification Summary

As with the TV plan, these are runtime/UX behaviors that need real-hardware validation, not just unit tests — per AGENTS.md, use a mid-range and a high-end phone (touch sampling rate and gesture feel vary across devices in ways that matter for P1-M2 specifically). Run `./gradlew ktlintCheck` and `./gradlew lintDebug` after each item, and a full `./gradlew :mobile:installDebug` (plus `:tv:installDebug` for P0-M1) manual pass before moving to the next.
