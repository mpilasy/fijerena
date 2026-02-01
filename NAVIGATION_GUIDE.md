# Navigation Guide - Type-Safe Navigation Setup

Complete guide for the type-safe navigation system using kotlinx.serialization.

## 📦 Module Structure

```
:core:navigation    → Screen sealed interface definitions
:core:data          → AuthViewModel (shared state)
:mobile             → MobileNavHost (Material3 transitions)
:tv                 → TvNavHost (TV Material3, D-pad focus)
```

## 🎯 Screen Definitions

Located in `:core:navigation/Screen.kt`

```kotlin
sealed interface Screen {
    @Serializable
    data object Login : Screen

    @Serializable
    data object CategoryList : Screen

    @Serializable
    data class Player(val streamId: Int) : Screen
}
```

### Usage

```kotlin
// Navigate to Login
navController.navigate(Screen.Login)

// Navigate to Category List
navController.navigate(Screen.CategoryList)

// Navigate to Player with parameter
navController.navigate(Screen.Player(streamId = 12345))
```

## 🔐 AuthViewModel

Shared authentication state across Mobile and TV modules.

Located in `:core:data/AuthViewModel.kt`

### Features

- Holds current `XtreamAuthResponse`
- Provides authentication status checks
- Manages session lifecycle
- Shared across both platforms

### Usage

```kotlin
val authViewModel: AuthViewModel = viewModel()
val authResponse by authViewModel.authResponse.collectAsState()

// Set session after login
authViewModel.setAuthSession(authResponse, serverUrl)

// Check authentication
if (authViewModel.isAuthenticated()) {
    // User is logged in
}

// Clear session on logout
authViewModel.clearAuthSession()
```

## 📱 Mobile Navigation

Located in `:mobile/navigation/MobileNavHost.kt`

### Features

- Standard Material3 components
- Slide + fade transitions
- Touch-optimized UI
- Auto-navigation when authenticated

### Integration

```kotlin
// In MainActivity.kt or app entry point
@Composable
fun MobileApp() {
    val authViewModel: AuthViewModel = viewModel()

    MobileNavHost(authViewModel = authViewModel)
}
```

### Transitions

- **Enter**: Slide left + fade in (300ms)
- **Exit**: Slide left + fade out (300ms)
- **Pop Enter**: Slide right + fade in (300ms)
- **Pop Exit**: Slide right + fade out (300ms)

## 📺 TV Navigation

Located in `:tv/navigation/TvNavHost.kt`

### Features

- androidx.tv.material3 components
- D-pad focus management
- Auto-focus restoration
- TV-safe UI spacing

### Integration

```kotlin
// In TV MainActivity.kt
@Composable
fun TvApp() {
    val authViewModel: AuthViewModel = viewModel()

    TvNavHost(authViewModel = authViewModel)
}
```

### D-Pad Focus Handling

Each screen should implement:
- `FocusRequester` for initial focus
- `Modifier.focusable()` on interactive elements
- `Modifier.focusRestorer()` for returning focus
- TV-safe padding (48dp) for overscan

## 🚀 Navigation Flow

```
┌─────────────┐
│   Login     │
└──────┬──────┘
       │ (success)
       ↓
┌─────────────┐
│ CategoryList│
└──────┬──────┘
       │ (select stream)
       ↓
┌─────────────┐
│   Player    │
└──────┬──────┘
       │ (back)
       ↓
┌─────────────┐
│ CategoryList│
└─────────────┘
```

### Navigation Rules

1. **Login → CategoryList**: Clear login from back stack
2. **CategoryList → Player**: Keep category list in back stack
3. **Player → Back**: Return to category list
4. **Logout**: Clear all and return to Login

### Example Navigation

```kotlin
// After successful login
navController.navigate(Screen.CategoryList) {
    popUpTo(Screen.Login) { inclusive = true }
}

// Navigate to player
navController.navigate(Screen.Player(streamId = 12345))

// Navigate back
navController.navigateUp()

// Logout
authViewModel.clearAuthSession()
navController.navigate(Screen.Login) {
    popUpTo(Screen.CategoryList) { inclusive = true }
}
```

## 🛠️ ADB Deployment (Linux)

### Prerequisites

1. Enable ADB debugging on your Shield/Sony TV:
   - Go to Settings → Device Preferences → About
   - Click "Build" 7 times to enable Developer Options
   - Go to Developer Options → Enable "USB debugging" and "Network debugging"

2. Find your TV's IP address:
   - Settings → Network → Network Status

