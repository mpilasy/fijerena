# Plan: Replace Jetpack Security Crypto with an owned SecretStore

**Status:** Not started. Deferred deliberately — see "Do nothing" below.
**Raised:** 2026-08-28, when `androidx.security:security-crypto` was bumped
`1.1.0-alpha06` → `1.1.0` and the compiler emitted 34 deprecation warnings.

---

## Part 1 — Why this matters, in plain terms

### What the app does today

When you add a provider, the app has to keep your password. It can't ask you
for it on every request, so it writes it to disk. Storing a password as plain
text in a file would be indefensible, so the app encrypts it first.

Two pieces do that job:

- **The Android Keystore.** A vault built into the phone, usually backed by a
  separate security chip. You ask it to create an encryption key, and it does —
  but it never hands the key back. You pass data *in* and get encrypted data
  *out*. Even an attacker who copies the whole filesystem gets nothing useful,
  because the key never existed in a file to begin with.

- **`EncryptedSharedPreferences`.** A convenience wrapper from Google. Android
  has a simple built-in way to save small values ("SharedPreferences" — a
  key-value file). This wrapper puts encryption in front of it: you write
  `password = "hunter2"`, it asks the Keystore to encrypt that, and saves the
  scrambled result.

So: the wrapper does the bookkeeping, the Keystore does the actual protecting.

The app has two of these encrypted files:

| File | Written by | What's in it |
|---|---|---|
| `xtream_secure_credentials` | `AccountManager` | `url`, `username`, `password`, `auth_response`, `remember_me` |
| `provider_creds_<providerId>` | `ProviderRepository` | `password`, `jellyfin_token`, `jellyfin_user_id` |

Eight values, three places in the code that touch them. That's the whole
surface. It is small.

### What changed

Google **deprecated** the wrapper. Not the Keystore — the Keystore is fine and
isn't going anywhere. Just their convenience layer on top of it.

"Deprecated" means: *we are no longer working on this, don't build new things
with it.* It does **not** mean broken, and it does **not** mean insecure. The
code still runs, still encrypts correctly, and nothing about your saved
passwords got weaker the day they announced it.

Worth knowing how odd the timing was: this library spent years in "alpha" (a
label meaning *not finished, may change*). Version `1.1.0` was its first
finished, stable release — and Google marked it deprecated in that same
release. It was declared done and abandoned simultaneously. That tells you no
one is going to fix it if something goes wrong later.

### So why change anything?

Two reasons. The second is the one that actually justifies the work.

**Reason 1: unmaintained code holding your passwords is a slow leak.**

The wrapper depends on other libraries underneath it (Google's "Tink" crypto
toolkit). Those keep moving. Android keeps moving — every year brings a new
version with new rules about what apps may do. Normally, when something shifts
underfoot, the library authors publish a fix and you bump a version number.
Here, nobody is going to publish that fix.

And the place it would surface is the worst possible place: reading your
credentials, which happens the moment the app starts. Not a corner feature you
could live without for a week.

This is not urgent. It is the kind of thing that is cheap to fix now and
expensive to fix in a panic later.

**Reason 2: it can already put the app in a crash loop, and you've hit it.**

This one is a real bug that exists today, not a hypothetical.

The password file and the Keystore key are two separate things stored in two
separate places. Almost always they travel together. Occasionally they don't:

- app data is partly cleared (`pm clear`, some cleaner tools, some restores)
- a backup is restored onto a different phone — the file comes along, the key
  cannot, because the key physically lives in the old phone's security chip
- the Keystore invalidates the key on its own (this is a documented behaviour
  after certain screen-lock changes)

Now the encrypted file exists, but the key that could read it does not.

`EncryptedSharedPreferences.create()` responds to that by throwing an
exception. Where the app catches that, you get a graceful failure —
`MediaProviderFactory.getJellyfinSessionPrefs()` catches it and returns `null`.
But `ProviderRepository.getProviderPrefs()` does **not** catch it. The
exception escapes, and the app crashes. On the next launch it reads the same
file, throws again, and crashes again. It cannot recover on its own, because
nothing in the app ever deletes the unreadable file. The only way out is
deleting it by hand over `adb` — which is exactly the workaround already
recorded in the project notes.

The right behaviour is obvious and boring: notice the credentials can't be
read, throw them away, and show the login screen. A user re-enters a password
once. Nobody crashes.

You could fix just that with a `try/catch` and keep the deprecated library. But
once you're writing the recovery logic anyway, owning the ~100 lines
underneath costs little more and removes reason 1 at the same time.

