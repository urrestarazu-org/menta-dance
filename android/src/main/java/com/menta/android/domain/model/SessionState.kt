package com.menta.android.domain.model

/**
 * Access tokens deliberately remain an implementation detail of the process
 * session. This state is safe to expose to presentation because it contains no
 * credentials.
 */
sealed interface SessionState {
    data object SignedOut : SessionState
    data class Authenticated(val expiresInSeconds: Long) : SessionState
    data class Failed(val failure: AuthFailure) : SessionState
}
