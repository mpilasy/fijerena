# TV Focus Navigation Plan

**Status:** Approved (2026-10-03), including the Live TV flows section. Not started.

D-pad focus feels wrong across the TV app, not only in Settings. The TV emulator
(Television_1080p, build `578f17c7` = `main`, which already includes `393a6405` "Back returns
focus" and `02ecc6a5` "picker dialogs keep focus") was walked screen by screen. Focus was read by
text after every key press (`uiautomator dump`, `focused="true"` node), with screenshots only to
confirm.

Screens walked: Profile picker, Home (iptv and jellyxtream), source switcher, Live TV list,
Live preview, Live full-screen player, Movies list, Movie details, TV Shows list, Episode
selection, Search. **Not walked:** TV Guide and EPG Browser — they only appear once a guide is
indexed, and the emulator's guide isn't. They get their own round as Phase 9 (decision 2).
A second, deeper walk of Live TV (browse → preview → full screen → flyouts → OSD → back) is
the "Live TV flows" section.

Settings and its child screens are covered by `docs/plans/20261003_settings-ux-overhaul-plan.md`;
this plan uses the same **TV focus contract** (that plan, Part B) and owns the shared primitives
both plans need.

No breaking changes: no setting, route or storage changes. Focus, focus stops and where row
actions live change everywhere; in Live TV, also which list Up/Down zaps through and the shape of
the channel panel (called out in that section, LT2/LT3).

## Root causes (why it feels bad everywhere)

Almost every finding below comes from one of eight patterns. Fixing the patterns, as shared
primitives, fixes the screens.

| # | Pattern | What the user feels |
|---|---|---|
| R1 | **No pane boundaries.** Two-pane screens (categories ↔ items, preview ↔ list) are plain siblings; Compose's geometric search picks whatever is nearest. | Down at the end of a list jumps into the other pane; Left from an item lands on a random category; Left from a category lands on Search in the corner. |
| R2 | **No per-pane focus memory.** Re-entering a pane lands on the geometrically nearest item, not the last-focused or the selected one. | Left from a stream goes to the category level with it (e.g. "Lifestyle"), not the category you are in; Right from Search resets the category list to the top. |
| R3 | **Hidden per-row action buttons** (Add to favourites, Mark as watched, Remove from Recent) appear next to the focused row and are extra Right stops. Their side flips between modes (right of the row in the list, **left** of it in preview). Down from one goes to the next *row*, not the next button. | Right does three different things depending on the row; destructive "Remove from Recent" is one press from a channel. |
| R4 | **Inconsistent entry focus.** Home opens on the source chip; Live TV opens on a stream; Movies/TV Shows open on categories; Episodes open mid-list with the show header scrolled away. | "Where am I?" on every screen. |
| R5 | **Utility icons in the main path.** Refresh categories / Refresh streams sit between the header and the list; Refresh movie/series info sits in the action row. | Accidental network refreshes (happened twice during this walk); extra presses to reach content. |
| R6 | **Several highlights look like focus.** Selected category (filled), "continue watching" episode (outlined), checked toggles, primary buttons all use the focus colour family. | Two or three rows look focused at once. |
| R7 | **Text field opens the keyboard on focus.** | Search: moving Up into the field pops the IME and traps D-pad in it; the search button to its right is unreachable. |
| R8 | **Right-aligned header over left-to-right content.** Header buttons sit top-right; cards start left. | Down from the header lands on the rightmost card (Movies / TV Shows), Up from Live TV goes to the chip, Down goes back to Movies — not reversible. |
| R9 | **Two components for one job.** The same list is drawn by different code in different places (Live TV preview panel vs full-screen flyout; Language dialog vs other option lists). | Same channels look and behave differently one key press apart; current-channel marking present here, missing there. |
| R10 | **Key map changes per layer.** Left/Right mean pane switch, list toggle, reveal actions, open flyout or move between buttons depending on where you are, with nothing on screen saying which. | "What does Left do here?" — and the wrong guess is sometimes destructive (Remove from Recent). |

## Findings by screen

IDs: `F-<screen>-n`. Root cause in brackets.

### Profile picker
- F-PP-1 Entry focus on last profile ✓. Focus ring thin on a coloured avatar — low contrast at
  distance. (R6)

### Home (`ContentTypeSelectionScreen`)
- F-H-1 Entry focus on the **Switch Source chip**, not content. (R4)
- F-H-2 Down from any header button → rightmost card; Up from the leftmost card → chip; Down
  again → rightmost card. Not reversible. (R8, R2)
- F-H-3 Live TV card focusable with "0 categories" (jellyxtream) — a dead end. (R4)
- ✓ Back from a content screen returns to the card that opened it (`393a6405`).

### Category screens — Live TV / Movies / TV Shows (`TwoColumnLayout`, `CategoryList`, `StreamList`, `LiveTvSplitLayout`)
- F-C-1 Entry focus differs: Live TV → first stream; Movies / TV Shows → "Recent" category. (R4)
- F-C-2 Down at the end of the item list → jumps to a category in the left column ("Recent
  Categories", "Favorites"). (R1)
- F-C-3 Left from any item → the category level with it, not the selected one. (R1, R2)
- F-C-4 Left from any category → **Search** (top-left). Right from Search → "Recent" (top),
  category list scrolled back up. (R1, R2)
- F-C-5 Right from a category → its hidden ★ button, then the items. Right from an item → ★,
  then ✓ (Movies). Down from a ★ → next row, not next ★. (R3)
- F-C-6 Up from the first category / first item → Refresh categories / Refresh streams; one OK
  re-fetches the list. (R5)
- F-C-7 Right from Categories header area → Recent (category) rather than the item pane. (R1)
- F-C-8 Selected category (filled) and focused category look alike. (R6)

### Live preview and full-screen player
First-walk notes (F-LP-1..4, F-PL-1..3) are superseded by the second walk below — every one of
them is explained there: F-LP-1 = L-11, F-LP-2 = L-8, F-LP-3 = L-9, F-LP-4 = L-2 (by design, not a
bug), F-PL-1 = L-2, F-PL-2 = L-3, F-PL-3 = L-4. ✓ Back unwinds panel → full screen → preview →
list → Home.

### Movie details (`MovieDetailsScreen`)
- F-MD-1 Entry focus on Play ✓.
- F-MD-2 Action row is unlabeled icons: ★, ○ (mark watched), ⟳ (refresh info — a
  maintenance action next to Play). (R5)
- F-MD-3 Up from the Cast / Details / Similar tabs → **Mark as watched**, not Play (column
  geometry); the page scroll jumps in big steps between hero and tabs. (R1)
- F-MD-4 Cast names visible but not focusable — Down from the tabs does nothing on Cast. Fine
  if intended; say so (no dead stop between stops, rule 3, is satisfied).

### Episode selection (`EpisodeSelectionScreen`, 2 320 lines)
- F-E-1 Entry focus on the continue-watching episode ✓, but the list is mid-scroll and the show
  header (title, Play, seasons) is off screen — no context. (R4)
- F-E-2 Reaching the header/Play/Favourites takes ~10 Up presses through every earlier
  episode. Left/Right do nothing in the list. (R1)
- F-E-3 Two highlighted rows: continue-watching (outlined) + focused (filled). (R6)
- F-E-4 Episode number column wraps: "E199 / 2" for E1992. (layout)
- F-E-5 Play button label "Play S0:E0" while Next Up says "Getting Into Cirque du Soleil". (copy)
- F-E-6 "Next Up" card top-right is not focusable but looks like a button. (rule 3)

### Search (`SearchScreen`)
- F-S-1 Entry opens the keyboard immediately; Up from results into the field re-opens it;
  Right in the field goes into the keyboard, never to the search button. (R7)
- F-S-2 Recent searches reachable only after Back hides the keyboard. (R7)

### Source switcher dialog
- ✓ Opens on the active source; Up/Down list; reference pattern for every picker.

## Live TV flows: browse, preview, full screen (walked 2026-10-03)

Walked on the TV emulator with `main` (`578f17c7`), profile Kid, source iptv, no guide indexed.
Three entries: Home → Live TV; browse → category → channel; browse → Recent → channel. Each
followed into the preview, full screen, both flyouts, the OSD, and back out. Focus read by text
after every key; zap and OSD timings from logcat and polling.

### How it is built today (what the user never sees, but feels)

- Home → Live TV pushes **two** nav entries: `CategoryList(showPreviewPane=false)` (browse) and,
  when a last channel exists, `CategoryList(showPreviewPane=true)` (preview) on top, so Back from
  the preview has a browse stopover (`docs/NAVIGATION_GUIDE.md` → Live TV Preview / Dock).
- OK on a channel in browse pushes a **third** preview entry, with `initialCategoryId` = the
  channel's **real** category (not the list it was picked from), and `initialStreamId`.
- The preview (`LiveTvSplitLayout`) is video 66 % + title/now/next, and a 34 % panel that shows
  **Recent** (default) or **Favorites** — never the browsed category. Left/Right toggle the two.
  Focus on a row tunes the preview after 600 ms. OK promotes to full screen in place (same engine).
- Full screen (`PlayerScreen`): Up/Down zap through `streams` = the entry's category (the one
  resolved above); Left slides in a "Category Channels" flyout (same list); Right slides in a
  "Recent" flyout (`recentStreams` **minus** the current channel); OK shows the OSD.
- The flyout (`TvChannelListOverlay`) is a different component from the preview panel: plain
  text buttons, no number/logo/now-playing, ⅓ width, title "Category Channels" (category unnamed).

### Findings

| # | Finding | Cause |
|---|---|---|
| L-1 | **Preview never opens on entry.** Home → Live TV always lands on the bare browse list, even with a last channel (3 of 3 tries). `docs/FEATURES.md` / `NAVIGATION_GUIDE.md` say Live TV "always shows a channel playing alongside the browse list". | `b3322406` (2026-09-23) switched the second push to `navigateOnce`, which only navigates when the current entry is RESUMED; right after the first push the browse entry is not yet RESUMED, so the preview push is dropped every time. Regression, 1 line. |
| L-2 | **Three different channel lists in one flow.** Pick 24 Horas Canarias from News;Public: the preview panel shows **Recent** (4 rows); Down in full screen zaps to 24 Horas Catalunya (**News;Public**); Left flyout shows **News;Public**; Right flyout shows Recent without the current channel. Pick 3ABN International from Recent: panel shows Recent; Down zaps to 3ABN Latino — a channel from its **real category**, a list the user never opened; Left flyout shows that category. | Preview panel hard-wired to Recent/Favorites (`listSource`); zap and Left flyout use the entry's `streams`; Right flyout uses `recentStreams` with `CurrentChannelPolicy.EXCLUDE`. |
| L-3 | **Preview panel ≠ flyout** in look, content and rules: panel = `StreamList` rows (number, logo, title, row actions, header with count + Refresh, glass panel, current row highlighted and *included*); flyout = bare `CinemaButton` text rows, no number/logo, no current marker in Recent (current *excluded*, focus lands on the first row = the previous channel — the F-PL-2 symptom), ⅓-width panel, different title style, slides from the edge. | Two components (`LiveTvChannelList`/`StreamList` vs `TvChannelListOverlay`) for the same job. |
| L-4 | **Left/Right mean something different on every layer.** Browse: pane switch. Preview panel: Recent↔Favorites toggle *and* reveal row actions (Left on a Recent row reveals ★/🗑, Left on a Favorites row switches to Recent; Right mirrored). Full screen: open a flyout. Flyout: nothing (only Back closes). OSD: move between buttons. | `RowActionsMode.REVEAL_LEFT/RIGHT` + the panel's `onKeyEvent` toggle + `PlayerKeyHandler`. |
| L-5 | **Nothing on screen says what the keys do.** No hint that Left/Right open channel lists, that Up/Down zap, that Left reveals Remove-from-Recent. The first-run hints card (`ControlHintsOverlay`, 7 s) **never shows**: `PlayerScreenState` sets `showControlHints = false` unconditionally (the comment says "based on preferences"; `hints_dismissed` is written but never read) — dead code since at least R-28. The OSD has no Channels / Recent / Guide button. | `PlayerScreenState.kt` init, `TvPlayerControlsOverlay`. |
| L-6 | **OSD:** entry focus on **Favourite** (a toggle — OK-OK favourites the channel by accident); buttons are unlabeled icons (subtitles, quality, ★, stats; audio/chapters when present); auto-hide ≈ 13–15 s from *show*, key presses don't reset it (hid between +11 s and +14 s while focused); once, Right past the last button dropped focus and hid the OSD (seen once, not reproduced); title shown twice top-left (small blue + large); resolution/codec line ("1280x576 AVC") is shown to **everyone** — `TvPlayerControlsOverlay` polls the player for it with no developer-mode check. | `PlayerKeyHandler`, `TvPlayerControlsOverlay`, `PlayerEffects` (`controlsAutoHideTvMs = 15 s`). |
| L-7 | **Zap feedback.** Down: engine goes to `state=NONE` at once, `PLAYING` again after **≈ 2.7 s** (emulator); in between only the stream-info banner changes. No "tuning…" state, no channel number, no now/next in the banner. | `onNextChannel` → `loader.loadStream` → stop + play. |
| L-8 | **Focus tunes the preview** (600 ms settle): browsing a long Favorites list starts a stream on every pause. Not a bug — a decision (was F-LP-2). | `focusedItemFlow` + `collectLatest { delay(600) }`. |
| L-9 | **Back lands on different things.** Full → preview ✓ (current channel row). Preview → browse: focus on the channel that *opened* the preview (3ABN International while Latino was playing) or on the **category row** (News;Public) — never on the channel that was playing. `NAVIGATION_GUIDE.md` says "Live TV follows the playing channel". | Browse is a separate nav entry rebuilt on Back; `NavReturnFocus` key = what was pressed, not what played. |
| L-10 | **Favorites tab with nothing in it** is an empty panel whose only focusable is a Refresh button — a dead end until Left. | Empty state in `StreamList`. |
| L-11 | **Row actions on the left** in the preview (🗑 ★ ⟵ row) but on the right in browse; Remove-from-Recent one Left from a channel. (Also F-LP-1.) | `REVEAL_LEFT`. |
| L-12 | **Preview pane wastes ~45 % of its column** below the video when there is no guide: "LIVE PREVIEW" badge + title, nothing else. No channel number, no category, no key hints. | `LiveTvSplitLayout` preview column. |
| L-13 | **Refresh button in the panel header** is a focus stop above the first row (Up from row 1 lands on it — R5). | `StreamList` header. |
| L-14 | **Two nav entries for one screen.** Browse and preview are separate `CategoryList` entries with separate `CategoryViewModel`s; the preview entry re-resolves the category; Back re-composes browse from scratch (L-9); the entry regression (L-1) is only possible because of this. Mobile keeps one entry with local `dockTarget`/`fullScreen` state and `BackHandler` stopovers. | TV-specific design in `TvNavHost` + `TvCategoryGridScreen`. |
| ✓ | Promote to full screen is seamless on the emulator (picture continuous at 0.5 s and 2 s; surface reconnect only). Entry focus in the preview = current channel row. Back chain full → preview → browse → Home works. Flyout from a category opens on the current channel. | — |

### Target: one list, one panel, one key map

```
BROWSE          [Categories]        | [Channels of <category>]        OK = preview
PREVIEW         [Video 66%          | [ <context list>  34% ]         OK = full screen
                 number · name       |  tabs: Category · Recent · Favs
                 now / next          |  current row marked + focused
                 "OK full screen"]   |
FULL SCREEN      video               | Left/Right = same panel, same tabs, same rows, over video
                                     | Up/Down = zap through the context list
                                     | OK = OSD: [Channels] [Guide] [★] [CC] [Audio] [Quality] [⋮]
```

1. **Context list** (`ChannelContext`): `Category(id)`, `Recent`, `Favorites`, `Search(results)`,
   `Guide(category)`. Set once when a channel is chosen (the list it was chosen from) and carried
   through preview → full screen → zap → flyout. **The preview panel, the zap order and the full
   screen panel are the same list.** Tabs on the panel switch context explicitly (and the zap
   order follows the tab). Recent includes the current channel in every context.
2. **One panel component** `LiveChannelPanel(context, current, onTune, onPromote)` used docked
   in the preview and as the full-screen overlay (slides from the right, 34 % width, same rows:
   number · logo · name · now-playing line, current marked with the P5 "current" style, focus
   opens on current). `TvChannelListOverlay` and the Category/Recent flyout pair are deleted.
3. **One key map** on both preview and full screen: Up/Down = rows (preview tunes after settle,
   see decision 1) or zap (full screen); Left/Right in the panel = **tabs** only; long-press OK /
   Menu = row actions (P3) — no reveal, no mirrored buttons; OK = full screen (preview) / OSD
   (full screen); Back = one layer out. Left/Right with no panel open in full screen = open the
   panel (one key for both; the other is free for the guide later).
4. **OSD**: labelled buttons; first = **Channels** (opens the same panel), then Guide (when EPG),
   ★, CC, Audio, Quality, ⋮ (stats, refresh info). Entry focus on Channels. Any key resets the
   15 s timer. Up/Down while the OSD is up still zap. The info banner shows number · name ·
   now · next; one title, not two; resolution/codec only in developer mode. The dead hints card is
   deleted (its content becomes the one hint line in the preview column, item 6, and the OSD's
   labels).
5. **Zap state**: keep the last frame, overlay "number · name" with a small spinner until
   `PLAYING`; no black. Preview settle 800 ms.
6. **Preview column** below the video: number · name · category, now/next with progress, and one
   hint line ("OK  Full screen   ⋮  Options") — standard Android TV banner content.
7. **Back/focus**: full → preview (video keeps playing) → browse with focus on the **playing
   channel** in the list it came from (or its category row when that list is not on screen) → Home.
8. **Entry**: Home → Live TV opens the preview on the last channel with context = Recent (L-1
   fix); Left from the panel's first tab goes to browse (categories) with the video stopped — the
   same thing Back does.
9. **Empty tab**: non-focusable text ("No favourites yet — hold OK on a channel"), focus stays on
   the tab row.
10. **Structure** (last, optional): one `CategoryList` nav entry per content type; preview and
    full screen are local layers with `BackHandler` stopovers — the mobile model. Kills the
    L-1 class of bug and makes L-9 trivial (browse never leaves composition). Promote stays in
    place (ANR note in `LiveTvSplitLayout` still holds).

### Live TV phases (one commit each, each shippable)

| Phase | Scope | Main files |
|---|---|---|
| LT1 | **L-1 regression**: second push uses `navigate(...)` (as `onSignInRequired` already does, with the same comment). Re-check the docs' claim holds. | `TvNavHost.kt` |
| LT2 | **Context list**: preview panel shows the list the channel was chosen from, with tabs Category / Recent / Favorites; zap order = the same list; Left/Right on rows no longer toggle lists (tabs do); row actions `ON_FOCUS_RIGHT` in both lists until P3 lands, then P3. Empty-tab state (9). | `LiveTvSplitLayout.kt`, `StreamList.kt`, `TvCategoryGridScreen.kt`, `PlayerScreen.kt` (`channelList` param replaces `categoryStreams`/`recentStreams`) |
| LT3 | **One panel**: `LiveChannelPanel` docked + overlay; delete `TvChannelListOverlay` and the two flyouts; Left/Right in full screen open it; Left/Right inside = tabs; Back/outward closes; focus opens on current. | new `LiveChannelPanel.kt`, `PlayerScreen.kt`, `PlayerKeyHandler.kt`, `LiveTvSplitLayout.kt` |
| LT4 | **OSD**: labelled buttons, Channels first + entry focus, Guide when EPG, timer reset on key, Up/Down zap with OSD up, banner content, single title, dev-gated codec line; delete the dead `ControlHintsOverlay` + `showControlHints`/`hints_dismissed`. | `TvPlayerControlsOverlay.kt`, `PlayerKeyHandler.kt`, `PlayerEffects.kt`, `PlayerScreenState.kt`, `ControlHintsOverlay.kt` |
| LT5 | **Zap/tune feedback** (5) + preview column content (6) + settle 800 ms. | `PlayerScreen.kt`, `LiveTvSplitLayout.kt` |
| LT6 | **Back/focus** (7): browse focuses the playing channel; `NavReturnFocus` keyed by playing stream id. | `TwoColumnLayout.kt`, `StreamList.kt`, `TvCategoryGridScreen.kt` |
| LT7 | **Single nav entry** (10) — scheduled right after LT6. | `TvNavHost.kt`, `TvCategoryGridScreen.kt`, `LiveTvSplitLayout.kt`, `docs/NAVIGATION_GUIDE.md` |

Dependencies: LT2 before LT3 (the panel needs the context list). LT3's row actions use P3 (Phase
3 above) — land P3 first or keep `ON_FOCUS_RIGHT` in the panel until it does. LT1, LT4, LT5,
LT6 are independent of each other.

