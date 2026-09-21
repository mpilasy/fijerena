# Reduce Compose/navigation fragility (episode-selection bug class)

## Context

This session found and fixed two real bugs on the TV episode-selection screen
(`tv/src/main/java/org/njarasoa/fijerena/feature/episode/EpisodeSelectionScreen.kt`,
mirrored in `mobile/.../feature/episode/EpisodeSelectionScreen.kt`), both only
caught by a live device repro + logcat, not by review or the existing test
suite:

1. `hasManuallySelectedSeason` was a plain `remember` guarding a season
   auto-select effect, but the composable it lives in gets disposed every time
   the user navigates to the player and recomposed fresh on return — so the
   guard forgot the user's manual choice across exactly the trip where it
   mattered. Fixed by making it `rememberSaveable` (commits `4d0d5434`,
   `d220f1b0`).
2. A years-old, previously-harmless `requestFocus()` call on the hero Play
   button became a live bug the moment season tabs (with focus-follow-select)
   shipped nearby (`1288076c`, Sep 2) — nothing in the old line changed, but a
   new feature next to it changed its safety, and no test caught it because
   Compose UI orchestration (state survival across nav disposal, focus
   handoff) has **zero** test coverage in this codebase, despite 40 solid
   logic-level unit tests elsewhere. Fixed by focusing the resume card instead
   (commit `4d0d5434`).

Both bugs share one shape: locally-scoped, ad hoc mutable state + fire-and-
forget async writes, coordinated across multiple call sites by convention
rather than by structure, in the one interaction (navigate to player, come
back) that's genuinely hard to exercise by hand. The goal here isn't a
sweep — it's making *this specific, proven-fragile interaction* structurally
harder to break again, and leaving a regression test that would have caught
both bugs today.

## Approach

Three phases, each independently shippable, in priority order (highest
leverage / lowest risk first).

### Phase 1 — Regression test for the nav-disposal round trip

Compose UI test tooling is already wired in `tv/build.gradle.kts`
(`androidTestImplementation(libs.androidx.compose.ui.test.junit4)`,
`debugImplementation(libs.androidx.compose.ui.test.manifest)`) — just unused.
No new dependencies needed.

- Change `EpisodeListContent` (private in `EpisodeSelectionScreen.kt`) to
  `internal` so a same-module `androidTest` can call it directly with fake
  `SeriesDetail`/`MediaRepository`/callbacks — no ViewModel or DI needed. This
  is the one structural change required to make the screen testable at all.
- Construct the fake `MediaRepository` the same way the existing unit tests
  do: real `MediaRepository` instance, mockk'd DAOs (see
  `core/network/.../MediaRepositoryTest.kt` for the established pattern —
  `watchStateDao`/`episodeDao`/etc. are already constructor-injectable for
  exactly this reason, per the comment on `MediaRepository`'s constructor).
- New test file: `tv/src/androidTest/java/org/njarasoa/fijerena/feature/episode/EpisodeSelectionScreenTest.kt`.
  One test: render `EpisodeListContent` with a 2-season `SeriesDetail`, select
  season 2 and an episode (simulating the manual navigation), then force a
  fresh recomposition of the same composable with the same saved-state
  registry (the standard Compose test idiom: `setContent` again inside the
  same `composeTestRule`, or use `StateRestorationTester` from
  `androidx.compose.ui.test`, which exists precisely to simulate
  save/restore across disposal) — assert season 2 is still selected and the
  right episode still shows "Continue Watching". This is the exact scenario
  that broke twice; it now has a permanent guard.
- Mirror the same test for mobile's `EpisodeListContent` once the TV version
  is working — mobile's `build.gradle.kts` will need the same
  `androidTestImplementation`/`debugImplementation` Compose test entries TV
  already has (mobile currently only has plain Espresso/JUnit deps).

### Phase 2 — One state holder instead of three loose vars + a flag

