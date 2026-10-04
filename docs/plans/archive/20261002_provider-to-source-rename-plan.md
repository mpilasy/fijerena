# Rename "Provider" to "Source" in the UI Plan

**Status:** Complete (2026-10-02). Both phases landed. Decisions taken 2026-10-02 (all recommendations accepted, see below).

The app calls everything it plays from a "provider". That word sounds like a paid company, but
the app's providers include Xtream services, M3U playlists, the user's own Jellyfin server, SMB
shares and local files. The proposed user-visible word is **Source**. The catch: the app already
says "source" for EPG (guide) sources, so those need a new name first.

Scope recommended: **visible text only** (string values in all three languages, plus docs).
Code identifiers (`ProviderEntity`, the `providers` table, `providerId`, `ProviderRepository`,
`Screen.ProviderSelection`, string keys such as `provider_add_title`) stay. Renaming them needs a
Room migration and touches ~hundreds of call sites for no user benefit.

Context: `docs/plans/archive/20261002_profile-last-provider-plan.md` (uses the word heavily, history —
not rewritten).

## Decisions (2026-10-02)

All recommendations below were accepted:
1. English: **Source**.
2. EPG sources: **Guide source(s)**.
3. French: **source** (feminine, agreement updated) and **source(s) de guide**.
4. Malagasy: **loharano** and **loharanon'ny fitarihana**; the untranslated provider/source
   strings are translated in the same pass.
5. Scope: visible text only; keys, code identifiers and the database stay.

## Decisions needed (options as proposed)

1. **English word for "provider".** Options:
   - **Source** (recommended) — neutral; fits URL, playlist, server and folder alike; short in
     dialogs ("Add Source", "Switch Source"). Con: clashes with EPG "source" (decision 2); a little
     vague on its own.
   - Library — good for Jellyfin/SMB/local, wrong for Xtream/M3U and Live TV.
   - Service — still sounds like a company; wrong for local files.
   - Server — wrong for M3U playlists and local files.
   - Account — wrong for everything without a login.
2. **What EPG sources become** (must be decided first, otherwise the two collide). Options:
   - **Guide source** (recommended) — "Add Guide Source", "Guide sources", "No guide sources".
     One-for-one swap of "EPG source"; the bare "Source(s)" strings (`epg_add_source`,
     `epg_sources_header`, `epg_delete_source_title`…) become "Guide source(s)" too.
   - TV guide — "TV Guide: 3 configured". Reads well as a section name, awkward for "Delete
     Source?" -> "Delete TV guide?" and for counts ("2 TV guides").
   Either way the technical term "EPG" can stay where it already appears ("EPG sources" ->
   "EPG guide sources" is clumsy, so drop "EPG" from those labels or keep it only as the screen
   title).
3. **French.** Today: **fournisseur / fournisseurs** (45 strings, masculine). Proposed:
   - provider -> **source** (feminine): "Ajouter une source", "Changer de source", "Source
     active". Agreement changes ("le/ce/cible/suivant(s)" -> "la/cette/cible/suivante(s)").
   - guide sources -> **source de guide** / **sources de guide** (recommended), or **guide TV**.
     Today these are "source EPG" (25 strings with "source").
4. **Malagasy.** `values-mg` is only partly translated: core/ui has 631 strings vs 824 in
   English (193 fall back to English), core/network 45 vs 46. Of the provider strings, 10 are
   translated and use **mpampiantrano**; 19 are untranslated English copies ("Add Provider",
   "Providers"…). Of the source strings, 11 are untranslated English copies. The word
   **fantsakana** is already used 16 times in core/ui values-mg, in places that mean a stream or
   channel ("Ovao ny fantsakana", "Ampidiro ny anaran'ny fantsakana…") as well as EPG labels
   ("Hampiditra fantsakana"), so it is ambiguous. Proposal: provider -> **loharano**
   (literally "source/spring") for Source; guide sources -> **loharanon'ny fitarihana** or
   **loharanon'ny gazety TV** — **both are my suggestions; the user must confirm the Malagasy
   wording**. Decide also whether to translate the 19 + 11 untranslated strings in the same pass
   (recommended: yes for the provider/source ones, since the rename touches those lines anyway).