### Decisions (Live TV, 2026-10-03)

See Decisions 3–5 at the end of this plan.

## Shared primitives (new, `tv/.../ui/components/input/`)

| # | Primitive | Fixes |
|---|---|---|
| P1 | `Modifier.tvPane(id, onExitLeft, onExitRight, …)` — `focusGroup()` + `focusRestorer(fallback)` + `focusProperties { exit = { dir -> … } }`. Up/Down never leave the pane at its ends; Left/Right leave only to the declared neighbour pane; re-entry lands on the remembered or **selected** item. | R1, R2 |
| P2 | `rememberPaneFocus(selectedKey)` — remembers last-focused key per pane (saveable), falls back to the selected item, then the first. Works with `LazyListState` (scrolls before requesting, using `requestFocusWithRetry`). | R2, L-3, L-9 |
| P3 | **Row action menu**: long-press OK *and* the Menu key on any item row open the existing `FavoriteContextMenuDialog` (extended with Mark watched / Remove from Recent). Rows lose their hidden trailing buttons; one small "⋮" hint appears on the focused row. | R3, L-11 |
| P3a | Scope of P3: **content rows** (channels, titles, episodes, guide cells) — hundreds of rows, actions secondary. Rows whose actions *are* the row's point and that number a handful (Sources, guide sources in Settings) keep **visible labelled slots** (settings plan T4). Not a contradiction: two row kinds, one rule each. | — |
| P4 | `TvSearchField` — focusable read-only until OK; OK opens the IME; IME Done/Back closes it and keeps focus on the field. Right from the field reaches the trailing button. | R7 |
| P5 | Highlight tokens: focus = existing scale + border + lighter fill; **selected/current** = accent left bar + accent text on the resting container; never a fill or full outline. Applied to `TvInputListItem`, category rows, episode rows, `TvSelectableButton`, `TvSwitchRow`. | R6 (shared with settings plan T-3) |
| P6 | `scripts/tv-focus-walk.sh` — sends a key sequence and prints the focused node's text after each key (the `uiautomator` + `focused="true"` method used for this audit). Takes an expected-sequence file; exits non-zero on mismatch. | verification |

