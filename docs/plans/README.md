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
| 2026-10-24 | [Settings focus tests](./20261024_settings-focus-tests-plan.md) | Settings' TV focus checks as Robolectric tests with fixed data, in the normal CI build; the emulator walks stay as a local check before a release; a nightly TV walk in CI later, if wanted | In progress: Phases 1 and 4 |
| 2026-10-24 | [Text fonts and colours](./20261024_text-roles-audit-plan.md) | Every text takes its font and colour from its role: names in the title font, the accent only on what is current or selected, no text unreadable on a light theme or a fill | Built 2026-10-24 (Phases 1–3) |
| 2026-10-24 | [Loading indicator](./20261024_loading-indicator-plan.md) | The player's "Buffering…" and "Tuning" text become an iris (films), a reel (episodes) and a ring with the channel logo (Live TV), with the title or channel logo at the top left; the last frame stays during a wait mid-stream; the TV stream info banner waits for play | Built 2026-10-24; checked on the emulators except the TV tuning ring |
| 2026-10-21 | [Meadow look](./20261021_meadow-look-plan.md) | A hand-painted look: Meadow (day, Expert only) and Meadow dusk colour themes with a painted sky and hills, a Meadow Look (Klee One, Zen Maru Gothic, round corners), packaged UI themes | Built 2026-10-21, checked on the emulators |
| 2026-10-21 | [UI themes](./20261021_ui-themes-plan.md) | A per-profile UI theme: a packaged one forces the colour theme and the Look and Feel; Custom keeps them free. A separate switch turns off the poster background | Built 2026-10-21: seven packaged UI themes and Custom, poster switch; checked on the emulators |
| 2026-10-20 | [Console look (LCARS)](./20261020_lcars-look-plan.md) | A starship-console colour theme and Look and Feel (neutral name "Console"): black ground, flat bright blocks, pill buttons, an L-shaped elbow frame (TV rail and top bar; caps on the phone's tab roots), condensed capitals (Antonio, OFL), focus by fill change only; all through the design-system tokens | Built 2026-10-21 (Phases 1–5): colour theme, Look, TV frame, phone dock, UI theme; checked on the emulators |
| 2026-10-20 | [Parental controls](./20261020_parental-controls-plan.md) | A Parental controls page (admin only, synced to every device) where the kids', adult and hidden-title word lists are edited without an app update, filled with today's words; plus Exceptions (Naruto, One Piece…) that a child may see from hidden categories, through a per-item `allowed` flag | Built 2026-10-20 (Phases 1–6), checked on the TV and phone emulators |
| 2026-10-20 | [Kids' filter](./20261020_kids-filter-plan.md) | A child profile sees only kids' categories (built-in list in many languages, no Animation, no Disney+) and no adult animation, horror or adult channels inside them | Built 2026-10-20 (Phase 1) |
| 2026-10-19 | [Kids' Home](./20261019_kids-home-plan.md) | A child profile's TV Home shows only big tiles of what a parent added (Continue watching, favourite channels, films, shows); no browsing, details pages lose their catalogue exits; the parent fills the favourites from Settings | Built 2026-10-19 (Phases 1–4, TV), checked on the TV emulator except a child's Live TV preview; phone not planned |
| 2026-10-19 | [Household UX](./20261019_tv-household-ux-plan.md) | A walk of every TV and phone screen for young children, a teenager and a parent: Child profile switch (adult categories hidden, filterable sources only, never Expert), filters reach Recent and Home, PIN nudge, kids' and teen polish | Built: Phases 1–8 (TV and phone); checked on the emulators except the slow-channel timeout, phone Add profile and phone no-guide; real devices not checked |
| 2026-10-19 | [Phone Home tab](./20261019_phone-home-tab-plan.md) | The phone gets a Home with TV Home's rows, first on the bottom bar in Search's place; Search stays in every tab header | Phase 1 built and checked on the phone emulator; phone check left; long-press menus not planned yet |
| 2026-10-17 | [Xtream stream release](./20261017_xtream-stream-release-plan.md) | A closed or idle stream never keeps an Xtream connection slot or reopens one by itself: no auto-retry after a stop, retries wait for Play, stop closes the socket, the session can't reopen a stopped stream, a paused film frees its slot after 5 min | Built 2026-10-17 (Phases 1–5) and on the Shields and Bravia; wake-lock follow-up done; left: a stalled panel-a stream quit on a Shield (Phase 3 on HTTP/1.1), the Xperia and phone |
| 2026-10-16 | [Design system](./20261016_design-system-plan.md) | Every look in both apps (text, buttons, fields, rows, chips, dialogs, borders, dock, rail) derived from the colour theme and Look and Feel through one token layer and one style class per element kind; a CI gate stops raw values | Built 2026-10-16: every file at 0 in the gate; full per-screen walk in every theme and Look and Feel left |
| 2026-10-16 | [One button look](./20261016_one-button-look-plan.md) | Every button in both apps looks the same, defined once per app (`ButtonStyle`, `TvButtonStyle`, `PhoneButtonStyle`): three kinds (main, other, danger), the Display style's shape, labels centred | Built 2026-10-16; screens beyond the profile page not walked; the TV look is not settled |
| 2026-10-12 | [Multi-source: no current source](./20261012_multi-source-search-history-plan.md) | No current source: each profile has its own sources (opt-in), and browsing, search, Recent, Continue Watching, favourites and the guide search are merged across them; every item plays from its own source | In progress: Phases 1–7 done 2026-10-12; Phase 8 (device walks, docs) not started |
| 2026-09-14 | [Codebase robustness](./20260914_codebase-robustness-plan.md) | Pay down technical debt found in a whole-codebase review | Phase 4 done 2026-10-12 (one accessibility node per channel row); Phase 5 in progress: more Compose UI tests (Robolectric, on the JVM); Phases 2–3 (single-return sweeps) dropped 2026-10-03; SecretStore part of Phase 6 deferred |
| 2026-08-28 | [Secret store migration](./20260828_secret-store-migration-plan.md) | Replace deprecated `EncryptedSharedPreferences` with an owned Keystore-backed `SecretStore` | All five phases; not started, deferred on purpose (the crash loop it cites is already handled) |
