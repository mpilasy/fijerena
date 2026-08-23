# Mobile App - Build & Run Guide

## Current Status

✅ **Code**: All Kotlin code compiles successfully
✅ **Dependencies**: All libraries resolve correctly
✅ **Configuration**: Mobile module properly configured
✅ **Environment**: JDK 21 and Android SDK tools installed and working

## Prerequisites

JDK 21 and Android SDK are required. If not already installed:

### Install Full JDK
```bash
# Ubuntu/Debian
sudo apt-get install openjdk-21-jdk
javac -version  # Should show: javac 21.x.x
```

### Install Android SDK
Download Android command-line tools or Android Studio from developer.android.com/studio.

## Building the Mobile APK

Once JDK is installed:

### Debug Build (for development/testing)
```bash
./gradlew :mobile:assembleDebug
```

**Output**: `build/outputs/apk/fijerena-mobile-debug.apk` (and `mobile/build/outputs/apk/debug/mobile-debug.apk`)

### Release Build (for production)
```bash
./gradlew :mobile:assembleRelease
```

**Output**: `mobile/build/outputs/apk/release/mobile-release.apk`

## Installing on Device

### Via USB Cable

1. **Enable USB Debugging on your phone:**
   - Settings → About Phone → Tap "Build Number" 7 times
   - Settings → Developer Options → Enable "USB Debugging"

2. **Connect phone via USB**

3. **Verify connection:**
   ```bash
   adb devices
   # Should show your device
   ```

4. **Install directly from Gradle:**
   ```bash
   ./gradlew :mobile:installDebug
   ```

   **Or install the APK manually:**
   ```bash
   adb install mobile/build/outputs/apk/debug/mobile-debug.apk
   ```

### Via Wireless (ADB over WiFi)

1. **Connect phone and computer to same WiFi**

2. **Enable wireless debugging:**
   ```bash
   # First time: connect via USB
   adb tcpip 5555

   # Find phone's IP (Settings → About Phone → Status)
   adb connect 192.168.1.XXX:5555

   # Now you can disconnect USB
   ```

3. **Install wirelessly:**
   ```bash
   ./gradlew :mobile:installDebug
   ```

### Via File Transfer (no ADB)

1. **Build APK:**
   ```bash
   ./gradlew :mobile:assembleDebug
   ```

2. **Transfer APK to phone:**
   - Copy `mobile/build/outputs/apk/debug/mobile-debug.apk` to your phone
   - Via USB file transfer, Google Drive, email, etc.

3. **Install on phone:**
   - Open the APK file on your phone
   - Allow "Install from Unknown Sources" if prompted
   - Tap "Install"

## Running the App

### From Android Studio
1. Open project in Android Studio
2. Select "mobile" configuration from dropdown
3. Click Run (green triangle) or Shift+F10
4. Select your device
5. App will build, install, and launch automatically

### From Command Line
```bash
# Build and install
./gradlew :mobile:installDebug

# Launch the app
adb shell am start -n org.njarasoa.fijerena/.MainActivity
```

## Useful Commands

### Build Commands
```bash
# Clean build
./gradlew clean :mobile:assembleDebug

# Build all variants
./gradlew :mobile:assemble

# Check for build issues
./gradlew :mobile:check
```

### Installation Commands
```bash
# Install debug build
./gradlew :mobile:installDebug

# Uninstall app
./gradlew :mobile:uninstallDebug
# Or: adb uninstall org.njarasoa.fijerena

# Install and run
./gradlew :mobile:installDebug && adb shell am start -n org.njarasoa.fijerena/.MainActivity
```

### Debugging Commands
```bash
# View logcat (app logs)
adb logcat | grep "fijerena"

# View crash logs
adb logcat *:E

# Clear logcat
adb logcat -c

# View app info
adb shell dumpsys package org.njarasoa.fijerena
```

## Mobile Module Configuration

### Current Setup
```kotlin
applicationId: org.njarasoa.fijerena
minSdk: 30  (Android 11 and above)
targetSdk: 36  (Latest Android)
versionCode: 4
versionName: 1.0.0
```

### Features Included
- ✅ StreamingService (shared :core:player library)
- ✅ Media3 ExoPlayer 1.7.1
- ✅ Device-aware codec selection
- ✅ Network streaming (HTTP/HTTPS/HLS)
- ✅ Wake lock management
- ✅ Foreground service for playback
- ✅ ViewModel-based state management

### Permissions (already configured)
- `INTERNET` - Network streaming
- `ACCESS_NETWORK_STATE` - Connection monitoring
- `WAKE_LOCK` - Prevent sleep during playback
- `FOREGROUND_SERVICE` - Background playback
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` - Media service

## Testing on Emulator

### Create Android Emulator (via Android Studio)
1. Tools → Device Manager
2. Create Virtual Device
3. Select phone (e.g., Pixel 5)
4. Select system image (Android 14/API 34 recommended)
5. Finish

### Run on Emulator
```bash
# List emulators
emulator -list-avds

# Start emulator
emulator -avd Pixel_5_API_34

# Install app
./gradlew :mobile:installDebug
```

## Troubleshooting

### Build Fails
```bash
# Clean and rebuild
./gradlew clean :mobile:assembleDebug

# Check dependencies
./gradlew :mobile:dependencies

# Refresh dependencies
./gradlew --refresh-dependencies
```

### Install Fails
```bash
# Check device connection
adb devices

# Clear app data
adb shell pm clear org.njarasoa.fijerena

# Reinstall
adb uninstall org.njarasoa.fijerena
./gradlew :mobile:installDebug
```

### App Crashes
```bash
# View crash logs
adb logcat -c  # Clear logs
adb logcat    # View new logs

# Filter for errors
adb logcat *:E

# Save logs to file
adb logcat > crash_log.txt
```

## Quick Start

```bash
# Build
./gradlew :mobile:assembleDebug

# Install on connected device
./gradlew :mobile:installDebug

# Or install specific APK
adb -s BH9044V7BZ install -r mobile/build/outputs/apk/debug/mobile-debug.apk
```

## Quick Start Script

Save this as `run_mobile.sh`:

```bash
#!/bin/bash

echo "🔨 Building mobile APK..."
./gradlew :mobile:assembleDebug

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"

    echo "📱 Installing on device..."
    ./gradlew :mobile:installDebug

    if [ $? -eq 0 ]; then
        echo "✅ Installation successful!"

        echo "🚀 Launching app..."
        adb shell am start -n org.njarasoa.fijerena/.MainActivity

        echo "📊 Showing logs..."
        adb logcat | grep "fijerena"
    else
        echo "❌ Installation failed. Check device connection."
    fi
else
    echo "❌ Build failed. Check errors above."
fi
```

Make executable: `chmod +x run_mobile.sh`
Run: `./run_mobile.sh`

---

**Build Status**: ✅ Fully operational — JDK 21 and Android SDK installed, builds and deploys successfully.
