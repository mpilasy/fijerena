# Settings focus tests

**Status:** Proposed 2026-10-24. Not started; two decisions open.

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
5. **Nightly TV walk in CI** (TF-D2, later): a TV emulator job in `instrumented-tests.yml`, a small
   fake Xtream server with fixed data started in the job, profiles seeded through the debug receiver,
   then all remaining walks. Only if TF-D2 says so.

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

## Phases

1. **Spike:** the helper, the fixture, and one test (the Profiles group for an Expert and a child).
   Decide whole screen or panes from the result.
2. **Settings tests:** every Settings group and pane listed in Design 3; run time under one minute.
3. **Walks and docs:** walk files per TF-D1, RUN_GUIDE and CONTRIBUTING (a focus change updates its
   test, or its walk where no test covers it).
4. **Nightly TV walk** (only per TF-D2).

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. Spike | Not started | |
| 2. Settings tests | Not started | |
| 3. Walks and docs | Not started | |
| 4. Nightly TV walk | Not started (TF-D2) | |