### What "owning it" actually means

Not writing cryptography. Writing the bookkeeping the deprecated wrapper was
doing, and calling the same platform APIs it called.

- Same algorithm: AES-256-GCM.
- Same key storage: the Android Keystore, same as today.
- Same protection level: unchanged. Nothing gets weaker.

The difference is that when it misbehaves, the fix is in this repo instead of
in an abandoned library.

One simplification worth taking while we're here: the old wrapper encrypted the
*names* as well as the values. The names are `password`, `jellyfin_token`,
`jellyfin_user_id`. Those aren't secrets — they're the same in every install of
the app, and leak nothing about you. Encrypting the values and leaving the
names in the clear is roughly half the code for the same real protection.

### Doing nothing is a legitimate choice

It still works. There is no known exploit. Deferring is defensible.

If you defer, fix the crash loop anyway — wrap `getProviderPrefs()` in a
`try/catch` that deletes the unreadable file and returns an empty store. That's
a few lines, and it removes the only failure here that actually bites users.

---

## Part 2 — The plan

### Phase 1: `SecretStore`

New file: `core/network/src/main/java/org/njarasoa/fijerena/core/network/security/SecretStore.kt`

```
class SecretStore(context: Context, storeName: String) {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun clear()
}
```

- One AES-256-GCM Keystore key, alias `fijerena_secret_store_v1`, created via
  `KeyGenParameterSpec` on first use. `minSdk 30`, so no back-compat branches.
- Values stored in an ordinary `SharedPreferences` as base64 of `IV ‖ ciphertext`.
  A fresh random IV per write — never reuse one with GCM.
- Keys stored in the clear (see above).
- **Recovery is the point of the exercise:** every read catches
  `AEADBadTagException`, `KeyPermanentlyInvalidatedException`, and
  `UnrecoverableKeyException`; on any of them, wipe the store, drop the Keystore
  key, and return `null`. Never let those escape to the caller.

### Phase 2: One-time migration

In `SecretStore.init`, if a legacy file of the same name exists: open it with
`EncryptedSharedPreferences` one final time, copy every key across, delete the
legacy file, and mark it done. Wrapped in `try/catch` — a legacy file that can
no longer be decrypted is deleted, not fatal. No re-login for anyone whose
Keystore key is intact.

### Phase 3: Swap the three call sites

| File | Change |
|---|---|
| `AccountManager.kt` | `encryptedPrefs()` → `SecretStore(context, "xtream_secure_credentials")` |
| `ProviderRepository.kt` | `masterKey` + `getProviderPrefs()` → per-provider `SecretStore`, cache unchanged |
| `MediaProviderFactory.kt` | `getJellyfinSessionPrefs()` → `SecretStore`; the `catch → null` becomes redundant |

Delete the four `@Suppress("DEPRECATION")` blocks and their comments. Drop
`androidx.security:security-crypto` from `core/network/build.gradle.kts` **one
release after** the migration ships, so upgraders from older builds still have
the legacy reader available.

### Phase 4: Tests

None of this is covered today.

- Round-trip: write, read back, `clear()` empties it.
- Recovery: with a store populated, delete the Keystore key, then read — expect
  `null` and a wiped store, not an exception.
- Migration: write via `EncryptedSharedPreferences`, construct `SecretStore`,
  assert the values transferred and the legacy file is gone.

Robolectric or `androidTest` — Keystore needs a real or emulated device.

### Phase 5: Verify on device

Migration correctness cannot be judged from a fresh install.

1. Install the **current** build on both emulators, with real Xtream and
   Jellyfin credentials saved (5556 already has them).
2. Install the migrated build over it — no uninstall.
3. Confirm both providers still load without re-login, and that the legacy
   files are gone:
   `adb shell run-as org.njarasoa.fijerena ls shared_prefs/`
4. Force the recovery path: `adb shell pm clear` is too blunt; instead delete
   the Keystore key while leaving the prefs file, then launch. Expect the login
   screen, **not** a crash.
5. Repeat on the TV emulator — the TV login flow is a separate screen.

### Rollback

Phases 1–3 are one commit and revert cleanly *as long as the dependency is
still declared*. Once a device has migrated, a reverted build reads the legacy
file, finds it deleted, and shows the login screen — recoverable, but the user
re-enters a password. Hence the one-release delay before removing the
dependency.

### Effort

Roughly half a day. The risk is concentrated in Phase 2 and is entirely a
data-migration risk, not a cryptography one.
