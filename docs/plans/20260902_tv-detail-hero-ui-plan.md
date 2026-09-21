# TV Detail Screens — Hero Layout Uplift Plan

**Date:** 2026-09-02 (updated 2026-09-02: Phase 5's blocker landed, see below)
**Status:** Phase 1 landed 2026-09-09 (backdrop plumbing — no UI change yet). Phase 2 landed
2026-09-11 (`TvDetailHero`, new file, nothing wired in yet). Phase 3 landed 2026-09-11
(`MovieDetailsScreen` rebuilt on the hero, verified on the TV emulator). Phase 4 landed 2026-09-11
(tabbed sections on the movie screen; series tabs deferred to Phase 5, which wires the hero there
too). Phase 5 landed 2026-09-11 (Seasons/Episodes/Cast/Details/Similar tabs on
`EpisodeSelectionScreen`, episode detail rebuilt on the hero; verified on the TV emulator). See its
own section below for deviations and a real bug found during verification that's out of this
phase's scope.
**Scope:** TV module detail screens only (`MovieDetailsScreen.kt`, `EpisodeSelectionScreen.kt`).
Mobile untouched. Reference: four screenshots of another client (Silo series, Silo
episode, The Godfather, The Martian) — used as a look-and-feel target, not a spec to
clone.

---

## 1. What the reference layout actually is

Same skeleton on all four screens:

| Zone | Reference | Ours today |
|------|-----------|------------|
| Background | Full-bleed unblurred backdrop, right-weighted, left-to-right dark scrim | Flat `colorScheme.background` |
| Left rail | Persistent icon rail (home, search, shuffle, genres, favourites, movies, settings) | None on TV |
| Headline | Large title logo art, top-left, alone on its line | `TitleLogoOrText` at `osdLogoHeight` (56dp) inline with 3 icon buttons |
| Meta line | One muted dot-separated line: year · content rating · runtime · Ends at · genres | Same facts, but split across a metadata row, a genre line, a "Released" line |
| Score chips | Dark rounded chips, number over label ("8.7 / Community Rating", "97% / Rotten Tomatoes") | `RatingBadge` inline, no label |
| Tagline / plot | Italic tagline, then plot, on the backdrop | Plot inside a `GlassPanel` |
| Actions | One wide white pill (`Resume from 38m`) + a row of circular outline icon buttons | Primary + secondary pills; icon buttons live up in the header |
| Sections | Pill tab row (Cast / Crew / Studios / Chapters / Details / Similar) with one section rendered below | Everything stacked at once inside the glass panel + related rows |
| Series extra | "Next Up" glass card top-right: episode title, plot, progress bar, "26m remaining" | Continue-watching handled inline in the episode list |
| Poster | None — backdrop replaces it | `posterWidth` × `posterHeightLarge` poster on the left |

The single structural idea worth taking: **backdrop + one column of text + one action
row + tabbed sections**, instead of **poster + glass panel holding everything**.

## 2. Data we do not have

Checked before planning any UI:

- **Backdrop URL** — not in `MediaMetadata`. `XtreamSeriesEntity.backdropPath` exists
  (comma-separated); `XtreamStreamEntity` (VOD) has no backdrop column at all.
  Jellyfin has `BackdropImageTags`. TMDB `/images` is already fetched for logos but
  `TmdbImagesResponse` only parses `logos`.
- **Cast headshots** — `metadata.cast` is a comma-joined string. No `/credits` call in
  `TmdbApiService`, no person model.
- **Rotten Tomatoes** — no source. TMDB does not carry it. **Out of scope**; the
  community-rating chip alone gets the chip treatment.
- **Chapters / Studios / Crew tabs** — no data behind them. Tabs are built from what
  each item actually has, never a fixed list.

Consequence: the backdrop is a data-layer task, done first, or every later phase draws
onto a flat background and looks unfinished.

---

## Phase 1 — Backdrop plumbing (no UI change) — **DONE 2026-09-09**

Landed as written below, with two adjustments:

- **Jellyfin's `BackdropImageTags` doesn't go through `getTmdbBackdropUrl`** — that
  method only ever receives `(tmdbId, contentType)`, no local item id, so Jellyfin
  can't build its image URL there. Instead `SeriesDetail` gained a `backdropUrl`
  field (same shape as the existing `coverUrl`), and each provider fills it from its
  own data at detail-build time — Xtream from `backdrop_path`, Jellyfin from
  `BackdropImageTags` right next to where it already sets `coverUrl`. The ViewModel
  fallback chain (item 5 below) reads this field, not a raw provider call.
- **Found and fixed a pre-existing bug while adding `bestBackdropUrl` next to
  `bestLogoUrl`:** both used `maxWithOrNull(compareByDescending { ... })`, which
  actually selects the *lowest*-voted/narrowest image — `maxWithOrNull` returns the
  comparator's greatest element, and a descending comparator inverts what "greatest"
  means relative to the real values. Every logo TMDB has ever served this app was the
  worst-ranked one available. Fixed on both functions (`compareBy`, ascending, not
  `compareByDescending`); caught by the new parsing test's assertion order, not by
  inspection.

Cheapest correct source is TMDB, because it covers movies and series uniformly and
needs no Room migration (a VOD backdrop column would mean `XtreamDatabase` v18 + a
`docs/DATABASE_SCHEMA.md` update — avoid).

1. `TmdbModels.kt`: add `backdrops: List<TmdbImage>` to `TmdbImagesResponse`. Reuse the
   `TmdbLogo` shape (rename to a shared `TmdbImage` or add a sibling — `file_path`,
   `iso_639_1`, and `vote_average` for picking the best one).
2. `MediaProvider`: add `getTmdbBackdropUrl(tmdbId, contentType)` next to
   `getTmdbLogoUrl`. `XtreamMediaProvider` mirrors its logo implementation (same
   `/images` response, so the same cached call can serve both — pick the highest-voted
   language-neutral backdrop). Jellyfin builds its URL from `BackdropImageTags`.
   Providers without one return `null`.
3. `MediaRepository`: one-line delegate, matching `getTmdbLogoUrl`.
4. `MovieDetailsViewModel` / `SeriesDetailsViewModel`: `_backdropUrl` StateFlow,
   populated by the same job that populates `_logoUrl` (one request, two results —
   do not add a second network round-trip), cleared on the same paths that clear
   `_logoUrl`.
5. Fallback chain when TMDB has nothing: series `backdropPath` first entry → `coverUrl`
   → no image (gradient only).

**Test:** parsing test for `backdrops` alongside the existing
`TmdbRecommendationsParsingTest`, plus the backdrop-selection rule.

---

## Phase 2 — `TvDetailHero` shared composable — **DONE 2026-09-11**

Landed as written below, with one contract change: `scoreChips` and `actions` are both
`@Composable RowScope.() -> Unit` content slots, not a `List<ScoreChip>` — a `data class`
shaped like `ScoreChip`'s own `(value, label)` parameters would collide with the
composable of the same name, and a slot lets a caller with nothing to show skip the row
instead of building a one-element list. `title: String` was also added (not in the
original sketch) purely as the logo image's accessibility description, since
`titleFallback` draws its own text and TMDB's logo art carries none.

New file `tv/.../ui/components/TvDetailHero.kt`. Both detail screens use it, so movie
and series never drift apart the way the current two headers already have.

Contract:

```
TvDetailHero(
    backdropUrl, logoUrl, titleFallback,
    metaLine: List<String>,      // pre-formatted, already dot-joined by the caller
    scoreChips: List<ScoreChip>, // rating today; RT if a source ever exists
    tagline, plot,
    actions: @Composable RowScope.() -> Unit,
    sideSlot: @Composable (() -> Unit)? = null,   // series "Next Up" card
)
```

Implementation notes:

- Backdrop: `AsyncImage` with `ContentScale.Crop`, aligned right, over a horizontal
  `Brush` scrim (opaque at left, transparent past ~60%) plus a bottom scrim.
  **Do not reuse `AmbientBackdrop`** — its whole point is a blur that does not exist
  below API 31 (Shields on Android 11), and the reference look is a sharp image behind
  a gradient, which is both closer and cheaper. Reuse `GradientOverlay` for the scrim.
- Size the Coil request to the backdrop's drawn size; a 1080p decode per screen entry
  is what makes Shields stutter on back-out (same lesson as the
  `LazyColumn`-over-scrolling-`Column` fix already in `MovieDetailsScreen`).
- New tokens only — no literals. `TvDimensions.heroLogoHeight` (≈96dp, the reference
  logo is far larger than `osdLogoHeight`), `heroContentWidthFraction`,
  `heroBackdropAspect`; scrim stops in `CinemaAlpha`.
- Hero is a non-focusable `item` in the existing `LazyColumn`, so the current
  `onPreviewKeyEvent` Back interception stays exactly as-is.

**Score chip:** new `core/ui` component `ScoreChip(value, label)` — dark rounded
container, value over caption. `RatingBadge` stays for list rows; this is the detail
treatment only.

---

## Phase 3 — Movie details rebuild — **DONE 2026-09-11**

Landed as written below, plus one structural fix it required: the screen's `LazyColumn`
carried `contentPadding` (horizontal safe margin, on every item) so the hero would have
inherited a margin on both sides — not full-bleed. Moved that horizontal padding onto
each non-hero item individually (`details`, both related-title rows); the hero now runs
edge to edge and everything else is unchanged. "Category" was tried as an icon button per the plan below, then reverted on request — it
stays a labeled `CinemaSecondaryButton` below the tech info, same spot and shape as
before this phase, not part of the action row.

Verified on the TV emulator (`emulator-5556`), not a Shield, per house policy — hero
renders correctly with a real backdrop/logo/score chip, the stream-switch focus dance
(switching to an alternate release of the same movie) lands focus back on the picker
exactly as before, and Back navigation is unaffected. No crashes in logcat across the
session.

Rework `MovieDetailsContent` onto `TvDetailHero`:

- Drop the poster `CinemaThumbnail` and the `GlassPanel` wrapper. The panel exists to
  make text legible over nothing; the scrim now does that job with less overdraw.
- Collapse the metadata row + genre line + "Released" line into one `metaLine`:
  `year · contentRating · duration · Ends at · genres`. Keep `computeEndsAt` /
  `formatDuration` / `extractYear` as they are — formatting is already correct, only
  the arrangement changes.
- Action row: `CinemaPrimaryButton` (Resume/Play) then circular `CinemaIconButton`s for
  start-from-beginning, favourite, watched, refresh, trailer, category. These move down
  out of the header, which is what makes the header a headline.
- Provider name and the `StreamNamePicker` drop to the Details tab (Phase 4) — they are
  diagnostics, not headline facts. The stream-switch focus dance
  (`streamSwitchSignal` / `streamNameFocusRequester`, hard-won on real hardware) must
  move with the picker unchanged, including its reassertion loop.

---

## Phase 4 — Tabbed sections — **DONE 2026-09-11 (movie screen only)**

Landed on `MovieDetailsScreen` as written below: `TvSectionTabs` built once as a
generic, label-only component (not `SeriesDetail`-specific), so Phase 5 reuses it for
the series/episode tabs rather than rebuilding it. Series tabs (`Seasons`, `Episodes`,
...) are **not** built yet — `EpisodeSelectionScreen` doesn't use `TvDetailHero` until
Phase 5, and there's nowhere to hang a tab row without it.

**A real bug, not just an implementation detail:** the Back-inside-a-section rule ("goes to the tab
  row, not out of the screen") initially worked in the screenshot-verified case but
  failed the very next test — a single Back press exited the screen straight past an
  open tab section. Root cause, found by temporary logging: pressing Back measurably
  clears focus (an `onFocusChanged` "false" event lands) *before* either of the
  screen's two Back-handling paths (`BackHandler`, and the `LazyColumn`'s
  `onPreviewKeyEvent`) gets to read that state — so both were reading a
  freshly-and-spuriously-cleared flag and always took the "exit" branch. Same
  "root cause unconfirmed" class of TV back-key flakiness this codebase already has two
  other documented instances of (see `SeasonTab`'s category-button comment and this
  screen's own original `LazyColumn` comment). Fixed by no longer trusting the `false`
  transition at all: `focusInSection` is set `true` only by genuine focus-in events, and
  set `false` only by the explicit "a tab just regained focus" event (`onTabSelected`),
  never by a passive focus-loss observation. Verified on the TV emulator: Back once from
  inside each of the four tabs' content returns to the tab row, Back again exits.

New `tv/.../ui/components/TvSectionTabs.kt`, generalising the existing `SeasonTab`
styling in `EpisodeSelectionScreen.kt` (focus/selected container + border rules are
already right — lift them, do not re-invent).

- Tabs built from available data only. Movie: `Cast`, `Details`, `Similar`
  (+`Collection` when `relatedTitles.collection` is non-empty). Series: `Seasons`,
  `Episodes`, `Cast`, `Details`, `Similar`. No `Crew` / `Studios` / `Chapters` until
  data exists.
- Selected tab holds one `LazyColumn` item below it. This is a perf win as well as a
  look change: today's screen composes cast, tech rows, and up to three related rows
  every visit.
- `Details` tab absorbs `TechInfoRow`s, TMDB id, release date, director, provider name,
  `StreamNamePicker`.
- `Cast` tab: comma-string chips.

**Focus rules — the risk in this phase.** Write them down before coding:
- Initial focus stays on Play/Resume (`playButtonFocusRequester` unchanged).
- Down from the action row lands on the selected tab; down again enters the section.
- Left/right inside the tab row switches tabs; switching resets the section's own
  scroll but never steals focus out of the tab row.
- `focusRestorer()` on the section container so returning up-then-down lands where it
  left. Back inside a section goes to the tab row, not out of the screen.

---

## Phase 5 — Series and episode screens

- `EpisodeSelectionScreen` header adopts `TvDetailHero`, with `sideSlot` = **Next Up
  card**: `TvGlassPanel` holding episode label, plot (2 lines), thumbnail, progress bar,
  remaining time. The data already exists — `resumeAnchorEpisodeId`,
  `episodeProgress`, and the resume position feed it; this is a re-presentation of the
  existing continue-watching logic, not new behaviour.
- Series `metaLine` uses `seriesYearRange()` (already written, already handles
  "2023–present") + content rating + season count + genres.
- `Seasons` tab: season poster row using `SeasonInfo.coverUrl`, selecting one switches
  to `Episodes` for that season. `Episodes` tab keeps the current episode list and its
  `EpisodeDetailPanel` untouched.
- ~~**Coordinate with the uncommitted work in progress**~~ — landed
  2026-09-02: both `EpisodeSelectionScreen` files now use a season
  tab row instead of the accordion (mobile: tabs + swipe; TV: tabs with
  D-pad Left/Right, sticky-pinned header, Left/Right-from-episode season
  switch). This phase's `TvDetailHero`/`sideSlot` work lands on top of that
  tab row, not the old accordion — re-check `SeasonTabs`/`SeasonTab` in
  `EpisodeSelectionScreen.kt` before editing, the season-header code this
  bullet originally warned about no longer exists.
- Episode detail (screenshot 2) is the series hero with the episode's own title, meta
  line, and plot swapped in; no new screen.

**Landed 2026-09-11.** Deviations from the sketch above, and what verification found:

- `Episodes` tab did **not** keep `EpisodeDetailPanel` untouched — the plan's own closing
  bullet calls for the episode-detail hero swap, which is Phase 5 work, not a later
  phase. Both landed together: `EpisodeDetailPanel` now opens on `TvDetailHero` (title,
  season/episode/content-rating/duration/ends-at meta line, score chip, plot, actions —
  Play/Resume, Prev/Next as icon buttons reusing `CinemaIcons.SkipPrevious`/`SkipNext`,
  trailer), with a plain details block below (provider, TMDB id, cast, director,
  container, air date, bitrate) — same shape as the series list and
  `MovieDetailsScreen`'s own Details tab.
- Full tab set landed for series, matching Phase 4's movie tabs exactly rather than a
  bespoke shape: `Seasons` (only with >1 season) / `Episodes` / `Cast` (only when
  `metadata.cast` is non-blank) / `Details` / `Similar` (only when `relatedTitles.moreLikeThis`
  is non-empty). `Episodes` is the one tab not built as a single wrapped item — its
  `stickyHeader` (season pills) + `itemsIndexed` (episode cards) emit directly into the
  outer `LazyColumn`, so episode cards stay individually lazy instead of measuring all at
  once inside a Box.
- Seasons tab: a `LazyRow` of season posters (`SeasonInfo.coverUrl`, falls back to a
  letter tile like every other thumbnail here) with episode counts; selecting one calls
  `selectSeason` and flips the outer tab to `Episodes`, landing on that season already
  selected in the (still-present) inner season-pill row.
- Verified on the TV emulator (single-season and 7-season real catalog entries): all
  five tabs render and switch correctly, Left/Right moves between tabs, Down from the
  hero's action row lands on the tab row, Down from the tab row into a section and Back
  out of one back to the tab row both work, a second Back from the tab row exits the
  screen. Season-poster selection correctly jumps to Episodes with that season already
  showing. Episode-detail Prev/Next correctly cross season boundaries and keep focus on
  the pressed button; Back from episode detail returns to the list on the season it was
  opened from.
- **A real bug found during this verification, not introduced by this phase and not
  fixed here:** `TvDetailHero`'s title (and, in severe cases, its meta line and score
  chip too) can render entirely off the top of the screen. The `heightIn(min)` fix from
  Phase 5's own predecessor work lets the hero grow taller than one screen for a long
  plot/meta combination; initial focus landing on the Play/Resume button then makes
  Compose's own focus-into-view scrolling scroll the list down to show that button,
  carrying the top of the hero (the title) off-screen with it. Reproduced identically on
  `MovieDetailsScreen` (untouched this phase) with an obscure title with no logo image,
  so this is a `TvDetailHero`-wide issue, not a series/episode-only one — worth its own
  follow-up plan, not a Phase 5 blocker.
- **A second, separate, also pre-existing quirk observed:** the first Back press while a
  focused TV `Button` has focus is sometimes swallowed before reaching either
  `BackHandler` — same class of TV back-key flakiness this file already documents twice
  over for the equivalent case on the series list and on `MovieDetailsScreen`; a second
  press always works. `EpisodeDetailPanel` was never given its own `onPreviewKeyEvent`
  interception (the list screen's `LazyColumn` has one; the detail panel doesn't), so
  this isn't a regression — just not fixed by this phase either.

---

## Explicitly not in this plan

- **Left nav rail.** It is a global navigation change across every TV screen
  (`TvNavHost`, safe-margin geometry, D-pad exit from every list on the left edge), not
  a detail-screen change. Worth doing, worth its own plan.
- **Rotten Tomatoes chip** — no data source.
- **Mobile** — the mobile detail screens keep their current layout.
- Poster-grid conversions anywhere (standing constraint).

---

## Order and risk

| Phase | Risk | Why |
|-------|------|-----|
| 1 Backdrop data | Low | Additive, no migration, no UI |
| 2 `TvDetailHero` | Low | New file, nothing wired in yet |
| 3 Movie rebuild | **Medium** | Focus regressions around the stream picker |
| 4 Tabs | **High** | D-pad geometry changes most here |
| 5 Series/episode | **High** | Season tab row now in place, but this phase wraps `TvDetailHero`/`sideSlot` around it — more D-pad geometry to get right |

Ship 1–3 as one reviewable change, 4 as its own, 5 whenever — its blocker (the
accordion→tabs work) is already in. Verify on a Shield (API 30, no
`RenderEffect`) and a Bravia (weak chipset — watch the backdrop crossfade)
before calling any phase done, per
`[[feedback_test_on_emulators_first]]`: emulators first, Shields only with explicit
permission.
