# Navigation Guide - Type-Safe Navigation

Complete guide for the type-safe navigation system using kotlinx.serialization.

## Module Structure

```
:core:navigation    → Screen sealed interface definitions
:core:ui            → ProviderViewModel, shared ViewModels
:core:network       → Room database, ProviderRepository, provider implementations
:mobile             → MobileNavHost (Material3 transitions)
:tv                 → TvNavHost (TV Material3, D-pad focus)
```

## Screen Definitions

Located in `:core:navigation/Screen.kt`

```kotlin
sealed interface Screen {
    @Serializable data object ProviderSelection : Screen
    @Serializable data class AddProvider(val editId: Long = -1L) : Screen
    @Serializable data object Login : Screen  // Legacy, not in nav graph
    @Serializable data object ProfilePicker : Screen  // "Who's watching?"
    @Serializable data object ContentTypeSelection : Screen  // Home
    @Serializable data object Settings : Screen
    @Serializable data class CategoryList(
        val contentType: String, val initialCategoryId: String? = null,
        val initialStreamId: String? = null, val showPreviewPane: Boolean = true
    ) : Screen
    @Serializable data class EpisodeSelection(
        val seriesId: String, val seriesName: String, val categoryId: String,
        val initialEpisodeId: String? = null
    ) : Screen
    @Serializable data class MovieDetails(val movieId: String, val movieName: String, val categoryId: String) : Screen
    @Serializable data class Search(val contentType: String, val initialQuery: String? = null) : Screen
    @Serializable data class EpgGuide(val categoryId: String, val categoryName: String, val focusChannelId: String? = null) : Screen
    @Serializable data class EpgBrowser(val categoryId: String? = null, val categoryName: String? = null) : Screen
    @Serializable data class EpgManagement(val providerId: Long) : Screen
    @Serializable data object CellularBufferSettings : Screen  // Dev mode only
    @Serializable data object SyncSettings : Screen  // Settings → Live sync
    @Serializable data object Diagnostics : Screen  // Dev mode: recorded crashes, process exits; also from SafeMode
    @Serializable data object SafeMode : Screen  // Start destination after a crash loop (SafeMode.isActive)
    @Serializable data object NewerData : Screen  // Start destination when providers.db is from a newer build (ProvidersDbGuard.isBlocked)
    @Serializable data class Player(
        val streamId: String, val streamName: String, val categoryId: String, val contentType: String,
        val episodeId: String? = null, val episodeExtension: String? = null,
        val seriesId: String? = null, val seriesName: String? = null,
        val startFromBeginning: Boolean = false
    ) : Screen
}
```

**Note:** All IDs are `String` (not `Int`) for compatibility across Xtream (numeric), Jellyfin (UUID), SMB, and Local providers.

### Usage

```kotlin
// Navigate to content type selection
navController.navigate(Screen.ContentTypeSelection)

// Navigate to category list
navController.navigate(Screen.CategoryList(contentType = "LIVE_TV"))

// Navigate to player with parameters
navController.navigate(Screen.Player(streamId = "12345", streamName = "CNN", categoryId = "1", contentType = "LIVE_TV"))

// Navigate to provider management
navController.navigate(Screen.ProviderSelection)
navController.navigate(Screen.AddProvider(editId = 5L))  // Edit provider with ID 5
```

## Navigation Flow

```
App Startup
├─ providers.db written by a newer build → NewerData (Close / Reset sources; nothing else runs)
├─ Last 3 launches died within 30 s, inside 10 min → SafeMode (Continue / Clear caches / Show diagnostics)
├─ No provider configured → Settings
├─ Provider configured, TV with more than one profile → ProfilePicker → ContentTypeSelection
└─ Provider configured otherwise (mobile always) → ContentTypeSelection

ContentTypeSelection (Home; ProfilePicker also opens from the header avatar)
├─→ EpgGuide("recent", Recent) ["TV Guide": calendar icon, when Live TV has a guide]
│     └─→ EpgBrowser(category) ["Search the guide", from the guide's Search]
├─→ EpgBrowser ["Search the guide": book icon, visible when EPG indexed]
├─→ Search("ALL") [Global Search]
│     ├─→ Player (if result is LIVE_TV)
│     ├─→ MovieDetails → Player (if result is MOVIES)
│     └─→ EpisodeSelection → Player (if result is TV_SHOWS)
├─→ CategoryList(LIVE_TV)  [TV: one entry; the preview and full screen are layers on it — see Live TV Preview / Dock below]
│     ├─→ Player (direct)
│     ├─→ Search(LIVE_TV)
│     └─→ EpgGuide (TV Guide: category header; TV full screen: the OSD's Guide, on the playing channel)
│           └─→ EpgBrowser(category) (its Search; Back returns to the guide)
├─→ CategoryList(MOVIES)
│     ├─→ MovieDetails → Player
│     └─→ Search(MOVIES)
├─→ CategoryList(TV_SHOWS)
│     ├─→ EpisodeSelection → Player
│     └─→ Search(TV_SHOWS)
└─→ Settings
      ├─→ ProviderSelection
      │     ├─→ AddProvider (new)
      │     └─→ AddProvider(editId) (edit)
      ├─→ EpgManagement(providerId)
      ├─→ SyncSettings (Live sync)
      ├─→ CellularBufferSettings (dev mode only, mobile)
      └─→ Diagnostics (dev mode only)
```

