# Audit cleanups

**Status:** Planned 2026-11-11, not started. One commit; no open questions.

## Goal

Fix the five findings of the 2026-11-11 code audit that the code confirms. Each one is small.
Users will not see a regression: the gains are one leaked scope fewer, tests that run focus retries,
less work while the app is in the background, and support for standard 12-digit XMLTV timestamps.

## What the audit found (checked 2026-11-11)

The audit listed 15 findings. Five are real and listed under Work. The others were dropped:

| Finding | Why dropped |
|---|---|
| Static lambdas in `DialogKeys`, `PlayHandover` | No leak. `DialogKeys.onKey` holds `KeyInputLog::record` or null. `PlayHandover` already registers and unregisters in a `DisposableEffect`. |
| Unbounded caches in `XtreamLoginPicker` | Keys are (source, login), so the maps stay small. `picks` is capped at `PICKS_KEPT`. |
| Caches in `PersonViewModel`, `ChartRows` | They grow by a few rows per catalogue update. No memory problem is measured. |
| Linear scans of the New Episodes cards | The list has one card per followed show with new episodes, so it is short. No jank is measured. |
| Per-episode writes when a season is marked | Local SQLite statements in one transaction. A batch query saves milliseconds. |
| Regex in `extractYear`, `cleanUrl` | Runs once per screen. `StreamList` passes `title = null`, so it does not run per list item. |
| `ProviderRepository` made per playback | One small object per playback start. |
| 53 bare `viewModelScope.launch` calls | Each has its reason in `scripts/check-viewmodel-launch-allowlist.txt`. The next level rock solid resilience plan (R-09) owns this work. |
| 342 functions with more than one return | The rule applies to new and changed code. A refactor of old code adds risk and changes nothing for users. |

## Work

| # | Change | Where |
|---|---|---|
| AC-1 | Look up the source before `MediaRepository` is made. When the source is gone (deleted meanwhile), make no repository: today one is made and never closed, and its `writeScope` stays open. | `core/ui/.../di/AppContainer.kt`, `cachedOrNewRepository` |
| AC-2 | Move the 13 mobile tests on the old `createComposeRule` to `androidx.compose.ui.test.junit4.v2.createComposeRule`. CONTRIBUTING → Tech Stack: with the old rule, focus retries do not run. | `mobile/src/test/` |
| AC-3 | Use `collectAsStateWithLifecycle()` in place of `collectAsState()`, so collection stops while the activity is stopped. | `SourceBadges.kt` (2 sites), both `MainActivity.kt`, `TvAddProviderScreen.kt`, `ProviderSettingsSection.kt`, `WatchQuestion.kt` |
| AC-4 | Make the two regexes in `formatFtsQuery` top-level `private val`s. It runs on each search query. | `core/network/.../xtream/manager/XtreamContentManager.kt` |
| AC-5 | Parse 12-digit XMLTV timestamps (`YYYYMMDDhhmm`, seconds optional per XMLTV DTD) and timestamps without a space before `+HHMM`/`-HHMM`. Feeds without seconds currently fail `SimpleDateFormat` and drop all programmes; feeds without a space drop the timezone and shift air times. | `core/network/.../xmltv/XmltvParser.kt`, `parseTimestamp` |

## Checks

- ktlint, tests of `core:ui`, `core:network` and `mobile`, and compile of both apps.
- AC-1: a unit test. When `getProviderById` returns null, no repository is made and none is cached.
- AC-2: the 13 tests pass on the new rule.
- AC-3: on both emulators, Home, the source badges and the theme still show, and a theme change
  still applies at once.
- AC-5: unit tests for `XmltvParser.parseTimestamp`: 14-digit with space, 12-digit with/without space, and no-space timezone offsets.
