# TV UI Performance Plan (NVIDIA Shield)

**Symptom:** playback is fine; the UI is not. Focus moves between menu items, screen-to-screen
navigation, and returning from a stream are all sluggish on the Shield.

**Status:** measured on hardware 2026-08-26. Priorities below are derived from the measurements,
not from code reading — an earlier draft of this plan guessed wrong about the GPU and about the
blur, and the numbers corrected it.

---

## Measured baseline

Device: `192.168.68.21` — NVIDIA Shield (darcy), **Android 11 / API 30**, app surface 1920x1080.
Provider: stream4ktv (Xtream), 915 Live / 441 Movies / 374 TV Shows categories.
Build: debug. Tool: `dumpsys gfxinfo` + `atrace`.

The **after** column was measured 2026-08-26 on the same device, same provider, after task 1 landed.

| Flow | Janky (before) | Janky (after) | p50 before → after | p95 before → after |
|---|---|---|---|---|
| Home — focus across the 3 cards (24 moves) | 0.00% | 1.13% | 5 → 5ms | 8 → 9ms |
| Category list — 20 × DPAD_DOWN | 2.95% | 2.36% | 5 → 5ms | 14 → 14ms |
| Select category → load stream list | 20.3% | **1.19%** | 5 → 5ms | 20 → 11ms |
| **Home → Movies category screen** | **89.9%** | **12.7%** | 19 → 5ms | 89 → 39ms |
| **Stream list → Movie Details** | **53.3%** | **26.0%** | 16 → 5ms | 20 → 101ms |
| **Back out of player → Details** | 25.0% | 26.3% | 7 → 7ms | 250 → 200ms |

Home → Movies is the mean of three consecutive runs (12.96 / 12.73 / 12.50%) — a single run of any
of these covers only ~60 frames, so one GC dominates a lone sample. Two caveats on the after column:
Stream list → Movie Details rendered 347 frames before and 100 after, so its p95 is computed over a
much smaller and differently-shaped sample (the p50 drop from 16ms to 5ms is the trustworthy half);
and the player-exit row played different content than the before run, so treat it as
order-of-magnitude.

Idle frame counts (zero input, screen settled):

| Screen | Before | After |
|---|---|---|
| **Home / ContentTypeSelection** | **283 frames in 6s** | 353 frames in 6s, **0.00% jank**, p50 5ms |
| Movies category screen (empty category) | 0 | 0 |
| Movies category screen (45 streams, posters loaded) | 0 | 0 |
| Movie Details | 0 | 0 |

The Home idle frame count did **not** go to zero, and it was wrong to state that as the gate: the
pulse dot is still an animation, so it still redraws. The gate that actually matters is whether it
still *recomposes*. Measured with `atrace`, 4 seconds idle on Home:

| Section | Before | After |
|---|---|---|
| `Choreographer#doFrame` | 243 | 242 |
| **`Compose:recompose`** | **242** | **0** |
| `Recomposer:recompose` | 242 | 242 |
| `traversal` | 191 | 242 |
| `draw` | 191 | 243 |

Composition is now completely idle; what remains is a small layer redraw at 0.00% jank and 5ms per
frame. If the permanently-awake RenderThread is later judged not worth a blinking dot, that is a
design call, not a performance one.

`atrace` on the Home screen, **4 seconds of zero input**:

```
Choreographer#doFrame    243
Compose:recompose        242
Recomposer:recompose     242
traversal                191
draw                     191
```

`atrace` during Home → Movies (8s window), top costs:

```
section                    thread            total_ms      n   max_ms
Choreographer#doFrame      main               2134.4     588    111.0
DrawFrame                  RenderThread       1821.4     475     10.2
animation                  main               1245.6     588     43.8
Recomposer:recompose       main                977.4     588     43.2
traversal                  main                856.0     473     67.2
Compose:recompose          main                624.1     590     27.1
Background concurrent GC   HeapTaskDaemon      522.7       1    522.7
Compiling (JIT)            —                    73.7      45     18.5
```