### Global Search Routing

Global search (`Screen.Search("ALL")`) is accessible from the home screen (`ContentTypeSelection`). Because results can come from different repositories, navigation from a search result is dynamically routed based on the result's `contentType`:

- **Live TV**: Routes directly to `Screen.Player`.
- **Movies**: Routes to `Screen.MovieDetails`.
- **TV Shows**: Routes to `Screen.EpisodeSelection`.

The `SearchViewModel` manages categories and individual stream results across all supported content types.

### Navigation Rules

1. **Startup → ContentTypeSelection**: Lands on the home screen (`ContentTypeSelection`) if a provider is configured, on TV through `ProfilePicker` first when there is more than one profile (mobile goes straight home).
2. **Startup → Settings**: If no provider configured.
2a. **Startup → NewerData / SafeMode**: checked before the rules above, in that order. Both replace the start destination and skip the nav host's `initializeStartup()` (provider lookups, legacy-credential migration, profile count) — `NewerData` because nothing may open `providers.db`, `SafeMode` because that work may be what keeps crashing. Back on either leaves the app, as from Home; `SafeMode`'s Show diagnostics pushes `Diagnostics` and Back returns to it. Continue / Reset sources restart the process. See `docs/plans/20261002_next-level-rock-solid-resilience-plan.md` → R-10, R-01.
3. **ContentTypeSelection → CategoryList**: Standard push.
4. **CategoryList → Player**: Standard push (content-type aware routing).
5. **Settings → ProviderSelection → AddProvider**: Standard push chain.
6. **Provider switch**: Navigate to ContentTypeSelection, clearing back stack — `popUpTo(navController.graph.id) { inclusive = true }`, as a profile switch does, so no screen holding the previous provider's (closed) repository survives underneath. A profile switch can itself change provider: the device moves to the provider the new profile last picked, when it differs from the current one (`docs/plans/20261002_profile-last-provider-plan.md`).
7. **Logout**: Clear auth session, navigate to Settings, clear back stack to ContentTypeSelection.
8. **Remote Stop (Live sync)**: when another device of the sync group stops this device's playback, whichever playing screen is up — TV `TvPlayerScreen` or the `LiveTvSplitLayout` preview/promoted player, mobile `MobilePlayerScreen` or the Live TV dock (docked or promoted) — finalises the session as Back does, stops, and pops back to ContentTypeSelection (`popBackStack(Screen.ContentTypeSelection, inclusive = false)`, the screens' `onHome`), with a "Playback stopped from <device>" toast. See `RemoteStopEffect` and `docs/plans/20261001_live-sync-now-playing-plan.md` → Remote Stop.

### Live TV Preview / Dock Back-Stack

Live TV always shows a channel playing alongside the browse list (see `docs/FEATURES.md`). On both platforms Live TV is one `CategoryList` nav entry and the preview is a layer on it, so Back never skips straight past the browse screen and out of Live TV:

