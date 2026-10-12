# Multi-source search and history

**Status:** Draft, 2026-10-12. Summary only. Open questions Q1–Q7 still need answers; there are no
phases yet. Nothing has been built.

## Today

One source is active per device. Search, Recent and Continue Watching only cover that source.
`watch_state` and `favorite_state` (`xtream_v2.db`) are keyed per source, and so are Recent
Categories (per-source SharedPreferences). See [FEATURES.md](../FEATURES.md) → Search, Recent and
Favourites, and [DATABASE_SCHEMA.md](../DATABASE_SCHEMA.md).

## Goal

Sources stay separate, but search and play history are shared across them. A search returns
results from every source the profile has enabled. Playing a result just works, whichever source it
came from. History is merged.

## Summary (working reading, to be confirmed)

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

## Open questions

| # | Question | Notes |
|---|---|---|
| Q1 | Same title on several sources: one result per source, or merge them into one row and pick a source? | If merged, which one plays: the active source first, or the source last watched? |
| Q2 | Progress across sources: a film half-watched on source A, is it half-watched on source B? | Cross-variant TMDB matching already does this within a source (movies by `tmdbId`, episodes by series TMDB id + season/episode). Extending it across sources is a bigger job. |
| Q3 | Favourites: per source, or merged like history? | |
| Q4 | Live channels in merged history: on TV, zapping with Up/Down through Recent would cross sources, so each zap might switch logins. OK, or should Live TV Recent stay per source? | |
| Q5 | Search the guide (EPG): does it also span all enabled sources, or only regular search? | Guide sources are per source today. |
| Q6 | Jellyfin and M3U: include them from the start, or Xtream only first? | Jellyfin search goes to its server, so a merged search makes several network calls. |
| Q7 | Live sync: should the new per-profile "enabled sources" setting sync to other devices? | Each profile's last picked source already syncs. |

## Phases

To be written once Q1–Q7 are answered.
