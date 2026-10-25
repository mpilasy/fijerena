# Live TV Recent: newest first, playing on top

**Status:** In progress 2026-10-25.

## Goal

One Recent order for Live TV on TV, the same in the preview's list, the full-screen slide-in panel
and the Live TV list behind them: the channel playing first, then newest first. The list moves only
when the channel changes, never while you scroll. Agreed with the user 2026-10-25.

## Today

- The preview's panel freezes Recent's order for each opening of the preview
  (`rememberStableRecentOrder`, `RecentChannels.kt`): a channel already in Recent keeps its row, so
  the playing channel is often not on top. The slide-in panel shares that list; it only looks
  different after the order was taken again (Guide round trip, a new channel added on top).
- The Live TV list keeps its own frozen order (`CategoryViewModel.liveRecentOrder`), with another
  lifetime, so it can disagree with the panel.
- `MediaRepository.refreshRecentItems` can read before a queued Recent write lands, so a fresh
  order can be stale.
- Zapping steps through the list on screen; a zapped channel not yet in Recent leaves it when you
  zap on, and Up then wraps to the end.

## Design

1. **Playing first:** the preview's Recent tab (docked and slid in) puts the channel playing at
   the top, whether or not it is in Recent yet (`CurrentChannelPolicy.FIRST`).
2. **Newest first:** no frozen order in the panel or the Live TV list; the repository's order
   (last played first). Rows still do not move under the cursor: the order changes only when a
   channel is written, which follows a change of channel. A removal and its Undo keep places (the
   row comes back with its time).
3. **Zap order:** Up/Down in full screen walk the list as it was at the first zap, until a channel is
   picked, the tab changes or full screen closes; the channel you stop on goes to the top.
4. **No stale read:** `refreshRecentItems` waits for queued writes first.
5. **Phone:** unchanged for now (its dock list and player sheet keep their frozen order); only the
   stale read fix reaches it.

## Phases

1. **Core:** `refreshRecentItems` awaits writes; `CurrentChannelPolicy.FIRST`; tests.
2. **TV:** the panel and the Live TV list without a frozen order; the zap order; tests; walk on the
   TV emulator.
3. **Docs:** FEATURES (Recent row), RELEASE_NOTES.

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. Core | In progress | |
| 2. TV | Not started | |
| 3. Docs | Not started | |