- **TV** (`TvCategoryGridScreen.kt`, UX overhaul plan Part II LT7): selecting Live TV on Home pushes one `CategoryList(showPreviewPane = false, initialStreamId = <last channel>)` with `popUpTo(ContentTypeSelection)` — the browse entry. The preview (`LiveTvSplitLayout`), and full screen promoted in place inside it, is a layer over browse, not a nav entry: it is open while `livePreviewChannelId` (`rememberSaveable`) names the channel it opened on — the last channel on entry (no last channel: the entry opens on browse), or the channel OK was pressed on in browse. Back is a chain of stopovers in `LiveTvSplitLayout`'s `BackHandler`: full screen → preview (`fullScreen -> false`, the video keeps playing), then preview → browse (`livePreviewChannelId -> null`; the preview's player is stopped and released as it leaves composition), and only then does Back fall through to the nav host and pop the entry to Home. Browse is not composed under the preview — its rows would stay in the focus tree, and its focus effects run, behind an opaque preview or the full-screen player — so it is rebuilt on Back like a destination, with its saved state (pane memories, scroll) kept in a `SaveableStateHolder` and the same `CategoryViewModel` the preview used. The preview's own ViewModels (`PlaybackViewModel`, `StreamLoaderViewModel`) are in a store of the layer's (`LivePreviewViewModels`, held by the entry): they survive a trip to the TV Guide and back and an activity recreate, and are cleared when the layer closes, as a popped preview entry's were — so a channel previewed for less than the watch delay is not written into Recent afterwards. **Entries that open on one channel push their own entry, as before:** Search (a Live TV result or category), the EPG Browser and the TV Guide (a channel or programme cell — including the guide opened from full screen's Guide button) push `CategoryList(initialCategoryId, initialStreamId)` with `showPreviewPane = true` (the default): that entry is the preview alone, with no browse layer, and Back from it pops back to the screen that opened it.
- **Mobile** (`MobileCategoryListScreen.kt`): there's only ever one `CategoryList` entry — the dock/preview is local composable state (`dockTarget`, `fullScreen`), not a navigation route, and it auto-seeds on entry so Live TV never shows a bare list first. Two `BackHandler`s provide the equivalent stopover: `fullScreen -> false` (full-screen collapses to dock), then `dockTarget -> null` (dock clears to bare list). Only a third Back (falling through to the `onBack` callback) actually leaves the screen. The toolbar's Back, Search and TV Guide buttons leave without those handlers, so each stops the dock first (the engine is Activity-scoped and would keep playing behind the next screen).

When touching either flow, preserve the "Back always has a real stopover before exiting" property — it's the reason both look more convoluted than a single `navigate()` call.

**The context list (TV, `ChannelContext`, UX overhaul plan Part II LT2).** A channel carries the list it was chosen from: the preview layer shares browse's `CategoryViewModel`, whose selection is the *browsed* list (the real category, or the virtual `recent` / `favorites`) — not the channel's own category; an entry opened on one channel from Search or a guide has that channel's category as its `initialCategoryId` — and `LiveTvSplitLayout` turns it into a `ChannelContext` (`Category(id, name)`, `Recent`, `Favorites`; Home → Live TV is `Recent`), held in `rememberSaveable`. That one list is what the channel panel shows and what Up/Down zap through in full screen (`neighborChannel` over it). The panel (`LiveTvChannelPanel`) is a `TvSectionTabs` row — the category tab, labelled with its name and present only when there is one, then Recent and Favorites, with the Refresh icon at the row's end — over a `StreamList` with no header and its rows as a `tvPane` with no neighbours: Up from the first row lands on the selected tab (the row is a focus group whose `onEnter` redirects there), Left/Right on the tabs switch the list (focus follows selection, so the context changes as focus moves), Left/Right on a row do nothing, Down from the tabs enters the rows on the current channel when the list has it, else the first row. An empty tab shows a line of text and nothing focusable, so focus stays on the tab; Refresh is no longer a stop between the tabs and the first row. `scripts/focus-walks/live-tv-preview.txt` walks it.

