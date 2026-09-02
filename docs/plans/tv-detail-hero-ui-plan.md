# TV Detail Screens — Hero Layout Uplift Plan

**Date:** 2026-09-02 (updated 2026-09-02: Phase 5's blocker landed, see below)
**Status:** Planned — nothing implemented
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

## Phase 1 — Backdrop plumbing (no UI change)

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

## Phase 2 — `TvDetailHero` shared composable

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

## Phase 3 — Movie details rebuild

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

## Phase 4 — Tabbed sections

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
