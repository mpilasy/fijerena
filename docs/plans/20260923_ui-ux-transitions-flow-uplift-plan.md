# UI/UX Polish, Transitions & Flow Uplift Plan

**Status:** In Progress — Phase 1 done (2026-09-23). Reprioritized 2026-09-23 — unified into one findings list, scored, and resequenced into ROI-ordered phases. No more "initial" vs "additional findings" split; every item below (originally Phases 1-6 plus the aggressive-audit items 8a-8d) lives in one list and one phase order.

## 1. Scoring Method

Every item below is scored 1 (low) – 3 (high) on three axes:

- **Complexity (C):** how much code moves — files touched, new components, new system-API integration.
- **Risk (R):** chance of a regression, a violated constraint, or landing in code already known to be fragile.
- **Reward (V):** how much a real user actually feels it, and how often.

**Priority = V − avg(C, R)** (higher = better ROI). Used only to order phases, not as a hard gate — a couple of items are pulled forward or held back from strict score order because they share a screen or a fragile function with a neighbor (noted inline).

| # | Item | C | R | V | Priority | Phase |
|---|---|---|---|---|---|---|
| 1c | Centralized easing tokens | 1 | 1 | — | prereq | 1 |
| 8a | Provider empty-state CTA | 1 | 1 | 3 | 2.0 | 1 |
| 8b | TV loading spinner | 1 | 1 | 2 | 1.0 | 1 |
| 5a | Debounced mobile search | 1 | 1 | 2 | 1.0 | 1 |
| 5b | Search scope filter chips | 1 | 1 | 2 | 1.0 | 1 |
| 6b | Haptic micro-interactions | 1 | 1 | 1 | 0.0 | 1 |
| 4a | "Jump Back In" shelf | 3 | 2 | 3 | 0.5 | 2 |
| 1a | Mobile player vertical transition | 1 | 1 | 2 | 1.0 | 2 |
| 1b | State crossfades (Loading/Success/Error) | 2 | 1 | 2 | 0.5 | 2 |
| 2b | Seamless channel switch (mobile) | 2 | 2 | 3 | 1.0 | 2 |
| 3a | 16:9 backdrop hero | 2 | 1 | 3 | 1.5 | 3 |
| 3b | Action bar & meta line redesign | 2 | 1 | 2 | 0.5 | 3 |
| 3c | Tabbed detail sections | 3 | 2 | 3 | 0.5 | 3 |
| 2a | Double-tap 10s seek | 2 | 2 | 3 | 1.0 | 4 |
| 2c | VOD brightness/volume swipe | 3 | 3 | 2 | −1.0 | 4 |
| 8c | Provider row overflow menu | 2 | 2 | 2 | 0.0 | 5 |
| 8d | TV Settings grouping | 2 | 1 | 2 | 0.5 | 5 |
| 6a | Mobile EPG Now/Next mode | 3 | 1 | 2 | 0.5 | 6 |

Two deliberate score-order overrides, both because of file/function locality, not because the math was wrong:

- **2b ships in Phase 2, 2a/2c wait for Phase 4** — all three touch `MobilePlayerScreen.kt`'s gesture code, but 2b's risk is about position-tracking correctness (same lesson as the `cd8c6b49` fix — track live position, not a frozen one), not gesture arbitration. 2a/2c *add new touch-gesture surface* to the same function already touched twice this session for gesture-conflict bugs (`ae1d5558`). Landing 2b alone first, verifying it, then coming back for 2a+2c as one coordinated pass avoids three separate risky edits to the same fragile block stacked on top of each other.
- **3a/3b/3c stay together in Phase 3** despite 3c's lower individual score — they're the same two screens (`MobileMovieDetailsScreen`, `MobileEpisodeSelectionScreen`), touched in a natural build order (hero, then actions, then tabs). Splitting them across phases would mean revisiting the same files three separate times for no benefit.

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

## Phase 2 — Home & Core Player Flow (highest-traffic screens)