**One panel in full screen (TV, LT3).** The same `LiveTvChannelPanel` opens over the video in full screen — `PlayerScreen`'s `channelPanel` slot, filled by `LiveTvSplitLayout` — sliding in from the right at the docked panel's 34 % width: same tabs, rows, current-channel marking and row actions (long-press OK / Menu). There are no separate Category / Recent flyouts any more. With neither the panel nor the OSD showing, **Left and Right both open it**; it opens on the tab used last, on the playing channel's row when the list has it, else the first row (the tab row while the list loads). A remembered tab with no rows (no favourites, or the last one removed) is not reopened: the panel opens on the first tab that has the playing channel instead — the category it came from, else Recent — on its row, and that tab is the zap order from then on. Inside, the keys are the docked panel's: Left/Right on the tab row switch tabs — which switches the `ChannelContext`, so the zap order follows — Up/Down move between rows, Left/Right on a row do nothing; OK on a row tunes that channel (the same full `loadStream` on the same loader/engine as a zap) and closes the panel; focus alone never tunes in full screen. **Back closes the panel** and focus returns to the player; Right (the outward key) does not, since on the tab row it is the next tab. Focus stays inside while it is open: the panel is a focus group whose exit is cancelled while `showChannelPanel` is true (not while it animates out, so the player can take focus back), Back is taken in `PlayerScreen`'s root `onPreviewKeyEvent`, and the player's key handler leaves the D-pad and OK to it (`isModalOpen`). Up/Down/OK with the panel closed are unchanged (zap / OSD). `scripts/focus-walks/live-tv-fullscreen.txt` walks it.

**The OSD (TV, `TvPlayerControlsOverlay`, LT4).** OK opens it: one row of buttons, each with its label beside the icon. Live: **Channels** (opens the same channel panel and hides the OSD; present only where there is a panel, i.e. the Live TV full screen), **Guide** (GD5: the TV Guide of the list being zapped through — the category, Recent or Favourites tab — with entry focus on the playing channel's row, via `Screen.EpgGuide(focusChannelId)`; only when the source has a guide, native EPG or an indexed one; Back from the guide returns to full screen on that channel — `LiveTvSplitLayout` keeps `fullScreen` and the channel in saved state across the round trip), ★ Favorite / Favorited, Subtitles, Audio, Quality (each only when the stream has the tracks), then **⋮ More**, which shows Stats after it in the row (focus moves onto Stats; OK on More again hides it). VOD: the same row without Channels, with Chapters first and Next episode before More, under the centre Play/Pause. Focus opens on **Channels** on live — a stray second OK opens the panel, never favourites — on Play/Pause on VOD, and on More otherwise (VOD while buffering, live without a panel); a picker returns focus to its button when it closes. Left/Right move along the row and stop at its ends (the row is a focus group whose Left/Right exit is cancelled). On live, **Up/Down still zap with the OSD up** (the OSD stays up, focus goes back to Channels on the new channel); on VOD they move between the row and the progress bar as before. **Any key while the OSD is up restarts its 15 s auto-hide** (`controlsKeyTick`, counted in `PlayerScreen`'s root `onPreviewKeyEvent`). The live banner is in the panel above the row: LIVE · channel name (the title shows once — not again top-left), then "Now: …" with the programme's progress bar and "Next: …" when the guide has them. The resolution/codec line top-left is developer mode only (`PlayerScreenState.isDeveloperMode`, from `AppSettings.isDevMode`). The channel number goes before the name once one reaches the player. The old first-run hints card (`ControlHintsOverlay`, never shown) is gone. `scripts/focus-walks/live-tv-osd.txt` walks it.

**Tuning feedback and the preview column (TV, LT5).** Focus on a row tunes the preview after it has rested **800 ms** (`PREVIEW_SETTLE_MS`); nothing is shown during the settle. From the moment a channel becomes the target — that settle, OK on another row, Up/Down or a panel pick in full screen — until the engine reaches Playing on *that* channel, `TvTuningOverlay` shows "Tuning · <channel>" with a small spinner, centred over a dimmed picture (in the preview pane, compact; in full screen, under the banner and the panel). It is derived in `LiveTvSplitLayout` (`tuningName`: the loader has resolved the target, the engine's stream URL is its URL, and the state is Playing) and passed to `PlayerScreen` as `tuningChannelName`, where it replaces the centre loading spinner; a later rebuffer of the same channel is not a tune and shows the plain spinner. It hides on a loader or playback error, which keep their own UI. The last frame does not survive a zap: the engine's `stop()` + new media source clears the tracks and `PlayerView` closes its shutter (`keepContentOnPlayerReset` is off), so the picture goes black until the new channel's first frame — the overlay sits on that black. Below the preview video: channel name, its category, "Now: …" with the programme's progress and "Next: …" when the guide has them (only the target's — not the previous channel's while a retune resolves), and at the bottom one hint line, "OK  Full screen · Hold OK  Options". The "LIVE PREVIEW" badge is gone: the video and the hint line already mark this layer. No channel number yet (not in the metadata).

### TV Back on Detail Screens: intercept at `onPreviewKeyEvent`

On TV, `BackHandler` alone is **not** enough on a screen where a `Button`/`Surface` holds focus. Confirmed on a real Shield: the first Back press reaches Compose's key dispatch, but something downstream marks it handled before it reaches the `BackHandler` / `OnBackPressedDispatcher` bridge — so the press only clears focus, the D-pad goes dead, and it takes a second press to actually navigate. (`androidx.tv:tv-material`'s `Surface` is not the culprit — it only intercepts `DPAD_CENTER`/`ENTER`.)

