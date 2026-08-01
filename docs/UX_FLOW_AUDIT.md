# Home Screen UX Flow Audit

Full map of every path reachable from `ContentTypeSelectionScreen`, on TV and Mobile, plus a
code-grounded friction log and recommendations. Findings are verified against source (file:line),
not inferred from docs.

**Scope:** `ContentTypeSelectionScreen` and everything reachable from it.
**Platforms:** TV (D-pad) and Mobile (touch).
**Source:** `core:navigation/Screen.kt`, `TvNavHost.kt`, `MobileNavHost.kt`, both
`ContentTypeSelectionScreen.kt` implementations.

---

## 1. The map

TV and Mobile share one route graph — same `Screen` definitions, same NavHost shape. The one place
they genuinely diverge is *how* Live TV's preview pane sits on the back stack (see finding on
"Leaving Live TV", below, and `NAVIGATION_GUIDE.md` → "Live TV Preview / Dock Back-Stack").

```mermaid
flowchart TD
    Launch(["App launch"])
    Launch -->|"no provider configured"| Settings
    Launch -->|"provider configured"| Home["Content Type Selection"]

    Home -->|"search icon"| SearchAll["Search — ALL"]
    Home -.->|"book icon, EPG indexed only"| EpgBrowser["EPG Browser"]
    Home -->|"gear icon"| Settings["Settings"]
    Home -.->|"tap provider name"| Switch{{"Switch Provider — dialog"}}
    Home -->|"Live TV card"| LiveTv["CategoryList — LIVE TV\n(preview pane)"]
    Home -->|"Movies card"| MoviesCat["CategoryList — MOVIES"]
    Home -->|"TV Shows card"| ShowsCat["CategoryList — TV SHOWS"]

    SearchAll -->|"result: Live TV"| Player["Player"]
    SearchAll -->|"result: Movie"| MovieDetails["Movie Details"]
    SearchAll -->|"result: TV Show"| EpisodeSel["Episode Selection"]

    LiveTv -->|"OK / tap channel"| Player
    LiveTv -.->|"search icon"| SearchLive["Search — LIVE TV"] --> Player
    LiveTv -.->|"guide icon"| EpgGuide["EPG Guide grid"] --> Player

    MoviesCat -->|"tap movie"| MovieDetails
    MoviesCat -.->|"search icon"| SearchMovies["Search — MOVIES"] --> MovieDetails
    MovieDetails -->|"Play / Resume"| Player

    ShowsCat -->|"tap show"| EpisodeSel
    ShowsCat -.->|"search icon"| SearchShows["Search — TV SHOWS"] --> EpisodeSel
    EpisodeSel -->|"tap episode"| Player

    Settings --> ProviderSel["Manage Providers"]
    ProviderSel -.->|"add"| AddProvider["Add Provider"]
    ProviderSel -.->|"edit"| EditProvider["Edit Provider"]
    Settings --> EpgMgmt["Manage EPG Data"]
    Settings -.->|"dev mode only"| CellBuffer["Cellular Buffer Settings"]
```

Solid arrow = standard push. Dashed arrow = conditional, dialog, or secondary entry point.

---

## 2. Inside the player

The player isn't a dead end — several gestures branch out of it without a route change, which is
why they don't show up in the map above. Still real navigation, just local state instead of a
back-stack entry.

| Trigger | TV | Mobile | Result |
|---|---|---|---|
| Switch channel | D-pad Up / Down | Vertical swipe | Live TV only — stream swaps without leaving player |
| Channel list overlay | D-pad Left | Swipe right | Slide-in panel, current category |
| Last watched overlay | D-pad Right | Swipe left | Slide-in panel, history |
| Episode skip | D-pad Left / Right | Swipe | TV Shows only — jumps to adjacent episode, same session |
| Stats overlay | Double-OK | Double-tap | Diagnostics panel, repositionable to 4 corners |
| Favorite | Star action | Star action | Adds to the Favorites virtual category |

---

## 3. Screen by screen

