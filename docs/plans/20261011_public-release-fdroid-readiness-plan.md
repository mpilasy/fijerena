# Public release and F-Droid readiness

**Status:** Proposed, 2026-10-11. Audit only; nothing built yet. Every decision is made (D1–D11,
2026-10-11); Phase 1 can start. Personal and provider details were scrubbed from the tree on
2026-10-11, ahead of Phase 1. Decided 2026-10-11: the public repository starts from a fresh squashed
snapshot (Option C), and that cut-over is the **last** step of this plan (Phase 8).

The audit was read-only and covered three areas: F-Droid compliance, release readiness, and
UI/UX for a new user. No device runs were done. The UX findings come from reading the code and
the strings and leave out what the 10-03, 10-07 and 10-08 UX plans already settled. Line numbers
are from `02c2ea5`.

**Verdict: not ready for public release, and not acceptable to F-Droid as it stands.** The
engineering itself is in good shape: 840 unit tests, credentials redacted in logs, crash-loop
safe mode, E2E-encrypted sync, complete fr/mg translations, and no analytics, ads or crash
upload. What blocks release is the licensing, the packaging, one proprietary dependency (ML Kit),
and a first-run experience written for the developer rather than a stranger.

---

## Decisions

| # | Question | Recommendation |
|---|---|---|
| D1 | License | **Decided 2026-10-11: GPL-3.0-or-later for the app, AGPL-3.0-or-later for `server/`.** README currently says "proprietary, all rights reserved". `org.jellyfin.media3:media3-ffmpeg-decoder` is GPL-3.0, so a closed APK was already a violation. GPLv3 is compatible with every other dependency (Apache-2.0 androidx/Media3/Ktor, smbj, OFL Manrope). With AGPL, anyone hosting a modified sync server has to publish their changes. |
| D2 | TV and phone packages | **Decided 2026-10-11: separate applicationIds, and the TV keeps `org.njarasoa.fijerena`.** The phone becomes `org.njarasoa.fijerena.mobile`. Existing TV installs upgrade in place; phones move their data by export/import or live sync, and the 1.0 release notes say so. Two F-Droid listings with their own screenshots. |
| D3 | FFmpeg extension | **Decided 2026-10-11: keep it as is, in every build.** F-Droid's inclusion policy accepts prebuilt FLOSS binaries from trusted Maven repositories (Maven Central, Google Maven, OSS Sonatype, JitPack and others), provided they are freely licensed. `org.jellyfin.media3:media3-ffmpeg-decoder` is GPL-3.0 and on Maven Central. GitHub and F-Droid builds are then identical, which the reproducible builds of D6 need anyway. If a reviewer still asks for a source build, the fallback is an F-Droid srclib for Media3 `decoder_ffmpeg`. |
| D4 | TMDB | **Decided 2026-10-11: the user enters their own TMDB key; without one, TMDB is off.** No key is built into any build: remove the `TMDB_API_KEY` `buildConfigField` (core/network/build.gradle.kts:23-29) so a release APK can never ship one extractably. Accept a v3 API key or a v4 read token (`TmdbApiService.authenticate()` already handles both), keep it in the encrypted credential store, carry it in live sync inside the E2E envelope, and send it in exports only if passwords are exported too. Settings needs a field with a "Get a free key at themoviedb.org" hint, a Test button, and TMDB's required attribution ("This product uses the TMDB API but is not endorsed or certified by TMDB" plus their logo) in About. Expect F-Droid to add **NonFreeNet** even though it is opt-in. Also: `language` is pinned to `en-US`; use the app language. |
| D5 | Distribution | **F-Droid and GitHub Releases for 1.0; Google Play later.** Play's requirements (targetSdk 36, a hosted privacy policy, the Data safety form, IPTV policy review) become an after-1.0 phase. |
| D6 | F-Droid signing | **Your signature, through reproducible builds.** F-Droid rebuilds each release, checks it matches the developer-signed APK and ships that APK, so users can move between GitHub and F-Droid builds without losing data. That makes F8/F9 (deterministic `BUILD_TIME`, `dependenciesInfo`) and stripping nondeterminism hard requirements; check reproducibility in Phase 3, right after R8 is on: two clean builds from different checkouts compared with `apksigcopier compare` / `diffoscope`, then once more before the RFP. Usual breakers: a different JDK (F-Droid builds with its own), baseline-profile ordering, timestamps (F8). **If F-Droid's build never matches, it signs with its own key:** a GitHub install and an F-Droid install can't update each other, and the release notes must say to pick one. |
| D7 | Signing key | **One release key, kept locally and in a GitHub Actions secret, with an encrypted offline backup.** Locally it is read from `keystore.properties` or env (never committed); CI signs tagged builds from the secret. The same key signs both packages. |
| D8 | Repository visibility | **The owner makes the current repository private before the cut-over** (not done yet). Until then its history still holds what the 2026-10-11 scrub removed from the tree. |
| D9 | Agent files | **Only `AGENTS.md` goes public.** `CLAUDE.md`, `GEMINI.md`, `CODEX.md`, `.cursorrules`, `.github/copilot-instructions.md`, `.jules/` and `.claude/` are left out of the snapshot (and the AGENTS.md intro that names them is updated). |
| D10 | UX rules | Phone vertical swipe: **keep it for channel switching but make it safer** (U-H5: about 56 dp, edge exclusion, one-time hint). TV VOD seeking: **a tap jumps ±10 s at once, holding enters the scrub cursor** (U-M7). PiP: **films and shows too, not only live** (L9). TV text scale: **unchanged** (40–100 %, default 80 %; U-M8 dropped). |
| D11 | Minimum Android | **Stays Android 11 (API 30).** README and store text say "Android 11 or later" up front, naming the common boxes it excludes. |

