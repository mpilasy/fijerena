# Login Feature (Legacy)

**Status:** Not in active navigation flow. Authentication is now handled automatically on startup via stored credentials, or through the Settings screen.

The `LoginScreen.kt` composable still exists but is not registered in `MobileNavHost`. The `LoginViewModel` is still used internally by the Settings screen for credential validation.

## Current Authentication Flow

1. **App startup**: `AccountManager.hasStoredCredentials()` determines start destination
2. **Provider configured**: Auto-restore session via `XtreamRepository.restoreSession()`
3. **No provider**: Navigate to Settings where user enters provider URL + credentials
4. **Settings**: Provider management handles add/edit/delete through `ProviderViewModel`

## Components (Legacy)

### LoginViewModel
- Manages authentication state and API calls
- Still used for session restore and credential validation
- States: Idle, Loading, Success, Error

### LoginScreen (Mobile) — Not in nav graph
- Touch-optimized login form
- Server URL, username, password inputs
- Remember Me checkbox

## Migration Note

The login screen was removed from both TV and mobile navigation in Phase 5. Authentication is now provider-based:
- Providers are managed via Settings → Manage Providers
- Credentials stored in per-provider EncryptedSharedPreferences
- Session auto-restored on app launch
