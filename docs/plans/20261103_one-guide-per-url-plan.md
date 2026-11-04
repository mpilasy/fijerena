# One guide source per URL

**Status:** Planned 2026-11-03. Not started. The user answered OG-Q1 to OG-Q8 on 2026-11-03 (see Answers). The
v19 offset work (`EpgOffsetShift`, `MIGRATION_18_19`) that Phase 1 builds on is committed (cff8ff566).

## Goal

Today a hand-added XMLTV guide for N media sources is stored as N `epg_source` rows, each with
`provider_id` set to one source. Each row is downloaded, imported, stored in the guide index and
synced on its own, with its own offset, refresh interval, error status and stats. This plan makes
these changes:

1. Each guide (URL) has one `epg_source` row. A link table connects the guide to the media sources
   that use it.
2. The app downloads, imports, stores (in `epg_index.db`) and syncs each guide one time.
3. Channel matching stays per media source. Each source matches the guide's channels to its own
   streams.
4. The Guide Sources page shows each guide one time, with the media sources it serves.

## What exists (survey 2026-11-03)

### Storage

- `providers.db` (`SettingsDatabase`, v19). `epg_source` has `provider_id INTEGER NOT NULL` and the
  index `index_epg_source_provider_id`. `source_key` (a UUID, unique) names the row in live sync.
- `epg_index.db` (`EpgIndexDatabase`) is a separate database. `epg_channel` and `epg_programme` have
  `source_id`, with the indexes `idx_channel_source` and `idx_programme_source`. The FTS4 table is
  `epg_programme_fts`. A migration of `providers.db` cannot write to `epg_index.db`.
- v19 added `file_utc_offset_minutes` and `applied_utc_offset_minutes`.
  `EpgOffsetShift.reconcile(sourceId, dao, indexer)` moves stored times when the offset changes.
  When it cannot move them, it returns `NEEDS_IMPORT` and calls `forgetValidators`.
- No entity uses Room `ForeignKey`. The code does every cascade by hand. Use the same method here.

### Every reader and writer of `epg_source.provider_id`

| File | Function | Use today |
|---|---|---|
| core/network/.../provider/EpgSourceEntity.kt | `providerId`, `Index("provider_id")` | The owner column |
| core/network/.../provider/EpgSourceDao.kt | `getSourceByUrl(url, providerId)`, `getSourcesForProvider`, `getSourceIdsForProvider`, `getEnabledSourcesForProvider`, `deleteSourcesForProvider`, `getFailedSources(providerId)` | Filters on `provider_id` |
| core/network/.../provider/SettingsSyncDao.kt | `sourcesAt(providerId, url)` | First-sync key adoption |
| core/network/.../provider/SettingsSyncTriggers.kt | `sync_epg_source_update` | Watches `provider_id` |
| core/network/.../provider/SettingsDatabase.kt | `MIGRATION_7_8`, `MIGRATION_16_17` | History (do not change) |
| core/network/.../provider/ProviderRepository.kt | `getGuideSources`, `pruneOrphanedCatalogData`, `deleteProviderEpgSources` | Edit Source count, Shrink Database, source deletion |
| core/network/.../MediaRepository.kt | `hasGuideForSource` | Enabled guides of one source |
| core/network/.../SettingsExportManager.kt | export, import | `providerName`/`providerUrl` per row; skips `(providerId, url)` duplicates |
| core/network/.../sync/SyncApplier.kt | `applyEpgSource` | One `providerKey`; `sourcesAt` adoption |
| core/network/.../sync/LocalRecords.kt | `EPG_SOURCE` branch | `sync.providerKey(source.providerId)` |
| core/network/.../sync/SyncPayloads.kt | `EpgSource` | `providerKey: String` |
| core/network/.../xmltv/EpgFileManager.kt | `stateFor`, `_refreshProviderId`, `launchRefreshStale`, `launchRefreshFailed`, `launchProcessSingleSource`, `processAllSourcesInternal`, `invalidateXmltvCache`, `processSingleSourceInternal`, `refreshOutdatedSources`, `getAllSources`, `getStaleSources`, `migrateFromAppSettings` | One run reports on one source; loops per source |
| core/network/.../xmltv/EpgSyncWorker.kt | `doWork`, `sourcesToRefresh` | One run per source. A shared guide is in two runs |
| core/network/.../xmltv/GuideScope.kt | `GuideScope.providerIdBySourceId: Map<Long, Long>`, `of`, `load`, `GuideAiringMatcher` | One source per guide |
| core/network/.../xmltv/XmltvEpgService.kt | `buildChannelMatchMaps` | Enabled guides of one source |
| core/network/.../sports/EpgGuideLookup.kt | `toProgrammes` | `providerIdBySourceId[row.sourceId]` |
| core/network/.../sports/GameChannelMatcher.kt | match | Reads `GuideProgramme.providerId` (works with fan-out) |
| core/network/.../xtream/manager/AutoXmltvSources.kt | `plan`, `loginUrl`, `onEmptyIngest`, `cleanUp` | The automatic guide of each Xtream source |
| core/network/.../xtream/manager/OwnXmltvIndex.kt | `listings` | The automatic guide of one source |
| core/ui/.../viewmodels/EpgManagementViewModel.kt | `GuideGroup`, `groups`, `sources`, `setSourceEnabled`, `addSource`, `updateSource`, `deleteSource`, `refreshStale`, `refreshFailed`, `refreshSource` | Groups by source; one row per source on Add |
| core/ui/.../viewmodels/EpgBrowserViewModel.kt | `sourcesFlow`, `refreshStale`, `ensureChannelMatcherCurrent`, `performSearch`, paged search filter | Search the Guide |
| tv/.../feature/epg/TvEpgManagementScreen.kt, mobile/.../feature/epg/MobileEpgManagementScreen.kt | groups, `GuideGroupHeader`, `groupHeaderIndex`, `EpgSourceEditDialog(forSources)` | The page |
| tv and mobile .../feature/provider/components/ProviderGuideSection.kt | `getGuideSources(providerId)` | Edit Source: count and last refresh |

