# UI Responsiveness Audit — fijerena

Companion to `UX_FLOW_AUDIT.md`, which covers navigation *friction* (IA, discoverability). This
is the orthogonal axis: how fast the app feels. Audited 2026-08-17.

**Status:** findings only — nothing here has been acted on.

---

## Delivery plan

Ten steps. Each one is self-contained: builds, lints, deploys to both Shields and the phone, has a
specific on-device check, and lands as one commit. Stop after any step without leaving the app in a
half-done state.

Items too small to justify their own deploy are grouped with their nearest relative, so no step is
a one-line commit and no step bundles unrelated risk.

**Before step 1:** restore `animator_duration_scale` to `1.0` on darcy
(`adb -s 192.168.68.21:5555 shell settings put global animator_duration_scale 1.0`). Several checks
below are invisible while animations are off, and you've said the `0.0` is leftover.

---

### Step 1 — The two bugs · §E1, E2 · low risk
`CategoryList.kt:125-134` key the spinner loop on `categoriesRefreshing` (copy `StreamList.kt:101`).
Move the `enteredStreamIds`/`enteredCategoryIds` first-seen check out of composition —
`StreamList.kt:289`, `CategoryList.kt:154/263`, `MobileCategoryListScreen.kt:958/993/1038/1078/1166`.

**Check:** pull-to-refresh a category, let it finish, leave the screen idle. `dumpsys gfxinfo
org.njarasoa.fijerena` — `Total frames rendered` must stop climbing at rest. Today it climbs forever
after the first refresh. Entrance animation should now play fully instead of snapping.
**Commit:** `fix(browse): stop the refresh spinner and entrance animation fighting recomposition`

### Step 2 — Live TV first frame · §A1 · low risk
Call `setContentType(LIVE_TV)` on the `LiveTvSplitLayout` path.

**Check:** `adb logcat | grep -i loadcontrol` on a live channel — expect the LIVE profile, not VOD.
Time OK→first frame from `screenrecord` frame counts, before and after; expect ~500ms off.
**Commit:** `fix(live-tv): select the live buffer profile on the preview and promoted paths`

### Step 3 — Permanent per-frame work in lists · §D2, D3 · low risk
`remember` the `ImmutableMediaList` wrapper at `LiveTvSplitLayout.kt:508`. Focus-gate the marquee at
`CategoryList.kt:361` and the four mobile sites — finishing what `51c5f628` started.

**Check:** sit idle on a category list with long names. Frames rendered must flatline at rest; today
each overflowing row runs its own `invalidateDraw` loop. Scroll `Janky frames` % should drop.
**Commit:** `perf(browse): stop marquee and entrance work on idle rows`

