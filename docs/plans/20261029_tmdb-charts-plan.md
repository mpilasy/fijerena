# TMDB charts in your library

**Status:** Proposed 2026-10-29; decisions CH-D1 to CH-D6 open. Not started.

## Goal

Home shows today's TMDB charts, cut down to what the profile's sources have: "Popular Films",
"Popular Shows", "Top Rated Films" and "Top Rated Shows". Only titles that can be played show. A
chart title that no source has (most new releases) is skipped. The app reads only as many chart
pages as it needs to fill a row.

## Today

- TMDB is on only with a user's own key (`TmdbApiService`, `hasApiKey()`). Without a key, no TMDB
  row shows anywhere.
- More Like This already turns a TMDB list into catalogue titles:
  `XtreamMediaProvider.matchToCatalogue` finds each TMDB title by its TMDB id
  (`getAlternateStreams`), else by name and year (`searchByFts` + `TitleMatcher.matches`), only
  outside excluded categories (`includeExcluded = false`). It keeps TMDB's order and one card per
  title (`taken`).
- Home merges the lists of every Xtream source of the profile (`MergedLists`; Xtream only, Q6 of
  the multi-source plan). TV Home: `ContentTypeSelectionScreen.kt` (`loadFavorites`). Phone Home:
  `MobileHomeScreen.kt` (`favoritesRow`).
- `TmdbApiService` has no chart endpoints yet.
- An IMDb chart is not possible: IMDb has no public API, and scraping it is fragile and against
  its terms. TMDB only.

## Design

1. **Charts:** TMDB `movie/popular`, `tv/popular`, `movie/top_rated`, `tv/top_rated`, 20 titles a
   page (`page` parameter). Titles in English (`Language.ENGLISH`), as More Like This asks, because
   catalogue names are mostly English.
2. **Read only what is needed (user, 2026-10-29):** for each chart, read page 1, match it, and
   read the next page only while the row has fewer than 20 titles. Stop at 20 titles, at the last
   page TMDB has, or at the page limit (CH-D3). A library that has most popular titles costs one
   or two calls a chart; a small one costs more.
3. **Matching:** the More Like This matching, called once per source of the profile
   (`MergedLists.sources`), with no minimum (`minCount = 0`). One `taken` set for the whole row,
   so a title on two sources shows once, from the first source in the profile's order. Each card
   carries its source (`MediaItem.providerId`), as the favourite rows do.
4. **Row:** at most 20 titles, in chart order. Hidden when fewer than CH-D4 titles match.
5. **Cache:**
   - **Chart pages:** the TMDB pages read are kept for 24 hours, per chart, for the device, in the
     app's cache directory. A later Home load reuses the cached pages, and asks TMDB only for a page
     it has not read yet.
   - **Matches:** the matched rows are kept in memory per profile, and made again after a catalogue
     sync or a source change.
6. **Filters:** the matching already leaves out excluded categories. The profile's content filters,
   Hidden Titles and the adult rules apply as on every other row.
7. **Failure:** no key, no network or a TMDB error means the row does not show. It never shows an
   error.

## Decisions

- **CH-D1 Charts:** (a) Popular and Top Rated, films and shows (4 rows), recommended; (b) also
  Trending this week (often thin: mostly new releases); (c) only Popular (2 rows).
- **CH-D2 Where:** (a) TV Home and phone Home, under the favourite rows, recommended; (b) a Charts
  list in Movies and TV Shows too; (c) TV Home only.
- **CH-D3 Page limit:** (a) 25 pages (the top 500), recommended; (b) 10 pages (the top 200);
  (c) 50 pages.
- **CH-D4 Smallest row:** (a) 8 titles, recommended; (b) 1 title (show any match); (c) 20 titles
  (only full rows).
- **CH-D5 Sources:** (a) Xtream sources only, as Home's merged lists, recommended; (b) Jellyfin
  too (Jellyfin items carry a TMDB id; more work).
- **CH-D6 Profiles:** (a) every profile but a child, whose Home stays the walled garden of the
  kids' Home plan, recommended; (b) admins only.

## Phases

| Phase | Work | Status |
|---|---|---|
| 1 | `TmdbApiService`: the four chart calls with `page`. A chart page cache (24 hours, cache directory). Unit tests: cache age, page reuse, last page. | Not started |
| 2 | Chart matching: page by page across the profile's sources, stop at 20, page limit, one `taken` set, smallest row. Unit tests with fake pages and catalogue: early stop after one page, a title on two sources once, no key gives no row. | Not started |
| 3 | TV Home: the rows (cards, focus, OK opens the details page on the card's source, Back returns to the card). Strings en, fr, mg. | Not started |
| 4 | Phone Home: the same rows. | Not started |
| 5 | Docs: FEATURES, NAVIGATION_GUIDE, RELEASE_NOTES, `PRIVACY.md` (the app asks TMDB for public chart pages, nothing of the user). Walk on both emulators with a TMDB key. | Not started |
