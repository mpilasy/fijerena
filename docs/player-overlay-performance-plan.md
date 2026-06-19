# Player Overlay Performance: Allocation Reduction

## Context

An external review (antigravity-cli) produced a 5-item allocation-reduction plan targeting the player overlay composables — the next tier of issues after all 14 P0/P1/P2 items in [TODO.md](../TODO.md) shipped. Every claim in that plan was re-verified against the current code below; two of the five proposed fixes would have introduced real bugs if implemented as originally written, and one additional finding (broader than the original audit) surfaced during verification. The plan below supersedes the external one with corrected, minimal-diff fixes.

**The one fact that changes everything here:** `CinemaSurface`, `CinemaBackground`, `CinemaTextPrimary`, `CinemaAccent`, etc. (`tv/.../ui/theme/CinemaColors.kt`, `mobile/.../ui/theme/Color.kt`) are not constants — they're `get()` properties reading `CinemaThemeHolder.current`, a `@Volatile var` set via `SideEffect` whenever the user picks a different theme in Settings (`core/ui/.../theme/CinemaThemePalette.kt`, `tv|mobile/.../ui/theme/Theme.kt:23-24`). Four predefined palettes exist (Deep Night, AMOLED Black, Emerald, Crimson). Any hoisting that caches a *value derived from* these colors must stay correctly invalidated when the palette changes, or theme switching silently breaks wherever the cache was applied. The Compose-tracked handle for this is `LocalCinemaTheme` (a `staticCompositionLocalOf`, same file) — reading `LocalCinemaTheme.current` and using it as a `remember` key is the safe pattern used throughout this plan.

