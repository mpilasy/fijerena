# Device builds in Live Sync

**Status:** Planned 2026-11-06. The user answered DB-Q1 and DB-Q2 on 2026-11-06 (see Answers). DB1 done; DB2 in progress.

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
| DB-D1 | Record | A shared **setting** record per device, key `device_build:<deviceId>` (`AppSettings.DEVICE_BUILD_SETTING_PREFIX`), the same way device names already sync (`device_name:<deviceId>`). No new kind, table or migration; an older build ignores the unknown key (`applyRemoteSetting` has no branch for it). Kept like every setting: the list shows a device's build while it is off. |
| DB-D2 | Payload | The setting's value is a `DeviceBuild(versionName, versionCode, buildType, app, model, androidRelease, sdkInt)` as JSON. `app` is `tv` or `phone`. Every field has a default, so a later field reads as empty from an older build; a value that does not read, or is over 1,000 characters, is left out. |
| DB-D3 | Build type | Each build type sets the manifest meta-data `fijerena.buildType` (`debug`, `localRelease`, `release`) through a manifest placeholder, because localRelease and release cannot be told apart at run time otherwise. The version and version code come from the PackageManager, so `core:network` needs no app `BuildConfig`. |
| DB-D4 | Writing | When sync starts (each app start) and when the device links, `SyncManager` writes this device's record only when it differs from the stored one: a new install, an update or an Android update sends one record; a plain restart sends none. Linking seeds every stored device build, as it does device names. |
| DB-D5 | Old rows | None to clean: the list shows only the server's listed devices, so a removed device's record is never shown (as device names). |
| DB-D6 | Display | Under each device's name in the device list: `<versionName> · <buildType> · Android <release>`, from the row of that device id. With no row yet: "Build not reported yet" (the device has not run a build with this feature, or has not been opened since). The current device shows its own build from `AppBuild`, not from the table. |
| DB-D7 | Words | en/fr/mg: "Build not reported yet", "Older build" (see DB-Q2), and the build type names as they are (`debug`, `localRelease`, `release`: technical words, the same in every language). |

## Answers (user, 2026-11-06)

| # | Answer |
|---|---|
| DB-Q1 | (a) Everyone who opens the Live Sync page. |
| DB-Q2 | (a) An "Older build" mark on a device below the newest version code in the list. |

## Open questions (answered, kept for the options)

| # | Question | Options |
|---|---|---|
| DB-Q1 | Who sees the builds | (a) **Everyone who opens the Live Sync page** (it is already an Admin page). Recommended: no new rule. (b) A Nerd only, as the other technical details. |
| DB-Q2 | Mark older builds | (a) **An "Older build" mark on a device whose `versionCode` is lower than the newest one in the list.** Recommended: it says which device to update. (b) No mark, the version text only. |

## Phases

| Phase | Work | Status |
|---|---|---|
| DB1 | **Data and sync.** `DeviceBuild` (read from the package and the manifest's `fijerena.buildType`), the `device_build:<deviceId>` setting in `AppSettings` (report on a change, apply, send, observe), seeding on link, the report from `SyncManager`. Tests: write only on change, apply, a later field ignored, a bad or huge value left out. | Done 2026-11-06 |
| DB2 | **Display.** The build line under each device on TV and phone, the "not reported" text and the DB-Q2 mark. Docs: FEATURES (Live Sync), RELEASE_NOTES. Checked on both emulators: each emulator sees the other's build after both are opened. | In progress |

Each phase is one commit on main.

## Not in scope

- A plain-text version on the server (the server would read it).
- Pushing an update to a device, or asking a device to update.
- The build of a device that has not opened the app since its update (sync runs only while the
  app is open).