### Step 4 — TV navigation feel · §B1, B5 · low risk
Declare the four transitions on `TvNavHost` (mobile's 300ms is the obvious reference). Give TV's
episode screen mobile's `lastSuccess` retention so refresh stops blanking the list.

**Check:** navigate Home → Movies → detail → Back. Should feel deliberate rather than a 700ms
double-fade; Live TV's double-push should no longer stack two crossfades. Refresh a series and
confirm the list stays put.
**Commit:** `feat(tv): declare navigation transitions, keep episode list through refresh`

### Step 5 — Compose stability · §D1 · medium risk
Add the Compose compiler plugin (or a stability config) to `core:player`.

**Check:** the one step that needs a real before/after artifact — build with
`-Pandroidx.compose.compiler.plugins.kotlin.reportsDestination=…` and diff the skippability report.
Expect `MediaItem`/`MediaCategory`/`EpgProgram` to move to stable and their composables to skippable.
Then scroll-test to confirm nothing regressed.
**Commit:** `perf(compose): make core:player domain types stable`

### Step 6 — Per-item card styling · §C4 · low risk, wide
Hoist `CardDefaults.colors/scale/glow` and the `Color.copy` calls out of the item bodies: EPG cells
(`EpgGridLayout.kt:509-542`, `:360-385`), `StreamList.kt:353-374`, TV `EpisodeCard`.

**Check:** scroll the EPG grid and a large category. `Janky frames` % and 90th percentile from
`gfxinfo`, reset between runs.
**Commit:** `perf(tv): hoist per-item card styling out of composition`

### Step 7 — EPG grid · §C2, C3, C1 · medium risk
Memoize the `is24HourFormat`/formatting path (`TimeFormat`), scope the 60s tick so it doesn't
invalidate the whole grid, then — separately, and only if the first two aren't enough — give each
row its own `LazyListState` instead of sharing one.

**Check:** scroll the guide horizontally and vertically; watch jank and confirm rows still scroll in
sync. C1 is the one item here that can visibly break sync — treat it as its own commit if attempted.
**Commit:** `perf(epg): memoize cell time formatting, scope the minute tick`

### Step 8 — Category entry cost · §B2, B4 · medium risk
Move the `toDomain` mapping and `ScriptDetector` filtering off Main. Keep stale content on reload
instead of flashing to a spinner.

**Check:** time Home → a large category (the 9,479-stream one) before and after. StrictMode is
already wired debug-only; add timing around the mapping passes.
**Commit:** `perf(browse): map catalogue rows off the main thread`

### Step 9 — Thumbnails and backdrop · §D4, B6 · medium risk
Stop `CinemaThumbnail` issuing an unsized request before it knows its size. Hoist the backdrop's
`RenderEffect`/`ColorMatrix` out of the `graphicsLayer` block.

**Check:** count image requests during a scroll (Coil logging or logcat); expect roughly half.
Confirm thumbnails still resolve at the right resolution — the failure mode is blurry or oversized.
**Commit:** `perf(images): request thumbnails once, hoist backdrop effects`

### Step 10 — Playback load path · §A2, A3, A4 · high risk
Last on purpose. Decouple first frame from the EPG and watch-history reads; cache resolved URLs;
revisit the `player.stop()` teardown and the 600ms debounce.

**Check:** wall-clock key-press → `STATE_READY`, plus a long zapping session watching for the
regressions this risks — overlapping decoder output, stale metadata, wrong channel after a fast
zap burst.
**Commit:** one per sub-item, not one for the step.

---

**Not scheduled:** §A5 (cold-start `awaitInstance`) — real, but startup is already ~2s better this
session and it's tangled with service lifecycle. §D5/§D6 (vestigial `scaled()`, `composed {}`
long-press) — wide, low-value; do opportunistically when touching those files anyway.

**Natural stopping point: after step 4.** Steps 1–4 are all low-risk and cover both bugs, the live
first-frame gate, both sources of permanent per-frame work, and the navigation feel. Measure there
before deciding whether 5–10 earn their risk.

## Context

You asked what would make the app feel more pleasant — no lag in transitions, faster video start
and switching. You named **video start/switching**, **moving between screens**, and **the EPG
grid**, said darcy's disabled animations are leftover rather than deliberate, and asked for
findings only.

Everything below is read from the current tree at file:line. **This is source analysis, not
runtime profiling** — the ranking is my judgement of felt impact, not measured frame data. Where I
independently re-verified a claim I say so; treat the rest as high-confidence but unmeasured.

Nothing here re-proposes work already done. Confirmed in place and deliberately excluded: the
marquee-on-focus fix in `StreamList`, D-pad focus off the snapshot system, EPG channel-map
scoping, the Room detail cache, hoisted gradient `Brush` in `CategoryList`, and the favorites /
watch-history caching in `MediaRepository` (`:659-693`) — the AGENTS.md "#1 hotspot" is **already
fixed**.

**Scale that makes this matter:** 230,310 streams, 45,128 series, 1,639 categories, 122 MB Xtream
DB. Largest single Live TV category: **9,479 streams**.

---

