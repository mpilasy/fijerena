# UI/UX Polish, Transitions & Flow Uplift Plan

**Status:** In Progress — Phases 1-5 done (2026-09-23). Reprioritized 2026-09-23 — unified into one findings list, scored, and resequenced into ROI-ordered phases. No more "initial" vs "additional findings" split; every item below (originally Phases 1-6 plus the aggressive-audit items 8a-8d) lives in one list and one phase order.

## 1. Scoring Method

Every item below is scored 1 (low) – 3 (high) on three axes:

- **Complexity (C):** how much code moves — files touched, new components, new system-API integration.
- **Risk (R):** chance of a regression, a violated constraint, or landing in code already known to be fragile.
- **Reward (V):** how much a real user actually feels it, and how often.

**Priority = V − avg(C, R)** (higher = better ROI). Used only to order phases, not as a hard gate — a couple of items are pulled forward or held back from strict score order because they share a screen or a fragile function with a neighbor (noted inline).

| # | Item | C | R | V | Priority | Phase |
|---|---|---|---|---|---|---|
| 1c | Centralized easing tokens | 1 | 1 | — | prereq | 1 (Done) |
| 8a | Provider empty-state CTA | 1 | 1 | 3 | 2.0 | 1 (Done) |
| 8b | TV loading spinner | 1 | 1 | 2 | 1.0 | 1 (Done) |
| 5a | Debounced mobile search | 1 | 1 | 2 | 1.0 | 1 (Done) |
| 5b | Search scope filter chips | 1 | 1 | 2 | 1.0 | 1 (Done) |
| 6b | Haptic micro-interactions | 1 | 1 | 1 | 0.0 | 1 (Done) |
| 1a | Mobile player vertical transition | 1 | 1 | 2 | 1.0 | 2 |
| 1b | State crossfades (Loading/Success/Error) | 2 | 1 | 2 | 0.5 | 2 |
| 2b | Seamless channel switch (mobile) | 2 | 2 | 3 | 1.0 | 2 |
| 4a | "Jump Back In" shelf (TV & Mobile) | 3 | 2 | 3 | 0.5 | 3 |
| 3a | 16:9 backdrop hero | 2 | 1 | 3 | 1.5 | 4 |
| 3b | Action bar & meta line redesign | 2 | 1 | 2 | 0.5 | 4 |
| 3c | Tabbed detail sections | 3 | 2 | 3 | 0.5 | 4 |
| 8c | Provider row overflow menu | 2 | 2 | 2 | 0.0 | 5 |
| 8d | TV Settings grouping | 2 | 1 | 2 | 0.5 | 5 |
| 2a | Double-tap 10s seek | 2 | 2 | 3 | 1.0 | 6 |
| 6a | Mobile EPG Now/Next mode | 3 | 1 | 2 | 0.5 | 6 |
| 2c | VOD brightness/volume swipe | 3 | 3 | 2 | −1.0 | 6 (Stretch) |

### Sequencing Rationale & Locality Overrides

- **Phase 2 unites Motion & Surface Polish (1a, 1b, 2b):** Rather than scattering transitions across disparate phases, 1a (player slide up/down), 1b (content crossfades), and 2b (seamless channel switch freeze-frame) form a focused, low-to-medium risk transition pass immediately consuming Phase 1's easing tokens.
- **Phase 3 isolates "Jump Back In" (4a):** 4a is high-value but cross-layer (Room DAO with series collapsing, thumbnail rehydration from Xtream DB, TV D-pad shelf, Mobile touch shelf, and NavHost routing). Separating it into its own phase prevents conflating data/shelf architecture with screen transitions.
- **Phase 4 keeps Mobile Detail Screens together (3a, 3b, 3c):** Both screens (`MobileMovieDetailsScreen`, `MobileEpisodeSelectionScreen`) are modernized in natural sequence: backdrop hero, action controls, and segmented tabs.
- **Phase 5 prioritizes TV Ergonomics (8c, 8d):** As a TV-first media player, TV provider action clutter and settings list navigation are addressed before introducing experimental touch gestures.
- **Phase 6 groups Gestures & Alternate Views (2a, 6a, 2c):** Double-tap seek (2a) lands cleanly after player lifecycle and transitions have fully settled. 2c (brightness/volume swipe) has negative ROI (-1.0) and high touch-arbitration risk, so it is decoupled from 2a and marked as an optional stretch item.

---

## 2. Architectural & Design Constraints (Strict)

