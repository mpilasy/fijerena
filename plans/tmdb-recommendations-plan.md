# TMDB "More Like This" — Open Work (Phase 0.1 & Phase 2)

## Status
Phase 1 (Xtream TMDB recommendations & similar titles matching via `MediaProvider.getRecommendations` and `TitleMatcher`) is complete and landed in `main`.

The open phases below cover expanding TMDB recommendations to non-Xtream providers (Remote M3U, Local files, SMB).

---

## Phase 0.1 — Standardize `search()` for Non-Xtream Providers

- **Goal:** Implement `search()` in `BaseM3uMediaProvider` (covering Remote M3U, Local files, and SMB).
- **Implementation:** Filter in-memory items via `SearchUtils.matchesQuery`, enabling catalogue search and matching without requiring a local SQLite database.

---

## Phase 2 — TMDB Resolution for M3U / Local / SMB Providers

Because non-Xtream providers do not expose native TMDB IDs, title resolution must take place before fetching recommendations:

```
catalogue title -> release-name parser -> (clean title, year)
  -> TMDB /search/movie?query=&year=   (1 network call)
  -> match best result above confidence threshold
  -> fetch /recommendations & /similar
  -> match results against in-memory provider item list
```

### Key Technical Requirements
1. **Release Name Parser:** Parse raw filenames/streams (e.g. `Dune.Part.Two.2024.2160p.WEB-DL.x265` -> `("Dune Part Two", 2024)`).
2. **Title Match Confidence:** Require normalized-title equality and year matching (within ±1) before accepting a TMDB ID.