| Screen | Reached from | Leads to | Platform variance |
|---|---|---|---|
| `ContentTypeSelection` | App launch (provider configured) | Search, EPG Browser, Settings, 3× CategoryList | Identical IA; layout differs (vertical stack vs. side-by-side hero cards) |
| `CategoryList(LIVE_TV)` | Home → Live TV card | Player, Search, EpgGuide | TV: two stacked back-stack entries (bare + preview). Mobile: one entry, dock is local state |
| `CategoryList(MOVIES)` | Home → Movies card | MovieDetails, Search | Same shape both platforms |
| `CategoryList(TV_SHOWS)` | Home → TV Shows card | EpisodeSelection, Search | Same shape both platforms |
| `MovieDetails` | CategoryList(MOVIES), Search(ALL/MOVIES) | Player | Same shape both platforms |
| `EpisodeSelection` | CategoryList(TV_SHOWS), Search(ALL/TV_SHOWS) | Player | Season accordion auto-expands next unwatched season, both |
| `Search(contentType)` | Home (ALL) or any CategoryList (scoped) | Player / MovieDetails / EpisodeSelection, by result type | Same shape both platforms |
| `EpgGuide` | CategoryList(LIVE_TV) only | Player | Same grid concept; TV renders full 20/80 split, mobile is a timeline |
| `EpgBrowser` | Home (book icon, gated on index state) | — | Same shape both platforms |
| `Settings` | Home (gear icon) | ProviderSelection, EpgManagement, CellularBufferSettings | Same shape both platforms |
| `ProviderSelection` → `AddProvider` | Settings | Player, eventually, via Home | Same shape both platforms |
| `Player` | Almost every leaf screen above | — | D-pad controls vs. touch gestures; see §2 |

---

## 4. Friction log

Six things that cost a tap, a moment of confusion, or a discoverable feature — ordered roughly by
how many users hit them.

### High — Mobile's provider name is a dead tap for most users
`mobile/…/ContentTypeSelectionScreen.kt:196` (tap target) vs. `:299` (dialog guard)

The top-bar title is always rendered as a clickable row with a dropdown arrow — on every build,
regardless of provider count. But the picker dialog it's supposed to open only renders
`if (allProviders.size > 1)`. Anyone running a single provider (very plausibly most users) taps a
control that visually promises a menu, and gets silence — no toast, no disabled state, nothing.

**Fix:** mirror what TV already does — it only composes the clickable pill at all when
`allProviders.size > 1` (line 259). On mobile, render the title as plain static text in the
single-provider case.

### High — Single-content-type providers still get the picker screen
Both `ContentTypeSelectionScreen.kt` — card visibility gated on `supportedContentTypes`

Jellyfin, SMB, Local, and Remote M3U providers each expose at most two of the three content types,
and several realistic setups expose only one. Those users still land on "Select Content Type", see
one giant card standing alone with two-thirds of the screen doing nothing, and have to tap it —
every app open, every content-type switch — for a decision that was never actually theirs to make.

**Fix:** when `supportedContentTypes.size == 1`, navigate straight to that `CategoryList` and skip
the picker. Keep the screen only when there's a real choice.

### Medium — The EPG Browser icon can go stale mid-session
Both files, ~line 99–105 (mobile) / 138–144 (TV) — `remember { EpgIndexer…state.value }`

`hasEpgData` is captured once at first composition, not collected as a live state. If indexing
finishes while the user is sitting on Home — or they pop back to it after starting a source
refresh — the book icon won't appear until the composable is torn down and rebuilt from scratch.
The only way to reach EPG Browser is that icon, so a just-finished source is effectively invisible
until an app restart or provider switch.

**Fix:** collect `EpgIndexer.state` with `collectAsStateWithLifecycle()` instead of a one-shot
`remember`.

### Medium — "TV Guide" and "EPG Browser" point at different things, from the wrong place
Home → book icon → EpgBrowser; CategoryList(LIVE_TV) → guide icon → EpgGuide

