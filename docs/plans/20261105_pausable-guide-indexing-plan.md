# Pausable Guide Indexing Plan

**Status:** Done 2026-11-05 (IX1–IX4).

## Why

On the Chromecast HD (about 1.4 GB of RAM, slow storage), the app froze with "not responding" at
13:44 on 2026-11-05 while the user watched Live TV. The ANR trace showed the main thread stalled on
memory that had to come back from swap, with I/O pressure at 77%. The job history showed a guide
job that had run for 20 minutes at that moment and ran about 25 minutes more after the restart.

Every guide refresh rebuilds the whole search index (`epg_programme_fts`) from scratch, for every
programme of every guide source, in one SQLite statement, in one transaction with the guide swap
(`EpgIndexer.swapAndRebuildFts`). On the Chromecast with the bears guide that takes about 8
minutes each time. It can't stop: an interruption rolls it all back and the next run starts again.

The user decided (2026-11-05):

- Never drop descriptions from the index.
- Index only what a refresh changed (decision 1).
- Indexing doesn't run while video plays, on all devices; it continues once nothing has played for
  1 minute (decision 3, option A, without a low-memory condition).
- Downloading and importing still run during playback.

## Design

### Incremental index, in batches (staging path)

A refresh run imports its sources `S` into staging as today. Then, in place of
`swapAndRebuildFts(S)`:

1. **Remove** (batches, can pause): for the live rows of `S`, and for the rows that the swap will
   delete as ended (`end_epoch < cutoff`), delete their entries from `epg_programme_fts`. The
   content rows still exist, which an external-content FTS4 table needs to delete an entry. 5,000
   rows per batch, one transaction each.
2. **Swap** (one short transaction): `executeSwap(S)` and the ended-programme delete, with the FTS
   triggers dropped (they are already dropped during the run). Then the sources are marked ingested
   in the settings database, as today.
3. **Add** (batches, can pause): insert the FTS entries of the new rows of `S`, those with an id
   above the highest id before the swap. Programme ids only grow (`AUTOINCREMENT`).
4. **Merge** (steps, can pause): `INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('merge=…')`
   until it has nothing left to merge, in place of one `optimize` over the whole index.

Each batch commits its work and its progress together, in a small table in `epg_index.db` that Room
doesn't manage (`epg_fts_progress`: phase, sources, cursor, cutoff, boundary id). So a batch is
never done twice: deleting an FTS4 entry twice would damage the index.

The non-staging path (low storage, writes straight into the live tables) keeps its full rebuild.
So do the other full rebuilds (orphan purge, crash recovery when the progress is unknown).

### Pause while video plays

Before each batch, the indexer asks whether video is playing or buffering (the check the import
already uses, `EpgFileManager.isPlaybackActive`). While it is, and for 1 minute after it was last
seen, the indexer waits, checking every 5 seconds. Then it continues with the next batch. A batch
already started finishes; a batch takes about a second or two even on the Chromecast. The 1-minute
delay covers channel changes, reconnects and preview-to-full-screen, which stop playback for a
moment.

The refresh run waits with it, inside the WorkManager job and its foreground service, as the FTS
rebuild does today (it must stay inline in `doWork`, see the wake-lock note). The card shows
"Indexing…" and, while it waits, "Indexing waits until playback stops". The index time on the card
counts only the time spent indexing, not the time waiting.

### Search while sources wait for their index

From the start of Remove to the end of Add, the sources of `S` are "pending". Guide search (and the
sports guide lookup) uses the FTS index for the other sources, and a plain text search in **titles
and descriptions** for the pending ones. Results stay correct, only slower for those sources. The
programme grid doesn't use the index, so it shows the new guide right after the swap.

### After a kill

`initialize` reads `epg_fts_progress`:

- **Remove** not finished: the swap never happened. The removed entries can't be put back cheaply,
  so this case falls back to a full rebuild (the existing `EpgFtsRebuildWorker`), and the staging
  rows of `S` are cleared; the next refresh imports them again. Rare: Remove is the shorter part.
- **Add** or **Merge** not finished: the worker continues from the cursor, pausing during playback
  as above.

## Progress

| Phase | What | Status |
|---|---|---|
| IX1 | Incremental Remove / Swap / Add / Merge in batches, progress table, pause while playing, pending-source search (titles and descriptions), card text, index time without waits | Done 2026-11-05 |
| IX2 | Resume after a kill: Add and Merge continue from the cursor; Remove falls back to a full rebuild | Done 2026-11-05 (with IX1) |
| IX3 | Docs (FEATURES, RELEASE_NOTES), TV emulator walk, Chromecast measurement | Done 2026-11-05 (walk with a generated 200,000-programme guide: bearstv 404 and xstream4k 513 from the PC); released 0.9.20261105231019 |
| IX4 | Indexing also waits while another app plays sound (YouTube, a game); on a device with under 2 GB of RAM the automatic refresh runs only when the device is idle | Done 2026-11-05 |

## IX4: other apps, small devices

The user asked (2026-11-05) what happens when YouTube or Lemuroid is in front and a guide refresh
starts on the Chromecast. Indexing only knew Fijerena's own player. Two changes, both decided by the
user:

- Indexing waits while anything plays: Fijerena's video, or sound from any app
  (`AudioManager.isMusicActive()`, no permission), checked before each batch as before.
- On a device with under 2 GB of RAM (`EpgFileManager.isLowMemory`: the Chromecast HD has 1.4 GB,
  the Shields and the Bravia 3 GB), the automatic periodic refresh also requires the device to be
  idle (WorkManager `setRequiresDeviceIdle`: the TV off or asleep), so its download and import never
  run beside an app in use. WorkManager allows no backoff on an idle job, so that job has none. A
  refresh started by hand runs at once, as before. The TV emulator (2 GB, reported 1.93 GB) counts
  as low on memory too.

## Checks

- Unit tests (Robolectric, real SQLite with FTS4): after Remove + Swap + Add + Merge, an FTS search
  finds exactly the rows a full rebuild finds, for the refreshed source and for an untouched one;
  `INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('integrity-check')` passes.
- A run stopped after each batch and resumed gives the same result.
- While "playing", no batch runs; one minute after, it continues.
- Pending sources are searched by title and description.
- TV emulator with bears: refresh during playback, the card shows the wait; stop playback, indexing
  finishes; search works throughout.
