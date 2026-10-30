# Episode cast

**Status:** Planned 2026-10-30.

## Goal

An episode's details show who is in that episode, and an actor's page says which episode of a show
they were in and opens it. Example (user, 2026-10-30): Robin Williams is in Law & Order: Special
Victims Unit only as Merritt Rook in S9E17 "Authority"; today the app cannot show that.

## Today

- A show's Cast tab lists the whole show's cast (`aggregate_credits`), each actor with the role
  played most (docs/plans/20261030_cast-tab-plan.md, CT-D3). An episode's details have no cast.
- `XtreamMediaProvider` already reads TMDB's season details (`TmdbApiService.getSeason`,
  `tv/{id}/season/{n}`) for the episode plots. That answer also carries each episode's
  `guest_stars` (and crew), which the model (`TmdbEpisode`) drops.
- An actor's page gets `combined_credits`, which give a show credit's `episode_count` and
  `credit_id`, but not which episodes. TMDB's `credit/{credit_id}` gives them (`media.episodes`:
  season, number, name, air date).

## Decisions (user, 2026-10-30)

| # | Question | Decision |
|---|----------|----------|
| EC-D1 | Cast on an episode's details? | The episode's guest stars first, then the show's regular cast; the same cards, "+N more" and actor pages as the Cast tab. |
| EC-D2 | What does an actor-page show card open? (changed by the user, 2026-10-30) | A list of the person's appearances in the show. A guest or recurring role: every episode by name ("S9E17 · Authority"), each opening the episode, the ones the source lacks marked. A regular role (TMDB lists only seasons): the seasons ("Seasons 1–29 · 602 episodes"), each opening the show on that season. A one-episode role opens the episode straight away. The card says "1 episode · as …" / "24 episodes · as …". |
| EC-D3 | A guest episode the source does not have (the show is there)? | The card opens the show's page on that season, with a short note "S9E17 is not in your source". |

## Design

1. **Data:**
   - `TmdbEpisode` keeps `guest_stars` (id, name, character, profile path, order). No new TMDB call:
     the season call already made for the plots brings them. They are cached with the season, as
     the plots are.
   - Regular cast: the show's cast already loaded for the Cast tab.
   - Actor page: the card's episode count comes from `combined_credits` (no call). Opening a show
     card makes one `credit/{credit_id}` call: `media.episodes` for a guest or recurring role,
     `media.seasons` for a regular role (TMDB lists no episodes there, e.g. 602 for Mariska
     Hargitay in SVU). Only for shows the profile's sources have (TMDB-only cards open the TMDB
     page). Through the TMDB rate limiter, cached with the person for 7 days (`TmdbPeople`,
     cleared by Refresh Charts).
2. **Episode details (TV and phone):** a cast section on the episode's details — guest stars, then
   regulars — with the Cast tab's cards (`TvCreditsTabContent`, `MobileCreditsTabContent`), "+N
   more" and actor pages. No section when TMDB has no cast for the show; the chips fallback (CT-D7)
   applies as on the Cast tab.
3. **Actor page cards (TV and phone):** EC-D2 text and target. The appearances list is a simple
   list screen or sheet (TV: a focusable list, Back to the card; phone: a sheet). Opening an
   episode goes to the show's page on that episode (focus on it on TV); it does not play by itself.
4. **Missing episode** (EC-D3): the show's page on that season, with the note.
5. **Filters and profiles:** as the Cast tab (a child gets the cards and the actor page).

## Phases

| Phase | Work | Status |
|-------|------|--------|
| 1 | Data: guest stars in the season model; episode list per guest credit on the actor page (credit call, cache). Unit tests. | Not started |
| 2 | Episode details cast section, TV and phone. | Not started |
| 3 | Actor page episode cards and targets, missing-episode note, TV and phone. | Not started |
| 4 | Checks (Android Lint included), emulator walks, docs (FEATURES, NAVIGATION_GUIDE, PRIVACY if needed, RELEASE_NOTES). | Not started |
