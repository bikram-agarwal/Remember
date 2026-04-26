// File-level suppress because SignInClient.signOut() is deprecated but remains the documented
// path for clearing Play Services' Identity sign-in cache (no non-deprecated equivalent has
// shipped yet). Scoped to this file so other deprecated-API uses elsewhere still get flagged.
@file:Suppress("DEPRECATION")

package dev.bikram.remember.googletasks

import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Tasks authentication using the modern Identity Services Authorization API.
 *
 * Why this and not [com.google.android.gms.auth.GoogleAuthUtil] + [android.accounts.AccountManager]:
 *  - AccountManager.newChooseAccountIntent shows the legacy AOSP radio-button picker with raw
 *    plaintext emails. The modern picker (avatars, name + email, system-styled bottom sheet)
 *    only comes from Identity Services. The Authorization API also returns an OAuth access
 *    token directly, so we don't need a second `getToken` round-trip.
 *  - Silent refresh just means calling [authorize] again. After the first user consent the
 *    same call returns `hasResolution() == false` with a fresh access token, no UI shown.
 *
 * Scopes requested:
 *  - tasks.readonly - the actual reason we're here.
 *  - email           - so the consent prompt clearly lists "See your primary email address"
 *                      instead of the generic "access to your Google Account", and so we
 *                      can reliably fetch the picked account's email via the userinfo
 *                      endpoint. AuthorizationResult.toGoogleSignInAccount() returns null
 *                      under the pure Authorization API (it's a legacy GoogleSignIn bridge
 *                      that only populates after a sign-in flow), so without the email scope
 *                      we have no reliable handle on which account the user picked.
 *
 * The OAuth client must still be registered in Google Cloud Console with this app's package
 * name + signing certificate SHA-1. See `localdocs/google-tasks-oauth-setup.md`.
 */
object GoogleTasksAuth {

    private const val TASKS_READONLY_SCOPE = "https://www.googleapis.com/auth/tasks.readonly"
    private const val EMAIL_SCOPE = "https://www.googleapis.com/auth/userinfo.email"
    private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
    private const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class UserInfoResponse(val email: String? = null)

