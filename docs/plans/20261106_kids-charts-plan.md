# Kids' charts

**Status:** Planned and done 2026-11-06 (KC1), walked on both emulators.

## Goal

The user (2026-11-06): TMDB's Trending and Popular lists, but for kids. A child profile had no
chart rows (CH-D6 of the TMDB charts plan). TMDB has no kids' lists, so the app builds them.

## Decisions (user, 2026-11-06)

| # | Subject | Decision |
|---|---|---|
| KC-D1 | Who | Child profiles, on TV and phone, in place of the adult charts. A child opens a title's page only (K-D2 of the kids' Home plan): the same titles a child already finds with Search. Other profiles keep the adult charts. |
| KC-D2 | Rows | Trending and Popular, for films and for shows (four rows). No Top Rated. |
| KC-D3 | Films | Popular: TMDB `discover/movie` with the US rating PG-13 at most (`certification.lte=PG-13`; first PG, user widened it 2026-11-06), in Animation or Family, 200 votes at least. Trending: TMDB's week, cut down to the Family genre: a Trending title has no rating, and Animation alone also holds adult anime. |
| KC-D4 | Shows | TMDB has no rating filter for shows. Popular: the Kids or Family genre, 50 votes at least, without news, reality, soap or talk shows, as the adult Popular. Family brings sitcoms such as Frasier (user, 2026-11-06: OK); the exclusion drops Dancing with the Stars. Trending: TMDB's week, cut down to the Kids genre, or Family and Animation together (Animation alone holds Family Guy; Family alone holds sitcoms). |
| KC-D5 | Child rules | The catalogue match leaves out the child's hidden categories (as every catalogue query does) and the parental rules' hidden titles (as Search). No "titles not in your sources" switch: a TMDB-only card opens the Wishlist, which a child has not. |
| KC-D6 | Names | The rows keep the adult rows' names ("Trending Films", "Popular Shows"): a child sees no other charts. No new strings. |

## What TMDB gives (2026-11-06)

- Trending films, 25 pages: about 35 Family titles (Toy Story 5, Zootopia 2, Moana 2…).
- Trending shows, 25 pages: about 10 (PAW Patrol, Peppa Pig, SpongeBob SquarePants…): a short row,
  or none when the sources have none of them.
- Popular films up to PG-13: 89 pages. Popular Kids or Family shows: about 40 pages.

## Phases

| Phase | Work | Status |
|---|---|---|
| KC1 | `TmdbChart.forKids` and `keeps` (a page's titles filtered on `genre_ids`), the four kids' charts, `TmdbChart.of(kids)`; `ChartRows` gives a child the kids' charts and leaves out hidden titles; the TV Home puts them in the adult rows' places, with no switch. Tests: the requests, the genre filter, `of`, `genre_ids` read. Docs: FEATURES, RELEASE_NOTES. Checked on both emulators with Kid. | Done 2026-11-06: Kid's TV Home shows Trending and Popular Shows and Films; focus walks every row, OK opens Coco, Back returns to its card. Phone (TMDB Charts on for the check) shows the same rows. |
