# Live TV preview-pane layout — plan

## Context

Today, selecting a Live TV channel **navigates away** to a full-screen player
(`Screen.Player` → `TvPlayerScreen`). We want the reference layout instead: a landscape
split view where a small **preview player** (top-left) plays the highlighted channel with a
now/next EPG card, while a **channel list** stays on the right with per-row actions and a
Categories/Recent switch. The user wants the plan to spell out the **flow to get into this
view and back out**.

Feasibility is good — most pieces exist and are reused (see Reuse map). The one real
architectural change: the player stops being a separate nav destination and becomes an
**embedded pane** on the Live TV browse screen, backed by the existing single playback engine.

**Both platforms, one core, two shells.** The interaction differs by platform (TV = D-pad focus,
mobile = touch), so this plan builds a shared core once and two thin layout shells: a **TV split
view** (focus-driven, side-by-side) and a **mobile docked mini-player** (tap-driven, vertical).
See Cross-platform architecture.

**Not adopting "Discover."** The reference mockup's bottom nav shows a Discover item — we are
**not** adding it. Our bottom nav / entry points stay as they are.

## Target UX

```
┌───────────────────────────┬───────────────────────────────┐
│  ┌─────────────────────┐  │  [Categories ▾] [Recent]  hdr │
│  │  PREVIEW PLAYER     │  │  ─────────────────────────────│
│  │  (highlighted chan) │  │  ▸ US: MSNBC        ♡ ⊞ ⋯    │  ← focused row drives preview
│  └─────────────────────┘  │    US: HBO HD       ♡ ⊞ ⋯    │
│  Now: The Moment… 74% ▓▓░  │    US: BBC WORLD…   ♡ ⊞ ⋯    │
│  Up next: Deadline 3:00PM │    US: ABC NEWS     ♡ ⊞ ⋯    │
└───────────────────────────┴───────────────────────────────┘
```

Left pane = embedded player + EPG card (reuses the now/next+progress card already built in
`PlayerControlsOverlay.kt:461-502`). Right pane = the existing `StreamList`.

## Scope — Live TV only (VOD stays details → play)

This pattern is **Live TV only**. Movies and TV Shows keep the current flow: browse → details →
play full-screen. Why:

- **Live TV fits** — channels are continuous streams (instant-tune, no start position, no
  episodes). Browsing *is* surfing; a preview/dock of the highlighted channel is the whole point.
- **Movies don't** — auto-previewing means starting the actual film from 0/resume, which is
  jarring, spoiler-y, and fights resume-vs-restart. VOD is a commit-to-one-title flow, not surfing.
- **TV Shows fit even less** — a series isn't a playable stream; it's seasons → episodes. There's
  no single target to preview.
- **A VOD analog, if ever wanted, is a different feature:** focus → details/trailer (Netflix-style
  hero: backdrop + synopsis, optionally `youtubeTrailer`) — **never** auto-playing the real content.
  Out of scope here; separate plan.

Concretely: `CategoryGridScreen` renders the split/dock **only for `LIVE_TV`**; Movies/TV Shows
keep `TwoColumnLayout` (TV) and their existing mobile list + details flow.

## Cross-platform architecture

Build the reusable guts once, in `core/ui`, then a platform shell in each module:

- **Shared core (`core/ui`)** — the embeddable player surface (extracted from
  `PlayerScreen.kt:145-167`), a preview/playback controller over the single
  `StreamingPlaybackService`, the now/next+progress EPG card, and the browse-path now/next data.
  No focus/touch assumptions live here.
- **TV shell (`tv/`)** — focus-driven **side-by-side split** (preview pane | channel list). Flow below.
- **Mobile shell (`mobile/`)** — tap-driven **docked mini-player** (vertical: mini-player + list).
  Flow below.

The TV composables do **not** carry to mobile (separate screens: `MobilePlayerScreen`,
`MobileCategoryListScreen`); only the shared core does.

## TV flow — in and out (focus-driven split)

**Entering the view** — no new navigation hop; the Live TV browse screen *becomes* the split.
1. User picks Live TV (existing `Screen.CategoryList(LIVE_TV)`, `TvNavHost.kt:205`). The screen
   renders the new `LiveTvSplitLayout` instead of the categories|streams `TwoColumnLayout`.
