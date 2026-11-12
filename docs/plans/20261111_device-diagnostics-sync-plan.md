# Device diagnostics over live sync

**Status:** Phases 1 to 4 done 2026-11-11; the emulator check of phases 3 and 4 done 2026-11-12 (see Progress). The user decided DD-Q1 to DD-Q7, all (a).

## Goal

From any device of a sync group (usually the phone), read a short summary of the recent crashes,
ANRs and errors of another device. Then a failure on a TV that is hard to reach (behind a VPN, a
network that sleeps in standby, no adb) can be read without adb. The data goes through live sync,
end-to-end encrypted, and a device sends it only while it is open (sync is foreground-only).

## What exists (survey 2026-11-11)

- **The crash log** (`core/player/.../diagnostics/CrashLog.kt`, `CrashLog`):
  - File `files/crashlog/crashes.log`, max 256 KB; when it is full, the oldest half goes.
  - `CrashLog.install` chains the uncaught-exception handler; an entry has the source `uncaught on <thread>`.
  - `CrashLog.record(source, throwable)`: about 35 callers, for example `AppScopes` (`scope <name>`), `launchGuarded`
    (`coroutine <name>`), `SyncManager` (`live sync failed`), `EpgFileManager` (`guide refresh failed`) and
    `ProviderSyncRunner` (`catalogue sync failed: <source name>`).
  - `CrashLog.note(source, message)`: a failure with no exception, such as `slow live sync` or `guide source failed: <label>`.
  - An entry is `"<epochMs> <source>\n<stack trace or message>"`. It has no app build and no screen.
  - `Redact.text` masks each entry before it is written.
- **The system's exit history** (`ProcessExits.recent`, the only use of `getHistoricalProcessExitReasons`):
  - The last 15 process exits, with the reason name (`ANR`, `CRASH`, `CRASH_NATIVE`, `LOW_MEMORY`, `USER_REQUESTED`, `PACKAGE_UPDATED`, ...).
  - Each exit has the description and the first 60 lines of the ANR trace or native tombstone.
  - The app cannot clear this history.
- **The connection log** (`ConnectionLog`): the last 500 failed server calls and player stream events, with
  addresses (logins masked). These do not go to `CrashLog`.
- **Logs** (Settings → About → Logs, code name Diagnostics; `Screen.Diagnostics`):
  - `DiagnosticsViewModel` merges the three logs, newest first.
  - `TraceCauses` gives the exception chain and `rootCause`.
  - Phone: Copy and Share (`MobileDiagnosticsScreen`). Both apps: Clear, which clears `CrashLog` and `ConnectionLog`, not the exits.
  - Everyone who has Settings can open it, also from Safe mode.
- **Masking** (`Redact.text`): by shape only:
  - Xtream stream paths `/(live|movie|series|timeshift)/<user>/<pass>/`;
  - the query values of `username`, `password`, `token`, `api_key`, `api-key`, `X-Emby-Token` and `access_token`;
  - URL userinfo.
  Host names, source names and guide labels stay as they are. Tests: `RedactTest`, `CrashLogRedactionTest`.
- **Safe mode** (`SafeMode`, `LaunchCounter`): after some launches in a row that crashed within 30 s, the
  next launch shows the safe-mode screen. `FijerenaApplication` then skips `startBackgroundWork`, which
  starts `SyncManager`. **So a device in a crash loop does not sync at all.**
- **Device builds** (the device builds plan; FEATURES → Live Sync → Device builds). This is the model to copy:
  - A shared `SyncKind.SETTING` record `device_build:<deviceId>` holds a JSON `DeviceBuild`.
  - `AppSettings.reportDeviceBuild` writes it only when it changed, and queues it (`SettingsSyncQueue.setting`).
  - `SyncManager.reportBuild` calls it when sync starts.
  - The receiver stores it in the settings prefs, length-capped (`AppSettings.applyRemoteSetting`, `MAX_DEVICE_BUILD_LENGTH`).
  - `observeDeviceBuilds` feeds the devices list (`DeviceBuildLine`, "Build not reported yet").
  - `LocalRecords.seedEverything` seeds every known build key.
  - An older build ignores a setting key it does not know (`applyRemoteSetting` has no branch for it).
- **Record kinds** (`SyncKind`):
  - **Kept kinds** (`KEPT`) have version rows. A new kept kind makes every device pull everything once
    after the update (`SyncEngine`, `store.knownKinds`). On a large account, a TV needs minutes for this.
  - **Volatile kinds** (`VOLATILE`: `now_playing`, `remote_command`, `pin_*`, `play_request`,
    `play_answer`) go out from `VolatileRecords` and go into in-memory stores (`NowPlayingStore`). After a restart
    the receiver has lost them, and its cursor is already past them.
