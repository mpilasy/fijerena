# Catch-up TV Plan

**Status:** Exploration done, not started (written 2026-10-10). Phase 0 (measuring a real panel) is
needed before any code: the timeshift URL shape and the server's time zone differ between panels.

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

### Xtream timeshift URL

Two shapes are in the wild; Phase 0 decides which this source uses:

- `GET /timeshift/<user>/<pass>/<durationMin>/<YYYY-MM-DD:HH-MM>/<streamId>.ts` (path form, the one
  the tests already pin)
- `GET /streaming/timeshift.php?username=…&password=…&stream=<id>&start=<YYYY-MM-DD:HH-MM>&duration=<min>`

The start is in the **panel's local time zone** (`server_info.timezone`), not UTC and not the
device's. That is the single most common catch-up bug in IPTV apps (programmes off by the zone
difference). So:

- Store `server_info.timezone` per source when `authenticate()` succeeds (a providers-table column, so
  a `providers.db` migration, or the source's prefs — decide in Phase 1). Fallback when missing or
  invalid: UTC, plus a per-source minutes offset in Edit Source only if Phase 0 shows it's needed.
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

## Phases

| Phase | What | Status |
|---|---|---|
| 0 | Measure a real panel (bears): `tv_archive` / `tv_archive_duration` values on several channels; `server_info.timezone`; which timeshift form answers (path / php, `.ts` / `.m3u8`); `Content-Length` and `Range` on the answer; whether it takes a connection slot (`active_cons`); `has_archive` in `get_simple_data_table`. Record the facts here like the shared-logins plan does. | Not started — needs the user's login |
| 1 | Data: `archiveDays` into `providerData` (Xtream mapper); store the panel time zone; `supportsCatchup`; `CatchupAvailability` + tests; `buildTimeshiftUrl` + tests (zone conversion around DST) | Not started |
| 2 | Player: `PlaybackMode`, `Screen.Player` catch-up arguments, `resolveCatchupStream`, every `isLive` branch reviewed; seek-by-new-URL if Phase 0 says so; overlay title / Live action / end card. Bridge: a fake `/timeshift/` in `tools/jellyfin-xtream/xtream_bridge.py` (serve a Jellyfin item from an offset, `tv_archive: 1`) so the emulators can test without a real panel | Not started |
| 3 | Guide: past retention, replay icon, **Watch from start** on TV and mobile, mobile past-day chips, **Start over** in the live OSD; strings in `values` / `-fr` / `-mg`; focus walks updated | Not started |
| 4 | Check on the emulators against the bridge, then bears on the Shield and a phone; docs (`NAVIGATION_GUIDE.md`, `DATABASE_SCHEMA.md` if the zone is a column, `RELEASE_NOTES.md`) | Not started |
| 5 | Remote M3U catch-up (attribute reader, modes, templates) | Not started; only if a playlist in use has `catchup` attributes |

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
