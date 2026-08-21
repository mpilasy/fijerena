# TMDB "More Like This" — Plan

**Status:** Phase 1 (Xtream) built. Phase 0 item 1 and Phase 2 not started.
**Scope:** every provider *except* Jellyfin — Xtream, Remote M3U, Local files, SMB
**Date:** 2026-08-21

Phase 1 landed as `MediaProvider.getRecommendations`, implemented only in `XtreamMediaProvider`
(where the TMDB client and the FTS index already sit) and defaulting to an empty list everywhere
else — Phase 2 drops into the same slot without touching the UI. Matching lives in
`core/network/.../tmdb/TitleMatcher.kt`. Tapping a card opens search pre-filled with the title
rather than navigating into the item's detail screen, which is why `Screen.Search` grew an
`initialQuery`.

Against the endpoint-choice section below, `/similar` was added afterwards as a **second row**
rather than as filler for the first. The objection there was to padding one row with weaker
results; two separately-labelled rows do not hide which source a title came from. Both endpoints
are fetched concurrently and returned as `RelatedTitles(recommended, similar)`; the similar row is
matched second and skips anything the recommendations row already claimed, so a title TMDB returns
from both endpoints is listed once.

---

## Goal

On a movie or series detail screen, show a row of other titles the user can actually play,
sourced from TMDB's recommendation graph and filtered down to what the active provider carries.

The hard constraint that shapes the whole design: **TMDB answers in TMDB titles, not in the
user's catalogue.** A row that lists films the provider does not carry is a row of dead ends.
Every phase below is really about closing that gap.

---

## What exists today

| Piece | Where | State |
|---|---|---|
| TMDB v3 client | `core/network/.../tmdb/TmdbApiService.kt` | 3 endpoints: season, movie release dates, TV content ratings. Handles both v3 keys and v4 bearer tokens. |
| API key | `XtreamMediaProvider.kt:33` — `BuildConfig.TMDB_API_KEY` | Build-time, not a user setting. `hasApiKey()` gates every call. |
| TMDB id on screen | `MediaMetadata.tmdbId`, shown on all four detail screens | Landed in `feat(details): show the trailer link and TMDB id`. |
| Provider search | `MediaProvider.search()` — `core/player/.../domain/MediaProvider.kt:48` | Defaults to `null`. Only Xtream (`XtreamMediaProvider.kt:487`) and Jellyfin (`JellyfinMediaProvider.kt:175`) override it. |
| Xtream local index | `XtreamStreamDao.searchByFts:123`, `XtreamSeriesDao.searchByFts:74` | FTS4, on-device, no network. |
| Client-side matcher | `SearchUtils.matchesQuery:31`, used by `SearchViewModel` fallback (~`:317`) | Already the path non-Xtream providers take in search. |

### Per-provider reality

| Provider | Has a TMDB id? | Catalogue searchable? | Title quality |
|---|---|---|---|
| **Xtream** | Often. Movies `tmdb_id`, series `info.tmdb`. Absent on plenty of real providers. | Yes — FTS4 in Room, local, fast. | Provider-prefixed: `EN - `, `4K - `, trailing year. |
| **Remote M3U** | Never. `BaseM3uMediaProvider.getMovieDetail:68` builds a `MovieDetail` with nothing but id/name/cover. | No — `search()` not overridden, items sit in an in-memory `List<MediaItem>`. | Whatever the playlist author wrote. |
| **Local files** | Never. | No. | `LocalFileScanner.createFileMediaItem:140` — filename minus extension. Release names: `Dune.Part.Two.2024.2160p.WEB-DL.x265`. |
| **SMB** | Never. | No. | Same as local. |

Note the existing inconsistency: `SmbMediaProvider.kt:27`, `RemoteM3uMediaProvider.kt:34` and
`LocalMediaProvider.kt:28` all advertise `supportsSearch = true` while `search()` returns `null`.
Search works for them today only because `SearchViewModel` falls back to a client-side scan.