# A. Video start and switching

### A1. Live TV runs on the VOD buffer profile — double the first-frame gate ✅*verified*

`StreamingPlaybackService.kt:313` defaults to `ContentType.VOD`. `setContentType` has exactly two
call sites — `TvPlayerScreen.kt:139`, `MobilePlayerScreen.kt:321`.

`LiveTvSplitLayout.kt` is the Live TV path actually used on TV (`TvCategoryGridScreen.kt:178`), and
by design it promotes the preview in place rather than navigating to `Screen.Player`. So it **never
calls `setContentType`**, and live channels play on `WIFI_VOD`:

| | WIFI_LIVE | WIFI_VOD (what live actually gets) |
|---|---|---|
| playback gate → first frame | **500 ms** | **1 000 ms** |
| back buffer | 0 | **10 000 ms** |

`NetworkBufferProfile.kt:18-29`. The live profile's own comment reads "optimized for fast start" —
it simply isn't reached on this path. Every zap pays double the gate and retains 10 s of back
buffer intended for VOD seeking. Smallest change here, largest payoff.

### A2. First frame is gated on data the decoder doesn't need

`StreamLoaderViewModel.loadStreamInternal` (`:129-301`) is strictly sequential, and
`_state.value = Success` (`:249`) — the sole trigger for `playStream` — publishes only after all
of it:

1. `resolvePlayableStream` `:147` — for Xtream this is a Room read + **pure string concat**
   (`XtreamApiService.kt:324-361`), no network
2. `getPlaybackPositionSuspend` `:161`
3. `isFavoriteSuspend` `:180`
4. Live TV: `getEpgBulkForItems` `:196` — can fall through to a **live network call**
   (`MediaRepository.kt:290-292`) on index miss
5. VOD: `getSeriesDetail` / `getMovieDetail` `:226`/`:237`

Steps 2–5 are not needed to start decoding. On Jellyfin, 2 and 3 are each a separate sequential
HTTP round trip (`MediaRepository.kt:939-941`, `:1026-1027`). Plus
`getWatchHistoryForContentTypeSuspend` unconditionally on **every** full switch (`:331`).

### A3. Every switch tears down the decoder and drops all buffer

`StreamingPlaybackService.kt:496` — `player.stop()` before `setMediaSource` + `prepare()`. Full
codec teardown, zero buffer carryover. The comment justifies it (overlapping decoder output), but
the seamless-recycle path at `:190-192` deliberately does *not* stop — a cheaper pattern already
exists in the same file.

No resolved-URL cache anywhere: `loadJob?.cancel()` + full re-resolve per switch (`:306`, `:347`).
`evictConnectionPool()` (`NetworkModule.kt:61-63`) wipes **all** pooled sockets app-wide on
recycle, forcing fresh TCP/TLS handshakes.

### A4. 600 ms debounce before any resolution work begins

`LiveTvSplitLayout.kt:150-158` — `collectLatest` + `delay(600)`, restarted by each D-pad move.
Then A2's chain, then A3's teardown, then A1's 1 000 ms gate. D-pad auto-repeat coalesces at 300 ms
(`PlayerEffects.kt:142-150`) then applies the delta in an **unspaced `repeat()` burst** of full
loads.

### A5. Cold-start first play serializes behind service `onCreate`

`awaitInstance()` (`PlaybackViewModel.kt:134`) blocks the first `playStream` on the entire service
`onCreate` — `NetworkMonitor.init`, `FfmpegLibrary.isAvailable()`, track-selector and `MediaSession`
build, wake-lock acquire.

---

# B. Moving between screens

### B1. TV declares no nav transitions — inherits a 700 ms crossfade ✅*verified*

`TvNavHost.kt` declares **zero** of the four transition params (grep count: 0). navigation-compose
2.8.5 then applies its default `fadeIn/fadeOut(tween(700))`. Every TV screen change is a 700 ms
crossfade with **both screens composed and drawn simultaneously**.

