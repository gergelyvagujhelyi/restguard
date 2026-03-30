package com.restguard.data.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.restguard.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.readonly"

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
) {
    /** All signed-in Google account emails. */
    private val _accounts = MutableStateFlow<Set<String>>(emptySet())
    val accounts: StateFlow<Set<String>> = _accounts.asStateFlow()

    private val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CALENDAR_SCOPE))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Restore saved accounts from preferences without blocking the main thread
        scope.launch {
            val saved = userPreferences.googleCalendarEmails.first()
            _accounts.value = saved
        }
    }

    fun getSignInIntent(): Intent = signInClient.signInIntent

    suspend fun handleSignInResult(account: GoogleSignInAccount?) {
        val email = account?.email ?: return
        _accounts.value = _accounts.value + email
        userPreferences.addGoogleCalendarEmail(email)
        // Sign out from the client so the account picker appears again next time
        signInClient.signOut()
    }

    fun hasAccounts(): Boolean = _accounts.value.isNotEmpty()

    /**
     * Returns an OAuth2 access token for the given account, or null if unavailable.
     * If the permission has been revoked (from Google account settings), the account
     * is automatically removed.
     */
    suspend fun getAccessToken(email: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val account = Account(email, "com.google")
                com.google.android.gms.auth.GoogleAuthUtil.getToken(
                    context, account, "oauth2:$CALENDAR_SCOPE",
                )
            } catch (e: UserRecoverableAuthException) {
                // Permission revoked — user needs to re-authorize
                signOut(email)
                null
            } catch (e: GoogleAuthException) {
                // Permanent auth failure (e.g. account removed from device)
                signOut(email)
                null
            } catch (_: Exception) {
                // Transient error (network, etc.) — don't remove the account
                null
            }
        }
    }

    suspend fun signOut(email: String) {
        _accounts.value = _accounts.value - email
        userPreferences.removeGoogleCalendarEmail(email)
        if (_accounts.value.isEmpty()) {
            signInClient.signOut()
        }
    }
}
