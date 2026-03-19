package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manages Google Sign-In for Drive appDataFolder access.
 * Uses the drive.appdata scope which only allows access to app-specific hidden folder.
 */
@Suppress("DEPRECATION")
class GoogleAuthManager(
    private val context: Context,
) {
    companion object {
        private const val TAG = "GoogleAuthManager"
        const val RC_SIGN_IN = 9001
    }

    private val signInOptions: GoogleSignInOptions by lazy {
        GoogleSignInOptions
            .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
    }

    private val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, signInOptions)
    }

    /**
     * Check if already signed in with required scope.
     */
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null &&
            GoogleSignIn.hasPermissions(
                account,
                Scope(DriveScopes.DRIVE_APPDATA),
            )
    }

    /**
     * Get the currently signed-in account, or null if not signed in.
     */
    fun getAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    /**
     * Get Google account credential for Drive API.
     */
    fun getCredential(): GoogleAccountCredential? {
        val account = getAccount() ?: return null
        return GoogleAccountCredential
            .usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE_APPDATA),
            ).apply {
                selectedAccount = account.account
            }
    }

    /**
     * Attempt silent sign-in. Returns true if successful.
     * Call this on app startup to restore previous session.
     */
    suspend fun trySilentSignIn(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // First check if we already have a valid account
                if (isSignedIn()) {
                    return@withContext true
                }

                // Try silent sign-in
                val account = signInClient.silentSignIn().await()
                true
            } catch (e: ApiException) {
                false
            } catch (e: Exception) {
                Log.e(TAG, "Silent sign-in error")
                false
            }
        }

    /**
     * Get sign-in intent for launching account picker.
     * Use this when silent sign-in fails.
     */
    fun getSignInIntent(): Intent = signInClient.signInIntent

    /**
     * Handle result from sign-in intent.
     * Call from Activity.onActivityResult.
     */
    suspend fun handleSignInResult(data: Intent?): GoogleSignInAccount? =
        withContext(Dispatchers.IO) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.await()
                account
            } catch (e: ApiException) {
                Log.e(TAG, "Sign-in failed: ${e.statusCode}")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Sign-in error")
                null
            }
        }

    /**
     * Sign out and revoke access.
     */
    suspend fun signOut() =
        withContext(Dispatchers.IO) {
            try {
                signInClient.signOut().await()
            } catch (e: Exception) {
                Log.e(TAG, "Sign out failed", e)
            }
        }

    /**
     * Revoke access completely.
     */
    suspend fun revokeAccess() =
        withContext(Dispatchers.IO) {
            try {
                signInClient.revokeAccess().await()
            } catch (e: Exception) {
                Log.e(TAG, "Revoke access failed", e)
            }
        }
}
