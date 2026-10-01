# Profile-Scoped Settings Plan

**Status:** Complete (2026-09-30)

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
- **Excluded flags: recompute on profile switch** (not a per-profile exclusion table).
  *Superseded 2026-10-01:* rewriting item flags took 30–50 s per switch on a Shield; only
  categories carry the flag now — see `docs/plans/20261001_fast-profile-switch-plan.md`. Xtream
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

**Storage (as built — SharedPreferences, not the Room table first planned).** One
`category_filters` prefs file, key `<providerId>_<profileId>`, value the `CategoryFilters` JSON
(same shape as before). `CategoryFiltersStore` wraps it. A table was the first plan, but
`MediaProviderFactory.create` and the Xtream content code read filters synchronously, some of it
on the main thread, where Room can't be queried. Provider and profile deletion remove their keys
by hand instead of by cascade. No Room migration.

**Upgrade.** `ProviderRepository.migrateCategoryFiltersToProfiles`, at startup: each provider's
filters still in `providers.providerSettings` (legacy `prefixes` shape normalised by the decoder)
are copied to every profile without its own key, then stripped from the JSON. Until it has run,
a missing key falls back to the JSON's filters, so nothing changes in the meantime. It replaces
`migrateLegacyCategoryFilterPrefixes`, whose job it also does.

**Reading and writing.** `ProviderSettings` keeps its `categoryFilters` field in memory, so the
UI and `MediaRepository` stay unchanged:

- `ProviderRepository.getProviderSettings(providerId)` overlays the active profile's filters (no
  key = the JSON's, empty once migrated).
- `ProviderRepository.updateProviderSettings` splits the write: filters to the active profile's
  key, everything else to `providers.providerSettings`. The existing immediate recompute stays.
- `settingsCache` is keyed by (providerId, profileId).
- A new provider has no keys (everything visible); copying a provider copies every profile's
  keys; adding a profile copies the active profile's keys.
- `XtreamContentManager` reads the active profile's filters afresh on every load and sync (a
  `categoryFilters` supplier from `XtreamRepository`) instead of the snapshot it was built with,
  so a sync still running across a profile switch applies the new profile's filters.

**Applying on switch.** `AppContainer.switchProfile`, under its existing mutex: for each Xtream
provider whose filters differ between the old and new profile, run
`XtreamCategoryExclusionSync.recompute` with the new profile's filters and drop the cached
provider instance and EPG matcher (as a filter edit does). Identical filters (the common case right
after upgrade) cost nothing. M3U, Jellyfin and Local filter in memory through
`MediaRepository`, which is rebuilt on switch already.

**UI.** Filters stay where they are: provider edit screen (Settings → Manage Providers → ⋮) →
category filters, on `:tv` and `:mobile`. Saving there changes the **active profile's** filters
for that provider. The filter section gains one line naming the profile it applies to. No other
UI change.

**Export.** A provider's exported `providerSettings` includes the **active profile's** filters
(same JSON as today); import writes them to the active profile. Format stays version 5.

## Steps

1. Dev mode per profile — `AppSettings`, upgrade step, profile-deletion cleanup, export, unit
   tests. One commit.
2. Filters per profile — prefs store + startup upgrade + schema doc, repository overlay/split,
   switch recompute, live filters in the Xtream content code, add/copy provider and add profile,
   export, UI note, unit + instrumented tests. One commit.
3. Verify on the TV emulator: two profiles with different filters on one Xtream provider;
   switching changes visible categories; timing of the switch recompute on a large catalogue
   noted here. **Ask before installing** on any device.

## Risks

- Switch latency on a big Xtream catalogue when filters differ (recompute updates tens of
  thousands of stream rows). Measure in step 3; if it's slow, recompute lazily per provider on
  first use instead of all at once.
- EPG browser and search read `excluded` too — they follow the active profile through the same
  flags; no change needed, but step 3 checks them.

## Step 3 results (2026-09-30, TV and phone emulators)

- Instrumented tests: 6/6 pass on the TV emulator (`ProfileCategoryFiltersTest` fixed to create its
  own profiles — `DeleteDefaultProfileTest` in the same APK deletes `default`).
- Upgrade: on both emulators every provider's filters landed under every profile byte-for-byte,
  the rest of each provider's settings JSON unchanged; the install-wide dev-mode flag was copied to
  every profile and removed. Home counts unchanged (bearstv 268/876, 139/428, 90/352).
- New profile copied the creator's filters on all six filtered providers; dev mode started off (the
  dev-only "X of Y" counts and "(XTREAM)" label disappeared for it). The filter section named the
  profile. Removing two rules as the new profile raised its TV Shows from 90 to 107 while Default
  stayed at 90; switching back re-applied Default's. Deleting the profile removed its filter keys.
- **Switch latency:** a switch where bearstv's filters differ takes ~3.4 s from choosing the profile
  to home appearing, against 0.16 s when they don't — the recompute of ~283k catalogue rows. The
  picker gives no feedback meanwhile. The same cost already applied to saving a filter edit.
- **Fixed (same day):** `XtreamCategoryExclusionSync.recompute` now writes only the categories whose
  flag changes, and only their streams and series (`setExcludedForCategories`); the end of a content
  sync keeps the full re-derivation (`fullStreamSync = true`). The same switch now starts showing
  home after 0.17 s. After two switches every stream (235k) and series (47k) flag on bearstv still
  matched its category.

