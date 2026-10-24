# Loading indicator

**Status:** Built 2026-10-24 (Phases 1–5). Checked on the TV and phone emulators: films (iris), episodes (reel), the start-up wait with the banner (TV), a stall mid-stream with the last frame and the scene colour (phone), the live ring with the channel logo (phone). The TV tuning ring was checked later the same day (preview and full screen).

## Goal

The player stops writing "Buffering…" and "Tuning · <channel>". A wait shows a moving picture that
also tells how far the wait is, says what is loading, and keeps the last frame on screen when a
stream that already played waits again. Expert mode keeps its buffering details under the picture.

## Decisions (2026-10-24)

- **LI-D1 One indicator per kind of content:**
  - Films: an **iris**. Its blades turn and open as the buffer fills (`bufferedMs / neededMs`).
  - Episodes: a **film reel**. It turns at a speed that follows the download speed.
  - Live TV and catch-up: a **ring** that fills as the buffer fills, with the channel logo in it
    (the short channel name when there is no logo).
  - All three spin without progress until the player knows how much it needs, and turn to the
    warning colour when the connection is slower than the stream needs (`BufferingInfo.tooSlow`).
- **LI-D2 What is loading, top left:** during a wait, the title's logo (film, show) or the
  channel logo shows at the top left; the stream or channel name as text when there is none.
  On TV the stream info banner already holds that logo, so while the banner is up it is the badge
  (the banner gets the name as text when there is no logo).
- **LI-D3 Stream info banner (TV):** at the start of a stream and on a channel change, the banner
  stays up for the whole wait, and its 3 s start only once the stream plays. A wait in the middle
  of a stream does not open it.
- **LI-D4 No black mid-stream:** when a stream that already played waits or reconnects, its last
  frame stays on screen, not dimmed. The indicator takes a colour from that frame that stands out
  against it. A new title or channel still starts from black (never the old channel frozen).
- **LI-D5 Text stays for screen readers:** "Buffering" and "Tuning <channel>" become the
  indicators' content descriptions. "Preview didn't start" stays as text: it says what to do.

## Progress

| Phase | What | State |
|---|---|---|
| 1 | Shared parts (core/ui): `WaitIndicator` (iris, reel, ring), `PlayerWait`, `StreamBadge`, `rememberSceneTint`; unit tests | Done |
| 2 | TV and phone players use them; TV tuning overlay becomes the ring | Done |
| 3 | Stream info banner waits for play (TV) | Done |
| 4 | Last frame kept mid-stream; no dimming | Done |
| 5 | Docs, checks, emulator check, release | Done |
| 6 | Live TV preview (TV) and phone dock use the ring; full-size ring in the TV preview | Done |

## Notes (2026-10-24)

- **Phone controls:** the indicator is always at the centre of the screen (user, 2026-10-24). While
  the controls are up it is smaller (`WaitStyle.liftedIndicatorSize`) and without the Expert
  details, and their Play/Pause row moves down under it (`controlsDrop`). The Expert details hang
  below the indicator, so they never push it off the centre (TV tuning ring too).
- **Scene colour:** the hue comes from the whole frame, the light or dark from its middle third
  (behind the indicator), so black side bars don't make a light face count as dark.
- **New stream vs reconnect:** `StreamingPlaybackService.newStreamStarting` is set by `playStream()`
  and `stop()`, cleared by the first frame; while set, the surface shows Media3's black shutter.
  A reconnect (`reprepare()`, a recycle, a film freed while paused) leaves it clear, so its frame stays.
- **Preview and dock (Phase 6):** the TV preview's 48 dp ring made a wide logo (RTP 1) too small to
  read, so the preview uses the full-size ring. The phone dock showed black (portrait) or the old
  spinner (landscape) while it loaded; both show the ring now, as does the TV preview's wait after
  the channel played.
- **Known, not from this work:** on the TV emulator, going full screen from the Live TV preview can
  stall with the buffer full and no picture. 0.7.4 does the same, so it is older than this plan.
