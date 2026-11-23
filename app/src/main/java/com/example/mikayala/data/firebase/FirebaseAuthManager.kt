package com.example.mikayala.data.firebase

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.mikayala.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object FirebaseAuthManager {

    private const val TAG = "FirebaseAuthManager"

    val auth: FirebaseAuth
        get() = Firebase.auth

    fun currentUser(): FirebaseUser? = try {
        auth.currentUser
    } catch (e: Throwable) {
        Log.w(TAG, "currentUser unavailable: ${e.message}")
        null
    }

    fun currentUid(): String = currentUser()?.uid.orEmpty()

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val firebaseAuth = try {
            auth
        } catch (e: Throwable) {
            Log.w(TAG, "authStateFlow unavailable: ${e.message}")
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { fa ->
            trySend(fa.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    /**
     * Silent auto sign-in using GetGoogleIdOption for returning authorized Google accounts.
     * Each GetCredentialRequest carries strictly ONE credential option.
     */
    suspend fun attemptAutoSignIn(context: Context): Result<FirebaseUser?> {
        auth.currentUser?.let { return Result.success(it) }
        return try {
            val serverClientId = context.getString(R.string.default_web_client_id)
            if (serverClientId.isBlank()) {
                return Result.success(null)
            }
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(context)
            val response = credentialManager.getCredential(context = context, request = request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = GoogleAuthProvider.getCredential(googleIdToken.idToken, null)
                val result = auth.signInWithCredential(firebaseCred).await()
                Result.success(result.user)
            } else {
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Auto Google sign-in cancelled by user or play services", e)
            Result.success(null)
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No authorized Google account for silent sign-in")
            Result.success(null)
        } catch (e: Exception) {
            Log.w(TAG, "Auto sign-in skipped: ${e.message}")
            Result.success(null)
        }
    }

    /**
     * Interactive Google Sign-In using GetSignInWithGoogleOption (required Activity context).
     * Each GetCredentialRequest carries strictly ONE credential option.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser> {
        return try {
            val serverClientId = activityContext.getString(R.string.default_web_client_id)
            require(serverClientId.isNotBlank()) { "Configuration Google Sign-In manquante." }

            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val credentialManager = CredentialManager.create(activityContext)
            val response = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                val authResult = auth.signInWithCredential(firebaseCred).await()
                val user = authResult.user
                    ?: return Result.failure(IllegalStateException("Compte Firebase introuvable après connexion Google."))
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Type d'identifiant Google non pris en charge."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google sign-in cancelled (GetCredentialCancellationException)", e)
            Result.failure(IllegalStateException("Connexion Google annulée. Sélectionnez un compte pour continuer."))
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO + NonCancellable) {
        try {
            auth.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseAuth signOut error: ${e.message}")
        }
        try {
            CredentialManager.create(context.applicationContext)
                .clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "CredentialManager clearCredentialState error: ${e.message}")
        }
    }
}
