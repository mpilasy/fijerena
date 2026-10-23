# Parental controls

**Status:** Built 2026-10-20 (Phases 1–6), checked on the TV and phone emulators.

Follows [Kids' filter](./20261020_kids-filter-plan.md). Replaces the allowed-titles plan of the same
day, which is now the Exceptions part of this plan.

## Goal

The words that decide what a child profile sees are in the app's code today
(`provider/KidsCategories.kt`, `provider/AdultCategories.kt`). A new source with other naming
needs an app update. This plan moves them to a **Parental controls** page in Settings, where the
admin (Expert) edits them without an update. The page starts with the words the app has now. On the
same page, the admin keeps a list of **exceptions**: titles a child may see even when the filter
hides them (Naruto, One Piece…).

The page is separate from the content filters a profile has today (Settings › Profiles › <profile>
› Content filters). Those stay as they are and still apply on top.

## Today

- Built-in lists, in code:
  - `KidsCategories.pattern`: kids' category words; `animationPattern`: Animation words, for film
    categories only.
  - `AdultCategories.pattern`: adult category words; `titlePattern`: titles hidden from a child
    (adult labels, adult brands, adult animation and horror).
- `CategoryFilters.shouldShowCategory` applies them for a child profile (`hideAdult`).
- The result is stored as the `excluded` flag on each category row in `xtream_v2.db`, for the
  profile in use on this device (`XtreamCategoryExclusionSync`). It is computed again on a profile
  switch, a filter or Child switch change, after a catalogue sync, and when
  `KidsCategories.VERSION` changes.
- The app hides whole categories, never single titles. 11 queries hide the items of excluded
  categories with `categoryId NOT IN (SELECT categoryId … excluded = 1)`:
  - `XtreamStreamDao`: `getStreamsByCategory`, `getAllStreams`, `getByTmdbId`,
    `getSiblingCompletedStreamIds`, `searchByFts`, `countExcludedByFts`;
  - `XtreamSeriesDao`: `getSeriesByCategory`, `getAllSeries`, `getByTmdbId`, `searchByFts`,
    `countExcludedByFts`.
- Code that filters lists in memory by hidden category id: `MediaRepository`
  (`getContinueWatchingItems`, `withoutHiddenCategories`), `XtreamContentManager` first loads,
  `ProfileFavorites.visibility` (the parent's Favourites pane), `GuideScope` (live only).
- A catalogue sync writes stream and series rows with `REPLACE`.

## Design

### The lists

Five lists, the same for every child profile in the household:

| List | What it does | Starts with |
|---|---|---|
| Kids' categories | A category shows to a child when its name has one of these words | kids, children, family, enfants, kinder, niños, bambini, dzieci, barn*, børn*, çocuk*, παιδικ*, أطفال, cartoon(s), disney, pixar… (today's `KidsCategories`) |
| Kids' film categories | Also shows, for film categories only | animation, animación, animação, animazione… |
| Adult categories | A category with one of these never shows to a child | xxx*, adult*, porn*, erotic*, nsfw, playboy, sex, sexy, 18+, +18 |
| Hidden titles | A title with one of these never shows to a child | adult labels, brands (brazzers, dorcel…), South Park, Family Guy, Rick and Morty, Adult Swim… |
| Exceptions | A title with one of these shows to a child, also from a category the lists hide (films and series) | Pokémon, Doraemon, One Piece, Naruto, Boruto, Dragon Ball, Detective Conan, Sailor Moon, Yu-Gi-Oh |

**Entries and matching:** an entry is a word or a short phrase. It matches whole words in any case.
A `*` at the end matches any ending (`barn*` matches "barnefilmer"). `+` counts as part of a word,
so `disney` matches "|FR| DISNEY" but not "DISNEY+ SERIES", and `18+` matches "18+ Movies". This
replaces today's regular expressions with something an admin can read. Unit tests check that the
starting lists give the same result as today's patterns on the 3,696 category names and the kids'
titles of the three catalogues (kept outside the repository).

**Order of the rules, for a child profile:**
1. Adult categories and Hidden titles always win.
2. Then a category shows if it is a kids' category (or a kids' film category for films).
3. Then a title shows if it matches an Exception, even when its category is hidden.
4. The profile's own content filters still apply on top of all this.

**Storage and sync:** each list is one shared synced setting in `AppSettings` (like the TMDB key:
`SyncKind.SETTING`, profile `shared`), holding a JSON list of entries. The admin's edits sync to
every device. If two devices edit the same list at the same time, the later edit wins for the whole
list (risk below).

**Starting values and app updates:** at first start after the update, each list is filled with
today's words, once. After that the lists belong to the admin: an app update does not change them.
Each list has **Reset to the app's list** (P-D2).

**When a list changes** (here or by sync), the app computes the flags again for the profile in use,
if it is a child profile. `KidsCategories.VERSION` is no longer needed.

### Exceptions in the database

