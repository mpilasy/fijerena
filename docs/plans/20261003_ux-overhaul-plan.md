# UX Overhaul Plan — Settings, TV Focus & Live TV, TV Guide

**Status (2026-10-03):** Parts I, II and III approved. In progress, four lanes (see Parallel lanes).

## Progress

Updated at every lane start and every merge to `main`. Rows in the phase tables are the source of
truth: `Todo` (no mark), **In progress (lane N, since date)**, **Done date** with what was
verified and where. Only the main session edits this file; lane agents never do. Plan edits go
in their own `docs:` commit, never amended into a lane's code commit.

| Lane | Done | Current | Next |
|---|---|---|---|
| 1 Mobile settings | M1 (2026-10-03, phone) | — | M2 |
| 2 TV focus + Live TV | LT1, Phase 1 (2026-10-03, TV) | — | Phase 2 (`tvPane`) → Phase 3 → LT2 |
| 3 Core + guide | A-W4, A-W6, GD0 (2026-10-03, both) | — | GD0b → GD1 |
| 4 TV settings | T1 (2026-10-03, TV) | — | T2 → T4 → T5 → T6; T3 after Phase 2 |

Verified on: TV = Television_1080p emulator, phone = Pixel_10 emulator; real devices only at
release time (`docs/RUN_GUIDE.md`).

**Effort / risk** (every work item and phase below carries both):

- Effort, one engineer including the emulator checks: **S** under half a day · **M** half a day
  to two days · **L** two to five days · **XL** more than a week.
- Risk: **Low** isolated, easy to verify · **Med** touches shared components, state or both
  platforms; needs the walk script · **High** architecture, playback or focus plumbing with a
  regression history.

One plan, three parts, so each can be attacked on its own but the shared rules live in one place:

- **Part I — Settings** (TV + mobile): information architecture, scope labels, two-pane on TV,
  grouped list on mobile, Edit Source, EPG Management, bugs found on the way.
- **Part II — TV focus navigation**, app-wide, including the **Live TV flows** (browse → preview
  → full screen → panel) and the shared primitives both other parts build on (`tvPane`, row
  action menu, highlight tokens, focus-walk script).
- **Part III — TV Guide** (grid), TV + mobile: findings, the rebuilt grid, entry points, naming.

Cross-part dependencies are stated in each part's phase table; the overall order of attack is
at the end.

---

# Part I — Settings (TV + mobile)

**Status:** Approved (2026-10-03) — not started.

Settings and every screen it opens were walked on both emulators:

- **TV** — Television_1080p, 1920×1080, UI scale 60%, profile Kid, source iptv.
- **Mobile** — Pixel_10, 1080×2424, profile atr, source jellyxtream.

Screens covered: Settings, Sources, Source actions menu, Edit Source, EPG Management, Live sync,
Diagnostics, Cellular Buffers (mobile), profile and language dialogs.

**No breaking changes.** Every setting keeps its storage key, scope, default and ViewModel. Every
route stays. Nothing is removed from the user's reach. Only placement, grouping, component choice
and focus behaviour change, plus a handful of real bugs found on the way.

This plan is split so each platform can be attacked on its own:

- **Part A — Shared:** decided once, lives in `core/*` (strings, `AppSettings`, ViewModels) or is
  a cross-platform rule both UIs follow. Do these first; both platforms depend on them.
- **Part B — TV only:** `tv/**`.
- **Part C — Mobile only:** `mobile/**`.

Issue IDs carry their part: `A-n` shared, `T-n` TV, `M-n` mobile.

---

## Part A — Shared

Both platforms have the same problems at the level of *what goes where*. The fix is one
information architecture (IA), one set of scope labels and one set of visual rules, then each
platform renders them its own way.

### A. Problems (seen on both platforms)

