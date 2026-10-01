# Fast Profile Switch Plan

**Status:** Complete (2026-10-01)

Make switching profile near-instant by no longer storing category-filter results on every
channel, movie and series row.

Context: `docs/plans/20260930_profile-scoped-settings-plan.md` → Decisions, "Excluded flags:
recompute on profile switch". This plan reverses that decision, with measurements.

## Problem

Category filters are per profile. Xtream applies them by setting an `excluded` flag on rows of
three tables in `xtream_v2.db`: `xtream_categories`, `xtream_streams` and `xtream_series`. A
profile switch re-runs `XtreamCategoryExclusionSync.recompute`, which rewrites the flag on every
category whose visibility changes, then on every stream and series in those categories.

The earlier plan expected about 3 s (280k rows on a TV emulator). Measured on a Shield
(192.168.68.21, 2026-10-01, `ProfileSwitch` log tag), with all the time in that rewrite for one
provider:

| Switch | Total | Rewrite |
|---|---|---|
| atr → kilonga | 51 s | 51 s |
| kilonga → rae | 46 s | 46 s |
| rae → atr | 39 s | 39 s |
| atr → kilonga | 34 s | 34 s |
| kilonga → atr | 32 s | 32 s |

No time is spent waiting for a lock or closing repositories, and a provider whose filters are the
same in both profiles takes 3–9 ms. The cost is the per-row update: each changed row also
rewrites the indexes that include `excluded`, on flash storage.

The picker now says "Switching to …" and ignores further picks (commit `67b9dfd7`), but 30–50 s
is still too long.

## Decision

**Keep the flag on categories only, and derive the visibility of streams and series from their
category at query time.**

- `xtream_categories.excluded` stays. It holds the visibility of each category for this device's
  active profile, as now. The active profile is per device and never synced, so one flag per
  category is enough. A switch rewrites only this table: about 1,700 rows for the bears catalogue
  (876 live, 428 movie and 352 series categories).
- `xtream_streams.excluded` and `xtream_series.excluded` are no longer read or written. A stream
  or series is hidden when its category is excluded.
- Items whose category isn't in `xtream_categories` stay visible. That's what the current
  `COALESCE(…, 0)` in `syncExcludedFromCategories` does.

Rejected alternatives:

- **A per-profile hidden-category table, joined into every query.** It would allow switching
  without writing anything, but every query would need the profile id. The active profile is per
  device, so the category flag already is per device and per profile. Rewriting 1,700 category
  rows is cheap enough.
- **Update only the provider being shown, and the others in the background.** The slow provider
  is the one being switched into, so this saves nothing.
- **One transaction per provider.** It would make the writes cheaper, but still writes hundreds
  of thousands of rows.

## Changes

### Queries (core/network, `xtream/db`)

Eleven queries read the item-level flag. Each `s.excluded = 0` becomes:

```sql
NOT EXISTS (
  SELECT 1 FROM xtream_categories c
  WHERE c.providerId = s.providerId AND c.type = :categoryType
    AND c.categoryId = s.categoryId AND c.excluded = 1
)
```

(and `s.excluded = 1` becomes `EXISTS (…)`). The lookup uses the primary key of
`xtream_categories` (`categoryId, providerId, type`), so it's one index probe per row.

- `XtreamStreamDao`:
  - by category (line 11)
  - all of a type (18)
  - same TMDB id (59)
  - sibling completed (94)
  - FTS search (190)
  - FTS hidden count (206)
- `XtreamSeriesDao`:
  - by category (11)
  - all (17)
  - same TMDB id (40)
  - FTS search (94)
  - FTS hidden count (109)

The category type for streams is the stream's own type (live or VOD); for series it's the series
category type. For the by-category queries the check is on one category, but stays for
correctness in case a hidden category is ever opened directly.

### Writes removed

- `XtreamStreamDao` / `XtreamSeriesDao`: `setExcludedForCategories` and
  `syncExcludedFromCategories`.
- `XtreamCategoryExclusionSync.recompute`: only sets category flags. `fullStreamSync` goes away.
- `XtreamContentManager`: stops computing `excluded` for streams and series on insert. It keeps
  the in-memory filtering of what it returns, and keeps setting category flags on insert.

### Schema (xtream_v2.db v23 → v24)

- Drop the two item-level indexes that include `excluded`
  (`index_xtream_streams_providerId_type_categoryId_excluded`,
  `index_xtream_series_providerId_categoryId_excluded`). This is cheap, and it also makes content
  sync writes lighter.
