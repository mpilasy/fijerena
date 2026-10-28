# Build, Deployment & Run Guide

Unified guide for building, installing, and deploying Fijerena across both **Android TV** (NVIDIA Shield, Chromecast with Google TV, Sony Bravia) and **Android Mobile** (phones and tablets).

---

## Prerequisites

- **JDK:** OpenJDK 21
- **Android SDK:** Command-line tools or Android Studio
- **API Targets:** `minSdk = 30` (Android 11+), `targetSdk = 35`, `compileSdk = 37`
- **Gradle:** 9.6.0 via the `./gradlew` wrapper (AGP 9.4.1); sources compile to Java 21
- **Host tools:** `adb`; `sqlite3` for the backup/restore scripts and `deploy-tv-ip.sh`; `python3` for the focus walks
- **TMDB key (optional, entered in the app):** release builds carry no key. Debug builds seed it once from `TMDB_API_KEY` in `local.properties` on a device that has never had a key set or removed (not synced; a key entered or removed in Settings wins). Otherwise enter your own in the running app: Settings → Source & guide → Film and series details (TMDB) → paste the key (v3 API key or v4 read access token) → Test → Save. On an emulator, typing it is easiest over adb while the field is open for editing: `adb shell input text <key>`. With a sync group it reaches the group's other devices on its own. Without a key TMDB lookups are skipped. A git worktree has no `local.properties` of its own — copy or symlink the main checkout's (the SDK path).

Verify environment:
```bash
javac -version   # Should report 21.x.x
./gradlew -v     # Verifies Gradle and JVM compatibility
```

---

## Building the Application

AGP builds APKs under each module's standard build directory:
- **TV APK:** `tv/build/outputs/apk/debug/tv-debug.apk`
- **Mobile APK:** `mobile/build/outputs/apk/debug/mobile-debug.apk`

### Debug Builds (Development)

```bash
# Build both TV and Mobile targets
./gradlew assembleDebug

# Build TV target only (same as scripts/build-tv.sh)
./gradlew :tv:assembleDebug

# Build Mobile target only (same as scripts/build-mobile.sh)
./gradlew :mobile:assembleDebug
```

Debug APKs carry native libraries for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`, so x86 emulators
(the TV AVD is a 32-bit x86 image) run the app natively instead of translating its ARM libraries —
timings taken on an emulator are only meaningful on such a build. Release builds stay ARM-only.

> [!IMPORTANT]
> **Incremental deploys, clean on demand:** the deploy scripts build incrementally (no `clean`, since 2026-10-02), so a repeat deploy takes seconds. A stale intermediate DEX shard after changes across modules (`core:*` consumed by `:tv`/`:mobile`) shows up as a `NoClassDefFoundError` at runtime; if that happens, clean and deploy again:
> ```bash
> ./gradlew clean && scripts/deploy-tv-emulator.sh   # or whichever deploy script
> ```

### Release Builds (Production)

```bash
./gradlew :tv:assembleRelease
./gradlew :mobile:assembleRelease
```

Signed with the release key when one is configured (see Releasing below), otherwise unsigned
(`*-release-unsigned.apk`; sign them, e.g. with `apksigner`, before installing). Minification is off.

---

## Quality Control & Verification

Run style checks and tests before committing code:

```bash
# Code style inspection (ktlint)
./gradlew ktlintCheck

# Auto-format style violations
./gradlew ktlintFormat

# Run Android Lint (each module's lint-baseline.xml lists the warnings that existed when lint was
# added to CI; only new issues fail. Regenerate one with ./gradlew :<module>:updateLintBaseline)
./gradlew lintDebug

# Run unit tests (what CI runs)
./gradlew testDebugUnitTest

