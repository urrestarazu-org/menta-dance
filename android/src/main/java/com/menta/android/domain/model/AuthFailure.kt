package com.menta.android.domain.model

/**
 * A safe, wire-independent authentication failure. Never carries token values,
 * response detail or credentials.
 */
sealed interface AuthFailure {
    /** Wrong email or password. */
    data object InvalidCredentials : AuthFailure

    /** The account is not activated or not operational; shown like [InvalidCredentials]. */
    data object AccountNotActivated : AuthFailure

    data object AccountLocked : AuthFailure

    data class RateLimited(val retryAfterSeconds: Long?) : AuthFailure

    /** Network failure or the Auth API is degraded; stored credentials are kept. */
    data object Unavailable : AuthFailure

    /** The refresh token was rejected; the stored token has already been wiped. */
    data object SessionRejected : AuthFailure

    data object InvalidResponse : AuthFailure

    data object LocalStorageFailed : AuthFailure

    /** An API problem code this client does not know. Stable code only, never detail. */
    data class Unexpected(val code: String) : AuthFailure
}