## Screen fixes (using the primitives)

- **Home:** entry focus = first content card (or the Continue Watching card when present);
  header Down → the remembered card, default leftmost (P1/P2 on the card row, `focusProperties
  { down = cardRow }` on the header). Live TV card with 0 categories not focusable (shown dimmed
  with "No channels"). (F-H-1..3)
- **Category screens:** categories and items are two `tvPane`s. Left from items → the
  **selected** category; Right from categories → the remembered item, default first. Up/Down
  stop at list ends. Left from the categories pane goes nowhere (Search stays reachable by Up
  from the top of either pane). Entry focus: the first item when the screen opens on a non-empty
  category, otherwise the selected category — the same rule for Live TV, Movies and TV Shows.
  Refresh moves to the row action menu of the category / to the screen's ⋮ in the header, out of
  the Up path. Hidden ★/✓ buttons → P3. (F-C-1..8)
- **Live TV (preview, full screen, panel):** see the Live TV flows section — target and LT1–LT7.
  The preview panel and the full-screen panel are one `tvPane` (P1) component.
- **Movie details:** labelled action buttons (★ Favourite, ✓ Watched) — text on focus at least;
  Refresh info moves to a "⋮ More" button at the end of the row. Up from the tabs → Play
  (`focusProperties { up = playRequester }` on the tab row). (F-MD-2, F-MD-3)
