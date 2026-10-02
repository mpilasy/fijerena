# Profile Remembers Its Provider Plan

**Status:** Proposed (2026-10-02) — not started

Each profile comes back to the provider it last picked, on every device. Profile A picks
provider X, profile B moves the device to provider Y; when A is picked again the device goes back
to X. The memory is synced: A picking X on the phone means A lands on X on the TV the next time
the TV switches to A.

Context: `docs/plans/20260929_live-sync-plan.md` → User profiles,
`docs/plans/20261001_fast-profile-switch-plan.md`.

## Decisions (2026-10-02)

1. **Synced** across devices, per profile — not per device.
2. **No memory yet** (new profile, upgrade, remembered provider deleted): **stay on the current
   provider**.
3. **Applied only on profile switch.** A remote change never moves a device that is already on
   that profile — the TV must not jump provider mid-playback because the phone picked another.
   The new value waits for the next switch to that profile.
4. **Only an explicit pick is remembered** — the provider pickers. Automatic changes (fallback
   after deleting the active provider, settings import, the switch itself) don't write it, so
   they never send a change to other devices.

## Today

- The current provider is one flag for the whole install: `providers.isActive` (one row = 1),
  set by `ProviderRepository.setActiveProvider` (`deactivateAll` + `activateProvider`). ~99 call
  sites read it (`getActiveProvider`, `ProviderViewModel`, `AppContainer.getMediaRepository(0L)`,
  EPG sync, list order).
- `AppContainer.switchProfile` sets `AppSettings.activeProfileId`, recomputes category filters,
  drops cached repositories. It never touches the provider.
- `isActive` is device state: not in `sync_providers_update`'s changed columns, not in payloads.
- Per-profile synced settings already exist: developer mode (`dev_mode_<profileId>`, `SETTING`
  kind with the profile as `profileKey`). This feature copies that path.

## Design

`providers.isActive` stays **the device's current provider** — no reader changes. A per-profile,
synced "last picked provider" is added and applied on switch.

### Storage

- `AppSettings` key `last_provider_<profileId>` → the provider's **`providerKey`** (UUID string),
  not its local `id`. Local ids differ per device; `providerKey` is what sync already uses.
- Read: `AppSettings.lastProviderKey(profileId): String?`. Resolved to a local row via
  `SettingsSyncDao.providerByKey` at switch time.

### Writing (local pick)

- New `ProviderRepository.pickProvider(id)`: `setActiveProvider(id)`, then look up the
  `providerKey` and store it for the active profile, then
  `SettingsSyncQueue.setting(context, KEY_LAST_PROVIDER, activeProfileId)`.
- Callers switched from `setActiveProvider` to `pickProvider`: TV/mobile
  `ContentTypeSelectionScreen`, TV/mobile nav hosts, `ProviderViewModel.selectProvider`.
- Left on `setActiveProvider` (no memory, no sync): delete fallback in
  `ProviderViewModel.deleteProvider`, `SettingsExportManager` import, `switchProfile`.
- Skip the write and the queue when the stored key already equals the picked one.

### Sync

Follows developer mode exactly:

- Add the key to `SYNCED_SETTING_KEYS`, and treat it as per-profile wherever `DEV_MODE_SETTING_KEY`
  is singled out today: `syncedSetting` (stored-key lookup, string value),
  `applyRemoteSetting` (write `last_provider_<profileId>` if the value is a string),
  `LocalRecords` seeding (one record per profile that has a key). Replace the two
  `key == DEV_MODE` checks with a small `PER_PROFILE_SETTING_KEYS` set.
- `applyRemoteSetting` only writes prefs — it does **not** call `setActiveProvider` (Decision 3).
- Conflicts: last writer wins per (profile, key) by HLC, as for every setting.
- Provider not here yet / deleted: nothing special at apply time — the value is just a string.
  Resolution at switch time finds no row and stays on the current provider. No deferral needed.
- Older app versions: `applyRemoteSetting` ignores unknown keys, so they drop it harmlessly.
- Payloads are encrypted end to end; the server needs no change.

### Applying on switch

