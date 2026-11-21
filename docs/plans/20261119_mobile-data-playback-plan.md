# Mobile data playback plan

**Status:** Phases 1 and 3 done 2026-11-19 (the user chose to do the four network fixes before the
logs). Phase 2 (logs from a phone on mobile data) waits for the phone and now checks the fixes; a
live TS buffer of about 10 s stays open until then.

## Goal

Live TV and films play on mobile data as well as they play in other Xtream players (iMPlayer is
the reference). On Wi-Fi nothing changes.

## What we know (2026-11-19)

- On mobile data most live channels never start with MPEG-TS (`ts`). With HLS (`m3u8`) a channel
  plays a few seconds, stops, and plays again.
- A screenshot of the phone's Stats with HLS on mobile data: speed 3.5 Mbps, bandwidth 2.9 Mbps,
  1–2 s buffered, position −4 s in a 60 s live window. The player fell behind the live window and
  the app had no recovery for that ("Behind live window").
- The stream format setting (Edit Source → Advanced) changes Live TV only. Films and episodes use
  the file's own extension. Catch-up always uses HLS.
- Tried before: Cronet (HTTP/3; servers answered 405, removed), a rewrite of every live `.ts` to
  `.m3u8` on mobile data (February 2026; 404 on servers without HLS, removed the next day), larger
  mobile buffers and a buffer multiplier (removed).

What other open-source Xtream players do differently (StreamVault, OwnTV, OpenTV, IPVIO-Stream,
Kodi pvr.iptvsimple, M3UAndroid):

1. They find a stall by frames or position, not by buffer depth. Ours also reconnects a live
   stream when less than 8 s is buffered for 20 s (`StreamHealthMonitor`).
2. They keep the live MPEG-TS buffer at about 8–10 s (ours: 50 s on mobile data).
3. Their streaming OkHttp client keeps `retryOnConnectionFailure` on (ours: off), so a pooled
   socket that the carrier or the panel closed does not fail the next request.
4. They use HLS for Xtream live, offer it as a setting, or swap `.ts` and `.m3u8` when a channel
   fails.
5. Their connect timeouts are 5–15 s (ours: 45 s on mobile data).

## Phases

| Phase | What | Status |
|---|---|---|
| 1 | Auto live stream format, HLS fallback, behind-live-window recovery | Done 2026-11-19 |
| 2 | Logs from a phone on mobile data (read only), now also a check of Phases 1 and 3 | Waiting for the phone |
| 3 | Network fixes (items 1–3 and 5 above) | Done 2026-11-19; item 2 (10 s live buffer) waits for Phase 2 |

### Phase 1: Auto live stream format (done 2026-11-19)

- Edit Source → Advanced → Stream Output Format has **Auto** (new default), m3u8 and ts. Auto uses
  m3u8 on mobile data when the server lists it in `allowed_output_formats` (the login answer), and
  ts everywhere else (`LiveStreamFormat`). The format is chosen when the channel starts; a network
  change during playback does not swap it.
- Auto is its own flag (`ProviderSettings.autoStreamFormat`, default on), not a third value of
  `streamOutputFormat`: older versions read that field from synced settings and would build an
  address ending in `.auto`. Every source saved before the flag decodes as Auto (the user's
  choice, 2026-11-19). No database change.
- A live HLS stream that the panel refuses before the first picture (HTTP 4xx other than the
  account-busy codes, or a playlist that does not parse) plays again as ts. When the ts stream
  then plays, Auto gives that panel ts until the app restarts. A refusal that ts does not fix
  either (a blocked address, such as a VPN's, or a busy account) leaves the panel on HLS
  (2026-11-20: one 403 behind a VPN had put a source on ts, which then never played on mobile
  data).
- A live HLS stream that falls behind the live window plays on from the live edge (Media3's
  `seekToDefaultPosition()` and `prepare()`), with no error and no retry count.
- `XtreamStreamUrl.sameStream` treats a channel's `.m3u8` and `.ts` addresses as the same stream,
  so a fallback does not stop the channel from going into Recent.
- `NetworkMonitor` starts with the app, so the first channel already knows the network type.

### Phase 2: logs from a phone on mobile data

Connect the phone by USB, Wi-Fi off, mobile data on. Read only:

1. Download a live stream from the phone's shell, without the app, to separate the network and
   the server from the app.
2. Start a channel in the app (ts, then Auto) and read logcat and Diagnostics: the error, the HTTP
   status, the timing, reconnects, busy answers (456/458/460).

### Phase 3: network fixes

- **Done 2026-11-19:** `retryOnConnectionFailure(true)` on both streaming clients
  (`NetworkModule`). It was off since June 2026 so that OkHttp's retries would not hide failures
  from Media3's retry count; a pooled socket that the carrier closed while idle then failed the
  next request at once.
- **Done 2026-11-19:** `LiveDropDataSource` wraps the HTTP source of a live progressive (non-HLS)
  stream. A read failure or an end of the stream closes the connection at once and asks the
  service for the health check's reconnect: a fresh start at the live point that keeps the last
  frame, counted with the other reconnects (fast tier, slow tier, then the error). The load waits
  up to 5 s for that start to cancel it; otherwise the drop goes to the player as before.
  - A first version reopened the same request inside the source, keeping the player's timeline.
    The emulator check showed the flaw: a server that restarts the channel's encoder (the fake
    server's ffmpeg per connection; Xtream panels with on-demand channels can do the same) sends
    timestamps from zero again, and the player waited for them until the screen gave up.
  - Fixed 2026-11-20: a stop (the viewer left the screen) or a new channel cuts the old
    connection, which read as a drop, and its reconnect started the stopped channel again with no
    screen on it. A drop now reconnects only while the player still plays that stream
    (`StreamingPlaybackService.isStillPlaying`).
- **Done 2026-11-19:** the health check's reconnect (`performSeamlessRecycle`) starts a live
  stream at the default position (the live point), not at the old position. On a fresh live TS
  stream, which can't seek, the old position made the player open it and then load it again from
  the start while the first connection was still closing: two connections for about 0.5 s on each
  reconnect (seen on the emulator with `ss`), which a 1-connection account can refuse. Now one
  connection closes before the next opens. Films keep their position.
- **Done 2026-11-19:** `StreamHealthMonitor` has no buffer-depth rule. A live stream reconnects
  when the picture stops while playing, or after 10 s with no data while buffering
  (`StallWatchdog.quietMs`, counted only after data has come, so a slow channel start is not a
  stall), or after 20 s of many dropped frames. A stall resets the "healthy since" time, so
  repeated stalls still escalate to the slow tier and to giving up.
- **Done 2026-11-19:** `CELLULAR_CONNECT_TIMEOUT_MS` 15 s (was 45 s), as on Wi-Fi.
- Open, after Phase 2: a live TS buffer of about 10 s (OwnTV: panels cut a TS socket that the
  player stops reading).
