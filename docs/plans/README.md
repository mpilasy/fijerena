# Plans

Multi-phase work is planned in writing before it is built. A plan lives in `docs/plans/` while it
is active, partly done or deliberately deferred. When it is finished or dropped it moves to
`docs/plans/archive/`. `docs/RELEASE_NOTES.md` stays the record of what shipped.

- **Naming:** `YYYYMMDD_<kebab-case-topic>-plan.md`, dated the day the plan was written.
- **Status:** every plan opens with a `**Status:**` line, and multi-phase plans keep a Progress
  table. Both are updated as work goes on: mark a phase in progress when it starts and done (with
  the commit) when it lands. The plan's own Status line is the source of truth; this index is a
  summary of it.
- **Moving a plan:** source comments cite plans by path and phase, so moving or renaming one
  means updating every citation (`grep -rn <file name>`).

## Open

| Date | Plan | Goal | Left |
|---|---|---|---|
| 2026-10-07 | [Phone home overhaul](./20261007_phone-home-overhaul-plan.md) | Phone home as on TV, for touch: bottom navigation bar (tabs keep their place), sync status, Channels and favourites rows, long-press sheet, pull to refresh | Phases 1–2 done; 3–6 in progress |
| 2026-10-07 | [TV home overhaul](./20261007_tv-home-overhaul-plan.md) | TV home: compact section tiles, Continue watching / Live / favourite rows, source sync status, clock, backdrop follows focus | Done on the TV emulator, Now line and Updating… checked on bears; Shield check left |
| 2026-10-06 | [Guide on/off clarity](./20261006_guide-on-off-clarity-plan.md) | One empty download no longer turns a source's guide off; guide sources get a real on/off switch instead of bulk-select checkboxes; refresh line, search header and "-1%" tell the truth | Done on the emulators; Phase 1 not forced on a device; OnePlus check left |
| 2026-10-05 | [Icon buttons](./20261005_icon-buttons-plan.md) | Action buttons back to icons app-wide; TV shows the name on focus, mobile on long-press | Done on the emulators; live sync rows, section-root button, phone guide rows not reached |
| 2026-10-05 | [Shared logins](./20261005_shared-logins-plan.md) | 2–3 Xtream logins on one source shared by all devices: each playback takes a free login (panel's `active_cons`), switches on refusal | Phases 1–3 done, checked on emulators against the bridge; Phase 4 (real bears) needs a second bears login |
| 2026-09-14 | [Codebase robustness](./20260914_codebase-robustness-plan.md) | Pay down technical debt found in a whole-codebase review | Phases 4–5: TV Compose allocations and Coil contention, more Compose UI tests; Phases 2–3 (single-return sweeps) dropped 2026-10-03; SecretStore part of Phase 6 deferred |
| 2026-08-28 | [Secret store migration](./20260828_secret-store-migration-plan.md) | Replace deprecated `EncryptedSharedPreferences` with an owned Keystore-backed `SecretStore` | All five phases; not started, deferred on purpose (the crash loop it cites is already handled) |

## Archive

| Date | Plan | Summary | Status |
|---|---|---|---|
| 2026-10-07 | [Guide Watch wrong channel](./archive/20261007_guide-watch-wrong-channel-plan.md) | Search the guide's Watch plays the channel asked for: no last-channel fallback in the TV preview, a matcher cache with every stream and no separator matches by name, results in hidden categories dropped | Done; checked on the TV emulator (atr on bears) |
| 2026-10-05 | [Up next details](./archive/20261005_up-next-details-plan.md) | Next episode's title and synopsis in a panel under the "Up next" strip (TV: while the card has focus, in practice whenever it shows; mobile: always) | Done; checked on the emulators and bears |
| 2026-10-04 | [Device info screen](./archive/20261004_device-info-screen-plan.md) | Settings → About → Device info for everyone: app, device, memory, storage, display, decoders, audio passthrough, network, power (charging as Doze sees it), sync health; Share on the phone | Done; review findings R1–R8 fixed |
| 2026-10-04 | [Playback capability errors](./archive/20261004_playback-capability-errors-plan.md) | Undecodable streams (Dolby Vision profile 5, 8K) stop at once with the codec named, in the app language; slow-connection banner with measured throughput; 60 s stall limit | Done |
| 2026-10-03 | [Sources, guide sources, profiles and section root](./archive/20261003_sources-guide-profiles-plan.md) | Profile page (switch to, developer mode, play next, content filters); "Provides a guide" per source; guide sources under Edit Source and Search the guide; auto-refresh per guide source (providers.db 16); section-root button from 4 deep; Home keeps Search the guide; TV preview plays on OK | Done |
| 2026-10-03 | [UX overhaul](./archive/20261003_ux-overhaul-plan.md) | Settings rebuilt on TV and mobile, app-wide TV focus rules, Live TV flows, rebuilt TV Guide | Done; native-speaker review of a few mg terms deferred |
| 2026-10-02 | [Provider to Source rename](./archive/20261002_provider-to-source-rename-plan.md) | "Provider" is "Source" everywhere in the UI, in all three languages | Done |
| 2026-10-02 | [Profile last provider](./archive/20261002_profile-last-provider-plan.md) | Each profile returns to the source it last picked, synced across devices | Done |
| 2026-10-02 | [Next-level resilience](./archive/20261002_next-level-rock-solid-resilience-plan.md) | Safe mode, CI gates, provider state and playback fixes (R-01–R-30) | Done; R-06 step 4 deferred |
| 2026-10-02 | [EPG search during refresh](./archive/20261002_epg-search-during-refresh-plan.md) | EPG search keeps working, or says why, while a refresh runs | Done; emulator check not recorded |
| 2026-10-02 | [Catalog sync cache churn](./archive/20261002_catalog-sync-cache-churn-plan.md) | Opening a cached show no longer re-downloads it or repeats TMDB calls | Done |
| 2026-10-01 | [Rock-solid stability](./archive/20261001_rock-solid-stability-resilience-plan.md) | Stability and resilience fixes F-01–F-38 across player, network, UI and server | Done; F-13 deferred, F-02 not reproduced |
| 2026-10-01 | [Live sync Now Playing](./archive/20261001_live-sync-now-playing-plan.md) | Each device shows what the others are playing; remote Stop from the phone | Done |
| 2026-10-01 | [Fast profile switch](./archive/20261001_fast-profile-switch-plan.md) | Near-instant profile switch: no per-item filter writes, xtream_v2.db v24 | Done |
| 2026-09-30 | [Profile-scoped settings](./archive/20260930_profile-scoped-settings-plan.md) | Developer mode and category filters are per profile | Done |
| 2026-09-30 | [Profile architecture review](./archive/20260930_profile-architecture-adversarial-review-plan.md) | Review of profiles and per-profile Jellyfin logins; findings 3, 5, 6, 7 fixed | Done; mg strings deferred |
| 2026-09-29 | [Live sync](./archive/20260929_live-sync-plan.md) | Favorites, watch state and settings synced live across household devices | Done; key rotation after revoke deferred |
| 2026-09-25 | [EPG add to calendar](./archive/20260925_epg-add-to-calendar-plan.md) | "Add to calendar" for a future airing in the EPG dialog | Done |
| 2026-09-23 | [UI/UX transitions and flow](./archive/20260923_ui-ux-transitions-flow-uplift-plan.md) | Transitions, Jump Back In shelf, mobile detail hero, TV settings grouping, double-tap seek | Done; 6a and 2c held back by the user |
| 2026-09-22 | [Codebase stability and resilience](./archive/20260922_codebase-stability-resilience-plan.md) | Data-loss, crash, DB, EPG, network and UI findings in batches 0–7 | Done; Jellyfin, SMB and M3U findings out of scope |
| 2026-09-21 | [Adversarial review findings](./archive/20260921_adversarial-review-findings-plan.md) | Xtream session cleanup, playback teardown, TMDB and M3U fixes; Ktor dispatcher root cause | Done |
| 2026-09-20 | [Xtream concurrency fixes](./archive/20260920_xtream-concurrency-fixes-plan.md) | `XtreamApiService` lifecycle and foreground catalog sync worker | Done |
| 2026-09-19 | [Concurrency deep dive](./archive/20260919_systemic-concurrency-memory-deep-dive-plan.md) | Playback lockout, WAL, cancellation, leak and EPG cadence fixes | Done; findings 2, 5, 11 dropped as not real |
| 2026-09-18 | [Systemic concurrency and memory](./archive/20260918_systemic-concurrency-memory-stability-plan.md) | Service lifecycle, native heap, worker safety and stable local/M3U ids | Done; finding 9 skipped on purpose |
| 2026-09-18 | [Concurrency and memory, round 2](./archive/20260918_concurrency-memory-stability-round2-plan.md) | Second round of concurrency, leak and thread-safety fixes | Done |
| 2026-09-18 | [Concurrency and memory](./archive/20260918_concurrency-memory-stability-plan.md) | CategoryViewModel guards, cache thread safety, cancellation, EPG memory | Done |
| 2026-09-12 | [Adversarial review, round 2](./archive/20260912_adversarial-codebase-review-round2-plan.md) | Bravia 4K detection, SMB handle leak, Jellyfin cancellation, EPG scope | Done; Phase 4 moved to the robustness plan |
| 2026-09-12 | [Adversarial remediation](./archive/20260912_adversarial-codebase-remediation-plan.md) | Cancellation, wake lock, FTS and D-pad fixes; single-return sweep of UI modules | Done; U2 deferred, rest of U3 moved to the robustness plan |
| 2026-09-11 | [Provider copy and duplicate](./archive/20260911_provider-copy-duplicate-plan.md) | Copy to and Duplicate for sources on TV and mobile | Done; no JVM test (no DI seam) |
| 2026-09-08 | [Episode selection fragility](./archive/20260908_episode-selection-fragility-plan.md) | Regression test, one resume-state holder, awaited position save | Done |
| 2026-09-02 | [TV detail hero](./archive/20260902_tv-detail-hero-ui-plan.md) | TV movie and series details rebuilt on a backdrop hero with tabs | Done |
| 2026-08-29 | [UI look and feel](./archive/20260829_ui-look-feel-uplift-plan.md) | Badges, scrims, row depth, mobile player controls, skeletons, TMDB posters | Done; 2c TV player controls skipped |
| 2026-08-29 | [Mobile UI polish](./archive/20260829_mobile-ui-polish-plan.md) | Rating, duration and title display fixes from a screenshot review | Done |
| 2026-08-28 | [Watch state durable storage](./archive/20260828_watch-state-durable-storage-plan.md) | Watch state moved to a Room table, TMDB dedup, mark watched from the UI | Done |
| 2026-08-28 | [Favorites durable storage](./archive/20260828_favorites-durable-storage-plan.md) | Favorites moved to a Room table, no 100-item cap | Done |
| 2026-08-27 | [Refresh change detection](./archive/20260827_refresh-change-detection-plan.md) | Catalog delta and EPG conditional GET/hash skip unchanged refreshes | Done; optional Phase 4 not pursued |
| 2026-08-26 | [TV UI performance](./archive/20260826_tv-ui-performance-plan.md) | Smooth TV navigation on the Shield, driven by hardware measurements | Done; leftovers optional (EPG cache sizing, mobile related-row allocations, unreproduced 6b) |
| 2026-08-24 | [Codebase audit fix](./archive/20260824_codebase-audit-fix-plan.md) | 29 audit findings fixed across tiers T1–T4 | Done |
