# People search

**Status:** Done 2026-10-31, walked on both emulators.

## Goal

Search TMDB straight from the app's Search: find an actor or actress by name and open their page,
and find films and shows by name on TMDB, also ones no source has.

## Decisions (user, 2026-10-31)

| # | Question | Decision |
|---|----------|----------|
| PS-D1 | Where? | A new search type between **Catalogue** and **Guide** on the Search screen (TV and phone), shown only when a TMDB key is set. |
| PS-D2 | What does it return? | TMDB's multi search: people (actors and actresses), films and shows. |
| PS-D3 | Its label? | **People** (fr "Personnes", mg "Olona"). |
| PS-D4 | A child profile? | The People type is not shown at all. |

## Design

- One TMDB call per query (`search/multi`, the app's TMDB language), after the user stops typing
  (as the catalogue search waits), through the TMDB rate limiter. Results in three groups, people
  first: **People**, **Movies**, **TV Shows**.
- A person: photo (a letter when none), name, up to two titles they are known for; it opens their
  actor page (`Screen.Person`). Only people whose department is Acting.
- A film or a show: matched to the profile's sources as the TMDB lists are (`findTmdbTitles`):
  one a source has opens there; one no source has is a TMDB-only card (Not available mark) that
  opens its TMDB page (Add to Wishlist). Titles show their list title (original name in Latin
  script, else English).
- A child profile: no People type (PS-D4); a Live TV section's search neither (TMDB has no channels). The profile's filters apply to the matched titles.
- No TMDB key: the type is not shown. A failed call: "Couldn't reach TMDB" with Retry.

## Phases

| Phase | Work | Status |
|-------|------|--------|
| 1 | Data (search/multi, matching), the search type and results on TV and phone, tests | Done |
| 2 | Checks with Android Lint, emulator walks, docs, release | Done |
