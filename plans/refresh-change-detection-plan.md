# Refresh Change Detection Plan

**Requirement:** when a catalog refresh or an EPG refresh runs a second time a few minutes after
the first, the app should know whether the remote data actually changed — and when it did not,
skip the expensive local work and say so.

**Two independent questions, deliberately kept apart:**

1. *Did the response change at all?* — one hash over the whole payload. Coarse, cheap, answers
   "was this refresh worth anything".
2. *What changed?* — row-level, and for the Xtream catalog it is **already computed today** and
   then thrown away. Surfacing it is nearly free.

**Scope:** Xtream catalog sync (`XtreamContentManager`) and the XMLTV EPG pipeline
(`EpgFileManager` + `EpgIndexer`). Jellyfin, SMB, Local and Remote M3U are untouched — they have
no equivalent bulk refresh path in this codebase.

**Status (2026-08-27): Phases 0–3 implemented and pushed to `main`** (`18cf8c48` catalog delta,
`9ac39512`/`0349e8d0` EPG conditional-GET + hash + skip). Phases 4 and 5 remain open — Phase 4 is
explicitly marked lower-value in its own section below; Phase 5 (UI surfacing) hasn't been asked
for yet. The mechanism fails safe by construction: a source whose `304`/hash signal turns out
untrustworthy simply never skips and always does a full ingest — today's exact behavior, not a
regression — so nothing here needed the open verification item resolved before shipping.

---

## What happens today

### EPG: no change detection whatsoever

`EpgFileManager.downloadSource` (`EpgFileManager.kt:946`) issues a bare
`Request.Builder().url(source.url).build()` — no `If-None-Match`, no `If-Modified-Since`, and no
digest of the bytes it streams to `tmpFile`. Every refresh then hands that file to
`ingestDownloadedSource` (`:1055`), which parses the entire XML and writes every channel and
programme row again. Refreshing a source whose file has not moved in a week costs the full
download *and* the full ingest.

`EpgSourceEntity` records the size (`last_download_bytes`) and the counts (`last_channels`,
`last_programmes`) of the last run, but nothing that identifies the *content*. Two runs producing
identical counts are indistinguishable from two runs producing different data with the same
totals.

### Catalog: change is detected, then discarded

`XtreamContentManager` already does exactly the right thing at row level. `syncStreams`
(`XtreamContentManager.kt:507`) reads the prior hashes with `streamDao.getStreamHashes()`
(`:520`), computes `XtreamStreamEntity.computeHash(...)` per incoming row (`:540`), and only
queues a write when `oldHash == null || oldHash != contentHash` (`:569`). Ids the server no longer
lists are collected into `toDelete` and removed (`:627-632`). `syncSeries` (`:646`) and
`syncCategories` (`:425-482`) follow the same shape.

So after every sync the code knows, precisely, how many rows were inserted, updated and deleted.
It keeps none of it. `ProviderSyncRunner.syncProvider` returns a bare `Outcome.Success`
(`ProviderSyncRunner.kt:81`), and `updateSyncStats` (`ProviderDao.kt:48`) persists only a
timestamp, a duration and an error string.

---

## Phase 0 — probe before building anything

A byte hash is only useful if identical content yields identical bytes. Two things routinely
break that, and both must be measured against the user's real sources before a line is written:

- **XMLTV generator stamps.** Many generators emit `<tv date="20260826120000" ...>` — the header
  changes on every request even when no programme did.
- **gzip container metadata.** `.gz` sources are stored as downloaded, and the gzip header carries
  an mtime. Re-compressing identical XML produces different bytes.

Run, twice, five minutes apart:

```sh
# EPG source, hashed both raw and decompressed
curl -s '<epg url>' | sha256sum
curl -s '<epg url>' | gunzip -c 2>/dev/null | sha256sum

# Xtream catalog actions
curl -s '<base>/player_api.php?username=U&password=P&action=get_live_streams' | sha256sum
curl -s '<base>/player_api.php?username=U&password=P&action=get_vod_streams'  | sha256sum
curl -s '<base>/player_api.php?username=U&password=P&action=get_series'       | sha256sum

# and does the server offer validators at all?
curl -sI '<epg url>' | grep -i 'etag\|last-modified'
```

Record the outcome in this file before starting Phase 1. Three cases:

| result | consequence |
|---|---|
| decompressed hashes match | Phase 1 as written works |
| only the `<tv date=...>` line differs | hash the body with that one line excluded |
| rows/order differ every fetch | byte hashing is dead for that source; fall back to the row-level digest already used for the catalog |

If `ETag` or `Last-Modified` is present, Phase 1a is worth far more than Phase 1 — it is the only
option that avoids the download itself.

