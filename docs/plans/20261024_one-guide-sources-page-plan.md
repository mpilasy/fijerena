# One Guide Sources page

**Status:** In progress 2026-10-24. All decisions taken. Phases 1–2 done; Phase 3 next.

## Goal

Guide Sources is one page for every media source, not one page per media source. Settings →
Guide Sources opens it directly, with no source chooser first. The page shows every guide source,
grouped by the media source it belongs to. Add asks which media sources the new guide source is
for.

## Today

- A guide source (`epg_source`) belongs to one media source (`provider_id`). The same XMLTV URL
  used by two media sources is two rows, each with its own switch, auto-refresh and stats.
- `Screen.EpgManagement(providerId)` opens one media source's page (`TvEpgManagementScreen`,
  `MobileEpgManagementScreen`). Its `EpgManagementViewModel` reads only that source's rows
  (`getSourcesForProvider`), and its stale and failed counts, processing state
  (`EpgFileManager.stateFor(providerId)`) and Add are for that source only.
- Five ways in, each to one media source:
  - Settings → Sources & Guide → Guide Sources: a source chooser first when the profile has two or
    more sources with live channels (`SourceChoice.of(uiState.guideSources)`).
  - Sources → a source's Guide button.
  - Edit Source → Guide Sources row.
  - Search the guide → Guide Sources button (chooser first, O4).
  - The EPG Browser's Guide Sources button.
- Focus coverage: `tv/src/test/.../epg/GuideSourcesFocusTest.kt`,
  `tv/src/test/.../settings/SettingsSourcesGuideFocusTest.kt`, and the walks
  `scripts/focus-walks/epg-management.txt` and `settings-guide.txt`.

## Design

1. **One page.** `Screen.EpgManagement(providerId: Long = 0L)`. 0 opens the page at the top; a
   media source's id opens it with focus on that source's group header (the four ways in from one
   source). Settings → Guide Sources opens it with 0 and no chooser.
2. **Groups.** One group for each media source on the device that can have live channels (GS-D2),
   in the Sources list's order. The header shows the source's name and a line: how many guide
   sources, the last refresh, and "Not used by this profile" when the profile in use does not have
   it (as on the Sources list). Under it, that source's guide sources as today's cards (switch,
   auto-refresh, refresh, edit, delete). A source's own guide (`<host> (Bulk)`) stays in its group.
   A group with no guide source shows one line: "No guide sources".
3. **Add (GS-D1).** The Add form gets a **For** list: the media sources with live channels, as
   switches (TV) or check boxes (phone), under "Use for" (GS-D5), the first one on — or the group's source when the page was
   opened on a source. Save adds one row per checked source (same URL, label, time zone). With one
   media source the list does not show. A URL that a checked source already has is skipped for that
   source (`getSourceByUrl`), not doubled.
4. **Refresh.** See GS-D3.
5. **ViewModel.** `EpgManagementViewModel` reads every row (`getAllSources`) and groups it by
   `providerId` with the providers list; stale and failed counts and the processing state are kept
   per media source and summed for the page. `addSource` takes a set of provider ids. Settings'
   own use (Guide data maintenance rows, providerId 0) is unchanged.
6. **No database change.** Rows keep one `provider_id` each; no migration.
7. **PIN and access.** Unchanged: an admin row, guarded by `isPinGuarded`.

## Decisions

- **GS-D1 Add for several media sources (decided, user 2026-10-24):** a check list of media
  sources; one row is added for each.
- **GS-D2 Which media sources show (decided, user 2026-10-24):** every media source on the device
  that can have live channels, not only the profile in use's. Guide sources are shared and only an
  admin opens this page.
- **GS-D5 Words (decided, user 2026-10-24):** no new words. On screen a media source is a
  "source" (Manage Sources, Add Source) and an EPG source is always a "guide source", never
  "source" alone. The page never says "media source": each group header is the source's own name,
  and the Add form's list reads "Use for" with the source names. "Media source" is this plan's word
  only.
- **GS-D3 Refresh buttons (decided, user 2026-10-24: recommended (a)):** (a) the page header keeps one **Refresh stale (n)** and one
  **Retry failed (n)** for all groups; or (b) each group header has its own. Recommend (a): fewer
  buttons in the D-pad path, and a per-guide refresh stays on every card.
- **GS-D4 Add from a group (decided, user 2026-10-24: recommended (a)):** (a) one Add at the top of the page only; or (b) also an Add
  on each group header, with that source checked. Recommend (a), with the group's source checked
  when the page was opened on a source.

## Phases

1. **ViewModel and route:** grouped data, per-source counts, `addSource` for several sources with
   the duplicate check, `providerId = 0` route default. Unit tests for grouping, counts and Add.
2. **TV page:** group headers, the For list in the Add form, focus on the group header when opened
   on a source (Back focus rules unchanged). Update `GuideSourcesFocusTest` and the walk
   `epg-management.txt`.
3. **Phone page:** the same groups and the For check boxes in the Add dialog.
4. **Ways in:** Settings opens the page with no chooser (TV and phone); the other four pass their
   source. Remove the now unused chooser paths. Update `SettingsSourcesGuideFocusTest` and the walk
   `settings-guide.txt`.
5. **Docs and checks:** FEATURES (Guide Sources), NAVIGATION_GUIDE (routes and Back focus),
   RELEASE_NOTES; walk both emulators with two media sources with live channels.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. ViewModel and route | Done | b865c642 |
| 2. TV page | Done (the Add dialog's Use for list not yet checked on the emulator) | Phase 2 commit |
| 3. Phone page | Not started | |
| 4. Ways in | Not started | |
| 5. Docs and checks | Not started | |
