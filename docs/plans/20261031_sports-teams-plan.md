# Sports teams

**Status:** Planned 2026-10-31; open decisions SP-Q1 to SP-Q6 wait for the user. Not started.

## Goal

Find professional and college sports teams, see each team's next games with the TV networks that
show them, and open the game on one of the profile's live channels: the guide programme when the
guide has it, else a channel whose name has the network's name.

## Decisions (user, 2026-10-31)

| # | Question | Decision |
|---|----------|----------|
| SP-D1 | Data source? | **ESPN's public site API** for teams, schedules and US networks in every league, plus the **NHL** (`api-web.nhle.com`) and **MLB** (`statsapi.mlb.com`) APIs for better hockey and baseball TV data. No key. Not TheSportsDB, no scraping. |
| SP-D2 | Where? | All three: a **Teams** search type, a **Sports** section, and an **Upcoming games** row on Home for followed teams. |
| SP-D3 | Followed teams? | Per profile, **synced** to the profile's other devices, like the Wishlist (keyed by the team, not by a source). |
| SP-D4 | A game the guide does not have? | Show the network ("On ESPN") and list the profile's live channels whose names match it, so the user can try them. |

## Open decisions

| # | Question | Proposal |
|---|----------|----------|
| SP-Q1 | The phone's Sports entry: the bottom bar has 5 tabs (Home, Live TV, Movies, TV Shows, Settings). | A 6th tab **Sports** between TV Shows and Settings, shown only while the profile follows a team; else Sports opens from the Home row's header and from the Teams search. |
| SP-Q2 | A child profile? | No Teams search type, no Sports section, no Home row (as People, PS-D4). A child's TV Home stays the walled garden. |
| SP-Q3 | Turning it off? | An Admin switch **Sports** (on by default, per device, as Show TMDB Charts), because the data comes from APIs the project does not control. |
| SP-Q4 | Which leagues? | The list below. A league is a line in one table (`SportsLeague`), so adding one later is small. |
| SP-Q5 | The label? | **Sports** (fr "Sports", mg "Fanatanjahantena"); the search type **Teams** (fr "Équipes", mg "Ekipa"). Malagasy words to be checked by the user. |
| SP-Q6 | Game reminders? | Not in this plan. The app has no reminder or notification code today; a reminder is its own plan. |

## Data sources

All three were called on 2026-10-31 and answered without a key.

**ESPN** (`site.api.espn.com`, not documented, used widely). Leagues (`{sport}/{league}`):
`football/nfl`, `football/college-football`, `basketball/nba`, `basketball/wnba`,
`basketball/mens-college-basketball`, `basketball/womens-college-basketball`, `baseball/mlb`,
`hockey/nhl`, `soccer/usa.1` (MLS), `soccer/eng.1`, `soccer/esp.1`, `soccer/ger.1`, `soccer/ita.1`,
`soccer/fra.1`, `soccer/uefa.champions`, `racing/f1`.

| Need | Endpoint | Fields |
|------|----------|--------|
| Find a team | `site.web.api.espn.com/apis/common/v3/search?query={q}&type=team&limit=10` | `items[].id`, `displayName`, `league`, `sport`, `logos[].href` |
| A league's teams | `/apis/site/v2/sports/{sport}/{league}/teams` | `sports[0].leagues[0].teams[].team.{id,abbreviation,displayName,shortDisplayName,location,name,logos}` |
| A team's games | `/apis/site/v2/sports/{sport}/{league}/teams/{id}/schedule` | `events[].{id,date,name}`, `competitions[0].competitors[].{homeAway,team}`, `status.type.state`, `broadcasts[].media.shortName` |
| Games of a day | `/apis/site/v2/sports/{sport}/{league}/scoreboard?dates=YYYYMMDD` | `competitions[0].geoBroadcasts[].{media.shortName,market.type,region}` |

ESPN's networks are **US only** (`region` does not change them). Games far ahead have no networks
until they are named. F1 is a race, not a team game: it is followed as a series (SP-Q4 keeps it in
only if the team model fits; else it moves to "Not in this plan").

**NHL** (`api-web.nhle.com/v1`): `club-schedule-season/{TRI}/now` (follow the redirect):
`games[].{id,startTimeUTC,gameState,homeTeam.abbrev,awayTeam.abbrev}`,
`tvBroadcasts[].{network,countryCode,market}` (market N, H or A: national, home, away). US and
Canada networks, regional ones too. An NHL team is found on ESPN, then mapped to its NHL code by a
table (`ESPN "NJ"` → `"NJD"`, `"LA"` → `"LAK"`, `"SJ"` → `"SJS"`, `"TB"` → `"TBL"`…), tested against
both team lists.

**MLB** (`statsapi.mlb.com/api/v1`): `schedule?sportId=1&teamId={id}&startDate=..&endDate=..&hydrate=broadcasts`:
`dates[].games[].{gamePk,gameDate,teams.home.team,teams.away.team}`,
`broadcasts[].{name,type,isNational,callSign,homeAway}`; TV rows only. MLB team ids are mapped
from ESPN by a table, tested as the NHL one. MLB's copyright notice allows only individual,
non-commercial use; the app is free and each device calls for its own user, which fits it.

**Order per game:** NHL and MLB networks replace ESPN's for those leagues; ESPN for the rest. When
NHL or MLB fails, ESPN's networks are used.

**Risk.** ESPN's API is not documented and can change or close. All three parsers keep unknown
fields out (`ignoreUnknownKeys`), a parse failure shows "Couldn't load games" with Retry (raw error
under it in dev mode), and a CI-free script (`scripts/check-sports-apis.sh`) calls each endpoint so
a break is found by hand, not by users.

