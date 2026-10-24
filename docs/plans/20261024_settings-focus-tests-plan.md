# Settings focus tests

**Status:** In progress 2026-10-24: Phases 1–3 done; Phase 4 built, its CI walk being fixed.

## Goal

The TV focus walks (`scripts/focus-walks/*.txt`, run by `scripts/tv-focus-walk.sh`) catch focus
regressions, but they are slow and need a prepared emulator. On 2026-10-24 a full run took more
than an hour and was stopped before the end. They match on-screen English text, so they need the
emulator in English, and they depend on the emulator's live data (sources, profiles, themes), which
changes. Settings changes most often and has the longest walks (`settings.txt`: 86 steps).

This plan moves the Settings focus checks into Robolectric Compose tests that run in the normal CI
build in seconds, with fixed data, and keeps the emulator walks as a local check before a release.

## Today

- 24 walk files; Settings has `settings.txt`, `settings-guide.txt`, `parental-controls.txt`,
  `sources.txt`, `edit-source.txt`, `live-sync.txt`, `epg-management.txt`.
- TV Compose UI tests already run on the JVM with Robolectric (`tv/src/test`), for example
  `ParentalListContentTest` (key presses with `performKeyInput { pressKey(...) }`, `assertIsFocused`).
- `SettingsScreen` (TV, about 1,050 lines) creates its view models itself
  (`viewModel(factory = SettingsViewModelFactory(context))`), which read `AppSettings` and the Room
  databases. A test must either give it real stores (Robolectric app context, preferences, an
  in-memory database) or use smaller composables that take state.
- `instrumented-tests.yml` runs a phone emulator every night; it has no TV job.

## Design

1. **A focus-walk helper for tests** (`tv/src/test`): a list of steps (key, expected focused text),
   the same idea as the walk files, run with `performKeyInput` and checked against the focused
   node's merged text. A failure names the step, the expected and the actual text.
2. **Fixed data:** a test fixture that writes profiles (an Expert, a grown-up, a child), their flags
   and PINs into `AppSettings`, and two or three sources into an in-memory settings database, before
   `setContent`. Strings come from the English resources, so no device language is involved.
3. **Tests per Settings screen** (TV): the rail and every group for an Expert, a grown-up and a
   child; the profile page (own and another profile's, Expert and child view, the linked rows
   G1 and E5); Parental Controls and a list; the Sources list and its ⋮ menu; Live Sync setup (not
   linked); Guide sources. Each test is the walk's path, short: entry focus, Down/Up through the rows,
   Right into a pane, Back returning focus to the row that opened it.
4. **Walk files:** the walks these tests cover are removed or cut down (TF-D1); `docs/RUN_GUIDE.md`
   → Focus walks says to run the remaining walks in English before a release.
5. **Nightly TV walk in CI** (TF-D2): a TV emulator job in `instrumented-tests.yml`, a small
   fake Xtream server with fixed data started in the job, profiles seeded through the debug receiver,
   then all remaining walks.

## Risks

- Running the whole `SettingsScreen` under Robolectric may need real stores that are slow or hard to
  seed. Phase 1 is a spike on one group; if it fails, the tests target the pane composables
  (`ProfileEditPane`, `ParentalControlsSettings`, the group panes) with state passed in, and the
  rail-to-pane moves stay with the walks.
- Robolectric's focus search can differ a little from a device's. The tests check the order the
  focus contract (CONTRIBUTING.md) sets, not pixels.

## Decisions for the user

- **TF-D1** When a Settings walk is covered by tests: (a) delete the walk file; (b) keep it, shorter,
  for the moves the tests can't make (rail to pane, screens opened from Settings). Recommend (b).
- **TF-D2** Nightly TV walk in CI with a fake Xtream server: (a) now, as Phase 4; (b) later, only if
  the local walks keep being skipped. Recommend (b).

**Decided (user, 2026-10-24):** TF-D1 (b) keep the walks, shorter; TF-D2 (a) the nightly TV walk
now, as Phase 4.

## Phases

1. **Spike:** the helper, the fixture, and one test (the Profiles group for an Expert and a child).
   Decide whole screen or panes from the result.
2. **Settings tests:** every Settings group and pane listed in Design 3; run time under one minute.
3. **Walks and docs:** walk files per TF-D1, RUN_GUIDE and CONTRIBUTING (a focus change updates its
   test, or its walk where no test covers it).
