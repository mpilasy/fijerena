# Catch-up TV Plan

**Status:** In progress — Phase 4 (checks on the emulators). Scope decided 2026-10-10: the full Phases 1–4, Xtream only
(Phase 5 dropped), checked on the emulators only. Per-source differences are detected, not configured
(see "Detected per source"); "Decided while building" overrides the design where they differ.

## Goal

Play a programme that has already aired (or "start over" the one on air) from the guide, on the
channels whose source keeps an archive, as a seekable, pausable stream. Where a source or channel
has no archive, nothing changes: no new buttons, no dead ends.

"Where available" means, per source type:

| Source | Catch-up info it gives | Plan |
|---|---|---|
| Xtream | `tv_archive` (0/1) and `tv_archive_duration` (days) per live stream; `has_archive` per programme in `get_simple_data_table` | Yes — the main target |
| Remote M3U | `catchup`, `catchup-days`, `catchup-source` on `#EXTINF` (and as defaults on `#EXTM3U`) | Yes, after Xtream (Phase 5) |
| Jellyfin | No Live TV support in the app at all | No (Live TV would come first) |
| SMB, Local | No live channels with an archive | No |

## What exists today (2026-10-10)

Nothing plays catch-up, but several pieces are already there.

**Data**
- `XtreamStream.tvArchive` / `tvArchiveDuration` are parsed (`core/player/.../model/XtreamModels.kt:57-62`)
  and stored in `xtream_streams` (`XtreamStreamEntity.kt:30-32`, already in `contentHash`) — no
  schema change needed for them.
- They stop at `XtreamMapper.toDomain` (`core/network/.../XtreamMapper.kt:40`): `MediaItem.providerData`
  carries only `epgChannelId`. Nothing in the UI knows a channel has an archive.
- `EpgProgram.hasArchive` (`has_archive`, `core/player/.../model/EpgModels.kt:18`) is parsed from the
  native Xtream EPG and never read. Programmes built from XMLTV never set it.
- `XtreamServerInfo.timezone` / `timestampNow` (`XtreamModels.kt:167-185`) are parsed on every
  `authenticate()` and never read or stored.
- `M3uParser` reads only `group-title`, `tvg-logo`, `tvg-id`; every `catchup*` attribute is dropped,
  and the `#EXTM3U` header's attributes are ignored.

**Guide**
- XMLTV index (`EpgIndexDatabase`): the importer skips programmes that ended more than **12 hours**
  ago (`EpgIndexer.kt:230-233, 296`). Xtream archives are usually 1–7 days, so most of an archive
  would have no guide entry to pick it from.
- Native Xtream EPG (`get_simple_data_table`) returns past programmes with `has_archive`; cached 6 h
  per stream in `xtream_epg_cache`.
- TV guide (`TvGuideGrid`) scrolls into earlier hours and earlier days (no lower bound); past cells
  are only dimmed. OK opens `ProgramDetailsDialog` with **Watch channel**, which plays the channel
  live — `TvNavHost.kt:798` receives the programme and ignores it. Same on mobile
  (`MobileNavHost.kt:852`, `ProgramDetailsSheet`), whose day chips go from today forward only.
- Search the guide (`EpgBrowserViewModel.kt:613`) searches from now on; past programmes never show.

**Player**
- One flag decides live vs VOD: `isLive = contentType == LIVE_TV` (`StreamLoaderViewModel.kt:267`) →
  `PlayerMetadata.isLive`. Live means: no seek bar, no pause (an external pause becomes `stop()`,
  `StreamingPlaybackService.kt:564`), Up/Down zap, `STATE_ENDED` is an error to retry, recovery
  reconnects at the live edge, health monitor and seamless recycle, live buffer profile, no resume
  position, Recent keyed by channel id after a delay.
- Live TV plays inside `LiveTvSplitLayout` (TV) / the docked mini-player (mobile), not through
  `Screen.Player`, to keep one player connection (the ANR noted in the layout's kdoc).
- `XtreamStreamUrl` and `Redact` already know `/timeshift/<user>/<pass>/<min>/<YYYY-MM-DD:HH-MM>/<id>.ts`,
  so login swapping (`XtreamLoginPicker`, shared logins) and credential masking cover catch-up URLs
  with no change.
- No timeshift URL builder, no programme / start-time argument on any route.

## Facts measured (2026-10-10, bears login `1fb5318f4d`, from the host on the home ISP address)

Login and hosts taken from the TV emulator's sources (`bears` = `cf.bearsp.xyz`, `bearstv` =
`bearstv.online`). A few KB per request (`Range: bytes=0-1023`), no full playback.

