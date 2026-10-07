# Navigation Guide

Type-safe navigation with Navigation Compose and kotlinx.serialization routes. The routes live in `:core:navigation`; each app has its own nav host: `tv/.../navigation/TvNavHost.kt` and `mobile/.../navigation/MobileNavHost.kt`. Module layout: [design.md](design.md#module-architecture).

## Screen Definitions

`core/navigation/.../Screen.kt`:

```kotlin
sealed interface Screen {
    @Serializable data object ProviderSelection : Screen  // "Sources"
    @Serializable data class AddProvider(val editId: Long = -1L) : Screen  // add, or edit when editId > 0
    @Serializable data object Login : Screen  // in neither nav graph
    @Serializable data object ProfilePicker : Screen  // "Who's watching?"
    @Serializable data class ProfileEdit(val profileId: String) : Screen  // a profile's page (mobile; TV shows it inside Settings)
    @Serializable data object ContentTypeSelection : Screen  // Home
    @Serializable data object Settings : Screen
    @Serializable data class CategoryList(
        val contentType: String, val initialCategoryId: String? = null,
        val initialStreamId: String? = null, val showPreviewPane: Boolean = true,
    ) : Screen
    @Serializable data class EpisodeSelection(
        val seriesId: String, val seriesName: String, val categoryId: String,
        val initialEpisodeId: String? = null,
    ) : Screen
    @Serializable data class MovieDetails(val movieId: String, val movieName: String, val categoryId: String) : Screen
    @Serializable data class Search(val contentType: String, val initialQuery: String? = null) : Screen
    @Serializable data class EpgGuide(val categoryId: String, val categoryName: String, val focusChannelId: String? = null) : Screen  // TV Guide
    @Serializable data class EpgBrowser(val categoryId: String? = null, val categoryName: String? = null) : Screen  // "Search the guide"
    @Serializable data class EpgManagement(val providerId: Long) : Screen  // one source's guide sources
    @Serializable data object SyncSettings : Screen  // Live sync
    @Serializable data object Diagnostics : Screen  // dev mode; also from SafeMode
    @Serializable data object DeviceInfo : Screen  // Settings → About & advanced, everyone
    @Serializable data object SafeMode : Screen  // start destination after a crash loop (SafeMode.isActive)
    @Serializable data object NewerData : Screen  // start destination when providers.db is from a newer build (ProvidersDbGuard.isBlocked)
    @Serializable data class Player(
        val streamId: String, val streamName: String, val categoryId: String, val contentType: String,
        val episodeId: String? = null, val episodeExtension: String? = null,
        val seriesId: String? = null, val seriesName: String? = null,
        val startFromBeginning: Boolean = false,
    ) : Screen
}
```

All IDs are `String` so Xtream (numeric), Jellyfin (UUID), SMB and Local (paths) fit the same routes. `showPreviewPane` is read by TV only (see [Live TV preview / dock](#live-tv-preview--dock-back-stack)).

## Navigation Flow

```
App start
├─ providers.db from a newer build ─→ NewerData (Close / Reset sources)
├─ crash loop (SafeMode.isActive) ─→ SafeMode (Continue / Clear caches / Show diagnostics ─→ Diagnostics)
├─ no source ─→ Settings
├─ TV, a source and 2+ profiles ─→ ProfilePicker ─→ ContentTypeSelection
└─ otherwise ─→ ContentTypeSelection

ContentTypeSelection (Home)
├─→ CategoryList(LIVE_TV | MOVIES | TV_SHOWS)
├─→ EpgBrowser()                         Search the guide button, when the EPG index is ready
├─→ Search("ALL")
├─→ ProfilePicker                        header avatar
├─→ Settings
├─→ AddProvider(editId)                  the active source needs a sign-in
└─→ Continue Watching card ─→ EpisodeSelection(initialEpisodeId) | Player (an episode) | MovieDetails

CategoryList
├─  Live TV: preview + full screen (TV) or dock + full screen (mobile) are layers, not entries
├─→ EpgGuide(category)                   Live TV header button
├─→ EpgGuide(list, focusChannelId)       TV full screen: the OSD's Guide
├─→ Search(contentType)
├─→ MovieDetails                         Movies
├─→ EpisodeSelection                     TV Shows (a Recent series card: with initialEpisodeId)
└─→ Player                               a Recent episode card

Search(contentType | "ALL")
├─→ CategoryList(LIVE_TV, category, channel)   live result: opens on that channel
├─→ MovieDetails | EpisodeSelection
└─→ CategoryList(contentType, category)        category result

EpgGuide ──→ CategoryList(LIVE_TV, category, channel)   channel cell, or Watch channel in a programme's details
         └─→ EpgBrowser(category)                       its Search
EpgBrowser ─→ CategoryList(LIVE_TV, category, channel)   Watch
           ├─→ EpgGuide("recent", Recent)                 TV Guide button (source with live channels)
           └─→ EpgManagement(active source)               Guide sources button (source with live channels)
AddProvider(editId) ─→ EpgManagement(editId)             Guide sources row (source with live channels)

MovieDetails ─────→ Player | CategoryList(MOVIES, category) | MovieDetails (related title)
EpisodeSelection ─→ Player | CategoryList(TV_SHOWS, category) | EpisodeSelection (related title)

Settings
├─→ ProviderSelection ─→ AddProvider() | AddProvider(editId) | EpgManagement(id)   Manage sources
├─→ ProfileEdit(profileId)               a profile's row (mobile; TV: a page in the Profiles pane)
├─→ SyncSettings
├─→ DeviceInfo                           About & advanced
├─→ Diagnostics                          dev mode
└─→ ContentTypeSelection                 Switch to this profile (back stack cleared)

ProfilePicker ─→ ContentTypeSelection (back stack cleared)
```

On both platforms Home opens the only content type straight away when the active source has just one, once per nav host lifetime, so Back still lands on a usable Home.

### Back-Stack Rules

1. **Pushes use `navigateOnce`** (`core/navigation/.../NavExtensions.kt`): it navigates only while the current entry is RESUMED, so a D-pad auto-repeat or a double tap during a transition can't push a screen twice. The sign-in prompt and the profile / external switches below use plain `navigate`, since they fire while Home may still be entering.
2. **Start destination**: `NewerData`, then `SafeMode`, are checked before anything else. Both skip the nav host's `initializeStartup()` (provider lookups, legacy-credential migration, profile count): `NewerData` because nothing may open `providers.db`, `SafeMode` because that work may be what keeps crashing. Back on either leaves the app; `SafeMode`'s Show diagnostics pushes `Diagnostics` and Back returns to it. Continue / Reset sources restart the process. See `docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md` → R-10, R-01. Otherwise Home when a source exists (TV: through `ProfilePicker` when there are two or more profiles), else `Settings`.
3. **Back on the root**: on TV, Back on Home does nothing, and on Settings when it is the start destination; on mobile it leaves the app.
4. **Source switch, profile switch, live-sync switch**: navigate to Home and clear the whole back stack (TV `popUpTo(navController.graph.id) { inclusive = true }`; mobile's source switch `popUpTo(graph.startDestinationId) { inclusive = true }`), so no screen holding the previous source's (closed) repository survives underneath. A profile switch can itself change source: the device moves to the source the new profile last picked when it differs (`docs/plans/archive/20261002_profile-last-provider-plan.md`). Live sync does the same when another device deletes this device's profile or source (`AppContainer.externalSwitches`).
5. **Remote Stop (Live sync)**: when another device of the sync group stops this device's playback, whichever playing screen is up — TV `TvPlayerScreen` or the `LiveTvSplitLayout` preview / full screen, mobile `MobilePlayerScreen` or the Live TV dock (docked or full screen) — finalises the session as Back does, stops, and pops back to Home (`popBackStack(Screen.ContentTypeSelection, inclusive = false)`, the screens' `onHome`) with a "Playback stopped from <device>" toast. See `RemoteStopEffect` and `docs/plans/archive/20261001_live-sync-now-playing-plan.md` → Remote Stop.
6. **Section-root button** (sources-guide-profiles plan D4, P6): a screen 4 or more entries above Home (`HOME_BUTTON_MIN_DEPTH = 4`, `core/navigation/.../SectionRoot.kt`) shows a button back to its section's root — the entry directly above Home: Movies, TV Shows, Live TV, Search, Search the guide, TV Guide, Settings, or a title opened from Home's Continue Watching — labelled with that screen's name. Depth is counted on `navController.currentBackStack` from the last `ContentTypeSelection` entry (the graph's own entry is below it); with no Home on the stack (Settings as the start destination) there is no button. `sectionRootFor(navController, entry)` (`core/ui/.../navigation/SectionRoot.kt`) gives each screen a `SectionRoot?` (label + onClick, null = hidden) from the stack up to its own entry; `NavController.popUpToEntry(root)` pops one entry at a time until the root is on top — not `popBackStack<Screen.CategoryList>()`, which would stop at the nearest of several category lists — so the root keeps its saved state (category, scroll, search results, the row focused) and Home stays one Back away. It is ignored while the current screen is still entering, as `navigateOnce` is. Example: Home → Movies → film → related film → its category shows "Movies" on the category screen; pressing it lands on Movies where browsing started, with only Home behind it. The screens that can sit that deep take it — details, episodes, category lists, Search, TV Guide, Search the guide, guide sources; never the player, and not the Live TV preview layer (TV) or full screen (mobile), which don't draw the header. Settings (only ever pushed from Home), Sources, Edit Source, Live sync, Device info and Diagnostics never reach depth 4 and have no slot. TV: the last button of the header row (the hero's action row on details and episodes), reached like the other header buttons (Up from the content; in the TV Guide header, Right from Refresh); it never takes focus itself. Mobile: the top bar's last action, an icon whose content description is the label; on Live TV it stops the dock first, as Back does.

### Live TV Preview / Dock Back-Stack

Live TV always shows a channel playing alongside the browse list ([FEATURES.md](FEATURES.md#live-tv)). Home → Live TV is one `CategoryList` entry and the preview is a layer on it, so Back never skips past the browse screen and out of Live TV.

- **TV** (`TvCategoryGridScreen.kt`, UX overhaul plan Part II LT7): Home pushes `CategoryList(showPreviewPane = false, initialStreamId = <last channel>)` with `popUpTo(ContentTypeSelection)` — the browse entry. The preview (`LiveTvSplitLayout`), with full screen promoted in place inside it, is a layer over browse: it is open while `livePreviewChannelId` (`rememberSaveable`) names the channel it opened on — the last channel on entry (no last channel: the entry opens on browse), or the channel OK was pressed on in browse. Back is a chain of stopovers in `LiveTvSplitLayout`'s `BackHandler`: full screen → preview (`fullScreen -> false`, the video keeps playing), then preview → browse (`livePreviewChannelId -> null`; the preview's player is stopped and released as it leaves composition), and only then does Back reach the nav host and pop the entry to Home. Left on the first tab of the docked panel is the preview → browse step too (the same `onBack`; Live TV target item 8), so browse sits to the left of the preview for the D-pad as well; in full screen it is not, Left there being the panel's tab key. Browse is not composed under the preview — its rows would stay in the focus tree, and its focus effects run, behind an opaque preview or the full-screen player — so it is rebuilt on Back like a destination, its saved state (pane memories, scroll) kept in a `SaveableStateHolder`, with the same `CategoryViewModel` the preview used. The preview's own ViewModels (`PlaybackViewModel`, `StreamLoaderViewModel`) live in a store of the layer's (`LivePreviewViewModels`, held by the entry): they survive a trip to the TV Guide and back and an activity recreate, and are cleared when the layer closes, so a channel previewed for less than the watch delay is not written into Recent afterwards. **Entries opened on one channel push their own entry:** Home's Live row (with `initialCategoryId` = the virtual `favorites` or `recent`, so the context list is the one the card came from), Search (a Live TV result), the EPG Browser and the TV Guide (a channel or programme — including the guide opened from full screen's Guide button) push `CategoryList(initialCategoryId, initialStreamId)` with `showPreviewPane = true` (the default): that entry is the preview alone, with no browse layer, and Back from it pops back to the screen that opened it. After process death the layer comes back as it was: on the list it was on (kept with the screen's saved state and put back if the recreated `CategoryViewModel` lands on Recent) and on the channel last playing (`livePlayingChannelId`, saveable, cleared when the layer closes); the preview's seed waits for a list that has the channel.
- **Mobile** (`MobileCategoryListScreen.kt`): the dock and its full screen are local state (`dockTarget`, `fullScreen`), not routes. The dock seeds itself on entry — `initialStreamId` when given, else the last-played channel when the first list shown has it — so Live TV rarely opens on a bare list. Two `BackHandler`s give the stopovers: `fullScreen -> false` (full screen collapses to the dock), then `dockTarget -> null` (the dock stops and clears to the bare list). Only a third Back reaches `onBack` and leaves the screen. The toolbar's Back, Search and TV Guide leave without those handlers, so each stops the dock first (the engine is Activity-scoped and would keep playing behind the next screen). Search, the TV Guide and the EPG Browser push their own `CategoryList` entry on the chosen channel, as on TV.

When touching either flow, keep "Back always has a real stopover before exiting" — it is why both are more than a single `navigate()` call.

**The context list (TV, `ChannelContext`, UX overhaul plan Part II LT2).** A channel carries the list it was chosen from: the preview layer shares browse's `CategoryViewModel`, whose selection is the *browsed* list (the real category, or the virtual `recent` / `favorites`) — not the channel's own category; an entry opened on one channel from Search or a guide has that channel's category as its `initialCategoryId` — and `LiveTvSplitLayout` turns it into a `ChannelContext` (`Category(id, name)`, `Recent`, `Favorites`; Home → Live TV is `Recent`), held in `rememberSaveable`. That one list is what the channel panel shows and what Up/Down zap through in full screen (`neighborChannel` over it). The panel (`LiveTvChannelPanel`) is a `TvSectionTabs` row — the category tab, labelled with its name and present only when there is one, then Recent and Favorites, with the Refresh icon at the row's end — over a `StreamList` with no header and its rows as a `tvPane` with no neighbours: Up from the first row lands on the selected tab (the row is a focus group whose `onEnter` redirects there), Left/Right on the tabs switch the list (focus follows selection, so the context changes as focus moves) — Left on the first tab leaves the preview for browse, as Back does (`onLeftFromFirstTab`, docked panel only) and Right on the last reaches Refresh — Left/Right on a row switch to the previous / next tab too, focus staying in the rows (the new list's current channel, else its first row, scrolled into view; `PaneFocusState.focusEntryInNewList`) — Left on a row of the first tab leaves the preview like Left on that tab, Right on a row of the last tab does nothing — and Down from the tabs enters the rows on the current channel when the list has it, else the first row. An empty tab shows a line of text and nothing focusable, so focus stays on the tab; Refresh sits at the end of the tab row, not between the tabs and the rows. None of these moves changes the channel: in the docked panel **OK on a row that isn't playing plays it in the preview, and OK on the row that is playing goes full screen** (`onStreamPromote`; sources-guide-profiles plan P8, replacing the overhaul's "preview tunes on focus"). `scripts/focus-walks/live-tv-preview.txt` walks it.

**One panel in full screen (TV, LT3).** The same `LiveTvChannelPanel` opens over the video in full screen — `PlayerScreen`'s `channelPanel` slot, filled by `LiveTvSplitLayout` — sliding in from the right at the docked panel's 34 % width: same tabs, rows, current-channel marking and row actions (long-press OK / Menu). With neither the panel nor the OSD showing, **Left and Right both open it**; it opens on the tab used last, on the playing channel's row when the list has it, else the first row (the tab row while the list loads). A remembered tab with no rows (no favourites, or the last one removed) is not reopened: the panel opens on the first tab that has the playing channel instead — the category it came from, else Recent — on its row, and that tab is the zap order from then on. Inside, the keys are the docked panel's: Left/Right on the tab row switch tabs — which switches the `ChannelContext`, so the zap order follows — Up/Down move between rows, Left/Right on a row switch tabs as in the docked panel (Left on a row of the first tab does nothing here); OK on a row tunes that channel (the same full `loadStream` on the same loader/engine as a zap) and closes the panel; focus alone never tunes in full screen. **Back closes the panel** and focus returns to the player; Right (the outward key) does not, since on the tab row it is the next tab. Focus stays inside while it is open: the panel is a focus group whose exit is cancelled while `showChannelPanel` is true (not while it animates out, so the player can take focus back), Back is taken in `PlayerScreen`'s root `onPreviewKeyEvent`, and the player's key handler leaves the D-pad and OK to it (`isModalOpen`). Up/Down/OK with the panel closed zap and open the OSD as usual. `scripts/focus-walks/live-tv-fullscreen.txt` walks it.

**The OSD (TV, `TvPlayerControlsOverlay`, LT4).** OK opens it: one row of buttons, each with its label beside the icon. Live: **Channels** (opens the same channel panel and hides the OSD; present only where there is a panel, i.e. the Live TV full screen), **Guide** (GD5: the TV Guide of the list being zapped through — the category, Recent or Favourites tab — with entry focus on the playing channel's row, via `Screen.EpgGuide(focusChannelId)`; only when the source has a guide, native EPG or an indexed one; Back from the guide returns to full screen on that channel — `LiveTvSplitLayout` keeps `fullScreen` and the channel in saved state across the round trip), ★ Favorite / Favorited, Subtitles, Audio, Quality (each only when the stream has the tracks), then **⋮ More**, which shows Stats after it in the row (focus moves onto Stats; OK on More again hides it). VOD: the same row without Channels and Guide, with Chapters (when the file has them) before Favorite and Next episode (from 80 % of an episode that has a next one) before More, under the centre Play/Pause. Focus opens on **Channels** on live — a stray second OK opens the panel, never favourites — on Play/Pause on VOD, and on More otherwise (VOD while buffering, live without a panel); a picker returns focus to its button when it closes. Left/Right move along the row and stop at its ends (the row is a focus group whose Left/Right exit is cancelled). On live, **Up/Down still zap with the OSD up** (the OSD stays up, focus goes back to Channels on the new channel); on VOD they move between the row and the progress bar. **Any key while the OSD is up restarts its 15 s auto-hide** (`controlsKeyTick`, counted in `PlayerScreen`'s root `onPreviewKeyEvent`). The live banner is in the panel above the row: LIVE · channel name (the title shows once, not again top-left), then "Now: …" with the programme's progress bar and "Next: …" when the guide has them. The resolution/codec line top-left is developer mode only (`PlayerScreenState.isDeveloperMode`, from `AppSettings.isDevMode`). The channel number goes before the name once one reaches the player. `scripts/focus-walks/live-tv-osd.txt` walks it.

**Tuning feedback and the preview column (TV, LT5).** Focus never tunes the preview (P8); OK does. From the moment a channel becomes the target — OK on another row in the preview, Up/Down or a panel pick in full screen — until the engine reaches Playing on *that* channel, `TvTuningOverlay` shows "Tuning · <channel>" with a small spinner, centred over a dimmed picture (in the preview pane, compact; in full screen, under the banner and the panel). It is derived in `LiveTvSplitLayout` (`tuningName`: the loader has resolved the target, the engine's stream URL is its URL, and the state is Playing) and passed to `PlayerScreen` as `tuningChannelName`, where it replaces the centre loading spinner; a later rebuffer of the same channel is not a tune and shows the plain spinner. It hides on a loader or playback error, which keep their own UI. The last frame does not survive a zap: the engine's `stop()` + new media source clears the tracks and `PlayerView` closes its shutter (`keepContentOnPlayerReset` stays at its default, off), so the picture goes black until the new channel's first frame — the overlay sits on that black. Below the preview video: channel name, its category, "Now: …" with the programme's progress and "Next: …" when the guide has them (only the target's — not the previous channel's while a retune resolves), and at the bottom one hint line, "OK  Play · OK again  Full screen · Hold OK  Options". No badge marks the layer, and no channel number yet (not in the metadata).

### TV Back on Detail Screens: intercept at `onPreviewKeyEvent`

On TV, `BackHandler` alone is **not** enough on a screen where a `Button`/`Surface` holds focus. Confirmed on a real Shield: the first Back press reaches Compose's key dispatch, but something downstream marks it handled before it reaches the `BackHandler` / `OnBackPressedDispatcher` bridge — so the press only clears focus, the D-pad goes dead, and it takes a second press to navigate. (`androidx.tv:tv-material`'s `Surface` is not the culprit — it only intercepts `DPAD_CENTER`/`ENTER`.)

`:tv`'s `MovieDetailsScreen` and `EpisodeSelectionScreen` therefore intercept Back in `onPreviewKeyEvent` on their root `LazyColumn`. Preview dispatch runs top-down, before any descendant sees the event, so it wins regardless of what swallows it further down — the same pattern `TvDpadEscape.kt` uses for the analogous Up/Down-in-a-text-field problem. The `BackHandler`s remain as an inert fallback.

Use this pattern for any new TV screen whose base state has focusable buttons and a Back action — and for overlays/panels that *replace* that root (the episode detail panel in `EpisodeSelectionScreen` intercepts on the screen's root `Box` while it is open, since the `LazyColumn` with the interceptor isn't composed then).

The player's "Up next" card (Play next episode automatically) follows it too: while the card is up over the playing episode, `PlayerScreen`'s root `onPreviewKeyEvent` takes Back — it hides the card and playback carries on, without leaving the player. While focus is in the card its buttons get the D-pad and OK; when the card is up but not focused (OSD hidden), Down moves onto it; otherwise the player's own key handling runs as usual. The card takes focus on "Play now" when it appears. On mobile a `BackHandler` hides it.

## Nav Hosts

Both hosts look up the source on start (`initializeStartup()`: provider count, one-time migration of legacy `AccountManager` credentials into Room; TV also counts profiles), show `AppLoadingScreen` meanwhile, and restore the Xtream session into `AuthViewModel` (`:core:data`, a holder for the session; nothing clears it — there is no logout).

| | TV (`TvNavHost.kt`) | Mobile (`MobileNavHost.kt`) |
|---|---|---|
| Components | `androidx.tv.material3`, D-pad focus | Material3, touch |
| Transitions | Fade in / out, `CinemaAnimation.navTransitionMs` (300 ms), every screen | Slide left + fade on push, slide right + fade on pop; `Player` slides up in and down out while the other screen only fades |
| Profile picker at launch | With two or more profiles | Never (header avatar only) |

## TV Focus

Each TV screen puts focus somewhere visible when it or a panel appears and returns it to where the user was. TV-safe padding: 56dp horizontal, 32dp vertical ([design.md](design.md#safe-margins-tv-overscan)).

- **Back from details** (movie/series) focuses the row that was opened — `StreamList` remembers it per category (`rememberSaveable`), falling back to the last played item; Live TV follows the playing channel. **Back from the Live TV preview** (UX overhaul plan Part II LT6) lands on the channel the preview was playing when it was left — after any retune or zap, not the row that opened it: `LiveTvSplitLayout` reports its channel each time it changes (`onPlayingChannel`), `TvCategoryGridScreen` keeps it, and when Back closes the preview layer (LT7) it hands it to browse as `returnedLiveChannelId` (`TwoColumnLayout`'s `returnedPlayingId`) — a plain `remember`, so a later return from Search or the TV Guide rebuilds browse without it. Closing the layer also refreshes the last-played item and watch state (`refreshLastPlayedItem`, `refreshWatchStateOnResume`) and reloads Recent when that is the browsed list, so a channel the preview recorded is in it. `TwoColumnLayout` then uses it in place of `lastPlayedItemId` (which only moves after the watch delay) as the items pane's selected row — so the pane remembers it and a later Right lands there too, and the row is marked current. When the list on screen does not have it (it was played from another list, or Recent has not recorded it yet), focus goes to the selected category row instead and the list does not take focus later on its own. Search / TV Guide returns use `NavReturnFocus`. `scripts/focus-walks/live-tv-back.txt` walks it.
- **Back to any other screen** focuses the control that navigated away, at the scroll position its list had — Home's section tiles, header buttons and row cards; Settings' Manage sources, Live sync, Device info and Diagnostics rows and its picker rows; Edit Source's Guide sources row; Search the guide's TV Guide and Guide sources buttons; Search results; the category screen's Search and TV Guide buttons; a details screen's Start Over / Category button / related title; TV Guide programme and channel cells and its Search button (Back from "Search the guide"); EPG Browser airing rows; Sources' Add / Guide / overflow buttons. Navigation Compose rebuilds a screen on Back with nothing focused, and Compose then focuses the first focusable (the source chip, the top Settings row). The pattern (`tv/ui/components/input/NavReturnFocus.kt`): `val returnFocus = rememberNavReturnFocus()` (saveable, so it survives the round trip); in the control's `onClick`, `returnFocus.leaveFrom(key, listState)` before navigating; `Modifier.navReturnFocusTarget(returnFocus, key)` on the control; `NavReturnFocusEffect(returnFocus, listState, fallback) { key -> /* wait for data, scroll an inner row */ }`. The effect runs when the screen is RESUMED again (after the pop transition and the screen's own first-open effects), restores the list position, then `requestFocusWithRetry` — once: the key is cleared whether or not it landed. A screen's own first-open focus checks `returnFocus.key == null` (or `isReturn`, when it can fire again after the hand-back) so it doesn't fight it. Back from the player lands on Play / the resume card, which those screens already focus.
- **Closing the episode detail panel** focuses that episode's card (the tab row if Next/Previous crossed into another season).
- **Episodes list:** Left from any episode, or Up from the first, goes to the episodes header (the selected season tab, else Play next, else the section tabs); Right on an episode does nothing. Arrows never change season — that is the season tabs' job (UX overhaul plan Part II Phase 6).
- **TV Guide** opens on the programme on air now in the first channel that has one (separator rows are not channels) — or, opened from the player's Guide button, in the playing channel's row; Up/Down keep the time across rows, Left/Right step by programme, Up from the first row reaches the header buttons, Down from the header returns to the cell you left. OK on a channel opens its preview; OK on a programme opens its details panel (title, channel, day and time, description; Watch channel opens the preview, Close) with focus on Watch channel, and Back or Close returns focus to the cell (GD6). Long-press OK or Menu on either cell opens the channel's row actions (below). `scripts/focus-walks/guide.txt` walks it.
- **Home (TV home overhaul plan, Phase 3):** opens on the first Continue Watching card, else the first Live row card, else the first section tile that can take focus (an empty Live TV tile cannot). Each row of cards is a `HomeRow` (`feature/contentselection/components/HomeRow.kt`): `focusRestorer` with the first card as fallback, so Up/Down into a row land on the card it had last; Left on its first card and Right on its last cancel. The tile row is a `focusGroup` with a `focusRestorer` too, so Up from a row comes back to the tile focused last, and Left/Right cancel at the row's first and last focusable tile (with Live TV dimmed, Left from Movies used to fall into the shelf). Down from the header goes to the tile focused last.
- **End of a row:** Home's rows cancel Right on their last card (`focusProperties { right = FocusRequester.Cancel }`) so focus doesn't escape to the top bar. The last tab of a `TvSectionTabs` row (movie and series details, the Live TV channel panel) does the same unless the row ends in a control (`endFocusRequester` — the panel's Refresh): Right on a movie's only tab (Details) stays put instead of jumping up to Play.
- Land focus from a `LaunchedEffect` with `requestFocusWithRetry` (`tv/ui/components/input/FocusRetry.kt`): it retries each frame (about 0.5 s) on the Boolean result of `requestFocus(FocusDirection.Enter)`, then an optional `fallback`. Never `try { requestFocus() } catch (IllegalStateException)` or `runCatching`: since Compose 1.10 an unattached target logs and returns `false` instead of throwing, so those catches retry nothing (R-05). Act on the result — e.g. `StreamList` marks a Back-restore handled only when it returned true. `scripts/check-focus-retry.sh` (CI) rejects the old pattern.
- **Error states** use `TvErrorState` (`tv/ui/components/TvErrorState.kt`): focus lands on Retry on entry, Back is taken in `onPreviewKeyEvent` when the screen has one, and `RetryWhenOnline` retries once when the network comes back (R-15).

### Panes (`tvPane`)

Two-column TV screens (Live TV browse, Movies, TV Shows: `TwoColumnLayout`) are built from two panes, `tv/ui/components/input/TvPane.kt` (UX overhaul plan, Part II P1/P2), so that moves follow the layout's intent rather than Compose's geometric search (which sent Down at the end of the items into a category, and Left from a category to Search).

- `val pane = rememberPaneFocus()` per column in the screen; the list calls `pane.bind(selectedKey, firstKey, listState, indexOf)` every composition; each row's card takes `Modifier.paneItem(pane, key)`; the list's container takes `Modifier.tvPane(pane, exitLeft = …, exitRight = …)`.
- **Left/Right are taken by the pane, not by geometry:** they go to the neighbour pane named in `exitLeft` / `exitRight`, or nowhere. **Down at the end of a pane stays put; Up at the top leaves** to whatever is above — the Refresh icon and the Search / TV Guide header buttons stay reachable from the first row.
- **Entering a pane lands on its remembered row** (the last one focused — `rememberSaveable`, so it survives Back), else the selected row, else the first; a categories pane binds with `preferSelected = true`, so Left from an item always lands on the category being browsed. A new selection (the row opened, the channel playing) becomes the memory, which is how Back from details / the player lands on the right row. A remembered row that is scrolled out of composition is scrolled to, then focused with `requestFocusWithRetry`.
- Entry focus on open: the selected category while the items load, then the items' entry row once they are there (the last played / opened item when it is in the list, else the first); an empty category keeps the category. OK on a category keeps focus on the category; Right enters its items.
- Programmatic focus (`requestFocusWithRetry`, `NavReturnFocus`) passes through a pane untouched; only D-pad moves are redirected (`focusProperties { onEnter }` on the group, `onKeyEvent` for Left/Right, `onExit` + `cancelFocusChange()` for Down). `focusRestorer` is not used: it defines the same `onEnter` and the outer definition wins, and it cannot express the selected-row fallback.

The same panes build TV Settings (rail and pane), Edit Source and the TV Guide header. In TV Settings a row can open a page in place of the group's rows — a picker, Guide data maintenance, or a profile's page (`SettingPicker.PROFILE`, `ProfileEditPane`): Left or Back leaves it (a profile's page unsaved, like Cancel), with focus back on the row that opened it, or on the group's first row after the profile was deleted. The profile page takes Left and Back in `onKeyEvent`, after the focused control, so the name's text field keeps them while it is being edited; its colour opens a picker in the same place.

### Row actions (long-press OK / Menu)

A content row (a channel, title or episode in `StreamList`, a category in `CategoryList`, the Live TV channel panel's rows, a TV Guide channel or programme cell — acting on its channel) is one focus stop: OK does the row's job, and **long-press OK or the Menu key** opens its action menu (`FavoriteContextMenuDialog`: Add/Remove favorite first and focused, Mark watched on Movies / TV Shows, Remove from Recent last, Cancel) — UX overhaul plan Part II P3. In the TV Guide, Remove from Recent is offered only in the Recent guide; after it (or Remove favorite in the Favourites guide) the rows reload and focus lands on the row that took its place. A row shows a small "⋮" at its end while focused as the hint (TV Guide: on the channel cell only, programme cells being too narrow); it is not focusable. Content rows have no trailing ★ / ✓ / 🗑 buttons, so Right from a category goes straight to its items and Right from an item goes nowhere. The rule is for content rows only (P3a): rows whose few actions are the row's point, such as Sources, keep visible labelled buttons. So do guide sources: each row is its selection toggle, then Refresh / Edit / Auto-refresh / Delete under it; Auto-refresh drills into a picker in place of the list (`SettingsPickerPane`, focus on the current interval), and OK, Left or Back closes it with focus back on that row's Auto-refresh button (`epg-management.txt`). On the phone the guide source card has the same Auto-refresh button, opening a picker dialog. An episode card on the episodes screen is not a `StreamList` row: long-press OK there toggles watched.

### Search fields (`TvSearchField`)

A focused Compose text field opens the keyboard, and on TV the keyboard then takes every D-pad key, so the Search and EPG Browser query fields are `TvSearchField` (`tv/ui/components/input/TvSearchField.kt`, UX overhaul plan Part II P4): at rest the field is an ordinary focus stop showing the query or placeholder, so Up/Down/Left/Right move focus as anywhere else and Right reaches the clear (×) and search buttons beside it. **OK** turns it into the text field, focused, keyboard open; the IME's Search/Done action submits, **Back** closes the keyboard (submitting when the text changed), and focus comes back to the resting field. Back is taken with `onInterceptKeyBeforeSoftKeyboard` on the text field, before the keyboard can swallow it to hide itself. The editing state is hoisted: both screens open on the field without the keyboard when there are recent searches (Down reaches them) and straight into the keyboard when there are none; a return from a result uses `NavReturnFocus`. Use it for any new TV text field the user reaches with the D-pad on the way to something else. `scripts/focus-walks/search.txt` walks it.

## Adding a Screen

1. Define it in `:core:navigation`:

   ```kotlin
   @Serializable
   data class NewScreen(val someParam: String) : Screen
   ```

2. Add it to both nav hosts:

   ```kotlin
   composable<Screen.NewScreen> { backStackEntry ->
       val screen = backStackEntry.toRoute<Screen.NewScreen>()
       NewScreenComposable(
           someParam = screen.someParam,
           onBack = { navController.navigateUp() },
       )
   }
   ```

3. Navigate with `navController.navigateOnce(Screen.NewScreen(someParam = "value"))`, then add it to the tree above.
4. If it can sit 4 or more entries above Home, give it the section-root button (Back-Stack Rule 6): a `sectionRoot: SectionRoot? = null` parameter drawn with `SectionRootButton` (TV, last in the header row) or `SectionRootAction` (mobile, last top-bar action), passed `sectionRoot = sectionRootFor(navController, backStackEntry)` by both nav hosts; and if it can be a section's root, a label in `sectionRootLabel`.

Building and deploying: [RUN_GUIDE.md](RUN_GUIDE.md).
