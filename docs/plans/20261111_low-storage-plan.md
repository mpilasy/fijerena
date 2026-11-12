# Low storage

**Status:** In progress. Phase 1 done 2026-11-11 (LS-Q5: (a)). Open questions LS-Q1 to LS-Q4 and LS-Q6 wait for the user.

## Goal

On a device with little free storage (TVs most of all):
- Each large write checks for enough space before it starts. When there is not enough, it stops
  and gives a clear message that says what to clear.
- A full disk during a write never damages data, never starts a retry loop, and never makes the
  app crash again and again.

## What exists (survey 2026-11-11)

Some protection exists already. The gaps are a disk that fills during a write, and checks that do
not know how much space a job needs.

- **The low-storage check exists:** `StorageSpace` (core/network/.../StorageSpace.kt).
  - It copies Android's low-storage rule: free space below 5% of the storage, at most 500 MB.
  - `ProviderSyncRunner.syncProvider` does not start a catalogue sync then. The source keeps a
    `STORAGE_LOW` catalogue error.
  - `EpgFileManager.blockedByLowStorage` does not start a guide refresh then. Each source keeps a
    `STORAGE_LOW` error, and the run ends in that error.
  - The periodic guide and catalogue work have `setRequiresStorageNotLow(true)`.
  - `EpgManagementViewModel.storageLowFreeBytes` shows `epg_storage_low_note` on the guide pages
    (`TvEpgManagementScreen`, `MobileEpgManagementScreen`).
  - `ErrorKind.STORAGE_LOW` has `error_storage_low` in en, fr and mg. It does not say what to clear.
- **Settings → Device Info shows free space already:** `storageSection` (core/ui/.../deviceinfo/CoreInfo.kt)
  shows free of total, each database's size, the image cache, the cache total and the files total.
- **A disk-full error during a write is taken for a network error:**
  - `errorKind` (core/network/.../FriendlyError.kt) maps every `IOException` to `NETWORK`. ENOSPC
    ("No space left on device") is an `IOException`, so the user reads "network error".
  - `networkCause()` also returns it, so `ProviderSyncRunner.isTransient` is true. The sync tries
    again up to `MAX_ATTEMPTS` (3) with back-off.
  - `EpgFileManager.downloadSource` catches every exception and tries 5 times with back-off. Each
    try writes the whole file to cacheDir/xmltv_source_<id>_tmp again.
  - `SQLiteFullException` is not an `IOException`: it reads as `GENERIC`.
  - `EpgSyncWorker` (5) and `EpgFtsRebuildWorker` (3) return `Result.retry()` on any exception.
- **The guide index** (docs/EPG_INDEX_STORAGE.md, `EpgIndexer`, `EpgIndexDatabase`):
  - `EpgFileManager.shouldUseStaging` uses staged ingestion only when free space is more than 1.5
    times epg_index.db. Else it ingests directly into the live tables.
  - Staging: a failure leaves rows in the staging tables until `clearStaging` at the start of the
    next run. The live guide stays as it was.
  - Direct ingest: REPLACE overwrites rows batch by batch. `deleteProgrammesNotRewritten` runs only
    after a full ingest. A failure part of the way leaves new and old rows mixed. The source's
    validators stay those of the last good ingest, so the next refresh ingests it again and the mix
    goes away.
  - Measured sizes: epg_index.db 261 MB after reclaim on a phone; 198 MB on the emulator.
- **The catalogue** (`ProviderSyncRunner`, `XtreamContentManager`, xtream_v2.db):
  - `syncStreams` and `syncSeries` write in batches (`insertKeepingDetailCache`), one transaction
    each. Deletes run after all inserts. A failure part of the way keeps old rows and some new
    ones. The next sync heals it.
  - `CatalogSearchIndex.compact` runs after `syncAll`. A failure there fails the whole sync,
    although the rows are written.
  - Measured size: xtream_v2.db 1.24 GB for a large catalogue (docs/EPG_INDEX_STORAGE.md §1).
- **The image cache:** Coil `DiskCache` in cacheDir/image_cache, fixed at 512 MB
  (`FijerenaApplication`), with no link to the device's storage. Clearing it is in safe mode only.
- **Other writers:**
  - `RemoteM3uMediaProvider`: a temp file in cacheDir, then a rename.
  - `SettingsExportManager`: export to a document URI. Small.
  - `LogShare` (mobile): one file in cacheDir. Small.
  - `CrashLog`, `LaunchCounter`: never throw on a failed write.
  - `ConnectionLog`: copy, then rename.
  - `SportsCache`, `TmdbChart`, `TmdbPeople`: a failed write is logged; a file that does not parse
    counts as not stored.
- **Crashes:** `AppScopes` scopes log and record an uncaught exception and do not crash. A crash
  at startup three times in a row starts `SafeMode`. Its Clear caches action deletes the guide
  index, each catalogue and the image cache. SQLite itself rolls back a statement or transaction
  that hits SQLITE_FULL and keeps the file whole. The risk is in the app: work split over many
  transactions, retry loops and wrong messages.