All items adhere strictly to the project rules and prior architectural decisions:
1. **No Hardcoded UI Values:** All colors, paddings, corners, elevations, and durations must use design tokens from [`CinemaColors`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/src/main/java/org/njarasoa/fijerena/core/ui/theme/CinemaColors.kt), [`CinemaSpacing`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/src/main/java/org/njarasoa/fijerena/core/ui/theme/CinemaSpacing.kt), [`CinemaCornerRadius`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/src/main/java/org/njarasoa/fijerena/core/ui/theme/CinemaCornerRadius.kt), [`CinemaAnimation`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/src/main/java/org/njarasoa/fijerena/core/ui/theme/CinemaAnimation.kt), [`TvDimensions`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/ui/theme/Dimensions.kt), and [`MobileDimensions`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/ui/theme/Dimensions.kt).
2. **Standing Constraint - Row Lists (No Poster Grid):** Keep the two-column and list-row browsing layouts (`[[feedback_no_poster_grid]]`). No grid conversion on list screens.
3. **Standing Constraint - No Horizontal-Card UI Theme:** Home content type selection maintains its established layout structure.
4. **TV D-Pad & Focus Navigation:** TV elements must remain 100% D-pad navigable with explicit focus styling (1.0 -> 1.1 scale on 200ms tween, 2dp accent border, 8dp glow). Avoid heavy animations that drop frames on mid-range TV chipsets (e.g. Sony Bravia).
5. **Live TV Preview / Dock Back-Stack:** Preserve the rule that Back always has a real stopover before exiting Live TV (TV: two pushed back-stack entries; Mobile: docked mini-player state stopover).
6. **TV Back on Detail Screens:** Intercept Back in `onPreviewKeyEvent` on root `LazyColumn` for TV detail screens to avoid focus clearance swallows.
7. **Coding Conventions:** Single return statement per function, zero breaks/continues/early returns, and continuous verification with `./gradlew assembleDebug`.

---

## Phase 1 — Free Wins (Prereq + 5 items, all C1/R1) — ✅ **DONE (2026-09-23)**

Cheapest tier in the whole plan: one file each, isolated, no cross-item dependency except 1c being a prerequisite token addition the later phases' motion work can reference. Ship this phase as one batch or as five trivial standalone PRs — either works, nothing here blocks anything else.

### 1c. Centralized Easing Tokens in `CinemaAnimation` (prerequisite) — ✅ **DONE** (commit `7ddc3607`)
- **Problem:** Currently, animations use default `tween(ms)` without explicit easing curves, resulting in stiff linear or semi-abrupt motion.
- **Solution:** Add centralized Compose easing curves to [`CinemaAnimation.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/src/main/java/org/njarasoa/fijerena/core/ui/theme/CinemaAnimation.kt):
  - `StandardEasing = FastOutSlowInEasing` (for screen slides, crossfades, and drawer reveals)
  - `EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)` (for focus scale and player overlay popups)
- **Landed:** Tokens added exactly as scoped. No consumers wired yet — Phase 2/3's motion items are what will actually reference these.

### 8a. Provider List's First Screen Has No Call-to-Action for a Brand-New User — ✅ **DONE** (commit `baf4216b`)
- **Problem:** [`TvProviderSelectionScreen`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/provider/ProviderSelectionScreen.kt#L135-L141) and [`ProviderSelectionScreen` (mobile)](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/provider/ProviderSelectionScreen.kt#L96-L107) both render `ProviderUiState.NoProviders` as nothing but centered, static body text ("No providers"). This is the literal first screen a brand-new install shows once past the splash — there is no button, arrow, or visual cue pointing at the header's "+" icon (itself icon-only, no visible label) that's the only way to actually do anything. A first-time user has no obvious next step.
- **Solution:**
  - Replace the bare text with a proper empty state: icon/illustration, the existing message as a subtitle, and a real "Add Provider" button (`CinemaButton`/`CinemaSecondaryButton`) wired to the same `onAddProvider` callback the header icon already uses.
  - On TV, make that button D-pad focusable and auto-focused on first composition of this state, so a new user can press the D-pad center key immediately without hunting for the header icon.
- **Landed:** Fixed on both platforms as scoped — icon, subtitle, `CinemaButton` wired to the existing `onAddProvider` callback, auto-focused on TV via the same `FocusRequester` + `LaunchedEffect` pattern used for F-27's empty-state fix earlier. Icon reuses `CinemaIcons.Add` (same glyph as the action it triggers) rather than sourcing a new illustration asset.

### 8b. TV Loading State Has No Visual Feedback, and Is Inconsistent with Mobile's — ✅ **DONE** (commit `baf4216b`)
- **Problem:** [`TvProviderSelectionScreen`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/provider/ProviderSelectionScreen.kt#L128-L134) renders `ProviderUiState.Loading` as plain static text ("Loading..."), with no spinner, skeleton, or animation of any kind. [`ProviderSelectionScreen` (mobile)](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/provider/ProviderSelectionScreen.kt#L88-L94) already does this correctly — a centered `CircularProgressIndicator()`. On a screen that can be reached mid-load (e.g. right after adding/editing a provider, per the screen's own `LaunchedEffect(Unit) { viewModel.loadProviders() }` at line 82), a static loading string with zero motion reads as a frozen/broken screen on TV specifically.
- **Solution:** Mirror mobile's `CircularProgressIndicator()` (or the app's TV-specific `MitohanaLoading` loader already used on the player's loading screen, for visual consistency with the rest of the TV app) in the TV `Loading` branch.
- **Landed:** Used `CircularProgressIndicator()`, not `MitohanaLoading` — checked `MitohanaLoading`'s actual implementation and it's an animated-dots *text* string ("Buffering..." + cycling dots), semantically built for player-buffering context, not a generic spinner. Using it here would show "Buffering..." on a provider list, which is wrong. Mobile's plain spinner was the better fit despite the "for visual consistency" framing in the original proposal.

### 5a. Debounced As-You-Type Search on Mobile — ✅ **DONE** (commit `6b1e8682`)
- **Problem:** In [`MobileSearchScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/search/SearchScreen.kt#L170-L218), search only triggers when the user presses the keyboard Search IME button. Typing does not filter results automatically.
- **Solution:**
  - Add a 300ms debounced `LaunchedEffect(searchQuery)` in `MobileSearchScreen`:
    - When `searchQuery.length >= 2`, automatically invoke `viewModel.performSearch(searchQuery)`.
    - When cleared, immediately invoke `viewModel.clearSearch()`.
  - Show a small animated indeterminate progress circle inside the search field trailing icon while search coroutines run.
