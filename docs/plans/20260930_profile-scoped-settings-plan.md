# Profile-Scoped Settings Plan

**Status:** Proposed (2026-09-30)

Move two settings from device/provider scope to the profile:

1. **Developer mode** — today one flag for the whole install (`AppSettings.isDevMode`). Becomes one
   flag per profile.
2. **Category filters** — today one set per provider (`ProviderSettings.categoryFilters`: rules,
   include/exclude mode, allowed scripts). Becomes one complete set per (profile, provider).

Context: `docs/plans/20260929_live-sync-plan.md` → User profiles.

## Decisions (2026-09-30)

- **Filters: each profile has its own complete set per provider** — no provider-level base with
  profile additions on top. One editor, no merge rule.
- **Upgrade: copy to every profile.** The current dev-mode flag and each provider's current
  filters are copied to every existing profile, so nobody's view changes on upgrade.
- **Excluded flags: recompute on profile switch** (not a per-profile exclusion table). Xtream
  applies filters by setting `excluded` on the shared `xtream_categories` / `xtream_streams` /
  `xtream_series` rows, read by ~34 queries. Keeping that and re-running the existing local
  `XtreamCategoryExclusionSync.recompute` on switch leaves every query untouched. A per-profile
  exclusion table joined into every query was rejected: large rewrite for no user-visible gain.
- **Live sync plan updated** to match (both settings sync per profile).

- **New profile:** copies the active profile's filters for every provider; dev mode starts off.
- **New provider:** no filters for any profile (everything visible), as today: the filter editor
  only exists on the provider's edit screen, not while adding it.

## Design

### Developer mode

- Stored per profile in `app_settings` prefs as `dev_mode_<profileId>`. Prefs, not a `profiles`
  column: `isDevMode` is read synchronously from ~40 call sites, some on the main thread.
- `AppSettings.isDevMode` get/set read and write the **active profile's** key. No call site
  changes. Background work (EPG sync, provider sync, metrics) follows the device's active profile.
- One-time upgrade: if the legacy `dev_mode` key exists, write its value to `dev_mode_<id>` for
  every profile, then remove `dev_mode`.
- New profiles start with dev mode off (no key = off). Profile deletion removes the key.
- Export: `global.isDevMode` keeps its place in the version 5 format and carries the **active
  profile's** flag; import sets the active profile's flag (same rule as favorites and history,
  live-sync Decision 9).

### Category filters

**Storage.** New table in `providers.db` (v13):

```
profile_provider_filters(
  profileId  TEXT    NOT NULL REFERENCES profiles(id)  ON DELETE CASCADE,
  providerId INTEGER NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
  filters    TEXT    NOT NULL,   -- CategoryFilters JSON, same shape as today
  PRIMARY KEY (profileId, providerId)
)
```

A table rather than a map inside the provider's JSON: cascades clean up on profile and provider
deletion, and it maps one-to-one onto a sync record later.

**Migration 12→13.** Create the table; for each provider × each profile, insert the provider's
current `categoryFilters` (legacy `prefixes` shape normalised as today). Strip `categoryFilters`
from `providers.providerSettings` so there is one source of truth. Instrumented migration test
like `SettingsDatabaseMigrationTest`. `docs/DATABASE_SCHEMA.md` updated in the same commit.

**Reading and writing.** `ProviderSettings` keeps its `categoryFilters` field in memory, so the
UI, `MediaRepository` and `XtreamContentManager` stay unchanged:

- `ProviderRepository.getProviderSettings(providerId)` overlays the active profile's row (or empty
  filters if none).
- `ProviderRepository.updateProviderSettings` splits the write: filters to the active profile's
  row, everything else to `providers.providerSettings`. The existing immediate recompute stays.
- `settingsCache` is keyed by (providerId, profileId), or cleared on switch.
- A new provider has no rows (no row = empty filters); copying a provider copies every profile's
  rows; adding a profile copies the active profile's rows.

**Applying on switch.** `AppContainer.switchProfile`, under its existing mutex: for each Xtream
provider whose filters differ between the old and new profile, run
`XtreamCategoryExclusionSync.recompute` with the new profile's filters. Identical filters (the
common case right after upgrade) cost nothing. M3U, Jellyfin and Local filter in memory through
`MediaRepository`, which is rebuilt on switch already.

**Race with a running content sync.** `XtreamContentManager` finishes with a recompute using the
settings it started with. If the profile switched meanwhile, it would re-apply the old profile's
filters. Fix: that final recompute re-reads the active profile's filters instead of using the
snapshot.

**UI.** Filters stay where they are: provider edit screen (Settings → Manage Providers → ⋮) →
category filters, on `:tv` and `:mobile`. Saving there changes the **active profile's** filters
for that provider. The filter section gains one line naming the profile it applies to. No other
UI change.

**Export.** A provider's exported `providerSettings` includes the **active profile's** filters
(same JSON as today); import writes them to the active profile. Format stays version 5.

## Steps

1. Dev mode per profile — `AppSettings`, upgrade step, profile-deletion cleanup, export, unit
   tests. One commit.
2. Filters per profile — table + migration 12→13 + schema doc, repository overlay/split, switch
   recompute, sync race fix, add/copy provider and add profile, export, UI note, unit +
   instrumented migration tests. One commit.
3. Verify on the TV emulator: two profiles with different filters on one Xtream provider;
   switching changes visible categories; timing of the switch recompute on a large catalogue
   noted here. **Ask before installing** on any device.

## Risks

- Switch latency on a big Xtream catalogue when filters differ (recompute updates tens of
  thousands of stream rows). Measure in step 3; if it's slow, recompute lazily per provider on
  first use instead of all at once.
- EPG browser and search read `excluded` too — they follow the active profile through the same
  flags; no change needed, but step 3 checks them.