## Open questions

| # | Question | Options |
|---|----------|---------|
| LS-Q1 | Not enough space for a staged guide refresh? | (a) **Direct ingest as today, but only when there is space for it**; else the source gets `STORAGE_LOW` and keeps its guide. Recommended: the guide stays current while space allows, and a failed direct ingest heals at the next refresh. (b) Skip the refresh. The old guide stays whole but gets older. |
| LS-Q2 | Image cache size? | (a) **2% of the storage, from 64 MB to 512 MB** (Coil `maxSizePercent` with a minimum and a maximum). Recommended: about 160 MB on an 8 GB TV. (b) Keep 512 MB. |
| LS-Q3 | A way to clear the image cache outside safe mode? It is a look-and-feel change. | (a) **A Clear Image Cache icon action on the image cache row of Device Info**, TV and phone. Recommended. (b) None. |
| LS-Q4 | The message? | (a) **One message for every writer**: `error_storage_low` gets a new text that says what to clear. Recommended. (b) A message for each writer. |
| LS-Q5 | A worker that hits a full disk? **Decided: (a).** | (a) **Finish with the stored `STORAGE_LOW` error and no retry.** The storage-not-low rule and the next period start it again. Recommended. (b) `Result.retry()` with back-off. |
| LS-Q6 | How to test on an emulator? The user decides before any emulator data change. | (a) **A debug-only free-space value set over adb** for the checks and messages, and one real run with a filler file in the app's own cache folder, deleted after. Recommended. (b) A new emulator with a small data partition. |

## Design

### One check and one classifier (in `StorageSpace`)

- `fun interface FreeSpace { fun freeBytes(): Long }`. The default reads `filesDir.usableSpace`.
  Tests give a fake.
- `StorageSpace.shortfall(needBytes, space)` returns null when there is enough, or a `STORAGE_LOW`
  `StoredError` with Nerd text such as "Need 420 MB, 180 MB free".
- Every check adds a fixed **50 MB margin**, so that small writes (watch history, settings) still work.
- `Throwable.isDiskFull()` is true when any cause is a `SQLiteFullException`, an `ErrnoException`
  with `ENOSPC`, or an `IOException` whose message has "ENOSPC" or "No space left on device".
- `errorKind` checks `isDiskFull()` first and gives `STORAGE_LOW`.
- `networkCause()` skips a disk-full `IOException`. So `ProviderSyncRunner.isTransient` is false,
  and there is no retry inside the run.

### Space each writer needs (starting values)

Phase 2 measures each value on the emulator and corrects it.