Mobile declares all four at 300 ms (`MobileNavHost.kt:153-176`).

Compounding: Live TV pushes **two** `CategoryList` entries back-to-back (`TvNavHost.kt:183-190`) —
one tap, two overlapping 700 ms crossfades.

**Currently masked on darcy** by `animator_duration_scale = 0.0`. Since that's leftover, restoring
it to 1 will make this immediately visible on both Shields.

### B2. Selecting a category maps thousands of objects on the main thread

`CategoryViewModel` launches everything from `viewModelScope` = `Dispatchers.Main.immediate`. The
I/O is wrapped, but post-processing lands back on Main:

- `XtreamMediaProvider.getItems:103` — `result.data.map { it.toDomain(mediaType) }`. Each
  `toDomain` (`XtreamMapper.kt:32-56`) allocates a `MediaItem` + a `buildMap` + a `MediaMetadata`.
  For a several-thousand-stream category that's **thousands of objects on Main at the moment you
  pick a category** — and it's a *second* mapping pass, since rows were already mapped to
  `XtreamStream` on IO (`XtreamContentManager.kt:223`). This is likely the dominant cost of slow
  category entry.
- `getFilteredCategories` (`MediaRepository.kt:208-220`) filters 869 categories through
  `ScriptDetector.detectScript` (`ScriptDetector.kt:23-38`) — a `mutableMapOf` allocation plus
  `Character.UnicodeBlock.of()` lookups **per category, on the UI thread, at screen entry**.
- `rebuildVirtualCategories` (`:524-570`) ends in a full copy of the 869-element list, on Main, on
  **every favourite toggle** (`:489`, `:505`).
- Two `SharedPreferences.getBoolean` reads per state emission, in release too — every
  `UiState.Success` calls `getCategoriesPayloadSize()`/`getPayloadSize()` (`:215, 263, 281,
  585-586, 608-609`), each starting with an `isDevMode` check.

### B3. `refreshPerItemData` fans out into four separate recompositions

`CategoryViewModel:426-475` is correctly off Main (`Dispatchers.Default`), but publishes into
**four** `MutableStateFlow`s (`:437, 441, 472, 473`), each collected separately at the screen root
and re-wrapped (`TvCategoryGridScreen.kt:99-102`). One refresh can trigger up to four screen-wide
recompositions, each handing every visible row new wrapper instances. It also makes three full
passes over the stream list plus a `HashSet` the size of the category (`MediaRepository.kt:830`).

### B4. Category grid flashes to a spinner on every reload ✅*verified*

`loadCategoriesInternal` sets `UiState.Loading` (`:167`) with no stale-content retention, so any
re-load blanks the grid instead of updating in place.

### B5. Detail screens serialize four round trips; series always hit network

`MovieDetailsViewModel.kt:49-80` emits `Success` only after four sequential suspend calls —
including `getFilteredCategories("MOVIES")` plus a `firstOrNull` scan purely to resolve a category
*display name* (`:73-78`). `SeriesDetailsViewModel.kt:57-59` is the same shape.

Movies have a 7-day Room detail cache. **Series don't** — `XtreamMediaProvider.kt:122-131` always
hits Xtream for the episode list, plus optional TMDB enrichment, before Success.

TV `EpisodeSelectionScreen.kt:151-152` drops the whole list to a full-screen spinner on refresh;
mobile retains `lastSuccess` (`:89-94`) and shows a pull-spinner. **Mobile's behaviour is the one
worth copying to TV.**

### B6. `AmbientBackdrop` allocates a 140 px blur in the draw phase ✅*verified*

`tv/.../AmbientBackdrop.kt:116-131` (mobile `:75-90`) — a fresh `ColorMatrix`,
`ColorMatrixColorFilter` and two `RenderEffect`s are built **inside the `graphicsLayer` lambda**, a
draw/layer-update block rather than composition. It renders a full-screen 140 px blur behind every
browse screen (`TvCategoryGridScreen.kt:167, 201, 239`), and on detail screens the same layer also
gets per-frame parallax (`MovieDetailsScreen.kt:214`).