The book icon on Home — the icon most likely to get read as "TV Guide" — actually opens a
programme-title search tool. The channel-by-time grid a user is picturing when they think "guide"
sits one level deeper, inside the Live TV category screen, with no icon-level hint from Home that
it exists at all.

**Fix:** either relabel/re-icon EpgBrowser to read clearly as "search", or add a direct Home-level
entry into EpgGuide for the default channel, and keep EpgBrowser as the deeper power-user tool.

### Medium — Favoriting is a hidden gesture with zero on-screen hint
Mobile: `onItemLongPress`/`onCategoryLongPress` → `toggleFavorite*`; TV: `tvLongPress` → same

Long-press (mobile) and D-pad-hold (TV) are the *only* way to favorite a stream or category from
any browse list — no "…" button, no visible affordance, no first-run coach mark. Favorites is one
of just four virtual categories the whole app has, and its only other entry point is buried inside
the player.

**Fix:** a one-time hint on first browse, or a persistent (if subtle) affordance — even a small
star glyph that appears on focus/press-start — would make this discoverable without adding a
permanent extra button.

### Low — Leaving Live TV can take up to three Back presses, with no indication why
`NAVIGATION_GUIDE.md` → "Live TV Preview / Dock Back-Stack"

Deliberate, and for a good reason: it guarantees Back always has a real stopover before exiting
Live TV, so browse position is never lost. But nothing in the UI marks which layer you're in — a
user who Back-mashes out of habit after full-screen video will bounce through 1–2 states that look
similar to what they just left, which reads as "Back is broken" rather than "Back is being
careful."

**Fix:** not a navigation change — a small, unobtrusive state cue (e.g. dimming or a label
distinguishing "browsing" from "docked") would make the existing, correct behavior legible instead
of surprising.

---

## 5. What's working

A friction log without this half is just a complaint list — these are worth protecting through any
redesign.

- **The ambient backdrop personalizes Home for free.** Both platforms pull a recently-watched
  poster into the background wash instead of a static gradient — an immediate "this is my library"
  signal — and it degrades gracefully to the plain gradient when there's no watch history yet.
- **Category counts give information scent before the tap.** "42 categories" (or "12 of 45" in dev
  mode) on each hero card tells you roughly what's behind Movies vs. TV Shows before you commit to
  opening either.
- **Preview-to-full-screen is a genuinely seamless promotion.** Live TV's dock/split view and the
  full-screen player share the same `StreamingPlaybackService` connection — promoting or demoting
  never restarts or rebuffers the stream, which is a harder engineering bar than it looks and pays
  off directly as perceived responsiveness.
- **Settings surfaces the thing that will actually bite you.** Subscription expiry and
  connection-limit info for the active Xtream provider sits at the top level of Settings, not
  buried in Edit Provider — it's in front of the user before it becomes a problem, not after.
- **Two-tier search matches an existing mental model.** Global "ALL" search from Home plus a
  scoped search inside each category list mirrors the broad-then-focused pattern users already
  know from other media apps — it doesn't need to be taught.

---

## 6. Recommendations, in order

Ordered by effort-to-impact — the first three are one-line guard fixes; the last two are real IA
calls worth a deliberate decision, not a quick patch.

1. **Guard the mobile provider tap.** Hide the dropdown affordance when there's only one provider,
   matching TV's existing behavior.
2. **Skip the picker when there's no real choice.** Auto-navigate to the single supported
   CategoryList for single-content-type providers.
3. **Make the EPG icon reactive.** Swap the one-shot `remember` for
   `collectAsStateWithLifecycle()` so it appears the moment indexing finishes.
4. **Add a hint for the favorite gesture.** A first-run coach mark or a subtle on-focus
   affordance — the feature shouldn't depend on tribal knowledge.
5. **Reconcile EPG Guide vs. EPG Browser.** Decide which one Home's book icon should point to, and
   make the other explicitly the "deeper" tool — a naming and IA decision, not a bug fix.
