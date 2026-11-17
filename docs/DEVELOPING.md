# Developing Fijerena

How to build, install, check and change the apps. To use the apps, see the [README](../README.md)
and the [User Guide](USER_GUIDE.md).

## Building

Prerequisites: **JDK 21**, the **Android SDK** (API level 37), and optionally Android Studio (a
release that supports AGP 9.4).

```bash
git clone https://github.com/mpilasy/fijerena.git
cd fijerena
./gradlew assembleDebug            # both apps
./gradlew :tv:assembleDebug        # TV only   (or scripts/build-tv.sh)
./gradlew :mobile:assembleDebug    # phone only (or scripts/build-mobile.sh)
```

Outputs: `tv/build/outputs/apk/debug/tv-debug.apk` and
`mobile/build/outputs/apk/debug/mobile-debug.apk`. Debug builds also include x86 and x86_64 so
emulators run natively.

Nothing is required to build. No release build carries a TMDB key. A debug build takes the key in
`TMDB_API_KEY` in your `local.properties`, if you set one (see
[docs/RUN_GUIDE.md](RUN_GUIDE.md) → Prerequisites). Otherwise, for TMDB details, enter your own
key (v3 API key or v4 read access token, free from themoviedb.org → Settings → API) in the app:
Settings → Sources & Guide → Film and Show Details (../TMDB). Without a key, nothing is sent to TMDB.

`./gradlew :tv:assembleRelease` and `:mobile:assembleRelease` build release APKs, signed when the
`FIJERENA_*` signing variables or a `keystore.properties` provide a key, unsigned otherwise (see
[docs/RUN_GUIDE.md](RUN_GUIDE.md) → Releasing).

## Installing a Development Build

The deploy scripts build the right APK and install it as an update:

```bash
scripts/deploy-tv-emulator.sh [serial]       # TV emulator
scripts/deploy-mobile-emulator.sh [serial]   # phone emulator
scripts/deploy-tv-ip.sh <ip>[:port] ...      # network TVs
scripts/deploy-mobile-usb.sh [--debug] [serial]   # phone over USB
```

The TV app is `org.njarasoa.fijerena` and the phone app `org.njarasoa.fijerena.mobile`, so one
APK never replaces the other. `adb install -r` can wipe the app's data without warning, so the
real-device scripts back up user data first (`scripts/backup-app-data.sh`, which needs `sqlite3` on
the host). `deploy-mobile-usb.sh` installs the non-debuggable `localRelease` build by default, which
cannot be backed up; it gives a warning and installs. With `--debug` it installs the debug build, and
backs up first while the installed build is debuggable.
Device discovery, backup/restore, logs and debugging are in
**[docs/RUN_GUIDE.md](RUN_GUIDE.md)**.

## Checks

```bash
./gradlew ktlintCheck          # code style (ktlintFormat fixes it)
./gradlew lintDebug            # Android Lint; each module's lint-baseline.xml holds the old warnings
./gradlew testDebugUnitTest    # unit tests
scripts/check-cancellation.sh && scripts/check-viewmodel-launch.sh && scripts/check-focus-retry.sh
scripts/check-init-order.py
scripts/check-design-system.sh
scripts/check-fixture-privacy.sh
```

GitHub Actions runs these on every push to `main`: `checks.yml` (the gates), `android-build.yml`
(tests, lint, the Room schema check, both debug APKs) and `server.yml` (the sync server's tests,
when `server/` changes). `instrumented-tests.yml` runs nightly and by hand (the phone instrumented
tests and the TV focus walk). `release.yml` builds and publishes the signed APKs when a
`v<version>` tag is pushed.

