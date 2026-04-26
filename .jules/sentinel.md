## 2025-02-14 - Prevent Stack Trace Leaks via secure logging
**Vulnerability:** Calls to `e.printStackTrace()` were found in `RefreshQueue.kt`, `XtreamContentManager.kt`, and `XtreamSyncWorker.kt`.
**Learning:** `printStackTrace()` exposes internal stack traces directly to the standard error stream, which can inadvertently leak sensitive information about the application's internal state or architecture.
**Prevention:** Always use secure logging mechanisms such as `android.util.Log.e("Tag", "Message", e)` to handle exceptions securely and defensively without leaking stack traces.
