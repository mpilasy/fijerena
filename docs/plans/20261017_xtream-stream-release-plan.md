# Xtream stream release

**Status:** Built 2026-10-17 (Phases 1–5); checked on the emulators; on the Shields and the Bravia since 2026-10-17, not yet on the Xperia or phone; Phase 3 not yet seen against an HTTP/1.1 Xtream panel.

## Goal

A closed or idle stream never keeps an Xtream connection slot ("max connections") and never
reopens one on its own. On a 1-connection account a slot held by a stream nobody is watching
makes the next stream fail as "account busy".

## Audit findings (2026-10-17)

Read-only audit of `StreamingPlaybackService`, `PlaybackViewModel`, the player screens and the
stream data sources. Teardown itself is sound (each release stage guarded, account-busy login
switch dropped after a stop, recycles and retries cancelled on stop, PlayerView detached,
controllers released). What can keep or reopen a slot:

1. **Phone: a stopped stream reopens on a session Play.** `stop()` keeps the media item so a
   headset Play resumes it; a Bluetooth button, a car's auto-play on connect or the shade's media
   controls reopen the stream with no screen, and nothing stops it again.
2. **Auto-retry after a drop restarts a stream nobody watches.** When connectivity returns, the
   service replays a stream in Error without asking whether a screen still wants it. A
   `stop()` racing the posted "recovery exhausted" handler (not cancelled by `stop()`) leaves it
   in Error; on the phone the service outlives the screen, so a later blip reopens the stream.
3. **Closing a stalled stream holds the socket up to 30 s.** The loader thread blocks in an
   OkHttp read that ignores the stop until the read timeout (30 s); the panel counts it.
4. **A queued retry overrides a pause.** The retry sets `playWhenReady = true`; pausing during a
   reconnect (or the phone's background pause) plays again and holds the slot.
5. **A paused film holds its connection.** Progressive loading blocks with the request open once
   the buffer is full; a pause has no limit in the app.

Not Xtream-specific, left for later: the Jellyfin stop report is skipped when a history write
throws, and on TV Home / phone live background; the service's wake lock stays held after Ended
and final errors (phone, backgrounded on a finished film) — fixed 2026-10-17: released on Ended and
on every final error, taken again on the next play.

## Decisions (user, 2026-10-17)

- Do all five; one commit per phase.
- Finding 1: clear the stream from the session when playback is stopped. Headset/car resume after
  leaving the player is gone; play/pause on the player is unchanged.
- Finding 5: a film paused for 5 minutes frees its connection; Play reopens it where it was.
- Not in parallel: every phase edits `StreamingPlaybackService.kt`.

## Phases

### Phase 1 — no auto-retry after a stop (finding 2)

- The "recovery exhausted" handler is a named runnable; `stop()` and teardown remove it.
- `stop()` marks the stream as not to be auto-retried (`autoRetryAttempted = true`); only
  `playStream()` re-arms it.
- Tests: a stop before the posted handler runs leaves Idle; connectivity back after a stop
  plays nothing.

### Phase 2 — retries wait for Play (finding 4)

- A retry or account-busy re-attempt that comes due while the player is paused is held
  (`deferredRetry`) instead of reopening the stream; the next Play (in-app or session) runs it.
- `stop()`, `playStream()` and teardown drop a held retry.
- Tests: a retry due while paused opens nothing; Play runs it; a stop drops it.

### Phase 3 — stop closes the stream's socket now (finding 3)

- The streaming data source's calls go through a tracking `Call.Factory`; after `player.stop()`
  (stop, new stream, teardown) every tracked call is cancelled, off the main thread, which
  closes its socket even mid-read.
- Order: after `player.stop()`, so the player drops the cancelled load instead of reporting it.
- Tests: the factory cancels calls it made and none it didn't.

### Phase 4 — a stopped stream can't be reopened from the session (finding 1)

- `stop()` clears the player's media items after stopping. In-app restarts already go through
  `playStream()` with the metadata, never the kept item.
- Tests: `stop()` clears the items.

### Phase 5 — a paused film frees its connection after 5 minutes (finding 5)

- Pausing a non-live stream schedules a release in 5 minutes; Play cancels it. When it fires:
  `player.stop()`, the screen keeps showing Paused at that position, and the next Play reopens
  the stream there (through Phase 2's held retry).
- Tests: the release stops the player and keeps Paused; Play reopens at the position; Play
  before 5 minutes cancels it; live is not released.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. No auto-retry after a stop | Done 2026-10-17 | a5be3ef3 |
| 2. Retries wait for Play | Done 2026-10-17 | fbb7db06 |
| 3. Stop closes the socket | Done 2026-10-17 | b6c2f8cc |
| 4. Clear the session on stop | Done 2026-10-17 | 3ec396cf |
| 5. Free a paused film | Done 2026-10-17 | 0eae8630 |

## Checked on the emulators (2026-10-17)

- Phase 5 (TV, jellyxtream, The Matrix): paused 16:32:03, released 16:37:00 ("Paused for 5 min…"),
  the screen stayed Paused at 5:52; Play reopened at 5:52.
- Phase 4 (phone, The Godfather): after leaving the player the session is empty (NONE);
  `cmd media_session dispatch play` and a MEDIA_PLAY key left it stopped; opening the film again
  played from its saved position.
- Phase 3: Jellyfin behind Cloudflare speaks HTTP/2, one TCP connection shared by every request,
  so the cancel resets the stream inside it and the socket stays (resume reused it). Xtream panels
  on plain HTTP/1.1 get their socket closed; not seen on one (no test panel on the emulators).
- Phases 1–2: unit tests only (a network drop during a stop and a reconnect during a pause are
  not reproducible on demand).
