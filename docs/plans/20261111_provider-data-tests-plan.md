# Provider data tests

**Status:** In progress. Phase 1 done 2026-11-11. The maintainer took the recommended answer (a) to
PD-Q1 to PD-Q6 on 2026-11-11.

## Goal

Unit tests run the app's own parsers on recorded provider answers and on hand-written variants of
known quirks. A change that breaks a real answer shape then fails in CI, not on a TV.

Two limits:
- CI does not call any provider. A new quirk from outside gets in when someone records it again or
  writes it as a variant from a field report.
- The recorded Xtream answers come from the local jellyxtream bridge (Jellyfin → Xtream). Its JSON
  copies a panel, but our own code writes it. Real panel quirks come in as hand-written variants.

The 12-digit XMLTV time fix is not part of this plan. It is AC-5 of
[the audit cleanups plan](./20261111_audit-cleanups-plan.md). This plan adds fixtures that keep it fixed.

## What exists (survey 2026-11-11)

- **Recorded answers exist for one source only:** the sports clients. There are 20 JSON files under
  core/network/src/test/resources/sports/ (304 KB, recorded 2026-10-31), read by `sportsFixture`
  (core/network/src/test/.../sports/SportsFixtures.kt). scripts/check-sports-apis.sh calls each
  endpoint by hand, not in CI. All other resources folders hold only robolectric.properties.
- **Xtream** (models and client in core:player):
  - Client: `XtreamApiService` (core/player/.../api/XtreamApiService.kt). Lists (`get_live_streams`,
    `get_vod_streams`, `get_series`) are read as a stream with `decodeToSequence`. Single items
    (`get_vod_info`, `get_series_info`) go through `parseItemResponse` into `XtreamResponse`
    (Ok / Unavailable / Malformed / Failed). Login (no action) gives `XtreamAuthResponse`.
  - Json config: `isLenient`, `ignoreUnknownKeys`, `coerceInputValues`. It is a private val in
    `XtreamApiService`. `XtreamNullFieldsTest` copies it by hand, so the two can drift.
  - Leniency in the models (core/player/.../model/XtreamModels.kt):
    - Every field has a default, so a `null` takes the default. An entry without an id is dropped (`withName`).
    - `title`, `description`, `rating`, `duration`, `tmdb`, `episode_run_time` are raw `JsonElement`: any type reads.
    - `EpisodesMapSerializer`, `VideoInfoSerializer`, `AudioInfoSerializer` accept `[]` where an object belongs.
    - `parseItemResponse` reads a body that starts with `[` as Unavailable.
    - Not covered: `""` in an `Int` or `Double` field (`tv_archive`, `stream_id`, `rating_5based`),
      a string where a list belongs (`backdrop_path`, `allowed_output_formats`). These fail the
      whole entry or list today, unless a test shows otherwise.
  - `XtreamUserInfo.activeCons` and `maxConnections` are `String?`. `XtreamLoginCheck` reads them
    with `toIntOrNull()`.
  - Panel guide: `XtreamEpgAnswer.toEpgResponse` (core/player/.../model/XtreamEpgListing.kt) for
    `get_short_epg` and `get_simple_data_table` (base64 title and description); `PanelGuideTimes` in core:network.
  - Tests: `XtreamPayloads` (inline bodies from real panels: `[]`, `{}`, episodes without info,
    a series without `name`), `XtreamResponseTest`, `XtreamNullFieldsTest`, `XtreamAuthResponseTest`,
    `XtreamListNamesTest`, `XtreamEpgListingTest`, `PanelGuideTimesTest`. All inline strings.
- **XMLTV:** `XmltvParser` (core/network/.../xmltv/XmltvParser.kt) and the index path
  `EpgIndexer.ingestFromStream` (`parseChannelForIndex`, `parseProgrammeForIndex`). Both use
  `XmlPullParserFactory`, which the plain JVM test classpath does not give. `EpgFileManager`
  decompresses only when the URL ends in `.gz`. No committed parser test; the AC-5 work in progress
  adds `XmltvTimestampTest` for `parseTimestamp`. Ingest tests
  (`EpgIndexerDirectIngestTest` and others) run under Robolectric with inline XML.
- **M3U:** `M3uParser.parse(BufferedReader)` (core/network/.../local/M3uParser.kt), plain JVM.
  Tests: `M3uParserTest` (2), `M3uParserOptimizationTest` (3), `BaseM3uMediaProviderTest` (7),
  `RemoteM3uMediaProviderTest` (3). Inline strings or generated text.
- **Jellyfin:** `JellyfinApiService`, `JellyfinModels` (`ignoreUnknownKeys`, `isLenient`). Tests:
  `JellyfinMediaProviderTest`, `JellyfinConnectTest`. Inline or mocked.
- **TMDB:** `TmdbApiService` (`isLenient`, `ignoreUnknownKeys`, `coerceInputValues`), `TmdbChart`,
  `TmdbPeople`. Many tests (`TmdbChartTest`, `TmdbImagesParsingTest`, `TmdbPeopleTest` and others),
  all with inline JSON.
