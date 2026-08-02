# Audit: duplicate logic in mobile/tv worth sharing

## Status (2026-08-02)
Tier 1 done and committed (`03db5e31`), except four small leftovers noted inline
below (marked TODO) — left as follow-up, low value relative to effort. Tier 2
untouched, still backlog. Verified via clean compile of every touched module
plus a live smoke test on both emulators (TV + mobile launch, no crashes).

## Context
Following the `sortedSeasons()` and `FavoriteMenuTarget` extractions, user asked for a
broader audit: any duplicate *code* (not just data models) between `mobile/` and `tv/`
that should move into a shared module. Ran 3 parallel Explore passes over
episode/movie/player screens, search/category/EPG screens, and viewmodels/utilities.

Finding: extensive copy-paste duplication, almost entirely **pure logic** (parsing,
formatting, filtering, state-derivation) with no platform-specific reason to differ.
Much of it is byte-for-byte identical, including comments. The existing
`SeriesDetail.sortedSeasons()` extraction is the right template — a plain-Kotlin
extension function living next to the model it operates on (`core/player`,
`core/network`, or `core/ui/model`).

## Scope decision
This is too much to land as one commit. Splitting into two tiers:

- **Tier 1 (do now):** pure functions with zero Compose/effect entanglement — safe,
  mechanical, low review risk. This is the bulk of the findings.
- **Tier 2 (backlog, not in this pass):** logic entangled with `LaunchedEffect`/suspend
  calls/Compose state, where extraction requires a small design decision (state
  machine class, composable hook, or shared suspend helper) rather than a pure move.
  Flagging these with a recommended shape but leaving them for a follow-up so Tier 1
  isn't held up by the harder cases.

Also flagging two **real bugs/divergences** found during the audit (not duplication —
already-diverged copies) and one stray file, for the user's awareness, not auto-fixed.

---

## Tier 1 — pure-function extractions (DONE, commit `03db5e31`)

### A. Time/duration formatting → `core/player` — DONE
Collapsed two near-identical `PlayerUtils.kt` files plus 4 copies of the same
helpers in `MovieDetailsScreen.kt`/`EpisodeSelectionScreen.kt` (mobile + tv) into
`core/player/model/PlaybackFormat.kt` (new file, sits next to `TimeFormat.kt`):
`formatTime`, `formatEpochTime`, `formatBitrate`, `parseDurationToSeconds`,
`computeEndsAt`, `formatDuration`, `channelLabel`, `resolutionLabel`. Both
`PlayerUtils.kt` files deleted.

### B. `SeriesDetail`/episode-list state → `core/player/domain/SeriesDetail.kt` — DONE
Added `seasonNumberContaining`, `defaultExpandedSeason`, `flattenedEpisodes`,
`firstSeasonWithUnwatchedEpisode`, `episodeScrollIndex`, all wired into both
`EpisodeSelectionScreen.kt` files. Fixed a latent inconsistency along the way: TV's
unwatched-season loop was re-sorting episodes inline instead of reusing the
pre-sorted map mobile already used — both now call the same function over the
same pre-sorted data.

### C. Search/category pure derivations — DONE
- `ContentType.asContentTypeLabel()` in `core/player/domain/ContentType.kt`
- `buildGroupedSearchResults`, `Set<String>.toggled()` in
  `core/ui/viewmodels/SearchViewModel.kt`
- `CategoryViewModel.VIRTUAL_CATEGORY_IDS` + `List<MediaCategory>.partitionVirtual()`
  in `core/ui/viewmodels/CategoryViewModel.kt`
- `FavoriteMenuTarget.nameAndFavoriteState()` and two `MediaItem.toFavoriteMenuTarget()`
  overloads (plain favorite-list check, and the category-ref-aware long-press variant
  that also unified mobile's category-list long-press with TV's `TwoColumnLayout`) in
  `core/ui/model/FavoriteMenuTarget.kt`

### D. EPG pure derivations — MOSTLY DONE
- DONE: `EpgProgram.elapsedFraction()` in `core/player/model/EpgModels.kt`;
  `EPG_REFRESH_INTERVAL_OPTIONS` constant in `core/network/AppSettings.kt`
- TODO (left as follow-up): `epgDevStats`/dev-stats-suffix (2 copies),
  `parseEpgRefreshHour` (2 copies) — low value, skipped this pass