The screens nearly every session touches: the home content-type picker and the player's basic open/close/switch motion. Higher value than Phase 1's items individually, but real code moves — not one-liners.

### 4a. "Jump Back In" Shelf on Home (TV & Mobile)
- **Problem:** In [`ContentTypeSelectionScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/contentselection/ContentTypeSelectionScreen.kt), returning users must make 4–5 manual clicks/taps through content categories to resume whatever media they were watching previously.
- **Verified (2026-09-23):** `MediaRepository.getWatchHistory(limit = 10)` as originally proposed does not exist and would not compile.
  - Actual signature: `suspend fun getWatchHistory(): List<WatchedItem>` — no `limit` parameter.
  - Backing query, [`WatchStateDao.getAll(providerId)`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/src/main/java/org/njarasoa/fijerena/core/network/xtream/db/WatchStateDao.kt#L231-L232): `SELECT * FROM watch_state WHERE providerId = :providerId` — unbounded, no `ORDER BY`, no completion filter. Its own comment: "Every row for this provider, all content types, unbounded."
  - This is most of why this item's Complexity is 3, not 1 — it needs a new DB-layer query first, not just a UI-layer call against something that already exists.
- **Solution (revised):**
  - Add `WatchStateDao.getRecentIncomplete(providerId: Long, limit: Int): List<WatchStateEntity>`:
    `SELECT * FROM watch_state WHERE providerId = :providerId AND isCompleted = 0 AND lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit`
    (`lastPlayedAt IS NOT NULL` excludes a manual watched/unwatched mark, which stores a null `lastPlayedAt` since it was never actually played — see `WatchStateEntity`'s kdoc.) Matches this DAO's existing precedent for this shape of query (`getLatestSeriesTrackPrefs` a few lines above already does `ORDER BY updatedAt DESC LIMIT 1`).
  - Add a `MediaRepository` wrapper (e.g. `getRecentIncompleteWatchHistory(limit: Int = 10): List<WatchedItem>`) mapping through the same `toWatchedItem()` used by `getWatchHistory()`.
  - Call that from `ContentTypeSelectionScreen` instead of the nonexistent overload.
  - When history exists, display a sleek "Jump Back In" horizontal row above or below the content type cards.
  - Each item displays: backdrop/thumbnail, title, progress bar with elapsed percentage, and "Resume" cue.
  - Tapping an item navigates directly to the player (or episode selection for series) with resume position intact.
  - Fully D-pad focusable on TV with smooth focus scaling.

### 1a. Mobile Player Expansion & Dismissal Transition
- **Problem:** In [`MobileNavHost.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/navigation/MobileNavHost.kt#L155-L178), `Screen.Player` enters with a flat horizontal slide (`slideIntoContainer(Left)`). Opening full-screen video feels like pushing an ordinary list item, and exiting slides sideways unnaturally.
- **Solution:** Configure a specialized transition spec for `composable<Screen.Player>` in `MobileNavHost.kt`:
  - **Enter:** `slideIntoContainer(SlideDirection.Up, animationSpec = tween(CinemaAnimation.navTransitionMs, easing = FastOutSlowInEasing)) + fadeIn(tween(CinemaAnimation.navTransitionMs))`
  - **Exit / Pop Exit:** `slideOutOfContainer(SlideDirection.Down, animationSpec = tween(CinemaAnimation.navTransitionMs, easing = FastOutSlowInEasing)) + fadeOut(tween(CinemaAnimation.navTransitionMs))`
  - Other screens retain directional left/right lateral transitions with smooth easing curves.

### 1b. State Crossfades for Loading / Content / Error
- **Problem:** In [`TvCategoryGridScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/category/TvCategoryGridScreen.kt#L174-L256) and [`MobileCategoryListScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/category/MobileCategoryListScreen.kt#L570-L585), transitions between `Loading`, `Success`, and `Error`, as well as category switching, swap views instantly using a raw `when (uiState)` without animation. This creates jarring content pops and skeleton flashes.
- **Solution:**
  - Wrap top-level state branches in `AnimatedContent` or `Crossfade(targetState = uiState, animationSpec = tween(CinemaAnimation.navTransitionMs))`.
  - For stream list reloading within `CategoryViewModel.UiState.Success`, animate the transition between `streamsLoading` (skeleton rows) and the populated stream list using a 200ms crossfade, preventing harsh list replacement.

### 2b. Seamless Channel Switch on Mobile (Eliminate Black Screen Flash)
- **Problem:** In [`MobilePlayerScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/player/MobilePlayerScreen.kt#L440-L450), swiping vertically on Live TV triggers `streamState = Loading`, which immediately replaces `PlayerContent` with `LoadingScreen()`. This destroys the `SurfaceView` and flashes a full black screen on every channel change.
- **Solution:**
  - Port TV's seamless solution from [`TvPlayerScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/player/TvPlayerScreen.kt#L261-L269) to Mobile: track `lastSuccessState` in `MobilePlayerScreen`.
  - When `streamState is StreamLoaderViewModel.StreamState.Loading`, if `lastSuccessState != null`, keep `PlayerContent` mounted with the frozen last frame and display the `ChannelToast` / loading indicator over the video until the new stream starts.
- **Note:** Porting the `lastSuccessState` pattern from TV should not port TV's bug along with it. `TvPlayerScreen.kt`'s original version of this pattern fed a frozen `resumePosition` (captured once, at initial load) into a resume/restart call — fixed in commit `cd8c6b49` by tracking the *live* position from `setPositionSaveListener` instead, since `recordHistory()` writes fresh positions to the DB but never back into `loaderViewModel`'s own state. If this phase's mobile port ever needs to re-trigger playback with a resume position (not just keep the frozen last frame visible during the Loading gap), use the same live-position-tracking approach, not TV's original one.

---

## Phase 3 — Mobile Detail Screens Modernization (Parity with TV Hero)

Same two screens end to end (`MobileMovieDetailsScreen`, `MobileEpisodeSelectionScreen`) — build in this order (hero → actions → tabs) rather than splitting across phases.

### 3a. Cinematic 16:9 Backdrop Hero Banner
- **Problem:** In [`MobileMovieDetailsScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/movie/MovieDetailsScreen.kt#L202-L230) and [`MobileEpisodeSelectionScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/episode/EpisodeSelectionScreen.kt#L456-L486), 2:3 vertical posters are forced into horizontal banners (`fillMaxWidth().height(posterHeightLarge)`), causing severe image cropping. Meanwhile, [`MovieDetailsViewModel.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/src/main/java/org/njarasoa/fijerena/core/ui/viewmodels/MovieDetailsViewModel.kt#L69-L70) already fetches 16:9 TMDB landscape `backdropUrl`s, which mobile leaves unused.
- **Solution:**
  - Collect `backdropUrl` in `MobileMovieDetailsScreen` and `MobileEpisodeSelectionScreen`.
  - Render a true 16:9 aspect-ratio header using `backdropUrl` (falling back to blurred/gradient cover art if null).
  - Apply top-to-bottom and bottom-to-top gradient scrims, overlaying the TMDB title wordmark logo ([`TitleLogoOrText`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/src/main/java/org/njarasoa/fijerena/core/ui/components/TitleLogoOrText.kt)) gracefully on the backdrop.

### 3b. Action Bar & Meta Line Redesign
- **Problem:** Action buttons on mobile detail screens are currently a mix of stacked full-width buttons and scattered icon toggles.
- **Solution:**
  - Modernize action controls into a single cohesive row:
    - Primary wide pill: "Play" / "Resume from XXm" (`CinemaButton`).
    - Secondary circular/rounded icon buttons: Favorite toggle, Watched toggle, Trailer, and Info.
  - Consolidate metadata into a single dot-separated meta row: Year · Content Rating badge · Duration · "Ends at" time · Resolution badge.

### 3c. Tabbed / Segmented Detail Sections
- **Problem:** On Mobile, synopsis, cast, alternate streams, and related titles are all dumped into a continuous vertical scroll, causing excessive scrolling distance.
- **Solution:**
  - Introduce a sticky segmented tab strip below the hero actions:
    - **Movie Details:** `Overview` (synopsis, director, studio), `Cast` (actors), `More Like This` (related titles), `Versions` (alternate streams).
    - **Series Details:** `Episodes` (season dropdown + episode cards), `Overview`, `Cast`, `More Like This`.
  - Prevents scrolling fatigue and keeps episode picking instantly accessible.

---

## Phase 4 — Mobile Player Gesture Expansion (new touch surface, land as one coordinated pass)

Both items add *new* gesture handling to the same `detectDragGestures`/tap-gesture block in `MobilePlayerScreen.kt` that's already been the site of two real bugs fixed this session (`ae1d5558` — overlay touch-stealing; the underlying function is fragile). Land together, after Phase 2's 2b has already gone through that file once and settled — don't stack three separate gesture rewrites on top of each other across phases.

### 2a. Double-Tap Left/Right 10s Relative Seek (Replacing Double-Tap Pause)
- **Problem:** In [`MobilePlayerScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/player/MobilePlayerScreen.kt#L465-L473), double-tap toggles pause/resume. This duplicates single-tap + center button and breaks universal mobile video player conventions (YouTube, Netflix, Plex, MX Player).
- **Solution:**
  - Replace the double-tap handler with a horizontal screen-split calculation:
    - Tap on left 40% of the screen: relative seek `-10_000L` (-10s).
    - Tap on right 40% of the screen: relative seek `+10_000L` (+10s).
    - Center 20%: toggle controls overlay or ignore.
  - Implement a momentary seek ripple overlay: a circular translucent pill showing `⟲ 10s` (left) or `10s ⟳` (right) with animated chevrons, accumulating rapid taps (`20s`, `30s`) and automatically fading out after 600ms.
  - Single tap continues to cleanly toggle controls overlay visibility.
- **Note:** This removes documented behavior — `AGENTS.md` § Controls & Navigation currently states "Pause: Explicit via pause button, `KEYCODE_MEDIA_PLAY_PAUSE`, or mobile double-tap (VOD only)." The change itself is sound (matches universal convention, and double-tap-pause is redundant with the existing pause button + single-tap-to-toggle-controls), but `AGENTS.md` is "the single source of truth" per its own header — update that line in the same PR, not as a follow-up. Also fold in the `HapticFeedbackType.LongPress` trigger on seek activation deferred from 6b (Phase 1) — same PR, not a separate haptics follow-up.

### 2c. VOD Vertical Gestures for Brightness & Volume
- **Problem:** In [`MobilePlayerScreen.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/player/MobilePlayerScreen.kt#L477-L483), drag gestures are completely disabled on VOD (`!isLiveContent`).
- **Solution:**
  - On Movies and TV Shows (VOD), utilize vertical swipe drag gestures:
    - Left half vertical drag: adjust screen window brightness (`Activity.window.attributes.screenBrightness`).
    - Right half vertical drag: adjust stream/media volume via `AudioManager`.
  - Display a temporary, elegant centered HUD pill (icon + vertical mini progress bar) showing current brightness/volume percentage that fades out 1s after touch release.
- **Note:** Lowest priority-score item in the whole plan (Complexity 3, Risk 3) — new system-API integration (window brightness, `AudioManager`) layered into the same block as 2a, on a VOD-only surface. Do this last within Phase 4, after 2a has landed and settled, and verify gesture arbitration against the *current* state of the guarded overlay checks (`ae1d5558`), not an older version of the function.

---

## Phase 5 — TV Provider & Settings Management (lower traffic, real friction when visited)

### 8c. Provider Row Crams up to 6 Icon-Only Action Buttons with No Labels
- **Problem:** [`ProviderList`'s row content, TV](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/provider/ProviderSelectionScreen.kt#L322-L389) renders, per provider, up to six adjacent `CinemaIconButton`s — Select (conditional), Manage EPG (conditional), Duplicate, Copy To (conditional), Edit, Delete — each distinguished only by a small icon, no visible text label, packed into one `Row` with `Spacing.xs` between them. On a D-pad, that's up to six small, visually-similar adjacent focus targets per row with no label to read before committing to a press — a user has to know the icon vocabulary (checkmark = select, pencil = edit, etc.) or focus each one and read the content-description off... nothing, since content descriptions aren't rendered visually, only exposed to accessibility services.
- **Solution:**
  - Collapse the secondary actions (Duplicate, Copy To, Edit, Delete) behind a single "more actions" overflow button that opens a focusable menu/dialog with real text labels — mirrors the existing [`FavoriteMenuDialog`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/category/components/FavoriteMenuDialog.kt) pattern already used elsewhere in the TV app for the same "several actions on one item" shape.
  - Keep only the primary action (Select, or nothing if already active) as a direct one-press button on the row itself.

### 8d. TV Settings Is One Long, Flat, Ungrouped List
- **Problem:** [`SettingsScreen` (TV)](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/settings/SettingsScreen.kt#L193-L330) is a single `TvLazyColumn` with ten sequential `item { }` cards — Provider, Playback, Theme, Language, EPG, UI Scale, Developer, Cloud Sync, Export/Import, About — with no section headers, grouping, or side navigation. Each card is itself expandable/interactive (own internal focusable controls), so reaching "About" or "Export/Import" from the top means holding D-pad-down through nine other cards' worth of focus stops first. There's no fast path to a specific settings area.
- **Solution (cheap option, do this one):**
  - Group related cards under section headers (e.g. "Provider & Playback", "Appearance", "Data & Sync", "Advanced") so the list has visual waypoints, even without restructuring the underlying cards. Complexity/Risk stay low (`C2 R1` above) because this doesn't touch any card's internals.
- **Stretch option (not scored/scheduled — separate proposal if wanted):** A persistent left-rail category selector for TV, jumping the `TvLazyColumn` to the relevant section. Meaningfully higher complexity (new persistent nav chrome specific to one screen) for an incremental improvement over section headers — only worth it if headers alone prove insufficient in practice.

---

## Phase 6 — EPG Alternate View (standalone, no dependencies on other phases)

### 6a. Mobile EPG "Now & Next" Fast-Browse Mode
- **Problem:** [`MobileEpgTimeline.kt`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/epg/MobileEpgTimeline.kt#L201-L219) has independent un-synchronized horizontal `LazyRow`s per channel, making horizontal timeline alignment difficult on a phone screen.
- **Solution:**
  - Add an EPG display mode toggle: **Grid Timeline** vs **Now & Next List**.
  - "Now & Next List" renders a clean vertical feed: Channel Logo/Name on the left, Current Program (with time elapsed bar) and Up Next program on the right. Tapping tunes the channel immediately; tapping program opens description dialog.

---

## 7. Verification & Quality Assurance Strategy

1. **Static Analysis & Style Verification:**
   - Run `./gradlew ktlintCheck` and `./gradlew lintDebug` to verify adherence to style and single-return principles.
2. **Build Integrity:**
   - Execute `./gradlew assembleDebug` to compile both `:tv` and `:mobile` targets without warnings or errors.
3. **Hardware & Emulator Testing:**
   - **Android TV (Shield / Bravia):** Verify D-pad navigation, focus restoration, safe margins, and absence of frame drops.
   - **Android Mobile:** Verify touch gestures, double-tap seek accuracy, orientation changes, and smooth 60fps animations.
4. **Phase 4 specifically:** given the fragile-function history of `MobilePlayerScreen.kt`'s gesture code, verify 2a and 2c each individually — full playback session, every overlay open/closed combination — before considering Phase 4 done, not just a final combined pass.
