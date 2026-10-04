# Build, Deployment & Run Guide

Unified guide for building, installing, and deploying Fijerena across both **Android TV** (NVIDIA Shield, Chromecast with Google TV, Sony Bravia) and **Android Mobile** (phones and tablets).

---

## Prerequisites

- **JDK:** OpenJDK 21
- **Android SDK:** Command-line tools or Android Studio
- **API Targets:** `minSdk = 30` (Android 11+), `targetSdk = 35`, `compileSdk = 36`
- **Gradle:** 9.6.0 via the `./gradlew` wrapper (AGP 9.4.1); sources compile to Java 21
- **Host tools:** `adb`; `sqlite3` for the backup/restore scripts and `deploy-tv-ip.sh`; `python3` for the focus walks
- **TMDB key (optional):** `TMDB_API_KEY=<key>` in the gitignored `local.properties` enables TMDB synopses; without it TMDB lookups are skipped. A git worktree has no `local.properties` of its own — copy or symlink the main checkout's.

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

The build defines no signing configuration, so these produce unsigned APKs
(`*-release-unsigned.apk`); sign them (e.g. with `apksigner`) before installing. Minification is off.

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

# Run unit tests (CI runs testDebugUnitTest)
./gradlew test

# Source gates (see AGENTS.md, constraints 7 and 13, and the TV focus rules)
scripts/check-cancellation.sh       # catch (Exception) in suspend code rethrows CancellationException
scripts/check-viewmodel-launch.sh   # no new bare viewModelScope.launch (use launchGuarded)
scripts/check-focus-retry.sh        # no requestFocus() + catch (IllegalStateException)
```

CI is `.github/workflows/android-build.yml`, started by hand (`workflow_dispatch`): unit tests,
ktlint, Android Lint, a grep for blanket destructive Room fallbacks, the three gates above, a check
that the build left `core/network/schemas` unchanged, the sync server's `npm ci && npm test`, then
`assembleDebug` with both APKs uploaded as artifacts.

`./gradlew connectedAndroidTest` uninstalls the app and wipes its data on every connected device —
only with real devices disconnected, or one emulator targeted with `ANDROID_SERIAL`.

---

## Deployment & Device Targeting

Both TV and Mobile share the identical `applicationId`: `org.njarasoa.fijerena`.

Use the deploy scripts rather than hand-run `gradlew` + `adb install`; each builds the right APK
incrementally and installs it as an update (`install -r`):

| Script | Target | Notes |
|--------|--------|-------|
| `scripts/deploy-tv-emulator.sh [serial]` | TV emulator | Picks the first emulator with the leanback feature; asks before interrupting playback |
| `scripts/deploy-mobile-emulator.sh [serial]` | Phone emulator | Picks the first emulator *without* leanback, so a TV emulator is never overwritten |
| `scripts/deploy-tv-ip.sh <ip>[:port] …` | Network TVs | Port 5555 by default; skips unreachable devices and any that don't report `tv` in `ro.build.characteristics`; asks before interrupting playback; builds once, then backs up each device's user data with `backup-app-data.sh` into `backups/*.tar.gz` (kept 7 days) and installs in parallel |
| `scripts/deploy-mobile-usb.sh [serial]` | USB phone | Uses the only USB device when no serial is given; backs up the phone's user data first, like `deploy-tv-ip.sh` (no playback check) |

A real-device script doesn't install on a device that has the app but whose backup failed. Other
scripts: `scripts/uninstall-app.sh [serial]` is the only sanctioned uninstall (it asks you to type
`UNINSTALL`); `scripts/tune-system-memory.sh` tunes the development host (zram, swappiness), not a
device.

> [!CAUTION]
> **Strict Deployment Rules:**
> 1. **Back Up Before Installing to Real Hardware — `install -r` is NOT a guaranteed data-safe operation.** Never run `adb uninstall` or clear data to resolve deployment issues. `adb install -r` *usually* preserves Room databases, credentials, favorites, and watch state — but a signing-key mismatch (or other cause) can make it install fresh with no warning, silently wiping everything. This happened for real on 2026-09-08 across 3 household TVs with zero warning from `adb` (it reported "Success" on every device). Before installing to any real device — not an emulator — run `scripts/backup-app-data.sh <serial> <out.tar.gz>` (user data only); `scripts/restore-app-data.sh <serial> <backup.tar.gz>` puts it back — see Backup & restore below. Both real-device deploy scripts (`deploy-tv-ip.sh`, `deploy-mobile-usb.sh`) run the backup themselves. Do this every time, unprompted — user permission to deploy is not permission to skip the backup.
> 2. **Device Detection:** Always detect device type via `getprop ro.build.characteristics` (or inspect `product:`/`model:` in `adb devices -l`) before deploying. Never assume target identity from port numbers or IPs.
> 3. **No Auto-Launch:** Never automatically launch the app (`am start` or monkey intents) after install. Let the user launch the app manually when ready.

### Backup & restore

```bash
scripts/backup-app-data.sh  <serial> backups/<name>.tar.gz   # ~50 KB, needs sqlite3 on the host
scripts/restore-app-data.sh <serial> backups/<name>.tar.gz   # force-stops the app first
```

A backup holds user data only: `shared_prefs`, `providers.db` (sources, profiles, guide sources,
live sync) and `xtream_v2_user_data.db` — the `watch_state`, `favorite_state` and `sync_*` tables,
copied out of `xtream_v2.db` on the host (its `-wal` comes off the device with it, so uncommitted
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

`tools/jellyfin-xtream/xtream_bridge.py` (Python 3, stdlib only) serves a Jellyfin server as an Xtream panel, so the Xtream code paths can be tested against a known library. It holds no credentials: the Xtream username/password the app sends are checked against Jellyfin. Pointing it at another Jellyfin server only means changing `JELLYFIN_URL`.

```bash
tools/jellyfin-xtream/restart.sh          # (re)starts it detached on :8080 for sm.njarasoa.org
JELLYFIN_URL=https://other.host BRIDGE_PORT=8081 tools/jellyfin-xtream/restart.sh
```

In the app, add an Xtream source with Server URL `http://10.0.2.2:8080` (emulator → host; use the host's LAN IP from real devices) and the Jellyfin username/password. Movie and TV-show libraries become one category each; Live TV and EPG appear only if Jellyfin has Live TV. Playback redirects to Jellyfin, so the device must reach `JELLYFIN_URL` too. Keep `xtream_ids_<host>.db` (or the file named by `BRIDGE_DB`): it maps Xtream ids to Jellyfin GUIDs, and losing it changes every id (orphaning watch history and favourites).

Like a real panel, a series' `last_modified` moves when episodes are added (Jellyfin's `DateLastMediaAdded`, else `DateCreated`), so adding an episode in Jellyfin makes the app fetch that show's episodes again after its next sync. An item that sits in two Jellyfin libraries is listed once, under the first library by name. After editing the bridge, rerun `restart.sh`.

### 2. Physical Android TV (NVIDIA Shield, Chromecast, Sony Bravia)

TV devices connect via ADB over TCP/IP (port 5555). Because TV IP addresses drift across sessions via DHCP (on development subnet `192.168.68.0/24`):

1. **Find TV on Network:**
   ```bash
   # Query mDNS for broadcasting ADB devices
   adb mdns services
   ```
   *(Falls back to `arp -a | grep 192.168.68.` if mDNS is unavailable)*

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

# Deploy Mobile APK (backs up first; pass the serial when more than one USB device is attached)
scripts/deploy-mobile-usb.sh [<device-id>]
```

---

## Logcat & Debugging

Filter logs for app-specific diagnostics:

```bash
# Stream app logs
adb -s <device-id> logcat | grep "fijerena"

# Stream crash logs and unhandled exceptions
adb -s <device-id> logcat *:E

# Clear logcat buffer
adb -s <device-id> logcat -c
```

### Crash log and Diagnostics

Every build records what went wrong on the device itself, so a crash on a TV is still there after
the fact without logcat having been attached:

- **Diagnostics** (developer mode on for the profile in use, on its page under Settings →
  Profiles; then Settings → About & advanced → Open Diagnostics; mobile's can also Share as
  text) lists, newest
  first, the app's own crash log — uncaught exceptions, and exceptions absorbed by the app-wide
  coroutine scopes (`AppScopes`), sync records that couldn't be applied, a database set aside on
  downgrade — together with Android's record of why recent processes ended (ANR, native crash,
  low-memory kill, package update…). Credential files the app had to reset (unreadable after a
  Keystore reset) appear as `credentials reset: <file>`.
- Off-device:

```bash
adb -s <device-id> shell run-as org.njarasoa.fijerena cat files/crashlog/crashes.log
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
adb -s <device-id> shell "run-as org.njarasoa.fijerena sh -c 'mkdir -p files/safemode && printf \"$NOW\n$NOW\n$NOW\n\" > files/safemode/launches'"
adb -s <device-id> shell am force-stop org.njarasoa.fijerena
# then launch the app; to leave by hand instead of Continue:
adb -s <device-id> shell run-as org.njarasoa.fijerena rm files/safemode/launches
```

### Debug broadcasts

Two receivers exist in debug builds only (`src/debug` source sets) and require
`android.permission.DUMP`, which the adb shell holds and other apps don't — so `adb shell am
broadcast …` works, and nothing else on the device can link the sync account or force refreshes.

```bash
# EpgSyncDebugReceiver (core:network): enqueue an immediate, forced EpgSyncWorker run
adb -s <device-id> shell am broadcast -a org.njarasoa.fijerena.DEBUG_EPG_SYNC -p org.njarasoa.fijerena

# SyncDebugReceiver (core:ui): drive live sync; results go to logcat under SyncDebug
adb -s <device-id> shell am broadcast -a org.njarasoa.fijerena.DEBUG_SYNC -p org.njarasoa.fijerena --es cmd status
```

`DEBUG_SYNC` commands (`--es cmd …`): `setup --es url <server> [--es secret <setup secret>] [--es name
<device name>]` (new account), `invite` (logs an invite QR code's text), `scan --es qr '<qr text>'`,
`handoff --es url <server>`, `now` (a sync pass), `status`, `unlink`. The full list is in the
receiver's KDoc.

### Focus walks

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
library-data Clear button while recording.

One driver per emulator: the script, a person with the remote, and any other script sending keys
must never share a device at the same time, or the focus read after each key belongs to someone
else's key. Put the app on the walk's start screen by hand first; the script does not navigate
there. Each file's header comments name the start screen, the source it expects and what it covers.

| Walk | Screen |
|------|--------|
| `home.txt` | Home: first card, header buttons, Down back to the last card |
| `live-tv-browse.txt` | Live TV browse: categories and channels as two panes |
| `live-tv-preview.txt` | Live TV preview: the docked channel panel's tabs and rows |
| `live-tv-fullscreen.txt` | Live TV full screen: the channel panel over the video |
| `live-tv-osd.txt` | Live TV full screen: the OSD button row |
| `live-tv-back.txt` | Back from the preview to browse, on the playing channel |
| `guide.txt` | TV Guide grid, header, programme details |
| `details.txt` | Movie details and the episodes list |
| `search.txt` | Search field (`TvSearchField`) and recent searches |
| `settings.txt` | Settings rail and panes, pickers |
| `settings-guide.txt` | Settings: Source & guide rows and guide data maintenance |
| `sources.txt` | Sources rows and their fixed slots |
| `edit-source.txt` | Edit Source, two columns |
| `epg-management.txt` | Guide sources of one source, a row's Auto-refresh picker |
| `live-sync.txt` | Live sync screen |

All fifteen were run on the TV emulator on 2026-10-03 (ten re-recorded with `-r`, 0 mismatches
after the fixes). Still open: `epg-management.txt` needs a source that has guide sources, and
`live-tv-preview.txt` gained a final Left step afterwards that has not been re-recorded. Some file
headers still say "not yet recorded" from before that round.

---

## Device-Specific Tips

- **NVIDIA Shield:** Supports 4K/HDR and AV1/HEVC hardware decoding. Ideal for stress-testing heavy streams.
- **Sony Bravia:** Features mid-range TV chipsets. Test for overscan compliance (56dp horizontal, 32dp vertical margins) and smooth 60fps D-pad focus animations.
- **Mobile Devices:** Locked to portrait outside the player; the player switches to landscape (either way round, by sensor).