**Outside the US.** The networks are US (and Canada for NHL) names. A user whose sources are
British or French still gets matches from the guide by team names (step 2 below), which needs no
network name.

## Design

### Gate

- Shown when the Admin switch is on (SP-Q3) and the profile is not a child (SP-Q2). No key.
- `SportsApi` (`core:network`, Ktor on `NetworkModule.okHttpClient` as `TmdbApiService`): one client,
  three base URLs, a rate limiter (30 calls a minute in total), a User-Agent of the app.

### Cache

- Disk JSON per team schedule (`cacheDir/sports/schedule_<league>_<id>.json`), 6 h; 1 h for a team
  with a game in the next 2 days (networks are often named late). League team lists 7 days. Same
  pattern as `TmdbChartPages` and `TmdbPeople`.
- A failed call falls back to the cached file when there is one.

### Followed teams (SP-D3)

- New table `followed_team` in `xtream_v2.db`: `profileId`, `league` (`football/nfl`…), `teamId`
  (ESPN id), `name`, `shortName`, `abbreviation`, `logoUrl`, `createdAt`; primary key (`profileId`,
  `league`, `teamId`). Room migration, `docs/DATABASE_SCHEMA.md` and the rollback helpers in
  `XtreamDatabaseMigrationTest` in the same commit.
- Sync kind `followed_team` on the `wanted` pattern: SQLite triggers in `XtreamSyncTriggers`,
  `LocalRecords`, `SyncApplier`, tombstones. Follow and Unfollow are an icon action on the team page.

### Matching a game to the profile's channels

`GameChannelMatcher` (`core:network`), pure Kotlin, unit tested. Inputs: the game (both teams with
their display, short, location and nickname forms; start time; league), its networks, the profile's
live channels (`MergedLists`) and the guide (`epg_index.db`, `GuideScope`). Output: an ordered list
of `GameChannel(stream, programme?, network, how)`.

1. **Guide programme on a network's channel** (best). For each network, the live channels whose
   normalized name matches it (`ChannelNameNormalizer`, plus an alias table: "ESPN" ↔ "ESPN HD",
   "FS1" ↔ "Fox Sports 1", "NBCSN"…; a "US:" or "CA:" prefix counts as a plus). On those channels, a
   programme that starts between 60 min before and 30 min after the game and whose title or
   description has a name of either team.
2. **Guide programme anywhere.** An FTS search for each team's names in the same window
   (`searchByTitleFts`, the window bounded both ways), kept when the programme names both teams, or
   one team and the league. The guide channel is matched to a live stream as the guide search does
   (`EpgChannelMatcher`).
3. **Network channel, no programme** (SP-D4): the channels of step 1 without a programme, shown
   under "On <network>".
4. **Nothing:** the network names as text, or "TV not announced yet" when there are none.

The guide keeps only the first `<category>` and no `sub-title`; this plan does not change the
parser. Team names in titles ("NFL Football: Chiefs at Bills", "Arsenal v Chelsea") are enough for
the first version; misses are recorded in Phase 5 and a parser change is its own decision.

### Screens (TV and phone)

- **Teams search type** (SP-D2): after People on the Search screen (`SearchTab.TEAMS`). One ESPN
  search call per query after the user stops typing, minimum 2 letters, cut to the leagues of
  `SportsLeague`; a row shows the logo, name and league. A row opens the team page.
- **Team page** (`Screen.SportsTeam(league, teamId, name)`): logo, name, league; a Follow icon
  action; the coming games (up to 10), by day in the device's time zone. A game row: opponent, home
  or away, time, and its best channel (logo, channel name, "In the guide" or "On ESPN"). Opening a
  game opens the game sheet.
- **Game sheet**: every `GameChannel` in the matcher's order; picking one plays the channel as a
  guide result does (`Screen.CategoryList` with `initialStreamId`); a programme that has not started
  shows its start time, and opening it still tunes the channel. Below: networks the profile has no
  channel for.
- **Sports section** (TV rail item after the sections; phone per SP-Q1): followed teams as a row of
  logos, then "Next Games": the games of every followed team in the next 7 days, by day, each with
  its best channel. No followed team: a short text and a Find Teams action that opens the Teams
  search.
- **Home row "Upcoming Games"**: the next 10 games of followed teams in the next 48 h, after
  Favourite Channels (`HomeShelf.UPCOMING_GAMES` on TV, `HomeRows` on the phone). A card: both
  logos, time, best channel logo. Absent when the switch is off, no team is followed or no game is
  coming.
- All looks through the design-system components and roles; icon actions; the focus contract on
  every TV screen (a focus-walk script per screen); British spelling, Title Case in English
  headings; strings in en, fr, mg.

## Phases

| Phase | Work | Status |
|-------|------|--------|
| 1 | `SportsApi` (ESPN, NHL, MLB), models, cache, `SportsLeague`, NHL and MLB id tables, Admin switch, `check-sports-apis.sh`; parser tests on saved responses | Not started |
| 2 | `followed_team` table, migration, sync kind, schema doc, migration test helpers | Not started |
| 3 | `GameChannelMatcher` with its alias table and unit tests on real guide samples | Not started |
| 4 | Teams search type, team page, game sheet on TV and phone; focus walks | Not started |
| 5 | Sports section, Home row; emulator walks on both emulators (live guide from the iptv test source); record match misses | Not started |
| 6 | Android Lint, docs (FEATURES, NAVIGATION_GUIDE, RUN_GUIDE, RELEASE_NOTES, a CONTRIBUTING rule for the sports APIs), release | Not started |

## Not in this plan

- Scores, live scores, standings, players.
- Reminders and notifications (SP-Q6).
- TV networks outside the US and Canada (no free keyless source has them).
- Cricket and rugby (ESPN gives no networks for them).
- Changes to the XMLTV parser (sub-titles, more categories).
