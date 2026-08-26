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

### Live TV (measured 2026-08-26, after tasks 1 and the related-card hoist landed)

Everything above is the Movies path. Live TV measured separately — same device and provider, 280
Live TV categories.

| Flow | Frames | Janky | p50 | p95 | p99 |
|---|---|---|---|---|---|
| Home → Live TV entry | 597 | 10.7% | 5ms | 18ms | 150ms |
| Preview channel-list focus churn (video playing) | 832 | 8.5% | 5ms | 28ms | 48ms |
| Idle on preview, video playing | 300 in 6s | **0.0%** | 5ms | 5ms | 6ms |
| **Back out of preview** | 46 | 15.2% | 6ms | **400ms** | **500ms** |
| Bare browse screen, 20 × DPAD_DOWN (280 categories) | 637 | 3.9% | 5ms | 15ms | 21ms |
| **Select a Live TV category** | 47 | **87–94%** | 19ms | 44ms | 150–200ms |
| Back → Home | 425 | 2.4% | 5ms | 9ms | 48ms |

**Selecting a Live TV category is the worst flow in the app** — and the category measured had only
**12 streams**, so this is not list size. `atrace` during it:

```
section                            thread            max_ms    n    total
Background concurrent copying GC   HeapTaskDaemon     466.8    1    466.8
MarkingPhase                       HeapTaskDaemon     272.1    1    272.1
Choreographer#doFrame              main               115.2   64    519.6
traversal                          main                96.8   47    332.6
AndroidOwner:measureAndLayout      main                90.4   48    161.7
allocateHardwareBitmap             DefaultDispatcher   41.8    4    125.7
```

A **467ms concurrent GC** lands inside the flow, and the main thread spends up to 90ms per layout
pass. Process total is ~400MB against the 512MB ceiling, which fits the GC pressure.

**Task 8 confirmed on device.** Pressing Back once from the Live TV preview does not leave Live TV —
it lands on a *second*, fully-populated Live TV `CategoryList` (280 categories, its own Recent
stream list) that was built and loaded but never shown. Exactly the double-push in
`TvNavHost.kt:196-218`.

**Coil disk-cache contention (new, not previously in this plan).** 922ms of blocked
`DefaultDispatcher` worker time on `coil3.disk.DiskLruCache$Snapshot`, spread over 12+ worker
threads, to load ~12 channel logos — single events up to 168ms. This does **not** block the UI
thread (main thread total blocked: 0.5ms), so it is a throughput problem in image loading, not a
jank source. It is why logos trickle in rather than appearing together.

**The video preview itself is cheap** — idling on the split layout with a channel playing is 0.00%
janky at 5ms per frame. That independently confirms dropping the `TextureView` item was right.

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

### 6. Player exit — **misattributed in the first draft; measured 2026-08-26**

The draft blamed `staggeredEntrance` replaying on back-navigation. That is wrong for this flow:
**`MovieDetailsScreen` does not use `staggeredEntrance` at all**, and `finalizeSession` is cheap
(it reads a position and hands off to `Dispatchers.IO`).

Instrumented with a temporary `LayoutModifierNode` that wraps each subtree's measure in an atrace
section — Compose emits no per-composable layout markers, so `AndroidOwner:measureAndLayout` is
otherwise one opaque number. Result, exiting the player:

```
   t(s)   dur_ms  section
  2.789     13.2  ViewPostImeInputStage          <- Back keypress
  2.848     92.2  Choreographer#doFrame
  2.891     41.7    AndroidOwner:measureAndLayout
  2.891     33.8      LAYOUT:rootScrollColumn
  2.975    243.6  Choreographer#doFrame
  2.993    215.0    AndroidOwner:measureAndLayout
  2.994    114.9      LAYOUT:relatedRecommended
  3.109     85.0      LAYOUT:relatedSimilar
```

The cost is the two `RelatedTitlesRow` LazyRows — **200ms of layout for ~14 poster cards**, each a
`tv.material3.Card` with an image and a two-line title that has to be text-laid-out. On first entry
this is invisible: `relatedTitles` arrives from the provider *after* the screen is laid out, so the
rows land in a later frame by themselves. Coming back from the player everything is cached, so it
all collided with the frame answering the Back keypress.

**Tried and reverted:** holding the rows out of the first frame (`withFrameNanos`, then compose
them). Measured:

