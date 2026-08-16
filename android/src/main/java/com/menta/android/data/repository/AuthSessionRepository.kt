package com.menta.android.data.repository

import com.menta.android.data.remote.AuthRemoteDataSource
import com.menta.android.data.remote.AuthRemoteResult
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
            is AuthRemoteResult.Problem -> SessionState.Failed(
                AuthFailure(response.code, response.retryAfterSeconds),
            )
            AuthRemoteResult.Unavailable -> SessionState.Failed(AuthFailure("AUTH_UNAVAILABLE"))
        }

    override fun refresh(): SessionState {
        val previousRefresh = refreshTokenStore.read() ?: return SessionState.SignedOut
        return when (val response = remote.refresh(previousRefresh)) {
            is AuthRemoteResult.Success -> storeRotatedSession(response)
            is AuthRemoteResult.Problem -> SessionState.Failed(
                AuthFailure(response.code, response.retryAfterSeconds),
            )
            AuthRemoteResult.Unavailable -> SessionState.Failed(AuthFailure("AUTH_UNAVAILABLE"))
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
            return SessionState.Failed(AuthFailure("INVALID_AUTH_RESPONSE"))
        }

        return try {
            // The server has already invalidated the old refresh. Persist the replacement
            // before retaining the new access token; otherwise fail closed.
            refreshTokenStore.replace(newRefresh)
            accessToken = newAccess
            SessionState.Authenticated(expiresIn)
        } catch (_: Exception) {
            clearLocalSession()
            SessionState.Failed(AuthFailure("LOCAL_TOKEN_STORAGE_FAILED"))
        }
    }

    private fun clearLocalSession() {
        accessToken = null
        refreshTokenStore.clear()
    }
}
