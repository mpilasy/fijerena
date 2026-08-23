# Plan: Home Screen UX Flow - Open Items

## Status
5 of 6 findings from the Home Screen UX Flow Audit are fixed and landed in `main` (`4beed037`, `893c2c4e`). 

The remaining open finding is an Information Architecture (IA) / naming item awaiting a product decision.

---

## Open Item — Reconcile EPG Guide vs. EPG Browser Naming & IA

- **Context:** `Home` -> book icon -> `EpgBrowser` (programme-title search); `CategoryList(LIVE_TV)` -> guide icon -> `EpgGuide` (time-slot TV guide grid).
- **Issue:** The book icon on Home is frequently interpreted by users as "TV Guide", but it actually opens the local XMLTV title search tool. The channel-by-time grid grid a user expects when seeking a "guide" sits one level deeper inside the Live TV category screen.
- **Action Required:** Make a product decision on IA/naming:
  1. Relabel/re-icon `EpgBrowser` to read explicitly as "Search EPG" / "Title Search".
  2. Or add a direct Home-level shortcut into `EpgGuide` for the active channel, retaining `EpgBrowser` as the deeper power-user tool.