These have no `provider_id` use and need no change: `EpgIndexer.purgeXtreamApiSources`,
`SafeModeViewModel`, `XtreamSessionManager` (it calls `AutoXmltvSources.reconcile` with the same
signature), and Device Info (`NetworkPowerInfo` reads only `epg_pipeline_stats`; after the merge,
`sourcesProcessed` counts guides).

Profile visibility: `GuideScope.searchedProviders(profileSources)` selects the profile's media
sources. A guide is read for a profile when one of its linked sources is in that profile. This rule
does not change.

## Decisions

| # | Subject | Decision |
|---|---|---|
| OG-D1 | Link table | New table `epg_source_provider` (`source_id`, `provider_id`, `linked_at_ms`), primary key (`source_id`, `provider_id`), index on `provider_id`. No Room foreign key; the code deletes links by hand. |
| OG-D2 | `epg_source.provider_id` | Removed. v20 builds the table again without the column and without `index_epg_source_provider_id`, so no code can read it by mistake. |
| OG-D3 | Automatic Xtream guide `<host> (Bulk)` | Stays one per Xtream source, with exactly one link: the source whose login `fetchUrl` adds. Never merged (not in the migration, Add, sync or import): two accounts on one server have the same stored URL `<server>/xmltv.php` but different logins and channels. The Edit dialog shows no "Use for" list for it. Identified as today: `isAutoXmltvSource(source, linkedProvider.url)` on its only link. |
| OG-D4 | The row that keeps its id after a merge | The row with the newest `last_ingested_at_ms` (tie: the lowest id). Its index data, stats, validators, hash, failure columns and v19 offsets stay, so the guide has no gap. |
| OG-D5 | The guide's sync key after a merge | The lowest `source_key` of the group. It uses only synced data, so every device that migrates the same rows selects the same key. |
| OG-D6 | Old keys | New table `epg_source_alias` (`alias_key` PK, `source_id`, `provider_id` nullable). The migration adds each merged row's old key with that row's source. An alias with a source means "an older build's row for (guide, source)". An alias with no source means "the same guide under another key" (two devices added it at the same time). |
| OG-D7 | Sync traffic from the migration | None. The migration sets `sync_clock.applying = 1`, as `MIGRATION_16_17` does. It builds the table again, so no delete trigger fires and no tombstone is sent. Every updated device merges its own rows in the same way. |
| OG-D8 | Index rows of the merged rows | A startup step deletes them (Design C). |
| OG-D9 | Refresh | Each run covers the distinct union of guides. A guide is downloaded one time per run, whatever the number of its sources. |
| OG-D10 | Channel matching | Per source, by fan-out. An airing of a shared guide is matched against each linked source of the profile. A search result gives one airing per source that has the channel, as the N rows do today. |
| OG-D11 | Guide Sources page | A flat list of guide cards, each shown one time, with a "Used for" line. Below the cards, the sources with no guide, each with its own Add (Design U). |
| OG-D12 | `enabled` | Stays per guide. "Use for" says which sources read the guide. There is no switch per link. |