---

# C. EPG grid

### C1. One `LazyListState` shared across N+1 lazy lists

`EpgGridLayout.kt:147` — `horizontalScrollState` is passed to the header `LazyRow` (`:421`) **and**
to every visible channel row's `LazyRow` (`:467`, via `ProgramRow` at `:212`). A `LazyListState` is
designed to back a single list; here every visible row registers against one instance. Combined
with a nested `LazyRow` per row inside the outer `TvLazyColumn` (`:177-220`, `:466-485`), each
channel row is its own lazy list with its own measure/layout/recycling.

### C2. `DateFormat.is24HourFormat(context)` per cell, per composition

`EpgGridLayout.kt:552` → `TimeFormat.kt:23-33`: `Instant.ofEpochSecond` + `ZoneId.systemDefault()`
+ `is24HourFormat(context)` + `format(...)`, none of it remembered. Same at `:444` per time-header
slot, and **twice** per search result (`:704-707`). Mobile hits the same path
(`MobileEpgTimeline.kt:253`, `:290`).

### C3. One 60-second tick recomposes the entire visible grid

`rememberNowEpochSeconds()` (`:105`) is threaded to every `ProgramRow` (`:211`) and `ProgramCell`
(`:481`). The shared ticker is itself a good design — it replaced per-cell
`System.currentTimeMillis()` — but every tick now invalidates the whole grid.

### C4. Per-cell card styling allocated on every recomposition ✅*verified*

`EpgGridLayout.kt:509-542` (and `:360-385`) construct `CardDefaults.colors(...)`,
`CardDefaults.scale(...)`, `CardDefaults.glow(...)` inline, including `Color.copy(alpha = …)`
calls — a `CardColors` + `CardScale` + `CardGlow` + 2 `Color` **per visible cell, per
recomposition**. Exactly the pattern AGENTS.md's own journal warns about, and the same class of fix
as the already-done hoisted `Brush`. Not EPG-specific: the same shape appears in `StreamList.kt:353-374`
and the TV `EpisodeCard`.

`EpgViewModel` also does its post-processing on Main (`:73, 80`): sorting listings for up to 50
channels (`:129-132`), `buildChannelRows` (`:223-250`), and 48 `ZonedDateTime` computations in
`generateTimeSlots` (`:252-268`) — on load, day-change and refresh.

---

# D. Systemic — affects everything above

### D1. `core:player` has no Compose compiler plugin, so every domain type is unstable

`core/player/build.gradle.kts:1-4` applies only `android.library` + `kotlin.serialization`. Every
type crossing into composition is therefore inferred **unstable**: `MediaItem` (which also carries
a raw `Map<String,String>` at `MediaItem.kt:35`), `MediaCategory`, `MediaMetadata`, `EpgProgram`,
`EpgChannelRow`, `TimeSlot`, `PlaybackState`.

Strong skipping (on by default at Kotlin 2.3) is currently what keeps rows skippable at all. There
is **no stability-configuration file** in the repo. This is the single highest-leverage item here —
one config change improves skipping across every list in the app, and makes the `ImmutableWrappers`
pattern less load-bearing.

Raw unstable types are still passed directly in places the wrappers were meant to cover:
`MobileCategoryListScreen.kt:935/938/1056`, `LiveTvSplitLayout.kt:622`, `EpgGridLayout.kt:84/85/96`,
and `CategoryViewModel` itself is threaded through `StreamList.kt:86`, `CategoryList.kt:81`,
`TwoColumnLayout.kt:55`. The mobile module never uses `ImmutableCategoryList`/`ImmutableMediaList`
at all.

### D2. An unremembered wrapper defeats the stability work in the hottest path