- **Keep the two `excluded` columns, unused.** Dropping a column needs SQLite 3.35. The minimum
  SDK is 30, which ships 3.28, so it would mean rebuilding both tables, around 280k rows each.
  Both have FTS tables keyed on their rowids, which the rebuild must preserve. That isn't worth
  the risk for two unused integer columns. The entity fields stay, marked unused since v24, and
  their values are never read. A later cleanup can drop them once the minimum SDK allows.
- Update `docs/DATABASE_SCHEMA.md` in the same commit (standing rule).

### Profile switch

`ProviderRepository.applyCategoryFiltersForSwitch` keeps its shape: compare filters, recompute
(now categories only), then `MediaProviderFactory.clearCache` for the cached category lists and
EPG matcher. The `ProfileSwitch` timing log stays.

### Filter edits

They use the same recompute, so editing a provider's filters becomes as fast as a switch.

## Verification

1. **Query cost before and after**, on a copy of the Shield's `xtream_v2.db` (pulled with
   `run-as`, read-only):
   - `EXPLAIN QUERY PLAN` shows a primary-key lookup on `xtream_categories`, not a scan.
   - Time each rewritten query against the current one on the host and on the Shield.
   - Acceptance: browsing and search no more than about 10% slower; paging stays instant to the
     eye.
2. **Switch time on the Shield:** under 1 s for atr ↔ kilonga (today 32–51 s), from the
   `ProfileSwitch` log.
3. **Tests:**
   - Unit tests for `XtreamCategoryExclusionSync` updated: only categories change.
   - A DAO test (androidTest) that streams and series follow their category's flag, including
     items with an unknown category.
   - A migration test for v23 → v24.
   - The existing profile filter tests (`ProfileCategoryFiltersTest`) still pass.
4. **Manual, on the Shield and phone:**
   - Category counts ("268 of 876") are right per profile.
   - Search hides filtered items and reports the hidden count.
   - Continue Watching and same-title siblings behave as before.

## Results (2026-10-01)

**Query form.** Measured on a copy of the Shield's `xtream_v2.db` (54.6k live, 180.8k movies,
47.5k series, 1,656 categories; host timings, best of 5). Every rewritten query returned exactly
the rows of the current one. Three forms were tried:
- A per-row `EXISTS` on the category: +0–29% on big lists.
- A correlated `IN`: up to 7× slower. Rejected.
- **`categoryId NOT IN (SELECT categoryId … WHERE providerId = :providerId AND type = … AND
  excluded = 1)`**, evaluated once per query: chosen.

| Query (chosen form, v24 indexes) | Before | After |
|---|---|---|
| Streams by category (9.5k rows) | 21 ms | 23 ms |
| All movies (154k rows) | 384 ms | 407 ms |
| All live (54k rows) | 114 ms | 128 ms |
| All series (36k rows) | 129 ms | 137 ms |
| Movie / series search | 1.5 / 0.6 ms | 1.6 / 0.6 ms |
| Hidden-match count (search) | 2 ms | 28 ms |
| TMDB siblings | 0.1 ms | 0.1 ms |

The hidden-match count is the only notable rise; it runs once per search.

**On the Shield** (192.168.68.21, after a full backup):
- The v24 migration kept everything: 255 watch-history rows, 128 favourites, 235k streams.
- A switch now takes **225 ms** (atr → kilonga) and **124 ms** (kilonga → atr), down from 32–51 s.
- Home shows the counts the database predicts for each profile: atr 862 / 349 / 271 visible.

**Also removed:** each streaming catalogue sync fetched the category list again only to set item
flags; that network call is gone.

**Tests:**
- `CategoryVisibilityTest` (new): items follow their category, items with an unknown category
  stay visible, a stale item flag is ignored, search hides and counts.
- `migration23To24_…` (new); the earlier migration tests now run through v24.
- `ProfileCategoryFiltersTest` and the unit tests pass.

Instrumented tests were run on the phone emulator with `am instrument` on the library's own test
package, not `connectedAndroidTest`, which the repo blocks because it uninstalls apps.

## Steps

1. Measure the current queries on the Shield's database copy (baseline).
2. Rewrite the eleven queries and remove the item-level writes; update unit tests.
3. Migration v24 (drop the two indexes), schema doc, migration test.
4. Measure again (queries and switch time), on the Shield.
5. Commit; update the profile-scoped settings plan's decision to point here.

Install on devices only with the user's go-ahead, backing up app data first.
