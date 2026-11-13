# Team results

**Status:** Done 2026-11-13 (Phases 1 to 4; 3 and 4 asked by the user after Phase 2). The user asked for the plan, then the work ("plan then execute").

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
  `uefa.champions`, `uefa.nations`). With no parameter, the `all` answer is out of date (an
  English club's August games; Germany's games of 2024). With `fixture=false` (or `season=2026`)
  it holds the played games of every competition, with their scores, also of a cup the team is out
  of (found 2026-11-13 after Phase 2, when the user asked about a team knocked out of the Europa
  League: Arsenal's League Cup game, Bayern's German Cup game, Germany's Nations League games).
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
    - Football: `soccer/all/teams/{id}/schedule?fixture=false`, one request for every
      competition. (Phases 1 and 2 first read one schedule per competition of the team's coming
      games, which missed a competition the team was out of; replaced the same day.)
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

## Phases 3 and 4 (asked 2026-11-13, after Phase 2)

- **Order and entry (Phase 3):** user: "when a team is picked, scroll to the next game then allow
  the user to scroll up (to previous game results) or down to future games. but on pick, focus on
  the next/current game."
  - The results go above the coming games, oldest at the top, newest just above "Next Games".
  - On opening the page, the list scrolls to "Next Games" and focus (TV) is on the next or current
    game; Up goes to the newest result, then older ones, then the header; Down goes to later games.
  - When the results come after the games are shown, the list stays on the games (the results are
    added above them, out of view), unless the user has moved.
  - TV: the header no longer stays in view with the first game while there are results above it.
- **A game in progress (Phase 4):** user: "if a game is in progress, show the score and time in the
  game (or HT or w/e)".
  - `SportsGame.clock`: ESPN's `status.type.shortDetail` while the game is on ("66'", "HT").
  - A game row (TV and phone, every list with game rows, and the game sheet's title) of a game in
    progress with both scores: the title with the score, as a result ("Moldova 1 – 1 Kazakhstan"),
    and the clock in place of the start time.
  - Fresh enough to follow: a team schedule with a game that has started and not ended is kept
    2 minutes (it was 1 hour); the results are kept 1 hour at most, so a game that just ended
    comes. The page is still read once per visit (SP-D10): a new visit reads the score again.

## Phases

| Phase | What | Tests |
|---|---|---|
| 1 | Scores in `EspnParser` (both forms), `SportsData.teamResults`, the football competitions | Parser: a schedule's object score, a scoreboard's string score, no score; `teamResults`: the 30-day window, finished only, newest first, every football competition from one request, a failed read gives none |
| 2 | `SportsTeamViewModel.results`; the TV and phone rows; strings (en, fr, mg); docs (FEATURES, RELEASE_NOTES) | The view model: results after the games, empty on failure; a Compose test for each app: rows with the score, nothing when empty |
| 3 | Results above the games, oldest first; entry on the next game; TV focus order | TV focus walk: entry on the first game, Up to the newest result, Down to the next game; phone: the first game in view on opening |
| 4 | `SportsGame.clock`; live title and clock on the game rows and sheet; the 2-minute and 1-hour cache ages | Parser: the clock of a game on, none otherwise; cache ages; a row of a game in progress shows the score and the clock |

## Progress

| Phase | Status |
|---|---|
| 1 | Done 2026-11-13 |
| 2 | Done 2026-11-13 |
| 3 | Done 2026-11-13 |
| 4 | Done 2026-11-13 (no game in progress for a followed team during the emulator check; covered by the tests) |