### E. EPG browser pure derivations → `core/network/xmltv/EpgBrowserModels.kt` — MOSTLY DONE
DONE: `filterMatchedOnly`, `formatAiringTime`, `formatFileSize`, `formatCount`,
`freshnessLabel` (in `EpgBrowserModels.kt`), plus `EpgBrowserViewModel.UiState.Results
.statsLine()`/`.noResultsMessage()` (in `core/ui/viewmodels/EpgBrowserViewModel.kt`
— these needed the `Results` state class so landed there instead of
`EpgBrowserModels.kt`).
TODO (left as follow-up): `epgDbStats` derivation, `isBusy` (isRefreshing)
derivation, `isOnAir`/`isSoon`/`isMatched` + tap-action decision — lower value,
skipped this pass.

**Verification:** `./gradlew :core:player:compileDebugKotlin :core:network:compileDebugKotlin :core:ui:compileDebugKotlin :mobile:compileDebugKotlin :tv:compileDebugKotlin` — clean. Installed both debug APKs on `emulator-5554` (TV) and `emulator-5556` (mobile), launched, no crashes in logcat.

---

## Tier 2 — backlog (needs a design call, not done in this pass)

1. **Player "finalize session" helper.** TV already extracted player logic into
   `PlayerEffects.kt`/`PlayerScreenState.kt`/`PlayerKeyHandler.kt`; mobile didn't get
   the same treatment. `finalizeSession()` (position/duration extraction + audio/sub
   track index lookup + `stopPlayback()` call) exists once in mobile
   (`MobilePlayerScreen.kt:685-705`) but is hand-copied **3 times** in
   `TvPlayerScreen.kt` — and TV's 3 copies aren't even consistent with each other
   (the `onDispose` copy skips the audio/sub index lookup). Recommend: one shared
   `finalizeSession(playbackState, streamingService, loaderViewModel)` in
   `core/ui/viewmodels`, called identically from both platforms and all 3 TV call
   sites — fixes a latent inconsistency, not just duplication.
2. **Exhaustion-toast debounce.** ~28 lines of sliding-window rebuffer-count logic,
   identical in `MobilePlayerScreen.kt` and `tv/.../PlayerEffects.kt`. Needs a small
   state-machine class (e.g. `ExhaustionToastDebouncer`) so the algorithm can be
   pulled out from under the `LaunchedEffect`.
3. **Shared "now" ticker composable.** The same 60-second `LaunchedEffect` +
   `mutableLongStateOf` ticker is copy-pasted **4 times** (EPG guide ×2, EPG browser
   ×2). Recommend a `@Composable fun rememberNowEpochSeconds(): Long` hook in
   `core/ui`.
4. **Favorite-hint show/dismiss effect**, duplicated in
   `MobileCategoryListScreen.kt`/`TvCategoryGridScreen.kt` — same shape, recommend
   `@Composable fun rememberFavoriteHintVisible(context): Boolean` in `core/ui`.
5. **Provider connect/re-auth helper.** The exact guard fixed twice in commit
   `da6a6e82` (`if (!provider.isConnected()) provider.connect()`) is duplicated across
   4 screens (mobile+tv × movie+episode) plus a similar pattern in both nav hosts and
   `AppContainer.kt`. This is the highest long-term-value item — any future
   auth/connect fix will otherwise need to be repeated N times again — but touches
   session/connection code, so it deserves its own careful pass rather than folding
   into a mechanical-extraction commit.

## Also found, not part of this plan (flagging only)
- **Real bug/divergence:** `SettingsUtils.kt` (same path, both modules) defines
  `formatProgrammeCount` differently — mobile caps at "1.0k" past 1,000; tv handles
  millions with a capital "K"/"M" scheme matching the EPG browser's `formatCount`.
  These aren't a duplicate to merge, they're already-diverged and mobile looks stale.
  Separate small fix, not folded into Tier 1's mechanical moves.
- **Stray file:** `mobile/.../feature/player/MobilePlayerScreen.kt.rej` — leftover
  patch-reject file in the tree, unrelated to this audit, worth deleting separately.
- Two intentional platform differences that are *not* duplication and were correctly
  left alone: TV's D-pad long-press modifier (`tvLongPress`, TV-internal only) and
  TV's 5-minute-snapped time picker vs mobile's native `TimePicker`.
