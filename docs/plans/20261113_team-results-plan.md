# Team results

**Status:** Done 2026-11-13 (Phases 1 and 2). The user asked for the plan, then the work ("plan then execute").

## Goal

A team's page (Sports → a team) shows the team's results of the last 30 days, with the scores, on
TV and on the phone. Only the team page changes. When there is no result, the page shows nothing
more: no heading, no message (user, 2026-11-13).

## What exists (survey 2026-11-13)

- Every team comes from ESPN (`SportsData.teamGames`: only `SportsSource.ESPN`).
- `SportsData.teamGames` reads `SportsApi.espnTeamSchedule` and keeps only the games that have
  not ended (`coming`). `EspnParser` reads each game's state (`toState`: `FINISHED`) but not its
  score.
- **Football** (`soccer`): the app reads `soccer/all/teams/{id}/schedule?fixture=true`, the coming
  games of every competition. Each game names its competition (`league.slug`, for example `eng.1`,
  `uefa.champions`, `uefa.nations`). Without `fixture=true`, the `all` answer is out of date (an
  English club's August games; Germany's games of 2024).
- **The played games of one competition** come from `soccer/{slug}/teams/{id}/schedule`, with
  each side's score (`competitors[].score.displayValue`) and `status.type.completed`. Checked
  2026-11-13: Arsenal in `eng.1` (7 Nov, Arsenal 1–0 Hull City), in `uefa.champions` (21 Oct,
  Bayern 2–2 Arsenal), in `eng.league_cup`; Germany in `uefa.nations` (1 Oct, Germany 2–0 Serbia).
  The events are not in date order.
- **Other sports:** `{sport}/{league}/teams/{id}/schedule` (the call the app makes now) holds the
  whole season, played games with their scores (Boston Bruins, 13 Nov, Bruins 2–5 Canadiens).
- A scoreboard's `score` is a plain string; a schedule's `score` is an object (`value`,
  `displayValue`).

## Design

- **Data** (`core/network/.../sports`):
  - `SportsGame` gets `homeScore` and `awayScore` (`String?`, as ESPN shows them; null when not
    given). `EspnParser` reads both forms of `score`.
  - `SportsData.teamResults(team, days = 30)`: the team's games with state `FINISHED`, both scores,
    and a start in the last [days] days, newest first.
    - Football: one schedule per competition of the team's coming games (the cached
      `fixture=true` answer), each once. A football team's id has no league (`soccer/{id}`), so a
      competition with no coming game (a cup the team is out of) gives no results.
    - Other sports: the schedule `teamGames` reads (the same cache entry).
    - Each answer is kept as the other schedules are (`SportsCache.scheduleMaxAgeMs`). A failed
      read leaves its games out; it never fails the page.
- **View model:** `SportsTeamViewModel.results` (`StateFlow<List<SportsGame>>`), read after the
  coming games, so they come first. Empty while loading and on failure.
- **UI** (both apps, team page only), after the coming games:
  - A heading "Last 30 Days", then one row per result: the logos as in a game row, the title with
    the score ("Germany 2 – 0 Serbia", home first as in the game titles), and the competition and
    date ("UEFA Nations League · 1 Oct").
  - TV: each row is a list row card that takes focus (so the list scrolls), with no action.
    Phone: a card with no click.
  - Nothing at all when `results` is empty.

## Phases

| Phase | What | Tests |
|---|---|---|
| 1 | Scores in `EspnParser` (both forms), `SportsData.teamResults`, the football competitions | Parser: a schedule's object score, a scoreboard's string score, no score; `teamResults`: the 30-day window, finished only, newest first, the football competitions from the fixtures, a failed competition left out |
| 2 | `SportsTeamViewModel.results`; the TV and phone rows; strings (en, fr, mg); docs (FEATURES, RELEASE_NOTES) | The view model: results after the games, empty on failure; a Compose test for each app: rows with the score, nothing when empty |

## Progress

| Phase | Status |
|---|---|
| 1 | Done 2026-11-13 |
| 2 | Done 2026-11-13 |
