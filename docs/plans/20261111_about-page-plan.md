# About Page

**Status:** Done 2026-11-11 (one phase), TV and phone, walked on both emulators.

## Goal

Settings → About opens a dedicated **About Fijerena** page on TV and phone. The page holds all of
the legal and project information: the version, the notice that Fijerena provides and hosts no
content, the copyright and licence notice, Terms of Use, the open-source licences, the data
credits, and links to the source code and the latest release, each with a QR code.

## Decisions (2026-11-11)

- **Entry:** Settings keeps one About entry (the TV rail group, the phone section). Its first row,
  About Fijerena, opens the page. The 7-tap Admin unlock moves with the version to the page.
- **Device Info and Logs** stay in Settings → About, under About Fijerena. They are not legal text.
- **Links:** Source Code, Latest Release, Privacy Policy and Report a Problem. The release link is
  the GitHub release page (`/releases/latest`): the APK names carry the version, so a direct link
  to an APK does not stay valid.
- **Credits:** TMDB's logo and attribution (moved from Settings), and a new sports data credit
  (ESPN, NHL, MLB).
- **No content notice:** every "does not provide or host content" text is on the page (the
  version block and Terms of Use). The Add Source notice stays where it is and names the new path.

## Design

- `ProjectLink` (core:ui `legal/ProjectLinks.kt`): each link's title, description and address.
- TV `AboutScreen`: one focusable row per block (focus contract rule 3). Focus starts on the
  version row. A link row opens `ProjectLinkDialog` (a QR code beside the address, Close); focus
  returns to the row when the dialog closes. Terms and Licences use `NavReturnFocus`.
- Phone `MobileAboutScreen`: the version block (7 taps), then the Links, Legal and Credits groups.
  A link's dialog has the QR code, the address, Open in Browser and Close.
- Route `Screen.About`, part of `isSettingsArea` and `isSettingsTree`.

## Progress

| Phase | What | State |
|---|---|---|
| 1 | Page, links with QR dialogs, Settings rows, focus test and CI walk, docs | Done 2026-11-11 |
