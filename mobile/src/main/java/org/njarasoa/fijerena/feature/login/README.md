# Login Feature

Xtream IPTV authentication module with platform-specific UIs for Mobile and TV.

## Components

### LoginViewModel
- **Purpose**: Manages authentication state and API calls
- **Dependency**: `XtreamApiService` (optional, created dynamically if not injected)
- **States**:
  - `Idle`: Initial state
  - `Loading`: During authentication
  - `Success`: Authentication succeeded
  - `Error`: Authentication failed with error message
- **Remember Me**: Supports credential persistence (TODO: implement DataStore)

### LoginScreen (Composable) - Mobile
- **Purpose**: Touch-optimized UI for phones/tablets
- **Package**: Uses `androidx.compose.material3`
- **Features**:
  - Server URL, username, password inputs
  - Keyboard actions (Next/Done) for smooth flow
  - Remember Me checkbox for session persistence
  - Loading indicator
  - Error message display in colored container
  - Help text at bottom

### LoginScreenTv (Composable) - Android TV
- **Purpose**: D-pad optimized UI for TV devices
- **Package**: Uses `androidx.tv.material3`
- **Features**:
  - Wide input fields (600dp) for 10-foot UI
  - Auto-focus on first field using FocusRequester
  - Highly visible focused button (bright green)
  - Button scale animation (1.05x when focused)
  - TV-safe padding (5% overscan margins)
  - D-pad navigation chain

## Usage Example

### Mobile App - Use LoginScreen

```kotlin
import org.njarasoa.fijerena.feature.login.LoginScreen

@Composable
fun MobileAppNavigation() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            MobileHomeScreen()
        }
    }
}
```

### TV App - Use LoginScreenTv

```kotlin
import org.njarasoa.fijerena.feature.login.LoginScreenTv

@Composable
fun TvAppNavigation() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "login") {
        composable("login") {
            LoginScreenTv(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            TvHomeScreen()
        }
    }
}
```

### Custom ViewModel Usage

```kotlin
val viewModel: LoginViewModel = viewModel(
    factory = LoginViewModelFactory(
        apiService = myCustomApiService // Optional
    )
)

LoginScreen(
    viewModel = viewModel,
    onLoginSuccess = { /* Navigate to home */ }
)
```

### Manual State Observation

```kotlin
val viewModel: LoginViewModel = viewModel()
val uiState by viewModel.uiState.collectAsState()

when (uiState) {
    is LoginViewModel.UiState.Idle -> {
        // Show login form
    }
    is LoginViewModel.UiState.Loading -> {
        // Show loading spinner
    }
    is LoginViewModel.UiState.Success -> {
        val authResponse = (uiState as LoginViewModel.UiState.Success).authResponse
        // Navigate to home, show user info, etc.
    }
    is LoginViewModel.UiState.Error -> {
        val errorMsg = (uiState as LoginViewModel.UiState.Error).message
        // Show error toast or message
    }
}

// Trigger login with Remember Me
viewModel.login(
    url = "http://example.com:8080",
    username = "user",
    password = "pass",
    rememberMe = true
)
```

## API Authentication Flow

1. User enters credentials (server URL, username, password)
2. `LoginViewModel.login()` is called
3. Creates `XtreamApiService` with credentials
4. Calls `apiService.authenticate()`
5. Validates response (`auth == 1` and `status == "Active"`)
6. Saves session (TODO: implement DataStore/SharedPreferences)
7. Updates UI state to `Success` or `Error`

## Session Persistence (TODO)

Currently, session saving is a placeholder. Implement using:

### Option 1: DataStore (Recommended)

```kotlin
// Add to build.gradle.kts
implementation("androidx.datastore:datastore-preferences:1.0.0")

// Create PreferencesKeys
object PreferencesKeys {
    val SERVER_URL = stringPreferencesKey("server_url")
    val USERNAME = stringPreferencesKey("username")
    val PASSWORD = stringPreferencesKey("password") // Consider encryption
    val EXP_DATE = stringPreferencesKey("exp_date")
}

// In LoginViewModel
private suspend fun saveSession(...) {
    dataStore.edit { preferences ->
        preferences[PreferencesKeys.SERVER_URL] = url
        preferences[PreferencesKeys.USERNAME] = username
        preferences[PreferencesKeys.PASSWORD] = password
        preferences[PreferencesKeys.EXP_DATE] = authResponse.userInfo.expDate ?: ""
    }
}
```

### Option 2: Encrypted SharedPreferences

```kotlin
val sharedPreferences = EncryptedSharedPreferences.create(
    context,
    "xtream_session",
    MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

sharedPreferences.edit {
    putString("server_url", url)
    putString("username", username)
    putString("password", password)
}
```

## Testing

### Unit Test Example

```kotlin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @Test
    fun `login success updates state`() = runTest {
        val mockService = MockXtreamApiService()
        val viewModel = LoginViewModel(mockService)

        viewModel.login("http://test.com", "user", "pass")

        assert(viewModel.uiState.value is LoginViewModel.UiState.Success)
    }
}
```

## D-Pad Navigation

The `LoginScreen` is optimized for Android TV remote controls:
- **Tab/Arrow Down**: Move to next field
- **Enter/OK**: Submit login when on button
- **Back**: Clear focus or navigate back

Ensure your Activity has proper focus handling:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyTheme {
                LoginScreen()
            }
        }
    }
}
```

## Next Steps

1. Implement session persistence (DataStore)
2. Add "Remember Me" checkbox
3. Add auto-login on app start if session is valid
4. Implement session refresh before expiration
5. Add logout functionality
