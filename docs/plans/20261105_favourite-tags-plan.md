# Favourite tags

**Status:** Planned 2026-11-05. The user answered FT-Q1 to FT-Q4 on 2026-11-05 (see Answers). FT1 in progress.

## Goal

The user (2026-11-05): add tags to favourites, so the user can filter them.

A profile gives its favourite channels, films and shows one or more tags (for example "News",
"Kids", "Weekend"). The Favourites list on TV and phone shows a tag filter: select one tag to see
only its favourites.

## What exists (survey 2026-11-05)

- **Storage.** `favorite_state` in `xtream_v2.db` (`FavoriteStateEntity`, `FavoriteStateDao`),
  primary key (`providerId`, `profileId`, `itemId`, `contentType`, `kind`). `kind` is `STREAM`
  (channel, film, show) or `CATEGORY` (a whole source category). Ordered by `createdAt DESC`.
  `upsert` is insert-or-replace: re-favouriting refreshes `createdAt`. `DB_VERSION` = 30.
- **Repository.** `MediaRepository` keeps an in-memory snapshot (`addFavorite`, `removeFavorite`,
  `getFavoritesForContentType`, `isFavorite`, …). `ProfileFavorites` edits another profile's
  favourites (Xtream only).
- **Sync.** `SyncKind.FAVORITE_STREAM` / `FAVORITE_CATEGORY`; payload `SyncPayloads.Favorite(name,
  parentCategoryId, createdAt, tmdbId)`. The triggers `sync_favorite_state_insert/update` queue a
  row on any change while `sync_clock.applying` = 0. Every sync decoder uses
  `ignoreUnknownKeys = true`.
- **Export.** `SettingsExportManager`: `ExportedFavorite(itemId, itemName, categoryId,
  contentType, tmdbId)` per source, for the active profile.
- **UI.** Favourites is a virtual category (`CategoryViewModel.FAVORITES_CATEGORY_ID`): a Star tab
  in TV `BrowseTabs` (`TvSectionTabs`), a chip in the phone `MobileCategoryListScreen` chip row
  (`CategoryChip` → `CinemaFilterChip`). Long-press menus: TV `FavoriteContextMenuDialog`
  (`FavoriteMenuTarget`), phone `ItemActionsDialog`.
- **No tag, label, group or folder concept exists** for favourites.

## Answers (user, 2026-11-05)

| # | Question | Answer |
|---|---|---|
| FT-Q1 | Tag scope | **Per profile, all sources.** One list of tags per profile; a tag works for favourites of every source. |
| FT-Q2 | Where to filter | **The Favourites list** in browse (TV tabs, phone chips). Home rows and the player stay as they are. |
| FT-Q3 | Tag model | **Many tags per favourite; the filter selects one tag at a time.** |
| FT-Q4 | Sync and export | **Both.** Tags sync between devices and go into the settings export. |

## Decisions (proposed)

| # | Subject | Decision |
|---|---|---|
| FT-D1 | What can have tags | Stream favourites only (channels, films, shows). Favourite categories do not. |
| FT-D2 | Storage | A new column `favorite_state.tags` (TEXT NOT NULL DEFAULT '[]', a JSON array of names, for example `["News","Kids"]`), migration 30 → 31. No tag table: the profile's tags are the names in use on its favourites, across all its sources. A tag with no favourite left disappears. |
| FT-D3 | Re-favouriting | `addFavorite` keeps the tags of an existing row (today `upsert` replaces the row). Removing a favourite removes its tags with it. |
| FT-D4 | Tag names | Trimmed, 1–30 characters. A name that matches an existing tag without regard to case uses the existing tag. Tags are sorted by name in the app's locale. |
| FT-D5 | Sync | `SyncPayloads.Favorite` gets `tags: List<String> = emptyList()`. The existing triggers already queue a change to the row. An older build ignores the field; if an older build re-favourites an item, that item's tags are lost (accepted; devices update together). |
| FT-D6 | Export | `ExportedFavorite.tags: List<String> = emptyList()`; import sets them under the existing "Favourites" choice. Bump the export format version. |
| FT-D7 | Set tags | A **Tags** action in the long-press menu of a favourite (TV `FavoriteContextMenuDialog`, phone `ItemActionsDialog`), shown only when the item is a favourite. It opens a tag dialog: a check row per tag of the profile, then **New Tag** (text entry, adds and checks it). Changes save at once. Same rule as favourites for who may edit (hidden where the favourite toggle is hidden). |
| FT-D8 | Filter (phone) | When the Favourites chip is selected and its favourites have at least one tag, a second chip row shows **All** and each tag used by the favourites of this content type. One chip selected at a time; All is the default. |
| FT-D9 | Filter (TV) | Same items as FT-D8, as a row of `TvSectionTabs` above the Favourites list, only while the Favourites tab is open. Follows the focus contract in CONTRIBUTING.md: entry focus on the selected tag tab, Down reaches the list, Up from the list returns to the selected tab. |
| FT-D10 | Filter state | The selected tag stays while the user stays in that browse screen; it resets to All when the screen opens again. A tag that empties (last favourite removed) falls back to All. |
| FT-D11 | Rename and delete | Long-press a tag chip (phone) or the menu key on a tag tab (TV): **Rename** (merges into an existing tag of the same name) and **Delete** (removes the tag from every favourite of the profile, asking first). One write per row, in one transaction. |
| FT-D12 | Words | "Tags", "New Tag", "All", "Rename Tag", "Delete Tag", "Remove the tag <name> from all favourites?" in en (Title Case, British spelling), fr and mg. |

## Phases

| Phase | Work | Status |
|---|---|---|
| FT1 | **Data.** Migration 30 → 31, entity field, DAO (`setTags`, `renameTag`, `deleteTag`, keep tags on upsert), `MediaRepository` API (`favoriteTags(profileId, contentType)`, `setFavoriteTags`, filter by tag), sync payload, export/import. Update `docs/DATABASE_SCHEMA.md`, the exported schema, and the rollback helpers in `XtreamDatabaseMigrationTest`. Unit tests: migration, keep-on-refavourite, rename merge, delete, sync round trip, export round trip. | In progress |
| FT2 | **Set tags.** Tag dialog in core/ui (shared state) with TV and phone builds; Tags action in both long-press menus. Strings in en/fr/mg. | Planned |
| FT3 | **Filter.** Tag chips (phone) and tag tabs (TV) on the Favourites list; rename and delete from a tag; filter state in `CategoryViewModel`. TV focus walk. Docs: FEATURES, NAVIGATION_GUIDE, RELEASE_NOTES. | Planned |

Each phase is one commit on main, checked on both emulators before it counts as done.

## Not in scope

- Tags on favourite categories, Home rows, the player dock or the TV player channel list.
- Selecting more than one tag at a time.
- Tags in the parent's `ProfileFavoritesPane` editor (a later step, if wanted).