- **Landed:** Fixed as scoped. Debounce duration uses a new `CinemaAnimation.searchDebounceMs` token, not a raw `300` literal, per the project's own no-hardcoded-durations rule. Verified `performSearch()` already cancels its prior job before starting a new one, so the debounced trigger can't race the existing manual ones (search-icon tap, keyboard search action) — confirmed in `SearchViewModel.kt` before relying on it.

### 5b. Content-Type Scope Filter Chips in Global Search — ✅ **DONE** (commit `6b1e8682`)
- **Problem:** In Global Search (`Screen.Search("ALL")`), 200 mixed results across Live TV, Movies, and TV Shows are grouped into long vertical collapsible sections.
- **Solution:**
  - Add quick filter chips right below the search bar: `All`, `Live TV (N)`, `Movies (N)`, `TV Shows (N)`.
  - Selecting a chip instantly filters the visible result set in memory without database re-querying, making result triage fast and effortless.
- **Landed:** Used `CinemaFilterChip` (a selectable chip with a `selected: Boolean` state) rather than `CinemaAssistChip` — better semantic fit for a scope filter than a one-off action chip. Filters the already-computed `buildGroupedSearchResults()` output client-side, exactly as scoped. Chips only render when there's more than one content type actually present in the results — a type-scoped search never needed triaging to begin with.

### 6b. Haptic Micro-Interactions on Mobile (scrubber + toggles only — see note) — ✅ **DONE** (commit `ab4d9634`)
- **Problem:** Mobile touch interactions (scrubbing video slider, long-press actions, chip toggles) lack tactile feedback.
- **Solution:**
  - Integrate `LocalHapticFeedback.current.performHapticFeedback()`:
    - `HapticFeedbackType.TextHandleMove`: on video scrubber seek snaps / seconds intervals.
    - `HapticFeedbackType.LongPress`: on favorite/watched toggle long-press.
