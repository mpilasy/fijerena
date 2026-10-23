# Phone Home tab

**Status:** Built 2026-10-19 (Phase 1); checked on the phone emulator (Pixel_10, jellyxtream + panel-a); not yet on the phone.

## Goal

The phone gets a Home, as the TV has, first on the bottom bar. The bar stays at five slots: Home
takes Search's place (option 2 of 2026-10-19, the user's choice; Home first). Search stays one tap
away in every tab's header. Reverses "Redesign: no Home on the phone" of
the phone home overhaul (2026-10-07).

## Design (defaults, not asked)

- **Bar:** Home, then the sections the profile's sources have (Live TV, Movies, TV Shows), then
  the profile slot. No Search tab (`Screen.SearchTab` becomes `Screen.HomeTab`).
- **Home's header:** "Home" over the sources' one line, then Search the guide (when the index is
  ready) and Search over everything ("All").
- **Rows:** TV Home's data and order, phone-sized (every card 16:9 at one width, as on TV), shared loaders (`MergedLists`,
  `mergeLiveRow`, `favoriteChannelsRow`): Continue watching, Channels, Favourite channels,
  Favourite channel categories, Favourite movies, Favourite movie categories, Favourite shows,
  Favourite show categories. Empty rows hidden; with every row empty, one line saying what fills
  Home. Re-read on each return to Home.
- **A card opens inside the Home tab** (Back returns to Home): Continue watching and favourites
  as the lists route them; a channel docked on Live TV, zapping through the list it came from
  (Recent or Favourites); a category its section's list on it.
- **Start tab:** the app opens on the last tab used (per profile), Home included; Home on first
  launch and when the last tab's section is gone.
- **Not in Phase 1:** long-press menus on Home's cards (remove, favourite) and pull to refresh.

## Progress

| Phase | What | Status |
|---|---|---|
| 1 | Home tab first on the bar, Search off it, Home screen with the rows, docs | Built; checked on the phone emulator |