- **Sealing:** `SyncCodec.encode` seals each record (`SyncCrypto.seal`, the key id as AAD). The server
  sees a hashed key and ciphertext only.
- **Server limits** (`server/src/account.ts`):
  - `MAX_PAYLOAD` = 64 Ki characters of sealed payload per record; `MAX_BATCH` = 500 records; the key is 1 to 256 characters.
  - The server keeps one row per key (the newest write wins), with no expiry.
  - Only tombstones expire, after 90 days (`purgeTombstones`).
- **Who sees what:**
  - Live Sync settings are an admin's only, behind the PIN.
  - Raw error text shows only to a Nerd (`LocalNerd`, `isNerdMode`), for example the sync status's `lastError`
    in `SyncSettingsScreen` and `MobileSyncSettingsScreen`.
- **Devices list rows** (`DevicesPanel` in `tv/.../SyncSettingsScreen.kt` and
  `mobile/.../MobileSyncSettingsScreen.kt`): name, now playing, build line, then Rename and Remove icon actions.
- **Debug hooks:** `core/ui/src/debug/.../sync/SyncDebugReceiver.kt` (re-pair over adb).

## Open questions

**Decided 2026-11-11:** the user took the recommended answer of each question: DD-Q1 (a), DD-Q2 (a),
DD-Q3 (a), DD-Q4 (a), DD-Q5 (a), DD-Q6 (a), DD-Q7 (a).