| | before | with deferral |
|---|---|---|
| Back keypress → first painted frame | ~300ms | ~92ms |
| layout in that frame | ~240ms | 34–42ms |
| frames rendered during exit | 17–20 | 6–7 |
| janky frames | 25% | **100%** |
| p50 / p90 | 7ms / 200–300ms | 81–125ms / 200–250ms |

It improved input latency 3.3x but moved the 200ms rather than removing it — the hitch just landed
one frame later, and jank percentage went to 100% because the frame *count* collapsed and only the
heavy frames were left. Reverted on that basis: not worth a deferral mechanism for a metric that
trades one number against another. **Do not re-propose it** — the measurement above is the reason.

**Kept:** `RelatedTitleCard`'s per-card `CardDefaults.colors/scale/border/glow/shape` hoisted into a
per-row `@Immutable RelatedCardStyle`, matching `StreamList`'s `StreamCardStyle`. Independent of the
deferral, and the same allocation-churn fix as task 2.

**The real fix, still to do — needs a design decision.** `EpisodeSelectionScreen` already puts these
same rows inside a `LazyColumn` as `item {}` blocks (`:847-866`), so its off-screen row is never
composed or laid out. `MovieDetailsScreen` scrolls with `Column(Modifier.verticalScroll(...))`,
which measures every child including the fully off-screen `Similar Titles` row — 85ms for content
nobody has scrolled to. Matching the sibling screen would delete that 85ms outright rather than
deferring it. The obstacle is not the scrolling container: the rows sit *inside* the `GlassPanel`
next to the metadata (`:626-637`), so hoisting them to top-level lazy items moves them out of the
glass panel visually. That is a design change, so it is not mine to make.

### 6b. Entrance animations replay on every back-navigation (still open, different screens)
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

### 8. Live TV entry loaded the category screen twice — **FIXED 2026-08-26**
`tv/.../navigation/TvNavHost.kt:196-224`

The browse screen was pushed first, and the `getLastItemId` check that decides whether to push the
preview on top of it ran *after*, in a coroutine. That suspend point was the bug: it let the browse
screen become the real current destination, so it composed, built its own `CategoryViewModel`, and
ran a full `loadCategoriesInternal()` → `getFilteredCategories()` → `loadStreams()` →
`loadNowPlaying()` for a screen the user never saw.

**Fix:** resolve the repository and the last-played channel first, then issue both `navigate()`
calls back to back with no suspend point between them. The browse entry still sits on the back stack
so Back behaves exactly as before, but it is never composed until Back actually reveals it. Also
removes a visible flash of the browse screen on the way into Live TV.

**Verified by counting ViewModel constructions** (temporary log in `CategoryViewModel.init`, since
frame counts cannot distinguish this):

| Live TV entry | `CategoryViewModel` created |
|---|---|
| before | **2** |
| after | **1** (the second is built lazily when Back reveals the browse screen) |

**Effect, same device, three runs each, on categories whose logos were not yet cached:**

| | before | after |
|---|---|---|
| Select a Live TV category, janky frames | 100% / 18.7% / 13.9% | **0.35% / 0.17% / 0.00%** |
| p50 | 5–18ms | **5ms** |
| Back out of preview, p95 | 400ms | **101–133ms** |
| Back out of preview, p90 | 105ms | **38–53ms** |
| Live TV entry, janky frames | 10.7% | 10.1% (unchanged) |

Two caveats on reading this. Back-out-of-preview's *jank percentage* rose (15% → ~19%) even as its
tail improved 3–4x: the browse screen now does its one real load at that moment instead of having
done it in the background, and the frame count is small, so a few busy frames dominate the
percentage. And the first run after any install reads far worse than steady state (100% vs 0.35% on
the same flow) — it is JIT-cold; discard it.

The mechanism behind the category-select improvement is that one `CategoryViewModel` instead of two
halves the concurrent stream/EPG/logo loading, which is what produced the 467ms GC and the 922ms of
Coil disk-cache contention across 12+ worker threads recorded above.

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
| 3 | **6** | **Diagnosed, not fixed.** Cause measured precisely (200ms of related-row layout); the deferral was tried and reverted. Real fix blocked on a design call — see task 6 |
| 3b | **8** | **Done** — one `CategoryViewModel` on Live TV entry instead of two; category select went from 14–100% janky to under 0.4% |
| 4 | 2, 3, 5 | Allocation churn feeding the 467–522ms GCs; safe, no behaviour change |
| 5 | 7 | Nav transition still holds two screens live for 300ms |
| 6 | 4 | Favorite lock per item — measured as *not* a UI-thread blocker (0.5ms), so lowest priority |

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