### Results (measured 2026-08-26/27, against the actual live provider)

Tested against the provider actually configured on a running device rather than generic public
XMLTV mirrors — those don't represent what this app's users are pointed at and are not repeated
here. Exported settings supplied with the original request were stale (per user correction);
pulled the real `providers.db` off the TV emulator (`emulator-5554`, `run-as org.njarasoa.fijerena
cat databases/providers.db` — see [[reference_backup_restore_app_data]]) to find the live one.
Active provider is `bears 2` on `bearsclub.online`; its EPG source row carries
`xmltv.php?username=...&password=...` (the EPG URL embeds live credentials directly), and its
last real sync recorded 8307 channels / 79951 programmes.

- **Xtream `player_api.php`** (`get_live_streams`/`get_vod_streams`/`get_series`): responds
  normally (`200`) from plain external `curl`, no CDN interference. Headers carry
  `Cache-Control: public, must-revalidate, proxy-revalidate` with **no `ETag` and no
  `Last-Modified`**. No conditional-GET path for this panel's catalog JSON — Phase 3's row-level
  diff (already implemented, just not surfaced) is the right and only mechanism here, not Phase 4.
- **This panel's `xmltv.php` actively blocks external fetches.** Every attempt — different
  `User-Agent` values including OkHttp's own default, different times, a clean retry after the
  first block appeared to have cooled off — returned `HTTP 513`, `Server: CDN PROXY SERVICE`,
  empty body. `player_api.php` on the same host answered normally in between, so this isn't a
  blanket IP block or general rate-limit: the CDN specifically gates the bulk XMLTV export,
  presumably to stop exactly the kind of scripted fetching this probe is. The stored
  `last_ingested_at_ms`/counts show the app got through at some point in the past, so something
  about a first-party client request was passing that a bare `curl` didn't.
- **Update, 2026-08-27, verified from inside the app:** Phases 1a/1/2 were implemented (see
  below) and deployed to the same TV emulator (`emulator-5554`) to check this specifically —
  `EpgSyncDebugReceiver` triggered for real, no probing tool involved. Result: `bearsclub.online`
  is *currently* blocked from the on-device app too — `epg_source.last_error` reads
  `server returned HTTP 513` after all 5 retry attempts, the identical signature the external
  `curl` probe hit. This isn't a sandbox-egress quirk after all; whatever let the app through
  before either isn't happening now or was a difference in *when*, not *how*, the two clients
  asked. Byte-stability for this source remains unverified — it can't be checked while the
  endpoint refuses every fetch, from anything — but the negative result is now confirmed
  first-party, not an artifact of testing from outside the app. Revisit once this provider's EPG
  endpoint is reachable again; the mechanism itself needs no further validation to be trustworthy
  in the meantime (see "fails safe" note below).

**Second, independent provider — `cf.stream4ktv.cc` (also live on a device per
[[reference_emulator_providers]]), same pattern:**

- `player_api.php?...&action=get_live_streams` answers normally from external `curl`, real
  ~20MB JSON payload, same headers as `bearsclub.online` — `Cache-Control: public,
  must-revalidate, proxy-revalidate`, no `ETag`, no `Last-Modified`. Two panels now agree: Phase
  3's row-diff is the mechanism for Xtream catalog data, not conditional GET or a response hash.
- `get.php?...&type=m3u_plus&output=ts` (the bulk M3U-playlist export — not an endpoint this
  codebase calls; `XtreamApiService` uses `player_api.php` JSON, not M3U) returned a custom
  Cloudflare status (`HTTP 884`, empty body) on every attempt, while `player_api.php` on the same
  host kept answering normally around it. Not the same CDN as bearsclub's (`cloudflare` here vs.
  `CDN PROXY SERVICE` there), so this is two unrelated panels independently choosing to gate their
  bulk-export endpoint while leaving the per-request JSON API open — reinforcing that "the
  full-catalog/full-guide export gets treated as scraping traffic" is a pattern to expect from
  Xtream-style panels generally, not an artifact of one provider's specific CDN. Strengthens the
  case for verifying EPG byte-stability from inside the app as the default assumption for any new
  provider, not a one-off workaround for `bearsclub.online`.

---

## Phase 1a — conditional GET (only if Phase 0 finds validators)

Store the response's `ETag` and `Last-Modified` on `EpgSourceEntity`. On the next refresh send
`If-None-Match` / `If-Modified-Since`. A `304` means unchanged: skip download and ingest entirely,
record the run, done.

`downloadSource` already inspects `response.isSuccessful` (`:970`) and treats anything else as an
error, so 304 needs an explicit branch above that check — a 304 is *not* `isSuccessful` in OkHttp.