4. **Nightly TV walk** (TF-D2): a fake Xtream server with fixed data (films, series, live channels
   with a short XMLTV guide), started in the job; a TV emulator job in `instrumented-tests.yml`;
   profiles seeded through the debug receiver; the remaining walks recorded against that data.

## Spike result

**Done 2026-10-24.** The whole `SettingsScreen` works under Robolectric, with its own view models
on real stores; no change to app code was needed. Robolectric moves focus on D-pad keys sent with
`performKeyInput { pressKey(...) }` (the geometric search, `tvPane`, `onPreviewKeyEvent` Back), so
the tests follow the walk's path as it is. The Profiles part of `settings.txt` gave the same
focus order as on the emulator, for the Expert and for the child.

- **Helper** (`tv/src/test/.../testing/FocusWalk.kt`): steps `down("…")`, `center("…")`,
  `back("…")`…; after each key it reads the focused node's text and its descendants' text and
  content descriptions, first four joined by " / " (as `tv-focus-walk.sh`), and fails on the first
  step whose text doesn't start with the expected text, naming the step, the key, the expected and
  the actual text.
- **Fixture** (`tv/src/test/.../testing/SettingsFixture.kt`): writes the profiles Admin (Expert),
  Guest (grown-up) and Kid (child), and two Xtream sources (`*.invalid` addresses), into the
  settings database and `AppSettings` before `setContent`. Nothing starts a sync, so nothing reaches
  the network. What it needs:
  - `testImplementation(libs.room.runtime)` in `tv/build.gradle.kts` (the fixture uses the DAOs).
  - The guide index (`EpgIndexDatabase`) opens through requery's native SQLite, which the JVM
    doesn't have; `SettingsViewModel` reads it on open. The fixture puts an empty in-memory copy on
    Android's SQLite in its `INSTANCE` first.
  - After each test, `reset()` clears the singletons (the three databases, `EpgIndexer`,
    `EpgFileManager`, `TmdbApiService`, `AppContainer`): Robolectric makes a new application per
    test, and an instance from the previous test read its data (the second test failed until then).
- **Dispatcher:** the rule is `createComposeRule(StandardTestDispatcher())`. With the default
  (unconfined) one a `LaunchedEffect` starts before the rows below it register with their pane,
  and the screen's first focus misses.
- **Back on the rail** leaves Settings through the activity's back dispatcher, not `onBack`: a
  test can't check it; the walk keeps that move.
- **Run time:** the two tests (`SettingsProfilesFocusTest`, 42 and 13 steps) take about 4.5 s, of
  which about 3.5 s is the first test's Robolectric start; each further test about 0.7 s.

**Phase 2:** keep the whole-screen route and `FocusWalk`; one test class per group or pane, each
test a walk path from `settings.txt` and the other Settings walks (open the group from the rail,
Right, Down to the end, OK into a pane, Left or Back to the row). Extend `SettingsFixture` when a
group needs more data (a PIN, guide sources, a backup waiting in Downloads); keep sources on
`*.invalid` and add any new singleton the screen reaches to `reset()`. Never press OK on a
destructive row. Expected time for all of Settings: well under one minute.

   **As built (2026-10-24, not yet run in CI):** `tools/fake-xtream/server.py` (stdlib only, fixed
   data: News / Kids / Adult live and film categories, two series categories, a guide made around
   the current time, streams answer 404; `--self-test` checks every endpoint).
   `TestHouseholdDebugReceiver` (core:network, debug source set, DUMP-guarded like the other debug
   receivers) adds the source, renames the first profile Admin (Expert) and adds Guest and Kid (a
   child), then loads the catalogue; it refuses an app that has a source already. Tested with
   Robolectric (`core/network/src/testDebug`). `scripts/seed-test-household.sh` sends it and waits;
   `scripts/ci-tv-walk.sh` installs, seeds, opens the app and runs `scripts/focus-walks/ci-walks.txt`.
   The job `tv-walk` in `instrumented-tests.yml` (Android TV image, API 34, x86, `tv_1080p`, the
   configuration of the TV job removed on 2026-10-12) uploads the walk output on a failure. One walk
   so far, `ci-settings.txt` (the picker, the rail, Settings for Admin, Admin's and Kid's pages),
   written from the code: the first CI run checks it. The other walks follow once it passes.

## Phase 2 as built

