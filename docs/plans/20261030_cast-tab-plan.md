# Cast tab: TMDB cast cards and actor pages

**Status:** Agreed 2026-10-30 (all decisions below). Phase 1 in progress.

## Goal

The Cast tab of a film's or show's page shows who is in it with a photo and a role, the way a
streaming service does. Opening an actor shows the films and shows of that actor that the
profile's sources have. The titles that no source has can show too, behind a switch.

## Today

- **Cast tab:** the source's cast text (`metadata.cast`, one comma string) split into plain chips.
  - TV: `CastTabContent` (`tv/.../feature/movie/MovieDetailsScreen.kt`) and
    `SeriesCastTabContent` (`tv/.../feature/episode/EpisodeSelectionScreen.kt`). The chips are
    `CinemaBadge`s: they take no focus and do nothing.
  - Phone: `CastChipsTabContent` in `mobile/.../feature/movie/MovieDetailsScreen.kt` and
    `mobile/.../feature/episode/EpisodeSelectionScreen.kt`.
  - The tab shows only when the cast text is not blank.
- **TMDB title page** (a title no source has: `TmdbTitleScreen`, `MobileTmdbTitleScreen`) already
  reads TMDB's credits: `TmdbApiService.getDetailsWithCredits` (`append_to_response=credits,videos`),
  then `TmdbTitleViewModel` keeps only the first names (`MAX_CAST`).
- **The credits model** (`TmdbCredits`) keeps only `name` for the cast and `name` and `job` for the
  crew: no TMDB person id, no character, no photo.
- **Matching TMDB titles to the catalogue** exists for More Like This and the charts:
  `MediaRepository.findTmdbTitles` (TMDB id first, then name and year), merged across the profile's
  Xtream sources in profile order (`matchChart`, `chartWithMissing` in `TmdbChart.kt`). The
  profile's filters apply. A missing title is a `TmdbOnlyItem` card, marked with the Not available
  overlay, and opens its TMDB page.
- The film and show pages (`MovieDetailsViewModel`, `SeriesDetailsViewModel`) read TMDB for the
  title, logo, backdrop and trailer, but not for the credits.
- `TmdbApiService` has no limit on how fast it calls TMDB and does not handle HTTP 429 (Too Many
  Requests). TMDB allows about 50 requests a second per IP address; each device uses its own key.

## Design

0. **TMDB rate limit (user, 2026-10-30):** one limiter in `TmdbApiService`, for every TMDB call
   of every feature (charts, More Like This, details, people): at most 20 requests a second and 4 at
   once. On HTTP 429, wait for TMDB's `Retry-After` (2 seconds when it is missing), then try once
   more; a second 429 is a failed call, which each feature already handles as "no TMDB data".
   - The new features add few calls: the Cast tab adds none (the credits come with the title call
     the page already makes), and an actor page makes one call, kept 7 days.
1. **Data, one call each:**
   - A title's credits: the details pages ask `getDetailsWithCredits` once, where they ask TMDB
     today. The cast model gets `id`, `character` and `profile_path`. For a show, `aggregate_credits`
     instead of `credits` (CT-D3), so a show's roles cover all its seasons.
   - An actor: `person/{id}?append_to_response=combined_credits` gives the name, photo, birth
     date, biography and every film and show credit in one call.
   - Names and biographies in the app language, with English for a missing biography. Titles for
     matching in English, as the charts do.
2. **Cast tab (TV and phone):**
   - Crew lines at the top: Director (films) or Created by (shows), and Writers (CT-D4).
   - Then a card for each of the first 10 actors in billing order (CT-D2): the photo (a letter on the section's colour
     when TMDB has no photo), the name, and "as <character>" under it.
   - TV: a focusable grid of cards. OK opens the actor page; Back returns to the card. Phone: a
     grid of cards; a tap opens the actor page.
   - No TMDB id or no TMDB key: today's chips stay (CT-D7).
3. **Actor page (new screen, TV and phone):**
   - Header: photo, name, birth and death dates, place of birth, and the biography shortened to a
     few lines (the full text on OK or tap) (CT-D6).
   - Rows: **Movies** and **TV Shows** of the actor that the profile's sources have, most popular
     first (CT-D5). A card opens the title on its source, as on Home.
   - A switch on each row also shows the titles no source has (`TmdbOnlyItem`, Not available
     overlay, opens the TMDB page where Add to Wishlist is), as on Trending (CT-D8).
   - Acting credits only, except for a person who is opened from a crew line: their crew credits
     (CT-D4).
4. **Matching:** the charts' matching from a list instead of chart pages: one call of
   `findTmdbTitles` per source, in profile order, a title once. At most the person's 50 most popular
   credits are matched (user, 2026-10-30), so a long career costs a bounded number of local
   catalogue queries. Matching makes no TMDB call.
5. **Cache:** an actor's credits for 7 days, a file per person in `cacheDir/tmdb_people/`, as the
   chart pages. Refresh Charts (Admin) also clears it. The matched rows are kept in memory and made
   again after a catalogue sync, as `ChartRows`.
6. **Filters and profiles:** the profile's content filters apply, as everywhere. A child profile
   gets the cast cards and the actor page, but not the switch for missing titles (CT-D9).
7. **Navigation:** a new route `Screen.Person(tmdbId, name)` on both apps; the Back return focus
   follows the TV focus contract (CONTRIBUTING.md).

## Decisions (user, 2026-10-30)

| # | Question | Decision |
|---|----------|----------|
| CT-D0 | A limit on TMDB calls? | Yes: 20 a second, 4 at once, `Retry-After` on 429 (Design 0). |
| CT-D1 | Where do the cast cards show? | Every page with a Cast tab: film and show pages from a source, and the TMDB title pages. |
| CT-D2 | How many actors? | The first 10 in billing order. |
| CT-D3 | A show's cast? | All seasons' (`aggregate_credits`), with the role played most. |
| CT-D4 | Which crew? Can a crew member be opened? | Director, Created by, Writers. Yes: their page lists the titles they made. |
| CT-D5 | The actor page's order? | Most popular first, the release year on each card. |
| CT-D6 | The biography on the actor page? | A few lines, the full text on OK or tap. |
| CT-D7 | No TMDB id or key: what does the tab show? | Today's chips, not openable. |
| CT-D8 | The missing-titles switch? | Off each time the page opens; not stored. |
| CT-D9 | A child profile? | Cast cards and actor page, no missing titles. |
| CT-D10 | How many of a person's credits are matched? | The 50 most popular. |

## Phases

| Phase | Work | Status |
|-------|------|--------|
| 1 | Data: the TMDB rate limiter (CT-D0), the credits model (id, character, photo; `aggregate_credits` for shows), the person call, the 7-day cache, the matching from a list. Unit tests. | In progress |
| 2 | Cast tab: crew lines and cast cards on TV and phone, film and show pages and the TMDB title pages; chips as the fallback. | Not started |
| 3 | Actor page on TV and phone: header, Movies and TV Shows rows, the missing-titles switch, navigation and Back focus. | Not started |
| 4 | Docs (FEATURES, NAVIGATION_GUIDE, PRIVACY: person ids sent to TMDB, RELEASE_NOTES), focus walks, emulator walk. | Not started |

## Privacy

The person ids of the actors opened go to TMDB, as the title ids already do. PRIVACY.md says so.