The app hides whole categories, so an exception needs a per-item flag:
- **Migration** (xtream_v2.db, next version): add `allowed INTEGER NOT NULL DEFAULT 0` to
  `xtream_streams` and `xtream_series`, with an index on `(providerId, allowed)`. The old unused
  `excluded` item columns are not reused: rows from before v24 can hold 1 there, and renaming a
  column needs SQLite 3.25. Update `docs/DATABASE_SCHEMA.md` in the same commit.
- **Recompute** (`XtreamCategoryExclusionSync`), with the category flags:
  1. `UPDATE … SET allowed = 0 WHERE providerId = ? AND allowed = 1`;
  2. for each exception, find the matching items with the full-text index (the Search tables),
     keep those whose category is hidden but not adult and whose title is not in Hidden titles, and
     set `allowed = 1`.
  This writes a few hundred rows at most.
- **Queries:** each of the 11 item queries becomes
  `(categoryId NOT IN (…excluded = 1) OR allowed = 1)`. `countExcludedByFts` leaves allowed items
  out of the count. `XtreamCategoryDao.getCategories` also returns a hidden category that has an
  allowed item (phone browsing). Timings are measured on a copy of the TV emulator's database (panel-a catalogue) before and
  after, as in the fast-switch plan.
- **In memory:** the filters by hidden category id keep a title when its category is visible or it
  matches an exception. Hidden titles still apply after it.

### Browsing

- **TV:** a child does not browse. Exceptions show in Search, on Home and in the parent's
  Favourites pane.
- **Phone:** a child browses categories. A hidden category with allowed items shows in the list
  with only those items.

### The page

- **Where:** Settings › **Parental controls**, for the admin only (Expert), behind the Settings PIN
  like Profiles. TV and phone.
- **Rows:** one per list, with its number of entries. A row opens the list.
- **A list:** a text field to add an entry; one row per entry; OK on an entry removes it, with
  Undo. Each entry shows how many categories (or titles) it matches on the sources of the child
  profiles, so a typo or a too-broad word ("the") shows at once. At the bottom: Reset to the app's
  list (asks first).
- **A check view** (P-D3): for one source, the categories a child sees and the ones it does not, so
  the admin can find what the words miss on a new source.
- Strings in en, fr and mg. The Child profile description points to the page.

## Decisions (2026-10-20)

- **P-D1 Exceptions at the start:** Pokémon, Doraemon, One Piece, Naruto, Boruto, Dragon Ball,
  Detective Conan, Sailor Moon, Yu-Gi-Oh. The admin can remove any of them.
- **P-D2 App updates:** each list is filled once with the app's words; after that only the admin
  changes it. Reset to the app's list brings in the newest words and removes the admin's changes.
- **P-D3 Check view:** built now, in Phase 5.
- **P-D4 Exceptions for films and series only,** not channels.
- **P-D5 Accents are ignored** in all five lists: "pokemon" matches "Pokémon", "ninos" matches
  "Niños".

## Phases