| # | Question | Options |
|---|----------|---------|
| DD-Q1 | Which record carries the report? | (a) **A shared setting `device_report:<deviceId>`, like `device_build`.** The receiver keeps it on disk, an older build ignores it, and there is no full pull after the update. Recommended. (b) A new volatile kind. It is lost on the receiver when the app restarts, so a phone that opens later sees nothing. (c) A new kept kind. Every device pulls everything once after the update. |
| DD-Q2 | On by default? | (a) **On by default, with a per-device switch "Share Logs with My Sync Group"** in Live Sync (never synced), as for Share What's Playing. The hard-to-reach TV then reports with no visit. Recommended. (b) Off by default. Someone must turn it on at each TV first. |
| DD-Q3 | Who sees the reports? | (a) **A Nerd, on the Live Sync page (which is an admin's only).** The row line and the Logs action show only for a Nerd. This follows the rule "raw error text for a Nerd only". Recommended. (b) Every admin sees the row line (counts only); the Logs page is for a Nerd. (c) Every admin sees all. |
| DD-Q4 | What goes in? | (a) **Crashes, ANRs, system kills (low memory, too many resources), and the errors the app records (`CrashLog`), plus the count of failed server calls in the last 24 h.** Recommended. (b) As (a), plus the last 10 failed server calls with their host masked. (c) Crashes and ANRs only. |
| DD-Q5 | How are addresses masked? | (a) **In the report, every URL becomes `<scheme>://***` plus its path with the secrets masked as now.** On the device, Logs keeps the host. Recommended. The group is the family and the record is sealed, but a report gets copied and shared from the phone. (b) Keep hosts, as Logs does. |
| DD-Q6 | Record the screen? | (a) **Yes: `CrashLog` adds the current screen (the route name of the top destination) to each new entry.** The report and Logs both show it. ANRs and system kills still have no screen. Recommended. (b) No screen. |
| DD-Q7 | From Safe mode? | (a) **A "Send Logs to Sync Group" icon action on the safe-mode screen.** One push of the report record only: no pull, no apply, no socket. Recommended: a crash loop is the case that needs this most, and Safe mode starts no sync. (b) No send from Safe mode. |

## Design

### The report (`DeviceReport`, core/network/.../sync)

- `@Serializable data class DeviceReport`. Every field has a default, as in `DeviceBuild`:
  - `sentAt`;
  - `versionName`;
  - `safeModeAt` (when Safe mode last started, or 0);
  - `counts7d` (crashes, ANRs, system kills, errors, in the last 7 days);
  - `failedCalls24h` (DD-Q4);
  - `entries`.
- An entry has:
  - `timeMs`;
  - `kind`: `crash`, `anr`, `killed` or `error`;
  - `source`: the `CrashLog` header, for example `guide refresh failed`, max 120 characters;
  - `message`: the top exception's first line, max 200 characters;
  - `cause`: `TraceCauses.rootCause` when it is different from `message`, max 200 characters;
  - `screen` (DD-Q6), max 60 characters;
  - `afterUpdate`: true when `timeMs` is after `PackageInfo.lastUpdateTime`. The page shows the entry
    as "This build" or "Before Update". This needs no change to the log format.
- **Kinds:**
  - `crash`: `CrashLog` entries `uncaught on ...`, and exits `CRASH` and `CRASH_NATIVE`.
  - `anr`: exit `ANR`.
  - `killed`: exits `LOW_MEMORY` and `EXCESSIVE_RESOURCE_USAGE`.
  - `error`: every other `CrashLog` entry.
  - Other exit reasons (user, update, self, freezer) are not problems and stay out.
- **No duplicates:** an exit `CRASH` within 10 s of an `uncaught` entry is the same crash; only the
  entry with the stack trace stays.
- **Caps:**
  - The newest 20 entries from the last 14 days.
  - The encoded report has a maximum of 12,000 characters. If it is longer, the oldest entries go
    first. This is far below the server's 64 Ki sealed characters.
  - The receiver refuses a value over 16,000 characters (`MAX_DEVICE_REPORT_LENGTH`), as for builds.
- **Masking:** each text field goes through `Redact.text`. Then a new `Redact.report` masks URL hosts
  (DD-Q5) and `Bearer <token>`, and (added 2026-11-11 at merge) a host the network stack names outside a URL
  ("resolve host", "connect to", "Hostname") and every IPv4 address. Neither changes Logs on the device.
- **Clear on the device:** Logs → Clear also writes `diagnostics_cleared_at` (local only). The report
  leaves out entries older than that, so it clears too, also the exits that the system keeps.
- **The builder:** `DeviceReportBuilder.build(context, now)` reads `CrashLog.read()` and
  `ProcessExits.recent()` off the main thread. It is a pure function from those lists for the tests.

### Sending and receiving

- `AppSettings` gets these, which copy the `device_build` functions:
  - `DEVICE_REPORT_SETTING_PREFIX = "device_report:"`;
  - `reportDeviceReport(deviceId, report)`;
  - `deviceReports()`, `observeDeviceReports()`;
  - branches in `applyRemoteSetting` and `syncedSetting`;
  - the seed in `LocalRecords.seedEverything`.
- **When:** `SyncManager.reportBuild` (sync start) and each return to the foreground build the report.
  The device sends it only when it changed, or when the last send is older than 24 h.
  - The 24 h resend lets a phone see a report that came before its own update. An older phone ignored
    that record, and no full pull brings it back.
  - A crash kills the process, so the crash goes out at the next launch.
  - There is no background send.
- **The switch (DD-Q2):** `share_logs_with_group`, per device, not synced. When it is off, the device
  sends one empty report, so that its old entries do not stay on the other devices.
- **Safe mode (DD-Q7):** a new `SyncEngine.pushOnly(records)`. It uses the stored link and keys, pushes
  one record, and does nothing else. The safe-mode screen calls it, and shows "Sent" or "Couldn't send".
- No server change. One row per device on the server; each send replaces it.

### The UI (TV and phone)

- **Devices list row** (another device; Nerd per DD-Q3):
  - **The line under the build line:**
    - "2 crashes, 1 ANR in 7 days";
    - "No problems in 7 days";
    - "Logs not reported yet": a device on an older build, or with the switch off, as "Build not reported yet".
  - **A Logs icon action** after Rename. On TV its label shows on focus; on the phone it has a tooltip.
  - This device's row has no Logs action: Settings → About → Logs shows it.
  - A shared `DeviceReportLine` in core/ui (next to `DeviceBuildLine`), with plural strings.
- **Device Logs page:** a new route `Screen.DeviceLogs(deviceId)`.
  - **The title:** "Device Logs", with the device name under it.
  - **The header:**
    - "Sent <date, time>";
    - the `DeviceBuildLine`;
    - the 7-day counts, and "Failed server calls (24 h): 37";
    - "Started in Safe Mode <date>" when `safeModeAt` is in the last 14 days.
  - **One row per entry:**
    - the time;
    - a kind label: Crash, ANR, Stopped by System or Error;
    - the source;
    - the message, then the cause;
    - "This build" or "Before Update";
    - the screen.
  - **TV:** focusable rows that scroll, as on Logs (`DiagnosticsScreen`). Back goes to the device's row
    (focus contract in CONTRIBUTING).
  - **Phone:** Copy and Share icon actions in the top bar, as in `MobileDiagnosticsScreen`. The text is
    the whole report, already masked.
  - **Empty:** "No problems reported".
- **Strings:** en, fr and mg. English in British spelling with Title Case titles, fr and mg in sentence
  case. The mg glossary applies: failure = tsy nety, Nerd = Mahay.

## Phases

| Phase | What | Visible change |
|---|---|---|
| 1 | `DeviceReport`, `DeviceReportBuilder`, `Redact.report`, `diagnostics_cleared_at`; the screen in `CrashLog` entries (DD-Q6). Unit tests: masking (hosts, Bearer, idempotent, text with no secrets unchanged), kinds and duplicates, the 20-entry and 14-day caps, the 12,000-character cap with the oldest entries dropped, Clear, JSON round trip with defaults. | Logs shows the screen on new entries |
| 2 | The `device_report:` setting: `AppSettings` functions, apply with the length cap, seed, send at start and on foreground (changed or 24 h), the switch (DD-Q2). Tests: `AppSettings` round trip and refusal of a long value (as `AppSettingsDeviceBuildsTest`); an older key is ignored; `SyncCodecTest`: a full-size report sealed stays under 64 Ki; `SyncApplier` applies it. | The Live Sync switch; reports flow, nothing shows them yet |
| 3 | The row line, the Logs action and the Device Logs page on TV and phone; Copy and Share on the phone; strings en, fr, mg. Tests: `DeviceReportLineTest`, a TV focus test for the action and Back, a phone Compose test of the page. A debug action in `SyncDebugReceiver` records a test error. | Reports readable from the devices list |
| 4 | Send from Safe mode (DD-Q7): `SyncEngine.pushOnly` and the safe-mode screen action, TV and phone. Test: `pushOnly` sends one record and does not pull. | Safe mode can send its logs |

Each phase is one commit. It updates this plan (Progress, Status), FEATURES (Live Sync → Device logs),
DATABASE_SCHEMA (the `app_settings` key list, next to `device_build`), NAVIGATION_GUIDE (phase 3) and
RELEASE_NOTES. No Room migration.

**Checks for each phase:** ktlint, the tests of the changed modules, compile, the gates.

**Emulator check (phases 3 and 4):**

- Use the TV and phone emulators, both linked through the local sync server (workerd on host :8787,
  see RUN_GUIDE). Re-pair over adb with the debug receiver when needed.
- On the TV, record a test error with the debug action, then send the TV to the background and bring it back.
- On the phone, check the row line and the page, in Malagasy and in English.
- Turn the TV's switch off and check that its report clears on the phone.
- Phase 4: force Safe mode on the TV (`LaunchCounter`), send, and read the report on the phone.
- Do not clear or reinstall app data.

## Progress

| Phase | Status |
|---|---|
| 1 | Done 2026-11-11 |
| 2 | Done 2026-11-11 |
| 3 | Done 2026-11-11; emulator check done 2026-11-12 |
| 4 | Done 2026-11-11; emulator check done 2026-11-12 |

**Emulator check (2026-11-12, debug 0.9.20261112184527, both emulators on the local sync server):** all
steps pass. The TV's `test-error` entry reached the phone after the TV went to the background and back.
On the phone, the TV's row line ("92 errors in 7 days") and the Device Logs page (header, entries with
screen and build, hosts masked as `***`, Copy and Share) show in Malagasy and in English. The TV's
switch off: the row says "Logs not reported yet". Safe mode on the TV: Send logs to my sync group says
"Logs sent to your sync group.", and the phone shows the new report with "Started in safe mode".