> [!IMPORTANT]
> **Post-implementation correction (superseded P0-1/P0-2/P2-2):** while implementing P0-1, the `remember(palette) { ButtonDefaults.colors(...) }` pattern below turned out to be a **hard compile error** — `remember`'s calculation lambda is `@DisallowComposableCalls`, and decompiling the actual library bytecode (`tv-material3 1.0.0-alpha10`, `material3 1.4.0`) confirmed that `ButtonDefaults.colors()`, `ClickableSurfaceDefaults.colors()`/`shape()`/`border()`, `IconButtonDefaults.filledIconButtonColors()`, and `SliderDefaults.colors()` are themselves `@Composable` functions (their compiled signatures carry an injected `Composer` parameter). Since this project compiles with Kotlin 2.3.0 (modern K2 Compose compiler, with mature support for skippable value-returning composables), Compose's own generated code should already skip re-allocating these objects across recompositions whenever their `Color`/`Shape`/`Border` arguments are unchanged — extracting the call to a local `val` instead of passing it inline as a parameter is semantically identical, with no functional difference. **P0-1, P0-2, and P2-2 were not implemented** as a result — see their sections below for the retraction. P0-3 and P1-1 are unaffected (no Compose-composable involved) and did land; see [Implementation Status](#implementation-status).

---

## Verified Findings

1. **`PlayerControlsOverlay.kt` (tv) does allocate per-recomposition `ButtonDefaults.colors()`**, confirmed at lines 260, 526, 542, 558, 574, 590, 621. Of the seven, five (chapter/audio/subtitle/quality/stats — lines 526, 542, 558, 574, 621) are byte-for-byte identical. The favorite button (590) differs only in `containerColor` (depends on `isFavorite`). The center play/pause button (260) differs only in `containerColor` (`Color.Transparent`, never conditional) — so it's actually a *third*, fully-constant variant, not a one-off.

2. **A bigger version of the same bug lives one layer down, in the shared button components**, not called out by the original audit. `CinemaIconButton`/`CinemaDangerIconButton` — defined separately in `tv/.../ui/components/buttons/CinemaButton.kt:243` and `mobile/.../ui/components/buttons/CinemaButton.kt:32` — allocate their `ClickableSurfaceDefaults.colors()`/`scale()`/`shape()`/`border()` (tv) or `IconButtonDefaults.filledIconButtonColors()` (mobile) fresh on every call, with no `remember` at all. These two composables have **20 call sites app-wide** (`grep -rl "CinemaIconButton("`), including all 5 icon buttons in `MobileControlsOverlay.kt` (audio/subtitle/quality/favorite/stats) — this is what the original plan flagged as "(audit same pattern) TBD" for mobile. Fixing it once here is higher-leverage than fixing it at each call site.

3. **`MetadataOverlay.kt:247`** has exactly one conditional `ButtonDefaults.colors()` (favorite button), as claimed.

4. **`flushWatchHistory()` does run synchronously on the main thread.** Confirmed: `MediaRepository.kt:111` creates `Handler(Looper.getMainLooper())`; `flushWatchHistory()` (line 556) runs `json.encodeToString(history)` and a `SharedPreferences` edit under `synchronized(watchHistoryLock)`, scheduled 500ms after each `addToWatchHistory()` call (line 499-500). `SharedPreferences.Editor` calls are documented thread-safe from any thread, so moving the `Handler` to a background thread is safe with no other code changes.

5. **`StatsOverlay.kt`'s 500ms `while(true)` loop (lines 116-232) is exactly as described** — unconditional `mutableStateOf` writes every tick (no diffing) and a nested `tracks.groups`/`group.length` scan every tick. **However, the original plan's proposed fix — wrapping the player reads in `withContext(Dispatchers.Default)` — is unsafe and would crash.** No `ExoPlayer.Builder.setPlaybackLooper(...)` override exists anywhere in the codebase (confirmed via grep), so the player enforces default ExoPlayer behavior: all `Player` interface calls (`currentTracks`, `bufferedPosition`, `currentPosition`, `videoSize`) must happen on the thread that created the player (the main thread here). Calling them from `Dispatchers.Default` throws `IllegalStateException: Player is accessed on the wrong thread.` on the very first tick. Same bug applies identically to `MobileStatsOverlay.kt`.

6. **5a/5b/5c (`CategoryList.kt:190`, `ContentTypeSelectionScreen.kt:345`, `MobileControlsOverlay.kt:327`) all confirmed** — unhoisted `Brush.verticalGradient`/`horizontalGradient`/`SliderDefaults.colors()`. 5b's `gradientColors` (`listOf(CinemaOrange, CinemaOrangeDark)` etc., lines 217/228/239) is already theme-derived but is passed as a parameter and used as the `remember` key in the original proposal — that's safe as-is, since structural list equality correctly detects a palette change. 5a and 5c read theme colors directly with no key — same staleness risk as item 1/2, needs the `LocalCinemaTheme.current` key treatment.

7. **The original plan's "Option A" for fix #2 (a top-level `object CinemaAlphaColors` with eagerly-computed `val`s like `CinemaSurface.copy(alpha = ...)`) would be a real regression, not just unidiomatic.** Kotlin `object` properties are evaluated once, at first touch, for the lifetime of the process. `CinemaAlphaColors.surfaceMedium` would permanently freeze on whichever palette happened to be active the first time anything in the app referenced it — never updating again even as the user cycles through all four themes. This must not be implemented as written.

8. **The premise behind fix #2 — that `Color.copy()` causes "real GC pressure" via boxing — doesn't hold up.** `Color` is an inline value class; calls typed `color: Color` (true for the overwhelming majority of these ~50 call sites — `Text(color = ...)`, `Modifier.background(...)`, `trackColor = ...`) don't box. The actual allocation cost of a bare `.copy(alpha = ...)` is one `ULong`-sized value, not a heap object competing with the `ButtonColors`/`SliderColors`/`Brush` allocations that items 1, 2, and 5 are about. Recommend **not** doing a sweeping hoist of all ~50 sites — see Fix #2 below for the scoped-down version.

---

## P0 — Land First (high impact, low/no risk)

### P0-1. ~~Hoist icon button color/scale/shape/border in the shared `CinemaIconButton`/`CinemaDangerIconButton` components~~ — RETRACTED, not implemented
**Files:** `tv/src/main/java/org/njarasoa/fijerena/ui/components/buttons/CinemaButton.kt:243-333`, `mobile/src/main/java/org/njarasoa/fijerena/ui/components/buttons/CinemaButton.kt:26-80`

**Status: skipped.** The `remember(palette) { ClickableSurfaceDefaults.colors(...) }` pattern originally proposed here does not compile — `ClickableSurfaceDefaults.colors()`/`shape()`/`border()` and mobile's `IconButtonDefaults.filledIconButtonColors()` are themselves `@Composable` (confirmed by decompiling the AARs), and `remember`'s lambda is `@DisallowComposableCalls`. The only legal alternative — assigning the call directly to a local `val` instead of inlining it as a `Surface`/button parameter — is semantically identical to the status quo from Compose's perspective (same call site, same automatic skip-on-unchanged-args behavior the framework already applies to any `@Composable` factory with stable parameters). It would be a no-op refactor, not a fix. See the [Implementation Status](#implementation-status) note for the decision to skip this rather than make a churn-only change.

### P0-2. ~~Hoist the 3 button-color variants in `PlayerControlsOverlay.kt`~~ — RETRACTED, not implemented
**File:** `tv/src/main/java/org/njarasoa/fijerena/ui/player/components/overlays/PlayerControlsOverlay.kt:260,526,542,558,574,590,621`

**Status: skipped**, same reason as P0-1 — `ButtonDefaults.colors()` (tv-material3) is `@Composable` (confirmed: `colors-oq7We08(..., Composer, int, int)` in the decompiled class), so the proposed `remember(palette) { ButtonDefaults.colors(...) }` hoist is a compile error, and a plain-`val` consolidation of the 7 call sites into 3 shared ones would not change allocation behavior on this toolchain (Kotlin 2.3.0, modern K2 Compose compiler). `PlayerControlsOverlay.kt` was left untouched.

### P0-3. Move `flushWatchHistory()` off the main thread — DONE
**File:** `core/network/src/main/java/org/njarasoa/fijerena/core/network/MediaRepository.kt:111`

```diff
-    private val watchHistoryWriteHandler = android.os.Handler(android.os.Looper.getMainLooper())
+    private val watchHistoryWriteThread = android.os.HandlerThread("WatchHistoryWriter").apply { start() }
+    private val watchHistoryWriteHandler = android.os.Handler(watchHistoryWriteThread.looper)
```
No other change needed — `flushWatchHistory()` (line 556) and `clearWatchHistory()` (line 567) already guard the shared state with `synchronized(watchHistoryLock)`, and `SharedPreferences.Editor` is documented safe to call from any thread. `MediaRepository` has no existing teardown hook (it's a process-lifetime singleton, like the rest of the repo's repositories) — skip adding a `shutdown()`, it'd never be called and the thread dies with the process like every other background thread in the app.

**Risk:** very low.

**Verify:** play a VOD/live item for >1s (triggers `addToWatchHistory` → debounced flush), confirm watch history still persists across app restart; check no `IllegalStateException`/ANR in logcat during playback. Verified: `:core:network:compileDebugKotlin` succeeds.

---

## P1 — Land Next (real but smaller wins)

### P1-1. `StatsOverlay.kt` / `MobileStatsOverlay.kt`: diff-before-write + skip unchanged track scans — DONE
**Files:** `tv/.../overlays/StatsOverlay.kt:116-243`, `mobile/.../components/MobileStatsOverlay.kt:103-220`

Dropped the original plan's `Dispatchers.Default` offload (finding 5 — unsafe, would crash). **Correction to this plan's own original wording:** `CinemaAnimation.statsUpdateMs` (`core/ui/.../theme/CinemaAnimation.kt:15`) was checked before implementing and turned out to already be `1_000L` — the "500ms" figure came from the original external plan and was never independently verified before now. No interval change was made; only the two real wins below landed:

1. **Skip the nested track-group scan when `currentTracks` hasn't changed.** A `lastTracks` reference (plus cached `selectedVideoFormat`/`currentVideoBitrate`/`currentAudioBitrate`, persisted across loop iterations) gates the whole `tracks.groups`/`group.length` scan behind a `tracks !== lastTracks` check. One subtlety handled explicitly: frame rate also depends on the live `serviceMeasuredFps` signal (the common fallback for streams whose container doesn't report a static rate) — that field is recomputed every tick *outside* the tracks-changed guard using the cached `selectedVideoFormat`, so the "measured fps" display doesn't freeze between track changes.
2. **Diff before assigning to `mutableStateOf` vars** — every stat var (`droppedFrames`, `bufferHealth`, `videoCodec`/`videoResolution`/`videoBitrate`, `audioCodec`/`audioSampleRate`/`audioChannels`/`audioBitrate`, `networkSpeed`, `videoFrameRate`) is now computed into a local `newX` and only assigned if it changed. `bufferedPosition` and `streamElapsed` were deliberately left as direct assignments — both are continuously-changing (position/clock) values where a diff check would be pure overhead with no recomposition-avoidance benefit.