| Writer | Check before | Need |
|---|---|---|
| XMLTV download (`downloadSource`) | the download | Content-Length; else the source's last `downloadBytes`; else 50 MB |
| Guide ingest, staged | the ingest | 1.5 × epg_index.db (today's `shouldUseStaging` rule) |
| Guide ingest, direct | the ingest | the uncompressed size (a `.gz` file: 10 × its size) |
| FTS rebuild (`rebuildFtsAndUpdateState`, `EpgFtsRebuildWorker`) | the rebuild | 0.5 × epg_index.db |
| Catalogue sync (`syncProvider`) | the sync | 25% of xtream_v2.db, at least 100 MB |
| Remote M3U download | the download | 2 × Content-Length (temp file and cache file) |
| Image cache | none | its own limit (LS-Q2) |

The `StorageSpace.isLow` checks stay. They stop automatic work as Android does.

### A full disk during a write

- **XMLTV download:** a disk-full error ends the retry loop at once. The temp file is deleted, and
  the source keeps `STORAGE_LOW`.
- **Guide ingest, staged:** `clearStagingForSources` and `incrementalVacuum` run at once to free
  the space. The live guide stays as it was.
- **Guide ingest, direct:** the source keeps `STORAGE_LOW`. Its validators stay those of the last
  good ingest, so the next refresh ingests it again. A test proves this.
- **FTS rebuild:** the guide keeps the index state it had before the rebuild (Phase 3 checks what
  `EpgIndexState` shows after a failed rebuild today). The worker finishes without a retry (LS-Q5).
- **Catalogue sync:** rows already written stay. A disk-full error in `CatalogSearchIndex.compact`
  is logged and does not fail the sync; the next sync compacts.
- **Workers:** `EpgSyncWorker` and `EpgFtsRebuildWorker` do not return `Result.retry()` on a disk-full error (LS-Q5).
- **Startup:** no change. A crash from a full disk at startup leads to safe mode, as today.

### What the user sees

- **The message (LS-Q4).** New en text of `error_storage_low` (key kept), with fr and mg in the same commit:
  "This device's storage is almost full. Free up space: remove apps you do not use, or clear this
  app's guide data in Settings. Then try again. Automatic updates wait until then."
  The Settings words must match the screen labels at build time.
- It shows where errors show today: the source cards, the guide refresh error, the catalogue
  failure line (`TvErrorState`, `MobileErrorState`, the stored source errors). A Nerd also sees the
  raw text ("Need 420 MB, 180 MB free").
- **Safe mode:** when storage is low, the safe-mode screen adds the same message above Clear
  caches, because a full disk is a likely cause of the crashes.
- **Device Info:** free space shows already. With LS-Q3, a Clear Image Cache icon action on the
  image cache row, with its label on focus on TV and a tooltip on the phone.
- New strings in en, fr and mg. British spelling. Title Case for English titles only.

### Tests

- **Unit tests with a fake `FreeSpace`:** `shortfall` at, below and above the need with the margin.
  Each writer's check: it stops, and stores `STORAGE_LOW`.
- **Classifier:** `isDiskFull` for `SQLiteFullException`, an ENOSPC `ErrnoException` as a cause,
  and the libcore text "write failed: ENOSPC (No space left on device)". Also: `errorKind` gives
  `STORAGE_LOW`, `isTransient` is false, and a normal `IOException` is still `NETWORK`.
- **A real SQLITE_FULL:** Robolectric with an in-memory `EpgIndexDatabase` (as
  `EpgIndexerDirectIngestTest` does) and `PRAGMA max_page_count` set low. An ingest then fails
  part of the way. The test checks:
  - the old guide rows stay after a staged ingest, and staging is empty;
  - the source has `STORAGE_LOW`;
  - nothing tries again.
  The same method works for a catalogue batch on an in-memory `XtreamDatabase`.
- **Download:** a fake output stream that throws ENOSPC. The test checks one try, no back-off, and
  that the temp file is gone.
- **Emulator (LS-Q6, only after the user agrees):**
  - A debug-only receiver sets a fake free-space value. Check the messages on the TV and phone
    emulators in Malagasy and in English.
  - With the user's agreement, one guide refresh with a filler file in the app's cache folder. The
    file is deleted after.

## Phases

| Phase | What | Visible change |
|---|---|---|
| 1 | `isDiskFull`; `errorKind` gives `STORAGE_LOW`; no retry on a full disk (catalogue sync, XMLTV download, Remote M3U, both guide workers); unit tests | "Storage almost full" in place of "network error"; no retry loops |
| 2 | `FreeSpace` and `shortfall`; a space check before each large write in the table; sizes measured and corrected | Jobs without enough space stop before they start, with the message |
| 3 | Full disk during a write: staging cleared at once, compact failure does not fail the sync; `max_page_count` tests | None, apart from space freed sooner |
| 4 | New `error_storage_low` text; safe-mode note; image cache size (LS-Q2); Clear Image Cache (LS-Q3); debug free-space receiver; emulator check | New message; Device Info action |

One commit per phase. Each commit updates this plan and the docs it affects (FEATURES,
docs/EPG_INDEX_STORAGE.md, RELEASE_NOTES).

## Progress

| Phase | Status |
|---|---|
| 1 | Done 2026-11-11 |
| 2 | Not started |
| 3 | Not started |
| 4 | Not started |

**Phase 1 done (2026-11-11):**
- `Throwable.isDiskFull()` is in FriendlyError.kt, next to `networkCause()`, not in `StorageSpace`:
  the two share the cause chain walk. `errorKind` checks it first and gives `STORAGE_LOW`.
  `networkCause()` skips a disk-full `IOException`.
- `ProviderSyncRunner.isTransient` is false for a full disk, also when a `CatalogSyncException`
  has a network failure next to it. `failureError` then describes the disk-full failure. The
  outcome is `Permanent`, so `XtreamSyncWorker` does not retry either.
- `triesUntilDiskFull` stops the tries of the XMLTV download (`downloadSource`) and of the Remote
  M3U download after a disk-full try, with no back-off before the stop. The temp files are deleted
  as before. Remote M3U still falls back to its stale cached playlist when it has one.
- Also the in-app guide task retry (`launchGenericTask`, 1 to 16 minutes): a full disk ends the
  task at once with its error.
- `EpgSyncWorker` and `EpgFtsRebuildWorker` return `Result.failure()` on a full disk
  (`failedRunResult`). `EpgSyncWorker` reads it from the sources' stored `STORAGE_LOW` errors when
  every source failed, or from the exception.
- The low-storage check before a catalogue sync still returns `Transient`, so `XtreamSyncWorker`
  can retry it; the storage-not-low rule holds that retry until there is space.
- Tests: `DiskFullTest` (Robolectric: each form, `errorKind`, `networkCause`, the tries),
  `ProviderSyncRunnerRetryTest`, `CatalogSyncFailureTest` (one sync try), `GuideWorkerRetryTest`,
  `RemoteM3uMediaProviderTest` (an output stream that fails with ENOSPC: one request, no temp file).

## Risks

- **Size estimates that are wrong.** Too high, and a refresh that could work does not start. Too
  low, and a write fails part of the way. Phase 3 makes such a failure safe, and Phase 2 measures.
- **ENOSPC found by message text.** Android gives the text in English, but a wrapper can change it.
  The `ErrnoException` cause check comes first.
- **Free space changes during a job** (another app writes). The check is a first gate only. The
  mid-write rules of Phase 3 handle the rest.
