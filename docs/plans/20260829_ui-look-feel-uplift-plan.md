# UI Look & Feel Uplift Plan

**Status:** Complete (Phases 1–3 landed 2026-08-29; Phase 4 landed 2026-08-29).

| Phase | Item | Status |
|-------|------|--------|
| 1 | 1a Language/region badge | Done 2026-08-29 |
| 1 | 1b Thumbnail scrim | Done 2026-08-29 |
| 1 | 1c List row depth | Done 2026-08-29 |
| 2 | 2a Player control cluster | Done 2026-08-29 (mobile) |
| 2 | 2b Scrubber restyle | Done 2026-08-29 (mobile) |
| 2 | 2c TV player controls | Skipped deliberately — see 2c |
| 3 | Content Type Selection | Done 2026-08-29, **scope changed** — see Phase 3 |
| 4 | 4a TMDB poster art in list rows | Done 2026-08-29 |
| 4 | 4b Skeleton loading states | Done 2026-08-29 |
| 4 | 4c Badge consistency pass | Done 2026-08-29 |

Scope: visual polish only — no functional changes. Screens reviewed: Content Type
Selection, Movies/TV Shows/Live TV lists, Movie/Series Details, Live TV player,
VOD player, on mobile (TV checked for parity where relevant).

**Hard constraint (user):** no horizontal-card UI theme. This originally ruled out
the Content Type Selection cards as-is — but see Phase 3: the user later revised
this to "keep the layout, just make it look modern, not flat", so no layout
rewrite happened and the constraint was not the thing that drove the final work.

**Standing constraint (prior decision, still in force):** no poster-grid
conversion for Movies/TV Shows/Live TV lists — see `[[feedback_no_poster_grid]]`.
Phases 1/2 worked within the existing row-list layout for those screens, and 4a
below must too.

Already fixed (separate commit, prerequisite for this plan): rating locale
consistency, bogus "0s" duration, dangling "Ends at", blank titles. Bugs, not
look-and-feel — not repeated here.

---

## Phase 1 — Quick wins (formatting/data layer, low risk, no layout change)

### 1a. Parse language/region prefixes out of titles into a badge — **Done**

Titles rendered raw provider metadata: `EN -`, `FR -`, `NP:`, `UK:` prefixes,
`(US)`/`(GB)` suffixes baked into the display string — the single biggest
"looks janky" signal, reading like an unprocessed M3U dump.

**What landed:**
- New `parseDisplayTitle()` + `ParsedTitle` in
  `core/player/.../domain/TitleLanguage.kt` — one shared parser, not three
  regexes. Prefix wins over suffix when both are present. Codes must be
  all-uppercase 2–4 letters so ordinary title casing ("A-Team") never matches.
- New `LanguageBadge` composable in `core/ui/.../components/LanguageBadge.kt`.
- Wired into all four display sites named in the original plan: mobile
  `StreamCard` (`MobileCategoryListScreen.kt`), TV `StreamItem`
  (`StreamList.kt`), and both platforms' `RelatedTitlesRow.kt`.

### 1b. Thumbnail scrim for inconsistent source art — **Done**

`CinemaThumbnail.kt` uses `ContentScale.Crop` uniformly — correct for real
poster art, but provider `stream_icon` stills vary wildly in aspect/subject.

**What landed:** `overlayGradient = true` on every list-row thumbnail — the four
files above plus mobile and TV `SearchScreen.kt`. Reuses the existing
`GradientOverlay`; no new component, no data pipeline change.

Note this is deliberately a cosmetic patch over the real problem, which is that
list rows use raw provider art at all. 4a is the proper fix.

### 1c. List row depth — **Done**

`CinemaCard` passed through M3 `CardDefaults.cardElevation()`/`cardColors()`
with no override, so rows read as flat single-tone blocks stacked in a list.

**What landed:** `CinemaCard` (mobile) now defaults to real elevation
(`MobileDimensions.cardRowElevation`) and a hairline border
(`CinemaAlpha.cardHairline` — new token — over `CinemaSurfaceLight`). Exposed as
`cinemaCardHairlineBorder()` so the two call sites that swap in an accent border
conditionally (now-playing row, continue-watching episode) fall back to the
hairline instead of `null`.

