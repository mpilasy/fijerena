# Build, Deployment & Run Guide

Unified guide for building, installing, and deploying Fijerena across both **Android TV** (NVIDIA Shield, Chromecast with Google TV, Sony Bravia) and **Android Mobile** (phones and tablets).

---

## Prerequisites

- **JDK:** OpenJDK 21
- **Android SDK:** Command-line tools or Android Studio
- **API Targets:** `minSdk = 30` (Android 11+), `targetSdk = 36`
- **Gradle:** 9.4.1+ (via `./gradlew` wrapper)

Verify environment:
```bash
javac -version   # Should report 21.x.x
./gradlew -v     # Verifies Gradle and JVM compatibility
```

---

## Building the Application

AGP builds APKs under each module's standard build directory:
- **TV APK:** `tv/build/outputs/apk/debug/tv-debug.apk`
- **Mobile APK:** `mobile/build/outputs/apk/debug/mobile-debug.apk`

### Debug Builds (Development)

```bash
# Build both TV and Mobile targets
./gradlew assembleDebug

# Build TV target only
./gradlew :tv:assembleDebug

# Build Mobile target only
./gradlew :mobile:assembleDebug
```

> [!IMPORTANT]
> **Pre-Deployment Clean Build Rule:** Whenever code changes span multiple modules (such as modifying `core:*` libraries consumed by `:tv` or `:mobile`), do **not** deploy from an incremental build. Always run a clean build to prevent stale intermediate DEX shards (`NoClassDefFoundError`):
> ```bash
> ./gradlew clean assembleDebug
> ```

### Release Builds (Production)

```bash
./gradlew :tv:assembleRelease
./gradlew :mobile:assembleRelease
```

---

## Quality Control & Verification

Run style checks and tests before committing code:

```bash
# Code style inspection (ktlint)
./gradlew ktlintCheck

# Auto-format style violations
./gradlew ktlintFormat

# Run Android Lint
./gradlew lintDebug

# Run unit tests
./gradlew test
```

---

## Deployment & Device Targeting

Both TV and Mobile share the identical `applicationId`: `org.njarasoa.fijerena`.

> [!CAUTION]
> **Strict Deployment Rules:**
> 1. **Preserve User Data:** Never run `adb uninstall` or clear data to resolve deployment issues. Always use `adb install -r` to preserve Room databases, user credentials, favorites, and watch state.
> 2. **Device Detection:** Always detect device type via `getprop ro.build.characteristics` (or inspect `product:`/`model:` in `adb devices -l`) before deploying. Never assume target identity from port numbers or IPs.
> 3. **No Auto-Launch:** Never automatically launch the app (`am start` or monkey intents) after install. Let the user launch the app manually when ready.

### 1. Emulator Targets

Emulator port numbers (`emulator-5554`, `emulator-5556`, etc.) are assigned by **launch order**, not by AVD type.

```bash
# Inspect all connected devices and emulators
adb devices -l

# Check characteristics if ambiguous
adb -s <emulator-id> shell getprop ro.build.characteristics
# TV returns "tv", mobile returns "default" or "nosdcard"

# Install to respective emulator
adb -s <tv-emulator-id> install -r tv/build/outputs/apk/debug/tv-debug.apk
adb -s <mobile-emulator-id> install -r mobile/build/outputs/apk/debug/mobile-debug.apk
```

### 2. Physical Android TV (NVIDIA Shield, Chromecast, Sony Bravia)

TV devices connect via ADB over TCP/IP (port 5555). Because TV IP addresses drift across sessions via DHCP (on development subnet `192.168.68.0/24`):

1. **Find TV on Network:**
   ```bash
   # Query mDNS for broadcasting ADB devices
   adb mdns services
   ```
   *(Falls back to `arp -a | grep 192.168.68.` if mDNS is unavailable)*

2. **Connect & Verify:**
   ```bash
   adb connect <TV_IP>:5555
   adb -s <TV_IP>:5555 shell getprop ro.build.characteristics  # Confirms "tv"
   ```

3. **Deploy TV APK:**
   ```bash
   adb -s <TV_IP>:5555 install -r tv/build/outputs/apk/debug/tv-debug.apk
   ```

### 3. Physical Android Mobile (Phones / Tablets)

Connect phone via USB cable or Wireless Debugging (Settings → Developer Options):

```bash
# Verify connection
adb devices -l

# Deploy Mobile APK
adb -s <device-id> install -r mobile/build/outputs/apk/debug/mobile-debug.apk
```

---

## Logcat & Debugging

Filter logs for app-specific diagnostics:

```bash
# Stream app logs
adb -s <device-id> logcat | grep "fijerena"

# Stream crash logs and unhandled exceptions
adb -s <device-id> logcat *:E

# Clear logcat buffer
adb -s <device-id> logcat -c
```

---

## Device-Specific Tips

- **NVIDIA Shield:** Supports 4K/HDR and AV1/HEVC hardware decoding. Ideal for stress-testing heavy streams.
- **Sony Bravia:** Features mid-range TV chipsets. Test for overscan compliance (56dp horizontal, 32dp vertical margins) and smooth 60fps D-pad focus animations.
- **Mobile Devices:** Locked to portrait orientation outside the player; sensor-unlocked inside the player.
