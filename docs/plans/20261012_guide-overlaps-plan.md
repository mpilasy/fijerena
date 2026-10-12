# Overlapping guide listings

**Status:** Phase 1 in progress (2026-10-12).

## Problem

bears' `xmltv.php` (downloaded 2026-10-12): 236,692 programmes on 3,605 channels, of which
**52,200 (22%) overlap the next programme on their own channel** (1,640 channels), 8,546 share
their start with another, 787 run over 6 h and 157 over 12 h. Two patterns:

1. **Stretched entries over a normal schedule.** beIN Sports 6 lists "Philadelphia Eagles @
   Jacksonville Jaguars" `20261011183000 +0200` → `20261012220000 +0200` (27.5 h) and "49ers @
   Seahawks" `20261012012500` → `20261012220000` (20.5 h), on top of Italy vs Türkiye 21:30–22:00,
   Vikings @ Saints 22:00–01:25 and sane replays of both games the next day. Search shows both as ON
   AIR at 23:10 the next evening; the grid stacks cards.
2. **One entry for the night.** CRTV "Journal télévisé bilingue" 01:00–07:00, India News MP
   "Ground Report" 19:30–01:30 and 01:30–08:00. No overlap: nothing else is listed overnight.

The panel's own guide (`get_simple_data_table`) has the same overlaps (CNN Portugal, Fri 9: two
schedules for one evening).

## Decision

- **Clip each programme at the next start on its channel** (same guide source): where a later
  programme starts before it ends, it ends there. Programmes sharing a start are both clipped at the
  next *later* start. Done once per guide source as it is ingested — one SQL `UPDATE` on the rows
  just written (`epg_programme_staging` on the staging path, `epg_programme` after the stale-row
  delete on the direct path) — so the grid, search, now/next and catch-up all read clean times with
  no extra query. The panel's own guide gets the same rule where `XtreamEpgManager` hands it out.
  A real programme that runs past a short insert ends early; that is far closer than 27 hours.
- **Show the end's day when a programme ends on another day** than it starts, in Search the guide
  ("18:25 – Mon 15:00"): pattern 2 can't be fixed from the data, and the end without its day read as
  ending before it started.
- Existing guides are cleaned at their next ingest (a changed file, or the daily forced re-ingest).

## Phases

| # | What | Status |
|---|---|---|
| 1 | Clip at ingest (`EpgIndexDao`, `EpgIndexer.clipOverlaps`, `EpgFileManager.ingestDownloadedSource`) and in the panel guide (`XtreamEpgManager`); end-day text in `formatAiringTime`; unit tests | In progress |
| 2 | Check on the phone emulator against bears after a guide refresh (beIN Sports 6, search "nfl"); docs | |