5. **Scope.** Visible text only (recommended) vs. also renaming code identifiers and the
   `providers` table (needs a migration, a `docs/DATABASE_SCHEMA.md` update, a huge diff; not
   recommended).

## Inventory (grep results, 2026-10-02)

Counts are `<string>` entries whose value matches case-insensitively. `core/player`,
`tv` and `mobile` string files contain none (the 2 tv/mobile hits are the `<resources>` tag).

| Language | core/ui provider | core/network provider | core/ui source | core/network source |
|---|---|---|---|---|
| English (`values`) | 46 | 2 | 21 | 4 |
| French (`values-fr`, fournisseur) | 43 | 2 | 21 | 4 |
| Malagasy (`values-mg`, English copies) | 19 | 0 | 11 | 0 |
| Malagasy (translated, mpampiantrano) | 10 | 0 | — | — |

Totals: English 48 provider + 25 source; French 45 + 25; Malagasy 19 untranslated + 10 translated
provider, 11 untranslated source. No string contains both words.

### Strings whose value contains "provider" (English; French and mg use the same keys)

core/network (2):

| Key | English |
|---|---|
| `error_saved_login_lost` | This device lost a saved login … Edit the provider and enter the password again. |
| `settings_export_summary_providers_format` | Providers: %1$s |

core/ui (46):

| Key | English |
|---|---|
| `settings_section_provider_playback` | Provider & Playback |
| `login_footer_text` | Contact your IPTV provider for credentials (here "provider" is the real company: keep it) |
| `provider_add_title` | Add Provider |
| `provider_edit_title` | Edit Provider |
| `provider_name_label` | Provider Name |
| `provider_type_label` | Provider Type |
| `provider_settings_title` | Provider Settings |
| `provider_error_name_required` | Provider name is required |
| `provider_update_button` | Update Provider |
| `provider_clear_cache_all_message` | … remove this provider's cached Live TV, Movies and TV Shows data … |
| `provider_url_placeholder_xtream` | http://provider.example.com (placeholder URL) |
| `provider_selection_title` | Providers |
| `provider_loading` | Loading providers… |
| `provider_no_providers` | No providers configured |
| `provider_delete_confirm_title` | Delete Provider? |
| `provider_delete_confirm_message` | Delete "%1$s"? All cached data for this provider will be removed. |
| `provider_duplicate_title` | Duplicate Provider |
| `provider_duplicate_name_label` | New provider name |
| `provider_copy_to_title` | Copy To Provider |
| `provider_copy_to_target_label` | Target provider |
| `edit_provider_title` | Edit Provider URL |
| `edit_provider_subtitle` | Change Provider URL |
| `edit_provider_url_label` | Provider URL |
| `edit_provider_update_success` | Provider URL updated successfully |
| `edit_provider_update_failed` | Failed to update provider URL |
| `edit_provider_url_placeholder` | Enter new provider URL |
| `settings_provider_section_title` | Provider |
| `settings_provider_manage_button` | Manage Providers |
| `settings_shrink_database_desc` | … leftover data from deleted providers … |
| `settings_import_providers_count_format` | Providers (%1$d) |
| `settings_import_provider_conflict_title` | Provider Conflict |
| `settings_import_conflict_message` | The following provider(s) already exist: … |
| `settings_import_conflict_intro` | The following provider(s) already exist: |
| `content_switch_provider_title` | Switch Provider |
| `content_switch_provider_description_format` | Switch Provider, current provider: %1$s |
| `provider_none_label` | No provider |
| `epg_error_not_supported` | EPG is not supported by this provider |
| `provider_error_add_failed` | Failed to add provider |
| `provider_error_select_failed` | Failed to select provider |
| `provider_error_delete_failed` | Failed to delete provider |
| `provider_error_update_failed` | Failed to update provider |
| `provider_error_save_failed` | Failed to save provider |
| `settings_profiles_description` | … Providers and settings are shared. |
| `profile_delete_confirm_text` | … on every provider will be deleted. |
| `live_sync_desc` | Keep profiles, providers, favourites and watch progress the same … |
| `live_sync_leave_message` | … Its profiles, providers, favourites and history stay on it. |

