package dev.bikram.remember.googletasks

import android.content.Context
import androidx.activity.result.IntentSenderRequest

/**
 * Thin facade combining [GoogleTasksAuth] (token mint) with [GoogleTasksApi] (REST). Centralises
 * 401-then-refresh-and-retry semantics so the ViewModel never sees a stale token.
 *
 * After the first successful authorize() the same call returns a fresh access token silently on
 * subsequent invocations (no UI), which means our refresh path is "just call authorize again".
 */
class GoogleTasksRepository(
    private val context: Context,
    private val api: GoogleTasksApi,
    private val auth: GoogleTasksAuthDelegate = DefaultGoogleTasksAuthDelegate,
) {
    suspend fun fetchTaskLists(
        firstAttemptToken: String,
    ): GoogleTasksFetchResult<List<GoogleTaskList>> =
        fetchWithRetry(
            firstAttemptToken = firstAttemptToken,
        ) { token -> api.listTaskLists(token) }

    suspend fun fetchTasks(
        firstAttemptToken: String,
        taskListId: String,
    ): GoogleTasksFetchResult<List<GoogleTask>> =
        fetchWithRetry(
            firstAttemptToken = firstAttemptToken,
        ) { token -> api.listTasks(token, taskListId) }

    private suspend fun <T> fetchWithRetry(
        firstAttemptToken: String,
        call: suspend (String) -> GoogleTasksApiResult<T>,
    ): GoogleTasksFetchResult<T> {
        val first = call(firstAttemptToken)
        if (first is GoogleTasksApiResult.Success) {
            return GoogleTasksFetchResult.Success(first.value)
        }
        if (first !is GoogleTasksApiResult.Unauthorized) {
            return first.toFetchFailure()
        }
        // 401/403: silently re-authorize. After first user consent this round-trip stays in
        // the background and produces a fresh token.
        auth.invalidateToken(context, firstAttemptToken)
        return when (val refresh = auth.authorize(context)) {
            is GoogleTasksAuthorizationResult.Success -> {
                when (val second = call(refresh.accessToken)) {
                    is GoogleTasksApiResult.Success ->
                        GoogleTasksFetchResult.Success(second.value, refresh.accessToken)
                    // A double-401 here is rare and usually a transient post-grant propagation
                    // gap. Surface it as AuthError; the screen shows a message and the user
                    // can retry. We deliberately do NOT forget()+re-authorize here because
                    // that would tear down a freshly-established grant and re-open the picker
                    // in a loop right after a successful sign-in.
                    else -> second.toFetchFailure()
                }
            }
            is GoogleTasksAuthorizationResult.NeedsConsent ->
                GoogleTasksFetchResult.NeedsConsent(refresh.request)
            is GoogleTasksAuthorizationResult.Failure ->
                GoogleTasksFetchResult.AuthError(refresh.cause)
        }
    }

    private fun <T> GoogleTasksApiResult<T>.toFetchFailure(): GoogleTasksFetchResult<T> =
        when (this) {
            is GoogleTasksApiResult.Success -> GoogleTasksFetchResult.Success(value)
            GoogleTasksApiResult.Unauthorized ->
                GoogleTasksFetchResult.AuthError(
                    cause = IllegalStateException("Tasks API returned unauthorized after refresh"),
                )
            is GoogleTasksApiResult.NetworkError -> GoogleTasksFetchResult.Network(cause)
            is GoogleTasksApiResult.Failure -> GoogleTasksFetchResult.Other(cause)
        }
}

sealed class GoogleTasksFetchResult<out T> {
    /**
     * On success [refreshedAccessToken] is non-null only when an internal refresh happened. The
     * caller can stash it so subsequent fetches skip the first stale-token round-trip.
     */
    data class Success<T>(
        val value: T,
        val refreshedAccessToken: String? = null,
    ) : GoogleTasksFetchResult<T>()

    /** Consent UI must be shown again. Forwarded as an IntentSenderRequest for the launcher. */
    data class NeedsConsent(
        val request: IntentSenderRequest,
    ) : GoogleTasksFetchResult<Nothing>()

    data class AuthError(
        val cause: Throwable,
    ) : GoogleTasksFetchResult<Nothing>()

    data class Network(
        val cause: Throwable,
    ) : GoogleTasksFetchResult<Nothing>()

    data class Other(
        val cause: Throwable,
    ) : GoogleTasksFetchResult<Nothing>()
}