- **Archive flags:** 978 of 54,148 live streams have `tv_archive = 1`; `tv_archive_duration` is `3`
  (921) or `7` (57).
- **Time zone:** `server_info.timezone = Europe/Amsterdam`; `time_now` (19:15) minus
  `timestamp_now` (17:15 UTC) = +2 h, which agrees with the name (CEST). The timeshift `start`
  is read in that **panel local time**: a start written as 18:56 played (past in Amsterdam, future
  in UTC); 19:21 and later gave 404 at 19:16 Amsterdam time.
- **Native EPG times are UTC:** in `get_simple_data_table`, the `start` / `end` strings equal
  `start_timestamp` / `stop_timestamp` in UTC (15:15 = 1791645300). Building the URL from the
  `start` string without converting gives a programme 2 h early. Always go from the epoch.
- **`has_archive`:** present and mixed per programme (RTP 1, a 3-day channel: 69 of 187 listings;
  a 7-day channel: 200 of 443). The native EPG reaches back ~4 days and ahead ~5 for both, so a
  7-day archive has only ~4 days of guide from it.
- **URL form depends on the host:** on `cf.bearsp.xyz` the path form (`.ts` and `.m3u8`) and
  `streaming/timeshift.php` all answer (302 to a stream server with a token, then the video). On
  `bearstv.online` all three give **404**. A 404 on every form means "this source has no
  catch-up", not "keep trying the other form".
- **`.ts`:** `206 Partial Content`, `video/mp2t`, `Content-Range: bytes 0-1023/624975872`,
  `Accept-Ranges` set: byte-range seeking works, so bears doesn't need seek-by-new-URL.
- **`.m3u8`:** a VOD playlist (`#EXT-X-PLAYLIST-TYPE:VOD`) of 60 s segments named by start minute,
  so HLS seeks too.
- **Out of range:** a start older than the channel's days (4 days back on a 3-day channel) gives
  404; a start in the future gives 404. A channel **without** archive gives `200` with an empty
  body — treat it as "not available", not as a stream that ended.
- **Connection slot:** the requests take the login's one slot (`active_cons` 1), and it stayed
  taken **~5 minutes** after the last (partial) request (12:17, free at 12:22). A real play that
  closes its connection may free it sooner (live `.ts` frees at once, HLS ~1 min); check in
  Phase 4. Until then, going from catch-up to live on another address can hit 460.

## Design

### Playback mode, not a second boolean

Catch-up needs almost every VOD path (seek bar, pause, `Ended`, VOD retry that seeks back, VOD
buffer, no health-monitor recycle) but none of VOD's watch state: there is no stable item to resume
and a Recent entry keyed by the channel id would collide with the live one.

Add `PlaybackMode { LIVE, CATCHUP, VOD }` to `PlayerMetadata` with `isLive` derived from it, so the
existing `isLive` branches keep working, then review each branch the player report listed
(`StreamingPlaybackService` retry / `reprepare` / `handleStreamEndedOrError` / health monitor,
`AdaptiveLoadControl`, both control overlays, `PlayerKeyHandler`, `MobilePlayerScreen` gestures, PiP,
stats overlays, `NowPlayingSnapshot`, `StreamLoaderViewModel.recordHistory` / `doStopPlayback`). Most
take the VOD side; the list of exceptions is short:

- **Watch state:** no `savePlaybackPosition`, no Recent entry (first version). Leaving and coming back
  starts the programme again.
- **Overlay:** VOD seek bar over the programme; the title line is "programme · channel · day time"
  with a "Catch-up" tag in place of the LIVE dot; a **Live** action jumps to the channel live.
- **Keys (TV):** VOD keys (scrub, play/pause, FF/REW). Up/Down do not zap.
- **End:** `Ended` offers the next programme on that channel if it is in the archive, and **Live**.
- **Now playing (live sync):** channel name + programme title + position/duration.

### Route

Catch-up goes through `Screen.Player` (the VOD route on both apps), never `LiveTvSplitLayout`, with
new optional arguments `catchupStartEpoch: Long?` and `catchupDurationSec: Int?` (`contentType`
stays `LIVE_TV`, `streamId` is the channel). The preview player is stopped before navigating, as the
existing full-screen route already does. `StreamLoaderViewModel` passes the window to the provider.

### Provider API

```kotlin
// ProviderCapabilities
val supportsCatchup: Boolean = false

// MediaProvider
suspend fun resolveCatchupStream(itemId: String, startEpochSec: Long, durationSec: Int): Result<PlayableStream> =
    Result.failure(UnsupportedOperationException())
```