`LiveTvSplitLayout.kt:508` — `streams = ImmutableMediaList(displayedStreams)`. The inner list *is*
remembered (`:495-506`); the wrapper is not. So `StreamList`'s `streams` parameter gets a new
identity on **every recomposition**, which re-runs `remember(streams) { mutableSetOf() }`
(`StreamList.kt:134`), which re-arms `staggeredEntrance` for every visible row, which re-arms a
per-frame `invalidateMeasurement()` loop (`StaggeredEntrance.kt:55-73`). Line `:392-394` in the
same file *is* correctly remembered — so this is an oversight, not a convention.

### D3. Marquee is focus-gated in one place and unconditional everywhere else

The `perf(live-tv)` commit gated it in `StreamItem` (`StreamList.kt:426`, `:445`). It was **not**
applied to:
- `CategoryList.kt:361` — unconditional on every category row
- `MobileCategoryListScreen.kt:988, 1032, 1290, 1316` — unconditional; touch has no focus concept

`BounceMarqueeNode` re-measures the child at up to **5× container width** every measure pass
(`BounceMarquee.kt:99-103`) and runs a `withFrameNanos` + `invalidateDraw()` loop **indefinitely**
for any overflowing text (`:168-182`). IPTV category names overflow constantly.

### D4. Every thumbnail issues two image requests on first appearance

`CinemaThumbnail.kt:85-95` — the `ImageRequest` applies `size(...)` only once `measuredSize` is
non-zero (`:91-93`). On first appearance that's zero, so **request #1 goes out unsized (full source
resolution)**, layout then writes the size, recomposition issues **request #2, sized**. Two
requests plus a recomposition per thumbnail, during exactly the scroll where it hurts.

Also: no global placeholder — instead every not-yet-loaded thumbnail runs its own
`rememberInfiniteTransition` shimmer (`:197-236`), all simultaneously. Coil is otherwise well
configured (25% heap memory cache, 512 MB disk, shared OkHttp — `FijerenaApplication.kt:51-68`).

### D5. `scaled()` is a no-op called hundreds of times per frame ✅*verified*

`UiScale.kt:18-33` — all four overloads `return this`. This is **intentional and correct**: scaling
moved to `LocalDensity` in `MainActivity`, and the comment says so. But the call sites remained, so
every `remember(scale, typography) { style.copy(fontSize = …scaled(scale)) }` block across the list
item trees allocates a `TextStyle` copy identical to its source, and `LocalUiScale` is
`compositionLocalOf` (dynamic, not `static` — `:13`), so every item subscribes to it. Dead weight
rather than a bug, but it's everywhere.

### D6. `composed {}` in the per-row long-press modifier

`StreamList.kt:345` `.tvLongPress(...)` → `CategoryGridUtils.kt:20-49`, a `Modifier.composed { }`,
applied to **every row**. `BounceMarquee.kt:30` and `StaggeredEntrance.kt:26-29` both explicitly
document avoiding `composed {}` for exactly this reason. Same again at `SearchScreen.kt:997`.

---

# E. Two real bugs found on the way

### E1. `CategoryList`'s refresh spinner can never stop ✅*verified*

`CategoryList.kt:125-134`:
```kotlin
LaunchedEffect(Unit) {
    snapshotFlow { categoriesRefreshing }.collect { refreshing ->
        if (refreshing) { while (true) { targetRotation = …; delay(…) } }
    }
}
```
Keyed on `Unit`, and the collector body never returns once `refreshing` is true — so `collect` can
never observe it going `false`. After the first refresh it writes `targetRotation` forever, keeping
`animateFloatAsState` producing frames for the lifetime of the screen. The sibling
`StreamList.kt:101` is keyed on `LaunchedEffect(streamsLoading)` and **is** cancelled correctly —
so the correct version already exists one file over.

### E2. Composition-time set mutation drops the entrance animation

