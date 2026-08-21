# EPG Index Storage and Reclaim

How `epg_index.db` grows, why it stopped reclaiming space, and what was done about it.
Investigated and fixed 2026-08-20/21 against darcy, mdarcy and the TV emulator.

---

## 1. The symptom

`epg_index.db` had grown larger than the entire Xtream catalogue on both Shields, and darcy was
at 81% disk usage with 2.1 GB free.

| Device | `epg_index.db` | pages | page size | free pages | dead space |
|---|---|---|---|---|---|
| mdarcy | 1.70 GB | 1,782,121 | 1024 | 1,562,995 | **1.49 GB (87.7%)** |
| darcy | 1.59 GB | 1,670,552 | 1024 | 1,445,207 | **1.38 GB (86.5%)** |
| `xtream_v2.db` (mdarcy) | 1.24 GB | 324,759 | 4096 | 0 | 0% |

The EPG index was 87% empty. Auto-vacuum was enabled, `incrementalVacuum()` was called after every
purge and every ingest, and none of it was reclaiming anything.

---

## 2. Root cause: PRAGMAs issued through `query()` never execute

```kotlin
db.openHelper.writableDatabase
    .query("PRAGMA incremental_vacuum")
    .close()                                   // never runs
```

Android's `SQLiteCursor` is lazy. The statement is not executed until something fills the cursor
window, so closing the cursor without stepping it discards the PRAGMA silently. No exception, no
log, no effect.

The same pattern silently disabled three more statements:

| Statement | Location | Consequence |
|---|---|---|
| `incremental_vacuum` | `EpgIndexer.incrementalVacuum()` | freelist never reclaimed |
| `wal_checkpoint(TRUNCATE)` | `EpgIndexer.rebuildFtsAndUpdateState()` | WAL not checkpointed before rebuild |
| `mmap_size = 268435456` | `EpgIndexDatabase.onOpen` | never took effect (see §6) |
| `journal_size_limit` | `EpgIndexDatabase.onOpen` | WAL size cap not applied |

`execPragma()` in `EpgIndexer.kt` is the fix: it steps the cursor so the statement actually runs.
`execSQL` is not an option for these — Requery rejects it for any statement that can return rows.

**Rule for this codebase:** a PRAGMA that can return rows goes through `execPragma`, never through
`query(...).close()`.

---

## 3. Second cause: the reclaim stalls in WAL mode

With the cursor fixed, the first real run on darcy reclaimed 41,998 of 1,431,134 free pages and
stopped. A batch that frees nothing is not the end of the freelist: in WAL mode the moved pages
only leave the file when the log is checkpointed, and once the log reaches `journal_size_limit`
with a reader holding it open, the vacuum stops making progress.

`incrementalVacuum()` now forces a checkpoint and retries the batch before concluding there is
nothing left. It also walks the freelist in `VACUUM_CHUNK_PAGES` (2000) batches rather than one
unbounded pass, which on a million-page freelist would hold the database for minutes.

Every run logs its outcome:

```
I EpgIndexer: Incremental vacuum reclaimed 121337 of 121337 free pages
```

---

## 4. `auto_vacuum` was set too late

`auto_vacuum` can only be enabled on a database with no tables in it. Room's
`RoomDatabase.Callback.onOpen` fires *after* Room creates the schema, so the old placement could
never have taken effect on a fresh file. It now runs from `CreationPragmaFactory`, a wrapping
`SupportSQLiteOpenHelper.Factory` whose `onConfigure` runs before Room touches the file.

---

## 5. Page size: Requery hardcodes 1024

`epg_index.db` runs on 1 KB pages while every Room-default database in the app is on 4 KB —
deeper B-trees and four times the page lookups per scan, on the largest database the app owns.

Decompiled from `sqlite-android-3.49.0`:

```
public static int getDefaultPageSize();
   ...
   21: invokevirtual  StatFs.getBlockSize:()I
   24: putstatic      sDefaultPageSize:I
   27: sipush         1024
   32: ireturn                      // returns the literal, discards what it just computed
```

Requery measures the filesystem block size, throws the result away, returns 1024, and applies it
per connection before any callback of ours runs. There is no configuration hook for it.

Two approaches that **do not work**, both tried on hardware first:

- `PRAGMA page_size = 4096` + `VACUUM` — inert on a WAL database. SQLite documents this. mdarcy
  spent 77 s vacuuming 506 MB and stayed on 1024.
