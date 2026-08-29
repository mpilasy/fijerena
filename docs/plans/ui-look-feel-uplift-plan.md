# UI Look & Feel Uplift Plan

Scope: visual polish only — no functional changes. Screens reviewed: Content Type
Selection, Movies/TV Shows/Live TV lists, Movie/Series Details, Live TV player,
VOD player, on mobile (TV checked for parity where relevant).

**Hard constraint (user):** no horizontal-card UI theme. This rules out the
current Content Type Selection cards as-is (`GradientContentCard` in
`mobile/src/main/java/org/njarasoa/fijerena/feature/contentselection/ContentTypeSelectionScreen.kt` —
full-width horizontal bar: icon left, title/description middle, count right) —
see Phase 3.

**Standing constraint (prior decision, still in force):** no poster-grid
conversion for Movies/TV Shows/Live TV lists — see `[[feedback_no_poster_grid]]`.
Phase 1/2 below work within the existing row-list layout for those screens.

Already fixed (separate commit, prerequisite for this plan): rating locale
consistency, bogus "0s" duration, dangling "Ends at", blank titles. Bugs, not
look-and-feel — not repeated here.

---

## Phase 1 — Quick wins (formatting/data layer, low risk, no layout change)

**1a. Parse language/region prefixes out of titles into a badge**
Titles render raw provider metadata: `EN -`, `FR -`, `NP:`, `UK:` prefixes,
`(US)`/`(GB)` suffixes baked into the display string (see TV Shows / Movies /
Live TV screenshots — this is the single biggest "looks janky" signal, reads
like an unprocessed M3U dump). No parsing exists today — confirmed no
`LanguageBadge`/`parseLanguage`/flag-icon code in `core`, `mobile`, or `tv`.
Fix: extract the prefix/suffix pattern into a small language/region chip
(text or flag emoji) rendered separately from the title; title itself shows
clean. Central place to do this once: wherever `MediaItem.name` is displayed
(`StreamCard` in `MobileCategoryListScreen.kt`, `StreamList.kt` on TV,
`RelatedTitlesRow.kt`) — ideally a single parsing helper in `core/player`
consumed by all three, not three separate regexes.

**1b. Thumbnail scrim for inconsistent source art**
`CinemaThumbnail.kt` uses `ContentScale.Crop` uniformly — correct for real
poster art, but provider `stream_icon` stills vary wildly in aspect/subject
(a face closeup, a text-only logo, a random frame). A subtle bottom gradient
scrim (reuse the existing `GradientOverlay`/`overlayGradient` param, already
built but only used selectively) on every list-row thumbnail would hide the
worst mismatches cheaply, no data pipeline change needed.

**1c. List row depth**
`CinemaCard` (`mobile/.../ui/components/cards/CinemaCard.kt`) passes through
M3 `CardDefaults.cardElevation()`/`cardColors()` with no override — rows read
as flat single-tone blocks stacked in a list. Give the shared stream/category
row card a deliberate elevation + a faint top-highlight or hairline border
token (add to `CinemaColors`/`CinemaAlpha`, not one-off per screen) so rows
read as cards, not a flat table.

---

## Phase 2 — Player chrome

**2a. Group controls into a control cluster**
`MobileControlsOverlay.kt` renders bare icon buttons (rewind/play/forward,
volume, subtitles, stats) and a raw M3 `Slider` directly over the video with
only a gradient scrim behind — reads as stock ExoPlayer skin, not a branded
player. Group the transport controls into a pill-shaped semi-opaque surface
(reuse `GlassPanel`, already imported in this file for something else) instead
of icons floating loose on black.

**2b. Restyle the scrubber**
Current `Slider` uses default M3 thumb/track shape at default thickness
(`SliderDefaults.colors()` only touches color, not shape/size). A thicker,
rounder track with a larger thumb reads more like a dedicated video player
than a form control.

**2c. TV player controls** — `TvPlayerControlsOverlay.kt` has the same bare-row
pattern; lower priority since 10-foot UIs read differently at distance, but
worth the same pill treatment for consistency if 2a/2b land well on mobile.

---

## Phase 3 — Content Type Selection redesign (constraint-driven)

Current: one full-width horizontal gradient bar per content type (icon, title
+ description, count), stacked three deep. This is explicitly the layout the
user wants gone.

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

This is a real layout rewrite of `GradientContentCard`, not a token tweak —
scope it as its own PR once Phase 1/2 land, so a design misstep here doesn't
block the cheaper wins.

---

## Phase 4 — Backlog (bigger effort, needs its own scoping pass)

- **TMDB poster art in list rows, not just detail pages.** Detail screens
  already prefer TMDB poster/backdrop (`TmdbModels.kt` has `poster_path`);
  list rows use the provider's raw `stream_icon` straight from Xtream, which
  is the real source of the aspect/quality inconsistency Phase 1b just papers
  over. Swapping list-row thumbnails to the cached TMDB poster when a title is
  already matched would fix it properly, but touches caching/perf — separate
  investigation before committing to it.
- **Skeleton loading states.** Streams list shows a bare `CircularProgressIndicator`
  while loading (`MobileCategoryListScreen.kt`); `ShimmerPlaceholder` already
  exists and is used for thumbnails — extending it to full-row skeletons would
  read more polished during category switches.
- **Icon/badge consistency pass** app-wide once 1a's language-badge component
  exists — same pattern likely fits "quality" tags, resolution badges, etc.
  that are currently plain text.

---

## Suggested order

1. Phase 1 (1a → 1c) — cheapest, highest visible impact, no constraint risk.
2. Phase 2 (2a, 2b) — player is high-visibility screen time, moderate effort.
3. Phase 3 — the constrained redesign; do this once 1/2 establish the visual
   language (badge style, elevation, color use) so the new tiles match rather
   than diverge.
4. Phase 4 — revisit after 1–3 ship and the app's re-evaluated.
