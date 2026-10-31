# Sports teams

**Status:** In progress since 2026-10-31: Phases 1–4 run in parallel on their own branches; shared models in `sports/SportsModels.kt`.

## Goal

Find teams in any sport and any country (US pro and college sports, every soccer league in the
world, handball, water polo and others), see each team's next games with the TV channels that show
them, and open the game on one of the profile's live channels: the guide
programme when the guide has it, else a channel whose name has the broadcaster's name.

## Decisions (user, 2026-10-31)

| # | Question | Decision |
|---|----------|----------|
| SP-D1 | Data sources? | Free sources that need no key: **ESPN** (teams and games; US networks), **FIFA** (soccer games worldwide), **UEFA** (its competitions' games and per-country broadcasters), **NHL** and **MLB** (better TV data for their games). Not TheSportsDB, no scraping, no SofaScore. |
| SP-D2 | Where? | All three: a **Teams** search type, a **Sports** section, and an **Upcoming Games** row on Home for followed teams and leagues. |
| SP-D3 | Followed teams and leagues? | Per profile, **synced** to the profile's other devices, like the Wishlist (keyed by the team or league, not by a source). |
| SP-D4 | A game the guide does not have? | Show the broadcaster ("On Canal+") and list the profile's live channels whose names match it, so the user can try them. |
| SP-D5 | TV outside the US? | Three layers: the **guide** (games found by team names, any country), the **UEFA broadcaster API** (per match, every country, for UEFA competitions), and a **season table** in the app (country → competition → broadcasters, per season) for the other leagues and sports. |
| SP-D6 | Which sports? | US sports, rugby, cricket, tennis and every soccer league ESPN or FIFA lists; **handball** and **water polo** too if Phase 1 finds a usable source for their games (EHF; World Aquatics). A sport with no source stays out. |
| SP-D7 | The phone's Sports entry? | A 6th bottom-bar tab **Sports** between TV Shows and Settings, shown only while the profile follows a team or a league; until then Sports opens from the Teams search. |
| SP-D8 | A child profile? | No Teams search type, no Sports section, no Home row (as People, PS-D4). A child's TV Home stays the walled garden. |
| SP-D9 | Turning it off? | An Admin switch **Sports** (on by default, per device, as Show TMDB Charts), because the data comes from APIs the project does not control. |
| SP-D10 | What is watched? | Only what the user **follows**: they search teams and leagues and mark them as favourites, and the app fetches games and TV only for those. For those games, the broadcasters of every country are shown, grouped by country. No country setting. |
| SP-D11 | The labels? | **Sports** (fr "Sports", mg "Fanatanjahantena"); the search type **Teams** (fr "Équipes", mg "Ekipa"). |
| SP-D12 | Game reminders? | **In this plan**: a bell on a game sets a reminder (see Reminders). |

## Data sources

All were called on 2026-10-31 and answered without a key. None is documented for outside use, so
each can change or close (see Risk).

| Source | Gives | Used for |
|--------|-------|----------|
| ESPN `site.api.espn.com`, `site.web.api.espn.com` | Team search; teams and games in US sports, NCAA, rugby, cricket, tennis and 220 soccer leagues (`sports.core.api.espn.com/v2/sports/soccer/leagues`); US networks only | Search, team pages, games, US TV |
| FIFA `api.fifa.com/api/v3` | Games of hundreds of soccer leagues worldwide (`calendar/matches?from=..&to=..&count=500`, paged by `ContinuationToken`); no TV data | Soccer leagues ESPN does not list |
| UEFA `match.uefa.com/v5/matches`, `broadcaster.uefa.com/v1/broadcasters` | UEFA competitions' games; broadcasters per match per country (no `countryCode` for every country, `countryCode`, `startDate`, `endDate`, `competitionId`, `offset`, `limit` → `{matchId, broadcasters[{name, logo, url}]}`); spec at `/v3/api-docs` | Per-country TV for UCL, UEL, UECL, Nations League, qualifiers |
| NHL `api-web.nhle.com/v1` | `club-schedule-season/{TRI}/now`: `tvBroadcasts[].{network,countryCode,market}`, US and Canada | NHL TV |
| MLB `statsapi.mlb.com/api/v1` | `schedule?...&hydrate=broadcasts`: national and local US TV | MLB TV |
| EHF, World Aquatics (`api.worldaquatics.com`) | Handball and water polo games, if Phase 1 finds usable team schedules; no TV data | Handball, water polo (SP-D6) |
| Season table (in the app) | Country → competition → broadcasters, per season | TV for every other league and sport outside the US (SP-D5) |

**Teams across sources.** A team is found on ESPN (or FIFA, EHF, World Aquatics where ESPN does
not have it) and stored with its source and id. A game from one source gets TV from another by
matching the date (± 2 h) and both team names, normalized (accents, "FC", "SC", "CF"… removed),
with a per-source alias table for the misses found in tests. NHL and MLB teams are mapped from ESPN
by id tables (`ESPN "NJ"` → NHL `"NJD"`…), tested against both team lists.

**TV per game:** the broadcasters of every country from every source that has the game, merged
by country (SP-D10): NHL or MLB for their games (US, Canada), ESPN (US), UEFA (every country) for
UEFA competitions, and the season table for each country a live source does not cover. A failed
source leaves its countries to the season table.

**The season table** (`core:network` asset `sports_broadcasters.json`, versioned):
`{season, country, competition, broadcasters[]}`, seeded from Wikipedia's "List of … broadcasters"
pages and rights announcements, for the competitions of the leagues the app lists. It is per
season, not per game: when two channels share a league in one country, it names both. It needs an
update each season; `scripts/check-sports-table.sh` flags entries whose season has ended.

**Risk.** The parsers ignore unknown fields; a parse failure shows "Couldn't load games" with Retry
(the raw error under it in dev mode); `scripts/check-sports-apis.sh` calls every endpoint so a break
is found by hand, not by users. The Admin switch (SP-D9) turns the feature off. MLB's notice allows
only individual, non-commercial use; the app is free and each device calls for its own user.

## Design

### Gate

- Shown when the Admin switch is on (SP-D9) and the profile is not a child (SP-D8). No key.
- `SportsApi` (`core:network`, Ktor on `NetworkModule.okHttpClient` as `TmdbApiService`): one
  client, a base URL per source, one rate limiter per source (30 calls a minute each).

### Cache

- Disk JSON per team schedule (`cacheDir/sports/schedule_<source>_<id>.json`), 6 h; 1 h for a team
  with a game in the next 2 days (broadcasters are often named late). UEFA broadcasters per
  week (every country), 6 h. League and team lists 7 days. Same pattern as `TmdbChartPages` and `TmdbPeople`.
- A failed call falls back to the cached file when there is one.

### Followed teams and leagues (SP-D3, SP-D10)

- New table `followed_sport` in `xtream_v2.db`: `profileId`, `kind` (TEAM or LEAGUE), `source`
  (`espn`, `fifa`, `uefa`, `ehf`, `worldaquatics`), `league`, `itemId` (the team or league id),
  `name`, `shortName`, `altNames`, `sport`, `logoUrl`, `createdAt`; primary key (`profileId`,
  `kind`, `source`, `itemId`). Room migration,
  `docs/DATABASE_SCHEMA.md` and the rollback helpers in `XtreamDatabaseMigrationTest` in the same
  commit.
- Sync kind `followed_sport` on the `wanted` pattern: SQLite triggers in `XtreamSyncTriggers`,
  `LocalRecords`, `SyncApplier`, tombstones. Follow and Unfollow are an icon action on the team and
  league pages.
- **Only followed items are fetched** (SP-D10): the Sports section, the Home row, reminders and the
  cache refresh read only the followed teams' schedules and the followed leagues' games; a team or
  league page opened from search fetches its games once, for that page, and is not kept up to date.

### Matching a game to the profile's channels

`GameChannelMatcher` (`core:network`), pure Kotlin, unit tested. Inputs: the game (both teams with
their name forms, start time, competition), its broadcasters (above), the profile's live channels
(`MergedLists`) and the guide (`epg_index.db`, `GuideScope`). Output: an ordered list of
`GameChannel(stream, programme?, broadcaster, how)`.

1. **Guide programme on a broadcaster's channel** (best). For each broadcaster, the live channels
   whose normalized name matches it (`ChannelNameNormalizer`, plus an alias table: "Canal+ Sport"
   ↔ "C+ Sport", "beIN Sports 1" ↔ "beIN 1", "SuperSport Premier League" ↔ "SS Premier League"…;
   a channel's country prefix ("FR:", "UK|") must agree with the broadcaster's country when both
   are known). On those channels, a programme that
   starts between 60 min before and 30 min after the game and whose title or description has a
   name of either team.
2. **Guide programme anywhere.** An FTS search for each team's names in the same window
   (`searchByTitleFts`, the window bounded both ways), kept when the programme names both teams, or
   one team and the competition. The guide channel is matched to a live stream as the guide search
   does (`EpgChannelMatcher`). This step needs no broadcaster data, so it works in every country
   and sport.
3. **Broadcaster's channel, no programme** (SP-D4): the channels of step 1 without a programme,
   shown under "On <broadcaster>".
4. **Nothing:** the broadcaster names as text, or "TV not announced yet" when there are none.

The guide keeps only the first `<category>` and no `sub-title`; this plan does not change the
parser. Team names in titles ("Arsenal v Chelsea", "Handball: THW Kiel – Barça") are enough for the
first version; misses are recorded in Phase 6 and a parser change is its own decision.

### Screens (TV and phone)

- **Teams search type** (SP-D2): after People on the Search screen (`SearchTab.TEAMS`). One search
  per query after the user stops typing, minimum 2 letters: ESPN's team and league search, then the
  other sources' team and league lists (cached) for those ESPN does not have. Results in two groups,
  **Teams** then **Leagues**; a row shows the logo, name and sport (and the league, for a team), with
  a mark when it is followed. A row opens the team or league page.
- **Team page** (`Screen.SportsTeam(source, teamId, name)`): logo, name, league; a Follow icon
  action; the coming games (up to 10), by day in the device's time zone. A game row: opponent, home
  or away, competition, time, and its best channel (logo, channel name, "In the guide" or "On
  Canal+"). Opening a game opens the game sheet.
- **League page** (`Screen.SportsLeague(source, leagueId, name)`): logo, name, sport; a Follow icon
  action; the league's games of the next 7 days, by day, as game rows (both teams); its teams as a
  list that opens the team pages.
- **Game sheet**: every `GameChannel` in the matcher's order; picking one plays the channel as a
  guide result does (`Screen.CategoryList` with `initialStreamId`); a programme that has not started
  shows its start time, and opening it still tunes the channel. Below: broadcasters the profile has
  no channel for, grouped by country. A bell icon action sets or clears a reminder.
- **Sports section** (TV rail item after the sections; phone tab per SP-D7): followed teams and leagues as
  a row of logos (each opens its page), then "Next Games": the games of every followed team and
  league in the next 7 days, by day, each with its best channel (a game in a followed league and of
  a followed team shows once). Nothing followed: a short text and a Find Teams action that opens the
  Teams search.
- **Home row "Upcoming Games"**: the next 10 games of followed teams and leagues in the next 48 h, after
  Favourite Channels (`HomeShelf.UPCOMING_GAMES` on TV, `HomeRows` on the phone). A card: both
  logos, time, best channel logo. Absent when the switch is off, nothing is followed or no game is
  coming.
- All looks through the design-system components and roles; icon actions; the focus contract on
  every TV screen (a focus-walk script per screen); British spelling, Title Case in English
  headings; strings in en, fr, mg.

### Reminders (SP-D12)

- A bell on a game (game sheet, team page row) sets a reminder 10 minutes before the start; a
  bell on a followed team's page sets reminders for all its games. Stored per profile in a local
  table `game_reminder` (`profileId`, `source`, `gameId`, `startEpoch`, `teamIds`); not synced (a
  reminder rings on the device where it was set).
- Scheduled with `AlarmManager.setExactAndAllowWhileIdle` when the app may set exact alarms
  (`SCHEDULE_EXACT_ALARM`, granted by the user on Android 12 and later), else
  `setAndAllowWhileIdle` (may ring a few minutes late in Doze). Rescheduled after a reboot
  (`BOOT_COMPLETED`) and when a game's time changes on a schedule refresh.
- The phone: a notification (`POST_NOTIFICATIONS`, asked when the first bell is set) with the game
  and its best channel; tapping it tunes that channel. TV: while the app is open, a banner at the
  top with Watch (tunes the best channel) and Dismiss, following the focus contract; when the app is
  closed, a notification (Android TV shows it only in its notification panel).
- A reminder's start comes from the cached schedule; the alarm reads the game again when it fires,
  so a moved game rings at its new time.

## Phases

| Phase | Work | Status |
|-------|------|--------|
| 1 | `SportsApi` for ESPN, FIFA, UEFA, NHL, MLB; models, cache, team-name matching across sources, NHL and MLB id tables, Admin switch, `check-sports-apis.sh`; parser tests on saved responses. Check EHF and World Aquatics for team schedules and record the result here (SP-D6) | In progress |
| 2 | Season table: format, loader, first seeding (the leagues listed by the app, for the countries of the test sources first, then the rest), `check-sports-table.sh` | In progress |
| 3 | `followed_sport` table, migration, sync kind, schema doc, migration test helpers | Done 2026-10-31: `xtream_v2.db` v29; sync key `<kind>:<source>` in the content type; export version 7 |
| 4 | `GameChannelMatcher` with its alias tables and unit tests on real guide samples | In progress |
| 5 | Teams search type, team and league pages, game sheet on TV and phone; focus walks | Not started |
| 6 | Sports section, Home row; emulator walks on both emulators (live guide from the iptv test source); record match misses | Not started |
| 7 | Reminders: `game_reminder` table (migration, schema doc, migration test helpers), alarms, boot receiver, phone notification, TV banner; tests and emulator walks | Not started |
| 8 | Android Lint, docs (FEATURES, NAVIGATION_GUIDE, RUN_GUIDE with the season table update steps, RELEASE_NOTES, a CONTRIBUTING rule for the sports sources), release | Not started |

## Not in this plan

- Scores, live scores, standings, players.
- Per-game TV outside UEFA competitions, the US and Canada (no free source has it; the season table
  and the guide stand in).
- A paid key (TheSportsDB, Sportmonks).
- Changes to the XMLTV parser (sub-titles, more categories).