Channel side: the mapper puts `archiveDays` into `providerData` (Xtream: `tvArchiveDuration` when
`tvArchive == 1`; M3U: `catchup-days`), read through one helper `MediaItem.catchupDays(): Int`.
`MediaItem.providerData` already exists for this and needs no schema change.

A programme is playable when all hold:
`supportsCatchup && channel.catchupDays() > 0 && program.end <= now + slack (or on air, for start
over) && program.start >= now - catchupDays*86400 && program.hasArchive != 0-when-known`.
One function, `CatchupAvailability.isPlayable(channel, program, now)` in `core:player/domain`, used
by every screen so they can't disagree. Unit-tested.

### Detected per source, not configured

Each Xtream panel can differ in every way below, so two sources can need different handling. Each
one is detected and kept per source (or per stream), never asked of the user:

| What differs | How it's detected | When |
|---|---|---|
| Which channels have an archive, how many days | `tv_archive` / `tv_archive_duration` per stream | Every catalog sync (already stored) |
| Which programmes are really in it | `has_archive` per programme (native EPG); unknown for XMLTV, so the channel's days decide | Guide load |
| Panel time zone | Each `authenticate()` returns `timestamp_now` (UTC epoch) and `time_now` (panel wall clock). Their difference is the panel's real UTC offset, even when the `timezone` name is missing or wrong. Keep the zone name when its offset agrees (it handles DST inside the archive window); otherwise keep the measured offset | Every login check (no extra request) |
| URL form (path or `timeshift.php`) | On a source's first catch-up play, try the path form; on 404 or a non-video answer, try the php form; remember the one that played | First play, on the user's request only |
| Container (`.ts` / `.m3u8`) | `user_info.allowed_output_formats` (already parsed) plus the source's live `output` | Login |
| Seeking | After prepare, `player.isCurrentMediaItemSeekable` (true when the panel sends `Content-Length` and accepts ranges). If false, seeking uses the "new URL with a later start" path | Each play |

A detected value is a cache: it is re-detected when it fails (the remembered URL form gets a 404, the
offset changes at the next login), never trusted forever. There are no background probes. A probe
would use a connection slot, and bears punishes a second address with HTTP 460, so detection
happens only inside a play the user asked for. Phase 0 still measures one real panel, to confirm the
detection is right, not to hard-code its answers.

### Xtream timeshift URL

Two shapes are in the wild; Phase 0 decides which this source uses:

- `GET /timeshift/<user>/<pass>/<durationMin>/<YYYY-MM-DD:HH-MM>/<streamId>.ts` (path form, the one
  the tests already pin)
- `GET /streaming/timeshift.php?username=…&password=…&stream=<id>&start=<YYYY-MM-DD:HH-MM>&duration=<min>`

The start is in the **panel's local time zone** (`server_info.timezone`), not UTC and not the
device's. That is the single most common catch-up bug in IPTV apps (programmes off by the zone
difference). So:

- Store the detected zone / offset per source when `authenticate()` succeeds (see "Detected per
  source"; a providers-table column, so a `providers.db` migration, or the source's prefs — decide
  in Phase 1). A manual per-source offset in Edit Source only if detection proves wrong somewhere.
- Duration: programme length + a small margin (start 2 min early, end 5 min late), rounded up to
  whole minutes; the player starts at the margin's offset so the programme begins on screen.
- `.ts` by default. Try `.m3u8` only if Phase 0 shows the panel serves it with seeking.

`XtreamApiService.buildTimeshiftUrl(streamId, startLocal: String, durationMin: Int)` builds it;
`XtreamMediaProvider.resolveCatchupStream` wraps it in `XtreamLoginPicker.forPlayback` like live
(catch-up uses a connection slot, so shared logins matter here too).

**Seeking in a `.ts` timeshift:** Media3 seeks progressive TS by byte range only when the server
sends `Content-Length` and accepts `Range`; many panels do neither. Phase 0 checks this. If the
panel cannot seek, seeking re-requests the URL with a later `start` and shorter `duration` (the
"seek by new URL" approach other players use), with the seek bar kept in programme time. This
belongs in the provider/loader, not in `StreamingPlaybackService`.

### Guide

- **Past retention:** the XMLTV import cutoff becomes `max(12 h, largest catchupDays among enabled
  sources, capped at 7 days)` instead of a fixed 12 h. Measure index size and import time on a
  big source first (the reason for the 12 h limit); if it is too costly, keep past programmes only
  for channels with an archive. Native Xtream EPG already returns the past.
- **TV guide:** past cells on an archive channel get a small replay icon (an existing
  `Icons.*Replay`), not a new colour, so dimming still says "past". `ProgramDetailsDialog` gains
  **Watch from start** (past and on-air programmes that are playable), first in the button order
  (entry focus is the useful item); **Watch channel** stays.
