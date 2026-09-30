# Adversarial Review Findings: User Profiles & Per-Profile Jellyfin Logins

**Status:** Resolved 2026-09-30 — see Resolution  
**Date:** 2026-09-30  
**Baseline Commit:** `1dbf3d5cb08d9b4cfcef5106d4b5dd7d42d4e0fd`  
**Scope:** `core:network`, `core:ui`, `tv`, `mobile`, `docs`

---

## 1. Executive Summary

This document captures findings from an adversarial review of all commits landing Live Sync Phase 1 (Scoped Favorites & Watch State) and Phase 2 (User Profiles & Per-Profile Jellyfin Logins), covering commits `cbc21b21` through `4e22ecba` and the unstaged working tree changes.

While database migrations (`providers.db` v11–v12, `xtream_v2.db` v20) and core DAO queries correctly scope data by `profileId`, the review identified two critical state-machine/data-integrity defects, several Android TV 10-foot UI guideline violations, and architectural inconsistencies around repository lifecycle management.

---

## 2. Findings & Root Cause Analysis

### Domain A: State Machine & Auth Gating Traps

#### Finding 1: Home Screen Permanently Stuck on "Sign in to Jellyfin" After Returning from Successful Sign-In
* **Severity:** P1 — High (Functional Blocker)
* **Locations:**
  * [`tv/src/main/java/org/njarasoa/fijerena/feature/contentselection/ContentTypeSelectionScreen.kt#L177-L227`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/contentselection/ContentTypeSelectionScreen.kt#L177-L227)
  * [`mobile/src/main/java/org/njarasoa/fijerena/feature/contentselection/ContentTypeSelectionScreen.kt#L136-L180`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/contentselection/ContentTypeSelectionScreen.kt#L136-L180)
* **Mechanism:**
  1. A profile with no login for the active Jellyfin provider opens `ContentTypeSelectionScreen`.
  2. `LaunchedEffect(refreshTrigger)` executes: `needsSignIn = !providerRepo.hasLogin(activeProvider)` evaluates to `true`. `mediaRepositoryRef` and `mediaProviderRef` are set to `null`.
  3. `onSignInRequired(activeProvider.id)` navigates forward to `Screen.AddProvider(editId = activeProvider.id)`.
  4. The user completes authentication (via Quick Connect or username/password). `onSuccess` calls `navController.navigateUp()`.
  5. The backstack pops back to `ContentTypeSelectionScreen`. Because this destination was retained in the backstack, its local state (`needsSignIn = true`, `mediaRepositoryRef = null`) is preserved.
  6. Popping back to a retained destination **does not re-trigger** `LaunchedEffect(refreshTrigger)` because `refreshTrigger` was not changed.
  7. The screen's `DisposableEffect` observing `Lifecycle.Event.ON_RESUME` only reloads `continueWatchingItems` and is explicitly guarded by `if (repo != null)`—which is `null`.
  8. `needsSignIn` is never re-evaluated upon returning.
* **Impact:** The home screen remains stuck displaying the "Sign in to Jellyfin" panel. Clicking the sign-in button opens `AddProvider` again, and returning is still stuck. Only killing the app process or switching to a different provider and back clears the state.
* **Remediation:** Track provider credentials/login readiness as a reactive Flow or re-check `providerRepo.hasLogin(activeProvider)` inside the `ON_RESUME` lifecycle handler. When returning with a valid login, update `needsSignIn = false` and resolve repository/capabilities.

---

#### Finding 2: `hasLogin` Returns False-Positive for Default Profile with Empty Credentials
* **Severity:** P2 — Medium
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/provider/ProviderRepository.kt#L379-L384`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/provider/ProviderRepository.kt#L379-L384)
* **Mechanism:**
  ```kotlin
  fun hasLogin(entity: ProviderEntity): Boolean {
      val profileId = loginProfileId(entity.type)
      return profileId == ProfileEntity.DEFAULT_ID ||
          getLogin(entity).username.isNotBlank() ||
          getProviderPrefs(entity.id, profileId).getString(KEY_JELLYFIN_TOKEN, null) != null
  }
  ```
  `profileId == ProfileEntity.DEFAULT_ID` unconditionally returns `true`.
* **Impact:** If the Default profile has empty credentials for a Jellyfin provider (e.g. initial setup without saved credentials or cleared session), `hasLogin` returns `true`. The home screen skips the sign-in prompt, attempts auto-connecting with empty credentials, and fails with connection errors rather than guiding the user to sign in.
* **Remediation:** Remove the unconditional `profileId == ProfileEntity.DEFAULT_ID` bypass from the boolean expression so `hasLogin` verifies that either `getLogin(entity).username.isNotBlank()` or a valid session token exists for all profiles when the provider is Jellyfin.

---

### Domain B: Data Integrity & Storage Invariants

#### Finding 3: Deletion of `default` Profile Leaves Orphaned Data & Breaks Non-Jellyfin Providers
* **Severity:** P1 — High (Data Loss & Architectural Invariant Failure)
* **Locations:**
  * [`core/network/src/main/java/org/njarasoa/fijerena/core/network/profile/ProfileRepository.kt#L51-L65`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/profile/ProfileRepository.kt#L51-L65)
  * [`tv/src/main/java/org/njarasoa/fijerena/feature/settings/components/ProfilesSettingsCard.kt#L159-L167`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/settings/components/ProfilesSettingsCard.kt#L159-L167)
  * [`mobile/src/main/java/org/njarasoa/fijerena/feature/settings/components/ProfilesSettingsCard.kt#L138-L145`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/settings/components/ProfilesSettingsCard.kt#L138-L145)
* **Mechanism:**
  1. `ProfileRepository.deleteProfile(id)` allows deleting any profile as long as it is not the active profile and `count > 1`.
  2. If a user creates a second profile and switches to it, the `default` profile can be deleted from Settings.
  3. **Credential Storage Orphan:** `deleteProfilePrefs(id)` looks for files ending in `_profile_$id`. For `default`, files are named `media_cache_<providerId>.xml` and `provider_creds_<providerId>.xml` (no suffix). They are never deleted and become orphaned on disk.
  4. **Non-Jellyfin Provider Breakage:** All non-Jellyfin providers hardcode their credentials and bookmarks to `ProfileEntity.DEFAULT_ID`:
     ```kotlin
     private fun loginProfileId(type: String): String =
         if (type == "JELLYFIN") AppSettings(context).activeProfileId else ProfileEntity.DEFAULT_ID
     ```
  5. **Sync Convergence Failure:** Per `DATABASE_SCHEMA.md` and the live sync plan, `default` is a non-UUID fixed ID intended for convergence across multi-device sync. Once deleted, it cannot be recreated without wiping data.
* **Remediation:** Enforce that `ProfileEntity.DEFAULT_ID` cannot be deleted in `ProfileRepository.deleteProfile` (return a new `DeleteBlocked.DEFAULT` enum) and disable the Delete button in the UI when editing the Default profile.

---

#### Finding 4: In-Memory Credential Cache Desynchronization on Profile Deletion
* **Severity:** P2 — Medium
* **Locations:**
  * [`core/network/src/main/java/org/njarasoa/fijerena/core/network/profile/ProfileRepository.kt#L72-L80`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/profile/ProfileRepository.kt#L72-L80)
  * [`core/network/src/main/java/org/njarasoa/fijerena/core/network/provider/ProviderRepository.kt#L71`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/provider/ProviderRepository.kt#L71)
* **Mechanism:**
  `ProviderRepository` caches `EncryptedSharedPreferences` instances in `encryptedPrefsCache` keyed by filename. When `ProfileRepository` deletes a profile's XML files using `context.deleteSharedPreferences(it)`, `ProviderRepository` is not notified, leaving stale in-memory preferences references active in `encryptedPrefsCache`.
* **Remediation:** Route profile preference file deletion through `ProviderRepository` or provide an invalidation method that evicts matching entries from `encryptedPrefsCache`.

---

#### Finding 5: Lack of Automated Migration Tests for `SettingsDatabase` (v10 $\to$ v11, v11 $\to$ v12)
* **Severity:** P2 — Medium
* **Locations:**
  * [`core/network/src/main/java/org/njarasoa/fijerena/core/network/provider/SettingsDatabase.kt#L187-L204`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/provider/SettingsDatabase.kt#L187-L204)
* **Mechanism:**
  While `XtreamDatabase.MIGRATION_19_20` has an instrumented test in `XtreamDatabaseMigrationTest`, `SettingsDatabase` migrations 10 $\to$ 11 (`profiles` table creation + `insertDefaultProfile`) and 11 $\to$ 12 (`colorIndex` column) have no migration test in `androidTest`.
* **Remediation:** Add `SettingsDatabaseMigrationTest` verifying migration from v10 to v12 and confirming that existing providers/EPG sources are preserved and the default profile is properly inserted.

---

### Domain C: Android TV Design & Accessibility Violations

#### Finding 6: TV `ProfilePickerScreen` Uses Unscrollable `Row` (Overflows with $\ge 5$ Profiles)
* **Severity:** P2 — Medium (10-Foot UI Guideline Violation)
* **Location:** [`tv/src/main/java/org/njarasoa/fijerena/feature/profile/ProfilePickerScreen.kt#L86-L119`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/profile/ProfilePickerScreen.kt#L86-L119)
* **Mechanism:**
  ```kotlin
  Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg.scaled(scale))) {
      profiles.forEach { profile -> PickerCard(...) }
      PickerCard(...) // + Add profile
  }
  ```
  A plain `Row` does not scroll. On standard 1080p Android TV screens, 5 or more profiles + the "+ Add profile" card exceed the viewport width.
* **Impact:** Off-screen cards are clipped. D-pad navigation rightwards either traps focus or shifts focus to clipped off-screen items without scrolling them into view.
* **Remediation:** Replace `Row` with `TvLazyRow` (with `contentPadding` and D-pad scroll handling) or a wrapped grid matching the mobile pattern.

---

#### Finding 7: `ProfileAvatar` Slices UTF-16 Surrogate Pairs (Broken Emoji Names)
* **Severity:** P3 — Low (Visual Rendering Defect)
* **Location:** [`core/ui/src/main/java/org/njarasoa/fijerena/core/ui/components/ProfileAvatar.kt#L36`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/components/ProfileAvatar.kt#L36)
* **Mechanism:**
  ```kotlin
  text = name.trim().take(1).uppercase()
  ```
  `take(1)` extracts a single 16-bit `Char`. Emojis (e.g. 🍿, 🎬, 👶) consist of two UTF-16 code units (surrogate pairs).
* **Impact:** For emoji-prefixed profile names, `take(1)` isolates the high surrogate, resulting in replacement character `` or font layout warnings.
* **Remediation:** Use codepoints or grapheme-cluster-aware extraction (e.g. `Character.charCount` or `name.trim().codePoints()`).

---

#### Finding 8: Unscaled Title in TV `ProfilePickerScreen`
* **Severity:** P3 — Low
* **Location:** [`tv/src/main/java/org/njarasoa/fijerena/feature/profile/ProfilePickerScreen.kt#L82`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/profile/ProfilePickerScreen.kt#L82)
* **Mechanism:**
  `style = MaterialTheme.typography.displaySmall` does not apply `.scaled(scale)`, unlike all other typography on TV screens. It fails to adjust when the user modifies UI scale in Settings.
* **Remediation:** Apply `.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize.scaled(scale))`.

---

### Domain D: Architecture & Concurrency

#### Finding 9: Direct Repository Instantiation in UI Composables
* **Severity:** P3 — Low (Architectural Constraint Violation)
* **Locations:**
  * [`tv/src/main/java/org/njarasoa/fijerena/feature/contentselection/ContentTypeSelectionScreen.kt#L184, L544`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/feature/contentselection/ContentTypeSelectionScreen.kt#L184)
  * [`mobile/src/main/java/org/njarasoa/fijerena/feature/contentselection/ContentTypeSelectionScreen.kt#L143, L440`](file:///home/tahiry/data/code/mpilasy/fijerena/mobile/src/main/java/org/njarasoa/fijerena/feature/contentselection/ContentTypeSelectionScreen.kt#L143)
  * [`tv/src/main/java/org/njarasoa/fijerena/navigation/TvNavHost.kt#L128`](file:///home/tahiry/data/code/mpilasy/fijerena/tv/src/main/java/org/njarasoa/fijerena/navigation/TvNavHost.kt#L128)
* **Mechanism:**
  `ProviderRepository(context.applicationContext)` and `ProfileRepository(context.applicationContext)` are constructed directly inside `LaunchedEffect` blocks, button listeners, and NavHosts instead of being injected or retrieved from `AppContainer`.
* **Remediation:** Add `val profileRepository: ProfileRepository` to `AppContainer` and consume both repositories through `AppContainer.getInstance(context)`.

---

#### Finding 10: Floating CoroutineScope in `MediaProviderFactory.clearProfileScopedProviders()`
* **Severity:** P3 — Low
* **Location:** [`core/network/src/main/java/org/njarasoa/fijerena/core/network/MediaProviderFactory.kt#L115-L123`](file:///home/tahiry/data/code/mpilasy/fijerena/core/network/MediaProviderFactory.kt#L115-L123)
* **Mechanism:**
  `clearProfileScopedProviders()` launches an unmanaged `CoroutineScope(Dispatchers.IO)`. In `AppContainer.switchProfile()`, the function returns immediately while `disconnect()` runs in the background, creating a race if the next profile immediately connects to Jellyfin.
* **Remediation:** Make `clearProfileScopedProviders()` suspend or accept an application/worker scope, ensuring cleanup is deterministic.

---

### Domain E: Localization Completeness

#### Finding 11: Missing Profile Strings in Malagasy Localization (`values-mg/strings.xml`)
* **Severity:** P3 — Low
* **Location:** [`core/ui/src/main/res/values-mg/strings.xml`](file:///home/tahiry/data/code/mpilasy/fijerena/core/ui/src/main/res/values-mg/strings.xml)
* **Mechanism:**
  18 new profile and Jellyfin sign-in strings were added in English and French, but omitted from `values-mg/strings.xml`. Devices set to Malagasy fall back to English for all profile features.
* **Remediation:** Provide translations for the 18 strings in `values-mg/strings.xml`.

---

## 3. Action Plan & Phased Work Breakdown

### Phase 1: High-Priority Functional Fixes
- [ ] **Fix Home Screen Stuck Sign-In State:** Add lifecycle-aware check on `ON_RESUME` in `ContentTypeSelectionScreen` (TV & Mobile) to re-evaluate `hasLogin()` and reload capabilities when returning from `AddProvider`.
- [ ] **Protect Default Profile:**
  - Update `ProfileRepository.deleteProfile()` to reject `ProfileEntity.DEFAULT_ID` with `DeleteBlocked.DEFAULT`.
  - Disable or hide the Delete action in `ProfilesSettingsCard` for the Default profile.
- [ ] **Correct `hasLogin` Gating:** Ensure Jellyfin providers always verify a non-empty username or session token regardless of whether the profile is Default.

### Phase 2: TV UX & Accessibility Polish
- [ ] **Scrollable TV Profile Picker:** Refactor TV `ProfilePickerScreen` from unscrollable `Row` to `TvLazyRow` with proper item spacing and focus restoration.
- [ ] **Unicode Grapheme Handling in Avatars:** Update `ProfileAvatar` to extract complete codepoints/surrogate pairs for the initial glyph.
- [ ] **TV UI Scale Compliance:** Ensure the title on TV `ProfilePickerScreen` scales with `LocalUiScale.current`.

### Phase 3: Architectural Cleanup & Test Coverage
- [ ] **DI & Scope Cleanup:** Expose `ProfileRepository` on `AppContainer` and remove raw constructor calls from UI/NavHost composables.
- [ ] **SettingsDatabase Migration Tests:** Add `SettingsDatabaseMigrationTest` in `core:network` covering migrations 10 $\to$ 11 and 11 $\to$ 12.
- [ ] **Localizations:** Populate missing translations in `core/ui/src/main/res/values-mg/strings.xml`.

---

## 4. Resolution (2026-09-30)

Each finding checked against the code and on the TV/phone emulators.

| # | Verdict | Outcome |
|---|---|---|
| 1 | Not a defect | Navigation Compose disposes a destination's composition when navigating forward; returning recreates `remember` state and re-runs `LaunchedEffect`. Observed on the emulator: signing in from the prompt returned to a loaded library. |
| 2 | By design | Default's login is the provider-level one. The proposed fix would also send Local/Remote M3U providers (no username) to sign-in. |
| 3 | Fixed differently | Default stays deletable (user decision). Deleting it now clears its Jellyfin logins, Recent Categories, bookmarks and legacy blobs from the shared storage; other providers' logins stay. `DeleteDefaultProfileTest`. |
| 4 | Not a defect | Profile ids are UUIDs and never reused, so a stale cache entry for a deleted profile's file is unreachable. |
| 5 | Fixed | `SettingsDatabaseMigrationTest` (v10 → v12). |
| 6 | Fixed | TV picker is a `LazyRow`, scrolled to the active profile before focusing it. |
| 7 | Fixed | `initialOf` takes the first code point. `ProfileAvatarTest`. |
| 8 | Not a defect | `.scaled()` is a no-op; UI scale is applied globally through density. |
| 9 | Not pursued | Matches the existing pattern across nav hosts and screens. |
| 10 | Not a defect | Same pattern as the existing cache clears; old and new Jellyfin sessions use different tokens. |
| 11 | Deferred | Needs a Malagasy translator; matches the rest of `values-mg`. |