### Configure Deployment

Edit `gradle.properties`:

```properties
# Set your TV's IP address
tv.ip.address=192.168.1.100
```

### Deploy Commands

#### Deploy to Shield/Sony TV

```bash
./gradlew deployToShield
```

This will:
1. Connect to TV via ADB (port 5555)
2. Build TV debug APK
3. Install on TV

#### Install TV Debug (to already connected device)

```bash
./gradlew installTvDebug
```

Or directly:

```bash
./gradlew :tv:installDebug
```

### Manual ADB Commands

```bash
# Connect to TV
adb connect 192.168.1.100:5555

# Verify connection
adb devices

# Install APK manually
adb install -r tv/build/outputs/apk/debug/tv-debug.apk

# Disconnect
adb disconnect 192.168.1.100:5555
```

### Troubleshooting

**Connection refused:**
- Ensure TV and PC are on same network
- Verify ADB debugging is enabled on TV
- Restart TV's ADB: Settings → Developer Options → Toggle USB debugging

**Installation failed:**
- Check if app is already installed: `adb uninstall org.njarasoa.fijerena`
- Ensure TV has enough storage

**APK not found:**
- Build first: `./gradlew :tv:assembleDebug`
- Check `tv/build/outputs/apk/debug/`

## 🔧 Adding New Screens

### 1. Define Screen in :core:navigation

```kotlin
sealed interface Screen {
    // Existing screens...

    @Serializable
    data class Settings(val userId: String) : Screen
}
```

### 2. Add Composable to NavHost

**Mobile:**

```kotlin
// In MobileNavHost.kt
composable<Screen.Settings> { backStackEntry ->
    val settingsScreen = backStackEntry.toRoute<Screen.Settings>()
    MobileSettingsScreen(
        userId = settingsScreen.userId,
        onBack = { navController.navigateUp() }
    )
}
```

**TV:**

```kotlin
// In TvNavHost.kt
composable<Screen.Settings> { backStackEntry ->
    val settingsScreen = backStackEntry.toRoute<Screen.Settings>()
    TvSettingsScreen(
        userId = settingsScreen.userId,
        onBack = { navController.navigateUp() }
    )
}
```

### 3. Navigate to New Screen

```kotlin
navController.navigate(Screen.Settings(userId = "12345"))
```

## 📝 Best Practices

### Mobile

- Use touch-friendly tap targets (48dp minimum)
- Implement swipe gestures for back navigation
- Handle orientation changes gracefully
- Use Material3 transitions consistently

### TV

- Always use `FocusRequester` for initial focus
- Make focused states highly visible (scale, color, border)
- Implement D-pad navigation chains
- Respect 5% overscan margins (48dp padding)
- Test with NVIDIA Shield and Sony Bravia TVs

### Both Platforms

- Share ViewModels via :core:data
- Keep navigation logic in NavHost files
- Use AuthViewModel for auth state
- Handle deep links if needed
- Implement proper back stack management

## 🧪 Testing Navigation

### Manual Testing

```kotlin
// Test navigation flow
@Test
fun testLoginFlow() {
    // Start at login
    composeTestRule.onNodeWithText("Login").assertExists()

    // Perform login
    // ...

    // Verify navigation to CategoryList
    composeTestRule.onNodeWithText("Category List").assertExists()
}
```

### ADB Testing on TV

```bash
# Launch app
adb shell am start -n org.njarasoa.fijerena/.MainActivity

# Simulate D-pad navigation
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_DPAD_CENTER

# Simulate back button
adb shell input keyevent KEYCODE_BACK
```

## 📚 Dependencies

All navigation dependencies are managed in `:core:navigation`:

```kotlin
// build.gradle.kts
dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.androidx.navigation.compose)
}
```

Both :mobile and :tv modules depend on :core:navigation:

```kotlin
dependencies {
    implementation(project(":core:navigation"))
    implementation(project(":core:data"))
}
```

## 🎬 Next Steps

1. **Implement CategoryListScreen**
   - Fetch categories from XtreamApiService
   - Display in grid layout
   - Handle stream selection

2. **Implement PlayerScreen**
   - Integrate StreamingPlaybackService
   - Show playback controls
   - Handle stream playback

3. **Add Session Persistence**
   - Implement DataStore in AuthViewModel
   - Auto-login on app launch
   - Handle session expiration

4. **Deep Linking** (Optional)
   - Configure deep links in manifest
   - Handle stream URLs from external sources