| Phase | What | Status |
|---|---|---|
| 1 | List storage (shared synced settings, filled once), the entry matcher, the rule order; unit tests against today's patterns on the three catalogues | Done 2026-10-20 |
| 2 | Recompute on a list change (local and synced); remove `KidsCategories.VERSION` | Done 2026-10-20 (same commit as Phase 1) |
| 3 | Migration (`allowed` columns), recompute of `allowed`; migration and DAO tests (instrumented, on CI, not on the user's devices) | Done 2026-10-20: xtream_v2.db v25; the recompute sets `allowed` for a child profile; JVM tests (recompute, migration); the instrumented migration test is compiled, not run |
| 4 | The 11 item queries, the category query, the in-memory filters, the Favourites pane; query timings on a copy of the TV emulator database | Done 2026-10-20: 11 item queries, `getCategories`, Recent, Favourites, Continue watching, first loads, phone category list, parent's Favourites pane; one Robolectric test per query; timings below |
| 5 | Parental controls page on TV and phone (lists, counts, Reset, check view), strings | Done 2026-10-20: TV 0b1c3818, phone 6b29f2ac |
| 6 | Emulator walks (TV and phone, Kid: edit a list, add "naruto"), focus walk, docs (FEATURES, NAVIGATION_GUIDE, RELEASE_NOTES, DATABASE_SCHEMA) | Done 2026-10-20 (TV and phone) |

Phases 1, 2 and 5 work without 3 and 4: the lists become editable first, and the exceptions follow.

## Built

**Phases 1–2 (2026-10-20):** `provider/ParentalRules.kt` (`ParentalList` with the starting words,
`ParentalRules`, `EntryMatcher`) replaces `KidsCategories` and `AdultCategories`.
`CategoryFilters.child` (the rules, or null) replaces `hideAdult`. `AppSettings.parentalList`,
`setParentalList`, `parentalRules`, `seedParentalListsOnce`; the keys are in
`SYNCED_SETTING_KEYS` and `PARENTAL_KEYS` (shared). `ProviderRepository.setParentalList` (store,
send, apply), `parentalListsChanged` (also called by `SyncApplier` for a received list) and
`seedParentalListsOnce` (at startup, in place of the version recompute).

Compared with the old patterns on the three catalogues (a test run once, not kept): the 3,695
category names give the same result. Of 736,223 titles, 23 differ: now hidden are "Bob’s Burgers"
with a curly apostrophe, "Jujutsu  Kaisen" with two spaces, xXx2, xxxHOLiC, XXXTentacion and
"XXXIX" ("xxx*" has an open end); now shown are "Sky Sport+ 18", "BTN+ 18" and "DISNEY+ 18"
(channel numbers, not adult labels); and "Si pudiera, te daría una patada" is newly hidden because
accents are ignored and "daria" is in Hidden titles.

**Phases 3–4 (2026-10-20):** migration 24→25 (`allowed` on `xtream_streams` and `xtream_series`,
index `(providerId, allowed)`). `XtreamCategoryExclusionSync.recompute` takes the stream and series
DAOs. It clears `allowed`. Then, for a child profile, it finds the films and series of each
exception with `matchNames` and `ftsQuery`, and keeps those that `ParentalRules.allowsTitle`
accepts in a category that `exceptionsReach` accepts: hidden by the lists, not adult, and shown by
the profile's own filters (rule 4). It sets `allowed` on 900 ids at a time. The item queries read
`(categoryId NOT IN (…) OR allowed = 1)`. For films the form is `OR rowid IN (… allowed …)`: SQLite
then still rejects hidden rows from the `(providerId, type, categoryId)` index and does not read
them. `getCategories` also returns an excluded film or series category with an allowed item. In
memory, `MediaRepository` (Recent, Favourites, Continue watching, `getFilteredCategories`) reads the
allowed ids and categories from the database (`XtreamCategoryDao.getAllowedItemIds`,
`getAllowedCategoryIds`). The first loads of `XtreamContentManager` and the parent's Favourites pane
(`ProfileFavorites.visibility`, for a profile that is not in use) use the rules and
`exceptionsReach`.

Query timings: host `sqlite3` 3.50.6, best of 5 after one warm-up run, on a copy of the TV emulator
database (panel-a catalogue). Provider 12 has 182,461 films and 48,138 series; its filters exclude
425 of 429 film categories and 349 of 354 series categories. `allowed` is set by hand on 394 films
and 155 series that match the starting exceptions in excluded categories. The migration takes
0.25 s on the 427 MB file.

| Query | Before (ms) | After (ms) | Rows before / after |
|---|---|---|---|
| All films (`getAllStreams`) | 20 | 23 | 1,618 / 2,012 |
| All series (`getAllSeries`) | 11 | 12 | 357 / 512 |
| Search films "the*" (`searchByFts`) | 33 | 25 | 200 / 200 |
| Search series "the*" (`searchByFts`) | 10 | 9 | 73 / 76 |
| Hidden count, films "the*" (`countExcludedByFts`) | 34 | 35 | 1 / 1 |
| Hidden count, series "the*" (`countExcludedByFts`) | 10 | 10 | 1 / 1 |
| Film categories (`getCategories`) | 0 | 0 | 4 / 61 |
| Series categories (`getCategories`) | 0 | 0 | 5 / 56 |

With `OR allowed = 1` in place of the `rowid` form, all films took 41 ms (SQLite read every film
row), and the films count went through the `(providerId, allowed)` index (45 ms).

**Phase 6 (2026-10-20, TV emulator):** the migration to v25 ran on install with no crash. As
atr, `parental-controls.txt` passes (39 steps): the five lists with their counts ("pokemon"
matches 232, "naruto" 70), remove and Undo, Reset at the end, Check a source on panel-a. As Kid,
"naruto" finds Naruto films in "VOD - LATINO [ES]", a hidden category; the database holds a few
hundred `allowed` rows per source (panel-a 229 films, 121 series; panel-d basic 210 films, 61
series). `settings.txt` (76 steps), `settings-guide.txt` and `kids-home.txt` pass after their
volatile lines (build stamp, "Last synced", programme count) became stable prefixes and the Kid
page got its Favourites row. Phone emulator (atr): Settings › Parental controls lists the five lists ("41 words"…) and Check
a source; Exceptions shows the same counts as the TV ("pokemon" 232, "naruto" 70).

## Risks

- **An admin edits a list badly** (removes "kids", adds "the"): a child sees too little or too much.
  The match counts and Reset limit this. The Adult categories and Hidden titles lists are
  editable too, so an admin can weaken them; they are admin-only and behind the PIN.
- **Two devices edit a list at once:** the later whole list wins, so one edit is lost. Rare in a
  household. A finer sync (one record per entry) is possible later.
- **A missed query** shows a whole hidden category, or does not show an allowed title. Phase 4 has
  one test per query.
- **A catalogue sync resets `allowed`:** the recompute after a sync must run before the lists are
  read again; `recomputeExclusions()` already runs there.