Not-to-rename exceptions among these: `login_footer_text` ("your IPTV provider" is the
commercial company the user pays; keep "provider", or reword to "your IPTV service") and
`provider_url_placeholder_xtream` (an example URL; change to `source.example.com` only if
desired). Note other strings already say things like "provider-defined categories" only in docs,
not in string resources.

### Strings whose value contains "source" (the clash; all EPG)

core/network (4): `settings_export_summary_epg_sources_format` (EPG Sources: %1$s),
`epg_freshness_no_sources` (No EPG sources), `epg_error_no_sources` (No sources to process),
`epg_error_source_not_found` (Source not found).

core/ui (21): `epg_refreshing_stale` (Refreshing %1$d stale source(s)...), `epg_deleted_sources`,
`epg_no_stale_sources`, `epg_no_failed_sources`, `epg_add_source` (Add Source),
`epg_sources_header` (Sources), `epg_status_processing` (Processing %1$d/%2$d sources),
`epg_last_run_stats` (… %2$d sources in …), `epg_unnamed_source` (Unnamed Source),
`epg_add_source_title` (Add EPG Source), `epg_edit_source_title` (Edit EPG Source),
`epg_clear_db_confirm_message` (… Your source URLs will be preserved.),
`epg_delete_source_confirm_title` (Delete EPG Source?), `epg_delete_selected_confirm_title`
(Delete Selected Sources?), `epg_delete_selected_confirm_message` (… remove %1$d source(s) …),
`epg_delete_sources_count_btn` (Delete %1$d Source(s)), `epg_delete_source_title` (Delete
Source?), `epg_browser_refresh_stale_description` (Refresh stale EPG sources),
`epg_summary_not_indexed` (%1$d source(s) configured, not yet indexed), `epg_summary_no_sources`
(No sources configured), `settings_import_epg_sources_count_format` (EPG Sources (%1$d)).

The same 25 keys exist in French; in `values-mg` 11 of the core/ui ones are untranslated English
copies and the rest use "fantsakana" (core/network mg uses "fantsakana" 4 times, no English).
Note `epg_summary_no_sources` ("No sources configured") would read as the new provider word if
left unchanged, which is exactly the confusion to avoid.

### Hardcoded user-visible text in Kotlin

None found: `grep -E 'Text\(\s*"[^"]*(rovider|ource)'` matches only `resource` in two unrelated
lines. There are 12 `Exception("…provider…")` strings (`No provider set` in `MediaRepository`
x8, `Provider has no $action for id …` in `XtreamItemUnavailableException`, `Failed to connect
to Xtream provider: …` in `XtreamMediaProvider`, plus comments). These are code-level messages
that can reach the user only as the raw `e.message` shown in dev mode; leave them.

## Docs to update

Counts are lines containing "provider" (case-insensitive).