**Phase 1 done (2026-11-11):**

- `DeviceReport` and `DeviceReportBuilder` (core/network sync). `DeviceReportBuilder.build(Input)` is pure;
  `build(context, now, clearedAt)` reads `CrashLog`, `ProcessExits`, `ConnectionLog` and `SafeMode.lastStartedAt`.
- `Redact.report` = `Redact.text`, then the URL hosts (with the userinfo and the port), `Bearer <token>`, a host named after "resolve host", "connect to" or "Hostname", and every IPv4 address (with its port).
- `CrashLog` writes the screen into the first line of each new entry (`" · screen: <route>"`), from
  `CrashLog.screen`, which both nav hosts set. `CrashLog.parse` splits an entry. Logs shows the screen in the title.
- `SafeMode` writes `files/safemode/started_at` when safe mode starts (`safeModeAt`).
- Logs → Clear writes `diagnostics_cleared_at` (`AppSettings.diagnosticsClearedAt`), and now runs in `launchGuarded`.
- Tests: `RedactReportTest`, `CrashLogScreenTest`, `DeviceReportBuilderTest`.

**Phase 2 done (2026-11-11):**

- `AppSettings`: `DEVICE_REPORT_SETTING_PREFIX`, `reportDeviceReport` (changed, its send time aside, or 24 h
  old), `deviceReports`, `observeDeviceReports`, the apply branch with `MAX_DEVICE_REPORT_LENGTH`, the
  `syncedSetting` branch, and `shareLogsWithGroup` (`share_logs_with_group`, on by default, not synced).
  `applyRemoteSetting` and `syncedSetting` are now one `when` each, with a single return.