| # | Problem | TV evidence | Mobile evidence |
|---|---|---|---|
| A-1 | **Grouping is accidental.** One flat list; related things split, unrelated things neighbours. Developer mode sits mid-page on mobile (between EPG Data and Live sync); on TV "Data & Sync" holds EPG status, Live sync, Export/Import and Shrink DB. | `SettingsScreen.kt` (tv) | `SettingsScreen.kt` (mobile) — no section headers at all |
| A-2 | **Order differs between platforms** — TV: Live sync before Export; mobile: Developer before Live sync. Same app, two mental maps. | — | — |
| A-3 | **Scope is invisible.** Four scopes exist; only autoplay says "For this profile". | — | — |
| A-4 | **EPG Data block is a dead end** — status text, not tappable/focusable, no way to the guide sources it counts. Guide sources are reached only via an unlabeled TV icon on Sources. | `EpgSettingsCard.kt` | `EpgSettingsCard.kt` |
| A-5 | **Selected looks like an action.** Selected option = filled blue; primary action buttons (Manage Sources, Export, Live sync Manage, Sync Data Now) = filled blue. On mobile, Edit Source selected chips are **orange** — a third colour for "selected". | Theme chips vs Export | Theme vs Manage Sources; orange m3u8 chip |
| A-6 | **Too many primaries / wrong danger placement.** Clear All Favorites / Clear All Progress sit right after Auto-Resume in Edit Source; Clear All Data is the biggest button on EPG Management. | Edit Source, EPG Mgmt | same, full-width red |
| A-7 | **Edit Source mixes two save models.** Connection fields need "Update" at the bottom; everything else applies instantly. Nothing says which. | `TvAddProviderScreen` | `MobileAddProviderScreen` |
| A-8 | **Category filters** — the per-profile × per-source content control (the Kid profile's main safety net) — buried mid-form in Edit Source; nothing in Profiles points at it. | — | — |
| A-9 | **EPG Management mixes scopes and buries its content.** Title doesn't name the source. Auto-Refresh (`AppSettings.epgAutoRefreshEnabled`, `epgRefreshTime`) and Maintenance / Clear All Data are **global** but live on the per-source screen — and on mobile they sit *above* the source's own guide list. | iptv: status + maintenance, no empty state | guide list starts after 4 global blocks |
| A-10 | **Source row actions are unlabeled icons that don't line up** — the active row lacks ✓, Jellyfin lacks the guide icon, so columns shift. | 3 icons | 6 icons incl. one-tap red trash |
| A-11 | **About differs:** TV shows version + git hash + build time; mobile shows version + tagline only. | `AboutSettingsCard.kt` | `AboutSettingsCard.kt` |
| A-12 | **Dead route:** `Screen.EditProvider` is registered in `TvNavHost` and declared in `core/navigation/Screen.kt`; nothing navigates to it. | `EditProviderScreen.kt` | — |

### A. Shared target: one IA, rendered per platform

Seven groups, **same order on both platforms**:

| # | Group | Contents (existing settings only) | Scope |
|---|---|---|---|
| 1 | **Profiles** | Profile list, add / edit / delete. Hint row → "Content filters for <profile> are set per source" → opens active source's filters. | device + synced |
| 2 | **Source & guide** | Active source summary + subscription; **Switch source**, **Edit this source**, **Guide sources** (→ EpgManagement(active)). EPG status row becomes tappable → same (A-4). **Guide auto-refresh** (moved from EPG Management, A-9). | source / device |
| 3 | **Playback** | Watch delay; Autoplay next episode; pointer row "Per-source playback (auto-resume, recent row size, stream format) → Edit this source". | device / profile |
| 4 | **Display** | Theme, Look & feel, Language; TV only: Text & grid size (renamed from "Category/Grid UI Scale"). | device |
| 5 | **Live sync** | Status + open screen. | sync group |
| 6 | **Backup & storage** | Export / Import; Shrink database; **Guide data maintenance** (Cleanup / Purge / Clear all guide data — moved from EPG Management, A-9). Quick Import from Downloads and shrink stats only in developer mode. | device |
| 7 | **About & advanced** | About (version, build hash, build time — same on both, A-11); Developer mode; Diagnostics (dev mode). | device / profile |

Within Edit Source (both platforms) the same grouping, top to bottom / left to right:

1. **Connection** — type, name, URL, user, password + **Save connection** right under them.
2. **Behaviour** — "Changes here apply immediately": auto-resume, recent row size, stream
   format, playlist type, caching.
3. **Content filters · <profile>** — own group, near the top (A-8).
4. **Library data** — Sync now, last sync, totals, per-type counts.
5. **Danger zone** — clear favourites, clear progress, clear all cached library, clear per type
   (each keeps its existing confirm dialog; per-type Clear hidden at 0 items).

EPG Management (both): title "Guide sources · <source>"; guide source list first; empty state with
"Add guide source"; bulk actions (refresh stale / retry failed / refresh-delete selected) stay;
global blocks gone (moved per above).

Source actions (both): one consistent order — **Edit**, Use, Guide sources, Duplicate, Copy to…,
then **Delete** last and separated.

### A. Shared visual rules (each platform implements in its own components)

- **Selected ≠ action ≠ focused.** Selected option = accent outline + ✓ + accent text on resting
  container (never filled). Primary action = filled. TV focus = existing scale + border.
  One accent colour for selection everywhere (kills the orange chips).
- **Hierarchy:** at most one primary per screen section; navigation buttons are secondary/text;
  red only in Danger zones and confirm dialogs.
- **Scope chip** on every setting: "This device" / "This profile" / "This source" / "Synced".
- **Dialogs:** confirm = primary, cancel = secondary/text.

### A. Shared code work

| # | Change | Files | Effort | Risk | Why |
|---|---|---|---|---|---|
| A-W1 | **Not a commit of its own** — each phase adds the strings it uses (en / fr / mg), so no phase ships unused strings and any phase can be dropped. Strings needed overall: 7 group titles, 4 scope chips, "Changes here apply immediately", "Guide sources · %s", guide empty state, "Switch source", "Edit this source", "Danger zone", "Content filters · %s", filters hint row. Rename display strings only (keys stay): "Category/Grid UI Scale" → "Text & grid size". | `core/ui/src/main/res/values*/strings.xml` | — | Low | rides with each phase |
| A-W2 | Expose active source id + type in `SettingsUiState` so both Settings screens can link "Edit this source" / "Guide sources" without extra repo calls. Lands with whichever of T3 / M2 comes first. | `core/ui/.../SettingsViewModel.kt` | S | Low |  |
| A-W3 | Optional: a `SettingsScope` enum + a `settingScope(key)` lookup so the chip text isn't hard-coded per card twice. Only if both platforms end up duplicating the mapping. | `core/ui/.../model/` | S | Low | optional |
| A-W4 | Decision D3 (approved): delete dead `Screen.EditProvider` + `EditProviderScreen.kt`. **Done 2026-10-03.** | `core/navigation/Screen.kt`, `tv/.../TvNavHost.kt`, `tv/.../EditProviderScreen.kt` | S | Low | delete only |
| A-W5 | **Remove the cellular buffer setting** (D4). Player stops reading `cellular_live_multiplier` / `cellular_vod_multiplier` and always uses the default 1.0× — otherwise anyone who set 2.0× keeps it forever with no way to see or undo it. `AppSettings` properties and the `cellularLiveMultiplier` / `cellularVodMultiplier` fields in the export JSON **stay** (import of older export files must keep parsing; values are simply ignored). **Must land in the same commit as M2b** — on its own, the mobile sliders would stay on screen and do nothing. | `core/player/.../StreamingPlaybackService.kt` | S | Med | playback buffer config; check `AdaptiveLoadControl` defaults on cellular |
| A-W6 | **Lock Source Type in edit mode** (D2): type shown read-only on both platforms; `addSourceTypes(isDevMode, editedType)` only used in Add mode. **Done 2026-10-03**, TV verified (type field disabled, first focus on the Name edit button); mobile verified (type field disabled, dropdown gone). | `core/ui/.../model/` (if `addSourceTypes` needs a flag), both Add/Edit screens | S | Low |  |

No `AppSettings` key, default, scope or sync payload changes.

---

## Part B — TV only

### B. Problems

| # | Problem | Evidence |
|---|---|---|
| T-1 | **~6 screens / ~25 D-pad presses** to reach About. No way to jump to a group. | s01–s07 |
| T-2 | **Bug: watch delay shows nothing selected out of the box.** Default 10 s (`AppSettings.DEFAULT_WATCH_DELAY_SECONDS`), chips are 5/15/30/60. (Mobile uses a 5–120 text field, so it's fine there.) | `PlaybackSettingsCard.kt` |
| T-3 | **Checked switch row and selected chip both fill blue** — on Live sync the checked "Share what's playing" row reads as the focused item. | `TvSwitchRow`, `TvSelectableButton` |
| T-4 | Inconsistent containers: Playback and UI scale have no `GlassPanel`; every other card does. Theme + Look share one panel. | `PlaybackSettingsCard.kt`, `UiScaleSettingsCard.kt` |
| T-5 | **2×2 grids for 4 options** double each block's height and make Left/Right ambiguous. | theme, look, scale, delay |
| T-6 | Accidental one-press changes: focus lands on a theme/look chip, one stray OK applies it (happened during this review). | — |
| T-7 | Header "Settings" only — no hint the profile-scoped values belong to Kid. | — |
| T-8 | **Sources: row itself not focusable**, initial focus on "+" not the active source. | `ProviderSelectionScreen.kt` |
| T-9 | **Bug: Back from Edit Source lands on "+"**, not on the row's ⋮. Seen on build `c8e76c61`; `393a6405` (on `main`) wires Manage Sources' overflow/Edit into `NavReturnFocus` — re-check before fixing. | — |
| T-10 | ⋮ menu opens with focus on Cancel; Edit is 3rd. | `ProviderDialogs.kt` |
| T-11 | **Edit Source is a 600 dp column centred on 1920 px** — ⅔ of the screen empty, ~4 screens of scroll; Update at the bottom. Indents inconsistent (Stream Format ~14 dp, Category Filters ~50 dp); headings centred, rows left. | `TvAddProviderScreen.kt`, `TvDimensions.formFieldWidth` |
| T-12 | Edit Source first focus = Source Type dropdown (editable on an existing source). | — |
| T-13 | Profile edit dialog: Cancel and Save both filled blue. | `ProfilesSettingsCard.kt` |
| T-14 | Live sync: "Remove" is a loud orange button on every device row; "Leave" floats alone. | `SyncSettingsScreen.kt` |

### B. Target

**Two-pane Settings** (Android TV / Google TV convention):

```
┌──────────────────────────────────────────────────────────────────────┐
│ Settings                                         (K) Kid · iptv      │
├────────────────────┬─────────────────────────────────────────────────┤
│   Profiles         │  Playback                                       │
│   Source & guide   │                                                 │
│ ▸ Playback       ◀ │  Count as watched after              10 s   ›   This device
│   Display          │  Play next episode automatically      (●)       This profile
│   Live sync        │  Per-source playback (auto-resume…)  iptv   ›   This source
│   Backup & storage │                                                 │
│   About & advanced │                                                 │
└────────────────────┴─────────────────────────────────────────────────┘
```

- Rail ~30% width, the 7 shared groups. Up/Down swaps the pane live; Right enters the pane;
  Left/Back from the pane returns to the same rail row; Back on the rail leaves Settings (keep the
  "no provider" `BackHandler`). Returning from a child screen restores the rail row and the
  control via `rememberNavReturnFocus` (already used).
- Header: profile avatar + name, active source (T-7).
- Pane renders the **existing cards**; cards move onto a new `SettingsRow`
  (`tv/.../feature/settings/components/SettingsRow.kt`): title, one-line description, current
  value or switch on the right, scope chip, left-aligned, inside one `GlassPanel` per group (T-4).
- `TvSelectableButton` / `TvSwitchRow`: shared visual rule (T-3).

#### Choice settings: value row + drill-in picker (replaces every 2×2 button grid)

Theme, Look & feel, Text & grid size, Watch delay and Language all become **one row each** that
shows the current value (`Theme · Deep Night ›`). No buttons in the pane at all for these.

OK on the row **drills in**: the right pane is replaced by a one-column list of the options, in
the same place (Google TV settings pattern — no modal on top, no 2-D grid):

```
│ ▸ Display        ◀ │  ‹ Theme                                        │
│                    │    ✓ Deep Night        ◀ focus starts here       │
│                    │      AMOLED Black                               │
│                    │      Amethyst                                   │
│                    │      Teal                                       │
```

- Focus opens **on the current value**. Up/Down only. OK = apply and go back to the row, focus
  on the row. Back = leave unchanged, focus on the row.
- One mechanism for all five (Language's dialog becomes the same drill-in; its Activity recreate
  stays on OK).
- Fixes T-5 (no grids, no Left/Right ambiguity) and T-6 (a stray OK in the pane can only open a
  picker, never change a value; changing takes two deliberate presses).
- Watch delay options: 5 / 10 / 15 / 30 / 60 / 120 s (T-2). A stored value outside the list
  (mobile allows any 5–120) shows as an extra "45 s (custom)" option, checked, so nothing is lost.
- Swatch preview: Theme options show a small 4-colour strip next to the name; Text & grid size
  shows "Aa" at that size. Static, no live preview of the whole app.
- Implementation: pane state `picker: SettingPicker?` in `SettingsScreen.kt`; one
  `SettingsPickerPane(title, options, selected, onPick, onBack)` composable. No dialogs.

#### TV focus contract (applies to Settings and every child screen)

App-wide version, root causes and the shared primitives (`tvPane`, row action menu, focus-walk
script) live in Part II (TV focus). Build T3's two-pane on its
`tvPane` primitive (that plan's Phase 2) rather than a one-off.

Today's focus problems come from mixed layouts: grids, rows holding 2–3 buttons, non-focusable
cards between focusable ones, dialogs opening on Cancel, Back losing position. The contract:

1. **One focus stop per row.** A row is the focusable thing; its OK does the row's one job. Rows
   that need several actions (Sources, guide sources) use labelled trailing buttons in
   **fixed-width slots**, so Up/Down stays in the same column. (Content rows in the app — channels,
   titles, episodes — use the long-press menu instead: Part II P3/P3a.)
2. **No 2-D grids anywhere in Settings.** Options are vertical lists.
3. **Up/Down never skips content.** Every block that shows information is either focusable
   (opens something) or sits inside a focusable row. No dead text blocks between stops.
4. **Left always goes back one level** (pane → rail, picker → row). Right never jumps across
   the screen; it only reaches a row's trailing slots. One exception, shared with Part II:
   a tabbed panel (the Live TV channel panel) uses Left/Right for its tabs; Back leaves it.
5. **Entry focus is the useful item:** the current value in pickers, the active source on
   Sources, Name on Edit Source, the first action (not Cancel) in menus, "Add guide source" on an
   empty guide list.
6. **Back restores the exact control** that opened the screen, at the same scroll position
   (`rememberNavReturnFocus` everywhere, not just Settings).
7. **Destructive actions are never the first or the default focus**, and always confirm.

Verification adds a focus walk per screen: press Down until the end, Up until the start, and
check by `uiautomator dump` that every focus stop is a row/slot from the list above and the order
is top-to-bottom with no jumps.

**Sources:** each row one focusable `TvInputListItem`, **OK = Edit**; trailing labelled buttons
in fixed slots — **Use** (slot kept on active row), **Guide** (slot kept when no guide), **⋮**
(T-8). Initial focus = active row; Back returns to the opening control (T-9). Menu in shared
order, focus on Edit, Cancel row dropped — Back closes (T-10).

**Edit Source (edit mode only; Add untouched):** two columns, full safe-area width (T-11):

```
┌ Connection ────────────────┐  ┌ Behaviour · applies immediately ─────┐
│ Type   Xtream IPTV         │  │ Auto-resume                     (●)  │
│ Name   jellyxtream      ✎  │  │ Recent row size   25           Edit  │
│ URL    http://…:8080    ✎  │  │ Stream format     [m3u8] [ts]        │
│ User   tahiry           ✎  │  │ Playlist type     [m3u_plus][simple] │
│ Pass   ••••••••         ✎  │  │ Caching                         (●)  │
│ [Cancel] [Save connection] │  ├ Content filters · Kid ──────────────┤
└────────────────────────────┘  │ Exclude · no filters     [Manage…]   │
                                ├ Library data ───────────────────────┤
                                │ Last sync … · 850 items  [Sync now]  │
                                ├ Danger zone ────────────────────────┤
                                │ Clear favourites · progress · cache  │
                                └──────────────────────────────────────┘
```

Type is read-only (D2, A-W6); first focus = Name (T-12).

**Dialogs / Live sync:** T-13 Cancel secondary. T-14 order: status + Sync now → Add a device →
share switch → devices (Remove secondary) → Danger zone (Leave).

### B. Phases (one commit each)

| Phase | Scope | Files | Effort | Risk | Why |
|---|---|---|---|---|---|
| T1 | Quick wins, no layout change: T-2, T-3 (Part II P5 later extends the same tokens to all lists), T-4, T-13, button hierarchy, EPG row focusable (A-4). **Done 2026-10-03**, verified on the TV emulator (10 s selected ≠ 120 s focused; Guide Sources row opens EPG Management and Back returns to it; switch rows no longer tinted when on). T-13 done via `CinemaDialogActionButton`'s `colors` (resting container + accent text); a `secondary` flag on that core composable can come with T2. | `PlaybackSettingsCard.kt`, `TvSelectableButton.kt`, `TvSwitchRow`, `UiScaleSettingsCard.kt`, `EpgSettingsCard.kt`, `ProfilesSettingsCard.kt`, `LiveSyncSettingsCard.kt`, `ExportImportSettingsCard.kt` | M | Med | `TvSelectableButton`/`TvSwitchRow` are app-wide; visual regression surface |
| T2 | `SettingsRow` + scope chips + drill-in pickers for theme / look / text size / watch delay / language (T-5, T-6); focus contract applied to the Settings list. | new `SettingsRow.kt`, new `SettingsPickerPane.kt`, all cards, `LanguageSettingsCard.kt` | L | Med | new picker pane; Language recreate path |
| T3 | Two-pane Settings, shared IA, header (T-1, T-7); dev-only gating. | `SettingsScreen.kt`, `TvNavHost.kt` | L | High | nav + focus + saveable state; needs Part II `tvPane` |
| T4 | Sources list + menu + focus (T-8, T-9, T-10, A-10). | `ProviderSelectionScreen.kt`, `ProviderDialogs.kt` | M | Med | focus return + menu order |
| T5 | Edit Source two-column + shared grouping (T-11, T-12, A-6, A-7, A-8). | `TvAddProviderScreen.kt`, `ProviderSettingsSection.kt`, `CacheManagementSection.kt` | L | Med | 699-line screen; two save models; Add mode must not change |
| T6 | EPG Management scoping + Live sync order (A-9, T-14). | `TvEpgManagementScreen.kt`, `SyncSettingsScreen.kt` | M | Med | moves global controls; 1 186-line EPG screen |

---

## Part C — Mobile only

### C. Problems

| # | Problem | Evidence |
|---|---|---|
| M-1 | **No section headers; every block is a card with its own title** — 12 cards, ~3.5 screens; nothing to scan. Not the Android settings idiom (grouped list rows with title/summary, tap to change). | `SettingsScreen.kt` |
| M-2 | **Full-width filled buttons everywhere** — Manage Sources, Configure Cellular Buffers, Open Diagnostics, Live sync Manage, Export are all filled blue; they look like the selected theme chip next to them (A-5). | m_s1–s3 |
| M-3 | **Theme / Look & feel as 2×2 button grids** where selected = filled `CinemaButton`, unselected = `CinemaOutlinedButton` — looks like 1 primary action + 3 secondary ones. | `ThemeSettingsCard.kt` |
| M-4 | **Watch delay is a free-text number field** (5–120) — opens the keyboard for a setting; a value row → picker (or slider with stops) is the phone idiom. Range and storage unchanged. | `PlaybackSettingsCard.kt` |
| M-5 | **Profile rows have no affordance** — tapping opens edit, but nothing (chevron, ripple hint) says so. | `ProfilesSettingsCard.kt` |
| M-6 | **Developer mode card mid-page**, and when on it pushes two full-width primary buttons (Cellular Buffers, Diagnostics) into the user-facing flow. | `DeveloperSettingsCard.kt` |
| M-7 | **Cellular buffers** (Live TV / VOD multipliers) hidden behind developer mode, with a third save model ("Apply Changes"). **Decision D4: remove.** | `MobileCellularBufferSettingsScreen.kt` |
| M-8 | **Sources: 6 icon-only actions per row** (use, guide, duplicate, copy-to, edit, delete), red trash one tap away, icons shift per row; card tap not obvious. | `ProviderSelectionScreen.kt` |
| M-9 | Edit Source: big red full-width **Clear All Favorites / Clear All Progress** directly under queue size; **orange** selected chips; "Update Source" at the very bottom after ~3 screens; no Cancel, back arrow silently drops unsaved connection edits. | `MobileAddProviderScreen.kt`, `ProviderSettingsSection.kt`, `DataManagementSection.kt` |
| M-10 | EPG Management: top of screen is Refresh Stale, System Status, Maintenance (big red Clear All Data), Auto-Refresh; the source's own guide sources start below the fold. Three guide sources share the same label "10.0.2.2 (Bulk)" with nothing to tell them apart but the truncated URL. | `MobileEpgManagementScreen.kt` |
| M-11 | Live sync card: three button styles in one card (filled Add a device, outlined Scan a code, text Sync now). | `MobileSyncSettingsScreen.kt` |
| M-12 | About: version + tagline, no build hash/time (A-11). | `AboutSettingsCard.kt` |

### C. Target

**Grouped preference list** (Material settings idiom), single scroll, the 7 shared groups as
section headers, no per-setting cards:

```
←  Settings                         (A) atr
PROFILES
  (A) atr  · Using on this device          ›
  (K) Kid                                   ›
  + Add profile
  Content filters are set per source        ›
SOURCE & GUIDE
  jellyxtream · expires Oct 3 2027, 5 conn. ›     → Edit this source
  Switch source                             ›
  Guide sources · 8.8k ch, 338k prog.       ›
  Guide auto-refresh · Daily 02:00          ›     This device
PLAYBACK
  Count as watched after · 10 s             ›     This device
  Play next episode automatically      (●)        This profile
DISPLAY
  Theme · Deep Night                        ›     This device
  Look & feel · Material                    ›
  Language · English                        ›
LIVE SYNC
  Synced 1 min ago · 10.0.2.2:8787          ›
BACKUP & STORAGE
  Export settings / Import settings
  Shrink database
  Guide data maintenance                    ›
ABOUT & ADVANCED
  Fijerena v1.0.0 · build c8e76c61
  Developer mode                       (●)        This profile
  Diagnostics                               ›     (dev mode)
```

- New `SettingsListRow` (`mobile/.../feature/settings/components/`): leading icon optional,
  title, summary = current value, trailing chevron / switch, scope chip under the summary. Tap a
  value row → existing dialog pattern (Language already does this): radio list for theme, look,
  watch delay (presets 5/10/15/30/60/120 + "Custom…" keeps the free 5–120 field for any stored
  value outside the presets) (M-1, M-3, M-4).
- Profile rows get chevrons (M-5).
- Developer mode + Diagnostics move to the last group (M-6).
- **Cellular buffers removed** (M-7, D4): delete `MobileCellularBufferSettingsScreen.kt`, the
  `onCellularBuffers` button in `DeveloperSettingsCard.kt`, the `composable<Screen.CellularBufferSettings>`
  in `MobileNavHost.kt` and `Screen.CellularBufferSettings` in `core/navigation/Screen.kt`, plus
  its now-unused strings. Player side is A-W5 (lands first or in the same commit).
- Header shows active profile avatar (parity with TV T-7).

**Sources:** card tap = Edit (with chevron); visible inline actions reduced to **Use** (text
button, hidden on active) and **⋮** overflow (Guide sources, Duplicate, Copy to…, Delete last,
red) (M-8, A-10).

**Edit Source:** shared grouping, single column. "Save connection" + "Cancel" directly under the
password field; Behaviour / Filters / Library data / Danger zone below as list sections; Danger
zone actions as outlined red text buttons, not filled. Back with unsaved connection edits →
"Discard changes?" dialog. Selected chips use the shared accent rule, not orange (M-9).

**EPG Management:** guide list first (A-9). Each source row shows label + host/path tail so the
three "10.0.2.2 (Bulk)" rows are distinguishable (M-10; label text unchanged, only the secondary
line shows more of the URL).

**Live sync:** Add a device = primary, Scan a code = outlined, Sync now = outlined (same weight as
Scan) (M-11).

### C. Phases (one commit each)

| Phase | Scope | Files | Effort | Risk | Why |
|---|---|---|---|---|---|
| M1 | Quick wins: button hierarchy (M-2), chip colour (M-9 part), profile chevrons (M-5), EPG row tappable (A-4), About build info (M-12). **Done 2026-10-03**, verified on the phone emulator (outlined actions, chevrons, EPG row opens EPG Management, About shows build hash/time). | `ProviderSettingsCard.kt`, `DeveloperSettingsCard.kt`, `LiveSyncSettingsCard.kt`, `ExportImportSettingsCard.kt`, `ProfilesSettingsCard.kt`, `EpgSettingsCard.kt`, `AboutSettingsCard.kt`, `ProviderSettingsSection.kt` | S | Low |  |
| M2 | `SettingsListRow` + grouped list in shared IA order + value-row pickers (M-1, M-3, M-4, M-6). | `SettingsScreen.kt`, all cards | L | Med | new row component + pickers; every card rewritten |
| M2b | Remove cellular buffer screen and route (M-7, with A-W5). | `MobileCellularBufferSettingsScreen.kt`, `DeveloperSettingsCard.kt`, `MobileNavHost.kt`, `core/navigation/Screen.kt`, strings | S | Low | with A-W5 (Med) |
| M3 | Sources list + overflow menu (M-8, A-10). | `ProviderSelectionScreen.kt`, `ProviderCopyDialogs.kt` | M | Med | overflow replaces 6 inline actions |
| M4 | Edit Source grouping, save/cancel placement, discard dialog (M-9, A-6, A-7, A-8). | `MobileAddProviderScreen.kt`, `ProviderFormSection.kt`, `ProviderSettingsSection.kt`, `DataManagementSection.kt` | L | Med | discard dialog + save placement |
| M5 | EPG Management order + labels; Live sync buttons (M-10, M-11, A-9). | `MobileEpgManagementScreen.kt`, `MobileSyncSettingsScreen.kt` | M | Med | moves global controls |

---

## Order of attack

1. **A-W4** — delete the dead `EditProvider` route. Own commit, any time.
2. **A-W6** — lock Source Type in edit mode, both platforms, one small commit.
3. **TV T1–T6** and **Mobile M1–M5** in order within each platform; the two platforms are
   independent and can be interleaved. A-W2 rides with the first of T3 / M2. **T3 (two-pane)
   waits for Part II's Phase 2 (`tvPane`)** so it is built on the shared primitive.
4. **A-W5 + M2b** — one commit (player ignores multipliers + mobile screen removed).

## Shippable after every phase

Yes — every phase leaves both apps working, with every setting reachable and nothing half-moved.
What the user sees after each one:

| Phase | App state after it ships |
|---|---|
| A-W4 | No visible change. |
| A-W6 | Type shown read-only when editing a source (both). |
| T1 | Same TV list; 10 s selected by default; selected vs focused distinct; consistent panels/buttons; EPG row opens guide sources. |
| T2 | Same TV list order; each choice is one row + drill-in picker; scope chips. |
| T3 | TV two-pane with the 7 groups. Guide auto-refresh / maintenance still on EPG Management (moved in T6). Filters hint row opens Edit Source (top); jumping straight to the filters group comes with T5. |
| T4 | TV Sources: labelled, aligned row actions; focus fixes; menu order. |
| T5 | TV Edit Source two columns + danger zone; filters hint row now lands on filters. |
| T6 | TV EPG Management per-source only; global guide controls now in Settings; Live sync reordered. |
| M1 | Same mobile list; consistent buttons/chips; EPG row tappable; About shows build info. |
| M2 | Mobile grouped list in shared order, value rows + pickers, watch delay presets + Custom. Cellular buffers button still under developer mode (removed in M2b). |
| M2b + A-W5 | Cellular buffers gone; player uses defaults. |
| M3 | Mobile Sources: tap row = Edit, Use + ⋮ overflow. |
| M4 | Mobile Edit Source grouped, Save/Cancel under login, discard prompt. |
| M5 | Mobile EPG Management per-source only; global controls in Settings; Live sync buttons. |

Rules that keep it that way:

- **Moves happen in one commit, both ends.** A control leaves its old screen in the same commit
  that adds it to the new one (A-9 in T6 / M5) — never "gone here, not there yet".
- **Strings travel with the phase** that uses them, all three languages (A-W1).
- **Two-pane state survives a language change:** Language's OK recreates the Activity, so the
  selected rail row and open picker live in `rememberSaveable` (T3).
- **Between T6 and M5 the platforms differ** (guide auto-refresh in Settings on TV, on EPG
  Management on mobile). Both settings are per device, so nothing conflicts — only the location
  differs for a while.
- No Room migration, no `AppSettings` key change, no sync payload change in any phase. No
  existing unit test covers these screens (core tests only touch `AppSettings` / sync /
  repositories), so the per-phase check is the scripted emulator walk in Verification.

Each commit updates this plan (Done note, Status) and `docs/FEATURES.md`,
`docs/NAVIGATION_GUIDE.md`, `docs/RELEASE_NOTES.md`.

## Verification

**Shared (once, on TV):** before/after diff of `shared_prefs/app_settings.xml` and the provider
`settings` JSON for one toggle per scope — same keys, same values. Reachability checklist: every
control listed in A / B / C problems is found in the new layout.

**TV (`scripts/tv-focus-walk.sh` from Part II, Phase 1 — key sequence in, focused text out):** walk rail Up/Down, assert each pane's
titles; Back pane → rail → Home; Back from Sources / Edit Source / Live sync / Diagnostics / EPG
Management returns to the opening control; fresh profile shows 10 s selected. Never press OK on
Update / Clear / Delete / Leave.

**Mobile (scripted taps by text):** each group header present in order; each value row opens its
picker with the current value checked; watch delay with a stored non-preset value (e.g. 45 s)
shows "Custom · 45 s"; Back with edited URL shows the discard dialog. Same never-press list.

## Decisions (2026-10-03)

1. **TV layout: two-pane.**
2. **D2: lock Source Type** when editing an existing source, both platforms (A-W6).
3. **D3: delete the dead `EditProvider` route** (A-W4).
4. **D4: remove the cellular buffer setting** altogether (A-W5 + M2b).
5. **Guide maintenance moves to Backup & storage.**
6. **Mobile watch delay: presets + Custom.** Same preset list on TV (5 / 10 / 15 / 30 / 60 /
   120 s), custom value shown as an extra checked option there.
7. **TV choice settings: value row + drill-in picker**, no grids (asked 2026-10-03: "not 2 rows?
   A dropdown maybe?"). Plus the TV focus contract in Part B.

### D3 — the dead `EditProvider` route

`Screen.EditProvider` (`core/navigation/Screen.kt`) and `EditProviderScreen.kt` (189 lines, TV)
are an older "change the server URL and re-login" screen. `TvNavHost` registers it but nothing
navigates to it — Edit on a source opens `Screen.AddProvider(editId = …)`. Approved 2026-10-03:
delete both, own commit (A-W4). No user-visible change.

---

# Part II — TV focus navigation and Live TV flows

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

Settings and its child screens are covered by Part I (Settings);
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

| Phase | Scope | Main files | Effort | Risk | Why |
|---|---|---|---|---|---|
| LT1 | **L-1 regression**: second push uses `navigate(...)` (as `onSignInRequired` already does, with the same comment). Re-check the docs' claim holds. **Done 2026-10-03**, verified on the TV emulator: Home → Live TV opens the preview on the last channel; Back → browse → Home. | `TvNavHost.kt` | S | Low | one line; verify Back stack after |
| LT2 | **Context list**: preview panel shows the list the channel was chosen from, with tabs Category / Recent / Favorites; zap order = the same list; Left/Right on rows no longer toggle lists (tabs do); row actions `ON_FOCUS_RIGHT` in both lists until P3 lands, then P3. Empty-tab state (9). | `LiveTvSplitLayout.kt`, `StreamList.kt`, `TvCategoryGridScreen.kt`, `PlayerScreen.kt` (`channelList` param replaces `categoryStreams`/`recentStreams`) | L | High | 732-line split layout; playback engine coupling; ANR history |
| LT3 | **One panel**: `LiveChannelPanel` docked + overlay; delete `TvChannelListOverlay` and the two flyouts; Left/Right in full screen open it; Left/Right inside = tabs; Back/outward closes; focus opens on current. | new `LiveChannelPanel.kt`, `PlayerScreen.kt`, `PlayerKeyHandler.kt`, `LiveTvSplitLayout.kt` | L | High | overlay focus in the player (see `02ecc6a5`) |
| LT4 | **OSD**: labelled buttons, Channels first + entry focus, Guide when EPG, timer reset on key, Up/Down zap with OSD up, banner content, single title, dev-gated codec line; delete the dead `ControlHintsOverlay` + `showControlHints`/`hints_dismissed`. | `TvPlayerControlsOverlay.kt`, `PlayerKeyHandler.kt`, `PlayerEffects.kt`, `PlayerScreenState.kt`, `ControlHintsOverlay.kt` | M | Med |  |
| LT5 | **Zap/tune feedback** (5) + preview column content (6) + settle 800 ms. | `PlayerScreen.kt`, `LiveTvSplitLayout.kt` | M | Med | keeping the last frame may need surface handling |
| LT6 | **Back/focus** (7): browse focuses the playing channel; `NavReturnFocus` keyed by playing stream id. | `TwoColumnLayout.kt`, `StreamList.kt`, `TvCategoryGridScreen.kt` | M | Med |  |
| LT7 | **Single nav entry** (10) — scheduled right after LT6. | `TvNavHost.kt`, `TvCategoryGridScreen.kt`, `LiveTvSplitLayout.kt`, `docs/NAVIGATION_GUIDE.md` | XL | High | nav architecture; documented back-stack semantics; do last |

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
| P3a | Scope of P3: **content rows** (channels, titles, episodes, guide cells) — hundreds of rows, actions secondary. Rows whose actions *are* the row's point and that number a handful (Sources, guide sources in Settings) keep **visible labelled slots** (Part I T4). Not a contradiction: two row kinds, one rule each. | — |
| P4 | `TvSearchField` — focusable read-only until OK; OK opens the IME; IME Done/Back closes it and keeps focus on the field. Right from the field reaches the trailing button. | R7 |
| P5 | Highlight tokens: focus = existing scale + border + lighter fill; **selected/current** = accent left bar + accent text on the resting container; never a fill or full outline. Applied to `TvInputListItem`, category rows, episode rows, `TvSelectableButton`, `TvSwitchRow`. | R6 (shared with Part I T-3) |
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

| Phase | Scope | Main files | Effort | Risk | Why |
|---|---|---|---|---|---|
| 1 | P6 focus-walk script + expected sequences for the screens walked here (records today's behaviour; later phases update the expectations). **Done 2026-10-03** — walks recorded on the TV emulator the same day (today's behaviour: F-H-2, F-C-3/4/5/6 reproduce). | `scripts/tv-focus-walk.sh`, `scripts/focus-walks/*.txt` | M | Low | tooling only |
| 2 | P1 + P2; apply to category screens (Live TV / Movies / TV Shows). | new `TvPane.kt`, `TwoColumnLayout.kt`, `CategoryList.kt`, `StreamList.kt` | L | High | Compose focus APIs are quirky; touches all category screens |
| 3 | P3 row action menu; remove hidden row buttons in category lists and Live preview. | `StreamList.kt`, `LiveTvSplitLayout.kt`, `FavoriteMenuDialog.kt` | M | Med | behaviour change: hidden buttons removed; long-press on tv-material `Surface` |
| 4 | Home entry/header focus; dead Live TV card. | `ContentTypeSelectionScreen.kt` | S | Low |  |
| 5 | **Live TV flows LT1–LT7** (own table in that section; LT1 is a one-line regression fix and can go first of everything). | see LT table | — | — | see LT table |
| 6 | Movie details + Episodes. | `MovieDetailsScreen.kt`, `EpisodeSelectionScreen.kt` | L | Med | `EpisodeSelectionScreen` is 2 320 lines |
| 7 | P4 Search field. | new `TvSearchField.kt`, `SearchScreen.kt` | M | Med | IME behaviour on TV |
| 8 | P5 highlight tokens across lists (lands with or after Part I T1, which changes `TvSelectableButton` / `TvSwitchRow`). | `TvFocusTokens`, `TvInputListItem.kt`, list rows | M | Med | visual regression across all lists |
| 9 | Guide round: walk TV Guide + EPG Browser with an indexed guide; fix with P1/P2. | `EpgGridLayout.kt`, `TvEpgBrowserScreen.kt` | — | — | became Part III GD6 |

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

## Relation to Part I

- Part I, Part B's focus contract = this plan's rules; `tvPane` (P1) is what the Settings
  two-pane (T3) should be built on. **Do P1/P2 (this plan's Phase 2) before Part I T3**, or
  build T3 with them.
- P5 and Part I T1 touch the same components — land Part I T1 first, P5 extends it.
- Row actions: P3 (long-press menu) is for content rows; Settings' Sources rows keep visible
  labelled slots (Part I T4) — see P3a. Part I's contract rule 1 and Part II agree.
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

---

# Part III — TV Guide (grid), TV + mobile

**Status:** Approved (2026-10-03) — not started.

Asked 2026-10-03: "epg search is great. The whole concept of the in player tv guide is
confusing and unusable. I've never used it. Both on the tv and mobile. … scratch the whole
feature or make it not shitty. I'd prefer making it work."

Walked the same day on both emulators with the **bearstv** source (the only one on either
emulator with guide data; approved for this walk, both profiles switched back afterwards):

- TV — Television_1080p, Kid. First with an **empty** XMLTV index (the guide fell back to the
  provider's native EPG), then after a forced guide refresh (80 MB, 8 343 channels, 182 k
  programmes, ~20 s).
- Mobile — Pixel_10, atr, with the index it already had (a day old).

Screens: Live TV category screen → "TV Guide" for Recent, 4K| RELAX and FR| GÉNÉRAL HD/4K;
date navigation; "Now"; in-guide search; OK/tap on a programme and on a channel; Back.
Code: `EpgViewModel`, `XmltvEpgService`, `TvEpgGuideScreen` + `EpgGridLayout`,
`MobileEpgGuideScreen` + `MobileEpgTimeline`, the nav wiring in both hosts.

Verdict: **keep the feature, rebuild the grid.** The data pipeline underneath (XMLTV download,
FTS index, the EPG Browser on top of it) is sound — it is what makes "EPG search" good. The
grid is a separate, per-category view bolted onto that pipeline with its own cache, its own
search, its own layout code on each platform, and a layout that cannot align by construction.
Nothing about it is salvageable as a screen; everything under it is.

## III.A What it is today

- Reached only from the Live TV category screen's header icon ("TV Guide"), only when a
  category is selected and the source has native EPG or the index is non-empty. Not from the
  player, the preview, Home (Home has "EPG Search", the browser) or Settings.
- `EpgViewModel.loadEpgDataInternal`: `repository.getItems(categoryId).take(50)` →
  `getEpgBulkForItems` → per-day rows; 48 × 30-min slots. `XmltvEpgService`: a 12-hour
  parsed-results cache in SharedPreferences, then the index, then the Xtream
  `get_simple_data_table` fallback.
- TV: `EpgGridLayout` — channel column + one `LazyRow` per channel + a `LazyRow` time header,
  **all sharing one `LazyListState`**. Mobile: `MobileEpgTimeline` — per channel, a title and
  its own `LazyRow` of fixed-width chips with gap placeholders.

## III.B Findings

### Shared (data and model — both platforms show these)

| # | Finding | Evidence / cause |
|---|---|---|
| G-1 | **Silent 50-channel cap.** `take(50)`; categories with hundreds of channels show the first 50, no notice, no paging. | `EpgViewModel.kt:108`; "26/50" in the title |
| G-2 | **Recent / Favourites guide shows the wrong channels.** "TV Guide – Recent" lists `##### 4K #####`, `4K: V SPORT`… — the catalogue's first 50 — on both platforms. The repository doesn't resolve the virtual category ids (`recent`, `favorites`), only `CategoryViewModel` does. | TV G2/G5, mobile MG0; `MediaRepository.getItems` |
| G-3 | **"N/50 channels matched" counts empty answers.** 4K| RELAX: "50/50 matched", every native response `[]`; FR| GÉNÉRAL via native EPG: 99 of 102 cached payloads empty, the rest dated **2026-09-29**. The grid renders blank (TV) or a day-long "No program found" chip per channel (mobile) instead of saying "no listings". | `xtream_epg_cache` dump; TV G7/G11; mobile MG0 |
| G-4 | **12-hour parsed cache hides a fresh guide.** After the index was refreshed, the guide kept showing the cached empty result until the header Refresh was pressed. Nothing tells the user the guide is cached, or how old the data is. | `XmltvEpgService.PARSED_CACHE_TTL_MS`; TV G14 after Refresh |
| G-5 | **Stale data is indistinguishable from no data.** The phone's index was a day old: TF1 had listings until 7:50 AM then "No program found" for the rest of the day; "Next Day" entirely empty. The native fallback was 4 days old. The guide never shows which source fed it or when it was updated. | mobile MG2/MG4; `epg_source.last_ingested_at_ms` |
| G-6 | **No way in from where you watch.** Not reachable from the player OSD, the preview, Home or Settings; only from a category header, and only when a category is selected. "In-player TV guide" does not exist. Two features with guide-ish names: "TV Guide" (grid) and "EPG Search" (browser). | `TvNavHost`, `MobileNavHost`, OSD buttons |
| G-7 | **Category-marker rows are channels.** `##### 4K #####`, `#### GÉNÉRAL HD/4K ####` appear as the first row of the guide (TV focus starts on them) and are playable from lists (opened one by accident during the walk). | TV G2, G11; T1 |
| G-8 | **"Now" and the day window.** "Now" reloads today but neither scrolls to the current time nor focuses the on-air programme (TV: header stays at 12:00 AM; mobile: no visible effect). The first cell/focus target is yesterday's programme that straddles midnight (10:50 PM Tfou). | TV G14/G16; mobile MG3 |
| G-9 | **Two searches.** The grid's search is a title filter over the 50 loaded rows (TV: replaces the grid with a bare text field; mobile: a results list); the EPG Browser searches the whole index. Back in grid-search mode **leaves the guide** on both platforms (query lost). | TV G19; mobile MG6 |
| G-10 | **Dev stats in the title** ("| 26/50 channels matched | 192ms") — on mobile the title wraps to three lines. | `TvEpgGuideScreen`, `MobileEpgGuideScreen` |
| G-11 | **Index was empty on the TV emulator** although `epg_source` said bearstv had 182 k programmes ingested on 2026-10-02 (`epg_programme` = 0 rows, `epg_index_metadata` empty) until a forced refresh. Root cause not found in this round (candidates: a clear path — safe mode "Clear caches", `clearAll` — or the staging swap on a killed process). Worth its own check; it is why the Settings walk saw "not yet indexed" with 5 sources configured. | `epg_index.db` before/after |

### TV (`EpgGridLayout`)

| # | Finding | Cause |
|---|---|---|
| G-T1 | **The grid cannot align, by construction.** The time header (48 slot items) and every programme row (N programme items) share **one `LazyListState`**; a `LazyListState` scrolls by item index, and bound to several lists it follows whichever attached last. So "scroll to slot 20 (10:00 AM)" means "item 20" in each row; rows with fewer items show nothing; the header stays at 12:00 AM. | `EpgGridLayout.kt` `horizontalScrollState` shared by `TimeHeaderRow` and `ProgramRow` |
| G-T2 | **Wrong horizontal scale.** Header: 120 dp = 30 min. Cells: 120 dp = 60 min, with a **1-hour minimum** for any shorter programme. A 10:50 PM programme sits under "12:00 AM–3:00 AM"; 4:45 / 4:50 / 5:50 AM cells are each an hour wide. | `calculateProgramWidth`, `TvDimensions.epgTimeSlotWidth` |
| G-T3 | No "now" line; on-air shown only by cell colour; past programmes not dimmed. | `ProgramCell` |
| G-T4 | **Focus.** Entry on the first (marker) channel, not the on-air programme of a real channel. Right from a channel → its *first* programme of the day (ended hours ago). Up/Down keep the item index, not the time. Up from any row → "Previous Day" in the header. Left from a channel does nothing. Header icon buttons turn into a blank white pill on focus (icon invisible). | `requestInitialFocus`, geometric focus search, `EpgHeader` |
| G-T5 | Header: unlabeled ‹ Now › ⟳ 🔍; search mode = the grid replaced by a bare text field; Back in search mode exits the guide. | `EpgHeader`, `EpgSearchContent` |
| G-T6 | ✓ OK on a channel or programme opens the preview on that channel; Back returns to the cell. | — |
| G-T7 | Load 5–8 s for 50 channels on first open (native path), 3.5 s on refresh, a bare spinner meanwhile. | `EpgViewModel` |

### Mobile (`MobileEpgTimeline`)

| # | Finding | Cause |
|---|---|---|
| G-M1 | **No time axis.** Each channel is a heading plus its own horizontally scrolling chip row; rows scroll independently; nothing lines up vertically; you cannot see what is on across channels at one time. | `ChannelTimelineRow`, one `LazyListState` per row |
| G-M2 | Chips are fixed ≥ 140 dp regardless of duration; gap chips "12:00 AM – 12:00 AM / No program found" span whole days; a channel without data still costs a full row. | `ProgramChip`, `fillGapsWithPlaceholders`, `epgProgramMinWidth` |
| G-M3 | "Now" has no visible effect; date nav is ‹ date › + Now; search replaces the screen; Back in search exits the guide. | `DateNavigationRow`, `MobileEpgSearchContent` |
| G-M4 | The category you get a guide for is whichever chip is selected in a strip that the preview dock covers; the TV Guide icon gives no hint which category it will open. | `MobileCategoryListScreen` `CategoryChipRow` |
| G-M5 | Title wraps to three lines with the dev stats. | `MobileEpgGuideScreen` |

## III.C Target: one time grid, fed by the index, reachable from where you watch

```
TV                                                  Mobile
┌ Guide · FR| GÉNÉRAL ▾     Today ▾   Now   🔍  ⋮ ┐  ┌ ‹  Guide · FR| GÉNÉRAL ▾        🔍 ⋮ ┐
│ 24 of 50 channels have listings · bearstv · 2 h ago│  │ Today  Tomorrow  Sat             Now │
│          10:00     10:30     11:00  ▏11:30   12:00 │  │        10:00   10:30 ▏11:00   11:30  │
│ TF1 HD   [Le journal ..][Grands reportages........]│  │ TF1 HD [Le jou..][Grands reportages..]│
│ TF1 4K   [Le journal ..][Grands reportages........]│  │ TF1 4K [Le jou..][Grands reportages..]│
│ FRANCE 2 [Télématin][Le jour..][Bel et bien.......]│  │ FR 2   [Télé..][Le jo..][Bel et bien.]│
│ FRANCE 3 [Moi à ton..][Anatole L....][Teen T......]│  │ FR 3   [Moi..][Anatole L..][Teen T...]│
└──────────────── ▏ = now, past dimmed ─────────────┘  └──────────────────────────────────────┘
```

1. **One data model, `GuideViewModel`** (replaces `EpgViewModel`): the channel set is a
   `ChannelContext` — the same type Part II's Live TV section introduces: `Category(id)`,
   `Recent`, `Favorites`, `AllWithListings`. Virtual categories resolved the way
   `CategoryViewModel` does (G-2). Marker rows (`#####…`) excluded (G-7). Rows paged in windows
   of ~30 as the user scrolls — no 50 cap (G-1). Time window now − 1 h … + 24 h, loaded per
   visible rows from the **index** (the browser's data path). Native Xtream fallback kept only
   for sources whose index has nothing, and labelled.
2. **Honest state** (G-3, G-4, G-5): `Loading` / `Ready(rows, listedCount, source, updatedAt)` /
   `NoListings(reason)` / `NoGuide` (no guide source → a link to Settings → Source & guide, Part
   I). The header's second line always says "N of M channels have listings · <source> · updated
   <relative>". The parsed-results cache is dropped on index swap, and keyed by index
   generation, so Refresh is never needed to see a new guide.
3. **One layout engine, both platforms**: `core/ui` `GuideLayout` — px-per-minute scale,
   time → x, visible-window culling. Each platform draws a fixed channel column and a time
   canvas with **one `ScrollState`** shared by the header and every row (a `Row`/`Layout` of
   cells placed by start time, not a `LazyRow` per row). Cell width = duration × scale with a
   small minimum for the label (text truncates; the slot keeps its true width). Vertical now
   line; past cells dimmed; on-air cell highlighted with the P5 "current" style (G-T1, G-T2,
   G-T3, G-M1, G-M2).
4. **TV interaction** (Part II focus contract): entry focus = the on-air cell of the first real
   channel, or the playing channel when opened from the player/preview. Up/Down keep the
   **time** (land on the cell under the same x); Left/Right move by programme; "Now" scrolls to
   now and focuses the on-air cell; Channel Up/Down page rows. Header reached by Up from the
   first row only, as a row of labelled buttons (Date, Now, Search, More). OK on a cell opens a
   details panel (title, time, description, Watch / Watch channel); OK on a channel tunes.
   Long-press = row actions (P3). Back: details → grid, search → grid, grid → where it came from.
   (G-T4, G-T5)
5. **Mobile interaction**: same grid; tap a cell = details bottom sheet; tap a channel = tune
   (dock); date as tabs (Today / Tomorrow / weekday) instead of ‹ ›; "Now" chip; horizontal
   pinch optional later. (G-M3)
6. **Entry points** (G-6): OSD "Guide" button (Part II LT4) with context = the playing list;
   preview column "Guide" row; Home "TV Guide" (context = Recent, decision 3 ✓); category header
   icon stays (context = category, and the icon's tooltip names it, G-M4). From the guide, a
   channel opens the preview/dock as today (G-T6).
7. **One search** (G-9): the grid's search icon opens the existing EPG Browser with the grid's
   context pre-filtered ("in this category" toggle, default on); Back returns to the grid. The
   grid's own title filter is removed. Naming: "TV Guide" = grid, "Search the guide" = browser
   (decision 2 ✓).
8. **Dev stats** move to the dev overlay / Diagnostics, out of the title (G-10).
9. **G-11** gets a dedicated check (why the TV's index was empty) before GD1 lands.

## III.D Phases (one commit each, each shippable)

| Phase | Scope | Main files | Effort | Risk | Why |
|---|---|---|---|---|---|
| GD0 | Investigate G-11 (empty index despite "ingested"). **Done 2026-10-03** — cause found, see III.H. | `EpgIndexer.kt`, `EpgFileManager.kt`, safe-mode clear path | M | Low | investigation |
| GD0b | **Fix G-11** (III.H): on the staging path write a source's `markIngested` stats and validators only after `swapAndRebuildFts` commits; send `If-None-Match` / `If-Modified-Since` and allow the hash skip only when the index holds rows for that source; call `resetAllIngestionState()` next to both `clearAll()` call sites (check it doesn't queue settings-sync records). | `EpgFileManager.kt`, `SafeModeViewModel.kt`, `EpgSourceDao.kt` | M | Med | ingestion ordering; verify with a kill mid-refresh on the emulator |
| GD1 | **Data honesty, no layout change**: resolve virtual categories (G-2); skip marker rows (G-7); `NoListings`/`NoGuide` states + "N of M · source · updated" line (G-3, G-5); drop the parsed cache on index swap (G-4); dev stats out of the title (G-10). Existing TV/mobile screens show the states. | `EpgViewModel.kt`, `XmltvEpgService.kt`, `MediaRepository.kt`, both guide screens, strings ×3 | M | Med | cache invalidation + virtual categories + states on both UIs |
| GD2 | **`GuideLayout` + TV grid** rebuilt on it: one scroll state, time-placed cells, now line, dimmed past; TV focus rules (entry on on-air cell, Up/Down keep time, labelled header row, Back closes search not the guide). | new `core/ui/.../guide/GuideLayout.kt`, `EpgGridLayout.kt` → `TvGuideGrid.kt`, `TvEpgGuideScreen.kt` | XL | High | new layout engine; 50×N cells perf; TV focus |
| GD3 | **Mobile grid** on `GuideLayout`: shared time axis, date tabs, Now, details sheet. | `MobileEpgTimeline.kt` → `MobileGuideGrid.kt`, `MobileEpgGuideScreen.kt` | L | Med |  |
| GD4 | **Paging / no cap** and performance: windows of ~30 rows, time-window queries, culling; 50-channel categories load < 1 s from the index on the emulator. | `GuideViewModel.kt`, `XmltvEpgService.kt` / DAO | L | High | DAO/time-window queries; paging under focus |
| GD5 | **Entry points + naming + one search**: OSD Guide (after Part II LT4), preview row, Home TV Guide, category tooltip; grid search → browser with context; rename strings. | `PlayerScreen.kt`, `LiveTvSplitLayout.kt`, `ContentTypeSelectionScreen.kt`, both nav hosts, browser screens, strings ×3 | M | Med | both nav hosts; OSD part waits for LT4 |
| GD6 | TV details panel, long-press row actions in the grid (P3), focus-walk expectations for the guide (Part II Phase 9 folds into this). | `TvGuideGrid.kt`, `scripts/focus-walks/guide.txt` | M | Med |  |

Dependencies: GD0b is independent of the rest and should land early (it is why the guide was empty on the test TV). GD1 before everything else (it defines the states the grids render). GD2 before GD3
(shared layout lands with its first user). GD5's OSD button needs Part II LT4; the rest of GD5
doesn't. Part II Phase 9 (guide focus round) becomes GD6.

Shippable after each: GD0/GD1 change what the current screens *say*, not how they look; GD2
ships the new TV grid while mobile keeps the chip rows (both honest since GD1); GD3 brings
mobile level; GD4–GD6 are additive. Strings travel with their phase, all three languages.

## III.E Verification

- **Alignment**: with a known programme (e.g. TF1 "Le journal de 13h", 13:00–13:40 Paris), the
  cell's left edge sits under the 13:00 header tick (device time zone applied) on both
  platforms; the now line matches the device clock; scrolling the header scrolls the rows.
- **States**: Recent guide lists the Recent channels; a category whose channels have no
  listings shows `NoListings` with the reason; a source without a guide source shows `NoGuide`
  with the Settings link; after a forced index refresh the grid updates without pressing
  Refresh.
- **TV focus walk** (`scripts/tv-focus-walk.sh`): open from category / OSD / Home; entry focus =
  on-air cell; Up/Down keep time; Back from search → grid; Back from grid → opener.
- **Mobile**: tap cell → sheet; tap channel → dock; date tabs; search → browser and back.
- **Perf**: cold open of a 50-channel category from the index < 1 s on the emulator; 300-channel
  category pages without jank (frame time log).
- Never press OK/tap on Clear/Delete anywhere; guide refreshes only on the test source.

## III.F Decisions (2026-10-03)

1. **Native Xtream EPG fallback stays**, labelled "from <source>" with its date — the only
   data for sources without an XMLTV guide.
2. **Naming:** "TV Guide" = the grid, "Search the guide" = the browser; the grid's search icon
   opens the browser pre-filtered to the grid's context; the grid's own title filter is removed.
3. **Home entry context = Recent.**

## III.H GD0 findings — why the index was empty while the source said "ingested" (2026-10-03)

**Cause 1 (confirmed).** Source stats are written per source *before* the staging swap commits.
`EpgFileManager.ingestDownloadedSource` calls `markIngested(...)` right after `ingestFromStream`
(`EpgFileManager.kt:1483`) — `last_ingested_at_ms`, `last_channels`, `last_programmes`,
`ingest_method` **and the validators** `etag` / `last_modified_header` / `last_content_sha256`.
On the staging path the rows live in `epg_channel_staging` / `epg_programme_staging` until
`swapAndRebuildFts(syncedIds)` runs after *all* sources of the run have finished
(`EpgFileManager.kt:814`; single-source variant `:1046`); the swap is one transaction and writes
the `epg_index_metadata` row inside it (`EpgIndexer.kt:515-567`). A process kill, WorkManager
stop, Doze cancellation or swap exception (caught at `EpgFileManager.kt:846`) between bearstv's
`markIngested` (08:51) and the swap leaves exactly what was observed: stats + validators in
`providers.db`, zero rows and no metadata in `epg_index.db`. With five sources the window is
wide (bearstv ingests in ~20 s; the other four were still downloading).

**Why it never self-heals.** The next run wipes staging (`:617` / `:943`), then `downloadSource`
sends the stored validators (`:1185-1186`): a `304` → `markUnchanged` (`:1302-1313`), a hash match
→ the same (`:1317`, `.gz` at `:1420`; the 24 h `STALENESS_FORCE_INGEST_MS` guard applies to the
hash path only, not to `304`). Every source "unchanged" or failed → `anyIngested` false (`:798`)
→ no swap → index stays empty, and `getStaleSources` won't pick the source again until it is
stale. `EpgSyncWorker`'s `force` (`:96`) only widens the source list; it doesn't bypass the
validators — the forced refresh on 2026-10-03 worked because the upstream file had changed.

**Cause 2 (mechanism confirmed; unknown whether it fired here).** Both clear paths destroy
`epg_index.db` without touching `epg_source`: `SafeModeViewModel.clearCaches`
(`SafeModeViewModel.kt:43`) and `EpgFileManager.launchClearAllData` (`:1135`) → `EpgIndexer.clearAll()`
(`EpgIndexer.kt:699`). `EpgSourceDao.resetAllIngestionState` (`EpgSourceDao.kt:116-123`) exists for
exactly this and has **no callers**. After a clear, the stale validators hit the same skip.

**Ruled out.** (d) metadata is written inside the swap transaction, so "no metadata row" is only
the signature of "swap never committed"; `initialize()`'s count fallback is fine. No
`EpgIndexDatabase` version bump in the window (destructive migration is at v17 since
`dba6a079`). Shrink Database deletes index rows and source rows together. `git log -S`
2026-09-28…10-02: nothing touched the ordering; the 304/hash skip dates from `e528a312` (08-27).

**Fix** = GD0b above (effort M): defer `markIngested` to after the swap on the staging path
(~40 lines in `EpgFileManager.kt`, keep the current order for `useStaging = false`); send
validators / allow `canSkipIngest` only when `EpgIndexDao.getLatestProgrammeEndTimeForSource(id)
!= null`; call `resetAllIngestionState()` next to both `clearAll()` calls (check settings-sync
side effects first — `epg_source` rows are synced since live sync phase 4b).

## III.G Side effects of this walk on the emulators

- TV: bearstv's guide was force-refreshed (index now 4 418 channels / 177 799 programmes,
  on-disk `epg_index.db` grew accordingly); Kid switched iptv → bearstv for the walk and
  **back to iptv**; TF1 and a few 3ABN/24 Horas channels added to Kid's Recent; one
  `##### RELAX #####` marker row opened by accident.
- Mobile: atr switched jellyxtream → bearstv for the walk and **back to jellyxtream**; TF1 (UHD)
  added to atr's Recent.


---

# Parallel lanes

Four lanes can run at once; within a lane the order is fixed. Each lane is its own branch /
worktree (symlink the gitignored `local.properties` into a worktree before building); every
phase still lands as one commit on `main` (rebase, don't merge-commit).

| Lane | Items, in order | Files it owns | Verifies on |
|---|---|---|---|
| **1 Mobile settings** | M1 → M2 → M3 → M4 → M5; M2b together with A-W5 | `mobile/**` only | phone |
| **2 TV focus + Live TV** | Part II Phase 1 → 2 (`tvPane`) → 3 (row menu) → LT1 → LT2 → LT3 → LT4 → LT5 → LT6 → LT7; then GD5 (its OSD button) and GD6 | `TwoColumnLayout`, `CategoryList`, `StreamList`, `LiveTvSplitLayout`, `PlayerScreen`, `PlayerKeyHandler`, overlays, `TvNavHost` | TV |
| **3 Core + guide** | A-W4, A-W6, GD0 → GD1 → GD2 → GD3 → GD4 | `core/**`, `feature/epg/**` on both platforms, `core/ui` guide layout | TV, then phone |
| **4 TV settings** | T1 → T2 → T4 → T5 → T6; **T3 only after lane 2's Phase 2 has landed** | `tv/feature/settings/**`, `tv/feature/provider/**`, `TvSelectableButton`, `TvSwitchRow` | TV |

Singletons with no dependency, for any lane with slack: Part II Phase 4 (Home), Phase 6 (Movie
details + Episodes), Phase 7 (Search field).

## Where lanes touch — serialize

- **T1 before Part II Phase 8** (highlight tokens): both edit `TvSelectableButton` /
  `TvSwitchRow`. Phase 8 stays in lane 4 after T1.
- **T3 after Phase 2** (`tvPane`).
- **GD5 after LT4** (OSD Guide button) — GD5 edits `PlayerScreen`, `LiveTvSplitLayout` and both
  nav hosts, so it runs in lane 2, not lane 3. **GD6 after GD2 and Phase 3.**
- **GD1 before GD2/GD3** (GD2/GD3 rewrite the screens GD1 patches).
- `TvNavHost` is edited by T3, LT1, LT7, GD5; `MobileNavHost` by M2b, GD5 — small hunks; rebase
  in that order.
- `strings.xml` ×3: every phase appends. Each phase keeps its strings in its own block at the
  end of each file so conflicts stay trivial.

## The real limit: emulators, not code

One TV AVD, one phone AVD, and the phone stays off when not needed (RAM). Lanes 2, 3 and 4 all
verify on the TV, so their **verification** serializes even when the coding doesn't; lane 1 is
fully parallel on the phone. Expect three lanes coding, one verifying on the TV at a time.
Never two lanes driving the same emulator — the walk script asserts focus by text and a second
driver would corrupt its run.

# Order of attack (all parts)

With the lanes above, "order" means *within a lane*; across lanes everything starts at once:

1. **Day 1, all lanes start:** lane 1 M1; lane 2 Phase 1 then LT1 (one line, user-visible);
   lane 3 A-W4, A-W6, GD0; lane 4 T1.
2. Lane 2: Phase 2 → 3 → LT2…; lane 3: GD1 → GD2…; lane 4: T2, T4, T5, T6, then T3 once
   Phase 2 is on `main`; lane 1 straight through M2–M5.
3. Lane 2 picks up GD5 (after LT4) and GD6 (after GD2); singletons (Phases 4, 6, 7) go to
   whichever lane frees up first; Phase 8 to lane 4 after T1.

Each commit updates this plan (Done note per phase, Status at the top) and the affected docs
(`docs/FEATURES.md`, `docs/NAVIGATION_GUIDE.md`, `docs/RELEASE_NOTES.md`, `docs/epg_guide.md`
for Part III, `AGENTS.md` for the focus contract).
