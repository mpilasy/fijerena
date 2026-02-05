package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.provider.ProviderSettings

/**
 * Firebase Realtime Database sync service for provider settings.
 * Provides bidirectional sync when Firebase is configured, gracefully degrades when not available.
 *
 * To enable Firebase sync:
 * 1. Create a Firebase project at https://console.firebase.google.com
 * 2. Add Android apps for both mobile and TV with package name "org.njarasoa.fijerena"
 * 3. Download google-services.json and place in mobile/ and tv/ directories
 * 4. Apply the google-services plugin in both module build.gradle.kts files:
 *    plugins {
 *        alias(libs.plugins.google.services)
 *    }
 * 5. Set up Realtime Database rules in Firebase console:
 *    {
 *      "rules": {
 *        "users": {
 *          "$uid": {
 *            ".read": "$uid === auth.uid",
 *            ".write": "$uid === auth.uid"
 *          }
 *        }
 *      }
 *    }
 */
class FirebaseSettingsSync(private val context: Context) {

    companion object {
        private const val TAG = "FirebaseSettingsSync"
        private const val PATH_USERS = "users"
        private const val PATH_PROVIDERS = "providers"
        private const val PATH_SETTINGS = "settings"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Check if Firebase is available and initialized.
     */
    val isAvailable: Boolean
        get() = try {
            FirebaseApp.getInstance() != null
        } catch (e: IllegalStateException) {
            false
        }

    /**
     * Check if user is signed in anonymously.
     */
    val isSignedIn: Boolean
        get() = try {
            FirebaseAuth.getInstance().currentUser != null
        } catch (e: Exception) {
            false
        }

    /**
     * Current user ID (null if not signed in).
     */
    val currentUserId: String?
        get() = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: Exception) {
            null
        }

    /**
     * Sync status for UI display.
     */
    sealed interface SyncStatus {
        data object Unavailable : SyncStatus
        data object SignedOut : SyncStatus
        data object Syncing : SyncStatus
        data object Synced : SyncStatus
        data class Error(val message: String) : SyncStatus
    }

    /**
     * Sign in anonymously to enable sync.
     * Returns the user ID on success, null on failure.
     */
    suspend fun signInAnonymously(): String? {
        if (!isAvailable) {
            Log.w(TAG, "Firebase not available, cannot sign in")
            return null
        }

        return try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser != null) {
                Log.d(TAG, "Already signed in: ${auth.currentUser?.uid}")
                return auth.currentUser?.uid
            }

            val result = auth.signInAnonymously().await()
            Log.d(TAG, "Signed in anonymously: ${result.user?.uid}")
            result.user?.uid
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign in anonymously", e)
            null
        }
    }

    /**
     * Sign out and disable sync.
     */
    fun signOut() {
        try {
            FirebaseAuth.getInstance().signOut()
            Log.d(TAG, "Signed out")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign out", e)
        }
    }

    /**
     * Upload provider settings to Firebase.
     *
     * @param providerId Local provider ID
     * @param providerName Provider name for identification
     * @param settings Settings to upload
     * @return true if successful, false otherwise
     */
    suspend fun uploadSettings(
        providerId: Long,
        providerName: String,
        settings: ProviderSettings
    ): Boolean {
        if (!isAvailable || !isSignedIn) {
            Log.w(TAG, "Cannot upload: Firebase unavailable or not signed in")
            return false
        }

        val uid = currentUserId ?: return false

        return try {
            val database = FirebaseDatabase.getInstance()
            val ref = database.reference
                .child(PATH_USERS)
                .child(uid)
                .child(PATH_PROVIDERS)
                .child(providerId.toString())

            val data = mapOf(
                "name" to providerName,
                PATH_SETTINGS to json.encodeToString(settings),
                "updatedAt" to System.currentTimeMillis()
            )

            ref.setValue(data).await()
            Log.d(TAG, "Uploaded settings for provider $providerId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload settings for provider $providerId", e)
            false
        }
    }

    /**
     * Download provider settings from Firebase.
     *
     * @param providerId Local provider ID
     * @return Settings if found, null otherwise
     */
    suspend fun downloadSettings(providerId: Long): ProviderSettings? {
        if (!isAvailable || !isSignedIn) {
            Log.w(TAG, "Cannot download: Firebase unavailable or not signed in")
            return null
        }

        val uid = currentUserId ?: return null

        return try {
            val database = FirebaseDatabase.getInstance()
            val ref = database.reference
                .child(PATH_USERS)
                .child(uid)
                .child(PATH_PROVIDERS)
                .child(providerId.toString())
                .child(PATH_SETTINGS)

            val snapshot = ref.get().await()
            val settingsJson = snapshot.value as? String
            if (settingsJson != null) {
                json.decodeFromString<ProviderSettings>(settingsJson)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download settings for provider $providerId", e)
            null
        }
    }

    /**
     * Listen for real-time settings changes from Firebase.
     *
     * @param providerId Local provider ID
     * @return Flow of settings updates
     */
    fun observeSettings(providerId: Long): Flow<ProviderSettings?> = callbackFlow {
        if (!isAvailable || !isSignedIn) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val uid = currentUserId
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val database = FirebaseDatabase.getInstance()
        val ref = database.reference
            .child(PATH_USERS)
            .child(uid)
            .child(PATH_PROVIDERS)
            .child(providerId.toString())
            .child(PATH_SETTINGS)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val settingsJson = snapshot.value as? String
                val settings = if (settingsJson != null) {
                    try {
                        json.decodeFromString<ProviderSettings>(settingsJson)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse settings", e)
                        null
                    }
                } else {
                    null
                }
                trySend(settings)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Settings listener cancelled: ${error.message}")
                trySend(null)
            }
        }

        ref.addValueEventListener(listener)
        Log.d(TAG, "Started observing settings for provider $providerId")

        awaitClose {
            ref.removeEventListener(listener)
            Log.d(TAG, "Stopped observing settings for provider $providerId")
        }
    }

    /**
     * Delete provider settings from Firebase.
     *
     * @param providerId Local provider ID
     * @return true if successful, false otherwise
     */
    suspend fun deleteSettings(providerId: Long): Boolean {
        if (!isAvailable || !isSignedIn) {
            return false
        }

        val uid = currentUserId ?: return false

        return try {
            val database = FirebaseDatabase.getInstance()
            val ref = database.reference
                .child(PATH_USERS)
                .child(uid)
                .child(PATH_PROVIDERS)
                .child(providerId.toString())

            ref.removeValue().await()
            Log.d(TAG, "Deleted settings for provider $providerId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete settings for provider $providerId", e)
            false
        }
    }

    /**
     * Get sync status flow for UI display.
     */
    fun getSyncStatusFlow(): Flow<SyncStatus> = flow {
        if (!isAvailable) {
            emit(SyncStatus.Unavailable)
            return@flow
        }

        if (!isSignedIn) {
            emit(SyncStatus.SignedOut)
            return@flow
        }

        emit(SyncStatus.Synced)
    }
}
