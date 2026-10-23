# UI themes: a packaged colour theme and Look and Feel

**Status:** Built 2026-10-21 (Phases 1–4), checked on the TV and phone emulators. Known limit: a device that syncs while it still runs a build without these two settings drops them (seen on the phone emulator); it gets them at the next change.

## Goal

A profile picks one **UI theme**:

- A **packaged** UI theme sets the colour theme and the Look and Feel together. While it is in
  use, the two rows show its values and cannot be changed.
- **Custom** (the default) keeps today's behaviour: the colour theme and the Look and Feel are
  picked one by one.

A separate switch, **Poster in the background**, turns off the blurred wash of the focused card's
picture (TV Home and Live TV; phone Home, Live TV and the category lists) and the sharp title art
at the top right of TV Home. The theme's own background shows instead. The UI theme does not set
it (user, 2026-10-21). Off also saves work and memory: no poster decode for the wash and no
full-screen blur layer (Android 12 and later), no colour sample of the poster (Android 11), and no
full-size title art decode on TV Home.

## Decisions (user, 2026-10-21)

| # | Decision |
|---|---|
| U1 | A packaged UI theme forces the colour theme and the Look and Feel. Custom makes both free. |
| U2 | The poster switch covers the blurred wash and the TV Home title art (user, after a screenshot). |
| U3 | The poster switch stays separate from the UI theme: no UI theme sets it. (Replaces "Console, Meadow and Meadow dusk turn the poster background off".) |
| U4 | Meadow (day, a light theme) is Expert only, as Daylight is. |
| U5 | Build the concept now; each new look's packaged UI theme is added when the look is built. |
| U6 | Four packaged UI themes from the platform-inspired Looks, named as the Look: Standard (Material Look + Deep night), Rounded (Cupertino Look + Midnight), Sharp (Roku Look + Amethyst), Balanced (Bravia Look + Pure black). |

## Design

- **Storage.** Two new per-profile settings, synced like the colour theme: `ui_theme_id`
  (string, default `custom`) and `poster_backdrop` (boolean, default on). An unknown id (from a
  newer build) counts as Custom.
- **Forcing.** `PackagedUiTheme` (core/network: id, colour theme id, Look id, Expert only) and
  `UiThemes.resolveLook()` give the look in use. `AppSettings.themeId` and `uiStyleId` return the
  packaged values while a packaged UI theme is in use, so every reader (both `MainActivity`s, the
  profile pages) sees them, and an older build that changes the colour theme cannot break the
  packaged look. Picking a packaged UI theme also writes its two values, so an older build shows
  the same look.
- **Names.** `uiThemeNameRes()` (core/ui): a packaged UI theme takes its Look and Feel's name;
  Custom has its own string.
- **Poster switch.** `observeLook()` sends a `ProfileLook` (theme, Look, poster). Each
  `MainActivity` provides `LocalPosterBackdrop`; `AmbientBackdrop` (both apps) drops the image
  when it is off, and TV Home also drops the title art.
- **Profile page (TV and phone).** In the Look section: **UI theme**, Theme, Look and Feel,
  Poster in the background. With a packaged UI theme, Theme and Look and Feel are disabled and
  show its values.

## Phases

| Phase | Work | Status |
|---|---|---|
| 1 | Storage, sync, `PackagedUiTheme` and `resolveLook()`, `ProfileLook`, `LocalPosterBackdrop`, `AmbientBackdrop` (TV and phone) and the TV title art honour it; tests | Done |
| 2 | Profile page rows on TV and phone: the poster switch; rows locked by a packaged UI theme; strings en/fr/mg; FEATURES, RELEASE_NOTES | Done |
| 3a | The UI theme picker row (TV pane, phone dialog) and the four packaged UI themes (U6); tests | Done |
| 3b | Packaged entries for Console, Meadow dusk and Meadow (Expert only) | Done |
| 4 | Re-run the profile page focus walks on the TV emulator (two new rows); phone check | TV done 2026-10-21: `settings.txt` 80 steps matched; picking Sharp shows Amethyst and the Sharp Look with both rows disabled; focus returns to the row of each picker (fix: the Theme and Look return flags were never reset, so after the Theme picker the Look picker sent focus to Theme). Phone done 2026-10-21: the seven UI themes, each locking Theme and Look and Feel |

## Progress

| Phase | Status | Commit |
|---|---|---|
| 1. Storage and poster switch | Done | fa38a071 |
| 2. Profile page rows | Done | d72d0b02 |
| 3a. Picker and four presets | Done | (this commit) |
| 3b. New looks' UI themes | Done | (this commit) |
| 4. Walks | Done (TV 68e84b85; phone checked 2026-10-21: the picker lists Custom and the seven UI themes, and picking one locks Theme and Look and Feel) | (this commit) |
