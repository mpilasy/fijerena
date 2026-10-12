# Multi-source search and history

**Status:** Draft, 2026-10-12. Questions Q1–Q7 answered 2026-10-12; phases not written yet.
Nothing has been built.

## Today

One source is active per device. Search, Recent and Continue Watching only cover that source.
`watch_state` and `favorite_state` (`xtream_v2.db`) are keyed per source, and so are Recent
Categories (per-source SharedPreferences). See [FEATURES.md](../FEATURES.md) → Search, Recent and
Favourites, and [DATABASE_SCHEMA.md](../DATABASE_SCHEMA.md).

## Goal

Sources stay separate, but search and play history are shared across them. A search returns
results from every source the profile has enabled. Playing a result just works, whichever source it
came from. History is merged.

## Summary

- **Browsing stays per source.** Live TV, Movies, TV Shows and the categories still show only the
  source picked in the header, and the source picker stays.
- **New per-profile setting: which sources join in.** Each profile turns sources on or off for
  search and history. A new profile defaults to all on. This switch is separate from the active
  source.
- **Search covers every enabled source.** Results are still grouped by type (Live, Movies, Shows),
  and each item shows a source badge.
- **Playing a result just works.** It plays from the source it came from without switching the
  active source. Back returns to Search. Details, episodes and Live TV open with that source's data.
- **History is merged.** Recent and Continue Watching (TV Home, phone tab Recents) list items from
  every enabled source, newest first, and each item plays from its own source.

## Decisions (2026-10-12)

| # | Question | Decision |
|---|---|---|
| Q1 | The same title on several sources | **One row per source**, each with a source badge. No merging. |
| Q2 | Progress across sources | **Per source.** Progress stays with the copy that was watched; merged history still lists it. Cross-variant TMDB matching stays within a source. |
| Q3 | Favourites | **Stay per source**, as today. |
| Q4 | Live channels in merged history | **Merged list, zap within source.** Recent shows channels from every enabled source; Up/Down zaps only through the playing channel's own source. |
| Q5 | Search the guide (EPG) | **All enabled sources.** Each source's guide sources are searched; an airing opens its channel on its own source. |
| Q6 | Source types | **Xtream only first** (searched in the local cached catalogue). Jellyfin and M3U later. |
| Q7 | Live sync of "enabled sources" | **Synced**, like the profile's content filters. |

## Phases

To be written: needs a read of search, watch history, the player and the guide index first.
