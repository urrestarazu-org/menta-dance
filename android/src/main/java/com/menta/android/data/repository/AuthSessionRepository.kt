package com.menta.android.data.repository

import com.menta.android.data.remote.AuthRemoteDataSource
import com.menta.android.data.remote.AuthRemoteResult
import com.menta.android.data.remote.JwtRoleReader
import com.menta.android.domain.model.AuthFailure
import com.menta.android.domain.model.SessionState
import com.menta.android.domain.repository.RefreshTokenStore
import com.menta.android.domain.repository.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the access token solely in this singleton's process memory. */
@Singleton
class AuthSessionRepository @Inject constructor(
    private val refreshTokenStore: RefreshTokenStore,
    private val remote: AuthRemoteDataSource,
) : SessionRepository {
    private var accessToken: String? = null

    override fun login(email: String, password: String): SessionState =
        when (val response = remote.login(email, password)) {
            is AuthRemoteResult.Success -> storeRotatedSession(response)
            is AuthRemoteResult.Problem -> SessionState.Failed(response.toLoginFailure())
            AuthRemoteResult.Unavailable -> SessionState.Failed(AuthFailure.Unavailable)
        }

    override fun refresh(): SessionState {
        val previousRefresh = refreshTokenStore.read() ?: return SessionState.SignedOut
        return when (val response = remote.refresh(previousRefresh)) {
            is AuthRemoteResult.Success -> storeRotatedSession(response)
            is AuthRemoteResult.Problem ->
                if (response.isRefreshRejection()) {
                    // The server will never accept this token again: drop it locally.
                    clearLocalSession()
                    SessionState.Failed(AuthFailure.SessionRejected)
                } else {
                    // Anything else may be transient: keep the token so a later launch can retry.
                    SessionState.Failed(AuthFailure.Unavailable)
                }
            AuthRemoteResult.Unavailable -> SessionState.Failed(AuthFailure.Unavailable)
        }
    }

    override fun logout() {
        val refresh = refreshTokenStore.read()
        val access = accessToken
        try {
            if (refresh != null) remote.logout(access, refresh)
        } finally {
            // Local destruction is unconditional: remote revocation is best effort.
            accessToken = null
            refreshTokenStore.clear()
        }
    }

    private fun storeRotatedSession(response: AuthRemoteResult.Success): SessionState {
        val newRefresh = response.refreshToken
        val newAccess = response.accessToken
        val expiresIn = response.expiresInSeconds
        if (newRefresh.isNullOrBlank() || newAccess.isNullOrBlank() || expiresIn == null) {
            clearLocalSession()
            return SessionState.Failed(AuthFailure.InvalidResponse)
        }

        return try {
            // The server has already invalidated the old refresh. Persist the replacement
            // before retaining the new access token; otherwise fail closed.
            refreshTokenStore.replace(newRefresh)
            accessToken = newAccess
            SessionState.Authenticated(expiresIn, JwtRoleReader.roleOf(newAccess))
        } catch (_: Exception) {
            clearLocalSession()
            SessionState.Failed(AuthFailure.LocalStorageFailed)
        }
    }

    private fun clearLocalSession() {
        accessToken = null
        refreshTokenStore.clear()
    }

    private fun AuthRemoteResult.Problem.toLoginFailure(): AuthFailure = when {
        code == "INVALID_CREDENTIALS" || status == HTTP_UNAUTHORIZED -> AuthFailure.InvalidCredentials
        code == "ACCOUNT_NOT_ACTIVATED" || status == HTTP_FORBIDDEN -> AuthFailure.AccountNotActivated
        code == "USER_LOCKED" || status == HTTP_LOCKED -> AuthFailure.AccountLocked
        status == HTTP_TOO_MANY_REQUESTS -> AuthFailure.RateLimited(retryAfterSeconds)
        code == "AUTH_DEGRADED" || status == HTTP_UNAVAILABLE -> AuthFailure.Unavailable
        else -> AuthFailure.Unexpected(code)
    }

    private fun AuthRemoteResult.Problem.isRefreshRejection(): Boolean =
        status == HTTP_UNAUTHORIZED ||
            code == "REFRESH_TOKEN_COMPROMISED" ||
            (status == HTTP_BAD_REQUEST && code == "INVALID_AUTH_REQUEST")

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_LOCKED = 423
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_UNAVAILABLE = 503
    }
}