| File | Lines | What to change | What to keep |
|---|---|---|---|
| `README.md` | 18 | Headline "multi-provider media player", "Multi-Provider Support", "switch between multiple providers", "Per-provider encrypted credential storage", "Provider screens", "prompt for provider setup", "Adding a New Provider" | Technical lines naming code: `XtreamMediaProvider.kt`, `MediaProvider` interface, `ProviderType`, `MediaProviderFactory`, `AddProviderScreen`, `ProviderCapabilities`, "Multi-provider API implementations" |
| `docs/FEATURES.md` | 34 | "Provider Types" table header, "Switch active provider from Settings -> Manage Providers", Jellyfin quick connect, settings table rows (Active Provider, Manage Providers, Per-Provider Settings), export/import wording ("Providers", provider name conflicts), profile/live-sync prose | `ProviderRepository`, `BaseM3uMediaProvider.search`, table/column names, "IPTV providers offer minimal descriptions" and "providers routinely carry the same title" (those are the real companies: leave) |
| `docs/NAVIGATION_GUIDE.md` | 22 | Prose in the flow lists and tables ("No provider configured -> Settings", "Provider switch", "Settings-based provider configuration") | Screen names/route code: `ProviderSelection`, `AddProvider`, `EpgManagement(providerId)`, `ProviderViewModel`, `ProviderRepository` |
| `docs/RUN_GUIDE.md` | 3 | "Test provider: Jellyfin as Xtream", "add an Xtream provider with Server URL …" (line 86 is the data-loss warning: "wiping providers" -> optional) | — |
| `docs/RELEASE_NOTES.md` | 88 | Nothing: release notes are history. Add one new entry for this rename in the new wording. | all old entries |

Other docs also say "provider" and are **out of this plan's scope** (technical or history, and
`docs/*.md` is being edited by another session): `docs/DATABASE_SCHEMA.md` (98, table names),
`docs/design.md` (44), `docs/epg_guide.md` (13 provider, 70 "source": needs the EPG rename if
that wording is user-facing), `AGENTS.md` (the "Multi-Provider Support" section and UI
descriptions; add a rule stating the new user-visible vocabulary: UI says "Source", code says
`provider`; EPG ones say "guide source"). Plan files are history.

## Phases

One decision gate, then two commits so the EPG rename lands before "Source" is reused:

1. **Phase 1 — rename guide sources** (25 en + 25 fr + 11 mg strings + `epg_guide.md` wording if
   user-facing). Values only. Verify with `grep -ci 'source'` on the three value files that no
   bare "Source" remains for EPG.
2. **Phase 2 — rename providers** (48 en + 45 fr + 29 mg strings, then docs table above plus
   RELEASE_NOTES entry plus the AGENTS.md vocabulary rule). Values only; keys unchanged.
   Plan, Status and roadmap update ride with each commit (project convention).

If the user prefers, do both in one commit; do phase 1 edits before phase 2 so a search for
"Source" is never ambiguous during the edit.

**Phase 1 done (2026-10-02).** 25 English, 25 French and 22 Malagasy string values now say
"guide source" ("source de guide", "loharanon'ny fitarihana"); the Malagasy pass also translated
the 11 that were still English, and moved the 3 that already used "loharano" for guide sources
off it, so "loharano" is free for Phase 2. `docs/FEATURES.md` says "guide source(s)" where it
meant the user-facing EPG sources; `docs/epg_guide.md` is technical and keeps "source". Left as
is: Malagasy "fantsakana" where it means a channel or stream. Checked: `:tv` builds and the TV
EPG management screen reads "Add Guide Source", "… 3 guide sources in …". French and Malagasy
were reviewed as text only, not on screen.

**Phase 2 done (2026-10-02).** 46 English, 43 French and 38 Malagasy string values now say
"source" / "source" (feminine agreement) / "loharano"; the Malagasy pass also replaced the mixed
"mpampiantrano", "mpanome" and "fantsakana" used for providers and translated the 19 that were
still English. Kept, as decided: `login_footer_text` (the IPTV company) and
`provider_url_placeholder_xtream` (an example URL). Not added: French and Malagasy translations for
keys that never had one (e.g. `settings_section_provider_playback`, `settings_shrink_database_desc`),
which still fall back to English, now "Source & Playback". Docs: README and FEATURES user-facing
wording, the RUN_GUIDE test-source steps; NAVIGATION_GUIDE is code-level and keeps "provider",
as do FEATURES lines where "provider" means the IPTV company. `AGENTS.md` has the wording rule;
RELEASE_NOTES has the entry. Checked: `:tv` and `:mobile` build; on the TV emulator the home
header ("Switch Source, current source: …"), Settings ("Source & Playback", "Source", "Manage
Sources", profiles text), the Sources screen ("Add Source") and Edit Source ("Source Type",
"Source Name", "Source Settings") read correctly. French and Malagasy reviewed as text only.