`:tv`'s `MovieDetailsScreen` and `EpisodeSelectionScreen` therefore intercept Back in `onPreviewKeyEvent` on their root `LazyColumn`. Preview dispatch runs top-down, before any descendant sees the event, so it wins the race regardless of what swallows it further down — the same pattern `TvDpadEscape.kt` uses for the analogous Up/Down-in-a-text-field problem. The `BackHandler`s remain as an inert fallback.

Use this pattern for any new TV screen whose base state has focusable buttons and a Back action — and for overlays/panels that *replace* that root (the episode detail panel in `EpisodeSelectionScreen` intercepts on the screen's root `Box` while it is open, since the `LazyColumn` with the interceptor isn't composed then).

The player's "Up next" card (Play next episode automatically) follows it too: while the card is up over the playing episode, `PlayerScreen`'s root `onPreviewKeyEvent` takes Back — it hides the card and playback carries on, without leaving the player. While focus is in the card its buttons get the D-pad and OK; when the card is up but not focused (OSD hidden), Down moves onto it; otherwise the player's own key handling runs as usual. The card takes focus on "Play now" when it appears. On mobile a `BackHandler` hides it.

## AuthViewModel

Shared authentication state across Mobile and TV modules.

Located in `:core:data/AuthViewModel.kt`

```kotlin
val authViewModel: AuthViewModel = viewModel()
val authResponse by authViewModel.authResponse.collectAsState()

// Set session after authentication
authViewModel.setAuthSession(authResponse, serverUrl)

// Clear session on logout
authViewModel.clearAuthSession()
```

## Mobile Navigation

Located in `:mobile/navigation/MobileNavHost.kt`

### Features
- Standard Material3 components
- Slide + fade transitions
- Auto-session restore from stored credentials
- No login screen (Settings-based provider configuration)

### Startup Logic

On startup, the app checks for a configured provider via `ProviderRepository`. If a provider exists, it navigates to the home screen (`ContentTypeSelection`), on TV through `ProfilePicker` when there is more than one profile; mobile goes straight home. If not, it navigates to Settings.

### Transitions
- **Enter**: Slide left + fade in
- **Exit**: Slide left + fade out
- **Pop Enter**: Slide right + fade in
- **Pop Exit**: Slide right + fade out

## TV Navigation

Located in `:tv/navigation/TvNavHost.kt`

### Features
- androidx.tv.material3 components
- D-pad focus management
- Auto-focus restoration
- TV-safe UI spacing
- Same startup logic as mobile (no login screen)

### D-Pad Focus Handling

Each screen should implement:
- `FocusRequester` for initial focus
- `Modifier.focusable()` on interactive elements
- `Modifier.focusRestorer()` for returning focus
- TV-safe padding for overscan (56dp horizontal, 32dp vertical)