- **Sync server protocol:** `SyncWire`, `SyncApi`, `SyncCodec`, `SyncPayloads` (`ignoreUnknownKeys`).
  The server is in this repository (server/, TypeScript, vitest). Kotlin tests: `SyncCodecTest`,
  `SyncApiSocketTest`. No sample shared by both sides.
- **Test providers:** tools/jellyfin-xtream/xtream_bridge.py answers every player_api action the app
  uses and `/xmltv.php`. It passes the login through to Jellyfin, so a recording needs that login.
  The test library has films and shows, no live channels. tools/fake-xtream/server.py has fixed
  data that we wrote: it is not a recording.

## Questions (decided 2026-11-11)

| # | Question | Options |
|---|----------|---------|
| PD-Q1 | Titles and plots in recorded VOD answers? They show what is in a private library. | (a) **Placeholders that keep the shape**: "Film 1 (2004)", a language prefix such as "EN - " kept, plot text replaced by neutral text of a similar length. Recommended. (b) Keep the real titles. **Decided: (a).** |
| PD-Q2 | Where do XMLTV fixtures come from? No allowed source has a real live guide. | (a) **Hand-written variants only**, plus the bridge's `/xmltv.php` if it has channels. Recommended. (b) Also a trimmed guide from a free public XMLTV source that the user names. **Decided: (a).** |
| PD-Q3 | TMDB and Jellyfin fixtures? | (a) **Hand-written from the public API documents.** Move today's inline JSON into fixture files. Recommended: no key, no private server. (b) Record TMDB with the user's key (key removed) and Jellyfin from the user's server, through the same clean-up. **Decided: (a).** |
| PD-Q4 | How do XMLTV tests get an XML pull parser? | (a) **kxml2 as `testImplementation`** (the parser Android ships), plain JVM, if Phase 4 proves it wins over the android.jar stub. Recommended. (b) Robolectric, as the ingest tests do today. **Decided: (a).** |
| PD-Q5 | A variant that fails today? | (a) **Fix the model in the same phase commit**, with a release note. Recommended. (b) Mark the test ignored and plan the fix apart. **Decided: (a).** |
| PD-Q6 | Sync protocol samples? | (a) **One folder of sample messages that both the server's vitest suite and the Kotlin tests read.** Recommended: the two sides cannot drift. (b) Kotlin samples only. **Decided: (a).** |

## Design

### Layout and names

- Xtream fixtures: core/player/src/test/resources/fixtures/xtream/, next to the models.
- All others: core/network/src/test/resources/fixtures/<kind>/, kind = `xmltv`, `m3u`, `jellyfin`,
  `tmdb`, `sync`, `sports`. The sports files move there with `git mv`, and `sportsFixture` follows.
- A file name tells where the file comes from:
  - `rec-<action>.json`: recorded, then cleaned and trimmed.
  - `var-<action>-<quirk>.json`: hand-written, for example `var-get_vod_info-movie_data-empty-array.json`.
- One small loader per module, `fixture("xtream/rec-get_vod_streams.json")`, as `sportsFixture` does.
  No shared test module.
- Each test class KDoc names the source and the recording date of its `rec-` files.
- Size: at most 16 KB per new file, 3 to 5 items per list. No fixture is larger than it must be to
  show its shape.

### What the tests check

- The tests run the app's code, not a copy:
  - `XtreamApiService`'s Json config moves to one internal value (for example `XtreamJson`), used by
    the client and by every test. `XtreamNullFieldsTest` stops copying it.
  - Lists decode as the client does (`decodeToSequence`, then `withName`). One test per list action
    goes through `XtreamApiService` against a local socket that serves the fixture, as
    `XtreamApiServiceDeadlineTest` does.
  - Single items go through `parseItemResponse`, so the test sees Ok, Unavailable or Malformed.
- Each test states the outcome that matters: item count, ids, names, the `XtreamResponse` case,
  epoch times, episode count. Not a full object dump.
- Plain JVM tests. Robolectric only where Android code has no JVM form (PD-Q4).

### Recording

- `scripts/record-provider-fixtures.py`: stdlib Python, like the tools/ servers.
  - Sources: the jellyxtream bridge at `http://localhost:8080` (login, categories, `get_vod_streams`,
    `get_vod_info`, `get_series`, `get_series_info`, and `get_live_*`, `get_short_epg`,
    `get_simple_data_table`, `/xmltv.php` when the library has live channels), and the public
    iptv-org playlist `https://iptv-org.github.io/iptv/index.m3u` (released under the Unlicense;
    check at recording time). No other provider.
  - The login comes from environment variables, never from arguments or files.
  - Output goes to a scratch folder first. The script writes into the fixture folders only after
    the privacy check passes.
- **Trimming:** keep 3 to 5 items per list. Items with different sets of keys go first, so each
  shape the source sends is kept.