Also cleaned up a leftover duplicate assignment in the tv version (`currentVideoBitrate = format.bitrate` was written twice in a row, lines 167 & 169 of the original).

All of this stays on the main thread, since it's all `Player` reads — no threading change, no crash risk.

**Risk:** low. Worst case a stat lags by one extra tick on a genuine change, imperceptible for a "stats for nerds" panel.

**Verify:** `:tv:compileDebugKotlin` and `:mobile:compileDebugKotlin` both succeed. Manual verify still pending: open Stats overlay during playback, confirm values still update on actual changes (start playback, change quality/audio/subtitle track) and that the measured-fps fallback keeps live-updating rather than freezing.

### P1-2 (scoped down). ~~Hoist `Color.copy()` only where it rides along with P0-2's button-color objects~~ — moot, nothing to do
Originally scoped to ride along with P0-1/P0-2's hoisted `ButtonColors` objects "for free." Since P0-1 and P0-2 were retracted (see above), there's no host left to fold this into. Combined with finding 8 (no real boxing cost for `Color.copy()` at these call sites) and finding 7 (the original "Option A" singleton-object approach is unsafe), there's no remaining case for touching any of the ~50 standalone `Color.copy()` call sites. No action taken.

---

## P2 — Low effort, do opportunistically

### P2-1. `CategoryList.kt:190` — hoist border `Brush.verticalGradient`
```diff
+    val palette = org.njarasoa.fijerena.core.ui.theme.LocalCinemaTheme.current
+    val borderBrush = remember(palette) {
+        Brush.verticalGradient(listOf(CinemaGlassBorder, Color.White.copy(alpha = CinemaAlpha.ghost), CinemaGlassBorder))
+    }
     // ...border(brush = borderBrush, ...)
```

