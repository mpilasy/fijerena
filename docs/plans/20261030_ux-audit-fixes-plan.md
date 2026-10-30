# UX audit fixes

**Status:** In progress 2026-10-30.

## Goal

Fix the major problems that the UX audit of 2026-10-30 found on the TV and phone emulators, and
the minor ones that share their code. The audit walked every main screen with real data
(jellyxtream for films and shows, iptv for Live TV); the root causes below come from the code.

## Decisions (user, 2026-10-30)

| # | Question | Decision |
|---|----------|----------|
| UX-D1 | Card menu: what has focus first? | A safe action. Removals go last, before Cancel. This replaces the earlier "Remove from Recent first" choice (`FavoriteMenuDialog` KDoc). |
| UX-D2 | A channel that does not start? | After 10 s with no picture: "<name> is not available right now". OK goes to the next channel, Back stays. A new pick stops the old stream at once. |
| UX-D3 | Recent and a channel that never played? | The "add after N s" time counts only time with picture. A channel that never plays never goes into Recent. |
| UX-D4 | Source text in another language? | When TMDB has the plot and genres in the app language, show them; the source text only when TMDB has none. |
| UX-D5 | Technical rows and language tags on details? | Hidden for a child profile only. |
| UX-D6 | Double OK in the TV preview? | A second OK waits for picture; then it goes full screen. Back during tuning always works. |
| UX-D7 | The show-missing toggle? | One eye icon, plain when off, filled in the accent when on; the label names the setting with On/Off. Each row has its own toggle. |
| UX-D8 | Phone Live TV while docked? | The category the channel was picked from becomes a third dock tab, selected, at the same scroll place. The chips stay hidden. |
| UX-D9 | Jump in long lists? | No preference: CH+/CH- and Page Up/Down move focus a screen at a time and keep it visible (TV). |

## Findings and fixes

1. **TV Home loses focus after a return** (Continue Watching and the other rows).
   - The return scrolls the card to the left edge (`ContentTypeSelectionScreen.kt` `scrollToItem` at
     the return paths), so earlier cards and the first-card requester leave composition.
   - The return requester is attached only while its key is pending (`NavReturnFocus.kt`), and
     clearing it drops focus from the focused card.
   - The saved key survives a rail pick of Home, so Home opens on the last opened card.
   - `MinimalBringIntoView` shows the card but not the row title above it.
   - Fix: scroll only when the card is not visible (as the removal path does); attach the return
     requester for good, one per card; clear the key when the rail opens Home; bring the whole row
     (title and cards) into view when it takes focus.
2. **Destructive action first in card menus** (UX-D1).
   - TV: `FavoriteMenuDialog` (`recentFirst`), Home cards, Recent and Favourites lists
     (`StreamList`), the Guide's recent list (`TvGuideGrid`).
   - Phone: the Home Recent sheet (Remove only), `ItemActionsDialog` (Remove from Favourites as the
     main button).
   - Fix: safe actions first with first focus; removals last; update the KDoc and CONTRIBUTING rule 7
     note.
3. **A channel that does not start** (UX-D2).
   - A Recent row of a channel no longer in the M3U list gives "Item not found" at once
     (`BaseM3uMediaProvider`), and the loader publishes Error. The phone dock reads only Success, so
     it shows "Connecting" for ever, keeps the old stream and moves the Playing mark.
   - The M3U resolve reads the snapshot, not `loaded()`, so after `trimMemory()` every resolve fails.
   - The loader has no time limit; the TV watchdog starts only after Success.
   - Fix: on Error or after 10 s with no picture, stop the old stream and show the UX-D2 message with
     Next on OK (TV and phone); the M3U resolve calls `loaded()`; a load has an overall time limit;
     Recent rows whose channel left the list are marked gone (as favourites are).
4. **Recent gets channels that never played** (UX-D3).
   - Recent is scheduled at Success (URL resolved), and the at-once write on full screen fires
     before picture.
   - Fix: start the delay, and the at-once write, when the engine first plays that stream; cancel it
     on a playback error or a watchdog stop.
5. **Phone: wrong channel marked Playing** after a zap in full screen.
   - The mark is `dockTarget`, which full-screen zaps never update.
   - Fix: mark the loader's current channel (by source key); set the dock target on return.
6. **Phone: the dock loses the browsed category** (UX-D8).
7. **TV tuning: Back and double OK** (UX-D6).
   - Back first hides the stream-info banner that tuning holds up; the second OK promotes at once.
   - Fix: Back during tuning goes straight back; promote only when the channel plays.
8. **Kid details: Greek text, raw tags and technical rows** (UX-D4, UX-D5).
   - The source plot wins over the TMDB overview (`XtreamMediaProvider`); genres come only from the
     source; Video, Audio, Subtitles, Container and Stream Name rows have no gate.
   - Fix: prefer the TMDB overview and genres in the app language (and refresh the cached plot);
     hide the technical rows and Stream Name on a child profile.
9. **The show-missing toggle** (UX-D7): person page and Trending rows, TV and phone; the person
   page's Movies and TV Shows rows get one toggle each; an empty row says "None in your sources"
   instead of a bare heading.
10. **Long lists** (UX-D9): CH+/CH- and Page Up/Down on TV lists.

## Phases

Lanes touch different files and run at the same time, each on its own branch; each merges as one
commit.

| Phase | Work | Status |
|-------|------|--------|
| A | TV Home focus (1) and card menus (2), TV and phone | In progress |
| B | Live TV loading, shared and TV: 3 (loader, M3U, TV message), 4, 7 | In progress |
| C | Phone Live TV dock: 3 (dock error and message), 5, 6 | In progress |
| D | Details text and child gate (8), TV and phone | In progress |
| E | Toggle (9) and long lists (10), after A–D | Not started |
| F | Full checks, emulator walks of every fix, docs (FEATURES, NAVIGATION_GUIDE, RELEASE_NOTES) | Not started |

## Later (minor and polish items from the audit, not in this plan)

Search scope and focus on the phone, live results on TV; duplicate titles that look the same;
wording, plurals and raw source types; English left in the Malagasy UI (Wishlist date, About,
screen-reader labels); details action order and icons; the phone channel panel; Recent order moving
under the cursor; the Charts tab scroll; Wishlist placeholder posters; player safe area and scrim;
the TV rail panel; the Kid profile's status line, gear badge and double avatar; M3U: Live TV depth
and the Guide button with no guide.