- **Mobile guide:** the same in `ProgramDetailsSheet`; day chips gain past days back to the largest
  archive among the visible channels.
- **Player OSD on live:** **Start over** on the on-air programme when it is playable.
- **Later, not in the first version:** past results in Search the guide, catch-up in Recent,
  a channel's archive list in the Live TV panel.

### Remote M3U (Phase 5)

`M3uParser` gets a real attribute reader (all `key="value"` pairs) and keeps `catchup`,
`catchup-days`, `catchup-source`, header defaults included. Supported `catchup` modes:

- `default` / `append`: `catchup-source` is a template (replaced or appended to the URL) with
  `{utc}`, `{utcend}`, `{start}`, `{end}`, `{duration}`, `{offset}`, `{lutc}`, `${start}` and the
  `{Y}{m}{d}{H}{M}{S}` / `{(b)YmdHMS}` style placeholders.
- `shift`: append `utc=<start>&lutc=<now>` to the live URL.
- `flussonic` / `fs`: `…/<name>/archive-<start>-<duration>.m3u8` (or `timeshift_abs-<start>.ts`).
- `xc`: rewrite an Xtream-shaped live URL into the timeshift path above.

Templates are unit-tested against examples from public playlists. `RemoteM3uMediaProvider` has
`supportsEpg = false` today, so M3U catch-up only shows where an XMLTV guide source is matched to
its channels.

## Size and first version

Medium: roughly 30–40 files, 2–3k lines with tests (an estimate). The data and URL work is small;
the cost is the player mode split (every `isLive` branch in the code that keeps live stable) and the
guide work on both apps.

**First version** (about half; still carries the player split, the one risk that can't be skipped):

- Xtream only, from the panel's native EPG (it returns past programmes with `has_archive`), so the
  XMLTV past cutoff stays at 12 h for now.
- **Watch from start** in the TV `ProgramDetailsDialog` and the mobile `ProgramDetailsSheet` only.
- Catch-up plays as VOD without resume or Recent; pause and play; seeking only where the panel
  answers ranges (`isCurrentMediaItemSeekable`).
- Left for later: **Start over** in the live OSD, the replay icon, mobile past-day chips, the
  seek-by-new-URL path, longer XMLTV past retention, Remote M3U.

## Decided (2026-10-10)

- The full Phases 1–4, not the first version. Xtream only: Phase 5 (Remote M3U) is dropped.
- Checked on the emulators only (bears and the bridge from the emulators), not on the Shields.

## Decided while building

These replace the design above where the two differ.

1. **Archive days are looked up by channel id**, not carried in `MediaItem.providerData`:
   `MediaProvider.getCatchupDays(itemIds)` reads `tv_archive` / `tv_archive_duration` from
   `xtream_streams`. Recent and Favourites channels are rebuilt from watch history and favourites
   rows, so a value set by the mapper would be missing exactly there.
2. **The panel clock is kept in memory**, from the `server_info` of each login (`connect()` always
   logs in). No column, no `providers.db` migration. Catch-up needs the panel online anyway.
3. **No `PlaybackMode` enum.** Catch-up plays with `isLive = false`, so every existing `isLive`
   branch already takes the VOD side (seek bar, pause, VOD buffer, VOD retry, no zapping). A
   catch-up window on the loader marks the few places that differ: no watch position, no Recent,
   the OSD title and the **Live** action.
4. **The XMLTV past cutoff stays at 12 h.** Past hours on channels with an archive come from the
   panel's own guide (`get_simple_data_table`), which also says programme by programme what is in
   the archive. Keeping 7 days in the index would multiply it on the TVs for little gain.
5. **One URL form:** the path form `/timeshift/…`, with the source's live output (`m3u8` or `ts`;
   bears seeks in both). A 404 or an empty answer is "This programme is not available to replay",
   with no retries. No `timeshift.php` fallback: on bears every host answers both forms or neither.
6. **No end card for catch-up.** A replayed programme that ends leaves the player, as a film does.
   **Watch live** in the controls replaces the player with the channel's preview (TV) or dock
   (phone). A programme still on air carries on past the point it was asked for (see Phase 2).
7. **No fake `/timeshift/` in the bridge.** The emulators reach bears directly (the host leaves by
   the home ISP address), so catch-up is checked against the real panel.

## Phases