```bash
# Instrumentation tests (requires a connected device/emulator).
# WARNING: this uninstalls the app and wipes its data on EVERY connected device;
# disconnect real devices first, or target one emulator with ANDROID_SERIAL.
./gradlew connectedAndroidTest
```

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 2.4.21 |
| Build System | Gradle | 9.8.2 |
| Build System | Android Gradle Plugin (../AGP) | 9.4.1 |
| UI Framework | Jetpack Compose | 2026.09.00 BOM (ui/foundation/runtime/animation 1.12.1) |
| Material Design | Material 3 | 1.4.0 (../`strictly`) |
| TV Components | androidx.tv.material3 (lists are plain LazyColumn/LazyRow) | 1.1.0 |
| Video Player | Media3 (../ExoPlayer), Jellyfin FFmpeg decoder | 1.9.0 |
| Trailer Player | android-youtube-player (YouTube's embedded player in the system WebView, MIT) | 13.0.0 |
| Networking | Ktor (OkHttp engine) | 3.6.0 |
| Serialization | kotlinx.serialization | 1.12.0 |
| Database | Room (../FTS4) | 2.8.5 |
| SQLite | Bundled requery build (FTS5 capable) | 3.49.0 |
| Background Work | WorkManager | 2.12.0 |
| Paging | androidx.paging | 3.5.3 |
| Image Loading | Coil | 3.6.3 |
| Navigation | Navigation Compose | 2.10.2 |
| Coroutines | kotlinx.coroutines | 1.11.0 |
| SMB Client | smbj (../Hierynomus) | 0.15.0 |
| Sync server | TypeScript on Cloudflare Workers / workerd | see `server/package.json` |

`gradle/libs.versions.toml` is authoritative. Minimum SDK 30 (Android 11), target SDK 35, compile
SDK 37; release ABIs arm64-v8a and armeabi-v7a.

Architecture in one line: multi-module MVVM (ViewModel + StateFlow), provider types mapped to shared
domain models, one `MediaRepository` per source, manual DI through `AppContainer`. See
[docs/design.md](design.md).

## Project Structure

```
fijerena/
├── mobile/                    # Phone app (touch UI)
├── tv/                        # Android TV app (10-foot UI)
├── core/
│   ├── player/               # Media3 player, playback service, domain models, diagnostics (CrashLog, SafeMode, AppScopes)
│   ├── network/              # Source implementations, API clients, Room databases, EPG pipeline, live sync client
│   ├── ui/                   # Shared ViewModels, Compose components, design tokens, strings, AppContainer
│   └── navigation/           # Type-safe Screen definitions
├── server/                   # Live sync server (Cloudflare Worker / workerd Docker image)
├── scripts/                  # Build, deploy, backup/restore, CI checks, TV focus walks
├── tools/                    # Development only: jellyfin-xtream and fake-xtream test panels
├── docs/                     # Technical documentation
├── gradle/                   # Version catalogue (../libs.versions.toml)
├── licenses/fonts/           # Licences of the bundled fonts
└── CONTRIBUTING.md           # Coding rules and conventions
```

## Documentation

- **[docs/USER_GUIDE.md](USER_GUIDE.md)** - How to use the apps, in plain words
- **[docs/FEATURES.md](FEATURES.md)** - Feature reference, settings, themes
- **[docs/design.md](design.md)** - System design and architecture
- **[docs/NAVIGATION_GUIDE.md](NAVIGATION_GUIDE.md)** - Screens, navigation flow, TV focus handling
- **[docs/DATABASE_SCHEMA.md](DATABASE_SCHEMA.md)** - Room databases and SharedPreferences
- **[docs/epg_guide.md](epg_guide.md)** - EPG pipeline implementation guide
- **[docs/EPG_INDEX_STORAGE.md](EPG_INDEX_STORAGE.md)** - Why the EPG index grew to 87% dead space, and how to read DB state from a file header
- **[docs/RUN_GUIDE.md](RUN_GUIDE.md)** - Build, install, deploy, backup/restore, debugging, focus walks
- **[docs/RELEASE_NOTES.md](RELEASE_NOTES.md)** - Changelog
- **[server/README.md](../server/README.md)** - Live sync server: develop, deploy, self-host, API

## Contributing

1. Read [CONTRIBUTING.md](../CONTRIBUTING.md) for the coding rules (design tokens, TV focus, strings in English,
   French and Malagasy, database migrations).
2. Work on a branch.
3. Keep TV screens D-pad navigable and inside the safe margins; use the design tokens.
4. Make ktlint, Android Lint, the unit tests and the `scripts/check-*.sh` gates pass.
5. Test on both TV and phone; include screenshots or recordings for UI changes.
6. Write commit messages that say why, not only what.
7. Sign off each commit (`git commit -s`) to certify the [Developer Certificate of Origin](../DCO).

By contributing you agree that your contribution is licensed under the project's licence (../below).
