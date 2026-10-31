# Sports teams

**Status:** In progress since 2026-10-31: Phases 1–4 and 6b done; Phase 5, Phase 6 and the core of Phase 7 built; left: the emulator walks (Phases 5–7) and Phase 8. Phase 6b cut the feature to its core (SP-D15); the Data sources and Design sections below describe the cut design.

## Goal

Follow teams that ESPN covers (US pro and college sports, the soccer leagues ESPN lists, rugby,
cricket, tennis), see each team's next games and whether the profile's channels, guide or PPV and
event channels carry each game, and open the game on that channel.

## Decisions (user, 2026-10-31)

| # | Question | Decision |
|---|----------|----------|
| SP-D1 | Data sources? | Free sources that need no key: **ESPN** (teams and games; US networks), **FIFA** (soccer games worldwide), **UEFA** (its competitions' games and per-country broadcasters), **NHL** and **MLB** (better TV data for their games). Not TheSportsDB, no scraping, no SofaScore. (replaced by SP-D15) |
| SP-D2 | Where? | All three: a **Teams** search type, a **Sports** section, and an **Upcoming Games** row on Home for followed teams and leagues. |
| SP-D3 | Followed teams and leagues? | Per profile, **synced** to the profile's other devices, like the Wishlist (keyed by the team or league, not by a source). |
| SP-D4 | A game the guide does not have? | Show the broadcaster ("On Canal+") and list the profile's live channels whose names match it, so the user can try them. (replaced by SP-D15) |
| SP-D5 | TV outside the US? | Three layers: the **guide** (games found by team names, any country), the **UEFA broadcaster API** (per match, every country, for UEFA competitions), and a **season table** in the app (country → competition → broadcasters, per season) for the other leagues and sports. (replaced by SP-D15) |
| SP-D6 | Which sports? | US sports, rugby, cricket, tennis and every soccer league ESPN or FIFA lists; **handball** and **water polo** too if Phase 1 finds a usable source for their games (EHF; World Aquatics). A sport with no source stays out. (replaced by SP-D15) |
| SP-D7 | The phone's Sports entry? | A 6th bottom-bar tab **Sports** between TV Shows and Settings, shown only while the profile follows a team or a league; until then Sports opens from the Teams search. |
| SP-D8 | A child profile? | No Teams search type, no Sports section, no Home row (as People, PS-D4). A child's TV Home stays the walled garden. |
| SP-D9 | Turning it off? | An Admin switch **Sports** (on by default, per device, as Show TMDB Charts), because the data comes from APIs the project does not control. |
| SP-D10 | What is watched? | Only what the user **follows**: they search teams and leagues and mark them as favourites, and the app fetches games and TV only for those. For those games, the broadcasters of every country are shown, grouped by country. No country setting. (replaced by SP-D15) |
| SP-D11 | The labels? | **Sports** (fr "Sports", mg "Fanatanjahantena"); the search type **Teams** (fr "Équipes", mg "Ekipa"). |
| SP-D12 | Game reminders? | **In this plan**: a bell on a game sets a reminder (see Reminders). |
| SP-D13 | UEFA countries? | Ask UEFA only for the countries found in the profile's channel names and categories ("FR:", "UK|"), plus Madagascar; the season table covers the others for UEFA games. (replaced by SP-D15) |
| SP-D14 | TV rights outside the live APIs? | Read **directly from Wikipedia** by the app (its broadcaster list pages, or the broadcasting section of a league's article), only for followed competitions, at most once a week, the last good result kept on failure. No bundled or hand-edited table. Wikipedia tables are written by hand, not structured data: the parser is loose and some pages will have gaps. (replaced by SP-D15) |
| SP-D15 | Cut to the core (user, later on 2026-10-31: "over-engineered") | The goal is a followed team's schedule (Bayern, Ajax…) and whether the profile's channels, guide or PPV/event channels carry each game. Kept: ESPN team search and schedules, following, guide matching, and a **new match of team names in channel names** (PPV and event channels). ESPN, NHL and MLB network names stay only inside the matcher, to find the profile's channels with those names; they are never shown as a list. Removed: UEFA and Wikipedia TV rights, the season table, country detection, FIFA and World Aquatics, and the game sheet's list of networks without a channel. This replaces SP-D4, SP-D5, SP-D13 and SP-D14, the TV part of SP-D10, and SP-D6 beyond what ESPN lists. |
| SP-D16 | What does a team search show? | The best-matching team first, with its next games and their channels right in the results (the user usually searches for one team); other teams and leagues below as smaller rows. |

## Data sources

All were called on 2026-10-31 and answered without a key. None is documented for outside use, so
each can change or close (see Risk).

| Source | Gives | Used for |
|--------|-------|----------|
| ESPN `site.api.espn.com`, `site.web.api.espn.com` | Team and league search; teams and games in US sports, NCAA, rugby, cricket, tennis and the soccer leagues ESPN lists; the US networks of a game | Search, team and league pages, games |
| NHL `api-web.nhle.com/v1` | `club-schedule-season/{TRI}/now`: `tvBroadcasts[].{network,countryCode,market}`, US and Canada | The networks of NHL games |
| MLB `statsapi.mlb.com/api/v1` | `schedule?...&hydrate=broadcasts`: national and local US TV | The networks of MLB games |

**Network names are for matching only.** A game carries the networks ESPN names (US), or the
NHL's or MLB's for their games (`Broadcaster`). `GameChannelMatcher` uses them only to find the
profile's channels with those names ("On ESPN"). They are never shown as a list of where the game
is on: a network the profile has no channel for is not shown.

**Ids.** ESPN league ids are `sport/league`; ESPN team ids are `soccer/{id}`, or
`sport/league/{id}` for other sports (ESPN gives one id to a college's teams in several sports).
A soccer team's games come from `soccer/all/teams/{id}/schedule?fixture=true` (every
competition). ESPN scoreboards are read one day at a time.

**NHL and MLB games.** An ESPN NHL or MLB game gets its networks from the NHL's or MLB's schedule
of its home team: the teams are mapped from ESPN by id tables (`ESPN "NJ"` → NHL `"NJD"`…,
`SportsTeamIds`), tested against both team lists, and the game is found by `TeamNames.sameGame`
(start within 2 h, both team names normalized: accents and club words such as "FC" removed). The
NHL's or MLB's networks replace ESPN's for the countries they name (`SportsData.mergeBroadcasters`).
A failed NHL or MLB call leaves ESPN's networks.

**League search.** ESPN's league search (`search/v2?type=league`) finds the men's UEFA European
Championship (`soccer/uefa.euro`) for "euro" and "European Championship" (checked 2026-10-31);
"Euro 2028" finds nothing, as ESPN does not match the year.

**Risk.** The parsers ignore unknown fields; a parse failure shows "Couldn't load games" with Retry
(the raw error under it in dev mode); `scripts/check-sports-apis.sh` calls every endpoint so a break
is found by hand, not by users. The Admin switch (SP-D9) turns the feature off. MLB's notice allows
only individual, non-commercial use; the app is free and each device calls for its own user.

## Design

### Gate

- Shown when the Admin switch is on (SP-D9) and the profile is not a child (SP-D8). No key.
- `SportsApi` (`core:network`, Ktor on `NetworkModule.okHttpClient` as `TmdbApiService`): one
  client, a base URL per source, one rate limiter per source (30 calls a minute each). Every call
  goes through `SportsData`; with the switch off it calls nothing and answers empty.

### Cache

- `SportsCache`: the raw answer of each call in `cacheDir/sports/<key>.json`, parsed again on each
  read. A team schedule or a league's day of games: 6 h; 1 h when a game starts in the next 2 days
  (networks are often named late). League team lists: 7 days. Same pattern as `TmdbChartPages`
  and `TmdbPeople`.
- A failed call falls back to the cached file when there is one, however old.

### Followed teams and leagues (SP-D3, SP-D10)

- Table `followed_sport` in `xtream_v2.db` (v29): `profileId`, `kind` (TEAM or LEAGUE), `source`
  (`SportsSource.id`: `espn`; `nhl` and `mlb` are kept for their games), `league`, `itemId` (the
  team or league id), `name`, `shortName`, `altNames`, `sport`, `logoUrl`, `createdAt`; primary
  key (`profileId`, `kind`, `source`, `itemId`). Schema in `docs/DATABASE_SCHEMA.md`.
- Sync kind `followed_sport` on the `wanted` pattern: SQLite triggers in `XtreamSyncTriggers`,
  `LocalRecords`, `SyncApplier`, tombstones. Follow and Unfollow are an icon action on the team and
  league pages.
- **Only followed items are fetched** (SP-D10): the Sports section, the Home row, reminders and the
  cache refresh read only the followed teams' schedules and the followed leagues' games; a team or
  league page opened from search fetches its games once, for that page, and is not kept up to date.

### Matching a game to the profile's channels

`GameChannelMatcher` (`core:network`), pure Kotlin, unit tested. Inputs: the game (both teams with
their name forms, start time, competition, its networks), the profile's live channels
(`ReminderChannelFinder.liveChannels`: the visible live streams of the profile's sources) and the
guide (`epg_index.db` through `EpgGuideLookup`). Output: an ordered list of
`GameChannel(stream, programme?, broadcaster?, how)`, only channels of the profile; an empty list
means "Not found on your channels".

1. **Guide programme on a network's channel** (`GUIDE_ON_BROADCASTER`, best). The live channels
   whose name matches a network of the game (`BroadcasterChannels`: country tag and quality words
   removed, one spelling per family; a channel's country tag ("US:", "UK|") must agree with the
   network's country when both are known). On those channels, a programme that starts between
   60 min before and 30 min after the game, or is on at the start and began up to 3 h before
   (pre-match shows), and whose title or description has a name of either team.
2. **Guide programme anywhere** (`GUIDE`). An FTS search for each team's names in the same window,
   kept when the programme names both teams, or one distinctive team name and the competition. The
   guide channel is matched to a live stream as the guide search does (`EpgChannelMatcher`). This
   step needs no network data, so it works in every country and sport.
3. **PPV or event channel by name** (`CHANNEL_NAME`). A live channel whose own name names the game
   ("UK| PPV 3: Bayern v Ajax", "US: NFL 05 | Chiefs @ Bills 20:15", "DAZN EVENT 2 - Real Madrid vs
   Barcelona", "PPV | Bundesliga 04: FC Bayern - Ajax (Sat 01/11 20:30)"): names of both teams, or
   one distinctive team name and the competition (`GameText.channelNameRank`; both teams first).
   The same team-name rules as the guide: accents folded, whole words, weak words ("Inter",
   "City", a city name) only next to the other team, aliases (`SportsAliases`). A club's 24/7
   channel ("FC Bayern TV") names one team only, so it does not match by name; its guide programme
   can (step 1 or 2) when it names the opponent. A channel in a PPV or Events category with no team
   names never matches. A replay word ("Classic", "Highlights") or another year rejects it.
   **Dates in the name** (`ChannelNameDates`): a date ("01/11", either day-month order; "Nov 1",
   "1st November", "1er novembre") must be within 1 day of the game's date (UTC), as the channel's
   time zone is not known; with no date, a day name ("Sat") must be the game's day or next to it; a
   time counts only with a zone ("8:30pm ET", "20:00 CET") and must be within 2 h of the start in
   that zone; a time with no zone ("20:15") never rejects. A word of a team name ("Sun") is not read
   as a day.
4. **Network's channel, no programme** (`BROADCASTER_CHANNEL`): the channels of step 1 without a
   programme, shown as "On ESPN" (a network's exact channel or its sport channels).

Network names use their own word normalization (`BroadcasterChannels`), not
`ChannelNameNormalizer`, which drops "+" ("ESPN+" would read as "ESPN"). Known misses: a guide
that names only the competition ("Basket-ball : NBA") never matches the game; women's and youth
games with the same club names can match; a PPV channel named for an older game of the same teams
matches when its name has no date.

The guide keeps only the first `<category>` and no `sub-title`; this plan does not change the
parser.

### Screens (TV and phone)

- **Teams search type** (SP-D2): after People on the Search screen (`SearchTab.TEAMS`), only where
  Search covers live channels. One search per query after the user stops typing, minimum 2
  letters: ESPN's team and league search. Results in two groups, **Teams** then **Leagues**; a row
  shows the logo, name and sport (and the league, for a team), with a mark when it is followed. A
  row opens the team or league page.
- **Team page** (`Screen.SportsTeam(source, teamId, name)`): logo, name, league; a Follow icon
  action; the coming games (up to 10), by day in the device's time zone. A game row: opponent, home
  or away, competition, time, and its best channel (logo, channel name, "In the guide", "Event
  channel" or "On ESPN"); "Looking for channels…" while matching, "Not found on your channels" when
  none. Opening a game opens the game sheet.
- **League page** (`Screen.SportsLeague(source, leagueId, name)`): logo, name, sport; a Follow icon
  action; the league's games of the next 7 days, by day, as game rows (both teams); its teams as a
  list that opens the team pages.
- **Game sheet** (TV `GameSheetOverlay`, phone `MobileGameSheet`): only the matched channels, in
  the matcher's order; picking one plays the channel as a guide result does (`Screen.CategoryList`
  with `initialStreamId`); a programme that has not started shows its start time, and opening it
  still tunes the channel. "Not found on your channels" when there is none. No list of networks. A
  bell icon action sets or clears a reminder.
- **Sports section** (TV rail item after the sections; phone tab per SP-D7, `Screen.SportsTab`):
  followed teams and leagues as a row of logos (each opens its page), then "Next Games": the games
  of every followed team and league in the next 7 days, by day, each with its best channel (a game
  in a followed league and of a followed team shows once: same key or `TeamNames.sameGame`).
  Nothing followed: a short text and a Find Teams action that opens the Teams search.
- **Home row "Upcoming Games"**: the next 10 games of followed teams and leagues in the next 48 h,
  after Favourite Channels (`HomeShelf.UPCOMING_GAMES` on TV, `HomeRows` on the phone), read again
  at most every 15 min. A card: both logos, time, best channel logo and name, else "Not found on
  your channels". Absent when the switch is off, nothing is followed or no game is coming.
- All looks through the design-system components and roles; icon actions; the focus contract on
  every TV screen (focus tests per screen); British spelling, Title Case in English headings;
  strings in en, fr, mg.

### Reminders (SP-D12)

- A bell on a game (game sheet, team and league rows, the Sports section) sets a reminder 10
  minutes before the start; a bell on a followed team's page sets reminders for all its games, now
  and as its schedule grows. Stored per profile in a local table `game_reminder` (v30, not synced:
  a reminder rings on the device where it was set).
- `GameReminders` schedules each with `AlarmManager.setExactAndAllowWhileIdle` when the app may set
  exact alarms (`SCHEDULE_EXACT_ALARM`), else `setAndAllowWhileIdle` (may ring a few minutes late
  in Doze). `GameReminderRescheduleReceiver` (boot, app update, clock changes) is on only while a
  reminder exists. A schedule read moves a reminder to its game's new time
  (`GameReminders.onGamesRead`); a cancelled or postponed game does not ring.
- When it rings, `ReminderChannelFinder` finds the game's best channel with the same matcher. The
  phone: a notification (`POST_NOTIFICATIONS`, asked when the first bell is set) with the game and
  that channel; tapping it tunes the channel. TV: while the app is open, a banner at the top with
  Watch and Dismiss (`TvGameReminderBanner`: never takes focus by itself; Menu moves to it; closes
  after 30 s); when the app is closed, a notification.

## Phases

| Phase | Work | Status |
|-------|------|--------|
| 1 | `SportsApi` for ESPN, FIFA, UEFA, NHL, MLB; models, cache, team-name matching across sources, NHL and MLB id tables, Admin switch, `check-sports-apis.sh`; parser tests on saved responses. Check EHF and World Aquatics for team schedules and record the result here (SP-D6) | Done 2026-10-31: `SportsApi`, `SportsCache`, `SportsData`, `TeamNames`, `SportsTeamIds`, Admin switch; World Aquatics built (national teams); EHF has no usable source, handball out; season table wired into `SportsData` |
| 2 | Season table: format, loader, first seeding (the leagues listed by the app, for the countries of the test sources first, then the rest), `check-sports-table.sh` | Done 2026-10-31: 26 competitions, 743 rows, 214 countries. Removed in Phase 6b (SP-D15) |
| 3 | `followed_sport` table, migration, sync kind, schema doc, migration test helpers | Done 2026-10-31: `xtream_v2.db` v29; sync key `<kind>:<source>` in the content type; export version 7 |
| 4 | `GameChannelMatcher` with its alias tables and unit tests on real guide samples | Done 2026-10-31: `GameChannelMatcher`, `EpgGuideLookup`, `SportsAliases`; tested on UK, FR, DE, ZA and US guide samples |
| 5 | Teams search type, team and league pages, game sheet on TV and phone; focus walks | Built 2026-10-31: Teams type (switch on, not a child, ALL or Live TV search), `Screen.SportsTeam`, `Screen.SportsLeague`, game sheet (TV overlay, phone bottom sheet). Left: emulator walks with Phase 6 |
| 6 | Sports section, Home row; emulator walks on both emulators (live guide from the iptv test source); record match misses | Built 2026-10-31: TV rail item and phone tab (after the first follow) on `Screen.SportsTab`, Upcoming Games row (48 h, 10 games, read again at most every 15 min), game and team bells. Left: emulator walks, match misses |
| 6b | Cut to the core (SP-D15): remove the UEFA, FIFA, World Aquatics clients, the season table and its check, country detection; channel-name (PPV) match; game sheet shows only matched channels and programmes, else "Not found on your channels"; rewrite this plan's Data sources and Design to match | Done 2026-10-31: UEFA, FIFA, World Aquatics clients and parsers, the season table and its check, `ProfileCountries`, `ChannelCountries` and their two DAO queries removed; `SportsSource` keeps ESPN, NHL, MLB; `GameChannelMatcher` step 3 matches team names in channel names, with `ChannelNameDates`; a game shows only matched channels, else "Not found on your channels"; `TeamNames` keeps `sameGame` for NHL, MLB and the Sports section, without its cross-source alias table |
| 7 | Reminders: `game_reminder` table (migration, schema doc, migration test helpers), alarms, boot receiver, phone notification, TV banner; tests and emulator walks | Core done 2026-10-31: `game_reminder` (v30, not synced), `GameReminders`, alarms, reschedule receiver (on only while a reminder exists), phone notification, TV banner (never takes focus by itself; Menu moves to it; closes after 30 s). Left: the bells (Phase 6) and emulator walks |
| 8 | Android Lint, docs (FEATURES, NAVIGATION_GUIDE, RUN_GUIDE, RELEASE_NOTES, a CONTRIBUTING rule for the sports sources), release | Not started; the docs and the CONTRIBUTING rule follow each phase since Phase 6b |

## Not in this plan

- Scores, live scores, standings, players.
- A list of every network or country that could carry a game: a game shows only the profile's
  channels, guide programmes and PPV or event channels (SP-D15).
- TV rights from UEFA, Wikipedia or a bundled season table; country detection from channel names;
  FIFA and World Aquatics schedules; sports ESPN does not list (handball, water polo).
- A paid key (TheSportsDB, Sportmonks).
- Changes to the XMLTV parser (sub-titles, more categories).