Player-exit frame breakdown (the 239ms frame):

```
#   recompose  meas/lay   record  syncwait  issue>swap   TOTAL
0        38.1       0.1    193.1       0.3         0.6   239.0
```

### What the numbers say

1. **The GPU is not the bottleneck.** GPU p90 = 3ms, p99 = 13ms across every flow. `DrawFrame` on
   the RenderThread averages 3.8ms. Everything expensive is Compose on the main thread.
2. **The Home screen recomposes at 60fps forever, with nothing on screen changing.** 242
   recompositions and 191 full measure/layout/draw traversals in 4 idle seconds. No other screen
   does this — every other screen idles at exactly 0 frames.
3. **A 522ms garbage collection lands inside an 8-second navigation.** That is allocation churn,
   which the per-item allocation defects below feed directly.
4. **Player exit spends 193ms in a single draw-recording pass** (Compose defers measure+layout into
   draw here, so that number is the whole screen being rebuilt from nothing).
5. **`AmbientBackdrop` and `GlassPanel` blur never runs on these Shields.** Both are gated on
   API 31+; the Shields are API 30. The earlier draft's "full-screen 140px blur" theory is dead on
   this hardware — it applies only to the Sony BRAVIA (API 31). Corrected below.
6. JIT compilation costs ~90ms inside the navigation window — real, but small, and it is what a
   baseline profile would remove. See "Not proposed".

---

## P0 — The idle recomposition loop — **DONE 2026-08-26**

### 1. ~~The Live TV "pulse" dot recomposes the Home screen at 60fps, forever~~ — fixed
`tv/.../feature/contentselection/ContentTypeSelectionScreen.kt:553-571`
`mobile/.../feature/contentselection/ContentTypeSelectionScreen.kt:439-457` (identical bug)

```kotlin
val pulseAlpha by pulseTransition.animateFloat(...)   // infiniteRepeatable, never ends
Box(
    modifier = Modifier
        .size(...)
        .border(..., CinemaTextPrimary.copy(alpha = pulseAlpha), CircleShape)
        .background(CinemaLive.copy(alpha = pulseAlpha), shape = CircleShape),
)
```

`pulseAlpha` is read **in the composable body**, so every frame of the animation invalidates
composition — not just drawing. `showLivePulse = true` is hardcoded on the Live TV card
(`:367` on TV, `:276` on mobile), so this runs from the moment Home appears until it is
navigated away from. Each frame also allocates two `Color` objects via `.copy()` — the exact
pattern AGENTS.md lists as a recomposition hotspot — which feeds the 522ms GC above.

**Fix:** move the alpha to a draw-only layer so the animation never re-enters composition:

```kotlin
Box(
    modifier = Modifier
        .size(TvDimensions.liveDotSize.scaled(scale))
        .graphicsLayer { alpha = pulseAlpha }          // lambda: read deferred to draw
        .border(TvDimensions.borderThin, CinemaTextPrimary, CircleShape)
        .background(CinemaLive, shape = CircleShape),
)
```

**Landed:** applied to both TV and mobile. `Compose:recompose` on idle Home went 242 → **0**, and
Home → Movies went from 89.9% to 12.7% janky frames (p50 19ms → 5ms). Selecting a category also fell
from 20.3% to 1.19% — that flow was never itself slow, it was starving behind Home's loop.

**Why this was P0:** Home is the app's root screen and the origin of every navigation. Burning the
entire main-thread frame budget on it meant every transition *out* of it started from a standing
deficit — which is what the 89.9% on Home → Movies was.

**What it did not fix:** player exit, unchanged at ~26% and still a ~198ms frame (152ms of it in the
draw-recording pass rebuilding the Details screen). That is task 6 below, and it is now the worst
remaining number in the table.

---

## P1 — Allocation churn (feeds the 522ms GC)

Each is a few lines, no behaviour change, and the codebase already established the right pattern
elsewhere in every case.

