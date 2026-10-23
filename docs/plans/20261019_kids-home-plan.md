# Kids' Home (TV)

**Status:** Built 2026-10-19 (Phases 1–4, TV); checked on the TV emulator except a child's Live TV
preview (Kid has no favourite channel on a source fit for playback tests); phone not planned yet. Follows the Child profile switch of
[Household UX](./20261019_tv-household-ux-plan.md).

## Goal

A young child on a child profile turns the TV on, picks their face, and sees only what a parent put
there, in big tiles. Nothing on the screen leads to the IPTV catalogue. A parent fills that screen
from Settings without switching to the child's profile.

## Decisions (2026-10-19)

- **K-D1 Favourites only:** a child profile cannot browse. The rail shows Home and Settings
  (Settings keeps its PIN). No Live TV, Movies, TV Shows ~~or Search~~ (Search back, K-D7).
- **K-D2 Details pages kept:** OK on a tile opens the same screen as for an adult (film details,
  episode list, Live TV list). Those screens lose their exits to the catalogue (below).
- **K-D3 Admin fills the favourites:** Settings › Profiles › <child> › Favourites, from the Expert's
  Settings. The child cannot remove a favourite.
- **K-D4 TV first:** the phone keeps today's Home for a child profile; a later plan.
- Unchanged: the picker never asks a PIN; adults and Teen keep today's Home.
- **K-D5 Teen is not a child profile** (2026-10-19): the Child profile switch now means the walled
  garden, so the teenager, who searches and browses, has it off (adult categories visible).
- **K-D6 Continue watching kept as is** (2026-10-19): a child's Home shows whatever it played, not
  only its favourites. Kid's old TV-show history from testing was cleared on the TV emulator
  (50 rows, with sync tombstones).
- **K-D7 Search, no browsing** (2026-10-19): Search is back on a child's rail. It finds films and
  shows through the child's filters, each opening its page (no exits, as K-D2). No channels: a
  child's Live TV list is its favourites, so a channel found would have nowhere to open. No category
  results, Guide tab, hidden-matches count or Hold OK menu. A child who types can reach any title
  that isn't adult, so this suits an older child.

- **K-D8 Phone: the light version for now** (2026-10-20): the phone keeps its layout and
  browsing (the kids' filter limits it to kids' categories). A child profile's phone Home has only
  Continue watching and its favourite channels, films and shows: no Channels (recent) row and no
  favourite-category rows. Normal card size and the normal empty text, because the child adds its
  own favourites (the star and the list menus stay). The full version (the TV's closed garden) can
  come later on top: nothing of the light version is undone by it.

## Today, for a child profile

- Header: clock, the sources' sync line, Guide. Meaningless to a young child.
- Up to eight rows, three of them favourite *categories*, which open browsing lists.
- The rail's Live TV, Movies, TV Shows and Search reach the whole (filtered) catalogue.
- Film details: the category link and More like this lead elsewhere. Episode list: the category
  link. Live TV list: the categories pane.
- The long-press menu offers Remove from favourites.
- To favourite something for the child, the parent switches to the child's profile.

## Design

### Home (child profile)

- Header: the profile's avatar and name only.
- Rows, only those with items: Continue watching, Favourite channels, Favourite films, Favourite
  shows. Not the "Channels" (recent) row, nor the three favourite-category rows.
- Tiles one size up from today's cards (a token, not a raw value).
- No favourites at all: an empty state, "Ask a grown-up to add something to watch", no button.
- Back does nothing (already so).

### Closing the exits

A child profile's:
- rail: Home and Settings only;
- film details: no category link, no More like this;
- episode list: no category link;
- Live TV list: opened on Favourites, no categories pane; Left/Right zaps within favourites;
- long-press menus: no Remove from favourites, no Hide category;
- player: unchanged (it has no catalogue exit; check in Phase 2).

One `isChildProfile` read decides all of these, at the screen, not per row.

### Favourites pane (admin)

On a child profile's page in Settings (Expert only, after the PIN):
- **Favourites:** the child's favourites by type, each removable;
- **Add:** a search field over the child's sources, with the child's filters (adult categories and
  titles already hidden); OK adds to the child's favourites.

Writes go to `favorite_state` with the child's `profileId` through the existing DAO paths that
record sync tombstones, so other devices follow.

## Phases

| Phase | What | Status |
|---|---|---|
| 1 | Kids' Home layout on TV (header, rows, tile size, empty state) | Done 2026-10-19 |
| 2 | Close the exits (rail, details, episode list, Live TV list, menus) | Done 2026-10-19 |
| 3 | Favourites pane in Settings (list, remove, search and add for another profile) | Done 2026-10-19 |
| 4 | Focus walk `kids-home.txt` on the Kid profile; docs (FEATURES, NAVIGATION_GUIDE, RELEASE_NOTES); emulator check | Done 2026-10-19 |
| 6 | K-D8: the phone's light version (Home rows) | Done 2026-10-20; phone emulator: Kid's Home shows Continue watching and Favourite movies only |
| 5 | K-D7: Search on a child's rail, films and shows only | Done 2026-10-19 |

## Built (2026-10-19)

- Home: `ContentTypeSelectionScreen` reads `isChildProfile` once; rows scaled by
  `TvDimensions.kidsHomeScale` (1.3) through `LocalDensity`; card actions null (no menu, no hint).
- Rail: `railItems(sections, child)`. Exits: `MovieDetailsScreen`, `EpisodeSelectionScreen`
  (no related titles, category or star), `LiveTvSplitLayout(favoritesOnly)`, `StreamList(rowActions)`,
  `TvPlayerScreen` (no favourite).
- Favourites pane: `ProfileFavorites` (core:network) reads and writes another profile's
  `favorite_state` rows through the tombstone paths; `ProfilesViewModel.searchFavoritesFor` searches
  the profile's Xtream sources with `includeExcluded` and applies that profile's own filters
  (`ProfileFavorites.visibility`); TV `ProfileFavoritesPane`.

**Emulator check (TV, 2026-10-19):** Kid's Home (name header, big cards, rail Home + Settings,
Menu and Hold OK do nothing); Narnia's page (Details and Cast tabs only, no star, no category);
play and Back twice to Home; a show page (no star); atr → Kid → Favourites: removed one, searched
"toy" and added Toy Story, which Kid's Home then showed; "lust" left out the title labelled adult
content. Teen (not a child now) has the full rail. Focus walk `kids-home.txt` passes.

**Emulator check, K-D7 (TV, 2026-10-19):** Kid's rail Home, Search, Settings (walk passes);
Search's field "Search films and shows", no Catalogue/Guide tabs; "news" lists Movies then TV Shows,
no channels or categories, no hidden count; Hold OK on a result does nothing; OK opens the show page
(no star, no category; More holds Refresh info only), Back lands on the result; "lust" lists no
adult-labelled title. Search reaches TV-MA titles: no age cap (one was offered and not chosen).

**Not checked:** a child's Live TV preview (no favourite channel on a source fit for playback
tests). **Seen, not caused here:** on a show page with one season, Up from the episode list lost
focus (same on the build before this plan); fixed 2026-10-19 with an app-wide focus safety net
(CONTRIBUTING.md focus contract rule 8).

## Open questions

- Should a child profile's Continue watching include live channels (it does not today)?
- Phone: the full version later (K-D8)?
