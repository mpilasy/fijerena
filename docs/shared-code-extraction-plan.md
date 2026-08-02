# Audit: duplicate logic in mobile/tv worth sharing

## Status (2026-08-02)
Tier 1 items A–E implemented and committed, except two small D items
(`epgDevStats` suffix, `parseEpgRefreshHour`) and two small E items (`epgDbStats`
derivation, `isBusy` derivation, `isOnAir`/`isSoon`/tap-action) — left as follow-up,
low value relative to effort. Tier 2 untouched. See git log for the commits.

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

## Tier 1 — pure-function extractions (do now)

### A. Time/duration formatting → `core/player`
Collapse two near-identical `PlayerUtils.kt` files
(`mobile/.../feature/player/utils/PlayerUtils.kt`,
`tv/.../ui/player/utils/PlayerUtils.kt`) plus 4 copies of the same helpers in
`MovieDetailsScreen.kt`/`EpisodeSelectionScreen.kt` (mobile + tv) into one
`core/player/model/TimeFormat.kt` (already exists, extend it):
`formatTime`/`formatMillis` (same function, two names), `formatEpochTime`,
`formatBitrate`, `parseDurationToSeconds`, `computeEndsAt`, `formatDuration`,
`channelLabel`, `resolutionLabel`. Delete both `PlayerUtils.kt` files once callers
point at core.

### B. `SeriesDetail`/episode-list state → `core/player/domain/SeriesDetail.kt`
Same file as `sortedSeasons()`. Extract from `EpisodeSelectionScreen.kt` (mobile + tv):
- `seasonNumberContaining(episodeId): Int?` (currently inline `resumeSeasonNumber` calc)
- `defaultExpandedSeason(resumeSeason, sortedSeasons): Set<Int>`
- `flattenedEpisodes(sortedSeasons): List<EpisodeItem>` (mobile has this as
  `flattenEpisodes()`; tv inlines the same algorithm — unify)
- pure part of "first season with unwatched episode" — takes a
  `Map<String, WatchProgress>` the caller already fetched; the suspend fetch itself
  stays in each screen's `LaunchedEffect`
- episode-scroll-index computation (walk seasons/episodes to find target offset)

### C. Search/category pure derivations
- `ContentType` display label (`getContentTypeLabel`, 2 copies) →
  `core/player/domain/ContentType.kt` as `.displayLabel()`
- Grouped-by-type builder (`groupedByType`, 2 copies) → `core/ui` near `SearchViewModel`
- `Set<String>.toggled(key)` helper (`toggleGroup`, 2 copies) → small core/ui util
- `VIRTUAL_CATEGORY_IDS` partition (2 copies) → core/ui near `CategoryViewModel`
- `FavoriteMenuTarget` → (name, isFavorite) mapping (4 copies) and
  `MediaItem.toFavoriteMenuTarget()` (tv already has this named; mobile inlines it
  twice) → both into `core/ui/model/FavoriteMenuTarget.kt` (the file created in the
  last pass)

### D. EPG pure derivations
- `EpgProgram.elapsedFraction(nowEpochSec)` (2 copies) →
  `core/player/model/EpgModels.kt`
- `epgDevStats`/dev-stats-suffix (2 copies), `intervalOptions` constant (2 copies),
  `parseEpgRefreshHour` (2 copies) → `core/network` near the EPG settings model or
  `core/ui`

### E. EPG browser pure derivations → `core/network` near `EpgBrowserAiring`/`EpgBrowserProgram`
This pair had the cleanest, largest duplication: `filterMatchedOnly` (23 lines),
`formatAiringTime`, `formatFileSize`, `formatCount`, `freshnessLabel`,
stats-row text builder, no-results message text, `epgDbStats` derivation,
`isBusy` (isRefreshing) derivation, `isOnAir`/`isSoon`/`isMatched` + tap-action
decision (`AiringStatus`/`AiringTapAction`) — all pure functions of
`EpgBrowserAiring`/`EpgFileManager.MultiSourceState`, all verbatim-identical between
`MobileEpgBrowserScreen.kt` and `TvEpgBrowserScreen.kt`.

**Verification for Tier 1:** `./gradlew :core:player:compileDebugKotlin :core:network:compileDebugKotlin :core:ui:compileDebugKotlin :mobile:compileDebugKotlin :tv:compileDebugKotlin`, then launch both emulators and smoke-check search, category list, EPG guide/browser, episode list, movie details (same manual pass done for the season fix).

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
