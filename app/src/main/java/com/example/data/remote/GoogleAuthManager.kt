package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

sealed interface GoogleSignInResult {
    data class Success(val user: FirebaseUser) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data class Error(val message: String) : GoogleSignInResult
}

object GoogleAuthManager {
    private const val TAG = "Auth"

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    suspend fun attemptSilentSignIn(context: Context): FirebaseUser? {
        val auth = Firebase.auth
        auth.currentUser?.let { return it }

        val webClientId = context.getString(R.string.default_web_client_id)
        if (webClientId.isBlank()) return null

        val hostContext: Context = context.findActivity() ?: context

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(hostContext)
            val result = credentialManager.getCredential(hostContext, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                signInToFirebaseWithIdToken(auth, googleCred.idToken)
            } else {
                null
            }
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No authorized Google credential for silent sign-in: ${e.message}")
            null
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Silent Google Sign-In cancelled: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Silent Google Sign-In skipped: ${e.message}", e)
            null
        }
    }

    suspend fun signInInteractive(context: Context): GoogleSignInResult {
        val auth = Firebase.auth
        auth.currentUser?.let { return GoogleSignInResult.Success(it) }

        val webClientId = context.getString(R.string.default_web_client_id)
        if (webClientId.isBlank()) {
            return GoogleSignInResult.Error("Google Sign-In client ID is missing in configuration.")
        }

        val activityContext: Context = context.findActivity() ?: context
        val credentialManager = CredentialManager.create(activityContext)

        // Primary interactive request: GetSignInWithGoogleOption (single option per request)
        val primaryError = try {
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val user = signInToFirebaseWithIdToken(auth, googleCred.idToken)
                return GoogleSignInResult.Success(user)
            }
            "Unexpected credential type returned."
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            return GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google account available via GetSignInWithGoogleOption: ${e.message}", e)
            null
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Primary Google Sign-In failed (${e.type}): ${e.message}", e)
            e.message ?: e.localizedMessage
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
            return GoogleSignInResult.Error(e.localizedMessage ?: "Google Sign-In failed. Please try again.")
        }

        // Secondary separate request: unfiltered GetGoogleIdOption (helps on OEM Play Services / 28404 quirks)
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()
            val fallbackRequest = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(activityContext, fallbackRequest)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val user = signInToFirebaseWithIdToken(auth, googleCred.idToken)
                GoogleSignInResult.Success(user)
            } else {
                GoogleSignInResult.Error(primaryError ?: "Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Fallback Google Sign-In cancelled or dismissed: ${e.message}", e)
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google account available on device: ${e.message}", e)
            GoogleSignInResult.Error("No Google account found on this device. Add a Google account in device settings and retry.")
        } catch (e: Exception) {
            Log.e(TAG, "Fallback Google Sign-In failed: ${e.message}", e)
            val rawMsg = e.message ?: primaryError ?: "Google Sign-In failed."
            GoogleSignInResult.Error(formatSignInErrorMessage(rawMsg))
        }
    }

    private fun formatSignInErrorMessage(raw: String): String {
        return if (raw.contains("28404") || raw.contains("Failed to retrieve an ID token", ignoreCase = true)) {
            "Google ID token error (28404): Check internet connection & Google Play Services, clear Play Services cache or install the rebuilt debug APK, and verify your SHA-1 / OAuth Consent Screen Test Users in Firebase/Google Cloud."
        } else {
            raw
        }
    }

    fun signOut() {
        Firebase.auth.signOut()
    }

    private suspend fun signInToFirebaseWithIdToken(
        auth: FirebaseAuth,
        idToken: String
    ): FirebaseUser = suspendCancellableCoroutine { cont ->
        val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCred)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user != null) {
                    cont.resume(user)
                } else {
                    cont.resumeWithException(IllegalStateException("Firebase user was null after sign-in"))
                }
            }
            .addOnFailureListener { exc ->
                cont.resumeWithException(exc)
            }
    }
}
