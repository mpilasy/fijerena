# Guide on/off clarity

**Status:** In progress. User said proceed 2026-10-06; bulk select dropped entirely (default taken, user didn't choose a menu).

## Problem

Seen on the OnePlus, bears, 2026-10-06. An Xtream source's own guide (`<host> (Bulk)`, its
`xmltv.php`) is switched on and off by **Provides a guide** in Edit Source. The app turned that
switch off by itself after one download came back with no channels. The guide had held 8.3K
channels before, and bears is known to throttle and to send empty or cut-off replies. Every screen
that depends on the switch then behaved as if nothing were wrong, or said something false:

1. **Auto turn-off on one empty download.** `AutoXmltvSources.onEmptyIngest` turns Provides a guide
   off whenever the automatic source ingests no channels and the viewer never set the switch.
   One bad reply is enough, and it stays off until someone notices.
2. **The selection checkbox reads as an on/off switch.** Guide sources (`MobileEpgManagementScreen`,
   `TvEpgManagementScreen`) have a checkbox on each card for the bulk "Refresh (n)" / "Delete (n)"
   bar. Ticking it looks like turning the source on. The tick goes away on Back or on Refresh, which
   looks like a bug. Bulk actions add little on a list that usually holds one or two sources, and
   each card already has Refresh and Delete.
3. **The refresh line lies for a source that is off.** The card says "Refreshes every 6 hours",
   but a source that is off is never refreshed automatically (`getEnabledSourcesForProvider`).
4. **The guide search header says "No guide sources configured"** (`freshnessLabel`,
   `epg_freshness_no_sources`) when the source is configured but off, while its old data still
   shows in the developer-mode index count.
5. **"-1%" during a download** with no `Content-Length` (bears never sends one):
   `ActiveSourceProgress.progressPercent` is -1 for "unknown", and both cards print it as a
   percentage and draw an empty bar.

## Proposal

| Phase | Change | Where |
|---|---|---|
| 1 | Detection: turn Provides a guide off only when the automatic source has **never** had channels (`lastChannels == 0` before this ingest). A source that once had channels keeps its switch, and the empty download shows as that source's error ("came back empty"), so the card's status and Retry Failed cover it. Check whether the ingest writes `lastChannels = 0` before `onEmptyIngest` runs; if it does, read the old value first. Unit tests for both cases. | `AutoXmltvSources`, `EpgFileManager` (~line 998) |
| 2 | The guide source card gets a real on/off switch. On the automatic source it is Provides a guide (it sets the same setting, `byUser = true`, so detection never turns it off again). On a hand-added source it is the source's own `enabled`. The checkboxes and the Refresh (n) / Delete (n) bar go away on both platforms. Refresh Stale and Retry Failed stay. TV: the switch is one more focusable control on the card, under the focus contract in AGENTS.md. | Both management screens, `EpgManagementViewModel` |
| 3 | The refresh line follows the switch: "Off. Not refreshed" (wording to settle) while the source is off, the interval only while it is on. | Both cards, `refreshIntervalSummary` |
| 4 | The guide search header tells "no guide source at all" apart from "guide sources here but all off": a new string such as "Guide off. Turn it on in Guide sources". | `freshnessLabel`, `EpgBrowserViewModel` (count of sources that are off), both browser screens |
| 5 | Unknown download size: an indeterminate bar with the bytes so far ("12.4 MB") instead of a percentage. | Both cards |

All new strings in en, fr and mg. FEATURES, NAVIGATION_GUIDE and RELEASE_NOTES updated with each
phase.

## Decisions for the user

- **Phase 2 changes the look of Guide sources**: checkboxes replaced by a switch. Per the icon-buttons
  rule, look-and-feel changes need your OK.
- Removing bulk select loses "delete several hand-added guide sources at once". It is rarely useful
  with one or two sources. Keep it behind a menu instead?

## Checks

Emulators first (TV and phone, bears for the empty-download case: point the source at a URL that
returns an empty `<tv/>` to force it). Then the OnePlus, the device where it was seen.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1 Detection | Done: in the ingest, a download with no channels for a source whose `lastChannels` > 0 (`isFailedEmptyIngest`, tested) is that source's error "came back empty" — no `markIngested`, and the staging swap leaves its old programmes in place (it skips errored sources). Detection only sees sources that never had channels | 09ff5fd3 |
| 2 Switch on the card | Done: `EpgManagementViewModel.setSourceEnabled` (own guide: `setProvidesGuide(byUser = true)` + `reconcileStored`; others: the row's `enabled`); mobile `Switch`, TV the same focusable toggle surface with an inert `Switch` ("Use this guide"); selection, Refresh (n) / Delete (n), `refreshSelected`, `deleteSelected`, `launchRefreshSelected` and their strings removed; the own-guide line now says its switch is Provides a guide; focus walk updated by hand (not re-run yet) | bc5c1728 |
| 3 Refresh line | Done: `EpgManagementViewModel.refreshSummary(enabled, hours)` ("Off: not used, not refreshed"), both cards, test | ac09ff9c |
| 4 Search header | Done: `freshnessLabel(hasOffSources)` → "Guide off: turn it on in Guide sources" (warning colour), `EpgBrowserViewModel.hasOffSources`, both browser screens, test | (this commit) |
| 5 Unknown size | Not started | |