2. Focus lands on the first channel row. **On focus settling** (debounced ~400ms so scrolling
   through the list doesn't machine-gun the tuner), the preview pane starts playing that channel
   on the shared engine, and the EPG card fills from that channel's now/next.
3. Arrowing up/down the list re-points the preview to the newly focused channel (same debounce).
   Preview is the *highlighted* channel — never a second stream.

**Going full-screen (deeper in)**
4. Pressing OK/center on the focused row **promotes** the preview to full-screen. Because the
   shared engine is *already* playing that exact channel, this is instant — no re-resolve, no
   reload. Implementation: navigate to `Screen.Player` as today but pass an
   `alreadyPlaying=true` hint so `StreamLoaderViewModel` skips resolution when the singleton is
   already on the target stream (guard, not a new engine).

**Coming back out**
5. **Back** from full-screen → returns to the split view; the preview keeps playing the same
   channel (engine is never stopped on this transition — it just changes which pane owns the
   surface).
6. **Back** from the split view → leaves Live TV (to content-type/Home). On leave, the preview
   is stopped/released so we don't tune in the background.

```
Content-Type ──select Live TV──▶ SPLIT VIEW ──focus row (debounced)──▶ preview plays highlighted
                                    │  ▲                                        │
                                    │  │ Back (engine keeps running)           │ OK / center
                                    │  │                                        ▼
                                    │  └────────────────────────────── FULL-SCREEN (same engine, instant)
                        Back (stop preview, release)
                                    ▼
                              Content-Type / Home
```

Edge cases to define during build: switching category/tab while a preview is live (re-point or
pause); losing focus to the tab strip / category dropdown (preview holds last channel); audio —
**decide preview starts muted or with sound** (mockup implies sound; recommend sound-on since
it's the highlighted channel, with the tuner debounce preventing zapping noise).

## Mobile flow — in and out (tap-driven docked mini-player)

Touch has no focus, and portrait has no room for side-by-side — so mobile uses the YouTube/Twitch
**docked mini-player** pattern instead of a preview pane.

```
┌───────────────────────┐        ┌───────────────────────┐
│  Live TV channel list │        │ ┌───────────────────┐ │  ← mini-player docked (top)
│   US: MSNBC           │  tap   │ │ ▸ US: MSNBC  74% ░ │ │     keeps playing while browsing
│   US: HBO HD          │ ─────▶ │ └───────────────────┘ │
│   US: BBC WORLD…      │        │  US: HBO HD           │  ← list continues below the dock
│   US: ABC NEWS        │        │  US: BBC WORLD…       │
└───────────────────────┘        └───────────────────────┘
```

1. **In:** Live TV shows the normal channel list (no auto-preview — nothing to preview without
   focus). **Tap a channel** → it docks as a small sticky mini-player (top of screen) and starts
   playing; the list stays scrollable underneath. Tapping another channel re-points the dock.
2. **Deeper in:** tap the mini-player (or its expand affordance) → **full-screen** player, same
   engine already on that channel → instant, no reload (same `alreadyPlaying` guard as TV).
3. **Out:** from full-screen, **back / swipe-down** → collapse back to the docked mini-player
   (keeps playing). From the list, **swipe the dock away / close** → stop and release the engine.
   Leaving Live TV also stops it.

```
Live TV list ──tap channel──▶ DOCKED mini-player (plays, list scrolls under)
     ▲                              │  ▲                         │
     │ close dock (stop)            │  │ back/swipe-down          │ tap dock / expand
     │                             (keeps playing)                ▼
     └──────────────────────────────┴──────────────────── FULL-SCREEN (same engine, instant)
```

Optional: **landscape / tablet mobile** can mirror the TV side-by-side split instead of the dock,
since the width exists — same shared core, a landscape branch in the mobile shell.

Difference vs TV in one line: TV **focus→preview** (lean-back, automatic); mobile
**tap→dock+play** (lean-forward, explicit). Both reach the same full-screen via the same engine.

## Reuse map (what already exists)

| Need | Reuse | Location |
|---|---|---|
| Two-pane scaffold | `TwoColumnLayout` structure | `feature/category/components/TwoColumnLayout.kt:49` |
| Channel list + rows | `StreamList` / `StreamItem` (drop into right pane) | `feature/category/components/StreamList.kt:233,263` |
| Now/next + progress EPG card | rendering already built | `ui/player/PlayerControlsOverlay.kt:461-502` |
| now/next/progress data | `currentEpgProgram`/`nextEpgProgram` + progress calc | `StreamLoaderViewModel.kt:46-47,173-196` |
| ExoPlayer surface at any size | `AndroidView(PlayerView)` block | `ui/player/PlayerScreen.kt:145-167` |
| Playback engine | single `StreamingPlaybackService` | `PlayerScreen.kt:161` |
| Favorite toggle | `toggleFavoriteStream` | `TwoColumnLayout.kt:120` |
| Recent / favorite data | virtual categories + recent streams | `CategoryViewModel.kt:483-528` |

## Work breakdown

**Shared core (`core/ui`) — do first, both shells depend on it:**
1. **Extract an embeddable player surface.** Pull the `AndroidView`+service-binding block out of
   `PlayerScreen.kt:145-167` into a reusable `EmbeddedPlayerSurface(modifier)` composable that
   does **not** grab focus or consume D-pad keys (those live only in the full-screen wrapper at
   `PlayerScreen.kt:104,128`). Full-screen `PlayerScreen` then also uses this surface.
2. **Preview/playback controller.** A light `LiveTvPreviewController` (or extend
   `PlaybackViewModel`) that, given a target stream, drives the shared engine — with a debounce for
   the TV focus case, immediate for the mobile tap case. Reuses `StreamLoaderViewModel` for
   resolution + EPG.
3. **now/next EPG card + browse-path data.** Extract the now/next+progress card from
   `PlayerControlsOverlay.kt:461-502` into a shared composable. Extend the browse now-playing path
   (today current-only, `getNowPlayingFromIndex`, `MediaRepository.kt:268`) to include next-program
   + progress% (derivable from `EpgProgram.startTime/endTime`).
4. **Full-screen promotion guard.** `alreadyPlaying` hint so promoting to full-screen skips
   re-resolution when the engine is already on the target (builds on existing `awaitInstance` race
   handling). Shared by both shells.

**TV shell (`tv/`):**
5. **`LiveTvSplitLayout`.** left = `EmbeddedPlayerSurface` + EPG card; right = `StreamList`.
   `CategoryGridScreen` renders this for `LIVE_TV` only; other content types keep `TwoColumnLayout`.
   Focus-settle (debounced) drives the preview; OK promotes to full-screen; lifecycle stops/releases
   on leaving Live TV.
6. **Categories/Recent tab strip + category dropdown.** New UI; data sources already exist (recent
   categories/streams, favorites). Replaces the left category column.

**Mobile shell (`mobile/`):**
7. **Docked mini-player.** A sticky mini-player above the existing mobile Live TV list
   (`MobileCategoryListScreen`) using `EmbeddedPlayerSurface` + the EPG card; tap-to-dock, tap-to-
   expand (full-screen via the shared guard), swipe/close to stop. Optional landscape/tablet branch
   that mirrors the TV split.

**Per-row actions (both, incremental):** inline favorite (reuses `toggleFavoriteStream`,
`TwoColumnLayout.kt:120`) + a "more" menu. The ⊞ add-to-folder is deferred (see Out of scope).

## Key decisions / risks

- **Single engine.** One app-wide player (`PlayerScreen.kt:161`) → preview and full-screen share
  it; no preview-A-while-watching-B. Acceptable: preview *is* the highlighted channel. This is the
  main constraint, not a blocker.
- **Focus debounce** is essential — without it, scrolling the list retunes on every row. ~400ms.
- **Full-screen reuse:** the full-screen `PlayerScreen` wrapper (focus grab, key handling,
  overlays) is not preview-safe; only the surface block transplants. Keep the wrapper for
  full-screen, use the bare surface for preview.

## Out of scope (deferred / not adopting)

- **"Discover" nav item** (shown in the reference mockup's bottom nav). We are **not** adding it;
  entry points / bottom nav stay as-is.
- **Folders** (the ⊞ add-to-folder icon in the mockup). Folders don't exist today — only favorite
  *categories*. Separate feature; the row can show favorite + more for now, folder later.
- Simultaneous preview + independent full playback (would need a second engine).

## Verification

- Build: `./gradlew :tv:compileDebugKotlin :mobile:compileDebugKotlin -q`.
- **TV** (Shield, real EPG data): open Live TV → split renders, focusing a row starts the preview
  after the debounce, the EPG card shows now/next+progress, OK goes full-screen **without a reload
  stutter**, Back returns to the split with preview still running, Back again exits Live TV and the
  tuner stops (no background playback in logcat).
- **Mobile** (phone, real EPG data): open Live TV → tapping a channel docks the mini-player and it
  plays while the list scrolls under it, tapping the dock expands to full-screen **without reload**,
  back collapses to the dock (still playing), closing the dock / leaving Live TV stops the engine.
- Regression: Movies/TV Shows still use the old layouts; navigating Live TV ↔ other types is clean
  on both platforms.