- **Cleaning** (the repository is public):
  - Each login value, host name and port given to the script → `user`, `pass`, `provider.invalid`.
    Also their URL-encoded forms.
  - `server_info.url`, `port`, `https_port`, `timestamp_now`, `time_now`, `user_info.exp_date`,
    `created_at` → fixed values.
  - The host of every URL (`stream_icon`, `cover`, `direct_source`, `backdrop_path`, M3U stream
    URLs) → `img.invalid` or `stream.invalid`. The path stays, because the parsers read extensions.
  - Query parameters `api_key`, `ApiKey`, `token`, `X-Emby-Token` → removed.
  - Jellyfin item ids (32 hex characters) → fixed ids.
  - Titles and plots: PD-Q1.
- **Privacy gate:** `scripts/check-fixture-privacy.sh`, run by the recording script and in
  .github/workflows/checks.yml. It fails on:
  - a URL host not on a short allow list (`*.invalid`, `example.com`, the public sports hosts);
  - an IPv4 address other than `127.0.0.1`, `10.0.2.2` and the documentation ranges;
  - `username=`, `password=`, `api_key=`, `token=` with any value except the placeholders;
  - an `Authorization` header.

### Hand-written variants

Each one is a known quirk. The test states the outcome the app must give.

- **Xtream:**
  - Numbers as strings and strings as numbers: `"stream_id":"12"`, `"active_cons":1`, `"max_connections":""`, `"auth":"1"`.
  - `""` in a number field: `"tv_archive":""`, `"rating_5based":""`, `"num":""`.
  - `null` against `""` for the same field.
  - Empty containers: `[]` and `{}` for `info`, `movie_data`, `episodes`, `seasons`, `video`, `audio`.
  - A string where a list belongs: `"backdrop_path":"https://img.invalid/a.jpg"`.
  - A refused login: `{"user_info":{"auth":0}}`.
  - `get_short_epg` with `epg_listings` as `[]`, `{}` or missing, and with base64 that does not decode.
  - Today's `XtreamPayloads` bodies move into `var-` files.
- **XMLTV** (cases from the XMLTV DTD):
  - Times: 14 digits with a space before the offset, 12 digits, no space before the offset, no offset.
  - Text: CDATA, entities, a title in two languages.
  - Structure: a programme without `stop`, a channel without `display-name`, a programme for an unknown channel.
  - A `.gz` file, and gzip bytes at a URL without `.gz`. Phase 4 records what the app does with the second one.
- **M3U:**
  - Encoding and line ends: a byte-order mark, CRLF line ends.
  - Header: no `#EXTM3U`, `x-tvg-url` in the header.
  - Attributes: a comma inside a quoted attribute, an empty `group-title`, `#EXTVLCOPT` lines.

## Phases

| Phase | What | Visible change |
|---|---|---|
| 1 | Fixture layout and loaders; sports files moved; one shared Xtream Json config; the privacy gate in CI; CONTRIBUTING rule ("a parser change adds or updates a fixture") | None |
| 2 | Recording script with trimming and cleaning; first `rec-` Xtream files from the bridge and a trimmed iptv-org M3U; their tests; RUN_GUIDE section "Recording provider fixtures" | None |
| 3 | Xtream `var-` files (lists, items, login, panel guide); fixes for the ones that fail (PD-Q5) | Answers that failed a list or a title now read |
| 4 | XMLTV and M3U `var-` files; the XMLTV test runner (PD-Q4); one `ingestFromStream` test per time form. Waits for AC-5 of the audit cleanups plan | A finding for gzip at a URL without `.gz`, if one shows |
| 5 | TMDB and Jellyfin fixtures (PD-Q3); sync samples read by both sides (PD-Q6) | None |

One commit per phase. Each commit updates this plan (Progress, Status) and the docs it affects.

## Progress

| Phase | Status |
|---|---|
| 1 | Done 2026-11-11 |
| 2 | Not started |
| 3 | Not started |
| 4 | Not started |
| 5 | Not started |

## Done

- **Phase 1 (2026-11-11):** `XtreamJson` (core/player api) is the one Json config for Xtream answers;
  `XtreamApiService` and the five Xtream model tests use it, none copies it. The sports files moved
  to core/network/src/test/resources/fixtures/sports/; `fixture(path)` loads a fixture in core:network
  and core:player, and `sportsFixture` calls it. `scripts/check-fixture-privacy.sh` runs in
  checks.yml. It allows the public sports hosts only under fixtures/sports/, and also checks
  `"username"` / `"password"` JSON fields and URLs with a login in them. CONTRIBUTING has the rule
  "a parser change adds or updates a fixture" (Coding Style).

## Risks

- **A leak in a public repository.** The gate runs in CI. The script writes only after the gate
  passes. Each recording commit gets a manual read of the diff.
- **The bridge is not a panel.** Recorded Xtream files prove the client against our bridge. The
  `var-` files carry the real panel quirks; each test names the source of its quirk (a field
  report or a public format document).
- **Slow tests.** Small files and plain JVM tests keep the added time to seconds. Robolectric only
  for the XMLTV ingest path.