## Answers (user, 2026-11-03)

| # | Answer |
|---|---|
| OG-Q1 | (a) The row edited last wins. |
| OG-Q2 | (a) The shortest interval that is on. |
| OG-Q3 | (a) One record per guide; an older build sees a shared guide only for its first source; the user updates every device. |
| OG-Q4 | (a) Link the chosen sources to the existing guide. |
| OG-Q5 | **(b) Normalised.** Two URLs are the same when they match after trim, with the scheme and host in lower case, no trailing `/` on the path, and no default port (`:80` for http, `:443` for https). One helper, `GuideUrls.matchKey(url)` in core/network/.../xmltv/, gives the key; the migration, Add, the sync applier and the import all use it. The stored URL stays as typed (the survivor row's URL after a merge). The path and the query stay as they are: a different path or query is a different guide. |
| OG-Q6 | (a) One entry per guide, with a `providers` list. |
| OG-Q7 | (a) The guide is on when one row is on; only the sources whose row was on get a link. |
| OG-Q8 | (a) Delete for all sources; the confirmation names them. |

## Open questions (answered, kept for the options)

| # | Question | Options |
|---|---|---|
| OG-Q1 | Merged rows have different offsets | (a) **The row edited last wins** (highest `sync_version.hlc` of the group's keys; tie: the guide-key row). Recommended: it is what the user set last, and synced devices get the same result. `EpgOffsetShift.reconcile` then moves the stored times. (b) Do not merge rows with different offsets: the URL then has two guides and downloads two times. (c) The most common value. |
| OG-Q2 | Merged rows have different refresh intervals | (a) **The shortest interval that is on.** Off only when all rows are off; null rows are ignored when another row has a value. Recommended: a guide that serves more sources refreshes as often as the most demanding one. (b) The row edited last wins. |
| OG-Q3 | Older app versions on linked devices | (a) **One record per guide. Keep the kind `epg_source`, add `providerKeys`, keep `providerKey` as the first linked source.** An older build sees a shared guide only for its first source; its own rows for the other sources stay. Deletes and unlinks reach it through alias tombstones. The user updates every device, as for the Admin rename. Recommended. (b) As (a), plus one older-style record for each (guide, source) link: an older build sees everything, but this is the double sync this plan stops, and more code that a later change must remove. (c) A new kind `epg_guide`: a clean break, an older build gets no guide change until it is updated. |
| OG-Q4 | Add with a URL that is already a hand-added guide | (a) **Link the chosen sources to the existing guide; keep its label, offset and interval; say "Already a guide source: added for <sources>".** Recommended. (b) Refuse with a message. |
| OG-Q5 | Which URLs are "the same" | (a) **The same text after trim.** Recommended: no risk of a wrong merge. (b) Normalised (case of scheme and host, a trailing `/`, the default port). |
| OG-Q6 | Export file format | (a) **One entry per guide, with a new `providers` list (name, URL); keep `providerName` and `providerUrl` as the first source for older builds.** Recommended. (b) One entry per (guide, source), as today; import merges by URL. |
| OG-Q7 | Merged rows that are off | (a) **The guide is on when one row is on; only the sources whose row was on get a link.** Each source keeps what it read before. When all rows are off, all sources get a link and the guide is off. Recommended. (b) All sources get a link. |
| OG-Q8 | Delete a guide that several sources use | (a) **Delete it for all sources; the confirmation names them.** To remove one source only, use "Use for" in Edit. Recommended. (b) Ask "Remove for <source> only" or "Delete for all". |

## Design

### D. Data model (`providers.db` v20)

```sql
CREATE TABLE `epg_source_provider` (`source_id` INTEGER NOT NULL, `provider_id` INTEGER NOT NULL,
  `linked_at_ms` INTEGER NOT NULL, PRIMARY KEY(`source_id`, `provider_id`));
CREATE INDEX `index_epg_source_provider_provider_id` ON `epg_source_provider` (`provider_id`);
CREATE TABLE `epg_source_alias` (`alias_key` TEXT NOT NULL, `source_id` INTEGER NOT NULL,
  `provider_id` INTEGER, PRIMARY KEY(`alias_key`));
CREATE INDEX `index_epg_source_alias_source_id` ON `epg_source_alias` (`source_id`);
```

- New entities `EpgSourceProviderEntity` and `EpgSourceAliasEntity` in core/network/.../provider/,
  added to `@Database(entities)`. `DB_VERSION = 20`.
- `EpgSourceEntity`: remove `providerId` and `Index("provider_id")`. Keep the unique index on
  `source_key`. No unique index on `url`: automatic guides can share a URL (OG-D3). The code keeps
  hand-added URLs unique.
- `EpgSourceDao` (the "first" link is the one with the lowest `linked_at_ms`, then `provider_id`):
  - Keep these names and change them to a JOIN on `epg_source_provider`, so most callers compile
    with no change: `getSourcesForProvider(providerId)`, `getSourceIdsForProvider`,
    `getEnabledSourcesForProvider`.
  - Add `getEnabledSourcesForProviders(ids)` (`SELECT DISTINCT s.*`), `getFailedSourcesForProviders(ids)`,
    `getProviderIds(sourceId)`, `getAllLinks(): Flow<List<EpgSourceProviderEntity>>`,
    `getAllLinksOnce()`, `getSourcesByUrl(url)`.
  - Add, as `@Transaction`: `insertGuide(source, providerIds)`, `setLinks(sourceId, providerIds)`,
    `unlink(sourceId, providerId)`, `deleteGuide(id)` (also deletes its links and aliases).
  - Remove `getSourceByUrl(url, providerId)`, `deleteSourcesForProvider`, `getFailedSources(providerId)`.
- `SettingsSyncTriggers.install`:
  - Remove `provider_id` from the `changed(...)` list of `sync_epg_source_update`.
  - Add `sync_epg_source_provider_insert` and `sync_epg_source_provider_delete`. They queue the
    parent guide's `source_key` with `WHEN NOT_APPLYING AND EXISTS (SELECT 1 FROM epg_source WHERE
    id = NEW/OLD.source_id)`. Without the `EXISTS` guard, deleting links after their guide inserts a
    NULL `itemKey` into `sync_version`, and the transaction fails.

### M. Migration 19 → 20 (`SettingsDatabase.MIGRATION_19_20`)

1. `UPDATE sync_clock SET applying = 1`: the live sync triggers from an earlier open still exist.
2. Create `epg_source_new` (every column but `provider_id`), `epg_source_provider` and
   `epg_source_alias`.
3. In Kotlin, read every row with its provider (`LEFT JOIN providers`):
   - **Automatic rows** (provider type `XTREAM` and `AutoXmltvSources.isAutoXmltvSource(url, label,
     provider.url)`): one group each.
   - **Rows whose provider is gone**: one group each, with no link. Shrink Database deletes them
     later, as today.
   - **All other rows**: grouped by `GuideUrls.matchKey(url)` (OG-Q5).
4. For each group, build one guide:
   - id, stats, validators, `last_content_sha256`, `ingest_method`, `file_utc_offset_minutes`,
     `applied_utc_offset_minutes`, failure columns: from the survivor row (OG-D4).
   - `source_key`: the lowest key (OG-D5). `label`: from the row with that key.
   - `timezone_offset_hours`: OG-Q1. `refresh_interval_hours`: OG-Q2. `enabled` and the links: OG-Q7.
   - `added_at_ms`: the lowest of the group.
   - Links: `linked_at_ms` = the row's `added_at_ms`; the guide-key row's link gets 0, so it stays
     the first link and older builds keep the same `providerKey`.
   - Aliases: every other key of the group, with the source of its row.
   - Keep the `sync_version` rows of the alias keys: the alias path uses them for last-writer-wins.
5. `DROP TABLE epg_source`, `ALTER TABLE epg_source_new RENAME TO epg_source`, create
   `index_epg_source_source_key` (unique) again. `DROP TABLE` fires no delete trigger.
6. `UPDATE sync_clock SET applying = 0`.
7. Add to `getInstance`'s `addMigrations`. Commit `core/network/schemas/...SettingsDatabase/20.json`.

### C. Index cleanup after the migration

- Add `EpgIndexer.purgeOrphanedSources()`, modelled on `purgeXtreamApiSources()`:
  1. `SELECT DISTINCT source_id FROM epg_channel` (cheap, uses `idx_channel_source`).
  2. Read the ids in `epg_source`.
  3. Stop when `epg_source` is empty or cannot be read (circuit breaker).
  4. `deleteBySourceIds(orphans)` under `writeMutex`.
  5. When something was deleted: `rebuildFtsAndUpdateState()`, then `EpgOffsetShift.reconcile` for
     each guide (a merged guide can have a new offset).
- Called from a new `startupStep("purgeOrphanedGuideSources")` in
  `FijerenaApplication.startBackgroundWork()`, after `purgeXtreamApiSources`, behind `SafeMode` and
  `ProvidersDbGuard`. It runs at every start; with nothing to delete it is one cheap query. It also
  heals orphans that other paths leave.

### R. Refresh (core/network/.../xmltv/)

- `EpgFileManager`:
  - `_refreshProviderId: Long?` becomes `_refreshProviderIds: Set<Long>?`; `stateFor(ids)` shows the
    state when the run's set has a source in `ids`.
  - `processAllSourcesInternal` sets the set from `getProviderIds` of its sources.
  - `launchRefreshStale(providerIds)` and `launchRefreshFailed(providerIds)` make one run on
    `getEnabledSourcesForProviders` / `getFailedSourcesForProviders`, not one loop per source.
  - `launchProcessSingleSource(sourceId, …)` loses its `providerId` parameter.
  - `invalidateXmltvCache` clears `XmltvEpgService` for every linked source of each ingested guide.
  - `migrateFromAppSettings` uses `insertGuide(source, listOf(ownerId))`.
  - Remove the comments that say "a source belongs to exactly one provider".
- `EpgSyncWorker`: `sourcesToRefresh` returns one list, distinct by id, for all `providerIds`;
  `doWork` makes one `processAllSources` run. Keep the retry rule (every source failed).
- `EpgRefreshSchedule`: no change (per guide row).
- `refreshOutdatedSources(providerId)`: keep the API; the JOIN gives the right rows.

### G. Guide reading per source

- `GuideScope`:
  - `providerIdBySourceId: Map<Long, Long>` becomes `providerIdsBySourceId: Map<Long, List<Long>>`,
    in the profile's source order. `sourceIds` are the distinct keys.
  - `of(providers, enabledSources)` appends each provider to each of its guides.
- `GuideAiringMatcher`: `providerIdsOf(sourceId)`; `matchAll(sourceId, channelId, name)` returns one
  match per linked source that has the channel; `match` returns the first match that is not hidden;
  `isHidden` stays per match, so a child's filter stays per source.
- `EpgBrowserViewModel.performSearch`: one `EpgBrowserAiring` per matching source; with no match, one
  airing on the first source, as today. Paged search: keep a row when it has no match, or when at
  least one match is not hidden. `sourcesFlow`: guides linked to a searched source (`getAllSources`
  combined with `getAllLinks`). `refreshStale` keeps calling `launchRefreshStale(providerIds)`.
- `EpgGuideLookup.toProgrammes`: one `GuideProgramme` per linked source, so `GameChannelMatcher`
  works with no change.
- `XmltvEpgService`, `MediaRepository.hasGuideForSource`: no change (the JOIN query).
- `OwnXmltvIndex`: keep only the automatic guide whose only link is this source.
- `filterToStreams` (EpgBrowserModels.kt): no change (per airing `providerId`).

### A. Automatic Xtream guide (`AutoXmltvSources`)

- `plan(providerId, providerUrl, sources, links, …)`: `own` = the guides linked to `providerId`;
  `autos` = the automatic guides whose only link is `providerId`; inserts through
  `insertGuide(…, listOf(providerId))`; deletes through `deleteGuide`.
- `loginUrl` / `fetchUrl` / `syncUrl`: the provider is the guide's only link
  (`getProviderIds(id).singleOrNull()`). The same for `onEmptyIngest` and `cleanUp`.
- `cleanUpOnce` already ran on most installs; it only needs to compile.
- New helper `AutoXmltvSources.ownerOf(source, linkedProviders): ProviderEntity?`: the source when
  the guide is automatic. The UI, the applier and the import use it.

### P. Deleting a source, Shrink Database (`ProviderRepository`)

- `deleteProviderEpgSources(id, recordTombstones)`:
  - A guide linked to `id` with another link: unlink only; the guide and its index data stay.
  - A guide with no other link: `deleteBySourceIds` in the index, then `deleteGuide`.
  - A deletion from another device (`!recordTombstones`): all inside `setApplying(true)`, as today.
  - A local deletion: the link trigger queues the guide (new provider list); Lane S adds tombstones
    for the source's alias keys.
- `pruneOrphanedCatalogData(userRequested = true)`: remove links to missing sources, then delete the
  guides with no link, with their index rows and no tombstones (F-11).
- `getGuideSources(providerId)`: no change (JOIN). Edit Source counts the guides the source uses.

### E. Export and import (`SettingsExportManager`)

- With OG-Q6 (a): `ExportedEpgSource` gets `providers: List<ExportedSourceRef>` (name, URL), default
  empty. Export writes one entry per hand-added guide; `providerName` / `providerUrl` = the first
  link. Automatic guides stay out, as today (`isXtreamOwnGuide`).
- Import: the refs are `providers`, else the legacy pair. Find each source on the device. Skip
  automatic guides. When a hand-added guide with that URL exists, add the missing links (skipped when
  nothing changed); else `insertGuide(…, owners)`.

### U. Guide Sources page, TV and phone

- `EpgManagementViewModel`:
  - `GuideGroup` becomes `GuideCard(source, usedBy: List<ProviderEntity>, ownGuideOf: ProviderEntity?,
    usedByProfile: Boolean)`.
  - `cards`: sorted by the first linked source's place in the Sources list, then `added_at_ms`.
  - `sourcesWithoutGuide: List<ProviderEntity>`: the sources with live channels and no link.
  - `guideProviders` stays the "Use for" list.
  - `addSource(url, …, providerIds)`: one guide per URL, with links (OG-Q4 for an existing URL).
  - `updateSource(source, providerIds)`: writes the fields, then `setLinks` (hand-added only, at
    least one source).
  - `deleteSource(id)`: `deleteGuide` plus index rows. `setSourceEnabled`: uses `ownGuideOf`.
    `refreshSource`: `launchProcessSingleSource(sourceId)`.
- `TvEpgManagementScreen` / `MobileEpgManagementScreen`:
  - The refresh row first, as today. Then one card per guide, with a "Used for: A, B" line (new
    string `epg_source_used_for`; names joined in code), plus "Not used by this profile" when no
    linked source is in the profile. The own-guide hint stays on the automatic card.
  - After the cards: a "Sources Without a Guide Source" heading (Title Case in English), one row per
    source with its name and an Add icon action that opens Add with that source checked. This
    replaces the empty groups.
  - Entry focus (TV focus contract): opened on `providerId`, focus goes to the first card that
    source uses, else to its Add row. Opened at the top, focus goes to the first card.
  - `groupHeaderIndex` becomes `cardIndex`.
- `EpgSourceEditDialog` (both apps): the "Use for" check list shows on Add **and** Edit for a
  hand-added guide when there are two or more sources; Save needs at least one source. An
  automatic guide shows "Used for: <source>" as text, with no list.
- Delete confirmation: with `usedBy.size > 1`, the text names the sources (OG-Q8).
- Strings in core/ui `values`, `values-fr` and `values-mg` in the same change. Media sources are
  "sources"; guides are "guide sources". Malagasy follows the user's glossary.
- `ProviderGuideSection` (TV and phone): no code change.

### S. Live sync

- `SyncPayloads.EpgSource`: add `providerKeys: List<String> = emptyList()`; `providerKey` stays the
  first link's key (OG-Q3 a).
- `LocalRecords` (`EPG_SOURCE`): `providerKeys` = the keys of the links in link order; `null` when no
  link has a key, as today. An automatic guide still goes with its login (`AutoXmltvSources.syncUrl`).
- `SyncApplier.applyEpgSource`:
  - `keys = providerKeys.ifEmpty { listOf(providerKey) }`.
  - Presence: an unknown key defers (as today); all keys deleted: `Skip(DELETED_PROVIDER)`;
    otherwise apply with the known keys.
  - Target, in this order: (1) the guide with this `source_key`; (2) an alias with this key; (3) for
    an automatic record, that source's automatic guide; (4) a hand-added guide with the same URL —
    when the server does not know it yet (first sync), adopt the key (`adoptSourceKey` plus
    `renameVersionKeys`), the links become the union, and the guide is queued again when the union
    differs from the record; when the server knows it, add an alias (with the source for a
    single-key record, without for a multi-key record); (5) none: `insertGuide` with links.
  - Upsert on the guide key: write the fields, replace the links with the known keys.
  - Upsert on a source alias (older build): write the fields to the guide and make sure that
    source's link exists, with `SyncMerge.Local(version = max(version(alias), version(guideKey)))`.
  - Delete on the guide key: `deleteGuide` plus index rows. Delete on a source alias: unlink that
    source, and delete the guide when no link is left. Delete on a guide alias: delete the guide.
- Outgoing tombstones for aliases: local `deleteGuide` and `unlink` insert an `epg_source` tombstone
  for each alias key they remove (`deletedAt = 0`); the `sync_tombstone_insert` trigger stamps and
  queues it, so older devices delete their row for that source.
- `SettingsSyncDao`: `sourcesAt(providerId, url)` becomes `guidesAtUrl(url)` and
  `autoGuideOf(providerId)`. `adoptProviderKey` needs no change (links use ids).
- The sync server needs no change: records are opaque.
- Older builds with OG-Q3 (a): they see a shared guide only on its first source; a guide that an
  updated device adds for several sources reaches them only for the first source. Their own changes
  reach updated devices through aliases. The release notes ask to update every device.

## Risks

1. **A wrong merge of two accounts on one server.** OG-D3 excludes automatic guides; a test covers it.
2. **A migration failure.** Room runs the migration in one transaction, so a failure rolls back. The
   index cleanup runs later, and again at the next start when it fails.
3. **A long first start on a slow TV.** Deleting the merged rows' programmes (possibly millions of
   rows) runs in the background under `writeMutex`; search stays usable (WAL); one FTS rebuild. The
   cost is that of deleting a guide source today.
4. **Older devices in the group.** See OG-Q3.
5. **A record deferred for ever** when a source key is unknown and its tombstone was pruned (90
   days). The same risk exists today.
6. **A merged guide with an unknown offset.** `EpgOffsetShift` returns `NEEDS_IMPORT`; the guide
   downloads one more time.
7. **A child's filter bypassed through fan-out.** `isHidden` is checked per source; a test covers it.
8. **Regressions on main between Phase 1 and the lanes.** Phase 1 keeps the old behaviour where it
   can. No release until every lane is merged.
9. **The link-delete trigger after a guide delete.** The `EXISTS` guard prevents the failure; a test
   covers it.
10. **TV focus after the list changes.** `GuideSourcesFocusTest` is written again.

## Tests

- **JVM (Robolectric) `SettingsDatabaseMigration19To20Test`**, in the style of
  `SettingsDatabaseMigration18To19Test`: two rows with one URL become one guide with two links and
  one alias; the survivor keeps its id, stats and v19 offsets; the key is the lowest key; the OG-Q1,
  OG-Q2 and OG-Q7 rules; two automatic rows on one server stay apart; a row whose source is gone
  stays with no link; nothing is queued in `sync_version` and no tombstone is added;
  `index_epg_source_provider_id` is gone.
- **androidTest**: `MIGRATION_19_20` in the list of `SettingsDatabaseMigrationTest`;
  `SettingsSyncTriggersTest` with the link triggers and the `EXISTS` guard; `SyncApplierTest`.
- **core/network**: `EpgIndexer.purgeOrphanedSources` (deletes orphans; nothing on an empty
  `epg_source`; FTS rebuild only after a delete); `GuideScopeTest` (multi-source map, `matchAll`,
  hidden per source); `GameChannelGuideLookupTest` (fan-out); `EpgSyncWorker.sourcesToRefresh`
  (distinct); `EpgFileManagerChangeDetectionTest`, `SyncFailureCountTest` (fixtures without
  `providerId`); `AutoXmltvSourcesTest` (links; never merged); `SyncApplierEpgSourceTest` (guide
  key, source alias, guide alias, URL merge, first-sync adoption with union, presence rules, alias
  tombstones on delete and unlink); `SettingsExportEpgIntervalTest` and a new export test (the
  `providers` list; legacy entries merge by URL); `ProviderRepository` deletion (a shared guide
  stays; the last link deletes it).
- **core/ui**: `GuideSourceGroupsTest` written again as `GuideSourceCardsTest` (cards, sources
  without a guide, Add to an existing URL, Edit "Use for"); `EpgManagementRefreshIntervalTest`,
  `EpgViewModelMultiSourceTest`.
- **TV Compose (Robolectric)**: `GuideSourcesFocusTest` (entry on `providerId`; the Add row of a
  source without a guide; Back from the dialogs) and `SettingsSourcesGuideFocusTest`.
- **Emulator walks**, TV and phone, in Malagasy (TV in English for the focus walk):
  1. Install the previous release. Add two fake-xtream sources (tools/) and one hand-added XMLTV for
     both, with different offsets. Refresh.
  2. Deploy the new build over it with the deploy script.
  3. One card with "Used for: A, B"; the Nerd stats show fewer channels; the TV Guide of both
     sources shows programmes; Search the Guide finds airings on both, with badges; the offset
     follows OG-Q1.
  4. Add a guide for two sources: it downloads one time (one progress row).
  5. Edit "Use for": remove B, then add it again.
  6. Delete source B (the guide stays). Delete A (the guide goes).
  7. Two accounts on one fake server: two automatic cards.
  8. Live sync between both emulators on the new build: edit, unlink, delete.
  9. With OG-Q3 (a), the previous release on the phone: TV changes reach it, and it keeps working
     (first source).
  10. Export, Clear All Data, Import.
  11. A child profile: no airing on a hidden channel.

## Parallel execution (max 4 agents)

```
t0  Phase 0 (lead)  answers recorded (done)
t1  Phase 1 (lead, on main, alone)  schema v20, migration, DAO, cleanup hook,
                                    automatic guide, source deletion, every caller compiles
t2  ┌ Lane R (agent) refresh ─────────────┐
    ├ Lane G (agent) guide reading ───────┤
    ├ Lane S (agent) sync, export, import ┤
    └ Lane U (agent) Guide Sources page ──┘
t3  lead merges in the order the lanes end; full suite; lint; gates
t4  Phase W (lead) emulator walks, docs, release notes
```

- Phase 1 blocks every lane: it removes `providerId`, and each caller must compile in the same
  commit. Phase 1 keeps the behaviour where it can: `groups` shows a shared guide in each of its
  groups for now; `GuideScope` takes the first source; the applier links the payload's `providerKey`.
- **File ownership**:
  - Lane R: `EpgFileManager`, `EpgSyncWorker`, `XmltvEpgService` cache invalidation. It keeps the
    signatures of `launchRefreshStale` and `launchRefreshFailed`.
  - Lane G: `GuideScope`, the whole of `EpgBrowserViewModel`, `EpgGuideLookup`, `OwnXmltvIndex`.
  - Lane S: `SyncPayloads`, `LocalRecords`, `SyncApplier`, `SettingsSyncDao`, alias tombstones in
    `EpgSourceDao.deleteGuide` / `unlink`, `SettingsExportManager`.
  - Lane U: `EpgManagementViewModel`, both screens, the strings.
- **At merge (lead)**: conflicts in `docs/FEATURES.md` (U, S), `docs/epg_guide.md` (R, G, U) and the
  CONTRIBUTING EPG section (R, G).
- Each agent works in its own worktree and branch, with `local.properties` linked, makes one commit
  with its Done note and docs, and runs only the fast checks: ktlint, the tests of the modules it
  touches, compile, `scripts/check-*.sh`.

## Phases

| Phase | Owner | Work | Tests | Docs |
|---|---|---|---|---|
| 0 | lead | Open questions answered | none | This plan's decisions |
| 1 | lead | Design D, M, C, A, P; minimal caller changes; triggers | Migration 19→20 JVM; androidTest list; orphan purge; `AutoXmltvSourcesTest`; deletion; every fixture without `providerId` | DATABASE_SCHEMA (v20, the two tables, triggers, `epg_source` without `provider_id`); CONTRIBUTING schema history |
| R | agent | Design R | `sourcesToRefresh` distinct; `stateFor` set; cache invalidation per link | epg_guide.md (EpgFileManager, EpgSyncWorker); CONTRIBUTING EPG |
| G | agent | Design G | `GuideScopeTest`, lookup fan-out, hidden per source, paged filter | epg_guide.md (Search the Guide); FEATURES (Search the Guide) |
| S | agent | Design S, E | `SyncApplierEpgSourceTest`, triggers, export and import | FEATURES (Live sync, Export); DATABASE_SCHEMA (sync triggers, alias) |
| U | agent | Design U | `GuideSourceCardsTest`, `GuideSourcesFocusTest` | FEATURES (Guide Sources); NAVIGATION_GUIDE (entry focus) |
| W | lead | Merge, walks | Full suite, `lintDebug`, gates, walks on both emulators | RELEASE_NOTES (update every device: OG-Q3); docs/plans/README.md row; this plan replaces GS-D1 "one row per source" of the one guide sources page plan |

## Progress

| Phase | Status | Commit |
|---|---|---|
| 0 | Done 2026-11-03: answers recorded | none |
| 1 | Not started | none |
| R | Not started | none |
| G | Not started | none |
| S | Not started | none |
| U | Not started | none |
| W | Not started | none |
