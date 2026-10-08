package com.menta.android.data.repository

import com.menta.android.data.remote.AuthRemoteDataSource
import com.menta.android.data.remote.AuthRemoteResult
import com.menta.android.data.remote.JwtRoleReader
import com.menta.android.data.session.AccessTokenSource
import com.menta.android.data.session.AccessTokenSource.Renewal
import com.menta.android.domain.model.AuthFailure
import com.menta.android.domain.model.SessionState
import com.menta.android.domain.repository.RefreshTokenStore
import com.menta.android.domain.repository.SessionRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the access token solely in this singleton's process memory.
 *
 * Every state change that talks to the server (login, cold-start refresh, logout and 401
 * renewal) runs under one [lock] held across the network call. The refresh token rotates and
 * a reused one revokes every session, so two rotations must never overlap or be replayed.
 */
@Singleton
class AuthSessionRepository @Inject constructor(
    private val refreshTokenStore: RefreshTokenStore,
    private val remote: AuthRemoteDataSource,
) : SessionRepository, AccessTokenSource {
    private val lock = Any()

    @Volatile private var accessToken: String? = null

    /** Counts finished renewal attempts. Read before blocking, to detect an attempt that finished meanwhile. */
    @Volatile private var generation = 0L

    /** Outcome of the latest renewal attempt, shared with callers that waited on it. Guarded by [lock]. */
    private var lastRenewal: Renewal = Renewal.Rejected

    private val expiry = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val sessionExpired: Flow<Unit> = expiry

    override fun currentAccessToken(): String? = accessToken

    override fun renewAfterUnauthorized(staleAccessToken: String): Renewal {
        val generationSeen = generation
        synchronized(lock) {
            // A renewal finished while this caller waited for the lock: reuse its outcome.
            if (generation != generationSeen) return lastRenewal
            // A newer token than the one that failed is already held: nothing to refresh.
            accessToken?.takeIf { it != staleAccessToken }?.let { return Renewal.Renewed(it) }
            return renewLocked().also {
                lastRenewal = it
                generation++
            }
        }
    }

    override fun expireSession() = synchronized(lock) {
        val hadSession = accessToken != null || refreshTokenStore.read() != null
        clearLocalSession()
        // Nothing to announce when the user already signed out: no screen is left to react.
        if (hadSession) expiry.tryEmit(Unit)
        Unit
    }

    override fun login(email: String, password: String): SessionState = synchronized(lock) {
        when (val response = remote.login(email, password)) {
            is AuthRemoteResult.Success -> storeRotatedSession(response)
            is AuthRemoteResult.Problem -> SessionState.Failed(response.toLoginFailure())
            AuthRemoteResult.Unavailable -> SessionState.Failed(AuthFailure.Unavailable)
        }
    }

    override fun refresh(): SessionState = synchronized(lock) { refreshLocked() }

    override fun logout() {
        synchronized(lock) {
            val refresh = refreshTokenStore.read()
            try {
                if (refresh != null) revokeRemotely(refresh)
            } finally {
                // Local destruction is unconditional: remote revocation is best effort.
                clearLocalSession()
            }
        }
    }

    /**
     * `/logout` needs a valid access token, so one that expired answers 401 even though the
     * refresh token is still alive for days. Renew once and repeat, or the server-side
     * session would outlive the local one.
     */
    private fun revokeRemotely(refresh: String) {
        val first = remote.logout(accessToken, refresh)
        if (first is AuthRemoteResult.Problem && first.status == HTTP_UNAUTHORIZED &&
            refreshLocked() is SessionState.Authenticated
        ) {
            refreshTokenStore.read()?.let { remote.logout(accessToken, it) }
        }
    }

    private fun renewLocked(): Renewal = when (val state = refreshLocked()) {
        is SessionState.Authenticated -> accessToken?.let { Renewal.Renewed(it) } ?: Renewal.Rejected
        is SessionState.Failed ->
            if (state.failure == AuthFailure.Unavailable) {
                Renewal.Unavailable
            } else {
                // The session was wiped (rejected, malformed or unstorable): tell the screens.
                expiry.tryEmit(Unit)
                Renewal.Rejected
            }
        SessionState.SignedOut -> Renewal.Rejected
    }

    private fun refreshLocked(): SessionState {
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
