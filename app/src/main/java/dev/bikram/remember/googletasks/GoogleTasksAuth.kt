package dev.bikram.remember.googletasks

import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest

interface GoogleTasksAuthDelegate {
    suspend fun authorize(
        context: Context,
        forceAccountSelection: Boolean = false,
    ): GoogleTasksAuthorizationResult

    suspend fun parseConsentResult(
        context: Context,
        data: Intent?,
    ): GoogleTasksAuthorizationResult

    suspend fun disconnect(
        context: Context,
        accountEmail: String?,
        accessToken: String? = null,
    )

    suspend fun invalidateToken(
        context: Context,
        token: String,
    )
}

sealed class GoogleTasksAuthorizationResult {
    /**
     * Already authorized. [accessToken] is live; [accountEmail] identifies the picked Google
     * account when available, otherwise the empty string. UI must tolerate a blank email.
     */
    data class Success(
        val accessToken: String,
        val accountEmail: String,
    ) : GoogleTasksAuthorizationResult()

    /**
     * Consent UI must be shown. Caller launches [request] via
     * [androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult]
     * and forwards the resulting [Intent] to the active [GoogleTasksAuthDelegate].
     */
    data class NeedsConsent(
        val request: IntentSenderRequest,
    ) : GoogleTasksAuthorizationResult()

    data class Failure(
        val cause: Throwable,
    ) : GoogleTasksAuthorizationResult()
}
