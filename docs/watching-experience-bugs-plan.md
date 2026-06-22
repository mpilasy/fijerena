# Watching-Experience Bug Sweep

## Context

A prior session in this same conversation fixed three real concurrency/silent-failure bugs in `core/player` (the `AdaptiveLoadControl` delegate-swap race, its wrong-thread replay, and the `instanceReady`/`instance` early-publish race in `StreamingPlaybackService`) — all variations of the same root cause: something gets published/marked-ready before it's actually fully set up, and the consumer fails silently instead of logging. This doc is a broader sweep of the rest of the app for bugs in that same spirit — anything that degrades the actual experience of watching content, not just code-quality nits.

Approach: three Explore agents mapped the EPG subsystem, the content-browsing/favorites/history subsystem, and the mobile player's gesture/rotation code (areas with the least existing context from the earlier session). Each lead was then personally verified by reading the actual code and tracing the exact failure mechanism — several agent-reported leads turned out to be non-issues on closer inspection and are listed below so they aren't re-investigated later.

---

## Verified Bugs

### 1. Live TV's "Now Playing" / "Up Next" info can be stale for up to 12 hours after an EPG sync

**Root cause, traced precisely:**
- `StreamLoaderViewModel.loadStreamInternal()` (`core/ui/.../viewmodels/StreamLoaderViewModel.kt:186`) calls `repo.getEpgBulkForItems(listOf(currentItem))` to populate `currentEpgProgram`/`nextEpgProgram` — the data shown in the player's live-TV info bar.
- That delegates to `MediaRepository.getEpgBulkForItems()` (`core/network/.../MediaRepository.kt:241-256`), which tries `xmltvEpgService.getEpgForChannels(items)` first.
- `XmltvEpgService.getEpgForChannels()` (`core/network/.../xmltv/XmltvEpgService.kt:133-207`) checks an in-memory/SharedPreferences cache via `getCachedEpg()` (lines 270-290) before ever touching the SQLite EPG index. That cache is considered valid as long as: (a) the wall-clock TTL (`PARSED_CACHE_TTL_MS = 12h`, line 25) hasn't elapsed, and (b) every requested channel ID is already a key in the cached map (line 145, `allPresent`).
- Nothing in this check considers whether the underlying SQLite index (`epg_index.db`) has been updated more recently than the cached snapshot. `EpgSyncWorker`/`EpgIndexer` write fresh programme data straight into SQLite on every background sync, but `clearXmltvCache()` (the only thing that nulls this cache) is never called from the sync path — only from explicit user actions elsewhere (e.g. switching providers).
- Net effect: once a channel's EPG has been fetched once, the player can keep showing that exact snapshot — wrong current-program title, wrong end time, wrong "up next" — for up to 12 hours after a sync corrects or updates it, with no way for the user to tell.

**Fix:** have `EpgSyncWorker`/`EpgFileManager.processAllSources()` call `XmltvEpgService.clearCache()` (already exists, `XmltvEpgService.kt:264-268`) after a successful ingest, so a completed sync invalidates the stale snapshot instead of leaving it live for up to 12h. Minimal, uses an existing method — no new invalidation mechanism needed.

**Verify:** force an EPG sync (`EpgSyncDebugReceiver`, per existing reference memory on EPG debug testing) while a live channel with stale cached EPG is playing; confirm the player's now/next info updates promptly after the sync completes instead of holding the old snapshot.

---

### 2. Mobile swipe gesture for channel-switch/category-panel can flicker or drop input mid-swipe

**Root cause, traced precisely** (`mobile/.../feature/player/MobilePlayerScreen.kt:351-396`):
- The drag-gesture `Modifier.pointerInput(state.categoryStreams, showCategoryOverlay, showLastWatchedOverlay, showStats) { detectDragGestures(...) }` is keyed on `showCategoryOverlay`/`showLastWatchedOverlay` — but the gesture handler **mutates those exact same two booleans from inside its own `onDrag` callback** (lines 384-390) while the user's finger is still down.
- The moment the horizontal-swipe branch flips `showCategoryOverlay` or `showLastWatchedOverlay`, Compose recomposes with a new `pointerInput` key, which tears down and restarts the `detectDragGestures` coroutine — *during* the same physical touch gesture. Local state (`verticalAccumulator`, `horizontalAccumulator`, `hasFiredThisGesture`) is lost, and Compose's pointer input system does not guarantee the new coroutine instance keeps receiving the remainder of an in-flight gesture, so the rest of that swipe can simply go unrecognized.
- Compounding this: the vertical channel-switch branch has a per-gesture single-fire guard (`hasFiredThisGesture`, reset only on `onDragStart`/`onDragEnd`/`onDragCancel`), but the **horizontal panel-toggle branch has no equivalent guard** — it resets its own accumulator to 0 every time it fires (line 392) and re-evaluates on the next 80px of movement within the *same* continuous drag. A single longer or less precise swipe can cross the threshold twice (e.g. overshoot-and-correct), toggling an overlay open then immediately closed within what the user experiences as one gesture.
- Net effect: swiping to switch channels or open the category/last-watched panel can feel unreliable — sometimes the panel flickers open-then-shut, sometimes the rest of a swipe is silently ignored right when the panel opens.

**Fix:** Two independent, minimal changes:
1. Don't key `pointerInput` on state the gesture handler itself writes. Drop `showCategoryOverlay`/`showLastWatchedOverlay` from the key list (keep `state.categoryStreams`/`showStats`, which are externally driven) and read the current overlay-open state via a `rememberUpdatedState`-wrapped value inside the callback instead, so toggling them doesn't restart the gesture detector mid-drag.
2. Give the horizontal branch the same single-fire-per-gesture protection the vertical branch already has (reuse `hasFiredThisGesture`, or add a second flag), so one continuous swipe can only open or close a panel once.