**Mobile only, by design.** TV has no `CinemaCard`; its rows use
`tv.material3.Card` with `Glow`/`CardScale`, which already gives depth on focus.

---

## Phase 2 — Player chrome

### 2a. Group controls into a control cluster — **Done (mobile)**

`MobileControlsOverlay.kt` rendered bare icon buttons over the video with only a
gradient scrim behind — stock ExoPlayer skin, not a branded player.

**What landed:** the centre transport row (rewind / play-pause / forward) now
sits inside a `GlassPanel` with `panelShape = CircleShape` — a pill, as
specified, reusing the component already imported in that file.

### 2b. Restyle the scrubber — **Done (mobile)**

**What landed:** thumb enlarged and rounded via a `SliderDefaults.Thumb`
override at `MobileDimensions.playerScrubberThumbSize` (20dp, was M3's 4dp-wide
bar). Needed `@OptIn(ExperimentalMaterial3Api::class)` for the `Slider` overload
that accepts `thumb`/`track` lambdas.

**Track deliberately left alone:** the original plan asked for "a thicker,
rounder track", but M3 1.4's default track is already 16dp
(`SliderTokens.InactiveTrackHeight`) with rounded caps and a thumb gap. The
premise was written against an older M3 default; there was nothing to fix.

### 2c. TV player controls — **Skipped deliberately**

`TvPlayerControlsOverlay.kt` was expected to have the same bare-row pattern. On
inspection it does not: its icon row already renders through `CinemaButton`
(rounded, individually tinted, with focus border and scale) inside a
`TvGlassPanel`. The "bare icons on black" problem 2a/2b fixed on mobile does not
reproduce there, so applying the same wrapper would have been redundant chrome.

Revisit only if the TV player is restyled for another reason.

---

## Phase 3 — Content Type Selection — **Done, scope changed**

**The plan of record for this phase is this section, not the superseded
direction preserved below.**

**What was originally planned:** a layout rewrite of `GradientContentCard` from
three stacked full-width horizontal bars into a row of vertical tiles.

**Why that changed (user decision, 2026-08-29):**
1. On review, TV's `ContentTypeHeroCard` **already was** the row-of-vertical-tiles
   shape this plan proposed — three cards in a `Row`, each `weight(1f)`, icon and
   live-pulse on top, vertical flow down to title/subtitle/count. Only mobile's
   `GradientContentCard` had the banned horizontal-bar shape.
2. Three equal tiles on a phone in portrait works out to roughly 98–116dp per
   tile (360–412dp screen, minus 16dp margins and two 16dp gaps) — too narrow for
   the description line the plan wanted kept.
3. The user's call: keep both platforms' existing layouts, fix only "flat, not
   modern".

**What landed** — identical treatment on both `GradientContentCard` (mobile) and
`ContentTypeHeroCard` (TV), no layout or content change on either:
- Real elevation/glow at rest: mobile `CardDefaults.cardElevation`
  (`MobileDimensions.heroCardElevation` / `heroCardPressedElevation`); TV a new
  `TvFocusTokens.restingGlow` — a permanently-on, dimmer sibling of the existing
  `focusedGlow`, carrying the same per-style `focusUsesShadow` opt-out so it
  never outshines focus.
- A resting hairline border (`CinemaGlassBorder`) — previously focus-only on TV,
  absent entirely on mobile.
- A diagonal gloss overlay brush over the flat gradient fill.
- The icon moved into a frosted circular chip.
- The count number/text moved into a frosted pill.
- Two new shared tokens behind the last three: `CinemaAlpha.heroSheen`,
  `CinemaAlpha.heroChipBackground`.

<details>
<summary>Superseded: the original tile-layout direction (kept for the reasoning trail)</summary>

Direction to build instead: three cards arranged as a **row of vertical
tiles** — icon/count up top, title below, no left-to-right horizontal bar
shape. Concretely:
- Equal-width tiles side by side (not stacked full-width bars) or a 2-up +
  1-wide arrangement.