# Source gates (see CONTRIBUTING.md, constraints 7 and 13, and the TV focus rules)
scripts/check-cancellation.sh       # catch (Exception) in suspend code rethrows CancellationException
scripts/check-viewmodel-launch.sh   # no new bare viewModelScope.launch (use launchGuarded)
scripts/check-focus-retry.sh        # no requestFocus() + catch (IllegalStateException)
scripts/check-design-system.sh      # no raw colours, sizes or Material controls outside the theme (per-file allow-list)
```

CI runs on every push to `main`, or by hand (`workflow_dispatch`), in these workflows under `.github/workflows/`:
- `checks.yml`: a grep for blanket destructive Room fallbacks and the four gates above.
- `android-build.yml` (not for docs-only, server-only or focus-walk changes): ktlint, unit tests,
  Android Lint, a check that the build left `core/network/schemas` unchanged, `assembleDebug`, then
  `assembleDebugAndroidTest` so the instrumented tests keep compiling.
  Both APKs are uploaded as artifacts from `main` and manual runs only. Gradle runs on JDK 21
  (`gradle/gradle-daemon-jvm.properties`).
- `server.yml` (only when `server/` changes): the sync server's `npm ci && npm test`.
- `instrumented-tests.yml`, nightly at 06:17 UTC or by hand: `connectedDebugAndroidTest` for
  `core:network` and `mobile` on a phone emulator, with the test reports uploaded as artifacts; and
  the TV walk (job `tv-walk`, see "Focus walks" → "Nightly TV walk in CI").
  Compose UI tests run on the JVM with Robolectric, among the unit tests: `robolectric.properties`
  in `tv/src/test/resources` (a plain `Application`, a 960 × 540 dp TV screen) and
  `mobile/src/test/resources` (a plain `Application`; a test sets its screen with `@Config(qualifiers)`).

`./gradlew connectedAndroidTest` uninstalls the app and wipes its data on every connected device —
only with real devices disconnected, or one emulator targeted with `ANDROID_SERIAL`.

---

## Deployment & Device Targeting

The TV app's `applicationId` is `org.njarasoa.fijerena` and the phone app's
`org.njarasoa.fijerena.mobile` (since 2026-10-23; before, the phone app used the TV app's id).
The commands below write `<pkg>` for it; `scripts/app-package.sh <serial>` prints the right one
for a device.

Use the deploy scripts rather than hand-run `gradlew` + `adb install`; each builds the right APK
incrementally and installs it as an update (`install -r`):

| Script | Target | Notes |
|--------|--------|-------|
| `scripts/deploy-tv-emulator.sh [--release] [serial]` | TV emulator | Picks the first emulator with the leanback feature; asks before interrupting playback |
| `scripts/deploy-mobile-emulator.sh [--release] [serial]` | Phone emulator | Picks the first emulator *without* leanback, so a TV emulator is never overwritten |
| `scripts/deploy-tv-ip.sh [--release] <ip>[:port] …` | Network TVs | Port 5555 by default; wakes a TV whose network sleeps in standby (a Bravia with Remote start on) with Wake-on-LAN first, screen off, using its MAC from the ARP cache or `scripts/wake-macs.local` (`<ip> <mac>` per line, not in git); skips devices still unreachable and any that don't report `tv` in `ro.build.characteristics`; asks before interrupting playback; builds once, then backs up each device's user data with `backup-app-data.sh` into `backups/*.tar.gz` (kept 7 days) and installs in parallel |
| `scripts/deploy-mobile-usb.sh [--debug] [serial]` | USB phone | Installs `localRelease` by default (`--debug` for the debug build). Uses the only USB device when no serial is given; backs up the phone's user data first while the installed build is debuggable, like `deploy-tv-ip.sh` (no playback check). A `localRelease` install cannot be backed up: the script warns and installs (accepted, 2026-10-21) |

**`--release`** builds and installs `localRelease` instead of `debug`: the release code (no R8, as
the release build), **not debuggable**, signed with the debug key so it installs over a debug build
and keeps the app's data, and profileable from the shell (Perfetto). It is for measuring start-up,
memory and frames on our own devices; it is never published. A non-debuggable install can't be read
with `run-as`, so: the deploy scripts back up a debug install before replacing it, but they can't
back up a `localRelease` install (they say so and install anyway, with the same key); database
pulls, the debug broadcast receivers and the `run-as` checks in this guide don't work on it. Use
Settings › Admin › Sync & Backup › Back up to a file first if the data matters, or deploy the debug build
again (it installs over `localRelease` and keeps the data) before using those tools.

A real-device script doesn't install on a device that has the app but whose backup failed. Other
scripts: `scripts/uninstall-app.sh [serial]` is the only sanctioned uninstall (it asks you to type
`UNINSTALL`).

> [!CAUTION]
> **Strict Deployment Rules:**
> 1. **Back Up Before Installing to Real Hardware — `install -r` is NOT a guaranteed data-safe operation.** Never run `adb uninstall` or clear data to resolve deployment issues. `adb install -r` *usually* preserves Room databases, credentials, favorites, and watch state — but a signing-key mismatch (or other cause) can make it install fresh with no warning, silently wiping everything. This happened for real on 2026-09-08 across 3 real TVs with zero warning from `adb` (it reported "Success" on every device). Before installing to any real device — not an emulator — run `scripts/backup-app-data.sh <serial> <out.tar.gz>` (user data only); `scripts/restore-app-data.sh <serial> <backup.tar.gz>` puts it back — see Backup & restore below. Both real-device deploy scripts (`deploy-tv-ip.sh`, `deploy-mobile-usb.sh`) run the backup themselves. Do this every time, unprompted — user permission to deploy is not permission to skip the backup.
> 2. **Device Detection:** Always detect device type via `getprop ro.build.characteristics` (or inspect `product:`/`model:` in `adb devices -l`) before deploying. Never assume target identity from port numbers or IPs.
> 3. **No Auto-Launch:** Never automatically launch the app (`am start` or monkey intents) after install. Let the user launch the app manually when ready.

### Backup & restore

```bash
scripts/backup-app-data.sh  <serial> backups/<name>.tar.gz   # ~50 KB, needs sqlite3 on the host
scripts/restore-app-data.sh <serial> backups/<name>.tar.gz   # force-stops the app first
```

A backup holds user data only: `shared_prefs`, `providers.db` (sources, profiles, guide sources,
live sync) and `xtream_v2_user_data.db` — the `watch_state`, `favorite_state`, `wanted_title` and
`sync_*` tables, copied out of `xtream_v2.db` on the host (`wanted_title` only when the device has it:
it came with v26; a restore keeps the device's own Wanted list when the backup has none) (its `-wal` comes off the device with it, so uncommitted
pages aren't lost). The catalogue is left out; a sync downloads it again. Both scripts use `run-as`,
so they work with debug builds.

Restore force-stops the app, writes the backup's prefs and `providers.db`, and replaces those tables
inside the device's current `xtream_v2.db` (catalogue untouched), with the sync triggers held off
(`sync_clock.applying`) and columns matched by name, so an older backup restores into a newer
schema. The app must have been opened once since install so `xtream_v2.db` exists. Saved passwords
don't survive an uninstall or `pm clear` either way (Keystore); the app asks for them again.

Tested 2026-10-02 on the TV emulator: back up, add a favourite (60 → 61), restore → 60, the added
one gone, watch/sync rows and the 471,796-row catalogue unchanged, `integrity_check` ok, app opens.

### 1. Emulator Targets

Emulator port numbers (`emulator-5554`, `emulator-5556`, etc.) are assigned by **launch order**, not by AVD type.

```bash
# Inspect all connected devices and emulators
adb devices -l

# Check characteristics if ambiguous
adb -s <emulator-id> shell getprop ro.build.characteristics
# TV returns "tv", mobile returns "default" or "nosdcard"

# Build and install (each script picks the right emulator when no serial is given)
scripts/deploy-tv-emulator.sh [<tv-emulator-id>]
scripts/deploy-mobile-emulator.sh [<mobile-emulator-id>]
```

HEVC testing on an emulator is limited, and Jellyfin content will be transcoded.

#### Test source: Jellyfin as Xtream

A development tool, not part of the apps: `tools/jellyfin-xtream/xtream_bridge.py` (Python 3, stdlib only) serves a Jellyfin server as an Xtream panel, so the Xtream code paths can be tested against a known library. It holds no credentials: the Xtream username/password the app sends are checked against Jellyfin. Pointing it at another Jellyfin server only means changing `JELLYFIN_URL`.

```bash
tools/jellyfin-xtream/restart.sh          # (re)starts it detached on :8080 for Jellyfin at localhost:8096
JELLYFIN_URL=https://other.host BRIDGE_PORT=8081 tools/jellyfin-xtream/restart.sh
```

In the app, add an Xtream source with Server URL `http://10.0.2.2:8080` (emulator → host; use the host's LAN IP from real devices) and the Jellyfin username/password. Movie and TV-show libraries become one category each; Live TV and EPG appear only if Jellyfin has Live TV. Playback redirects to Jellyfin, so the device must reach `JELLYFIN_URL` too. Keep `xtream_ids_<host>.db` (or the file named by `BRIDGE_DB`): it maps Xtream ids to Jellyfin GUIDs, and losing it changes every id (orphaning watch history and favourites).

Like a real panel, a series' `last_modified` moves when episodes are added (Jellyfin's `DateLastMediaAdded`, else `DateCreated`), so adding an episode in Jellyfin makes the app fetch that show's episodes again after its next sync. An item that sits in two Jellyfin libraries is listed once, under the first library by name. After editing the bridge, rerun `restart.sh`.

#### Test source: fake Xtream

`tools/fake-xtream/server.py` (Python 3, stdlib only) is a fake Xtream panel with fixed data, for
the nightly TV walk and other scripted checks: 3 live categories (News, Kids, Adult) with 6
channels, 3 film categories (Drama, Kids, Adult) with 8 films, 2 series categories (Drama Series,
Kids Series) with 4 series of 2 seasons × 3 episodes, and an XMLTV guide (`xmltv.php`, also
`get_short_epg`) of half-hour programmes from 2 hours before now to 12 hours after, so the guide
is never empty. Names are English, ids never change. Login `test` / `test`. Stream URLs answer HTTP
404: nothing plays.

```bash
python3 tools/fake-xtream/server.py --port 8090    # serve until stopped (default port 8090)
python3 tools/fake-xtream/server.py --self-test    # call every endpoint, check the answers, exit 0 or 1
```

In the app, add an Xtream source with Server URL `http://10.0.2.2:8090` (emulator → host) and
`test` / `test`, or seed a fresh install with `scripts/seed-test-household.sh` (see "Focus walks").

### 2. Physical Android TV (NVIDIA Shield, Chromecast, Sony Bravia)

TV devices connect via ADB over TCP/IP (port 5555). Because TV IP addresses drift across sessions via DHCP (on development subnet `192.168.1.0/24`):

1. **Find TV on Network:**
   ```bash
   # Query mDNS for broadcasting ADB devices
   adb mdns services
   ```
   *(Falls back to `arp -a | grep 192.168.1.` if mDNS is unavailable)*

2. **Connect & Verify:**
   ```bash
   adb connect <TV_IP>:5555
   adb -s <TV_IP>:5555 shell getprop ro.build.characteristics  # Confirms "tv"
   ```

3. **Deploy TV APK** (connects, checks for playback, backs up, installs; several TVs at once):
   ```bash
   scripts/deploy-tv-ip.sh <TV_IP> [<TV_IP> ...]
   ```

### 3. Physical Android Mobile (Phones / Tablets)

Connect phone via USB cable or Wireless Debugging (Settings → Developer Options):

```bash
# Verify connection
adb devices -l

# Deploy the mobile localRelease APK (--debug for the debug build; backs up first while the installed build is debuggable; pass the serial when more than one USB device is attached)
scripts/deploy-mobile-usb.sh [<device-id>]
```

---

## Releasing

Downloadable APKs are published on the repository's GitHub Releases page by
`.github/workflows/release.yml`, when a tag `v<version>` is pushed. Both apps share one version,
`fijerena.versionName` / `fijerena.versionCode` in `gradle.properties`; the workflow refuses a tag
that doesn't match `versionName`. It builds `assembleRelease` signed with the
release key (release plan D7: one key for both apps), and publishes `fijerena-tv-<version>.apk`,
`fijerena-phone-<version>.apk` and `SHA256SUMS`, with `docs/RELEASE_NOTES.md`'s first section as
the notes. Release builds are not minified yet (release plan R2).

**One-time setup: the release key.** Every release must be signed with the same key, or it won't
install over the last one. Lose it and users must uninstall (losing their data) to update, so keep
an encrypted offline backup.

```bash
keytool -genkeypair -keystore fijerena-release.jks -alias fijerena -keyalg RSA -keysize 4096 \
  -validity 10000 -dname "CN=Fijerena"
gh secret set FIJERENA_KEYSTORE_BASE64 < <(base64 -w0 fijerena-release.jks)
gh secret set FIJERENA_KEYSTORE_PASSWORD   # prompts
gh secret set FIJERENA_KEY_ALIAS --body fijerena
gh secret set FIJERENA_KEY_PASSWORD        # prompts (the store password, unless keytool asked for another)
```

To sign release builds locally too, add a `keystore.properties` at the repository root (ignored by
git, as are `*.jks` and `*.keystore`):

```properties
storeFile=/path/to/fijerena-release.jks
storePassword=…
keyAlias=fijerena
keyPassword=…
```

Without the key (no `FIJERENA_*` environment variables, no `keystore.properties`), `assembleRelease`
still builds, unsigned (`*-release-unsigned.apk`).

**A release.**

1. Raise `fijerena.versionCode` by one (every release, or the APK won't install over the last one)
   and set `fijerena.versionName` in `gradle.properties` to `0.<minor>.<UTC time>`: the minor
   version, then the release's time in UTC as `YYYYMMDDHHMMSS`
   (`echo 0.8.$(date -u +%Y%m%d%H%M%S)`), for example `0.8.20261026194500`. The time only grows,
   so each release sorts after the last; raise the minor version for a larger change.
2. Retitle `docs/RELEASE_NOTES.md`'s `## Unreleased` heading to `## Version: <version>`.
3. Commit, push `main`, then tag and push the tag:
   `git tag v<version> && git push origin v<version>`. Tag only code that Android Build has passed
   on: the Release workflow does not run the unit tests again.

## Logcat & Debugging

Filter logs for app-specific diagnostics:

```bash
# Stream app logs
adb -s <device-id> logcat | grep "fijerena"

# Stream crash logs and unhandled exceptions
adb -s <device-id> logcat *:E

# Clear logcat buffer
adb -s <device-id> logcat -c

# Debug builds: every track's codecs string, format support and chosen decoder (Media3 EventLogger)
adb -s <device-id> logcat -s EventLogger
```

### Crash log and Logs

Every build records what went wrong on the device itself, so a crash on a TV is still there after
the fact without logcat having been attached:

- **Logs** (Settings → About → Logs, any profile that has Settings, no PIN; mobile's can also
  Share as text) lists, newest
  first, the app's own crash log — uncaught exceptions, and exceptions absorbed by the app-wide
  coroutine scopes (`AppScopes`), sync records that couldn't be applied, a database set aside on
  downgrade — together with Android's record of why recent processes ended (ANR, native crash,
  low-memory kill, package update…). Credential files the app had to reset (unreadable after a
  Keystore reset) appear as `credentials reset: <file>`. Server calls that failed (no answer, a
  timeout, an error status) from the Xtream, Jellyfin and TMDB clients appear as `No answer from
  <host>` or `HTTP <code> from <host>`, with the address asked and the error, logins masked — the
  last 500 (`ConnectionLog`, `files/crashlog/connections.log`). The same file keeps what the
  player's streams did: `Stream error from <host>` (the error code and its causes), `Stream
  reconnect on <host>` (the health check's reason) and `Stream waited <n> s on <host>` (a wait of
  1 s or more during playback, or while it recovered: why, the load errors and data received
  during it, and what followed). Failed syncs and index work appear too
  (`CrashLog.record` / `CrashLog.note`): `catalogue sync failed: <source>`, `guide source failed:
  <name>` (address and reason), `guide refresh failed` / `guide refresh (background) failed` (no
  network included), `live sync failed` (an exception or the server's HTTP status), `guide search
  index rebuild failed`, `guide index: dropping / restoring indexes … failed` and `Shrink database:
  VACUUM failed`. On TV, a dead D-pad appears as `D-pad did nothing` (`DeadKeyDetector`: three
  unhandled D-pad presses in at least two directions), with the screen (`screen=<route>`, plus
  `Live TV preview` / `Live TV full screen`) and the keys pressed.
- Off-device:

```bash
adb -s <device-id> shell run-as <pkg> cat files/crashlog/crashes.log
```

The log is capped at 256 KB (oldest half dropped). "Clear log" clears only the app's own entries;
Android's exit history stays.

### Crash-loop safe mode

Each process start appends a timestamp to `files/safemode/launches` (a plain file, not
SharedPreferences or Room); 30 s later, if the process is still alive and not in safe mode, the file
is emptied. Three timestamps within 10 minutes at the next start mean the last three launches died
early, and that launch starts in safe mode (see `docs/FEATURES.md`). Background-only processes
(WorkManager, the playback service) count the same way, which is why "healthy" is process lifetime,
not a screen being shown.

To see it on an emulator (debug build, so `run-as` works), write three recent timestamps and
cold-start:

```bash
NOW=$(date +%s%3N)
adb -s <device-id> shell "run-as <pkg> sh -c 'mkdir -p files/safemode && printf \"$NOW\n$NOW\n$NOW\n\" > files/safemode/launches'"
adb -s <device-id> shell am force-stop org.njarasoa.fijerena
# then launch the app; to leave by hand instead of Continue:
adb -s <device-id> shell run-as <pkg> rm files/safemode/launches
```

### Debug broadcasts

Three receivers exist in debug builds only (`src/debug` source sets) and require
`android.permission.DUMP`, which the adb shell holds and other apps don't — so `adb shell am
broadcast …` works, and nothing else on the device can link the sync account or force refreshes.

```bash
# EpgSyncDebugReceiver (core:network): enqueue an immediate, forced EpgSyncWorker run
adb -s <device-id> shell am broadcast -a org.njarasoa.fijerena.DEBUG_EPG_SYNC -p <pkg>

# SyncDebugReceiver (core:ui): drive live sync; results go to logcat under SyncDebug
adb -s <device-id> shell am broadcast -a org.njarasoa.fijerena.DEBUG_SYNC -p <pkg> --es cmd status

# TestHouseholdDebugReceiver (core:network): the TV walk's test household on a fresh install;
# results go to logcat under TestHousehold. Use scripts/seed-test-household.sh, which waits for it.
adb -s <device-id> shell am broadcast --include-stopped-packages -a org.njarasoa.fijerena.DEBUG_TEST_HOUSEHOLD \
    -p <pkg> --es url http://10.0.2.2:8090
```

`DEBUG_SYNC` commands (`--es cmd …`): `setup --es url <server> [--es secret <setup secret>] [--es name
<device name>]` (new account), `invite` (logs an invite QR code's text), `scan --es qr '<qr text>'`,
`handoff --es url <server>`, `now` (a sync pass), `status`, `unlink`. The full list is in the
receiver's KDoc.

### Focus walks

Settings' and the two-pane browse's focus order is checked by Robolectric focus tests
(`tv/src/test`, the `FocusWalk` helper, one `*FocusTest` class per group or screen). They need no
device and run in CI with the other unit tests. To run them alone:

```bash
./gradlew :tv:testDebugUnitTest --tests '*FocusTest'
```

The Settings walks below keep only the moves no test can make (Back on the rail, Back that closes
a dialog, Back from a screen Settings opens, Live sync linked, Check a source with real data). Run
the remaining emulator walks before a release, in English: they match English text, so set the TV
emulator's app language to English first.

`scripts/tv-focus-walk.sh` drives the TV app with D-pad keys and reads which node has focus after
each one (`uiautomator dump`, then the `focused="true"` node's text and content-desc plus its
descendants', first four joined by ` / `). It needs `adb` and `python3` on the host, nothing on the
device.

```bash
scripts/tv-focus-walk.sh scripts/focus-walks/home.txt                 # assert; exit 1 on any mismatch
scripts/tv-focus-walk.sh -s emulator-5554 -d 0.6 scripts/focus-walks/*.txt
scripts/tv-focus-walk.sh -r scripts/focus-walks/home.txt              # record: rewrite the expectations
```

`-s` picks the device (default: the first `emulator-*` in `adb devices`); `-d` is the pause after
each key in seconds (default 0.45, raise it on a slow emulator). The script prints one line per step
(`N  KEY  →  <focused text>  OK | MISMATCH (expected "…")`) and a summary per file.

A walk file is one step per line, `KEY<TAB>expected`, where KEY is `UP`, `DOWN`, `LEFT`, `RIGHT`,
`CENTER`, `BACK`, `MENU` or `WAIT <seconds>`, and `expected` is a substring of the focused text. A
step with no expectation sends the key without checking. A first line `@start <substring>` asserts
the focus before any key is sent, so a walk can check it started on the right screen. `#` comments
and blank lines are skipped, and kept as they are when recording.

Two modes: without `-r` the file is a test — every expectation is checked, all files are run, and
the script exits 1 if any step mismatched. After the first mismatch in a file it stops sending
`CENTER` (the walk is off its path, and OK would press whatever has focus). With `-r` it is a
recorder — every key is sent, and the observed text is printed and written back into the file as the
new expectations (comments kept), which is how a change to focus order updates its walk in the same
commit. Record only from a start state a check run has matched. In both modes it never sends `CENTER` while focus
is on a destructive control (Delete, Clear, Remove, Purge, Shrink, Reset, Leave the sync group): it
counts a mismatch and stops pressing OK, since a walk started in the wrong place once pressed a
library-data Clear button while recording. The one exception is the `REMOVE` step, for removals the
Undo bar takes back: it presses OK only while focus is on "Remove from Recent", "Remove from
Favourites" or an edit-mode row (its ✕ reads "Remove") — never Clear / Delete / anything else — the
walk's next line must be `MENU` (the undo), and on any mismatch while a removal is still undoable,
or a walk ending without its `MENU`, the script presses Menu itself to undo it.

One driver per emulator: the script, a person with the remote, and any other script sending keys
must never share a device at the same time, or the focus read after each key belongs to someone
else's key. Put the app on the walk's start screen by hand first; the script does not navigate
there. Each file's header comments name the start screen, the source it expects and what it covers.

| Walk | Screen |
|------|--------|
| `home.txt` | Home (main, iptv): opens on the last channel watched, Left to the rail and back, header buttons, Down back to the row |
| `home-rows.txt` | Home (main, jellyxtream): Continue Watching and favourites rows — Left from a first card to the rail and back, Up/Down keep each row's card, Up from the first row to the header |
| `live-tv-browse.txt` | Live TV browse: categories and channels as two panes |
| `live-tv-preview.txt` | Live TV preview: the docked channel panel's tabs and rows; Left on the first tab stays, Back leaves |
| `live-tv-fullscreen.txt` | Live TV full screen: the channel panel over the video |
| `live-tv-osd.txt` | Live TV full screen: the OSD button row |
| `live-tv-back.txt` | Back from the preview to browse, on the playing channel |
| `guide.txt` | TV Guide grid, header, programme details |
| `details.txt` | Movie details and the episodes list |
| `search.txt` | Search field (`TvSearchField`) and recent searches |
| `search-filter.txt` | Search: the filter icon under the field and its dialog (Source, Decade, Genre) |
| `settings.txt` | Settings: the start focus only (the focus tests cover the rail and the panes) |
| `settings-guide.txt` | Settings: the start focus only (`SettingsSourcesGuideFocusTest` covers the rest) |
| `sources.txt` | Sources: the start focus only (`SourcesFocusTest` covers the rows and slots) |
| `edit-source.txt` | Edit Source: the way to Guide sources, and Back to its row |
| `epg-management.txt` | Guide sources of one source: Back to Edit Source |
| `live-sync.txt` | Live sync screen, linked to a sync group |
| `recent-remove.txt` | Live TV browse, Recent (iptv): menu opens on Remove from Recent, removal → next row, Menu undoes; edit mode (pencil, OK removes, Menu undoes), Back from Clear Recent → the pencil |
| `favorites-remove.txt` | Live TV browse, Favourites (iptv, one favourite): Remove from Favourites with no confirmation, the empty list's Refresh focused, Menu undoes |
| `live-panel-remove.txt` | Live TV preview's channel panel, Recent tab (iptv): removal → next row (not the tabs), Menu undoes |
| `home-card-actions.txt` | Home (iptv): a Channels card's menu, removal → next card, Menu undoes; a Favourite channels card opens on Remove from Favourites, Back keeps focus |
| `ci-settings.txt` | CI only (test household, fake source): the profile picker, the rail to Settings, Settings' rail and every group for Admin, Admin's and Kid's pages |

The first fifteen were run on the TV emulator on 2026-10-03 (ten re-recorded with `-r`, 0 mismatches
after the fixes). After the navigation rail and the Home change (2026-10-08 / 09): `home.txt`,
`home-rows.txt`, `sources.txt` and `live-tv-preview.txt` re-run with 0 mismatches; `search.txt`
and `settings.txt` match on their rail steps but depend on the device's recent searches and on a
profile page that needs another source; `details.txt`, `live-tv-browse.txt`, `guide.txt` and
`epg-management.txt` were edited by hand for the rail and not yet re-run (`guide.txt` and
`epg-management.txt` need a source with a guide). The Recent and Favourites rework (2026-10-09:
immediate removal with Undo, edit mode, Home card actions, the Favourite channels row) was walked by
hand on the emulator, then the four walks above were recorded (2026-10-09, 0 mismatches each) and
`home.txt` re-run (0 mismatches); `home-rows.txt` not re-run after it. Some file headers still say "not yet recorded" from before those rounds.

#### Nightly TV walk in CI

`instrumented-tests.yml`'s job `tv-walk` (nightly and by hand) runs walks on a TV emulator
(Android TV image, API 34, `tv_1080p`) with fixed data, so it needs no prepared device. It builds
`:tv:assembleDebug`, checks and starts the fake Xtream server on the runner (port 8090, see "Test
source: fake Xtream"), then on the emulator runs `scripts/ci-tv-walk.sh`: install the APK, seed the
test household, open the app, run the walks listed in `scripts/focus-walks/ci-walks.txt`. On a
failure it uploads `walk-output/` (the walk log, a screenshot, the UI dump and logcat) and the
server's log as the artifact `tv-walk-output`.

The test household (`scripts/seed-test-household.sh <serial> [server URL]`, debug builds only,
emulators only): the source "Fake Xtream" (`http://10.0.2.2:8090`, `test` / `test`) and the
profiles Admin (the first profile renamed, an admin, in use, no PIN), Guest (a grown-up) and Kid (a
child profile), all with the source. The script sends `TestHouseholdDebugReceiver` its broadcast
and waits (up to 5 minutes) until the catalogue is loaded. The app refuses when it has a source
already, so the script never changes a device that has data; it does not open the app either. The
app's language is English on a fresh install, which the walks need.

The CI walks start on the profile picker of a fresh start, each where the one before ended, and
assert labels, not values that depend on the build (theme names, the TMDB key). A new CI walk is
recorded the same way as the others, on an emulator seeded like CI: a fresh install there needs an
uninstall first, so ask before doing it on an emulator that has data. To run the same thing
locally on a throwaway TV emulator with the server running: `./gradlew :tv:assembleDebug`, then
`scripts/ci-tv-walk.sh <serial>`.

`ci-settings.txt` was written from the code and `settings.txt` on 2026-10-24 and has not run yet:
the first CI run checks it.

---

## Device-Specific Tips

- **NVIDIA Shield:** Supports 4K/HDR and AV1/HEVC hardware decoding. Ideal for stress-testing heavy streams.
- **Sony Bravia:** Features mid-range TV chipsets. Test for overscan compliance (56dp horizontal, 32dp vertical margins) and smooth 60fps D-pad focus animations.
- **Mobile Devices:** Locked to portrait outside the player; the player switches to landscape (either way round, by sensor).