Replace `resumeEpisodeId` / `selectedSeasonNumber` / `hasManuallySelectedSeason`
(three independent `rememberSaveable`/`remember` vars, written from 4 different
call sites: the anchor `LaunchedEffect`, `SeasonTabs`' `onSeasonSelected`, the
D-pad Left/Right handler, and the detail panel's `onPlay`) with one cohesive
state holder, mirroring the state-holder pattern this codebase already uses
for the player screen (`PlayerScreenState` /
`rememberPlayerScreenState(context, initialMetadata)` in
`tv/.../ui/player/PlayerScreenState.kt`) rather than inventing a new idiom.

- New small class (e.g. `EpisodeResumeState`) owning the three fields as
  private `mutableStateOf` with public read-only properties, plus the
  transitions as named methods instead of ad hoc assignments:
  `selectSeason(season, manual: Boolean)`, `setResumeEpisode(id, season)`,
  `applyAnchorIfNotManual(season)`. Every one of the 4 current write sites
  becomes a call to one of these — the "don't clobber a manual pick" rule
  lives in exactly one place instead of being re-implemented at each site.
- Persisted with a `listSaver`-backed `rememberSaveable(seriesDetail.id) { ... }`
  constructor function (`rememberEpisodeResumeState(...)`), so the object
  itself — not three separate primitives — is what survives disposal. This
  removes the failure mode from Phase 1's bug #1 by construction: there's no
  longer a second, independently-declared boolean that can drift out of sync
  with the saveable fields next to it.
- Apply to both `EpisodeSelectionScreen.kt` files (TV first, then mobile —
  same shape, per the "mirrors TV" comments already in the mobile file).

### Phase 3 — Await the position-save write before navigating back

`StreamLoaderViewModel.stopPlayback` (`core/ui/.../StreamLoaderViewModel.kt`)
launches its DB write on `Dispatchers.IO` via `viewModelScope.launch`,
unawaited. `finalizeSession` (top-level fun, same file) calls it synchronously
and returns immediately. The two call sites that matter are the explicit
**Back** paths — `TvPlayerScreen.kt`'s `PlayerContent.onBack` (~line 255) and
`MobilePlayerScreen.kt`'s wrapper `onBack` (~line 143) — both plain composable-
scope lambdas, both free to launch a coroutine before calling the nav
callback. (The `DisposableEffect { onDispose { finalizeSession(...) } }` call
sites — leaving the app entirely — must stay fire-and-forget; `onDispose` has
no coroutine scope that outlives it, and that's fine, they're not implicated
in either bug.)

- Extract `stopPlayback`'s body into a private `suspend fun`, keep the
  existing `stopPlayback(...)` (fire-and-forget, `viewModelScope.launch`)
  unchanged for existing callers, and add a `suspend fun
  stopPlaybackAwaited(...) = withContext(Dispatchers.IO) { ... }` sibling.
- Add `suspend fun finalizeSessionAndAwait(playbackState, loaderViewModel)`
  next to `finalizeSession`, same synchronous pos/dur/track computation, but
  calling `stopPlaybackAwaited` instead.
- At the two `onBack` call sites: `val scope = rememberCoroutineScope()`,
  wrap the existing three lines (`finalizeSession(...)`; `.stop()`;
  `onBack()`) in `scope.launch { finalizeSessionAndAwait(...); ...; onBack() }`.
  Mechanical, ~4 lines changed per file.

### Not in scope

No interface extraction for `MediaRepository`, no DI framework change, no
attempt to audit every other `remember` in the codebase for the same class of
bug — that's a review task, not a structural fix, and unbounded. This plan
targets the one interaction proven fragile twice in one session.

## Verification

- `./gradlew :tv:connectedDebugAndroidTest --tests "*EpisodeSelectionScreenTest*"`
  (emulator/device required, per this session's testing — `emulator-5556` is
  already set up and was used for every repro this session) — confirms the
  new test fails on the pre-Phase-2 code shape and passes after.
- After Phase 2, manually repeat this session's exact repro on
  `emulator-5556` (already documented as reliable): play a non-anchor episode
  from season ≠ 1, fast double-back, confirm season stays selected — same
  check already done for the current fix, now redundant with the automated
  test but worth one pass to confirm the refactor didn't reintroduce it.
- After Phase 3, confirm via `adb logcat` that no
  `IllegalStateException`/dropped-write warnings appear on repeated fast
  back-outs, and that watch history (`Continue Watching` position) is
  correct immediately after backing out with zero delay — the case Phase 3
  specifically targets.
- `./gradlew check` before considering any phase done (existing 40 unit tests
  must keep passing — nothing here touches `SeriesResumeAnchorTest` or the
  other domain-logic tests, but they're the cheapest regression check
  available).