In `AppContainer.switchProfile`, under the existing mutex, after `activeProfileId` is set and
before repositories are torn down:

1. `lastProviderKey(newProfileId)` → `providerByKey` → local row.
2. Row exists and isn't the current active one → `setActiveProvider(row.id)`.
3. Otherwise do nothing (Decision 2).

`switchProfileExternally` (live sync moving a device off a deleted profile) gets the same.
The nav hosts already rebuild from home after a switch, so home opens on the right provider.

Cost: one prefs read + one indexed lookup on every switch. When the provider changes,
`setActiveProvider` → `MediaProviderFactory.clearAllCaches()` also drops shared Xtream sessions
(today a switch keeps them), so that switch reconnects. Only when the provider actually differs.
Log it in the existing `ProfileSwitch` timing line.

### Cleanup

- Profile deleted (local or remote): remove `last_provider_<id>` next to `removeDevMode`.
- Provider deleted: leave keys alone — they're synced values, and the switch already ignores a
  missing provider. Removing them would need a sync tombstone for no gain.

### Edge cases

| Case | Behaviour |
|---|---|
| Upgrade, no keys anywhere | Every switch stays put until a profile picks a provider. |
| New profile | No key → stays on current provider. |
| Remembered provider deleted | Switch stays on current provider. |
| Remembered provider not yet synced to this device | Same — stays on current; next switch after it arrives picks it up. |
| Phone (A) picks Y while TV is on A watching X | TV's stored value becomes Y; TV stays on X until it next switches to A. |
| TV on A, picks X; phone on A picked Y earlier | Newest pick wins everywhere. |
| Jellyfin provider, profile has no login | Existing `shouldPromptSignIn` sends it to sign in. |
| Settings import | Not exported/imported (only remembered on pick). |
| EPG worker running during switch | Follows device's active provider, as today. |

## Not doing

- Moving `isActive` into a per-profile table (~99 readers, no user gain).
- Moving a device live when another device picks a provider (Decision 3).
- A new `SyncKind`: `SETTING` with a per-profile key already carries this.

## Complexity and risk

**Complexity: low–medium.** ~10 files, mostly small edits: `AppSettings`, `ProviderRepository`,
`AppContainer`, `LocalRecords`, `ProfileRepository` (cleanup), 5 picker call sites, tests, docs.
No Room migration, no new sync kind, no server change.

**Risk: low–medium.**
- Switch path is hot (fast-switch work, 2026-10-01). Adds a DB update and a factory cache clear
  only when the provider changes — measure on a Shield.
- Sync: reuses the dev-mode path; main risk is a missed `DEV_MODE` special case (seeding,
  `syncedSetting`) making the key send as shared instead of per profile. Covered by tests.
- Wrong caller on `pickProvider` vs `setActiveProvider` would either not remember a pick or
  leak automatic changes to other devices. Small, reviewable list.

## Tests

- Unit (`AppSettings`): store/read/remove per profile; `syncedSetting` returns the profile's
  value; `applyRemoteSetting` writes it and ignores non-string values.
- androidTest (`ProviderRepository`): `pickProvider` stores the `providerKey` for the active
  profile and queues a `SETTING` version under that profile; `setActiveProvider` doesn't.
- androidTest (`AppContainer.switchProfile`): A picks X, B picks Y, switch to A → X, to B → Y;
  remembered provider missing → stays; no key → stays.
- `LocalRecords` seeding: one record per profile with a key.
- Two-emulator live-sync run: A picks X on device 1; device 2 switches to A → X; device 2 on A
  does not move when device 1 picks again.
- **Ask before any emulator run** — connectedAndroidTest wipes app data.

## Docs to update with the commit

- `docs/FEATURES.md` — profiles: each profile returns to the provider it last picked, on every
  device.
- `docs/plans/20260929_live-sync-plan.md` — synced settings list: `last_provider` per profile.
- `ProfileEntity` KDoc ("providers … shared by all of them") — current provider is remembered per
  profile.
- `docs/RELEASE_NOTES` entry.
- No Room migration → `docs/DATABASE_SCHEMA.md` unchanged.

## Phases

Single phase, one commit.
