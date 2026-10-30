# Phone poster cards

**Status:** In progress 2026-10-30.

## Goal

More titles per screen on the phone (user, 2026-10-30, with a screenshot of Google TV's phone app):
film and show cards as 2:3 posters about 112 dp wide, about 3.3 per screen, in place of 160 dp 16:9
cards (about 2.3).

## Decisions (user, 2026-10-30)

| # | Question | Decision |
|---|----------|----------|
| PC-D1 | Which rows? | Film and show rows: on Home the favourite, Wishlist and TMDB chart rows; the rows of an actor's page; More Like This. Continue Watching keeps its wide cards (art and progress bar); channel rows keep their logo cards. |

## Design

- One poster card for these rows, from the look-and-feel tokens (`MobileDimensions`, card shape,
  title and second line as today, badges and the Not available mark kept), 2:3 art from the title's
  poster (the TMDB poster for TMDB-only cards), the letter placeholder when there is none.
- Lists, grids and the TV are not changed (no poster grid for lists: user decision, kept).

## Phases

| Phase | Work | Status |
|-------|------|--------|
| 1 | Poster card on the phone rows of PC-D1; walk on the phone emulator | In progress |
