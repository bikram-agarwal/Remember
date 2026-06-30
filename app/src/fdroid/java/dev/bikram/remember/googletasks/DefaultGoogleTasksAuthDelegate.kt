package dev.bikram.remember.googletasks

import android.content.Context
import android.content.Intent

object DefaultGoogleTasksAuthDelegate : GoogleTasksAuthDelegate {
    private val unavailable =
        UnsupportedOperationException("Google account import is not available in the F-Droid build")

    override suspend fun authorize(
        context: Context,
        forceAccountSelection: Boolean,
    ): GoogleTasksAuthorizationResult = GoogleTasksAuthorizationResult.Failure(unavailable)

    override suspend fun parseConsentResult(
        context: Context,
        data: Intent?,
    ): GoogleTasksAuthorizationResult = GoogleTasksAuthorizationResult.Failure(unavailable)

    override suspend fun disconnect(
        context: Context,
        accountEmail: String?,
        accessToken: String?,
    ) = Unit

    override suspend fun invalidateToken(
        context: Context,
        token: String,
    ) = Unit
}