- Icon + live-pulse dot centered near the top of each tile, count as a large
  number below it, title/description beneath that — vertical flow inside
  each tile instead of the current horizontal icon-left/count-right split.
- Keep the existing per-type gradient identity (orange/blue/light-blue) and
  the `showTotal`/dev-mode fraction logic — only the shape changes, not the
  data shown.

</details>

---

## Phase 4 — Backlog, now specced

### 4a. TMDB poster art in list rows — **premise corrected, biggest item**

**The original plan's premise was wrong and must not be carried forward.** It
claimed "Detail screens already prefer TMDB poster/backdrop (`TmdbModels.kt` has
`poster_path`)", implying the posters were already fetched and cached and the
work was just pointing list rows at them. Verified 2026-08-29, that is not the
case:

- `TmdbModels.kt` declares `posterPath`, but **`posterPath` has zero read sites
  anywhere in the repo** — it is parsed off the wire and dropped.
- There is **no TMDB image base URL** constant anywhere (`TmdbApiService` only
  ever hits `https://api.themoviedb.org/3/`; images live on a different CDN host).
- **No Room table stores a poster path.** No column on `xtream_streams` or
  `xtream_series`.
- Detail screens render `movieDetail.coverUrl` / `seriesDetail.coverUrl` — the
  **provider's** artwork, not TMDB's. TMDB is used today only for localized
  titles (`getTmdbTitle`), season data, release dates / content ratings, and
  recommendations / similar.
- TMDB is called **on demand from detail-screen ViewModels only**, one title at a
  time, when a detail screen opens. There is no bulk enrichment pass anywhere in
  catalogue sync.

So this is "build poster fetching, storage and backfill from scratch", not "reuse
what's there". Size it accordingly — it is a data-layer feature with a UI payoff,
not a UI task.

**The hard problem, to settle before writing code:** how posters get populated for
a catalogue of thousands of rows without a per-row network call at scroll time.
Three candidate strategies, to be chosen explicitly:

1. **Opportunistic** — cache the poster whenever a detail screen is opened.
   Zero new sync cost, degrades gracefully, but rows fill in only for titles the
   user has already visited, so the list stays visibly mixed for a long time.
   Cheapest to build; weakest payoff.
2. **Bulk backfill during sync** — enrich every `tmdbId`-bearing row.
   Best payoff, but thousands of TMDB requests per provider sync. Needs a
   deliberate throttle, resumability, and a check of TMDB's current rate-limit
   policy before committing (do not assume a figure — verify).
3. **Lazy-on-visible with persistence** — fetch when a row first scrolls into
   view, then persist. Middle ground; risks bursty request storms during fast
   scrolling and needs debouncing plus an in-flight guard.

**Recommendation: start at (1)**, measure how much of a real catalogue actually
gets covered, and only escalate to (2) if coverage is unacceptable. (1) is also a
prerequisite for (2) — both need the same storage and rendering work.

**Steps:**
1. Add TMDB image CDN base URL + a size constant (list rows want a small width
   variant, not the full-size original) to `core/network/.../tmdb/`.
2. Add poster-path storage. Room migration on `xtream_v2.db`: new nullable column
   on `xtream_streams` and `xtream_series`. **This is a schema change — it must
   update `docs/DATABASE_SCHEMA.md` in the same commit**, per AGENTS.md, including
   the version line and the migration-history parenthetical.
3. Populate it on the chosen strategy's hook.
4. Render with an explicit fallback chain: TMDB poster → provider `stream_icon` →
   `TypographyFallback`. `CinemaThumbnail` already has the last two; this adds a
   preferred first choice.
5. Re-evaluate 1b's blanket scrim once real poster art is in place — the scrim
   exists to hide provider-art inconsistency and may become unnecessary noise on
   rows that have a proper 2:3 poster.

**Must degrade cleanly with no API key.** `TmdbApiService.hasApiKey()` already
exists and can be false; every path added here has to no-op to the provider
artwork in that case, not blank the row.