### P2-2. ~~`MobileControlsOverlay.kt:327` — hoist `SliderDefaults.colors()`~~ — RETRACTED, same issue as P0-1/P0-2
Decompiling `material3-android 1.4.0` confirms `SliderDefaults.colors()` is also `@Composable` (`colors-q0g_0yA(..., Composer, int, int, int)`), so `remember { SliderDefaults.colors(...) }` is a compile error here too, and there's only one call site to begin with (nothing to consolidate). Recommend leaving `MobileControlsOverlay.kt:327` untouched — not attempted.

### P2-3. `ContentTypeSelectionScreen.kt:345` (mobile) — already safe as proposed
`remember(gradientColors) { Brush.horizontalGradient(colors = gradientColors) }` — confirmed safe, no change needed beyond what the original plan proposed. Listed here only for completeness against the original 5-item plan.

---

## Summary Matrix

| # | Fix | Impact | Effort | Status |
|---|-----|--------|--------|--------|
| P0-1 | Hoist colors in `CinemaIconButton`/`CinemaDangerIconButton` (tv+mobile) | — | — | **Retracted** — `ClickableSurfaceDefaults.colors()`/`shape()`/`border()` are `@Composable`; `remember{}` wrapping is a compile error, plain-`val` extraction is a no-op |
| P0-2 | Hoist 3 button-color variants in `PlayerControlsOverlay.kt` | — | — | **Retracted** — same reason, `ButtonDefaults.colors()` (tv-material3) is `@Composable` |
| P0-3 | `flushWatchHistory()` → background `HandlerThread` | Medium | Low | **Done** — `core/network/.../MediaRepository.kt:111`, `:core:network:compileDebugKotlin` passes |
| P1-1 | Stats overlays: skip-unchanged-tracks + diff writes | Medium | Medium | **Done** — `StatsOverlay.kt` + `MobileStatsOverlay.kt`, `:tv:compileDebugKotlin`/`:mobile:compileDebugKotlin` pass. statsUpdateMs was already 1000ms, no interval change needed |
| P1-2 | Color.copy hoisting, scoped to P0-1/P0-2 only | — | — | **Moot** — nothing to fold into once P0-1/P0-2 were retracted |
| P2-1 | `CategoryList.kt` border `Brush.verticalGradient` hoist | Low | Low | Not yet attempted — `Brush.verticalGradient()` is a plain (non-composable) factory, this one should hold up |
| P2-2 | `MobileControlsOverlay.kt` `SliderDefaults.colors()` hoist | — | — | **Retracted** — `SliderDefaults.colors()` is also `@Composable`, same issue as P0-1/P0-2 |
| P2-3 | `ContentTypeSelectionScreen.kt` gradient — already safe | — | — | No change needed, confirmed safe as originally proposed |

**Common thread across every theme-staleness correction:** any `remember` that captures a *plain* (non-`@Composable`) value derived from `CinemaSurface`/`CinemaTextPrimary`/etc. — e.g. P2-1's `Brush` — must be keyed on `LocalCinemaTheme.current`, not bare `remember {}`, or it freezes at the theme active when that composable first entered composition.

**Common thread across every retraction (P0-1, P0-2, P2-2):** the underlying factory function (`ButtonDefaults.colors()`, `ClickableSurfaceDefaults.colors()`/`shape()`/`border()`, `IconButtonDefaults.filledIconButtonColors()`, `SliderDefaults.colors()`) is itself `@Composable`. `remember {}` can't wrap a composable call (`@DisallowComposableCalls`), and manually hoisting to a local `val` changes nothing Compose-wise versus the inline-as-parameter status quo — the framework's own skip-on-unchanged-stable-args mechanism already applies at that call site either way. This was verified by decompiling the actual AARs (`tv-material3 1.0.0-alpha10`, `material3 1.4.0`) and checking for the compiler-injected `Composer` parameter, not assumed.

## Implementation Status

Landed (2026-06-19): **P0-3**, **P1-1**. Compile-verified via `./gradlew :core:network:compileDebugKotlin :tv:compileDebugKotlin :mobile:compileDebugKotlin`; manual on-device verification (watch history persistence, stats overlay live updates) still outstanding. Retracted: P0-1, P0-2, P2-2 (see Summary Matrix). Not yet attempted: P2-1, P2-3.
