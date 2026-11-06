# Device builds in Live Sync

**Status:** Planned 2026-11-06; open questions DB-Q1 and DB-Q2. Not started.

## Goal

The user (2026-11-06): live sync tells which build each device of the household has.

Each device reports its own app build in an encrypted synced record. The Live Sync device list on
TV and phone shows it under each device's name, for example "0.9.20261106024835 · debug · Android
11". The server stays blind: it stores the record as ciphertext, like every other record.

## What exists (survey 2026-11-06)

- **The server's device list** (`server/src/account.ts`, table `devices`): `id`, `name`,
  `created_at`, `last_seen`, `revoked`. Read by `SyncApi` as `SyncWire.Devices`
  (`SyncWire.Device`: `id`, `name`, `createdAt`, `lastSeen`, `revoked`, `current`). No build.
- **Records.** Kept kinds (`SyncKind.KEPT`) live in a local table, and SQLite triggers queue a
  `sync_version` row on each change; the payload is sealed on the device. Volatile kinds
  (`SyncKind.VOLATILE`, such as `NOW_PLAYING`, keyed by the server device id) live only in memory
  and are gone after a restart. An older build skips a kind it does not know.
- **Builds.** `BuildConfig.VERSION_NAME` is read only in the app modules (TV
  `AboutSettingsCard`, phone `SettingsScreen`). Build types: `debug`, `localRelease`, `release`.
- **Databases.** `providers.db` (`SettingsDatabase`) is version 21 and holds the household's kept
  kinds (providers, profiles, settings, guide sources); `xtream_v2.db` is version 31.
- **UI.** The device list is in `SyncSettingsScreen` (TV) and `MobileSyncSettingsScreen` (phone):
  each device's name, last seen, Rename and Remove.
- **Sync runs only while the app is open** (see the sync design): a device reports on its next
  open after an update.

## Decisions (proposed)

| # | Subject | Decision |
|---|---|---|
| DB-D1 | Kind | A new kept kind `device_build` (`SyncKind.DEVICE_BUILD`, in `KEPT`), keyed by the device's server id, profile slot `SHARED`. Kept, not volatile: the list shows a device's build while it is off. |
| DB-D2 | Payload | `SyncPayloads.DeviceBuild(versionName, versionCode, buildType, app, model, androidRelease, sdkInt, reportedAt)`. `app` is `tv` or `phone`. Every field has a default, so a later field reads as empty from an older build. |
| DB-D3 | Storage | A table `device_build` in `providers.db` (migration 21 → 22), primary key `deviceId`, one column per payload field, with sync triggers like the other `providers.db` kinds. `docs/DATABASE_SCHEMA.md` and the rollback helpers change in the same commit. |
| DB-D4 | Writing | At app start, once the device is linked, the app writes its own row only when a field differs from the stored one. A new install, an update or an Android update sends one record; a plain restart sends none. The app module passes its `BuildConfig` values to `core:network` once, at start (an `AppBuild` object), because `core:network` cannot read the app's `BuildConfig`. |
| DB-D5 | Old rows | A row whose device is no longer in the server's device list (removed, or paired again under a new id) is deleted locally when the list loads, with no tombstone: every device does the same. Removing a device on the Live Sync page also deletes its row with a tombstone. |
| DB-D6 | Display | Under each device's name in the device list: `<versionName> · <buildType> · Android <release>`, from the row of that device id. With no row yet: "Build not reported yet" (the device has not run a build with this feature, or has not been opened since). The current device shows its own build from `AppBuild`, not from the table. |
| DB-D7 | Words | en/fr/mg: "Build not reported yet", "Older build" (see DB-Q2), and the build type names as they are (`debug`, `localRelease`, `release`: technical words, the same in every language). |

## Open questions

| # | Question | Options |
|---|---|---|
| DB-Q1 | Who sees the builds | (a) **Everyone who opens the Live Sync page** (it is already an Admin page). Recommended: no new rule. (b) A Nerd only, as the other technical details. |
| DB-Q2 | Mark older builds | (a) **An "Older build" mark on a device whose `versionCode` is lower than the newest one in the list.** Recommended: it says which device to update. (b) No mark, the version text only. |

## Phases

| Phase | Work | Status |
|---|---|---|
| DB1 | **Data and sync.** `AppBuild` (set by both apps at start), `SyncKind.DEVICE_BUILD` in `KEPT`, the payload, `providers.db` migration 21 → 22 with the table and its triggers, the writer at start (only on a change), the applier, old-row clean-up when the device list loads, and the tombstone on Remove. Tests: migration, write only on change, apply, an older payload with fewer fields, clean-up. | Planned |
| DB2 | **Display.** The build line under each device on TV and phone, the "not reported" text and the DB-Q2 mark. Docs: FEATURES (Live Sync), RELEASE_NOTES. Checked on both emulators: each emulator sees the other's build after both are opened. | Planned |

Each phase is one commit on main.

## Not in scope

- A plain-text version on the server (the server would read it).
- Pushing an update to a device, or asking a device to update.
- The build of a device that has not opened the app since its update (sync runs only while the
  app is open).