- **Episodes:** open scrolled so the header stays visible with focus on the continue-watching
  episode when it fits on screen; otherwise show a compact sticky header (title + season +
  "Play next"). Up from the first visible episode → season tabs; **Left from any episode → the
  season tabs/header** (one press back to context). Continue-watching row uses P5's "current"
  style. Episode number column sized for 4-digit numbers. Play label uses the episode title
  ("Play: Getting Into Cirque du Soleil"). Next Up card focusable (plays it) or styled as text.
  (F-E-1..6)
- **Search:** P4. Entry focus on the field without the keyboard when recent searches exist;
  with the keyboard when they don't. (F-S-1, F-S-2)

## Phases (one commit each, each shippable)

| Phase | Scope | Main files |
|---|---|---|
| 1 | P6 focus-walk script + expected sequences for the screens walked here (records today's behaviour; later phases update the expectations). | `scripts/tv-focus-walk.sh`, `scripts/focus-walks/*.txt` |
| 2 | P1 + P2; apply to category screens (Live TV / Movies / TV Shows). | new `TvPane.kt`, `TwoColumnLayout.kt`, `CategoryList.kt`, `StreamList.kt` |
| 3 | P3 row action menu; remove hidden row buttons in category lists and Live preview. | `StreamList.kt`, `LiveTvSplitLayout.kt`, `FavoriteMenuDialog.kt` |
| 4 | Home entry/header focus; dead Live TV card. | `ContentTypeSelectionScreen.kt` |
| 5 | **Live TV flows LT1–LT7** (own table in that section; LT1 is a one-line regression fix and can go first of everything). | see LT table |
| 6 | Movie details + Episodes. | `MovieDetailsScreen.kt`, `EpisodeSelectionScreen.kt` |
| 7 | P4 Search field. | new `TvSearchField.kt`, `SearchScreen.kt` |
| 8 | P5 highlight tokens across lists (lands with or after settings plan T1, which changes `TvSelectableButton` / `TvSwitchRow`). | `TvFocusTokens`, `TvInputListItem.kt`, list rows |
| 9 | Guide round: walk TV Guide + EPG Browser with an indexed guide; fix with P1/P2. | `EpgGridLayout.kt`, `TvEpgBrowserScreen.kt` |

Each phase leaves the app shippable: primitives land with their first user, every screen keeps
all its actions (row actions move into the menu in the same commit that removes the buttons),
and the walk script's expectations are updated in the same commit. What the user has after each:

| Phase | App state after it ships |
|---|---|
| 1 | No visible change; `scripts/tv-focus-walk.sh` records today's focus order per screen. |
| 2 | Category screens: Up/Down stop at list ends, Left/Right move between the two columns and land on the selected/remembered item. |
| 3 | Content rows: long-press OK / Menu opens the actions menu; no more hidden ★/✓/🗑 buttons; "⋮" hint on the focused row. |
| 4 | Home opens on content; header Down returns to the card you left; empty Live TV card dimmed. |
| LT1 | Home → Live TV opens the preview on the last channel again. |
| LT2 | Preview panel shows the list you picked from, with Category / Recent / Favourites tabs; Up/Down in full screen zap through that same list. |
| LT3 | One channel panel in preview and full screen; the two flyouts are gone; Left/Right open it in full screen. |
| LT4 | OSD with labelled buttons, Channels first; keys keep it open; banner shows number · now · next. |
| LT5 | Zap keeps the last frame with a "tuning" overlay; preview column shows channel info and key hints. |
| LT6 | Back from the preview lands on the playing channel in the browse list. |
| LT7 | No visible change (one nav entry for Live TV). |
| 6 | Movie details: Up from tabs → Play; labelled actions. Episodes: header reachable in one press; current episode styled, not focused-looking. |
| 7 | Search: keyboard only on OK; search button reachable. |
| 8 | One "current/selected" style across all lists. |
| 9 | TV Guide / EPG Browser follow the same pane rules. | Each commit updates this plan
and `docs/NAVIGATION_GUIDE.md` (focus rules, long-press/Menu key), `docs/RELEASE_NOTES.md`; the
focus contract goes into `AGENTS.md` as a rule for new TV UI in Phase 2.

## Relation to the settings plan

- Settings plan Part B's focus contract = this plan's rules; `tvPane` (P1) is what the Settings
  two-pane (T3) should be built on. **Do P1/P2 (this plan's Phase 2) before settings T3**, or
  build T3 with them.
- P5 and settings T1 touch the same components — land settings T1 first, P5 extends it.
- Row actions: P3 (long-press menu) is for content rows; Settings' Sources rows keep visible
  labelled slots (settings T4) — see P3a. The settings plan's contract rule 1 and this plan agree.
- Contract rule 4 ("Left = back one level") has one exception, written into both plans: tabbed
  panels (the Live TV channel panel) use Left/Right for tabs; Back leaves the panel.
- Settings finding T-9 ("Back from Edit Source lands on +") was observed on the older build;
  `393a6405` wires Manage Sources' overflow/Edit into `NavReturnFocus`. Re-check with the walk
  script before fixing.

## Decisions (2026-10-03)

1. **P3 row actions: long-press OK + Menu key** open the row action menu (reuses
   `FavoriteContextMenuDialog`); hidden trailing row buttons go away. A small "⋮" hint on the
   focused row only, not a focus stop.
2. **Guide round (Phase 9): OK to trigger a guide refresh on the TV emulator** so TV Guide and
   EPG Browser appear.
3. **Preview tunes on focus** stays, with an 800 ms settle and a visible tuning state (LT5).
4. **Full screen: both Left and Right open the one channel panel** (LT3). The guide gets its own
   OSD button (LT4), not a D-pad key.
5. **LT7 (single nav entry) is scheduled right after LT6.**

## Open

Nothing — all decisions taken.

## Side effects of this audit on the emulator

- `main` (`578f17c7`) installed over the old build with `install -r` (approved; data kept).
- Kid switched iptv → jellyxtream → iptv (back where it was).
- One "Refresh streams" on jellyxtream TV Shows (stray OK).
- Second walk: TV emulator relaunched; ~3 min of channels watched (3ABN International,
  Latino, Proclaim!, 24 Horas Canarias, Catalunya). Kid's Recent on iptv now holds these; no
  favourites, settings or sources changed.