- **Note:** The double-tap-10s-seek haptic trigger from the original proposal is deferred to Phase 4 alongside 2a — that gesture doesn't exist yet, so its haptic can't be wired here. Add `HapticFeedbackType.LongPress` on seek activation as part of 2a's own PR instead of a separate follow-up.
- **Landed:** Checked `MobileControlsOverlay.kt` for a "watched" toggle to wire alongside favorite, per the original text — there isn't one; only a favorite toggle exists in the player controls (a watched toggle, if it exists at all, lives on list/detail screens, out of a player-interactions item's scope). Scrubber haptic fires once per second boundary crossed during drag (Slider's `onValueChange` fires continuously, so a naive per-callback trigger would buzz constantly) — tracked via a `lastHapticSecond` state var, not a debounce.

---

## Phase 2 — Motion & Surface Polish (NavHost, State Crossfades & Seamless Switch) — ✅ **DONE (2026-09-23)**

A cohesive transition and visual continuity pass across navigation and playback surfaces, directly consuming Phase 1's easing curves.

### 1a. Mobile Player Expansion & Dismissal Transition — ✅ **DONE** (commit `e6fd9434`)
- **Problem:** In [`MobileNavHost.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/navigation/MobileNavHost.kt#L155-L178), `Screen.Player` enters with a flat horizontal slide (`slideIntoContainer(Left)`). Opening full-screen video feels like pushing an ordinary list item, and exiting slides sideways unnaturally.
- **Solution:** Configure a specialized transition spec for `composable<Screen.Player>` in `MobileNavHost.kt`:
  - **Enter:** `slideIntoContainer(SlideDirection.Up, animationSpec = tween(CinemaAnimation.navTransitionMs, easing = FastOutSlowInEasing)) + fadeIn(tween(CinemaAnimation.navTransitionMs))`
  - **Exit / Pop Exit:** `slideOutOfContainer(SlideDirection.Down, animationSpec = tween(CinemaAnimation.navTransitionMs, easing = FastOutSlowInEasing)) + fadeOut(tween(CinemaAnimation.navTransitionMs))`
  - Other screens retain directional left/right lateral transitions with smooth easing curves.
- **Landed:** Fixed as scoped via per-destination `enterTransition`/`exitTransition`/`popEnterTransition`/`popExitTransition` on `composable<Screen.Player>` — other destinations keep the NavHost-wide lateral transitions untouched.

### 1b. State Crossfades for Loading / Content / Error — ✅ **DONE** (commit `67197117`)
- **Problem:** In [`TvCategoryGridScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/category/TvCategoryGridScreen.kt#L174-L256) and [`MobileCategoryListScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/category/MobileCategoryListScreen.kt#L570-L585), transitions between `Loading`, `Success`, and `Error`, as well as category switching, swap views instantly using a raw `when (uiState)` without animation. This creates jarring content pops and skeleton flashes.
- **Solution:**
  - Wrap top-level state branches in `AnimatedContent` or `Crossfade(targetState = uiState, animationSpec = tween(CinemaAnimation.navTransitionMs))`.
  - For stream list reloading within `CategoryViewModel.UiState.Success`, animate the transition between `streamsLoading` (skeleton rows) and the populated stream list using a 200ms crossfade, preventing harsh list replacement.
- **Landed:** Scope narrowed on landing — only the top-level `Loading`/`Success`/`Error` crossfade shipped, via `AnimatedContent(targetState = uiState, contentKey = { it::class }, ...)` on both `TvCategoryGridScreen` and `MobileCategoryListScreen`. `contentKey` on the sealed subtype (not the state instance) is what makes this safe: `Success` carries fresh data on nearly every emission (stream list updates, `streamsLoading` toggling, the docked Live TV preview target changing), and a plain `targetState` comparison would've refired the crossfade on every one of those instead of only on real Loading/Success/Error swaps — would've been constant flicker, including inside TV's `LiveTvSplitLayout`'s promoted full-screen player. The second half — a 200ms crossfade between `StreamList`'s skeleton and populated-list branches — was intentionally **not** done: that component owns D-pad auto-scroll/auto-focus (`LaunchedEffect(streams, streamsLoading, lastPlayedItemId)`, per-item `FocusRequester`s) and wrapping it in `AnimatedContent` would keep both the outgoing skeleton and incoming list composed and requesting focus simultaneously during the fade window — exactly the "heavy animation on TV focus-sensitive code" risk this plan's own constraints (§2.4) warn against. Left as a follow-up if wanted, not silently dropped.

### 2b. Seamless Channel Switch on Mobile (Eliminate Black Screen Flash) — ✅ **DONE** (commit `16d3088c`)
- **Problem:** In [`MobilePlayerScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/player/MobilePlayerScreen.kt#L440-L450), swiping vertically on Live TV triggers `streamState = Loading`, which immediately replaces `PlayerContent` with `LoadingScreen()`. This destroys the `SurfaceView` and flashes a full black screen on every channel change.
- **Solution:**
  - Port TV's seamless solution from [`TvPlayerScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/player/TvPlayerScreen.kt#L261-L269) to Mobile: track `lastSuccessState` in `MobilePlayerScreen`.
  - When `streamState is StreamLoaderViewModel.StreamState.Loading`, if `lastSuccessState != null`, keep `PlayerContent` mounted with the frozen last frame and display the `ChannelToast` / loading indicator over the video until the new stream starts.
- **Note:** Porting the `lastSuccessState` pattern from TV should not port TV's bug along with it. `TvPlayerScreen.kt`'s original version of this pattern fed a frozen `resumePosition` (captured once, at initial load) into a resume/restart call — fixed in commit `cd8c6b49` by tracking the *live* position from `setPositionSaveListener` instead, since `recordHistory()` writes fresh positions to the DB but never back into `loaderViewModel`'s own state. If this phase's mobile port ever needs to re-trigger playback with a resume position (not just keep the frozen last frame visible during the Loading gap), use the same live-position-tracking approach, not TV's original one.
- **Landed:** Implemented via a `displayState` indirection rather than porting TV's `PlayerContent()` extraction: `lastSuccessState` tracks the same way as TV, but instead of splitting Mobile's ~300-line inline `Success` branch (dense with local `mutableState` closures — toasts, overlays, gesture state) into a standalone function, the final `when` that picks what to draw now switches on `displayState` (falls back to `lastSuccessState` only while `streamState is Loading`), while every effect that actually drives playback (`LaunchedEffect(currentStreamId)`, the position-save listener, `enrichedState`) still keys off the live `streamState` untouched — so TV's original resume-position bug was never in the code path to port in the first place. Added a small `CircularProgressIndicator`, independent of the existing ExoPlayer-buffering overlay, visible only while `streamState is Loading` — covers the stream-resolve gap before `playStream()` is even called, which the buffering overlay doesn't reach.

---

## Phase 3 — Home Screen Quick-Resume / Jump Back In — ✅ **DONE (2026-09-23)**

A high-value, cross-layer feature providing 1-click/tap resume capability right on the app's landing screen.

### 4a. "Jump Back In" Shelf on Home (TV & Mobile) — ✅ **DONE** (commits `8fe88265`, `15140bfc`, `cce06ce0`)
- **Problem:** In [`ContentTypeSelectionScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/contentselection/ContentTypeSelectionScreen.kt), returning users must make 4–5 manual clicks/taps through content categories to resume whatever media they were watching previously.
- **Architecture & Data Pipeline:**
  - `MediaRepository.getWatchHistory()` as originally conceived returned unbounded rows without series collapsing or thumbnail artwork.
  - **Database Queries (`WatchStateDao`):**
    - `getResumable(providerId, ContentType.MOVIES, limit)`: retrieves incomplete movie watch states ordered by `lastPlayedAt DESC`.
    - `getResumableSeriesCollapsed(providerId, ContentType.TV_SHOWS, limit)`: uses SQLite window function `ROW_NUMBER() OVER (PARTITION BY COALESCE(seriesId, itemId) ...)` so multiple in-progress episodes of the same show collapse to a single card for that show.
  - **Thumbnail Rehydration:** `watch_state` stores IDs, positions, and titles, but no artwork. Rehydrate posters and covers via `XtreamDatabase` (`streamDao().getIconsByIds` for movies; `seriesDao().getCoversByIds` for series) or server metadata (Jellyfin).
  - **Domain Model:** Dedicated `@Immutable data class ContinueWatchingItem(...)` in `core:player/domain/` with `id`, `name`, `subtitle`, `contentType`, `categoryId`, `thumbnailUrl`, `progress` (0.02..0.95), `playbackPositionMs`, `durationMs`, and `target: BrowseTarget`.
  - **Repository Method:** `MediaRepository.getContinueWatchingItems(limit = 10)`: fetches in-progress movies and series, rehydrates thumbnails, and merges sorted descending by recency.
- **UI & Interaction:**
  - **TV (`TvContinueWatchingShelf`):**
    - Displayed below hero cards when in-progress items exist.
    - Horizontal `TvLazyRow` with TV focus tokens (1.0 -> 1.1 scale on 200ms tween, `CinemaAccentLight` border, 8dp glow).
    - Card displays 16:9 thumbnail via `CinemaThumbnail`, title (>=18sp), subtitle/episode cue, and bottom progress bar (`CinemaAccent`).
    - D-pad Down from hero cards lands directly on the shelf; D-pad Up returns to hero cards.
  - **Mobile (`MobileContinueWatchingShelf`):**
    - Displayed prominently in `MobileContentTypeSelectionScreen` above content cards.
    - Horizontal `LazyRow` with touch-friendly 16:9 cards, progress bar, title, and episode/time subtitle.
  - **Navigation Routing:**
    - Selecting an item routes via `TvNavHost` and `MobileNavHost`:
      - `BrowseTarget.Movie`: navigates directly to `Screen.Player` at saved position (1 click to play!).
      - `BrowseTarget.Series`: navigates to `Screen.EpisodeSelection(seriesId, ..., initialEpisodeId)` with the in-progress episode preselected in the detail panel.
      - `BrowseTarget.Episode`: navigates directly to `Screen.Player`.
  - **Lifecycle Refresh:** Refresh shelf items on `Lifecycle.Event.ON_RESUME` so returning from playback immediately reflects updated progress.
- **Landed:** Data pipeline, both shelves, and lifecycle refresh built as scoped, with four deliberate deviations from this text, each checked against real code before landing:
  - **Resumable band enforced in SQL, not just by name.** `getResumable`/`getResumableSeriesCollapsed` filter `isCompleted = 0 AND durationMs > 0 AND (positionMs * 100.0 / durationMs) BETWEEN 2.0 AND 95.0` — the same 2%-95% band `WatchedItem.resumeProgress()` already uses everywhere else progress is computed, done in SQL so the `LIMIT` lands on the right rows instead of over-fetching. `getResumableSeriesCollapsed` applies that filter *before* the `ROW_NUMBER()` partition: a series whose most-recently-played episode was already finished has nothing to resume and correctly drops off the shelf, even though it would still show in the plain Recent row.
  - **Thumbnail rehydration reuses `MediaRepository`'s existing private `rehydrateThumbnails()`** (already used by the per-content-type Recent row) rather than a new standalone lookup — same `streamDao().getIconsByIds`/`seriesDao().getCoversByIds` source, zero new DB-access code. Jellyfin/server-backed providers are explicitly out of scope (`getContinueWatchingItems` returns empty under `usesServerUserData`) — this phase was scoped to Xtream/local storage from the start, and the server user-data endpoints don't expose raw position/duration, only a completion fraction, so this shelf can't be built the same way for them.
  - **`ContinueWatchingItem` carries `remainingMs`, not `playbackPositionMs`/`durationMs` separately** — the card only ever needs the remaining-time label, computed once in the repository (`durationMs - positionMs`) rather than re-derived per card render. `subtitle` is the episode's own stored title (`watch_state.itemName`) for a TV Shows card, null for a Movie card — not a "S1E4"-style label, since `watch_state` has no season/episode-number columns; adding one would mean joining `XtreamEpisodeDao` per shelf item for a first cut nothing else in the app does either (the existing per-content-type Recent row shows no episode cue at all).
  - **Navigation routing matches the existing `BrowseTarget` dispatch used everywhere else** (`CategoryList`'s Recent row, Search's results), not the "`BrowseTarget.Movie` → `Screen.Player` directly" text above: a movie card opens `Screen.MovieDetails`, same as every other resumable movie entry in the app. The original text would have made the *new* shelf the only place in the app where a resumable movie skips the details screen — an inconsistency the plan's own text didn't intend, caught by checking against `TvNavHost`/`MobileNavHost`'s real dispatch code before wiring the new callback.
  - Explicit D-pad Down/Up interception between the hero row and the shelf was *not* added — Compose TV's default 2D focus search already moves focus vertically between two focusable rows with nothing unusual in between, and adding `onPreviewKeyEvent` interception for a case the default already handles would be unnecessary speculative complexity. Revisit only if real-device testing shows the default behavior isn't good enough.

---

## Phase 4 — Mobile Detail Screens Modernization (Parity with TV Hero) — ✅ **DONE (2026-09-23)**

Same two screens end to end (`MobileMovieDetailsScreen`, `MobileEpisodeSelectionScreen`) — build in this order (hero → actions → tabs) rather than splitting across phases.

### 3a. Cinematic 16:9 Backdrop Hero Banner — ✅ **DONE** (commit `caa899df`)
- **Problem:** In [`MobileMovieDetailsScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/movie/MovieDetailsScreen.kt#L202-L230) and [`MobileEpisodeSelectionScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/episode/EpisodeSelectionScreen.kt#L456-L486), 2:3 vertical posters are forced into horizontal banners (`fillMaxWidth().height(posterHeightLarge)`), causing severe image cropping. Meanwhile, [`MovieDetailsViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/MovieDetailsViewModel.kt#L69-L70) already fetches 16:9 TMDB landscape `backdropUrl`s, which mobile leaves unused.
- **Solution:**
  - Collect `backdropUrl` in `MobileMovieDetailsScreen` and `MobileEpisodeSelectionScreen`.
  - Render a true 16:9 aspect-ratio header using `backdropUrl` (falling back to blurred/gradient cover art if null).
  - Apply top-to-bottom and bottom-to-top gradient scrims, overlaying the TMDB title wordmark logo ([`TitleLogoOrText`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/src/main/java/org/njarasoa/fijerena/core/ui/components/TitleLogoOrText.kt)) gracefully on the backdrop.
- **Landed:** Fixed as scoped via a new shared `MobileDetailHero` composable, used by both screens (they had byte-for-byte identical hero blocks — this reads as a net code reduction, not just a swap). One scrim brush does both directions (dark→transparent by 25%, transparent→dark from 60%) rather than two stacked overlays. "Falling back to blurred/gradient cover art if null" landed as *unblurred* poster fallback — `Modifier.blur()` needs API 31 (this app's `minSdk` is 30) and nothing else in the codebase uses it; the poster was already being center-cropped to a wide banner before this item, so falling back to that same crop (now at a true 16:9 ratio) is a strict improvement with zero new API surface, not a regression.

### 3b. Action Bar & Meta Line Redesign — ✅ **DONE** (commit `92d0e654`)
- **Problem:** Action buttons on mobile detail screens are currently a mix of stacked full-width buttons and scattered icon toggles.
- **Solution:**
  - Modernize action controls into a single cohesive row:
    - Primary wide pill: "Play" / "Resume from XXm" (`CinemaButton`).
    - Secondary circular/rounded icon buttons: Favorite toggle, Watched toggle, Trailer, and Info.
  - Consolidate metadata into a single dot-separated meta row: Year · Content Rating badge · Duration · "Ends at" time · Resolution badge.
- **Landed:** Scope narrowed to the meta-line half, applied to all three places carrying one of these rows (movie header, series header, per-episode detail panel) — checked `CinemaIconButton`/`DetailIconAction` before touching the action row and found they're already a `FilledIconButton` in `CircleShape` with a label underneath: the "primary pill + circular secondary icons" the Problem/Solution text asks for already existed, not "stacked full-width buttons and scattered icon toggles". Two further deliberate deviations from the text, not silently dropped:
  - No "Info" icon added — its likely purpose (surfacing technical stream info without cluttering the header) is what 3c's Overview tab now does instead; adding a redundant icon action would fight the phase that lands right after it in the same commit sequence.
  - "Start from Beginning" (an existing, working resume-restart action, shown whenever `hasResume`) was kept even though it isn't in the plan's 4-icon list — removing a working feature to match an illustrative list isn't a real improvement, and nothing in the Problem statement calls it out as broken.
  - New shared `MetaText`/`MetaBadge` composables (`ui/components/MobileDetailMeta.kt`) so all three meta rows build the dot-joined line the same way; star rating stays its own `RatingBadge` pill (matching TV's `TvDetailHero`, which keeps its score chip outside the plain-text meta line too), content rating and resolution are `MetaBadge` pills, everything else is plain `MetaText`.

### 3c. Tabbed / Segmented Detail Sections — ✅ **DONE** (commit `063b4535`)
- **Problem:** On Mobile, synopsis, cast, alternate streams, and related titles are all dumped into a continuous vertical scroll, causing excessive scrolling distance.
- **Solution:**
  - Introduce a sticky segmented tab strip below the hero actions:
    - **Movie Details:** `Overview` (synopsis, director, studio), `Cast` (actors), `More Like This` (related titles), `Versions` (alternate streams).
    - **Series Details:** `Episodes` (season dropdown + episode cards), `Overview`, `Cast`, `More Like This`.
  - Prevents scrolling fatigue and keeps episode picking instantly accessible.
- **Landed:** Found TV already shipped this exact pattern (`docs/plans/20260902_tv-detail-hero-ui-plan.md` Phase 4 — `MovieDetailTab`/`SeriesDetailTab` in the TV screens, tabs built from what the title actually has, not a fixed list) and mirrored its structure and naming for mobile rather than designing from scratch, with three differences from both the TV precedent and this plan's literal text:
  - **Not sticky-pinned during scroll.** TV's tab row is a `stickyHeader`; making mobile's the same would mean restructuring the whole `LazyColumn` item layout (currently one large `item(key="detail")` block holding everything above the tabs) into multiple items so a sticky header has something to pin against — a materially bigger, riskier change than this item's C2 score budgeted for. A plain inline `TabRow` still delivers the core ask (only one tab's content composes/scrolls at a time, cutting total scroll length) without that restructuring risk. Worth revisiting as a follow-up if a pinned strip turns out to matter in practice.
  - **"Versions" is its own tab (mobile only)**, unlike TV which folds the alternate-stream picker into its "Details" tab. Followed the plan's literal mobile spec here since it explicitly asks for a dedicated tab; the trade-off is that the plain "Stream name: X" line (previously always visible, even with zero alternates) now only shows when there's actually something to switch to — the Versions tab doesn't exist otherwise. A minor information trim, not a lost capability.
  - **"Studio" was dropped from Overview** — `MediaMetadata` (the shared domain model every provider metadata flows through) has no studio field, so there was nothing to show.
  - Series-only fix caught before landing: the horizontal swipe-to-change-season gesture was originally attached to the whole `LazyColumn` unconditionally. Since the season list/episode items only compose while the `Episodes` tab is selected, an un-gated swipe while viewing Overview/Cast/More Like This would silently change the season selection in the background — invisible until switching back to Episodes. Gated the gesture's `pointerInput` on `selectedTab == SeriesDetailTab.EPISODES` before committing.

---

## Phase 5 — TV Provider & Settings Management (Ergonomics & Navigation) — ✅ **DONE (2026-09-23)**

Focus on Android TV usability, decluttering dense action rows and organizing settings navigation.

### 8c. Provider Row Crams up to 6 Icon-Only Action Buttons with No Labels — ✅ **DONE** (commit `3a696ed0`)
- **Problem:** [`ProviderList`'s row content, TV](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/provider/ProviderSelectionScreen.kt#L322-L389) renders, per provider, up to six adjacent `CinemaIconButton`s — Select (conditional), Manage EPG (conditional), Duplicate, Copy To (conditional), Edit, Delete — each distinguished only by a small icon, no visible text label, packed into one `Row` with `Spacing.xs` between them. On a D-pad, that's up to six small, visually-similar adjacent focus targets per row with no label to read before committing to a press — a user has to know the icon vocabulary (checkmark = select, pencil = edit, etc.) or focus each one and read the content-description off... nothing, since content descriptions aren't rendered visually, only exposed to accessibility services.
- **Solution:**
  - Collapse the secondary actions (Duplicate, Copy To, Edit, Delete) behind a single "more actions" overflow button that opens a focusable menu/dialog with real text labels — mirrors the existing [`FavoriteMenuDialog`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/category/components/FavoriteMenuDialog.kt) pattern already used elsewhere in the TV app for the same "several actions on one item" shape.
  - Keep only the primary action (Select, or nothing if already active) as a direct one-press button on the row itself.
- **Landed:** Fixed as scoped, with one addition beyond the text: Manage EPG stays a direct row button too, alongside Select — the Problem/Solution text's own icon list already separates it from the four collapsed into the menu ("Duplicate, Copy To, Edit, Delete"), and it's a single-tap-and-done action like Select, not an occasional maintenance one like the four that moved. New `ProviderActionsMenuDialog` (`feature/provider/components/ProviderDialogs.kt`) reuses `TvInputListItem` rows exactly like `FavoriteContextMenuDialog` does, including its `initialFocus` on a trailing Cancel row. Added a `MoreVert` entry to `CinemaIcons` (outlined/rounded/sharp trio, same as every other icon there) — nothing in the icon set covered an overflow glyph before this.

### 8d. TV Settings Is One Long, Flat, Ungrouped List — ✅ **DONE** (commit `59ed0204`)
- **Problem:** [`SettingsScreen` (TV)](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/settings/SettingsScreen.kt#L193-L330) is a single `TvLazyColumn` with ten sequential `item { }` cards — Provider, Playback, Theme, Language, EPG, UI Scale, Developer, Cloud Sync, Export/Import, About — with no section headers, grouping, or side navigation. Each card is itself expandable/interactive (own internal focusable controls), so reaching "About" or "Export/Import" from the top means holding D-pad-down through nine other cards' worth of focus stops first. There's no fast path to a specific settings area.
- **Solution (cheap option, do this one):**
  - Group related cards under section headers (e.g. "Provider & Playback", "Appearance", "Data & Sync", "Advanced") so the list has visual waypoints, even without restructuring the underlying cards. Complexity/Risk stay low (`C2 R1` above) because this doesn't touch any card's internals.
- **Stretch option (not scored/scheduled — separate proposal if wanted):** A persistent left-rail category selector for TV, jumping the `TvLazyColumn` to the relevant section. Meaningfully higher complexity (new persistent nav chrome specific to one screen) for an incremental improvement over section headers — only worth it if headers alone prove insufficient in practice.
- **Landed:** Fixed as scoped, using exactly the plan's own example group names. Doing this required more than adding headers, though: the ten cards' original order (Provider, Playback, Theme, Language, EPG, UI Scale, Developer, Cloud Sync, Export/Import, About) interleaves what the groups need — UI Scale sat after EPG, Developer sat before Cloud Sync/Export-Import — so cards had to be reordered into contiguous runs (Provider+Playback / Theme+Language+UI Scale / EPG+Cloud Sync+Export-Import / Developer+About) before a header could sit in front of each group without splitting it. Card contents themselves are untouched, as scoped — only their order and the new header items between them changed. New private `SettingsSectionHeader` composable, plain non-focusable label text, no new focus-order concerns.

---

## Phase 6 — Player Gestures & EPG Alternate View

Delivering mobile player seek muscle memory and fast EPG feed browsing.

### 2a. Double-Tap Left/Right 10s Relative Seek (Replacing Double-Tap Pause)
- **Problem:** In [`MobilePlayerScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/player/MobilePlayerScreen.kt#L465-L473), double-tap toggles pause/resume. This duplicates single-tap + center button and breaks universal mobile video player conventions (YouTube, Netflix, Plex, MX Player).
- **Solution:**
  - Replace the double-tap handler with a horizontal screen-split calculation:
    - Tap on left 40% of the screen: relative seek `-10_000L` (-10s).
    - Tap on right 40% of the screen: relative seek `+10_000L` (+10s).
    - Center 20%: toggle controls overlay or ignore.
  - Implement a momentary seek ripple overlay: a circular translucent pill showing `⟲ 10s` (left) or `10s ⟳` (right) with animated chevrons, accumulating rapid taps (`20s`, `30s`) and automatically fading out after 600ms.
  - Single tap continues to cleanly toggle controls overlay visibility.
- **Note:** This removes documented behavior — `AGENTS.md` § Controls & Navigation currently states "Pause: Explicit via pause button, `KEYCODE_MEDIA_PLAY_PAUSE`, or mobile double-tap (VOD only)." Update that line in the same PR. Fold in `HapticFeedbackType.LongPress` on seek activation deferred from 6b (Phase 1).

### 6a. Mobile EPG "Now & Next" Fast-Browse Mode
- **Problem:** [`MobileEpgTimeline.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/epg/MobileEpgTimeline.kt#L201-L219) has independent un-synchronized horizontal `LazyRow`s per channel, making horizontal timeline alignment difficult on a phone screen.
- **Solution:**
  - Add an EPG display mode toggle: **Grid Timeline** vs **Now & Next List**.
  - "Now & Next List" renders a clean vertical feed: Channel Logo/Name on the left, Current Program (with time elapsed bar) and Up Next program on the right. Tapping tunes the channel immediately; tapping program opens description dialog.

### 2c. VOD Vertical Gestures for Brightness & Volume (Optional Stretch)
- **Problem:** In [`MobilePlayerScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/player/MobilePlayerScreen.kt#L477-L483), drag gestures are completely disabled on VOD (`!isLiveContent`).
- **Solution (Optional Stretch):**
  - Left half vertical drag: adjust screen window brightness (`Activity.window.attributes.screenBrightness`).
  - Right half vertical drag: adjust stream/media volume via `AudioManager`.
  - Display a temporary, elegant centered HUD pill (icon + vertical mini progress bar) showing current brightness/volume percentage that fades out 1s after touch release.
- **Note:** Lowest priority-score item in the whole plan (Complexity 3, Risk 3, Priority -1.0) due to potential touch-arbitration conflicts with Android system navigation gestures. Kept as an optional stretch item to be attempted only after 2a has fully validated.

---

## 7. Verification & Quality Assurance Strategy

1. **Static Analysis & Style Verification:**
   - Run `./gradlew ktlintCheck` and `./gradlew lintDebug` to verify adherence to style and single-return principles.
2. **Build Integrity:**
   - Execute `./gradlew assembleDebug` to compile both `:tv` and `:mobile` targets without warnings or errors.
3. **Hardware & Emulator Testing:**
   - **Android TV (Shield / Bravia):** Verify D-pad navigation, focus restoration, safe margins, and absence of frame drops.
   - **Android Mobile:** Verify touch gestures, double-tap seek accuracy, orientation changes, and smooth 60fps animations.
4. **Phase 6 specifically:** given the fragile-function history of `MobilePlayerScreen.kt`'s gesture code, verify 2a and 2c each individually — full playback session, every overlay open/closed combination — before considering Phase 6 done, not just a final combined pass.
