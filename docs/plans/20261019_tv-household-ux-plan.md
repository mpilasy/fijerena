# Household UX (TV, then phone): kids, teens, parents

**Status:** Built (2026-10-19), Phases 1–8. Phases 1–5 built; Phases 1–4 checked on the TV emulator; Phase 5 (a slow channel's timeout) and the phone not checked.

## Goal

A walk of every TV screen on the TV emulator (2026-10-19) with three viewers in mind: young
children who only watch from a handful of favourites, a teenager who searches and pokes around,
and a parent who sets up parental controls. Make the kids' path one press, keep the teenager
inside the parent's limits, and make the parent's setup short.

## What the walk found

Profiles `atr` (Expert, no PIN), `Kid` (Expert, PIN) and a new profile `Mia` made for the walk
(not Expert, no PIN), sources panel-a, panel-d, jellyxtream (Xtream) and iptv (Remote M3U).

**Parental controls (the parent, and what the kids get)**

- F1 **A new profile is unfiltered.** Add profile copies the parent's filters — usually none — so
  `Mia` found 183 adult channels by searching "adult". Nothing on Add profile says the new profile
  is a child's.
- F2 **A new profile gets every source of the parent's, including ones filters can't touch.**
  Content filters only work on Xtream sources; `Mia` got the Remote M3U source, unfiltered, with
  nothing saying so.
- F3 **Filters are typed rules.** Content filters are comma-separated name rules typed with the
  remote, per source, with no list of categories to look at. Fine as an extra; as the only
  protection it's a wall.
- F4 **Recent, Favourites and Continue watching ignore the filters.** A film watched before a
  filter was added stays on Home and in Recent and still plays (`Kid` has "Very Bad Trip 2" and
  "From" on Home). Only the catalogue lists and search hide filtered categories.
- F5 **Search tells a teenager what's hidden:** "323 results · 2 hidden".
- F6 **Nothing asks the parent for a PIN.** With no Expert PIN, 7 taps on the version make anyone
  Expert, and Settings is open. A household with a child profile and no Expert PIN is unprotected
  without knowing it.
- F7 **After Add profile focus stays on Add profile**, though the next thing to do is the new
  profile's page (sources, filters).
- Picking a profile never asks a PIN (decided 2026-10-17, re-confirmed 2026-10-19): a child can
  pick the parent's profile. Not changed.

**Kids**

- K1 **A new profile's Home is an empty dark screen** with focus on the rail: no sentence, no way in.
- K2 **A section opens on an empty Recent with focus on Refresh** ("No films in this category",
  Refresh does nothing for Recent).

**Teenager**

- T1 **The rail's Search follows the last section:** from Live TV it searches only channels
  ("Search channels"); from Home everything. The rail is the global way in, so it should search
  everything.
- T2 **TV Guide on a source without a guide** shows an error screen with Retry focused, which can
  only fail again.

**Everyone**

- E1 **The rail marks Home while browsing an M3U source** ("Browse iptv").
- E2 **Live TV preview stuck on "Tuning"** after the player went Idle 8 s in, with no error and no
  retry (iptv, 1KZN TV, first OK). A second OK played it.
- E3 Add profile dialog: Down from the colours lands on Cancel, Save is to its right.

## Design

**Child profile** (user's choice, 2026-10-19). One switch, on Add profile and on a profile's page
(Admin group, Expert only, as Content filters):

- Every Xtream source hides adult categories for that profile, on top of its own rules: a
  built-in word list (`AdultCategories`: xxx, adult, adults, adulte, adultos, 18+, +18, porn…,
  erotic…, nsfw, playboy, sex, sexy), whole words, case-insensitive ("Sussex" stays). Not stored
  in the filters, applied when they're read for that profile, so the custom rules editor is
  unchanged and turning the switch off restores exactly what was there.
- A child profile is never Expert: turning Child on turns Expert off (and the last Expert can't
  be made a child).
- Add profile with Child on gives the new profile only the parent's Xtream sources (the ones
  filters work on); the Sources pane says "No content filters" under a source they can't reach.
- Synced per profile like Expert mode.

**Filters reach the lists (F4):** Recent, Favourites, Continue watching and Home's rows drop
titles and channels whose category is hidden for the profile in use, on every profile (a filter
means hidden everywhere). The entries are kept; removing the filter brings them back.

**Hidden counts (F5):** shown only to Expert profiles.

**PIN nudge (F6):** while a child profile exists and no Expert profile has a PIN, Settings opens
with a row at the top: "Set a PIN so children can't change settings" → the profile in use's PIN.

**After Add profile (F7):** TV opens the new profile's page.

**Kids and teen polish:** K1 an empty Home says what fills it (focus stays on the rail, where the
sections are); K2 an empty Recent or Favourites says what fills it, and its button, Browse
categories, takes focus to the categories (no Refresh); T1 the rail's Search keeps its section
(designed so in the rail plan), but a section's search that finds nothing offers **Search
everything** with the same words; T2 no guide is an empty state with Back, not an error with
Retry; E3 Save is reached by Down. E1 dropped: Browse entries are never lit by design (rail plan).

## Phone walk (2026-10-19)

The same walk on the phone emulator (Pixel_10) with the profiles synced from the TV: atr (Expert),
Kid and Teen (child profiles), Leroy (grown-up, not Expert). Phases 1–3 carried over (Child
profile switch on Add profile and the page, filters in the lists, PIN reminder): checked there.

- P1 **Home's cards show the raw names** — "ES - From (2022)", "4K-AR - The Chronicles of Narnia…",
  "CA - CP24 HD ◉" — where every list shows the clean title with its badges (`BadgedTitle`).
- P2 **Adult films in ordinary categories** reach a child profile: "The Intern - A Summer of Lust
  (Adult content)" sits in "VOD - ALBANIA". Category rules can't see it (TV too).
- P3 **After Add profile nothing opens**: the new profile's page is the next step (as TV, F7).
- P4 **Categories are only a sideways row of chips**: 496 film and 1,220 channel categories, one
  swipe at a time. Nothing lists them.
- P5 **No Search on the section tabs** (regression, 2026-10-19): Live TV, Movies and TV Shows lost
  their Search icon when Search was the app's one tab; Home took that tab's place today, so a
  teenager in Movies has to go to Home to search.
- P6 **Empty Recent and Favourites** say "No films in this category" (TV K2).
- P7 **TV Guide without a guide** offers Retry (TV T2).
- Not changed: the phone opens on the last profile used, with no picker (a personal device; the
  picker never asks a PIN anyway); the player has no on-screen Back (the system Back closes it).

**Design:** P1 Home's cards through `BadgedTitle`/`parseDisplayTitle` like the lists. P2 a child
profile also hides films, shows and channels whose own name says adult — a narrower title list
than the categories' (`AdultCategories.titleMatches`: xxx, porn…, erotic…, erotik…, nsfw, "adult
content", "adults only", 18+, +18; not "Adults in the Room" or "Adulthood"), in the lists and in
Search, TV and phone. P3 the phone opens the new profile's page. P4 a first chip, **All
categories**, opens a sheet listing every category of the section with a filter field. P5 each
section tab's header has Search again, opening Search over everything with that section's chip
chosen. P6, P7 as on TV.

## Progress

| Phase | What | Status |
|---|---|---|
| 1 | Child profile: setting, built-in adult rules, never Expert, Add profile switch, profile page switch (TV, phone), sources note, sync, docs; TV opens the new profile's page (F7) and Down reaches Save (E3) | Built; checked on the TV emulator 2026-10-19 |
| 2 | Filters reach Recent, Favourites, Continue watching, Home; hidden counts Expert only | Built; checked on the TV emulator 2026-10-19 |
| 3 | PIN nudge (TV and phone) | Built; checked on the TV emulator 2026-10-19 |
| 4 | Kids and teen polish: K1, K2, T1, T2 (E1 dropped, by design) | Built; checked on the TV emulator 2026-10-19 |
| 5 | E2 live preview stuck on Tuning: the preview's dead-stream watchdog stopped the channel at 8 s (first byte at 5.4 s) and nothing changed the overlay; now 15 s, and a stopped channel says it didn't start | Built; emulator check left |
| 6 | Phone: P1 Home's clean titles, P3 open the new profile, P5 Search on the section tabs, P6, P7 | Built; P1, P5, P6 checked on the phone emulator; P3, P7 not walked |
| 7 | Child profile hides adult titles (P2), TV and phone (`XtreamRepository`, `MediaRepository`) | Built; checked on the phone emulator (Kid's "adult" search: 36 films to 34) |
| 8 | Phone: All categories list (P4) — a dialog with a filter field (no sheet component exists) | Built; checked on the phone emulator (Teen, Movies, "kids") |

## Emulator profiles (2026-10-19)

The TV emulator's profiles were set up for these checks at the user's request: **atr** the only
Expert (no PIN), **Kid** a child profile (young children; keeps its old PIN), **Teen** (was the
walk's `Mia`) a child profile, **Leroy** a grown-up who isn't Expert. The Settings walk was
re-recorded on them; the source-filter Search walk moved from Kid to atr.