---

## F-Droid blockers

| ID | Problem | Evidence | Fix |
|---|---|---|---|
| F1 | No license, declared proprietary | README.md:12, :226-232; no `LICENSE`; `server/package.json` `"private": true`, no license | D1: add `LICENSE`, rewrite the README section and badge, set `"license"` in `server/package.json`, add SPDX headers (optional) |
| F2 | ML Kit barcode scanning (proprietary, bundles `libbarhopper` and GMS `datatransport` logging) | libs.versions.toml:67, :166; mobile/build.gradle.kts:135; `QrScanner.kt:40-43,101` | Decode in the existing CameraX `ImageAnalysis` with `zxing:core` (already a dependency in core:ui: `QRCodeReader` + `PlanarYUVLuminanceSource`), then remove ML Kit |
| F3 | Play Services Cronet in the version catalog (unused, but the fdroidserver scanner matches `com.google.android.gms`) | libs.versions.toml:63, :153, :162 | Delete the three entries |
| F4 | ~~Blocker~~ **Nit (downgraded 2026-10-11):** prebuilt `libsqlite3x.so` from JitPack (requery 3.49.0, Apache-2.0) | settings.gradle.kts:20; core/network/build.gradle.kts:84; `EpgIndexDatabase.kt:10,82` | JitPack is one of F-Droid's trusted repositories for prebuilt FLOSS binaries, so it can stay for 1.0 (checked 2026-10-11: listed in the [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/) and in `allowed_repos` of fdroidserver's `scanner.py`; the scanner flags `.so` files in the source tree, not inside dependencies). Optional later: `androidx.sqlite:sqlite-bundled` (`BundledSQLiteDriver`) or framework SQLite, re-measuring size and ingest on a Shield first (the PRAGMA notes in `EpgIndexDatabase.kt:47-100` and `docs/EPG_INDEX_STORAGE.md` exist because of requery). |
| F5 | ~~Blocker~~ **Nit (downgraded 2026-10-11):** prebuilt `libffmpegJNI.so` (GPL-3.0) from Jellyfin, and the `s01.oss.sonatype` repo | core/player/build.gradle.kts:58; settings.gradle.kts:22 | Kept (D3). The artifact is on Maven Central, so the s01 repo is redundant: remove it to keep the repository list short. |
| F6 | Both apps are `org.njarasoa.fijerena`, versionCode 4; versionName `"1.0"` (tv) vs `"1.0.0"` (mobile) | tv/build.gradle.kts:26-30; mobile/build.gradle.kts:15-19 | D2. Keep one version source (`gradle.properties` or catalog) for both apps |
| F7 | Daemon JVM toolchain 25, auto-download off | gradle/gradle-daemon-jvm.properties:2; android-build.yml:33 | Set it to 21 (the sources target 21; F-Droid's buildserver has 17/21). Confirm AGP 9.4.1 / Gradle 9.6.0 are in fdroidserver's `gradlew-fdroid` checksums |
| F8 | Builds aren't reproducible: `BUILD_TIME = Date()` | tv/build.gradle.kts:49-51; mobile/build.gradle.kts:39-41 | Use `SOURCE_DATE_EPOCH` or the commit time (`git log -1 --format=%ct`). `GIT_HASH` is fine |
| F9 | AGP's encrypted dependency blob is in the APK | no `dependenciesInfo` block | `dependenciesInfo { includeInApk = false; includeInBundle = false }` in both apps |

## F-Droid anti-features (what would be flagged)

- **NonFreeDep:** ML Kit (F2), until it's removed.
- **Tracking:** possibly ML Kit's `datatransport` usage logging. Nothing else: CrashLog stays on
  the device, sharing it is user-initiated, and `ANDROID_ID` goes only to the user's own Jellyfin
  server as `DeviceId` (normal for Jellyfin clients).
- **NonFreeNet:** likely, for the opt-in TMDB key (D4). Nothing contacts TMDB without a key the user entered.
- **TetheredNet / NonFreeAdd:** none. There's no default sync server (the hint is
  `sync.example.com`) and no update checks or third-party hosts.