Also worth knowing: `XtreamStreamEntity.tmdbId` / `XtreamSeriesEntity.tmdbId` are written **only**
by `updateDetailCache` (`XtreamContentManager.kt:840`, `:868`) — i.e. only for titles whose detail
screen was already opened. A TMDB-id ⋈ catalogue join is therefore sparse by construction and
cannot be the primary matching strategy.

---

## Endpoint choice

TMDB offers two, and they are not interchangeable:

- `GET /movie/{id}/recommendations`, `GET /tv/{id}/recommendations` — algorithmic "if you liked
  this". Higher quality, but returns few or zero rows for obscure titles.
- `GET /movie/{id}/similar`, `GET /tv/{id}/similar` — keyword/genre overlap. Always returns 20
  rows, quality noticeably worse.

**Use `/recommendations`.** After catalogue filtering the row is short anyway; padding it with
genre-adjacent noise from `/similar` makes it worse, not longer. Revisit only if telemetry shows
the row hidden for a large share of titles.

Response shape for both (page 1 only, 20 results): `id`, `title`/`name`, `poster_path`,
`release_date`/`first_air_date`, `overview`, `vote_average`.

---

## Phases

### Phase 0 — prerequisites

1. Implement `search()` in `BaseM3uMediaProvider` (covers Remote M3U, Local, SMB): filter the
   in-memory `items` with `SearchUtils.matchesQuery`. Small, self-contained, and it also makes
   `supportsSearch = true` honest for those three.
2. `TmdbApiService`: add `getMovieRecommendations(movieId)`, `getTvRecommendations(tvId)`, and
   the response models. Mirror the existing `authenticate()` pattern.

### Phase 1 — Xtream

The only provider that already has both halves: a TMDB id on the detail object and a local index
to match against.

Flow, on detail screen load, after the detail itself has rendered:

```
metadata.tmdbId ?: bail (hide row)
  → TMDB /recommendations            (1 network call)
  → for each of 20 results: provider.search(title)   (20 local FTS queries)
  → keep results whose normalized title matches and whose year is within ±1
  → hide the row if fewer than 3 survive
```

The row must never block or delay the detail screen — it loads into its own state and appears
when ready.

### Phase 2 — M3U / Local / SMB

These have no TMDB id, so the id must be *resolved* first:

```
catalogue title → release-name parser → (clean title, year)
  → TMDB /search/movie?query=&year=   (1 network call)
  → take result 1 if the title matches closely, else bail
  → then exactly as Phase 1, matching against the in-memory item list
```

Two new pieces of work here:

- **Release-name parser.** `Dune.Part.Two.2024.2160p.WEB-DL.x265` → `("Dune Part Two", 2024)`.
  Strip separators, resolution/source/codec tokens, release groups, bracketed junk; take the
  first 4-digit 19xx/20xx as the year and everything before it as the title. This is also
  independently useful — local titles currently display raw.
- **Search-result confidence.** TMDB's search will return *something* for almost any string. A
  wrong id yields a confidently wrong recommendation row, which is worse than no row. Require a
  normalized-title equality (not substring) plus a year match before accepting the id.

Deliberately no id resolution for series on these providers — M3U has no series concept at all
(`BaseM3uMediaProvider.getSeriesDetail` returns `UnsupportedOperationException`).

### Phase 3 — persistence (optional, only if Phase 1/2 prove slow)

Cache resolved TMDB ids and recommendation lists in Room, keyed by provider id + item id, with a
long TTL — recommendations change on the order of months. Follow the existing `DETAIL_CACHE_TTL_MS`
7-day convention. In-memory `TtlCache` (already in `core/network/TtlCache.kt`) is the cheaper first
step and may be enough.

---

## Matching rules

Normalization applied to both sides before comparing:

- lowercase; strip accents
- drop provider prefixes: `EN - `, `4K - `, `[VIP] `, leading language/quality tags
- drop punctuation, collapse whitespace
- drop a trailing `(2024)` / `2024` year token, captured separately

Accept a catalogue item as a match when normalized titles are equal **and** (either side lacks a
year **or** the years are within ±1 — providers and TMDB disagree on release vs. air year often
enough that exact equality throws away good matches).

Substring matching is explicitly rejected: `Dune` matching `Dune: Part Two` is a plausible-looking
wrong answer, and the row is small enough that recall matters less than not lying.

---

## Cost

- Phase 1 (Xtream): **1 network call** + 20 local FTS queries per detail screen. FTS queries are
  the same ones the search screen already runs; no measurable cost.
- Phase 2 (M3U/local/SMB): **2 network calls** (search + recommendations), then in-memory
  filtering over a list already held in RAM.
- TMDB no longer enforces the old 40-req/10s ceiling but still throttles. One or two calls per
  detail screen open is nowhere near it. No batching needed.

---

## UI

Placement — below the existing metadata block, above the technical-info rows on movie screens;
below the episode list on series screens (the episode list is the point of that screen).

Four screens to touch, same four as the trailer work:
`tv/.../movie/MovieDetailsScreen.kt`, `mobile/.../movie/MovieDetailsScreen.kt`,
`tv/.../episode/EpisodeSelectionScreen.kt`, `mobile/.../episode/EpisodeSelectionScreen.kt`.

**Presentation is an open question** — there is a standing rejection of converting the Movies/TV
Shows lists to a poster grid. That rejection is about the catalogue lists, not necessarily about a
recommendation row, but the safe default is to reuse the existing `CinemaCard` style rather than
introduce a poster carousel. Confirm before building.

TV specifics: every card D-pad focusable, `focusRestorer()` on the row, the standard 1.0→1.1 scale
+ 2dp border focus treatment from `FocusModifiers.kt`. The row must not steal initial focus from
the Play button.

Hide the row entirely — no header, no empty state, no spinner left behind — when:

- `BuildConfig.TMDB_API_KEY` is blank (`hasApiKey()` is false)
- no TMDB id and none resolvable
- the TMDB call fails
- fewer than 3 catalogue matches survive

Strings: `values/` + `values-fr/`. `values-mg/` is still English placeholders in this area; skip it
and let it fall back.

---

## Edge cases

- **Provider switched while the row is loading** — key the load on provider id, discard stale results.
- **Series vs. movie endpoint** — a title mis-typed as a movie yields a 404 on `/movie/{id}`; treat
  any TMDB failure as "hide the row", never as a screen error.
- **Duplicate catalogue matches** — providers list the same film in several categories and
  qualities. Deduplicate by normalized title, keep the first.
- **The current title recommending itself** — filter the source id out of the results.
- **Excluded categories** — respect the provider's category filters; pass `includeExcluded = false`
  so hidden categories stay hidden.

---

## Testing

- Unit: release-name parser table (release names, plain names, names with years, series patterns).
- Unit: title normalization + match acceptance, including the near-miss cases that must be rejected
  (`Dune` vs `Dune: Part Two`).
- Unit: TMDB response parsing, including an empty `results` array and a 404 body.
- Manual: a title with recommendations and good catalogue overlap; a title with none; a provider
  with no TMDB ids at all; a debug build with a blank TMDB key.

---

## Open questions

1. Card style for the row — reuse `CinemaCard`, or something more compact? (See the poster-grid
   note above.)
2. Is Phase 2 worth it? It costs a release-name parser and a fuzzy-resolution step for providers
   whose catalogues are usually small enough that the overlap may be thin. Phase 1 alone may
   deliver most of the value.
3. Should a matched recommendation navigate straight into the detail screen, or into search
   pre-filled with the title? Direct navigation is better UX but needs the matched item's real
   provider id carried through the row's state.