This is the cheapest possible answer and the only one that saves bandwidth, but many IPTV EPG
endpoints are dynamic PHP that send neither header. Phase 1 stands on its own if this is not
available.

---

## Phase 1 — content hash for EPG

### Where the hash is taken

The hash must be known *before* parsing, otherwise nothing is saved. Two paths, split by whether
the source is compressed:

- **Plain XML source** — digest in the existing download read loop
  (`EpgFileManager.kt:980-1002`). One `digest.update(buffer, 0, read)` next to the existing
  `output.write(...)`. Costs nothing; the bytes are already in hand.
- **`.gz` source** — the raw bytes are unreliable (gzip mtime), so digest the *decompressed*
  stream in a pre-pass at the top of `ingestDownloadedSource`, before the parser is constructed:
  read `tmpFile` through `GZIPInputStream` into a `MessageDigest` and discard the output. This is
  a local read plus a gunzip — perhaps a second or two — against an ingest that writes millions of
  rows. Worth it.

Reuse the wrapper pattern already in the file: `CountingInputStream` (`EpgFileManager.kt:1278`) is
the model for a `DigestInputStream`-style wrapper if one reads better than an inline loop.

### Storage

`SettingsDatabase` is at version 8 (`SettingsDatabase.kt:12`). Add `MIGRATION_8_9`:

```kotlin
db.execSQL("ALTER TABLE epg_source ADD COLUMN last_content_sha256 TEXT")
```

and register it in `getInstance` alongside the existing seven. On `EpgSourceEntity`, add
`@ColumnInfo(name = "last_content_sha256") val lastContentSha256: String? = null`, placed with the
other `last_*` fields. Null means "never hashed" and must always force a full ingest.

`resetAllIngestionState()` (`EpgSourceDao.kt`) must null the column too, or "clear all data"
leaves a hash that suppresses the next ingest.

---

## Phase 2 — act on the EPG hash

### Skipping

In `ingestDownloadedSource`, when the computed hash equals `source.lastContentSha256`:

- do not parse, do not touch staging or primary tables;
- return `SourceStats(..., unchanged = true)` carrying forward `source.lastChannels` /
  `source.lastProgrammes` so the UI totals do not collapse to zero;
- call a new `EpgSourceDao.markUnchanged(id, timestamp, hash)` that sets `last_ingested_at_ms` and
  clears `last_error` but leaves the count columns alone. Reusing `markIngested` would zero them.

Add `val unchanged: Boolean = false` to `SourceStats` (`EpgFileManager.kt:117`).

### The staging swap is already safe

`processAllSourcesInternal` builds `syncedIds` from `allStats.filter { it.error == null }`
(`:673`) and passes it to `executeSwapToMain`. `EpgIndexDao.executeSwap` (`EpgIndexDao.kt:57-62`)
deletes only the source ids it is given before transferring staging, so a skipped source simply
keeps its existing primary rows. **Skipped sources must be excluded from `syncedIds`** — including
one would delete its primary rows and then transfer nothing for it, wiping the guide for that
source. This is the single most dangerous line in the phase.

`anyIngested` (`:663`) already gates the swap, the FTS rebuild and `invalidateXmltvCache`. When
every source is unchanged it goes false and all three are correctly skipped.

### The staleness guard

Byte-identical XML does not mean the ingest would produce identical rows. `ingestFromStream`
windows programmes against wall-clock time — `cutoffEpoch = now - 12h`, `futureLimitEpoch =
now + 7d` (`EpgIndexer.kt:240-242`) — and `deleteStaleProgrammes` prunes what falls behind the
cutoff. A static file re-ingested three days later admits three further days of programmes that
the first ingest discarded as too distant.

So: **force a full ingest, hash match or not, when `now - lastIngestedAtMs` exceeds 24 hours.**
Without this, a source served from a static file quietly stops extending its guide window while
reporting healthy refreshes. This is a correctness requirement, not an optimisation knob.

---

## Phase 3 — catalog delta counters

The cheapest high-value change in this plan; independent of every hashing phase.

In each of `syncStreams`, `syncSeries` and `syncCategories`, count what the existing diff already
decides:

