# Plans

Multi-phase work is planned in writing before it is built. A plan lives in `docs/plans/` while it
is active, partly done or deliberately deferred. When it is finished or dropped it moves to
`docs/plans/archive/`, which is not published. `docs/RELEASE_NOTES.md` stays the record of what shipped.

- **Commit hashes** in plans written before 2026-10-23 refer to the project's earlier history,
  which is kept privately; they don't resolve in the public repository.
- **Naming:** `YYYYMMDD_<kebab-case-topic>-plan.md`, dated the day the plan was written.
- **Status:** every plan opens with a `**Status:**` line, and multi-phase plans keep a Progress
  table. Both are updated as work goes on: mark a phase in progress when it starts and done (with
  the commit) when it lands. The plan's own Status line is the source of truth; this index is a
  summary of it.
- **Finishing a plan:** source comments cite open plans by path and phase. When a plan moves to
  `archive/`, every citation of it becomes its name in words ("the catchup plan", `git grep
  <file name>`), and anything it still records as live moves into the standing docs.

## Open

| Date | Plan | Goal | Left |
|---|---|---|---|
| 2026-11-11 | [Audit cleanups](./20261111_audit-cleanups-plan.md) | Five small fixes the 2026-11-11 code audit confirmed: a scope leak in AppContainer, v2 test rule in mobile tests, lifecycle-aware flow collection, hoisted search regexes, standard XMLTV timestamp parsing | AC-5 done 2026-11-11; AC-1 to AC-4 not started |
| 2026-11-11 | [Match frame rate](./20261111_match-frame-rate-plan.md) | The TV app plays full-screen video at a display mode that matches the content's frame rate (24 fps at 24 Hz, 50 Hz channels at 50 Hz), with a This Device switch | Done 2026-11-11 (Phases 1 to 3: films and Live TV full screen); device checks on the Shields and the Bravia wait for the user |
| 2026-11-11 | [Preferred track languages](./20261111_preferred-track-languages-plan.md) | Each profile sets a preferred audio and subtitle language, used when the title or series has no saved choice; TV and phone | In progress; PL-Q1 to PL-Q7 decided; Phases 1 to 3 done |
| 2026-11-11 | [Provider data tests](./20261111_provider-data-tests-plan.md) | Tests that run the parsers on recorded and hand-written provider answers (Xtream, XMLTV, M3U, TMDB, Jellyfin, sync), with a privacy gate for the fixtures | Done 2026-11-11 (Phases 1 to 5); PD-Q1 to PD-Q6 decided (a) |
| 2026-11-11 | [Low storage](./20261111_low-storage-plan.md) | A full disk is an error of its own, never retried as a network error; large writes check free space first and fail with a clear message | Phases 1 to 4 done 2026-11-11; emulator check (LS-Q6) waits |
| 2026-11-11 | [Device diagnostics over live sync](./20261111_device-diagnostics-sync-plan.md) | A masked, size-capped summary of each device's recent crashes and errors, readable from another device of the sync group | Phases 1 to 4 done 2026-11-11; emulator check waits |
| 2026-11-01 | [Home row order](./20261101_home-row-order-plan.md) | Each profile orders (and may hide) its Home rows on TV and phone; the order syncs to every device of the profile | Planned 2026-11-01; open questions HO-Q1 to HO-Q3 |
| 2026-10-31 | [Jellyfin in the merged lists](./20261031_jellyfin-merge-plan.md) | A Jellyfin source's films and shows in the merged lists, Search and Home rows from a local copy of its library; watch history and favourites kept current with the Jellyfin server both ways | Planned 2026-10-31; open questions JM-Q1 (storage) and JM-Q2 (shared login) |
| 2026-10-24 | [Tablet layout](./20261024_tablet-layout-plan.md) | The phone app as a good tablet app: both orientations, a navigation rail, wider grids, and list-and-details panes, without changing the phone | Shelved 2026-10-24; decisions taken, not started |
| 2026-10-24 | [Text fonts and colours](./20261024_text-roles-audit-plan.md) | Every text takes its font and colour from its role: names in the title font, the accent only on what is current or selected, no text unreadable on a light theme or a fill | Built 2026-10-24 (Phases 1–3); left: a few phone accents not reviewed (detail stream-name rows, guide lines, search progress, the sync status line) |
| 2026-10-16 | [Design system](./20261016_design-system-plan.md) | Every look in both apps (text, buttons, fields, rows, chips, dialogs, borders, dock, rail) derived from the colour theme and Look and Feel through one token layer and one style class per element kind; a CI gate stops raw values | Built 2026-10-16: every file at 0 in the gate; full per-screen walk in every theme and Look and Feel left |
| 2026-10-16 | [One button look](./20261016_one-button-look-plan.md) | Every button in both apps looks the same, defined once per app (`ButtonStyle`, `TvButtonStyle`, `PhoneButtonStyle`): three kinds (main, other, danger), the Display style's shape, labels centred | Built 2026-10-16; screens beyond the profile page not walked; the TV look is not settled |
| 2026-10-12 | [Multi-source: no current source](./20261012_multi-source-search-history-plan.md) | No current source: each profile has its own sources (opt-in), and browsing, search, Recent, Continue Watching, favourites and the guide search are merged across them; every item plays from its own source | In progress: Phases 1–7 done 2026-10-12; Phase 8 (device walks, docs) not started |
| 2026-09-14 | [Codebase robustness](./20260914_codebase-robustness-plan.md) | Pay down technical debt found in a whole-codebase review | Phase 4 done 2026-10-12 (one accessibility node per channel row); Phase 5 in progress: more Compose UI tests (Robolectric, on the JVM); Phases 2–3 (single-return sweeps) dropped 2026-10-03; SecretStore part of Phase 6 deferred |
| 2026-08-28 | [Secret store migration](./20260828_secret-store-migration-plan.md) | Replace deprecated `EncryptedSharedPreferences` with an owned Keystore-backed `SecretStore` | All five phases; not started, deferred on purpose (the crash loop it cites is already handled) |