### Check step

- Grep leftovers: `grep -rniE 'provider' core/*/src/main/res/values*/strings.xml` should show only
  the deliberate exceptions (`login_footer_text`, placeholder URL); `grep -rniE 'fournisseur'
  core/*/src/main/res/values-fr` should be empty (or the same exceptions); `grep -rniE
  'mpampiantrano'` empty; `grep -rniE 'source' …` shows only "Source" (provider sense) and
  "guide source" (EPG sense), never "EPG source".
- Build both flavours (`:tv:` and `:mobile:`) to catch typos in format specifiers (`%1$s`,
  `%1$d`); lint for missing/extra placeholders per language.
- Screens to eyeball on TV and mobile (use the deploy scripts; do not clear device state):
  Settings (the "Source & Playback" section header, the Active Source row, Manage Sources),
  Manage Sources, Switch Source dialog (and its accessibility description), Add Source / Edit
  Source (labels, name-required error, URL labels, update/failed toasts), Duplicate and Copy To
  dialogs, Delete confirmation, Settings import (count line, conflict dialog), Settings export
  summary, Profiles description and delete-profile dialog, Live sync description and leave
  dialog, EPG management (header, add/edit/delete dialogs, stale/failed toasts, summary line,
  last-run stats), empty states ("No sources configured", "No guide sources") and error
  messages (saved-login-lost, EPG not supported). Check French in all of them.

## Risks and notes

- **Keys stay.** `provider_*` keys now hold "Source" text; add a one-line comment at the top of
  each `strings.xml` so a future reader isn't confused. Searching the code for "provider" still
  finds the code concept.
- **French agreement.** Source is feminine, fournisseur masculine: articles, adjectives and
  participles change ("Le(s) fournisseur(s) suivant(s) existe(nt) déjà" ->
  "La/Les source(s) suivante(s) existe(nt) déjà"; "Fournisseur cible" -> "Source cible"; "Mettre
  à jour le fournisseur" -> "Mettre à jour la source"; "Changer de fournisseur, fournisseur
  actuel" -> "Changer de source, source actuelle"). Elision: "de la source", "l'URL de la source".
  The English "(s)" plural forms stay as the strings already use them.
- **Concatenation / format strings.** `provider_delete_confirm_message`, `…_description_format`
  and the count formats take `%1$s`/`%1$d`; keep the placeholders intact. No Kotlin code builds
  these sentences from "provider" fragments (no hardcoded visible text found), so no code edit
  should be needed, but check for any `stringResource` result concatenated with a literal while
  eyeballing screens.
- **"Source" vs "guide source" side by side.** The EPG management screen and Settings sit near
  each other; the two words must read as different things on screen (hence phase 1 first).
- **Real providers vs sources.** The IPTV company is still a provider in `login_footer_text`
  and in docs prose about "IPTV providers"; do not blindly replace.
- **Dev-mode raw errors.** Exception text such as `No provider set` stays as is (raw error
  beneath the friendly message when dev mode is on; project rule), so the word "provider" can
  still appear in dev mode. Document it in the AGENTS.md vocabulary rule.
- **Malagasy.** Wording must be confirmed by the user; it is a bigger gap (193 untranslated
  core/ui strings), so keep this plan to the provider/source strings only.
- **No migration, no schema change**: `docs/DATABASE_SCHEMA.md` needs no update.
- Backups, export/import files and sync payloads use field names, not UI text, so they are
  unaffected.