Focus must land somewhere visible when a screen or panel appears, and return to where the user was:
- **Back from details** (movie/series) focuses the row that was opened — `StreamList` remembers it per category (`rememberSaveable`), falling back to the last played item; Live TV follows the playing channel. **Back from the Live TV preview** (UX overhaul plan Part II LT6) lands on the channel the preview was playing when it was left — after any retune or zap, not the row that opened it: `LiveTvSplitLayout` reports its channel each time it changes (`onPlayingChannel`), `TvCategoryGridScreen` keeps it, and when Back closes the preview layer (LT7) it hands it to browse as `returnedPlayingId` — a plain `remember`, so a later return from Search or the TV Guide rebuilds browse without it. Closing the layer also does what resuming the browse entry used to (`refreshLastPlayedItem`, `refreshWatchStateOnResume`) and reloads Recent when that is the browsed list, so a channel the preview recorded is in it. `TwoColumnLayout` then uses it in place of `lastPlayedItemId` (which only moves after the watch delay) as the items pane's selected row — so the pane remembers it and a later Right lands there too, and the row is marked current. When the list on screen does not have it (it was played from another list, or Recent has not recorded it yet), focus goes to the selected category row instead and the list does not take focus later on its own. Search / TV Guide returns are unchanged (`NavReturnFocus`). `scripts/focus-walks/live-tv-back.txt` walks it.
- **Back to any other screen** focuses the control that navigated away, at the scroll position its list had — Home's hero cards, header buttons and Continue Watching cards, Settings' Manage / Live Sync / Diagnostics buttons, Search results, the category screen's Search and TV Guide buttons, a details screen's Start Over / Category button / related title, TV Guide programme and channel cells and its Search button (Back from "Search the guide"), EPG Browser airing rows, Manage Sources' Add / EPG / overflow buttons. Navigation Compose rebuilds a screen on Back with nothing focused, and Compose then focuses the first focusable (the "Switch Source" chip, the top Settings card). The pattern (`tv/ui/components/input/NavReturnFocus.kt`): `val returnFocus = rememberNavReturnFocus()` (saveable, so it survives the round trip); in the control's `onClick`, `returnFocus.leaveFrom(key, listState)` before navigating; `Modifier.navReturnFocusTarget(returnFocus, key)` on the control; `NavReturnFocusEffect(returnFocus, listState, fallback) { key -> /* wait for data, scroll an inner row */ }`. The effect runs when the screen is RESUMED again (after the pop transition and the screen's own first-open effects), restores the list position, then `requestFocusWithRetry` — once: the key is cleared whether or not it landed. A screen's own first-open focus checks `returnFocus.key == null` (or `isReturn`, when it can fire again after the hand-back) so it doesn't fight it. Back from the player still lands on Play / the resume card, which those screens already focus.
- **Closing the episode detail panel** focuses that episode's card (the tab row if Next/Previous crossed into another season).
- **Episodes list (TV):** Left from any episode, or Up from the first, goes to the episodes header (the selected season tab, else Play next, else the section tabs); Right on an episode does nothing. Arrows never change season — that is the season tabs' job (UX overhaul plan Part II Phase 6).
- **TV Guide** opens on the programme on air now in the first channel that has one (separator rows are not channels) — or, opened from the player's Guide button, in the playing channel's row; Up/Down keep the time across rows, Left/Right step by programme, Up from the first row reaches the header buttons, Down from the header returns to the cell you left. OK on a channel opens its preview; OK on a programme opens its details panel (title, channel, day and time, description; Watch channel opens the preview, Close) with focus on Watch channel, and Back or Close returns focus to the cell (GD6). Long-press OK or Menu on either cell opens the channel's row actions (below).
- **End of a row:** the last Continue Watching card cancels Right (`focusProperties { right = FocusRequester.Cancel }`) so focus doesn't escape to the top bar. The last tab of a `TvSectionTabs` row (movie and series details, the Live TV channel panel) does the same unless the row ends in a control (`endFocusRequester` — the panel's Refresh): Right on a movie's only tab (Details) stays put instead of jumping up to Play.
- Land focus from a `LaunchedEffect` with `requestFocusWithRetry` (`tv/ui/components/input/FocusRetry.kt`): it retries each frame (about 0.5 s) on the Boolean result of `requestFocus(FocusDirection.Enter)`, then an optional `fallback`. Never `try { requestFocus() } catch (IllegalStateException)` or `runCatching`: since Compose 1.10 an unattached target logs and returns `false` instead of throwing, so those catches retried nothing (R-05). Act on the result — e.g. `StreamList` marks a Back-restore handled only when it returned true. `scripts/check-focus-retry.sh` (CI) rejects the old pattern.
- **Error states** use `TvErrorState` (`tv/ui/components/TvErrorState.kt`): focus lands on Retry on entry, Back is taken in `onPreviewKeyEvent` when the screen has one, and `RetryWhenOnline` retries once when the network comes back (R-15).

### Panes (`tvPane`)

Two-column TV screens (Live TV browse, Movies, TV Shows: `TwoColumnLayout`) are built from two panes, `tv/ui/components/input/TvPane.kt` (UX overhaul plan, Part II P1/P2). Without them the columns were plain siblings and Compose's geometric search decided every move: Down at the end of the items jumped into a category, Left from an item landed on the category level with it, Left from a category landed on Search.