**Acceptance:** list rows show consistent artwork for matched titles; no network
request is issued during scrolling; offline and no-API-key both still render;
`assembleDebug`, `ktlintCheck` and `lintDebug` clean.

**Respects `[[feedback_no_poster_grid]]`** — this changes what a row's thumbnail
*is*, never the row-list layout into a grid.

### 4b. Skeleton loading states — **smallest of the three, do first**

Category and stream lists show a bare centred `CircularProgressIndicator` while
loading. `ShimmerPlaceholder` already exists in
`core/ui/.../components/CinemaThumbnail.kt` and is already used for thumbnails
and for the Content Type Selection counts, so the building block is in place.

**Sites to convert** (verified 2026-08-29):
- `MobileCategoryListScreen.kt:516` — categories, `UiState.Loading` branch.
- `MobileCategoryListScreen.kt:1157` — streams, `streamsLoading` branch (has a
  "loading streams" label alongside the spinner).
- `StreamList.kt:296` (TV) — streams.
- `CategoryStates.kt:34` (TV) — `LoadingScreen()`, shared, also labelled.

**Steps:**
1. Add a `SkeletonRow` composable to `core/ui` shaped like a real list row —
   thumbnail block at `posterWidth`×`posterHeight`, two stacked text bars — built
   out of the existing `ShimmerPlaceholder`.
2. Replace each loading branch with a short fixed-count column of them (roughly a
   screenful; do not try to guess the incoming item count).
3. Keep the existing text labels where they exist — a skeleton says "loading" less
   explicitly than the current copy does, and the TV one is read at distance.

**Acceptance:** no layout jump between the skeleton and the loaded row on either
platform; shimmer animation does not survive the loading state ending.

**Watch the recomposition trap** documented in AGENTS.md's journal: shimmer runs
an infinite transition. Read the animated value inside a `graphicsLayer` lambda,
never in a composable body, or the whole screen recomposes every frame while it
is on screen — exactly the bug the Content Type Selection live-pulse dot already
carries a long comment about.

### 4c. Icon/badge consistency pass

1a introduced `LanguageBadge`; several other places render what is conceptually a
badge as bare text, so they read inconsistently.

**Candidates found 2026-08-29:**
- Resolution and codec in both player overlays — `MobileControlsOverlay.kt:191`
  and `:199`, `TvPlayerControlsOverlay.kt:234` and `:242`. The codec one already
  hand-rolls a background and corner radius inline with a raw `0.3f` alpha
  literal, which is both a duplicate of `LanguageBadge` and an AGENTS.md
  hardcoded-value violation.
- The `"★ ${formatRating(rating)}"` string, repeated at seven sites across both
  platforms (movie details, episode lists, category rows). Worth a shared
  `RatingBadge` rather than a bare-string eighth copy.

**Steps:**
1. Generalise `LanguageBadge` into a shared badge primitive taking text plus an
   optional tint, keeping `LanguageBadge` as a thin named wrapper so 1a's call
   sites do not churn.
2. Convert the codec/resolution sites, deleting the inline `0.3f` literal in
   favour of a token.
3. Add `RatingBadge` and convert the seven star-string sites.

**Acceptance:** no raw alpha/corner literals left at the converted sites;
`ktlintCheck` clean; badges visually identical across the app.

**Do this after 4a**, not before — if 4a lands, rows gain real poster art and the
badge treatment on top of artwork may want different contrast than badges on a
flat surface, which would mean converting these sites twice.

---

## Suggested order

1. ~~Phase 1 (1a → 1c)~~ — done 2026-08-29.
2. ~~Phase 2 (2a, 2b)~~ — done 2026-08-29; 2c skipped.
3. ~~Phase 3~~ — done 2026-08-29, scope changed.
4. **4b** — smallest, self-contained, no data-layer risk, immediate payoff on
   every category switch.
5. **4a** — the real fix for list-row art quality, but a data-layer feature with a
   schema migration and an unsettled population strategy. Settle the strategy
   question before writing code.
6. **4c** — last, so badges are styled once against whatever 4a leaves on screen.