| Phase | What | Status |
|---|---|---|
| 0 | Measure a real panel (bears): `tv_archive` / `tv_archive_duration` values on several channels; `server_info.timezone`; which timeshift form answers (path / php, `.ts` / `.m3u8`); `Content-Length` and `Range` on the answer; whether it takes a connection slot (`active_cons`); `has_archive` in `get_simple_data_table`. Record the facts here like the shared-logins plan does. | Done 2026-10-10 (see "Facts measured") |
| 1 | Data: `getCatchupDays` (by channel id); the panel clock from each login; `supportsCatchup`; `resolveCatchupStream`; `CatchupAvailability` + tests; `buildTimeshiftUrl` + tests (zone conversion around DST) | Done 2026-10-10 (`7013da3e`). Also fixed: the panel's own guide never showed (text times, base64 titles); it now reads `start_timestamp` / `stop_timestamp` and decodes the text. A start-over window past now gets a playlist that ends at now (bears) — Phase 2 continues it |
| 2 | Player: catch-up plays with `isLive = false` (decision 3), `Screen.Player` catch-up arguments, every `isLive` and Live-TV content-type branch reviewed; a start-over that reaches now fetches the window again and carries on; "not available to replay" with no retries; overlay title / Live action / end card. Bridge: a fake `/timeshift/` in `tools/jellyfin-xtream/xtream_bridge.py` (serve a Jellyfin item from an offset, `tv_archive: 1`) so the emulators can test without a real panel | Done 2026-10-10 (`6613f53f`); checked on the emulators in Phase 4 (nothing opens catch-up before Phase 3) |
| 3 | Guide: past hours on archive channels from the panel's guide (decision 4), replay icon, **Watch from start** on TV and mobile, mobile past-day chips, **Start over** in the live OSD; strings in `values` / `-fr` / `-mg`; focus walks updated | Done 2026-10-10 (`cdd1f05e`); checked on the TV emulator. Found and fixed while checking: the player hand-off between screens (see AGENTS.md → "Handing the player to another screen"), a stale index hiding the programme on air, the replayed programme's description picked by time from the other guide |
| 4 | Check on the emulators against the bridge, bears from the TV and phone emulators (decision 2026-10-10: emulators only); docs (`NAVIGATION_GUIDE.md`, `DATABASE_SCHEMA.md` if the zone is a column, `RELEASE_NOTES.md`) | In progress |
| 5 | Remote M3U catch-up (attribute reader, modes, templates) | Dropped 2026-10-10 (Xtream only) |

## Phase 4 checks (2026-10-10, emulators, bears)

TV emulator (Television_1080p), source `bears`, PT: RTP 1 HD (3-day archive):

- Start over from the live controls: `/timeshift/…/277/2026-10-10:17-28/386405.m3u8` — 10:28 on the
  emulator's clock (UTC−5) for a programme at 10:30, so the panel clock is right. Ready in 20–25 s
  (bears' timeshift is slow to answer; live starts in 1–3 s). Controls: "CATCH-UP · Estrelas ao
  Sábado · PT: RTP 1 HD ◉ · Today 10:30 AM", seek bar, Watch live first, no Favorite.
- Seek: fast-forward to 3:30 and OK — plays from 3:30. The length grew (2:54 → 3:02) as the
  programme on air was asked for again.
- Watch live: back on the preview, live ready in 1.8 s. Back from catch-up: live again in 3 s.
- Guide, yesterday: RTP 1 HD / 2 HD / 3 HD past programmes carry the replay mark, plain RTP 1
  doesn't. Watch from start is the focused button; "Bom Dia Portugal" played from
  `…/247/2026-10-09:06-58/…` with the whole 4:07 window. Today's page showed "No listings": the
  emulator's XMLTV guide was two days old (today isn't filled from the source's guide, by design).
- Not checked on a device: "This programme is not available to replay" (bearstv, the host that
  answers 404, can't log in on the emulator: "Session expired"); reaching the end of a programme.

## Risks and open questions

- **Time zone:** the panel's `timezone` may be wrong or missing; the zone conversion must handle
  DST changes inside the archive window. Phase 0 compares a known programme against what plays.
- **Seeking:** if the panel neither serves ranges nor answers a shifted `start` accurately, the
  first version ships with pause and play-from-start only, and says so.
- **EPG index size:** 7 days of past programmes for a large XMLTV feed may multiply the index; the
  cutoff change is measured before it ships (mid-range TVs, `EpgIndexer` import time).
- **Connection slots:** with one stream per login, catch-up on one device competes with live on
  another exactly as live does; nothing new, but test with shared logins.
- **Archive flag vs reality:** some panels set `tv_archive = 1` on channels whose archive is empty.
  An empty or failing timeshift answer gets a clear "This programme is not available to replay"
  error, not the generic stream error, and no retries.
