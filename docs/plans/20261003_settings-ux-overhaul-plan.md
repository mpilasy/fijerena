# Settings UX Overhaul Plan (TV + Mobile)

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

| # | Change | Files |
|---|---|---|
| A-W1 | **Not a commit of its own** — each phase adds the strings it uses (en / fr / mg), so no phase ships unused strings and any phase can be dropped. Strings needed overall: 7 group titles, 4 scope chips, "Changes here apply immediately", "Guide sources · %s", guide empty state, "Switch source", "Edit this source", "Danger zone", "Content filters · %s", filters hint row. Rename display strings only (keys stay): "Category/Grid UI Scale" → "Text & grid size". | `core/ui/src/main/res/values*/strings.xml` |
| A-W2 | Expose active source id + type in `SettingsUiState` so both Settings screens can link "Edit this source" / "Guide sources" without extra repo calls. Lands with whichever of T3 / M2 comes first. | `core/ui/.../SettingsViewModel.kt` |
| A-W3 | Optional: a `SettingsScope` enum + a `settingScope(key)` lookup so the chip text isn't hard-coded per card twice. Only if both platforms end up duplicating the mapping. | `core/ui/.../model/` |
| A-W4 | Decision D3 (approved): delete dead `Screen.EditProvider` + `EditProviderScreen.kt`. | `core/navigation/Screen.kt`, `tv/.../TvNavHost.kt`, `tv/.../EditProviderScreen.kt` |
| A-W5 | **Remove the cellular buffer setting** (D4). Player stops reading `cellular_live_multiplier` / `cellular_vod_multiplier` and always uses the default 1.0× — otherwise anyone who set 2.0× keeps it forever with no way to see or undo it. `AppSettings` properties and the `cellularLiveMultiplier` / `cellularVodMultiplier` fields in the export JSON **stay** (import of older export files must keep parsing; values are simply ignored). **Must land in the same commit as M2b** — on its own, the mobile sliders would stay on screen and do nothing. | `core/player/.../StreamingPlaybackService.kt` |
| A-W6 | **Lock Source Type in edit mode** (D2): type shown read-only on both platforms; `addSourceTypes(isDevMode, editedType)` only used in Add mode. | `core/ui/.../model/` (if `addSourceTypes` needs a flag), both Add/Edit screens |

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
script) live in `docs/plans/20261003_tv-focus-navigation-plan.md`. Build T3's two-pane on its
`tvPane` primitive (that plan's Phase 2) rather than a one-off.

Today's focus problems come from mixed layouts: grids, rows holding 2–3 buttons, non-focusable
cards between focusable ones, dialogs opening on Cancel, Back losing position. The contract:

1. **One focus stop per row.** A row is the focusable thing; its OK does the row's one job. Rows
   that need several actions (Sources, guide sources) use labelled trailing buttons in
   **fixed-width slots**, so Up/Down stays in the same column. (Content rows in the app — channels,
   titles, episodes — use the long-press menu instead: focus plan P3/P3a.)
2. **No 2-D grids anywhere in Settings.** Options are vertical lists.
3. **Up/Down never skips content.** Every block that shows information is either focusable
   (opens something) or sits inside a focusable row. No dead text blocks between stops.
4. **Left always goes back one level** (pane → rail, picker → row). Right never jumps across
   the screen; it only reaches a row's trailing slots. One exception, shared with the focus plan:
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

| Phase | Scope | Files |
|---|---|---|
| T1 | Quick wins, no layout change: T-2, T-3 (focus plan P5 later extends the same tokens to all lists), T-4, T-13, button hierarchy, EPG row focusable (A-4). | `PlaybackSettingsCard.kt`, `TvSelectableButton.kt`, `TvSwitchRow`, `UiScaleSettingsCard.kt`, `EpgSettingsCard.kt`, `ProfilesSettingsCard.kt`, `LiveSyncSettingsCard.kt`, `ExportImportSettingsCard.kt` |
| T2 | `SettingsRow` + scope chips + drill-in pickers for theme / look / text size / watch delay / language (T-5, T-6); focus contract applied to the Settings list. | new `SettingsRow.kt`, new `SettingsPickerPane.kt`, all cards, `LanguageSettingsCard.kt` |
| T3 | Two-pane Settings, shared IA, header (T-1, T-7); dev-only gating. | `SettingsScreen.kt`, `TvNavHost.kt` |
| T4 | Sources list + menu + focus (T-8, T-9, T-10, A-10). | `ProviderSelectionScreen.kt`, `ProviderDialogs.kt` |
| T5 | Edit Source two-column + shared grouping (T-11, T-12, A-6, A-7, A-8). | `TvAddProviderScreen.kt`, `ProviderSettingsSection.kt`, `CacheManagementSection.kt` |
| T6 | EPG Management scoping + Live sync order (A-9, T-14). | `TvEpgManagementScreen.kt`, `SyncSettingsScreen.kt` |

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

| Phase | Scope | Files |
|---|---|---|
| M1 | Quick wins: button hierarchy (M-2), chip colour (M-9 part), profile chevrons (M-5), EPG row tappable (A-4), About build info (M-12). | `ProviderSettingsCard.kt`, `DeveloperSettingsCard.kt`, `LiveSyncSettingsCard.kt`, `ExportImportSettingsCard.kt`, `ProfilesSettingsCard.kt`, `EpgSettingsCard.kt`, `AboutSettingsCard.kt`, `ProviderSettingsSection.kt` |
| M2 | `SettingsListRow` + grouped list in shared IA order + value-row pickers (M-1, M-3, M-4, M-6). | `SettingsScreen.kt`, all cards |
| M2b | Remove cellular buffer screen and route (M-7, with A-W5). | `MobileCellularBufferSettingsScreen.kt`, `DeveloperSettingsCard.kt`, `MobileNavHost.kt`, `core/navigation/Screen.kt`, strings |
| M3 | Sources list + overflow menu (M-8, A-10). | `ProviderSelectionScreen.kt`, `ProviderCopyDialogs.kt` |
| M4 | Edit Source grouping, save/cancel placement, discard dialog (M-9, A-6, A-7, A-8). | `MobileAddProviderScreen.kt`, `ProviderFormSection.kt`, `ProviderSettingsSection.kt`, `DataManagementSection.kt` |
| M5 | EPG Management order + labels; Live sync buttons (M-10, M-11, A-9). | `MobileEpgManagementScreen.kt`, `MobileSyncSettingsScreen.kt` |

---

## Order of attack

1. **A-W4** — delete the dead `EditProvider` route. Own commit, any time.
2. **A-W6** — lock Source Type in edit mode, both platforms, one small commit.
3. **TV T1–T6** and **Mobile M1–M5** in order within each platform; the two platforms are
   independent and can be interleaved. A-W2 rides with the first of T3 / M2. **T3 (two-pane)
   waits for the focus plan's Phase 2 (`tvPane`)** so it is built on the shared primitive.
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

**TV (`scripts/tv-focus-walk.sh` from the focus plan, Phase 1 — key sequence in, focused text out):** walk rail Up/Down, assert each pane's
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