`StreamList.kt:289` — `if (enteredStreamIds.add(item.id))` inside the item lambda. Two effects:
the set grows to full category size (thousands of entries), and `add` returns `false` on the first
*recomposition* of that same item, so `staggeredEntrance` is dropped from the modifier chain,
detaching the node and cancelling the animation mid-flight. Same pattern at
`MobileCategoryListScreen.kt:958, 993, 1038, 1078, 1166` and `CategoryList.kt:154, 263-267`.

Adjacent: `MobileCategoryListScreen.kt:1014` calls `categoryViewModel.isFavoriteCategory(...)`
**inside** the `LazyRow` item lambda, per category, per recomposition, over 869 categories. TV
avoids this by passing a precomputed `favoriteCategoryIds` set (`CategoryList.kt:82, 258`) — mobile
never adopted that flow. Also `MobileCategoryListScreen.kt:460` does an O(n) `find` over 869
categories inside `TopAppBar(actions = …)`.

---

# What's already good — worth protecting

- Player and surface are reused across switches, never recreated (`movableContentOf` +
  `EmbeddedPlayerSurface`); preview→full-screen promotion shares one service connection.
- No `runBlocking` or `Thread.sleep` anywhere in `src/main`.
- Search is properly tuned: grouping hoisted out of the lazy list, `key` + `contentType` on every
  block, `doSearch` on IO, O(N) bucketed sorting.
- `loadNowPlaying` is bounded (`take(50)`) and batches its emissions.
- Mobile's EPG timeline is better hoisted than TV's grid.
- Coil's cache configuration is sensible.
- The focus-debounce in `LiveTvSplitLayout` is deliberately recomposition-free, with the reasoning
  documented.

---

# If you only act on five

1. **A1** — the `setContentType` gap. One missing call; halves the live first-frame gate.
2. **D1** — add the Compose compiler plugin (or a stability config) to `core:player`. One config
   change, improves skipping in every list.
3. **B1** — declare TV nav transitions. A 700 ms library default is almost certainly not your
   choice; restore darcy's `animator_duration_scale` first or you can't see it.
4. **E1 + D3** — the never-terminating spinner loop, and finishing the marquee focus-gate in the
   two places the earlier commit missed. Both are small, and both remove permanent per-frame work.
5. **A2** — stop gating first frame on EPG and watch-history. The decoder needs a URL; the rest can
   land after playback starts.

# How to verify any of it

On darcy with `animator_duration_scale` restored to 1:

- **Buffer profile (A1):** `adb logcat | grep AdaptiveLoadControl` to confirm which profile the
  split path selects; time OK→first-frame from `screenrecord` frame counts.
- **Transitions (B1), backdrop (B6), marquee (D3):** `dumpsys gfxinfo org.njarasoa.fijerena` before
  and after; watch `Janky frames` and the 90th percentile. `--reset` between runs.
- **Load chain (A2–A4):** timestamped logs at each step of `loadStreamInternal`, then wall-clock
  from key-press to `STATE_READY`.
- **Stability (D1):** build with
  `-Pandroidx.compose.compiler.plugins.kotlin.reportsDestination=…` and diff the skippability
  report before/after.
- **Main-thread work (B2):** StrictMode is already wired debug-only
  (`FijerenaApplication.kt:28-37`); add `detectCustomSlowCalls` timing around the mapping passes.

# Caveats

- Source analysis, not profiling. Ranking is judgement. A1, B1, D1, E1 are the ones I'd stake most
  confidence on — a wrong default, a missing parameter, a missing plugin, and a loop that provably
  cannot exit.
- I have not measured how much of B2's mapping cost is actually felt versus hidden behind the
  700 ms crossfade in B1 — fixing B1 may well *expose* B2.
- `plans/UX_FLOW_AUDIT.md` is untouched; it covers navigation *friction* (IA, discoverability) and
  is largely resolved. This is the orthogonal axis: responsiveness.