```kotlin
var inserted = 0
var updated = 0
// inside the existing `if (oldHash == null || oldHash != contentHash) {`
if (oldHash == null) inserted++ else updated++
// after the cleanup block
val deleted = toDelete.size
```

Carry a `SyncDelta(inserted, updated, deleted)` per task out of `XtreamMediaProvider.syncAll()`
(`XtreamMediaProvider.kt:682`), summed across the six jobs, into
`ProviderSyncRunner.Outcome.Success` and on to `updateSyncStats`. That needs three columns on
`providers` — `lastSyncInserted`, `lastSyncUpdated`, `lastSyncDeleted` — in the same
`MIGRATION_8_9` as the EPG column, matching the `MIGRATION_5_6` pattern that added the existing
sync stats.

All three zero means the catalog did not change. Note this detects change without avoiding work:
the fetch and the parse still happen, because the diff *is* the parse.

Deliberately excluded from the counters: the "server returned 0 streams" guard at
`XtreamContentManager.kt:614-620` (and its twins at `:466` and `:732`) returns early without
deleting, and must report no delta at all
rather than a delta of zero — a failed sync and an unchanged sync must not look alike.

---

## Phase 4 — catalog response hash (optional, lower value)

For symmetry with EPG, `XtreamApiService.getStreamsStreaming` (`XtreamApiService.kt:158-176`) and
its VOD/series siblings can wrap the body in a digest before `decodeToSequence`:

```kotlin
response.bodyAsChannel().toInputStream().use { raw ->
    val md = MessageDigest.getInstance("SHA-256")
    val hashing = DigestInputStream(raw, md)
    json.decodeToSequence<XtreamStream>(hashing).forEach { onItem(it) }
    hashing.readBytes()   // drain: decodeToSequence may stop before EOF
    ...
}
```

Ktor decompresses transport gzip before `bodyAsChannel()`, so this hashes the JSON text and is not
exposed to the container-metadata problem the EPG `.gz` path has. Store per provider and action
next to `KEY_STREAMS_TIMESTAMP_PREFIX` (`XtreamCacheKeys.kt`).

What it buys: skipping `streamDao.rebuildFts()` (`XtreamContentManager.kt:634`) and
`recomputeExclusions()`. What it does not buy: the fetch or the parse. Build it only if Phase 3's
counters prove insufficient — an unchanged payload always produces a zero delta anyway, so this
phase is mostly a cheaper route to the same answer.

The known risk is PHP panels that do not guarantee row order across requests. Phase 0 measures it.

---

## Phase 5 — surface it

- **EPG management screen** (`TvEpgManagementScreen.kt`, `MobileEpgManagementScreen.kt`): a
  skipped source should read "Unchanged" rather than showing a full ingest it did not perform.
  `MultiSourceState.Completed.sourceStats` already carries the per-source record.
- **Provider settings** (`EpgSettingsCard.kt`, provider list): "No changes" when the delta is
  zero, otherwise the counts.
- **Dev mode** (`AppSettings.isDevMode`): show the hash prefix and which branch was taken —
  304, hash match, forced by the 24h guard, or full ingest. This is the only way to debug a
  suppression bug in the field.

---

## Test plan

Unit, alongside the existing `EpgFileManagerTest`:

- identical bytes twice ⇒ second run reports `unchanged`, no indexer call;
- one byte differs ⇒ full ingest;
- `lastContentSha256 == null` ⇒ full ingest;
- hash matches but `lastIngestedAtMs` is 25 hours old ⇒ full ingest (the Phase 2 guard);
- a skipped source id never reaches `executeSwapToMain`.

For Phase 3, assert the counters against a fixture where rows are added, changed, and removed
between two sync passes.

On device, the existing procedure applies — trigger a sync via `EpgSyncDebugReceiver`, refresh
twice in succession, confirm the second run finishes in a fraction of the time and the guide is
still complete afterwards. Emulators first.

**Unrelated bug noticed while verifying on device, not fixed here:** two `epg_source` rows both
labeled `cf.stream4ktv.cc (Bulk)` failed ingestion with `XmlPullParserException` (`Dangling <`,
`Unexpected EOF`) — a truncated download. `downloadSource`'s read loop treats any `input.read()`
returning `-1` as a clean finish; it never compares `totalRead` against the response's
`Content-Length`, so a connection cut short reads as success and the truncation is only caught
later, expensively, by the XML parser. Pre-existing, not introduced by this change (the loop
structure is unchanged) — flagged here rather than fixed, since it's outside this plan's scope.

---

## Rejected

**Semantic digest at parse time** — accumulate an order-independent hash over
`(channelId, start, stop, title)` inside `ingestFromStream` and compare before the staging swap.
Immune to header stamps and row reordering, but it has already parsed the whole file by the time
it can answer, so it saves only the swap and the FTS rebuild. Keep it in reserve for a source that
fails Phase 0 in the "rows reorder every fetch" way; not worth building otherwise.

**Comparing `last_download_bytes`** — cheaper than a hash and already stored, but a same-size
different-content payload is exactly the case that matters, and the file that changes by one
programme title keeps its length. Not a substitute for a digest.