- **Reviewer optics (not an anti-feature, but F-Droid pushes back on IPTV apps that look
  piracy-oriented):** no content or playlist ships, which is good. But docs, comments and tests
  named real panels and their domains. That was scrubbed on 2026-10-11: they're now `panel-a`
  to `panel-d` and `*.example`. Channel names ("Sky Sports UHD", "FR| FRANCE SPORT VIP RAW",
  "CANAL+") remain in docs and examples; they are broadcasters, not providers.

## F-Droid metadata and process

- No git tags. Tag every release (`v1.0.0`) so F-Droid can use `UpdateCheckMode: Tags`.
- No `fastlane/metadata/android/<locale>/` (title, short and full description, changelogs keyed
  by versionCode, icon, `phoneScreenshots` / `tvScreenshots`). Provide en-US, fr-FR and mg, one
  set per applicationId.
- Image provenance isn't recorded. The "Blue Marble + 3D glasses" icon and `app_logo.png` need a
  source and license note: NASA Blue Marble is public domain, but the glasses art needs its own.
- Then, after the cut-over (Phase 8), open the RFP / merge request on `fdroiddata` against the
  new public repository. Put an `fdroid` build flavor in Gradle
  (D3/D4) so the recipe stays a plain `gradle: [fdroid]`.

---

## Release-readiness work (any store, including GitHub releases)

### P0