**Verify:** on a mobile device/emulator, do a single continuous swipe right that's noticeably longer than the 80px threshold (don't lift between); confirm the category panel opens once and stays open, and that a subsequent independent swipe (lift, then swipe again) still works to close it. Repeat for vertical channel-switch with a long swipe to confirm only one channel change fires and the rest of the gesture isn't dropped.

---

### 3. Live TV never recovers after the device spends hours in standby — black screen, OSD updates but video doesn't, "stats for nerds" all N/A

**Root cause, traced precisely:**
- `StreamingPlaybackService`'s companion object (`core/player/.../service/StreamingPlaybackService.kt:1081-1093`) pairs a correctly-updated `@Volatile var instance` with a single, never-reset `CompletableDeferred<StreamingPlaybackService>` called `instanceReady`. `onCreate()` does `instance = this` (a plain reassignment, fine on every call) then `instanceReady.complete(this)` (line 196) — but `CompletableDeferred.complete()` silently no-ops once already completed, it does not update the held value.
- `onDestroy()` (line 745) sets `instance = null` but never touched `instanceReady`.
- Android stops an idle, non-foreground, unbound-but-started service (exactly what `StreamingPlaybackService` is once playback auto-stops ~30s after the app loses focus, via `PlaybackViewModel.onFocusLost`) well before the hosting process itself dies — this is normal background-service-limit/standby-bucket behavior, and is far more likely after hours of device standby. When that happens, the *next* `onCreate()` (triggered by `PlaybackViewModel.startService()` on app resume) silently fails to re-arm `instanceReady`, so `awaitInstance()` permanently hands out the original, fully torn-down instance instead of the new live one.
- `PlaybackViewModel.playStream()` (`core/player/.../viewmodel/PlaybackViewModel.kt:131`) and `observeServiceState()` (line 86) both depend on `awaitInstance()`. The resulting `service.playStream(...)` call hits `mediaSession?.player as? ExoPlayer ?: return` at `StreamingPlaybackService.kt:383` — a silent no-op, no exception, no log.
- This explains the full symptom set: the OSD/channel banner updates because `PlaybackViewModel.playStream()` sets `_currentMetadata.value = metadata` synchronously (line 126) *before* the broken async call to the dead service; the live, correct service instance (reachable via `getInstance()`, used by `StatsOverlay`) never receives a `MediaSource`, so it sits in `STATE_IDLE` and every stream-derived stat stays at its unpopulated "N/A" default.
- Confirmed via a codebase-wide sweep that this is the only occurrence of the pattern (a one-shot readiness gate paired with a component that can be recreated within a process's lifetime) — `NetworkMonitor`'s analogous `init()`/`release()` pair correctly resets its `initialized` flag, and the EPG/Xtream/Drive singletons are only ever initialized once per process from `Application.onCreate()`, so the precondition doesn't apply to them.

**Fix:** Make `instanceReady` a `@Volatile var` and reassign it to a fresh `CompletableDeferred()` in `onDestroy()` right next to the existing `instance = null`, so the next `onCreate()` publishes into an awaitable deferred instead of one that's already permanently completed. Also added `Log.w` at the four silent `?: return` guards in `playStream()`/`performSeamlessRecycle()` that this bug routes through, so a future recurrence (for any reason) leaves a diagnostic trail instead of a silent black screen. Deliberately did **not** add `startForeground()`/Doze-exemption to the service — it legitimately should be reclaimable once nobody is watching (the app already pauses-then-stops playback ~30s after losing focus); the defect was purely about reconnecting correctly on resume, not about keeping the service alive indefinitely.

**Verify:** play a live channel, force the device toward background reclamation (`adb shell dumpsys deviceidle force-idle`, the same trick used for EPG-under-Doze testing), confirm via logcat that `StreamingPlaybackService` actually goes through `onDestroy()` then a fresh `onCreate()`, then select a different channel from the "recently watched" overlay — video should start normally and stats-for-nerds should populate, instead of a black screen with N/A stats. Repeat with the device's actual remote-power-button standby for a couple of hours as the authoritative real-world check.

---

## Lower-confidence / informational (not recommending action without more evidence)

- **EPG timezone handling**: `EpgIndexer`/`XmltvParser` apply a per-source `timezoneOffsetHours` at parse time rather than converting at query time. This is config-dependent, not a code bug per se — if a source's offset is ever misconfigured, programme times would be wrong app-wide for that source, but no logic error was found in the conversion itself. Worth a spot-check against a real XMLTV source's stated timezone if program times are ever reported as off, but not flagging as an active bug.
- **Ruled out** (agent-reported leads that didn't hold up on direct verification): TV's D-pad channel-up/down already correctly gates on `repeatCount == 0` (`PlayerKeyHandler.kt:76,98`) — holding the remote does not spam channel changes. Mobile's `PlayerView` is correctly memoized via unkeyed `remember {}` (`MobilePlayerScreen.kt:401`) — not recreated per recomposition. TV's `StatsOverlay.kt` does already show the live stream-health row, same as mobile — no parity gap there.

---

## Suggested priority

Fix #3 (standby recovery) first, despite being added to this doc last — it's a complete, unrecoverable failure of the core "watch TV" experience (black screen, no error, no recovery short of fully relaunching and hoping the OS hadn't already cycled the service again) triggered by an extremely common real-world action: turning the device off for the night and back on. Fix #1 (EPG staleness) next — a one-line addition to an existing sync path, affects every live-TV session after every background sync, frequent and silent. Fix #2 (mobile gesture) last — lower frequency of occurrence (depends on swipe precision/length) but more jarring/obviously "broken-feeling" when it happens, and mobile-only.
