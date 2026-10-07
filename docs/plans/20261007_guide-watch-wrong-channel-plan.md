# Search the guide: Watch plays the wrong channel

**Status:** In progress. Found while testing the TV home overhaul on bears 2026-10-07; user said do
all three fixes, hidden-category results hidden (2026-10-07).

## Problem

On bears with profile atr (14 of 875 live categories visible), Watch on a Search the guide result
played another channel: the last channel watched (EPT 1 HD), or a category separator row
("#### ΑΘΛΗΤΙΚΑ/SPORTS ⱽᴵᴾ ####", stream 2153213), which then went into Recent and Home's
Channels row as "Last watched".

1. **Preview seed falls back to the last channel.** `LiveTvSplitLayout`'s seed is
   `guideReturn ?: initialStream ?: lastPlayed` found in the list. When the requested channel is not
   in the list (its category is hidden, so `CategoryViewModel` loads Recent), the last channel
   watched plays. The fallback is meant only for an entry with no channel asked for.
2. **The shared matcher cache holds only visible streams after a sync.** `ProviderSyncRunner`
   warms `EpgChannelMatcher`'s cache with `getAllStreams` (hidden categories left out: 394 of
   54,120 on bears); Search the guide reuses it instead of building from all streams. The real
   channel is missing, so matching falls to "name contains", and a separator whose name normalizes
   to "sports" (Greek letters and `#` stripped) matches "NOW: SKY SPORTS RACING".
3. **Results on channels whose category the profile hides** are shown, and can't be opened.

## Fixes

| # | Change | Where |
|---|---|---|
| 1 | Seed with the last channel only when no channel was asked for; a requested channel not in the list leaves the preview on the list | `LiveTvSplitLayout.kt` |
| 2 | Warm the cache with every live stream (`getAllStreamsIncludingExcluded`), as the browser builds it; separator rows (name starting with `#`) never match by name (levels 3–5), only by guide id | `ProviderSyncRunner.kt`, `EpgChannelMatcher.kt`, test |
| 3 | Search the guide drops airings matched to a stream whose category the active profile hides, as it drops excluded streams; hidden ids read per search (the matcher cache stays profile-independent) | `EpgBrowserViewModel.kt` |

## Checks

TV emulator, atr on bears (user asked for bears): Watch on a result in a visible category plays it;
no result from a hidden category; a sync then a search still matches by guide id.

## Progress

| Fix | Status | Commit |
|---|---|---|
| 1 Preview seed | Done: the last channel seeds only an entry with no channel asked for (guide return, then the requested channel, keep their order). Checked on the TV emulator, atr on bears: Watch on AL: SUPER SPORT 1 (matched to SAT: SUPERSPORT 1, category hidden) opens the Recent list with nothing playing | |
| 2 Matcher cache + separators | In progress | |
| 3 Hidden categories | Not started | |