### 2. `CategoryItem` allocates four card-style objects per row per recomposition
`tv/.../feature/category/components/CategoryList.kt:321,337,339,345`

`CardDefaults.colors/shape/scale/glow` inside the item body. `StreamList` hoists the identical set
into an `@Immutable StreamCardStyle` built once per list composition (`StreamList.kt:91-125`)
precisely to stop this; `CategoryList` was never migrated.

**Fix:** mirror `streamCardStyle`. `containerColor`/`contentColor` depend on `isSelected`, so build
*two* instances at list level (selected + unselected) and pick per row.

### 3. `staggeredEntrance` re-measures every frame for a pure layer animation
`core/ui/.../components/StaggeredEntrance.kt:68,72`

Animates `alpha` and `translationY`, applies both inside `placeable.placeWithLayer` — neither can
change measured size — yet calls `invalidateMeasurement()` each frame, dragging the item **and its
parent `TvLazyColumn`** through a measure pass. Every visible row runs this at once on screen entry.

**Fix:** `invalidatePlacement()` at both sites.

**Relevance:** this is inside the 193ms player-exit draw pass and the 67ms `traversal` spikes.

### 4. Favorite lookups take a lock per item
`core/ui/.../viewmodels/CategoryViewModel.kt:456-467`

`isFavorite()` / `isFavoriteCategory()` called per item inside `filter`, each entering
`synchronized(favoriteLock)` (`MediaRepository.kt:665-674`, `:739-748`). The sets are already
cached; only the lock round-trip is wasted, thousands of times per list load.

**Fix:** expose the cached id sets, take one lock, do membership tests in the loop.

**Note:** runs on `Dispatchers.Default`, so it delays badges appearing rather than blocking frames.

### 5. Per-key logging in `dispatchKeyEvent`
`tv/.../MainActivity.kt:96-99` — `Log.i` with string interpolation on every D-pad event including
key repeats. Delete or gate on `AppSettings.isDevMode`.

---

## P2 — Screen rebuild cost

### 6. Entrance animations replay on every back-navigation
`StreamList.kt:186,346` · `CategoryList.kt:156,267`

`enteredStreamIds` / `enteredCategoryIds` are plain `remember`, not `rememberSaveable`. Navigation
Compose disposes a destination's composition on navigate-away, so popping back loses the set and
**every visible row replays the staggered entrance** — each currently forcing a measure pass
(task 3), on top of the 300ms cross-fade holding both screens live.

This is the measured 239ms player-exit frame: 38ms recompose + 193ms rebuilding the screen.

**Recommend:** drop `staggeredEntrance` from list *rows* entirely; keep it for headers and hero
elements. Rows enter and leave constantly on a D-pad UI. The alternative — tracking entered ids in
`rememberSaveable` — still pays full price on first entry into every list.

### 7. Nav transition holds two screens live for 300ms
`tv/.../navigation/TvNavHost.kt:186-189`

`CinemaAnimation.navTransitionMs = 300`, during which both screens are composed and drawn. Already
cut down from navigation-compose's 700ms default. With Home → Movies at p50 19ms per frame, 300ms
of double-composition is ~18 frames that all miss.

**Fix:** try 150–200ms, and consider `EnterTransition.None` for `Screen.CategoryList`. Cheap to try,
trivial to revert.

### 8. Live TV entry loads the category screen twice
`tv/.../navigation/TvNavHost.kt:196-218`

Selecting Live TV pushes `CategoryList(showPreviewPane = false)` and then immediately pushes
`CategoryList(showPreviewPane = true)` on top. Two back-stack entries, two `CategoryViewModel`s,
each independently running `loadCategoriesInternal()` → `getFilteredCategories()` → `loadStreams()`
→ `loadNowPlaying()` (index query plus up to five chunked EPG network calls). One is never seen.

The bare entry is needed so Back has a destination — but it does not need its data loaded until
shown.

