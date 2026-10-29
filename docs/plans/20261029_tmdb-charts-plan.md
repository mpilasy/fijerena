# TMDB charts in your library

**Status:** Agreed 2026-10-29 (all decisions below); Done 2026-10-29, walked on both emulators.

## Goal

Home shows today's TMDB charts, cut down to what the profile's sources have: "Trending Movies",
"Trending Shows", "Popular Movies", "Popular Shows", "Top Rated Movies" and "Top Rated Shows" (the
app's word for films in English UI). Only titles that can be played show. A
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

1. **Charts:** TMDB `trending/movie/week`, `trending/tv/week`, `movie/popular`, `tv/popular`, and
   for Top Rated `discover/movie` and `discover/tv` sorted by average with at least 1,000 (movies) or
   500 (shows) votes, 20 titles a page (`page` parameter). Not `movie/top_rated` / `tv/top_rated`:
   found in the walk (2026-10-29), they rank by average alone, so new titles with a few perfect
   votes came first. Titles in English (`Language.ENGLISH`), as More Like This asks, because
   catalogue names are mostly English.
2. **Read only what is needed (user, 2026-10-29):** for each chart, read page 1, match it, and
   read the next page only while the row has fewer than 20 titles. Stop at 20 titles, at the last
   page TMDB has, or at the page limit (CH-D3). A library that has most popular titles costs one
   or two calls a chart; a small one costs more.
3. **Matching:** the More Like This matching, called once per source of the profile
   (`MergedLists.sources`), with no minimum (`minCount = 0`). One `taken` set for the whole row,
   so a title on two sources shows once, from the first source in the profile's order. Each card
   carries its source (`MediaItem.providerId`), as the favourite rows do.
4. **Row:** at most 20 titles, in chart order. Any number of matches shows; a row with no match
   is hidden (CH-D4).
   - **Charts list (CH-D2):** Movies and TV Shows get a **Charts** list beside Recent and
     Favourites, with the section's three charts, each with the same titles as its Home row.
5. **Cache:**
   - **Chart pages:** the TMDB pages read are kept for 24 hours, per chart, for the device, in the
     app's cache directory. A later Home load reuses the cached pages, and asks TMDB only for a page
     it has not read yet.
   - **Matches:** the matched rows are kept in memory per profile, and made again after a catalogue
     sync or a source change.
6. **Filters:** the matching uses the same queries as Search (`includeExcluded = false`), and the
   catalogue's excluded flags follow the profile in use. So the profile's content filters, the
   kids' and adult rules, Hidden Titles and the Parental Controls exceptions apply as on every
   other row, a teenager's filters too.
7. **Admin switch (user, 2026-10-29):** **TMDB Charts** in Admin → Sources & Guide, under the
   TMDB row, on by default. Off, no chart row or Charts list shows on this device, and no chart page
   is read. A device setting (`AppSettings`), as Show Clock: not synced, not exported.
8. **Failure:** no key, no network or a TMDB error means the row does not show. It never shows an
   error.

## Decisions

- **CH-D1 Charts (decided, user 2026-10-29: b):** (a) Popular and Top Rated, films and shows
  (4 rows); (b) also Trending this week (6 rows); (c) only Popular (2 rows).
- **CH-D2 Where (decided, user 2026-10-29: b):** (a) TV Home and phone Home, under the favourite
  rows; (b) a Charts list in Movies and TV Shows too; (c) TV Home only.
- **CH-D3 Page limit (decided, user 2026-10-29: a):** the most pages read for one chart when the catalogue has fewer
  than 20 of its titles. TMDB goes to page 500, so without a limit a small library could cost
  hundreds of calls for one row. (a) 25 pages (the top 500), recommended; (b) 10 pages (the top 200);
  (c) 50 pages.
- **CH-D4 Smallest row (decided, user 2026-10-29: b):** (a) 8 titles; (b) 1 title: show any
  match, hide a row with none; (c) 20 titles (only full rows).
- **CH-D5 Sources (decided, user 2026-10-29: a):** (a) Xtream sources only, as Home's merged lists; (b) Jellyfin
  too (Jellyfin items carry a TMDB id; more work).
- **CH-D6 Profiles (decided, user 2026-10-29: a):** (a) every profile but a child, whose Home stays the walled garden of the
  kids' Home plan; (b) admins only.

## Phases

| Phase | Work | Status |
|---|---|---|
| 1 | The TMDB Charts switch (`AppSettings`, TV and phone Admin rows). `TmdbApiService`: the six chart calls with `page`. A chart page cache (24 hours, cache directory). Unit tests: cache age, page reuse, last page. | Done 2026-10-29 (with Phase 2) |
| 2 | Chart matching: page by page across the profile's sources, stop at 20, page limit, one `taken` set, smallest row. Unit tests with fake pages and catalogue: early stop after one page, a title on two sources once, no key gives no row. | Done 2026-10-29: `matchChart` (core/network `tmdb/TmdbChart.kt`), `ChartRows` (core/ui `home/`) |
| 3 | TV Home: the rows (cards, focus, OK opens the details page on the card's source, Back returns to the card). Strings en, fr, mg. | Done 2026-10-29: six `HomeShelf` chart rows, last on Home under the Wishlist row (`ContentTypeSelectionScreen.kt`) |
| 4 | Phone Home: the same rows. | Done 2026-10-29: `MobileHomeScreen`, last on Home after the Wanted row as on TV; long-press: favourite toggle only |
| 4a | The Charts list in Movies and TV Shows, TV and phone. | Done 2026-10-29: `CategoryViewModel.CHARTS_CATEGORY_ID`, last virtual list; the charts one after the other, each under a heading row (the list's separator row), a title of an earlier chart not repeated (list rows need one key each) |
| 5 | Docs: FEATURES, NAVIGATION_GUIDE, RELEASE_NOTES, `PRIVACY.md` (the app asks TMDB for public chart pages, nothing of the user). Walk on both emulators with a TMDB key. | Done 2026-10-29: walked on both emulators; the walk found Back on TV Home going to a row's first card (all rows, older than the charts), fixed |