| ID | Problem | Evidence | Fix |
|---|---|---|---|
| R1 | The export file carries the Xtream password in clear | `SettingsExportManager.kt:277-288` exports EPG source `url` raw; the auto-added guide source is `xmltv.php?username=…&password=…` (`AutoXmltvSources.kt:47`). The same URL sits unencrypted in `providers.db`, which bypasses the encrypted credential store | On export, drop auto-XMLTV sources (they're recreated on import) or strip the query. In Phase 4 as well, store them credential-less and rebuild the URL at fetch time from the secret store (a `providers.db` migration). Check that live sync doesn't send the same URL outside the E2E envelope |
| R2 | Release builds aren't signed, minified or built in CI | `isMinifyEnabled = false` (tv:63, mobile:52); no `signingConfig`; CI builds only `assembleDebug` | Add a signing config from env or `keystore.properties` (never committed). Enable R8 and `shrinkResources`. Move the core modules' rules to `consumerProguardFiles` (they currently apply only to the libraries themselves); fix the invalid `-keepattrs` in core/player; replace the blanket `-keep androidx.media3.**/io.ktor.**/kotlinx.coroutines.**`; add `-dontwarn` for smbj/BouncyCastle/slf4j and keep rules for `@Serializable` routes. Add `assembleRelease` plus a launch smoke test to CI. Strip `Log.d` with `-assumenosideeffects` |
| R3 | Personal and household data in tracked files | LAN IPs, a personal domain, home-directory paths, device host names, a committed agent memory file, an Xtream login name, real panel names and domains | **Done 2026-10-11** (tree only): replaced with `192.168.1.x`, `*.example`, repo-relative paths, `phone-a`/`phone-b`, `panel-a`…`panel-d`, `alice`/`user1`; memory file removed. History is not rewritten: the public repository starts from a squashed snapshot at the end (see "Public repository cut-over") |
| R4 | No disclaimer or privacy policy | Only `login_footer_text`; no PRIVACY file | Show "Fijerena does not provide or host any content" on first run and in About. Add a `PRIVACY.md` covering: no telemetry; credentials stay on the device; the sync server (user-chosen) receives E2E-encrypted data (AES-256-GCM + HKDF, `SyncCrypto.kt`); camera only for QR pairing; cleartext HTTP allowed for LAN and IPTV panels. Link it from the store listings |
| R5 | Unused dangerous permissions on TV | tv/AndroidManifest.xml:12-14 `READ_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`; local files go through SAF | Remove both |

### P1

- **targetSdk 36.** Play requires it for new apps (verify the current deadline). It brings
  predictive back, and Android 16 ignores orientation locks on large screens (see U-M9).
- **CI on pull requests.** None of the four workflows has a `pull_request` trigger, and the last
  nightly instrumented run failed (2026-10-10). Fix both before taking outside contributions.
- **Developer mode is a plain toggle** that exposes unfinished SMB/Local sources and Diagnostics.
  Hide it behind a 7-tap on the version row, and fix its description: it says it enables "stats
  for nerds", but Stats is always available.
- **Project hygiene:**
  - Add `CONTRIBUTING.md`, `SECURITY.md`, issue and PR templates, screenshots, and a
    `CHANGELOG.md` keyed to versionName. `RELEASE_NOTES.md` is 60 unversioned feature entries,
    so keep it as the dev log and start a user-facing changelog.
  - Rewrite the README for users: what it is, what it isn't (no content), install from F-Droid
    or Releases, and the requirements. **Android 11+ excludes most cheap Android TV boxes and
    Fire OS 7, so say so up front.**
- **Drop the household tooling from the public tree:**
  - `deployToShield` (tv/build.gradle.kts:151-199)
  - `scripts/tune-system-memory.sh` (sudo host tuning)
  - `tools/jellyfin-xtream` (or document it as a dev-only bridge)
- **Docs and agent files:**
  - **Decided 2026-10-11: archived plans don't go public.** `docs/plans/archive/` stays in the
    private repository only; open plans stay. See Phase 8, step 3.
  - Keep `AGENTS.md`; drop or ignore the other AI-vendor stubs and `.claude/projects/`.
- **`data_extraction_rules.xml`** still holds the template TODO and isn't referenced. With
  `allowBackup="false"`, device-to-device transfer on 31+ still copies data. Reference explicit
  exclusion rules for the credential store and databases.
- **Deprecated `security-crypto` 1.1.0.** The secret-store migration plan exists; schedule it
  after release, since it's FOSS and not a blocker.

### P2

- `keepDebugSymbols` ships unstripped `.so` files, which makes the APK bigger. Strip them in
  release.
- Release ABIs are ARM only. Fine for TVs and phones; x86 Chromebooks run under translation.
  F-Droid can build per-ABI.
- Lint baselines hold 437 issues (core/ui 254). Burn them down gradually.
- The mobile module doesn't apply ktlint (tv does).
- `runBlocking(Dispatchers.IO)` at `MediaRepository.kt:1199` can run on Main: a small ANR risk on
  slow storage.
- Only 3 `<plurals>`. Check the count strings for fr/mg grammar.

---

## UI / UX for a new public user

### High

| ID | Problem | Evidence | Recommendation |
|---|---|---|---|
| U-H1 | First run lands in Settings (TV: on the Profiles group). It takes about 5 unexplained steps to reach Add Source, and saving a source returns to the Sources list, not Home. Back from Settings exits the app | TvNavHost.kt:284-298, :881-884; MobileNavHost.kt:276-285, :627-628 | A welcome screen with the R4 disclaimer and one "Add your first source" action that opens Add Source directly. After the first successful add, go to Home and clear the stack (as `onProviderSelected` does, TvNavHost.kt:919-921) |
| U-H2 | TV Undo works only with the Menu key, which the Chromecast with Google TV remote doesn't have. Removals don't ask for confirmation | TvUndoBar.kt:110; strings `undo_removed_format`, `row_actions_hint` | Also accept long-press OK (or Up into a focusable Undo chip). Change the copy to "Hold OK to undo" or base it on the remote's keys |
| U-H3 | Source types are bare jargon, hardcoded in English ("Xtream IPTV", "Remote M3U"), and the TV name placeholder is always "e.g. My IPTV" | ProviderType.kt:6-10; TvAddProviderScreen.kt:163, :417 | Localised names with one-line subtitles ("IPTV subscription: server, username and password from your provider", "Jellyfin media server", "Playlist link (M3U)"). Surface the existing paste-the-full-link parsing (`XtreamForm.kt:33-43`) with a hint |
| U-H4 | Login failure: hardcoded "Connection Failed" (TvAddProviderScreen.kt:861), technical messages ("Server returned HTTP %d", "missing #EXTM3U header"), raw `e.message` ("…: null", EpgViewModel.kt:169-196), and a red primary "Save Anyway" | as cited | Use string resources, pass everything through `friendlyErrorMessage`, put the details behind "Details", and make "Save Anyway" secondary or developer-mode only |
| U-H5 | The phone channel-switch swipe fires at 100 **px** (about 33 dp) vertically and 80 px horizontally, has no edge exclusion and is never explained. VLC, MX and YouTube users expect brightness and volume there | MobilePlayerScreen.kt:790, :800 | Thresholds of 48-64 dp, ignore the outer 10% of the screen, a one-time coach mark, and consider a setting |

### Medium

- **U-M1 Expert options shown to everyone.**
  - Edit Source shows Stream Output Format, Playlist Type and Enable Caching
    (ProviderSettingsSection.kt:158-179).
  - Shrink Database and Guide data maintenance (Cleanup and Purge) sit in Backup & storage.
  - Live sync is a top-level group but needs a self-hosted server and a setup secret, with no
    pointer to either.
  - Fix: an "Advanced" disclosure for these, and a "How to set up a sync server" link.
- **U-M2 Guide source setup is for experts.**
  - It asks for "XMLTV URL" and whole-hour "Timezone Offset" in Material text fields on TV, which
    goes against the TV audit's X5 decision. Invalid input quietly becomes 0, and +5:30 can't be
    entered.
  - `epg_browser_no_file_message` sends users to "Settings" when guide sources live in Edit
    Source → Guide sources.
  - Fix: "Guide link (XMLTV)" with a helper line, a ±hh:mm picker defaulting to Auto, and the
    corrected copy.
- **U-M3 Jellyfin Quick Connect is buried on TV** under username and password, and its
  instructions name the admin-only Dashboard. Put it first on TV and check the wording against a
  current Jellyfin server.
- **U-M4 Trademarks.** The look-and-feel names "Roku", "BRAVIA" and "Cupertino" (UiStyle.kt:84,
  95, 106) are registered marks; the theme names are hardcoded English (CinemaThemePalette.kt:54,
  72). Use neutral, localised names.
- **U-M5 First sync of a large catalogue** shows only "Updating…" and a pulsing dot
  (HomeHeaderStatus.kt:89-115). Show the phase and running counts, plus "Still loading your
  library…" in empty sections.
- **U-M6 Jargon left in the strings:**
  - "EPG indexing %d%%…", "EPG is up to date.", "Clear EPG Data?"
  - "Total Database Items", "Ingesting", "Swapping to primary guide…"
  - "Download / Ingest / Latest Prog"
  - the "Streams" tab, "Last Watched Queue Size"
  - "HTTP 401 — Authentication failed"

  Fix: "guide" instead of "EPG", plain sentences first, and HTTP codes only in developer mode.
- **U-M7 TV VOD seeking.** Left and Right move a scrub cursor that seeks only on OK. Jump ±10 s
  on a tap and scrub only on hold, which is what Google TV users expect.
- **U-M8 TV text size.** At the 80 % default, 14 sp `labelSmall` renders at about 11 sp. Add
  110-120 % for low vision. System `fontScale` is already respected, which is good.
- **U-M9 Phone portrait lock, no tablet or foldable layout.** On target 36 Android 16 ignores the
  lock on large screens anyway. Unlock at ≥600 dp and add a list/detail two-pane layout.

### Low

- Language names should appear in their own language in every locale ("English", "Français",
  "Malagasy"); today the French UI shows "Anglais".
- Remove the unused strings: `epg_management_title`, `epg_data_manage_button`,
  `search_streams_placeholder`, `category_streams_count`, `stream_count_format`,
  `category_no_streams`, `player_press_back_exit`, `category_live_preview_badge`, `epg_no_data`
  and `epg_browser_hint_search_titles_local`.
- Pick sentence case and one spelling of "favorites" throughout.
- Make alarming status copy calmer ("Error. Retrying (Attempt %d/%d)", "Current Status Error:
  %s").
- Remove the fixed 1 s minimum splash (`AppLoadingScreen.kt:49`).
- Contrast: `textDisabled` #606060 is about 3:1 and must never carry information; `textTertiary`
  drops below 4.5:1 on lighter surfaces.
- PiP is live only. Users will expect it for films too (that was a decided rule; revisit it).

---

## Without a TMDB key (D4)

Without a key, an Xtream source still gives a complete app. TMDB only fills gaps
(`XtreamMediaProvider.kt:298, 387, 541`) and adds extras. **Kept:** the provider's catalogue,
posters, categories, seasons and episode lists, plots, cast, genre, rating, release date and
backdrop (`XtreamMapper.kt:87`), plus all of playback. **Lost:**
- missing plots, years and release dates filled in (the year still comes from titles like
  "… (1972)");
- missing episode summaries;
- age ratings (display only; nothing filters on them);
- the detail screen's title logo and TMDB backdrop and poster;
- the More like this, Similar and collection rows (`getRelatedTitles`, `:747`).

Jellyfin and M3U never use TMDB.

**Check before release (Phase 6, with the key field).** Development builds always had a key, so
the keyless path is the least exercised. On TV and phone, with no key, open a film, a series, a
series whose provider sends no episode plots, and a film from a franchise. Look for:
- More like this, Similar or collection sections that are empty or stay on a spinner;
- blank space where the title logo would be;
- a hero with no backdrop when the provider sent none (poster or ambient fallback instead);
- an empty age-rating chip.

Then do the same with a key that has been revoked: a 401 must look like "no key", not an error
on every screen. Record the result in this plan.

**Keyless fallback: Wikidata?** Not for 1.0. Wikidata is free (CC0, no key, no anti-feature) and
can match on the TMDB id (P4947 film, P4983 series). But for this app it adds little:
- its descriptions are one-liners ("1972 film by …"), not plots;
- the year is mostly already covered;
- age ratings are patchy;
- it has no posters, backdrops or logos;
- it has no episode summaries;
- it has nothing like recommendations.

It also brings an extra network dependency with Wikimedia's User-Agent and rate-limit rules, and
a query service that has had availability trouble. The one thing it does well is franchises (P179
"part of the series" lists the other films with their TMDB ids), and that only pays off if
joined to the user's own catalogue.

**Better keyless option (Phase 9), with no network at all:**
- **More like this from the user's own catalogue,** ranked by shared genre, cast and director.
  Xtream sends all three, and `xtream_streams` / `xtream_series` already hold them.
- **A "same series" row by title prefix** (e.g. "Harry Potter and …").

Every result is a title the user can actually play, with the provider's poster. Revisit Wikidata
only for franchise rows, if the title-prefix heuristic proves too weak.

---

## Public repository cut-over (Option C, last step)

**Decision (2026-10-11):** history is not rewritten. When every other phase that goes into 1.0 is
done, the public repository starts from one squashed commit of the cleaned tree, and the current
repository, with its full history, becomes private.

**Why:** a scan of the full history (1,706 commits, 2026-10-11) found no real secrets: no API keys,
tokens, keystores, `local.properties` or real passwords, only test values. What it does hold is
personal and provider detail that only a fresh root removes for certain:

- A phone traffic capture (CSV) naming a real IPTV host and another player app. Added in
  `09f0d660` (2026-03-19), deleted in `0dc16169` (2026-04-24); not in the tree since.
- Real panel names, domains and a login name. Scrubbed from the tree on 2026-10-11 but still in
  older commits.
- LAN IPs, a personal domain, home-directory paths and device names. Same: scrubbed from the tree
  on 2026-10-11, still in history.
- Two personal author emails (353 commits carry the gmail address) and one "Your Name" commit.

`git filter-repo` could remove these too, but it changes every commit hash (the plans cite commits
by hash), and old copies survive in clones, forks and GitHub's pull-request refs. AI-authored
commits and agent files are not a reason to rewrite either way.

**Steps** (done last, after Phase 7):

1. Make the current repository **private** now, without waiting for this phase, if it is public.
2. Freeze `main`: every earlier phase is merged and CI is green.
3. Final scrub check on the tree: `gitleaks detect --no-git` and a grep for the names, domains,
   IPs and paths that were scrubbed on 2026-10-11 (keep that list privately, outside the repo), in
   case any came back.
3a. **Archived plans stay private** (decided 2026-10-11). Leave `docs/plans/archive/` out of the
   snapshot. About 260 files cite archived plans by path, including 392 citations in Kotlin and
   the scripts; they're mostly `// … docs/plans/archive/<plan>.md → Phase N` comments. Before
   the cut-over, on `main`:
   - Move anything an archived plan still records as live into the standing docs (`AGENTS.md`,
     `docs/*.md`), e.g. the watch-state plan's "Known adjacent problems". Grep the archive for
     "out of scope", "follow-up", "deferred" and "known".
   - Rewrite the citations with a script: keep each comment's own explanation and drop the
     archive path, or replace it with the standing doc that now holds the rule. Check the
     `scripts/check-*.sh` messages, `.github/workflows/checks.yml`, `server/` files and
     `AGENTS.md` by hand.
   - Remove the Archive table from `docs/plans/README.md`. Change the AGENTS.md "Plans" rule:
     a finished plan is deleted from the public repository (its record is the repository
     history and `RELEASE_NOTES`), not moved to `archive/`.
   - The open plans that go public lose their commit-hash citations, which don't resolve in the
     new repository. Drop them or note that they refer to the private history.
4. Create the new public repository from `git archive main` (a tree only, no `.git`), as a single
   commit authored with the GitHub noreply address, with the message "Initial public release
   (v1.0.0)".
5. Tag `v1.0.0` in the new repository, publish the signed release APKs, and point the README,
   fastlane metadata and the F-Droid RFP at the new URL.
6. Archive the old repository (private, read-only) and keep it as the full history. All further
   work happens in the public repository.

## Plan of attack

| Phase | Content | Depends on | Size |
|---|---|---|---|
| 0 | Decisions D1–D11 | none | Done 2026-10-11 |
| 1 | **Legal and hygiene:** LICENSE + README rewrite (F1), scrub personal data and provider names (R3), full-history secret scan, remove the TV storage permissions (R5), drop the household tooling, image provenance notes | D1 | S |
| 2 | **FOSS dependencies:** ML Kit → zxing (F2), delete the Cronet entries (F3), drop the redundant s01 repository (F5), daemon JVM 21 (F7). requery and FFmpeg stay (D3, F4) | none | S |
| 3 | **Packaging:** applicationId split or universal APK, one version source (F6), reproducible `BUILD_TIME` (F8), `dependenciesInfo` (F9), signing, R8 + consumer rules (R2), the reproducibility check (D6: two clean builds match), `assembleRelease` + CI on PRs | D2 | M; R8 needs a full regression pass on both apps |
| 4 | **Privacy and security:** export without credentialed guide URLs, credential-less guide URLs at rest (R1), `PRIVACY.md` + in-app disclaimer (R4), data extraction rules | none | S-M; at-rest needs a `providers.db` migration and a `DATABASE_SCHEMA.md` update |
| 5 | **First-run UX:** welcome screen, source-type subtitles and paste hint, go to Home after the first add (U-H1, U-H3), login errors (U-H4), Undo without a Menu key (U-H2), phone swipe thresholds (U-H5) | Phase 4 disclaimer text | M; focus walks to update |
| 6 | **TMDB key in Settings (D4)** and the keyless/revoked-key check (see "Without a TMDB key"), then the **copy and IA pass:** Advanced disclosures (U-M1), guide source form (U-M2), Quick Connect (U-M3), neutral look names (U-M4), jargon (U-M6), developer-mode 7-tap, Low items | none | M, mostly strings in three languages |
| 7 | **Store presence:** fastlane metadata in en/fr/mg, TV and phone screenshots, CHANGELOG | 1–3 | S |
| 8 | **Public repository cut-over (Option C), always the last step:** final scrub check, archived plans left out (citations rewritten first), fresh squashed public repository, tag `v1.0.0`, signed release, F-Droid RFP / fdroiddata MR; old repository archived private | 1–7 | S |
| 9 | **After 1.0** (in the new public repository): keyless More like this and same-series rows from the local catalogue, sync progress detail (U-M5), TV seek (U-M7), targetSdk 36 + tablets (U-M9), PiP for films and shows (D10), Google Play (D5), secret-store migration, lint baseline | none | L |

Phases 1–3 are the minimum for F-Droid. Phases 4–5 are the minimum before inviting strangers.
Phase 8 always comes last: nothing goes to the public repository before the tree is final.

## Progress

**Phase 6 done (2026-10-11, 0a81775f).** D4: the TMDB key is entered in Settings → Source &
guide → Film and series details (TMDB) on TV and phone; no build embeds one. `TmdbKeyStore`
keeps it encrypted and app-wide; live sync carries it as `tmdb_api_key` inside the encrypted
records; exports never do. No key ⇒ no TMDB request; a 401 marks the key refused ⇒ treated as no
key, shown only as "Key refused" in Settings. Plots in French for a French UI. TMDB logo and
attribution in About. Copy and IA: guide instead of EPG (U-M6), guide source labels and hints and
the no-guide message (U-M2), Quick Connect wording (U-M3), neutral localised look and theme names
(U-M4: Standard, Rounded, Sharp, Balanced; Deep night, Pure black, Amethyst, Teal), developer mode
behind 7 taps on the version, language names in their own language, unused strings removed,
sentence case and British spelling in English. **Keyless / refused-key check, 2026-10-11, TV
emulator (jellyxtream):** no key — no More like this / collection sections, no spinner, title as
text, backdrop hero, no empty age chip; valid key — logo, More like this, collection and age
rating appear at once on re-open; refused key — same as no key, no error on any screen, Settings
reads "Key refused"; developer mode 7 taps on and profile switch off both work. Fixed during the
check: the refused banner stayed up while a different key was tested. Focus walks: edit-source
updated; settings needs re-recording on a device with a "main" profile; settings-guide and the
live-TV walks need the iptv source (not run). Left for the user: Advanced disclosures (U-M1), the
±hh:mm guide picker (U-M2), Quick Connect first on TV (U-M3), the 1 s splash floor, contrast
tokens, HTTP codes in the remaining player format strings.

**F7 checked (2026-10-11):** Gradle 9.6.0 is in F-Droid's gradle-transparency-log checksums
(which `gradlew-fdroid` uses; it lists up to 9.8.1). AGP is a normal Google Maven dependency and
needs no entry.

**Phase 4 done (2026-10-11, e062432b).** R1: the automatic Xtream guide source is stored as
`<server>/xmltv.php` (providers.db v17 strips existing rows, sync triggers held off) and the login
is added at download time from the encrypted store; with no password on the device the source
shows `error_guide_login_missing` and nothing is requested. Live sync still sends the login inside
the encrypted record so older builds keep working; receivers strip it. Exports leave out every
Xtream source's own `xmltv.php` (re-added on login); hand-added guide URLs are exported as typed.
R4: `PRIVACY.md`, and the `app_disclaimer_no_content` string in About on TV and phone (Phase 5
reuses it). Data extraction rules exclude everything from cloud backup and device transfer.
Checked on the phone emulator: v16 → v17 with both bears guide sources stripped, ingest state and
pending sync rows unchanged; a manual refresh downloaded and ingested the bears guide (172k
programmes) with the stored URL still bare; the disclaimer shows in Settings. Behaviour change: a
login with other credentials no longer forces an immediate guide re-download.

**Phase 1 done (2026-10-11, b1f78c65).** `LICENSE` (GPL-3.0-or-later) and `server/LICENSE`
(AGPL-3.0-or-later), `server/package.json` licence; README rewritten user-first (licence, "does
not provide or host any content", Android 11+, "Artwork and fonts": Blue Marble is NASA public
domain, Manrope OFL-1.1 in `docs/licenses/`); copyright holder Orinasa Njarasoa (F1). R3
re-checked clean in the tree, and household device details dropped from docs and comments.
Full-history secret scan (regex over `git log --all -p`, gitleaks not installed): no real
secrets, only test values. TV storage permissions removed (R5). `deployToShield`, its LAN IP and
`tune-system-memory.sh` removed; `tools/jellyfin-xtream` kept as development only. Open: the
source and licence of the icon's 3D-glasses art; SPDX headers (optional); the OFL notice in an
in-app licences screen.

**Phase 2 done (2026-10-11, c6994c46).** F2: ML Kit replaced by zxing `QRCodeMultiReader` on the
CameraX luminance plane (`QrFrameDecoder.kt`, unit-tested); `QRCodeReader` alone missed ~1% of
pairing codes in one orientation (finder-like patterns in the data). No mlkit/gms/datatransport
on the runtime classpaths; mobile debug APK ~23.5 MB smaller. F3: Cronet entries deleted. F5: s01
repository removed (FFmpeg resolves from Maven Central). F7: daemon JVM and CI on JDK 21. Open:
`gradlew-fdroid` checksums for AGP 9.4.1 / Gradle 9.6.0; the QR scanner's device check.


| Phase | Status | Commit |
|---|---|---|
| 0 | Done (2026-10-11) | |
| 1 | Done (2026-10-11) | b1f78c65 |
| 2 | Done (2026-10-11) | c6994c46 |
| 3 | Not started | |
| 4 | Done (2026-10-11) | e062432b |
| 5 | Not started | |
| 6 | Done (2026-10-11) | 0a81775f |
| 7 | Not started | |
| 8 | Not started (last) | |
| 9 | Not started | |
