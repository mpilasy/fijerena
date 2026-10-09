# TV Recent and Favorites: removal and Live TV Recent

**Status:** Proposed 2026-10-09, not started. Written from a walk on the TV emulator (iptv, atr) after
the user said removing from Recent / Favorites is "a chore and error-prone" and Live TV's Recent "a
disaster", and asked for a proposal without questions.

## What the walk found

Set-up: six News channels played ~13 s each in the preview, then zapped through Recent in full screen.

1. **Removing from Recent kills focus.** Browse → Recent → Hold OK / Menu → Down → OK on "Remove from
   Recent": the row goes and *nothing* has focus. Down, Up, Left, Right, OK do nothing; only Back
   (to the rail) gets you out. A second removal is impossible without Back → Right → find the row
   again. In the preview's channel panel, focus jumps to the Recent *tab* at the top instead.
2. **The menu opens on the wrong action.** In Recent the menu opens on "Add to Favorites"; the
   removal is the second item. One OK too fast adds a favourite instead of removing.
3. **Favourites take four steps and a dialog.** Hold OK → "Remove from Favorites" → a confirmation
   that opens on Cancel → Right → OK. Recent has no confirmation at all. Two rules for the same act.
4. **No way to act from Home.** Hold OK / Menu on Home's Channels, Continue Watching and favourites
   cards does nothing: no remove, no mark watched.
5. **No "clear Recent".** The only clear is Edit Source → Clear all progress: every section's history
   at once, on a screen the rail doesn't even show.
6. **Live TV Recent fills with every channel you stop on.** Anything watched past the 10 s delay goes
   in, zapping included; with the cap at 25, a short zap through a category pushes out the channels
   you actually watch.
7. **Recent changes order under you.** One full-screen zap pass (Down through Recent) reversed the six
   News channels (stored order became 10 TV, 13 Siam, 22Scope, 24/7, 24 Horas, 24Hrs). The panel's
   Recent kept the old order until the layer closed; browse and Home showed the new one; Home's
   Channels row puts favourites first, so the same channels appear in three different orders.

## Proposal

### A. Removing is one press, everywhere, and focus stays put

- **Fix focus after a removal** (bug, 1 above): focus moves to the next row — the previous one when
  the last row went — in browse, the preview panel and the full-screen panel. The TV Guide already
  does this (Remove from Recent there lands on the row that took its place); `StreamList` gets the
  same.
- **The menu opens on what the list is about:** in Recent, "Remove from Recent" first and focused; in
  Favorites, "Remove from Favorites" first and focused; elsewhere "Add to Favorites" first as now.
- **No confirmation dialog; Undo instead.** A removal (Recent or Favorites) happens at once and a
  small bar shows "Removed 24Hrs TV · Undo" for ~5 s (Up/OK on it undoes). The favourites dialog
  goes. One rule for both lists; a slip costs one press to undo, not a dialog every time.
- **An "Edit" mode for Recent and Favorites lists** (a pencil icon in the list header, next to
  Refresh): each row shows a ✕ on focus; OK removes it at once, focus stays on the next row, so
  clearing six channels is six presses. Back or the pencil again leaves the mode. In Recent the mode
  also offers **Clear Recent** (this section only, with a confirmation — the only confirmed action).
- **Home's cards get the same Hold OK / Menu actions** as the list rows: Remove from Recent on
  Continue Watching and Channels cards, Remove from Favorites on favourite cards, Mark watched on
  films and episodes. The card leaves the row with Undo, focus on its neighbour.

### B. Live TV Recent means "channels I watched", in one order

- **Settle before recording.** A channel you *chose* (OK on a row, the Guide, Search, a Home card)
  goes into Recent after the usual watch delay. A channel you only *zapped onto* (Up / Down in full
  screen, the panel's zap) goes in only once you stay on it for a minute (`ZAP_SETTLE_MS`, 60 s).
  Zapping through a category no longer floods Recent.
- **The list doesn't move while you use it.** The order is fixed when you open Live TV and only
  refreshes when you come back to it (or press Refresh): zapping, playing and removing don't
  reshuffle the rows you're looking at — a removed row just goes, and the one playing is marked.
  (Today the panel already keeps its order for the layer's life; browse and Home don't.)
- **One order everywhere:** last watched first, then most recent. Home's Channels row stops
  mixing favourites in front: it shows Recent in that order, and favourite channels get their own
  "Favorite channels" row like Favorite movies / shows.
- **A smaller cap for Live TV** (12 instead of the source's 25): a channel list you scan, not an
  archive. Films and shows keep the source's Recent row size.

## Phases

| Phase | What |
|---|---|
| 1 | Focus after removal (browse, panels) — the bug — with a focus walk (`recent-remove.txt`) |
| 2 | Menu order by list; Undo bar replacing the favourites dialog |
| 3 | Edit mode on Recent / Favorites lists, Clear Recent (per section) |
| 4 | Home cards: Hold OK / Menu actions |
| 5 | Live TV Recent: settle rule for zapped channels, stable order per visit, one order everywhere, Home's Favorite channels row, cap 12 |
| 6 | Docs (FEATURES, NAVIGATION_GUIDE, RELEASE_NOTES), focus walks, archive |

Phase 1 is a bug fix on its own; 2–4 change how removal looks and feels; 5 changes what Recent
records. No model or database change: the settle rule is when `watch_state` is written, the cap is
the query's limit, the stable order is UI state. The phone is out of scope (its swipe-to-reveal
rows don't have the focus problem), except that the settle rule and the Live TV cap live in shared
code and apply to it too.