- Seeding the file through Requery before Room opens it — Requery stamps its own size on the
  connection first.

What works is `EpgIndexDatabase.seedPageSize()`: create the file with the **framework**
`android.database.sqlite.SQLiteDatabase`, which honours the requested size, and create then drop a
throwaway table to force the header out. By the time Room and Requery open it the file is no
longer empty, so nothing can change the page size back.

It only runs when the file is absent. **There is no in-place route for an existing index** — not a
Room migration, not a VACUUM, nothing.

---

## 6. `mmap_size` deliberately not restored

The `mmap_size = 268435456` pragma had never once executed. Repairing it along with the others
would have switched on a 256 MB mapping in a process that already sits at ~497 MB against a 512 MB
per-app ceiling. It was removed rather than fixed — enabling it is a memory decision, not a bug
fix. `cache_size = -64000` (64 MB) does apply, because it goes through `execSQL`.

---

## 7. How these reach a device

None of this is a Room migration, and a migration would be the wrong tool: migrations only run on
a schema version change and never on a fresh install, while Requery stamps 1024 on databases it
creates. The mechanisms are version-independent instead.

| Fix | Reaches an existing install | Reaches a new install |
|---|---|---|
| Working `incremental_vacuum` | next EPG sync | yes |
| WAL checkpoint before FTS rebuild | next FTS rebuild | yes |
| `journal_size_limit` | next open | yes |
| `auto_vacuum` at creation | no (already set on existing files) | yes |
| 4 KB page size | **only via delete-and-rebuild** | yes |

The first sync after upgrading is slower than usual on a neglected index — it walks the whole
accumulated freelist once. Every later sync is short.

---

## 8. Results

| | before | after |
|---|---|---|
| darcy `epg_index.db` | 1.60 GB, 86.5% free | 261 MB |
| darcy `/data` free | 2.1 GB (81% used) | 3.4 GB (69% used) |
| mdarcy `epg_index.db` | 1.70 GB, 87.7% free | deleted, 5.1 GB free |
| emulator (rebuilt, 4 KB pages) | 396 MB, 49.2% free | 198 MB, **0% free** |

The index settles at a few tens of MB of slack rather than zero, because the vacuum runs
mid-pipeline and later sources free more pages afterwards. That is steady state, not a leak.

Both Shields are still on 1 KB pages; converting them needs one delete-and-rebuild each.

---

## 9. Reading the state without pulling the database

The first 100 bytes of any SQLite file answer most questions, which matters when the file is
gigabytes and the device is on the far end of adb:

```bash
adb -s "$D" shell "run-as org.njarasoa.fijerena \
  dd if=databases/epg_index.db bs=100 count=1 2>/dev/null | base64"
```

| Offset | Bytes | Meaning |
|---|---|---|
| 16 | 2 | page size (1 means 65536) |
| 28 | 4 | database size in pages |
| 36 | 4 | total free pages |
| 52 | 4 | largest root b-tree page — non-zero iff auto-vacuum |
| 64 | 4 | incremental-vacuum flag |

Free pages × page size is the dead space. Note the header reflects the last checkpoint, so it can
lag during heavy WAL activity.

Do not use `dumpsys meminfo` to sample a running process during playback investigation: it forces
an `Explicit` GC in the target and dumps DB stats, which shows up as app activity that isn't real.

---

## 10. Related memory findings (2026-08-20, mdarcy, live playback)

Measured while chasing GC-related playback stutter. Recorded here because they bound what the app
can afford, and they are not derivable from the code.

- Process sits at **~497 MB PSS against a 512 MB per-app ceiling**: native heap 208 MB live in a
  533 MB arena, Dalvik 45 MB live.
- GC pauses are **71–117 µs** — not stop-the-world stalls. Each GC does 360–630 ms of *concurrent*
  work, and they are `NativeAlloc`-triggered, i.e. native pressure rather than Java pressure.
- 14 minutes of steady playback produced **zero** app GCs. They come in bursts tied to catalogue
  and EPG work, not to playback.
- The Stats-for-Nerds `GC nn runs, nnnnn ms` figure comes from `Debug.getRuntimeStat("art.gc.*")`
  and is **cumulative since process start** — it includes startup and indexing, and cannot tell
  you whether GC is happening now.
- Coil's memory cache is `maxSizePercent(0.25)`; with `largeHeap` on a 512 MB heap class that is a
  **128 MB** bitmap cache. Not yet changed.