**Done 2026-10-24.** Ten more test classes, 21 tests and 283 steps, on the whole screen as in
Phase 1; all 23 Settings focus tests run in about 10.5 s (about 4 s of it Robolectric's start).
No focus bug found; no app code changed.

- **Settings groups** (`tv/src/test/.../feature/settings/`): `SettingsRailFocusTest` (the rail
  for an Expert, a grown-up and a child), `SettingsPlaybackDisplayFocusTest` (pickers; the rows
  that open the profile page at Play next episode and at UI theme, and Back or Left to them, for
  an Expert and a grown-up), `SettingsProfilePageFocusTest` (a grown-up's own page, a grown-up's
  page in an Expert's view, a child's page with an Expert PIN: no "Set a PIN" row, PIN On, and
  "Parental Controls ›" opening that group), `SettingsParentalControlsFocusTest` (the group, the
  Exceptions list to Reset, Check a source, the link to Profiles), `SettingsSourcesGuideFocusTest`
  (the rows, the guide's maintenance and TMDB key sub-panes), `SettingsSyncBackupFocusTest`,
  `SettingsAboutFocusTest` (Expert and grown-up).
- **Screens Settings opens**, shown directly: `SourcesFocusTest` (the Sources list's slots and ⋮
  menu; Edit Source's two columns), `GuideSourcesFocusTest` (a guide source's row and its
  auto-refresh picker), `LiveSyncSetupFocusTest` (Live sync not linked).
- **Helpers:** `SettingsFocusTest` (the rule, the walk, `open`, or `seed` then `show` for another
  screen); `FocusWalk.awaitFocused` waits for a first focus that waits for a list loaded off the
  main thread; the walk reads the top window's focus, so a dialog's rows count. The fixture adds
  a PIN on chosen profiles (the one in use unlocked), a weekly guide source on Source One and
  `sourceId`; `reset()` also clears `SyncManager` and `PinSession`.

**Stays with the walks (Phase 3):** Back on the rail (leaves Settings) and Back that closes a
dialog (both go through the activity or the dialog's window, which a test key doesn't reach);
Back from a screen Settings opens (Sources, Live sync, Guide sources, Edit Source) to the row that
opened it; Live sync linked to a sync group (`live-sync.txt`); a word removed and put back in a
list (`ParentalListContentTest` covers it on the list alone); Check a source with several sources
and real categories.

## Phase 3 as built

**Done 2026-10-24.** Each Settings walk keeps only the moves the tests can't make, plus the steps
that reach them; a header line names the test classes that cover the rest. Kept lines have the
texts recorded on the emulator; no line is new, and no walk was run.

| Walk | Steps before | Steps after | Kept |
|---|---|---|---|
| `settings.txt` | 86 | 0 | the start focus |
| `settings-guide.txt` | 18 | 0 | the start focus |
| `parental-controls.txt` | 42 | 20 | a word removed and put back, Check a source with real data |
| `sources.txt` | 9 | 0 | the start focus |
| `live-sync.txt` | 13 | 13 | all (linked; no test) |
| `epg-management.txt` | 16 | 1 | Back to Edit Source |
| `edit-source.txt` | 20 | 12 | the way to Guide sources, Back to its row |

Back on the rail (leaves Settings), Back that closes the Sources ⋮ menu and Back from Sources to
Manage Sources have no recorded step yet; record them with `scripts/tv-focus-walk.sh -r` at the
next walk before a release. CONTRIBUTING (focus walks rule) and RUN_GUIDE (Focus walks: how to run
the tests, the walks in English before a release) updated.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. Spike | Done 2026-10-24 | 3fe44e1f |
| 2. Settings tests | Done 2026-10-24 | 68006eb0 |
| 3. Walks and docs | Done 2026-10-24 | 11682338 |
| 4. Nightly TV walk | Built; first CI run: infrastructure works, walk fixed at its first line, second run due | 1870a1b4 |

**Phase 3 follow-up (2026-10-24).** The three moves left unrecorded were recorded on the TV
emulator in English: Back on the Settings rail (to Home's content), Back closing the Sources ⋮
menu (to ⋮), Back from Sources (to Manage Sources). The first CI run of `ci-settings.txt` stopped
at its first line: the picker's cards read only the initial ("A"), and the + card nothing. The
cards now carry their names (`ProfilePickerScreen`, `c7e4571a`), a screen-reader fix as well.
