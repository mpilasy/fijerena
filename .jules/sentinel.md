## 2024-06-18 - [Exported Debug Receiver]
**Vulnerability:** EpgSyncDebugReceiver was exported=true in tv/src/main/AndroidManifest.xml, allowing any third-party app to trigger internal sync workers.
**Learning:** Even debug-only components need access control (exported=false) if they trigger work, because third-party apps can still send intents to them if the app is installed.
**Prevention:** Always set exported=false for internal receivers or protect them with signature-level permissions.