- `val pane = rememberPaneFocus()` per column in the screen; the list calls `pane.bind(selectedKey, firstKey, listState, indexOf)` every composition; each row's card takes `Modifier.paneItem(pane, key)`; the list's container takes `Modifier.tvPane(pane, exitLeft = …, exitRight = …)`.
- **Left/Right are taken by the pane, not by geometry:** they go to the neighbour pane named in `exitLeft` / `exitRight`, or nowhere. **Down at the end of a pane stays put; Up at the top leaves** to whatever is above — the Refresh icon and the Search / TV Guide header buttons stay reachable from the first row.
- **Entering a pane lands on its remembered row** (the last one focused — `rememberSaveable`, so it survives Back), else the selected row, else the first; a categories pane binds with `preferSelected = true`, so Left from an item always lands on the category being browsed. A new selection (the row opened, the channel playing) becomes the memory, which is how Back from details / the player lands on the right row. A remembered row that is scrolled out of composition is scrolled to, then focused with `requestFocusWithRetry`.
- Entry focus on open: the selected category while the items load, then the items' entry row once they are there (the last played / opened item when it is in the list, else the first); an empty category keeps the category. OK on a category keeps focus on the category; Right enters its items.
- Programmatic focus (`requestFocusWithRetry`, `NavReturnFocus`) passes through a pane untouched; only D-pad moves are redirected (`focusProperties { onEnter }` on the group, `onKeyEvent` for Left/Right, `onExit` + `cancelFocusChange()` for Down). `focusRestorer` is not used: it defines the same `onEnter` and the outer definition wins, and it cannot express the selected-row fallback.

### Row actions (long-press OK / Menu)

A content row (a channel, title or episode in `StreamList`, a category in `CategoryList`, the Live TV preview panel's rows, a TV Guide channel or programme cell — acting on its channel; Remove from Recent only in the Recent guide, after which the rows reload and focus lands on the row that took its place; the "⋮" only on the channel cell, programme cells being too narrow) is one focus stop: OK does the row's job, and **long-press OK or the Menu key** opens its action menu (`FavoriteContextMenuDialog`: Add/Remove favorite first and focused, Mark watched on Movies / TV Shows, Remove from Recent last, Cancel) — UX overhaul plan Part II P3. A row shows a small "⋮" at its end while focused as the hint; it is not focusable. There are no hidden trailing ★ / ✓ / 🗑 buttons on content rows any more, so Right from a category goes straight to its items and Right from an item goes nowhere. The rule is for content rows only (P3a): Settings' Sources rows, whose few actions are the row's point, keep their visible labelled buttons.

### Search fields (`TvSearchField`)

A focused Compose text field opens the keyboard, and on TV the keyboard then takes every D-pad key, so the Search and EPG Browser query fields are `TvSearchField` (`tv/ui/components/input/TvSearchField.kt`, UX overhaul plan Part II P4): at rest the field is an ordinary focus stop showing the query or placeholder, so Up/Down/Left/Right move focus as anywhere else and Right reaches the clear (×) and search buttons beside it. **OK** turns it into the text field, focused, keyboard open; the IME's Search/Done action submits, **Back** closes the keyboard (submitting when the text changed), and focus comes back to the resting field. Back is taken with `onInterceptKeyBeforeSoftKeyboard` on the text field, before the keyboard can swallow it to hide itself. The editing state is hoisted: both screens open on the field without the keyboard when there are recent searches (Down reaches them) and straight into the keyboard when there are none; a return from a result uses `NavReturnFocus` as before. Use it for any new TV text field the user reaches with the D-pad on the way to something else. `scripts/focus-walks/search.txt` walks it.

## Adding New Screens

### 1. Define Screen in :core:navigation

```kotlin
@Serializable
data class NewScreen(val someParam: String) : Screen
```

### 2. Add Composable to Both NavHosts

```kotlin
// In MobileNavHost.kt and TvNavHost.kt
composable<Screen.NewScreen> { backStackEntry ->
    val screen = backStackEntry.toRoute<Screen.NewScreen>()
    NewScreenComposable(
        someParam = screen.someParam,
        onBack = { navController.navigateUp() }
    )
}
```

### 3. Navigate to New Screen

```kotlin
navController.navigate(Screen.NewScreen(someParam = "value"))
```

---

## Deployment & Build

For comprehensive instructions on building and deploying the app via ADB to emulators and physical devices, see [RUN_GUIDE.md](RUN_GUIDE.md).