    private fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(TASKS_READONLY_SCOPE), Scope(EMAIL_SCOPE)))
            .build()

    /**
     * Attempt to authorize the requested scopes.
     *
     * Returns [GoogleTasksAuthorizationResult.Success] when an access token is already live -
     * either because the user just consented or because Identity Services silently refreshed
     * a previously-granted token. The success result also carries the picked account's email
     * (best-effort: fetched from userinfo endpoint, falling back to empty if the network is
     * unavailable).
     *
     * Returns [GoogleTasksAuthorizationResult.NeedsConsent] when the consent UI must be shown.
     * Caller launches `request` via
     * [androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult]
     * and feeds the returned [Intent] back through [parseConsentResult].
     */
    suspend fun authorize(context: Context): GoogleTasksAuthorizationResult {
        return try {
            val task = Identity.getAuthorizationClient(context).authorize(authorizationRequest())
            val result = task.await()
            result.toAuthorizationResult()
        } catch (e: ApiException) {
            GoogleTasksAuthorizationResult.Failure(e)
        } catch (e: Throwable) {
            GoogleTasksAuthorizationResult.Failure(e)
        }
    }

    /**
     * Read the result of the consent-screen activity. The [data] is the Intent delivered to
     * the [androidx.activity.result.ActivityResultCallback] from the
     * [androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult]
     * launcher.
     */
    suspend fun parseConsentResult(context: Context, data: Intent?): GoogleTasksAuthorizationResult {
        if (data == null) return GoogleTasksAuthorizationResult.Failure(IllegalStateException("Empty consent result"))
        return try {
            val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            result.toAuthorizationResult()
        } catch (e: ApiException) {
            GoogleTasksAuthorizationResult.Failure(e)
        } catch (e: Throwable) {
            GoogleTasksAuthorizationResult.Failure(e)
        }
    }

    /**
     * Forget the current authorization grant so the next [authorize] call shows the picker
     * again instead of silently returning the previously picked account.
     *
     * The combination matters - none of these alone is enough:
     *  1. **`CredentialManager.clearCredentialState()`** - the AndroidX Credential Manager API.
     *     This is what actually convinces Identity Services to drop its "current account"
     *     pointer so `authorize()` returns a NeedsConsent (picker) on the next call. Critically,
     *     it does NOT revoke the underlying OAuth grant, so freshly-issued tokens after
     *     re-consent still work for the Tasks API.
     *  2. **REST `/revoke`** of the current access token - kills the access token + sibling
     *     refresh token server-side. Belt-and-suspenders for the security-token side.
     *  3. **`SignInClient.signOut()`** - clears the legacy Identity Services sign-in cache.
     *
     * Why we deliberately do NOT use `GoogleSignInClient.revokeAccess()` (legacy API): that
     * call fully wipes the OAuth grant for our app, which is too aggressive - the new tokens
     * issued after re-consent are then rejected by the Tasks API in a loop.
     */
    suspend fun forget(context: Context, accessToken: String? = null) {
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
        if (!accessToken.isNullOrBlank()) {
            revokeToken(accessToken)
        }
        runCatching {
            Identity.getSignInClient(context).signOut().await()
        }
    }

    /**
     * Invalidate a previously-issued token. Used after a 401 response so the next
     * [authorize] call mints a fresh one rather than handing back the stale one from cache.
     */
    suspend fun invalidateToken(context: Context, token: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                com.google.android.gms.auth.GoogleAuthUtil.clearToken(context, token)
            }
        }
    }

    /**
     * POST to https://oauth2.googleapis.com/revoke with the access token in the body. After
     * this returns 200 the OAuth grant for (this app, this account) is gone, and any future
     * authorize() call goes through the consent UI again.
     *
     * Errors are swallowed: failure to revoke is not catastrophic since we're about to launch
     * a fresh authorization anyway, but a successful revoke gives the cleaner UX (the picker
     * re-appears rather than silently re-using the cached account).
     */
    private suspend fun revokeToken(accessToken: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(REVOKE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                try {
                    conn.outputStream.use { os ->
                        os.write("token=$accessToken".toByteArray(Charsets.UTF_8))
                    }
                    // Reading the response code drives the request to completion.
                    conn.responseCode
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    private suspend fun AuthorizationResult.toAuthorizationResult(): GoogleTasksAuthorizationResult {
        if (hasResolution()) {
            val sender = pendingIntent?.intentSender
                ?: return GoogleTasksAuthorizationResult.Failure(
                    IllegalStateException("Authorization result has resolution flag but no pendingIntent"),
                )
            return GoogleTasksAuthorizationResult.NeedsConsent(
                IntentSenderRequest.Builder(sender).build(),
            )
        }
        val token = accessToken
        if (token.isNullOrBlank()) {
            return GoogleTasksAuthorizationResult.Failure(
                IllegalStateException("Authorization result has neither resolution nor access token"),
            )
        }
        // toGoogleSignInAccount() is a bridge to the legacy GoogleSignIn API and returns null
        // when we used the pure Authorization flow. Try it first (cheap), then fall back to the
        // userinfo endpoint, then to empty - the UI does not require a non-empty email.
        val accountFromBridge = toGoogleSignInAccount()?.email
        val email = accountFromBridge ?: fetchEmailFromUserInfo(token).orEmpty()
        return GoogleTasksAuthorizationResult.Success(accessToken = token, accountEmail = email)
    }

    /**
     * Best-effort fetch of the picked account's email using the OpenID userinfo endpoint.
     * Requires the `userinfo.email` scope, which we always include on the authorization request.
     * Returns null on any network or parse error - callers must tolerate a missing email.
     */
    private suspend fun fetchEmailFromUserInfo(accessToken: String): String? = withContext(Dispatchers.IO) {
        val conn = (URL(USERINFO_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 10_000
            readTimeout = 15_000
            doInput = true
        }
        try {
            if (conn.responseCode !in 200..299) return@withContext null
            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            json.decodeFromString(UserInfoResponse.serializer(), body).email
        } catch (_: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }
}

interface GoogleTasksAuthDelegate {
    suspend fun authorize(context: Context): GoogleTasksAuthorizationResult
    suspend fun parseConsentResult(context: Context, data: Intent?): GoogleTasksAuthorizationResult
    suspend fun forget(context: Context, accessToken: String? = null)
    suspend fun invalidateToken(context: Context, token: String)
}

object DefaultGoogleTasksAuthDelegate : GoogleTasksAuthDelegate {
    override suspend fun authorize(context: Context): GoogleTasksAuthorizationResult {
        return GoogleTasksAuth.authorize(context)
    }

    override suspend fun parseConsentResult(
        context: Context,
        data: Intent?,
    ): GoogleTasksAuthorizationResult {
        return GoogleTasksAuth.parseConsentResult(context, data)
    }

    override suspend fun forget(context: Context, accessToken: String?) {
        GoogleTasksAuth.forget(context, accessToken)
    }

    override suspend fun invalidateToken(context: Context, token: String) {
        GoogleTasksAuth.invalidateToken(context, token)
    }
}

/** Suspend-friendly wrapper around the Play Services [Task] so we don't pull in a play-services-coroutine artifact. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    if (isComplete) {
        val ex = exception
        if (ex != null) cont.resumeWithException(ex)
        else if (isCanceled) cont.cancel()
        else @Suppress("UNCHECKED_CAST") cont.resume(result as T)
        return@suspendCancellableCoroutine
    }
    addOnSuccessListener { value -> cont.resume(value) }
    addOnFailureListener { error -> cont.resumeWithException(error) }
    addOnCanceledListener { cont.cancel() }
}

sealed class GoogleTasksAuthorizationResult {
    /**
     * Already authorized. [accessToken] is live; [accountEmail] identifies the picked Google
     * account when available, otherwise the empty string. UI must tolerate a blank email.
     */
    data class Success(val accessToken: String, val accountEmail: String) : GoogleTasksAuthorizationResult()

    /**
     * Consent UI must be shown. Caller launches [request] via
     * [androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult]
     * and forwards the resulting [Intent] to [GoogleTasksAuth.parseConsentResult].
     */
    data class NeedsConsent(val request: IntentSenderRequest) : GoogleTasksAuthorizationResult()

    data class Failure(val cause: Throwable) : GoogleTasksAuthorizationResult()
}
