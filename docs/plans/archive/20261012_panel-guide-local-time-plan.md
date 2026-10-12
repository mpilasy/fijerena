# Panel guide times labelled as UTC

**Status:** Done 2026-10-12. Checked on the phone emulator against bears.

## Problem

An Xtream panel's own guide (`get_simple_data_table`, one channel per call) can stamp its local
wall time as if it were UTC. bears (`cf.bearsp.xyz`) does: its clock is Europe/Amsterdam, and for
Flamengo x Fluminense (kick-off 20:30 UTC) it answers

| | Raw | Real |
|---|---|---|
| `xmltv.php` | `20261011204500 +0200` | 18:45 UTC |
| per-channel `start` | `2026-10-11 20:45:00` | Amsterdam wall time |
| per-channel `start_timestamp` | `1791751500` (= 20:45 **UTC**) | 2 h late |

The app reads both correctly; the panel mislabels one. Measured on the phone emulator: every
channel both feeds cover (6, incl. the catch-up channels RTP 3 and TVI) is +2 h on the per-channel
side, and a CNN Portugal replay showed the archive follows real time (requested 22:58 UTC, CNN's
on-screen clock read 23:59 Lisbon = 22:59 UTC). So listings from the per-channel guide on bears sit
2 h late in the guide, and Watch from start on one of them replays the wrong 2 hours.

Where that guide is used: past days on catch-up channels before the XMLTV index's first programme
(bears' `xmltv.php` starts yesterday, its archive keeps 3 days), a whole guide page when the index
has nothing for its channels, and the player's catch-up title / Start over when the index has no
programme at that time (`MediaRepository.withArchivePast`, `getGuideForItemsInWindow`,
`StreamLoaderViewModel`).

## Decision

Detect it per source, never assume it, and never use a fixed offset:

- The panel's offset is already measured at each login (`XtreamPanelClock`, `time_now` −
  `timestamp_now`, with the zone name when it agrees, so DST is handled).
- A panel guide that labels local time as UTC is wrong by exactly that offset. So compare its
  listings with the panel's own `xmltv.php` in the index (the automatic "<host> (Bulk)" source),
  same channel and title, under two hypotheses: the start equals the index's start (**correct**),
  or the start read as panel wall time equals it (**local as UTC**). Exact matches only — both feeds
  come from the same panel database, so a real match is to the second.
- Verdict: one hypothesis with at least 20 matches and at least 4× the other wins; anything else
  leaves the stored verdict unchanged. Stored per source (its `xtream_cache_<id>` preferences),
  re-measured on every fresh per-channel answer, so a provider that fixes its feed (or a DST change)
  is picked up.
- When the verdict is **local as UTC**, every per-channel listing's start and end are read as panel
  wall time (per listing, so a DST change inside the archive window is right). Applied when the
  guide is read, not when cached: the cache keeps the panel's raw answer.

Added while checking: until a comparison reaches a verdict in a process, cached answers are compared
too, so an update or a restart doesn't wait for the 6-hour cache to expire.

Not covered, on purpose:

- **`xmltv.php` mislabelled instead** (the mirror case): not seen on a real panel. The comparison
  would find neither hypothesis and change nothing.
- **Guide sources the user added**: never compared or shifted; they keep the manual Time correction
  (its UI rework is item 5 of the Everyday UX fixes plan, on hold).
- **A source without its own `xmltv.php` indexed** (automatic guide off, or the panel has none):
  nothing to compare, so its per-channel times stay as sent.
- **`has_archive` flags**: computed by the panel from the same mislabelled times, so they lag 2 h.
  `CatchupAvailability` already distrusts a recent `has_archive = 0`; no change.
- **A panel at UTC**: the two hypotheses are the same, so there is nothing to detect.

## Phases

| # | What | Status |
|---|---|---|
| 1 | `PanelGuideTimes` (pure: the verdict and the correction) with unit tests; `XtreamEpgManager` applies the stored verdict to what it returns and re-measures after each fresh fetch against the provider's own `xmltv.php` rows in the index (`OwnXmltvIndex`) | Done 2026-10-12 |
| 2 | Check on the phone emulator against bears: catch-up channels 2–3 days back line up with XMLTV, a replay from a per-channel listing lands on its programme (on-screen clock), jellyxtream unchanged; docs (FEATURES, epg_guide, release notes) | Done 2026-10-12 |

## Phase 2 checks (phone emulator, bears, 2026-10-12)

- Log on the first guide page after a restart: `Provider 1: panel guide labels local time
  (Europe/Amsterdam) as UTC = true`; stored as `panel_guide_local_as_utc` in `xtream_cache_1`.
  jellyxtream (`xtream_cache_5`) has no verdict and is unchanged.
- Replay from a corrected per-channel listing two days back: CNN PT 4K, Fri 9 "CNN Meia Noite"
  at 10:06 PM CDT (04:06 Lisbon). The app asked for 04:04 (lead-in); CNN's on-screen clock read
  04:05 over the programme's opening titles. Uncorrected, the listing sat at 12:06 AM and would
  have replayed 06:04.
- Overlapping cells on that evening are in bears' own answer (two schedules: Contrapoder 01:52 and
  "7 de outubro" 01:55 UTC, CNN Meia Noite at 03:01 and 03:06), not from the correction.