**Fix:** gate the `init` load on visibility, or push a lightweight Back-stopover destination, or
share one ViewModel across both entries.

---

## Reprioritised down — measured as not the problem

- **`AmbientBackdrop` / `GlassPanel` blur.** Both gated on API 31+; the Shields are API 30, so
  neither blur path executes there at all. The `GlassPanel.kt:65` defect is real (a fresh
  `RenderEffect` allocated inside the `graphicsLayer {}` lambda on every redraw, where
  `AmbientBackdrop.kt:100-125` correctly hoists its own) — but it only costs anything on the
  **Sony BRAVIA (API 31)**. Fix it when touching that file; it is not a Shield item.
- **`TextureView` for the Live TV promoted player** (`LiveTvSplitLayout.kt:295`). GPU p90 is 3ms —
  compositing is not the constraint. Do not touch this; the ANR it guards against is documented and
  the saving is unmeasurable here.
- **`androidx.tv.foundation` lazy-list migration** (9 screens). Category-list focus churn measured
  2.95% jank / p99 22ms. The lists are not the problem. Leave the migration alone.

---

## Not proposed — noted so the baseline reads correctly

The Shield runs a **debug** build (`tv/build.gradle.kts` has no `debug {}` block; `release` has
`isMinifyEnabled = false`). The trace shows ~90ms of JIT `Compiling` inside the navigation window,
and Compose's `sourceInformation`/`traceEventStart` bookkeeping is only stripped by R8 in a minified
release build. Per the standing decision, R8 and baseline profiles stay parked until public
release — this is here only so the numbers above are read as debug-build frame times.

---

## Suggested order

| Step | Tasks | Status |
|---|---|---|
| 1 | **1** | **Done** — `Compose:recompose` on idle Home 242 → 0 |
| 2 | re-measure all six flows | **Done** — see the after column above |
| 3 | **6, 7** | **Next.** Player exit is now the worst row: ~198ms frame, 152ms of it rebuilding the screen |
| 4 | 2, 3, 5 | Allocation churn feeding the 522ms GC; safe, no behaviour change |
| 5 | 4, 8 | Load-time cleanup |

## Verification

The gate is **recomposition count, not frame count** — a draw-only animation still renders frames by
design, so `Total frames rendered` cannot distinguish a fixed screen from a broken one:

```bash
D=192.168.68.21:5555
PID=$(adb -s $D shell pidof org.njarasoa.fijerena | tr -d '\r')
adb -s $D shell 'atrace --async_start -b 20000 -a org.njarasoa.fijerena gfx view'
sleep 4   # screen settled, no input
adb -s $D shell 'atrace --async_stop -a org.njarasoa.fijerena gfx view' > idle.atrace
grep -c "B|$PID|Compose:recompose" idle.atrace   # must be 0 on a settled screen
```

Then re-measure every flow in the table. Two process notes learned the hard way:

- **Re-establish focus deterministically before each run and screenshot to confirm it.** Repeated
  BACK/CENTER drifts focus; one batch of three "runs" turned out to be measuring the Switch Provider
  dialog, and another measured the EPG Search screen instead of Movies.
- **Discard the first run after an install** — it is JIT-cold and reads far worse (64.6% versus a
  steady-state 12.7% on the same flow).

Reproduce with:
```bash
D=192.168.68.21:5555
adb -s $D shell dumpsys gfxinfo org.njarasoa.fijerena reset
# ... drive the flow (adb shell input keyevent 20/21/22/23/4) ...
adb -s $D shell dumpsys gfxinfo org.njarasoa.fijerena | sed -n '6,17p'
```
`atrace` for section-level attribution (no extra deps needed):
```bash
adb -s $D shell 'atrace --async_start -b 40000 -a org.njarasoa.fijerena gfx view res dalvik sched'
# ... drive the flow ...
adb -s $D shell 'atrace --async_stop -a org.njarasoa.fijerena gfx view res dalvik sched' > trace.txt
grep -c "B|<pid>|Compose:recompose" trace.txt
```
