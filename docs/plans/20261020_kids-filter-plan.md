# Kids' filter

**Status:** Built 2026-10-20 (Phase 1) and checked on the TV emulator.

## Goal

A child profile sees only what is for children, on every Xtream source and in every section
(films, shows, channels). This limits browsing on the phone, Search, Recent, Continue watching,
favourites, and the search in the parent's Favourites pane.

## Source of the lists

The session `catalogue analysis` analysed three catalogues (panel-a, panel-d basic, panel-d
premium; dumps of 2026-10-14/15) (kept outside the repository):
- `refined-catalogues-20261016/child-friendly-categories.csv`: 237 categories rated kids (204),
  family (7) or mixed (26).
- Kids' categories are not safe alone. They also hold adult animation and horror (South Park,
  Sausage Party, Family Guy, Rick and Morty, "Winnie-the-Pooh: Blood and Honey"), and "US| KIDS"
  holds "ADULT SWIM HD".
- Porn channels are in a normal country category ("CG: BRAZZERS TV" in "CG| MONTENEGRO").
- The Xtream `is_adult` field marks only the panel-a "FOR ADULTS" items, which the word "adult"
  already hides.

## Decisions (2026-10-20)

- **F-D1 Built-in:** the kids' list is built in, like the adult list. It is on for every child
  profile and is not stored in the profile's rules, so an app update improves it. The parent's own
  rules still apply on top.
- **F-D2 Animation films only** (changed 2026-10-20): "Animation", "Animación", "Animação" and
  "Animazione" categories pass for films, not for series. Film categories hold mostly children's
  films (Toy Story, Paddington) and add about 880 titles; series categories hold more adult series
  (Cyberpunk: Edgerunners, Evangelion, King of the Hill). "Anime" and "Manga" categories stay
  hidden: they are mostly for teenagers and adults, and the title list catches about 3% of them.
  Naruto, One Piece, Pokémon and Dragon Ball still show where a server puts them in a kids'
  category (for example 12 One Piece films and 10 series of 168 and 82).
- **F-D3 All sections:** films, shows and channels.

## Design

A child profile (`CategoryFilters.hideAdult`) sees a category only when:
1. its name matches `KidsCategories`: kids, children, family, enfants, jeunesse, kinder, niños,
   infantil, bambini, crianças, dzieci, bajki, barn, børn, çocuk, copii, děti, deciji, crtani,
   fëmijë, παιδικά, أطفال, cartoon(s), Disney (not "Disney+"), Pixar, DreamWorks, Ghibli,
   Nickelodeon; or, for a film category only, Animation, Animación, Animação, Animazione; and
2. its name does not match `AdultCategories`.

Inside the categories it sees, a title is hidden when `AdultCategories.titleMatches` matches it.
This list now also has "not suitable for children", "rated r", adult channel brands (brazzers,
dorcel, hustler, penthouse, vivid, redlight, babestation, "private tv") and adult animation and
horror franchises (South Park, Family Guy, Rick and Morty, Adult Swim, Futurama, Bob's Burgers and
others).

Tested against the three catalogues: the kids' pattern matches all 211 kids and family categories.
It lets in 5 "mixed" live categories ("24/7 CARTOON", "24/7 KIDS/FAMILY", "MUZYKA | DZIECI") and
"NL| 24/7 DISNEY". The title list hides 222 of 30,432 items in kids' categories.

The app stores a hidden flag for each category of the profile in use, and computes it again only
on a profile switch, a filter or Child switch change, or a catalogue sync. So a change of the
built-in lists does not reach a child profile in use by itself. `KidsCategories.VERSION` fixes
this: when it differs from the version stored on the device, the app recomputes the flags once at
startup (`ProviderRepository.recomputeChildFiltersOnce`). Increase it when either list changes.

**Emulator check (TV, Kid, 2026-10-20):** before the version recompute, "news" still listed films
from "VOD - GERMANY" and "VOD - PRIME+" (old flags). After it: Narnia left Kid's Home (its category
is not a kids' one; the favourite stays stored), Toy Story stayed; "news" and "family guy" find
nothing; "frozen" lists 23 films, all in kids' categories ("VOD - KIDS [EN]", "FILMOVI CRTANI",
"أفلام اطفال").

## Phases

| Phase | What | Status |
|---|---|---|
| 1 | `KidsCategories`, title list, one recompute after an update, unit tests, child profile text, docs | Done 2026-10-20 |
| 2 | F-D2 changed: Animation for films (`KidsCategories.VERSION` 2) | Done 2026-10-20 |

**Emulator check, Phase 2 (TV, Kid, 2026-10-20):** after the update, `child_filters_version` is 2
and the stored flags changed with no other step. On Kid's source (panel-d basic) "VOD -
ANIMAZIONE [IT]" shows and "VOD - ANIME [FR]" and "VOD - GERMANY ANIME" stay hidden; on panel-a the
Animation film categories show and every Animation series category stays hidden. "giocattoli"
finds "Toy Story" in "VOD - ANIMAZIONE [IT]". No database migration: only the values of the
existing `excluded` column change.

## Not done

- `is_adult`: the app does not read it. It adds almost nothing on these servers. To add it: parse
  it, store it (Room migration), filter on it.
- An age-rating cap from TMDB (`/movie/{id}/release_dates`, `/tv/{id}/content_ratings`). About
  40% of items have no valid TMDB id.
- A source with no kids' categories (for example a Jellyfin bridge with genre names) shows nothing
  to a child profile.