- `LocalRecords.seedEverything` seeds every known report key.
- `SyncManager.reportLogs` runs at each return to the foreground (`onForeground`, which is also where
  sync starts when the app opens), on a link change and on a change of the switch. It is not called from
  `start()`: a process started for background work sends nothing, and the push goes with the next pass.
- The switch "Share Logs with My Sync Group" under Share What's Playing, on TV and phone
  (`scripts/focus-walks/live-sync.txt` edited by hand).
- Tests: `AppSettingsDeviceReportsTest`, `SyncApplierDeviceReportTest`, and `SyncCodecTest`: a report at
  the cap whose texts are all quotes and backslashes (escaped twice more on the way) stays under 64 Ki sealed.

**Phase 3 done (2026-11-11):**

- Shared in core/ui (`sync/DeviceReportLine.kt`): `DeviceReportSummary`, `DeviceReportLine`, `DeviceLogsHeaderLines`,
  `DeviceLogsEntryLines`, `deviceReportText` (Copy and Share), `DeviceReport.recentSafeModeAt`.
  `DeviceLogsViewModel` follows the report, the build and the name of one device.
- `Screen.DeviceLogs(deviceId, deviceName = "")`: the name the row showed comes with the route, for a device
  the group never renamed (the server's name is not in the prefs). Admin only and PIN guarded, as Live Sync
  (`isAdminOnly`, `isPinGuarded`); on TV in the Settings tree.
- Both devices lists: the report line and the Logs icon action (`CinemaIcons.Logs`) on the other devices'
  rows, for a Nerd only. TV: `NavReturnFocus` brings Back to the Logs action; Live Sync's first focus skips
  while it is pending. `DevicesPanel` (TV) is internal now, for its test.
- Device Logs page: TV `DeviceLogsScreen` (focusable cards, focus on the first block), phone
  `MobileDeviceLogsScreen` (Copy and Share in the top bar, as on Logs).
- Strings en, fr, mg. Departures: "Before the last update" and "Started in safe mode" are in sentence case
  (values, not titles), and the kind "Stopped by the system".
- Debug: `SyncDebugReceiver` `test-error [--es msg <text>]` records an error and sends the report again.
- Tests: `DeviceLogsFocusTest` (TV: Logs between Rename and Remove, the page's first focus, Back to Logs),
  `MobileDeviceLogsScreenTest`, and `DeviceReportLineTest` (in the mobile module: core/ui runs no Robolectric).

**Phase 4 done (2026-11-11):**

- `SyncEngine.pushOnly(records)`: one push with the stored link and account key; no pull, no apply, no
  seed, no cursor.
- `SafeModeViewModel.sendLogs` builds the report and pushes the one `device_report:<deviceId>` record, with
  the clock value "now". It writes nothing to the prefs or `providers.db` (the database can be what
  crashes); the next normal start sees a change and sends again with the sync clock.
- The icon action "Send logs to my sync group" (`CinemaIcons.Send`) shows only on a linked device whose
  Share Logs with My Sync Group switch is on (a departure: the plan did not say; the switch is the user's
  choice not to share). TV: after Show logs, focus stays on it. Phone: in the top bar. The screen says
  "Logs sent to your sync group." or "Couldn't send the logs.", and the masked raw error for a Nerd.
- Test: `SyncEnginePushOnlyTest` (one sealed record pushed, no pull, no cursor or seed written; nothing
  sent when not linked).

## Difficulty

Medium-small. Phases 1 and 2 copy the device builds pattern. Phase 3 is one new page and one row
action in each app. Phase 4 has the risk: a push path that works when the rest of startup did not run.

## Risks

- **Masking misses a secret shape** (a token in a message with no `token=`). The host mask and the
  length caps limit what can go out. Copy and Share on the phone take the masked text only.
- **A TV that is never opened sends nothing.** This is by design (foreground-only sync); the page shows
  the time of the last send.
- **A crash loop that comes before `SafeMode.init`** (in `Application.onCreate`) cannot send, even with
  phase 4.
- **Reports of removed devices stay in the prefs**, as builds do. The page shows only listed devices.
